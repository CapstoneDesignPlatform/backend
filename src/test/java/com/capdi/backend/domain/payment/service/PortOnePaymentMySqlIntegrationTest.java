package com.capdi.backend.domain.payment.service;

import com.capdi.backend.domain.announcement.entity.*;
import com.capdi.backend.domain.announcement.repository.AnnouncementRepository;
import com.capdi.backend.domain.bid.entity.*;
import com.capdi.backend.domain.bid.repository.BidRepository;
import com.capdi.backend.domain.client.entity.ClientInfo;
import com.capdi.backend.domain.client.repository.ClientRepository;
import com.capdi.backend.domain.expert.client.*;
import com.capdi.backend.domain.expert.entity.*;
import com.capdi.backend.domain.expert.repository.ExpertProfileRepository;
import com.capdi.backend.domain.expert.repository.ExpertFileRepository;
import com.capdi.backend.domain.expert.repository.BusinessRegistrationInfoRepository;
import com.capdi.backend.domain.payment.dto.*;
import com.capdi.backend.domain.payment.entity.*;
import com.capdi.backend.domain.payment.gateway.*;
import com.capdi.backend.domain.payment.repository.*;
import com.capdi.backend.domain.user.entity.*;
import com.capdi.backend.domain.user.repository.UserRepository;
import com.capdi.backend.global.exception.*;
import com.capdi.backend.global.jwt.JwtUtil;
import com.fasterxml.jackson.databind.*;
import jakarta.persistence.EntityManager;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.*;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.*;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.*;
import org.springframework.test.context.bean.override.mockito.*;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.*;
import javax.sql.DataSource;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.IntStream;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@Tag("mysql-integration") @Testcontainers
@Import(PortOnePaymentMySqlIntegrationTest.PortOneConfig.class)
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,properties={
        "spring.config.location=classpath:/application.yml","spring.profiles.active=issue57-processing-test",
        "server.address=127.0.0.1","spring.datasource.driver-class-name=com.mysql.cj.jdbc.Driver",
        "spring.flyway.enabled=true","spring.flyway.baseline-on-migrate=false","spring.flyway.validate-on-migrate=true",
        "spring.jpa.hibernate.ddl-auto=validate","spring.jpa.show-sql=false","logging.level.org.hibernate.SQL=OFF",
        "nts.business.enabled=false","payment.portone.webhook-enabled=true"})
class PortOnePaymentMySqlIntegrationTest {
    @TestConfiguration static class PortOneConfig {
        @Bean @Primary PortOneStub portOneStub(){return new PortOneStub();}
        @Bean @Primary PaymentGateway portOneGateway(PortOneStub stub,@org.springframework.beans.factory.annotation.Qualifier("paymentClock") Clock clock) {
            var settings=new com.capdi.backend.domain.payment.gateway.portone.PortOneSettings(true,true,"SANDBOX",
                    "merchant-test","store-test","channel-test","SYNTHETIC_API_SECRET",WEBHOOK_SECRET,Duration.ofSeconds(3));
            return new com.capdi.backend.domain.payment.gateway.portone.PortOneV2Gateway(settings,stub,new ObjectMapper(),clock);
        }
    }
    static MutableClock testPaymentClock(){return new MutableClock();}
    static class MutableClock extends Clock {
        final AtomicReference<Instant> now=new AtomicReference<>(Instant.parse("2026-01-01T00:00:00Z"));
        void reset(){now.set(Instant.parse("2026-01-01T00:00:00Z"));}
        void advance(long seconds){now.updateAndGet(i->i.plusSeconds(seconds));}
        public Instant instant(){return now.get();}
        public ZoneId getZone(){return ZoneOffset.UTC;}
        public Clock withZone(ZoneId zone){return this;}
    }
    @Container static final MySQLContainer<?> MYSQL=new MySQLContainer<>("mysql:8.4")
            .withDatabaseName("capdi_issue57_portone_test").withUsername("processing57_test")
            .withPassword(UUID.randomUUID().toString()).withTmpFs(Map.of("/var/lib/mysql","rw"))
            .withLabel("capdi.test.scope","issue57-processing-ephemeral");
    @TempDir static Path uploads;
    @DynamicPropertySource static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url",MYSQL::getJdbcUrl);registry.add("spring.datasource.username",MYSQL::getUsername);
        registry.add("spring.datasource.password",MYSQL::getPassword);registry.add("file.storage.upload-dir",()->uploads.toString());
        registry.add("jwt.secret",()->UUID.randomUUID()+"-isolated-processing-test");
    }
    @Autowired TestRestTemplate http;@Autowired ObjectMapper mapper;@Autowired JdbcTemplate jdbc;
    @Autowired DataSource dataSource;@Autowired Flyway flyway;@Autowired UserRepository users;
    @Autowired ClientRepository clients;@Autowired ExpertProfileRepository profiles;
    @Autowired AnnouncementRepository announcements;@Autowired BidRepository bids;
    @MockitoSpyBean PaymentRepository payments;
    @Autowired PaymentAttemptRepository attempts;@Autowired PaymentReconciliationJobRepository jobs;
    @Autowired PaymentWebhookEventRepository events;@Autowired PaymentExecutionService execution;
    @Autowired PaymentExecutionPersistenceService persistence;@Autowired PaymentWebhookService webhooks;
    @Autowired PaymentReconciliationService recovery;@Autowired PlatformTransactionManager manager;
    @Autowired EntityManager em;@Autowired JwtUtil jwt;@org.springframework.test.context.bean.override.convention.TestBean(name="paymentClock",methodName="testPaymentClock",enforceOverride=true) Clock clock;
    @Autowired @org.springframework.beans.factory.annotation.Qualifier("requestMappingHandlerMapping") RequestMappingHandlerMapping handlerMapping;
    @MockitoBean GoogleVisionOcrClient vision;@MockitoBean NtsHttpTransport nts;
    @LocalServerPort int port;
    record Fixture(long client,long expert,long payment) { }
    record Reply(int status,JsonNode data) { }
    @BeforeEach void setup() throws Exception {
        stub.reset();((MutableClock)clock).reset();
        try(var c=dataSource.getConnection()) {
            assertThat(c.getCatalog()).isEqualTo("capdi_issue57_portone_test");
            assertThat(c.getMetaData().getURL()).startsWith(MYSQL.getJdbcUrl());
        }
    }
    @AfterEach void noOtherExternalApis(){verifyNoInteractions(vision,nts);}
    Fixture order(PaymentMethod method) throws Exception {
        long[] values=new TransactionTemplate(manager).execute(tx->{
            User client=users.saveAndFlush(User.builder().userType(UserTypeEnum.CLIENT).name("Synthetic Client")
                    .email(UUID.randomUUID()+"@example.test").phone("01000000000").build());
            ClientInfo info=clients.saveAndFlush(ClientInfo.builder().user(client).representativeName("Synthetic Client")
                    .contact("01000000000").email("synthetic@example.test").companyName("Synthetic Company")
                    .businessNumber("1234567891").address("Synthetic Test Address").build());
            User expert=users.saveAndFlush(User.builder().userType(UserTypeEnum.EXPERT).name("Synthetic Expert")
                    .email(UUID.randomUUID()+"@example.test").phone("01000000000").build());
            var profile=profiles.saveAndFlush(ExpertProfile.createDefault(expert,"Synthetic Company"));
            var a=announcements.saveAndFlush(Announcement.builder().user(client).clientInfo(info)
                    .announcementCode("P57-"+UUID.randomUUID()).industry(IndustryTypeEnum.CONSTRUCTION)
                    .purpose(AnnouncementPurposeEnum.REQUIRED_LICENSE).jobType(JobTypeEnum.LICENSE)
                    .businessOwnerType(BusinessOwnerTypeEnum.CORPORATION).capital(new BigDecimal("1000000"))
                    .status(AnnouncementStatusEnum.CLOSED).progressStep(AnnouncementProgressStepEnum.STEP_3_EXPERT_SELECTION).selectionRound(1).build());
            var b=Bid.create(a,expert,new BigDecimal("1000"));b.select(new BigDecimal("1000"));bids.saveAndFlush(b);
            return new long[]{client.getId(),expert.getId(),b.getId(),a.getId()};
        });
        String code=jdbc.queryForObject("SELECT announcement_code FROM announcement WHERE id=?",String.class,values[3]);
        Reply result=send(values[0],UserTypeEnum.CLIENT,HttpMethod.POST,"/announcements/"+code+"/payments",
                Map.of("bid_id",values[2],"method",method.name()),"order");
        assertThat(result.status()).isEqualTo(201);
        return new Fixture(values[0],values[1],result.data().path("payment_id").asLong());
    }
    Reply send(Long user,UserTypeEnum role,HttpMethod method,String route,Object body,String key) throws Exception {
        var headers=new HttpHeaders();headers.setContentType(MediaType.APPLICATION_JSON);
        if(user!=null)headers.setBearerAuth(jwt.generateToken(user,role));if(key!=null)headers.set("Idempotency-Key",key);
        var r=http.exchange("http://127.0.0.1:"+port+"/api"+route,method,new HttpEntity<>(body,headers),String.class);
        return new Reply(r.getStatusCode().value(),mapper.readTree(r.getBody()).path("data"));
    }
    Reply prepare(Fixture f,String key,PaymentMethod method) throws Exception {
        return send(f.client(),UserTypeEnum.CLIENT,HttpMethod.POST,"/payments/"+f.payment()+"/attempts",Map.of("method",method.name()),key);
    }
    long ready(Fixture f) throws Exception {
        Reply r=prepare(f,"attempt",PaymentMethod.CARD);assertThat(r.status()).isEqualTo(200);return r.data().path("attempt_id").asLong();
    }
    Reply confirm(Fixture f,long a,String reference) throws Exception {
        return send(f.client(),UserTypeEnum.CLIENT,HttpMethod.POST,"/payments/"+f.payment()+"/attempts/"+a+"/confirmations",
                Map.of("confirmation_reference",reference),null);
    }
    Reply reconcile(Fixture f,long a) throws Exception {
        return send(f.client(),UserTypeEnum.CLIENT,HttpMethod.POST,"/payments/"+f.payment()+"/attempts/"+a+"/reconciliations",null,null);
    }
    PaymentGateway.Reference ref(long id){return attempts.findById(id).orElseThrow().reference();}
    PaymentWebhookPersistenceService.Receipt receive(long attempt,String event,String hint) {
        var r=ref(attempt);byte[] body=(event+"|"+r.attemptKey()+"|"+r.orderId()+"|"+hint).getBytes(StandardCharsets.UTF_8);
        return webhooks.receive(Map.of("X-Test-Signature",List.of("SYNTHETIC_SIGNATURE")),body);
    }
    long countAttempts(long payment){return jdbc.queryForObject("SELECT COUNT(*) FROM payment_attempts WHERE payment_id=?",Long.class,payment);}


    @Autowired PortOneStub stub;
    static final String WEBHOOK_SECRET="whsec_"+Base64.getEncoder().encodeToString(new byte[32]);
    static class PortOneStub implements com.capdi.backend.domain.payment.gateway.portone.PortOneTransport {
        final Map<String,String> payloads=new ConcurrentHashMap<>();
        final java.util.concurrent.atomic.AtomicInteger calls=new java.util.concurrent.atomic.AtomicInteger();
        void reset(){payloads.clear();calls.set(0);}
        public Reply get(String id,String store,String key,Duration timeout) {
            assertThat(org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            calls.incrementAndGet();String value=payloads.get(id);
            return new Reply(value==null?404:200,value==null?new byte[0]:value.getBytes(StandardCharsets.UTF_8));
        }
    }
    void publish(long attempt,String state,PaymentMethod method) throws Exception {
        var r=ref(attempt);
        stub.payloads.put("capdi_"+r.attemptKey(),mapper.writeValueAsString(Map.of(
                "id","capdi_"+r.attemptKey(),"transactionId","synthetic-"+r.attemptKey(),
                "merchantId","merchant-test","storeId","store-test","version","V2","status",state,"currency","KRW",
                "amount",Map.of("total",1000),"method",Map.of("type",method.name()),"channel",Map.of("type","TEST","key","channel-test"))));
    }
    @ParameterizedTest @EnumSource(PaymentMethod.class)
    void browserParametersAndServerVerificationUseOriginalSnapshot(PaymentMethod method) throws Exception {
        var f=order(method);var prepared=prepare(f,"portone-attempt",method);
        assertThat(prepared.status()).isEqualTo(200);long a=prepared.data().path("attempt_id").asLong();
        assertThat(stub.calls).hasValue(0);
        var checkout=send(f.client(),UserTypeEnum.CLIENT,HttpMethod.GET,"/payments/"+f.payment()+"/attempts/"+a+"/checkout",null,null);
        assertThat(checkout.status()).isEqualTo(200);
        assertThat(checkout.data().path("total_amount").decimalValue()).isEqualByComparingTo("1000");
        assertThat(checkout.data().path("pay_method").asText()).isEqualTo(method.name());
        assertThat(checkout.data().toString()).doesNotContain("SYNTHETIC_API_SECRET","webhook_secret","api_secret");
        String pid=checkout.data().path("payment_id").asText();
        publish(a,method==PaymentMethod.VIRTUAL_ACCOUNT?"VIRTUAL_ACCOUNT_ISSUED":"PAID",method);
        var confirmed=confirm(f,a,pid);assertThat(confirmed.status()).isEqualTo(200);
        assertThat(confirmed.data().path("payment_status").asText()).isEqualTo(method==PaymentMethod.VIRTUAL_ACCOUNT?"WAITING_DEPOSIT":"PAID");
        if(method==PaymentMethod.VIRTUAL_ACCOUNT) {
            publish(a,"PAID",method);assertThat(reconcile(f,a).data().path("payment_status").asText()).isEqualTo("PAID");
        }
        int count=stub.calls.get();assertThat(confirm(f,a,pid).status()).isEqualTo(200);assertThat(stub.calls).hasValue(count);
        assertThat(payments.findById(f.payment()).orElseThrow().getStatus()).isEqualTo(PaymentStatus.PAID);
    }
    @Test void signedInboxAckIsDurableBeforeProviderLookup() throws Exception {
        var f=order(PaymentMethod.CARD);long a=ready(f);publish(a,"PAID",PaymentMethod.CARD);
        String pid="capdi_"+ref(a).attemptKey();
        byte[] body=mapper.writeValueAsBytes(Map.of("type","Transaction.Paid","data",Map.of("storeId","store-test","paymentId",pid)));
        String timestamp=Long.toString(clock.instant().getEpochSecond());
        javax.crypto.Mac mac=javax.crypto.Mac.getInstance("HmacSHA256");
        mac.init(new javax.crypto.spec.SecretKeySpec(new byte[32],"HmacSHA256"));
        mac.update(("event-test."+timestamp+".").getBytes(StandardCharsets.UTF_8));
        String signature="v1,"+Base64.getEncoder().encodeToString(mac.doFinal(body));
        var headers=new HttpHeaders();headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("webhook-id","event-test");headers.set("webhook-timestamp",timestamp);headers.set("webhook-signature",signature);
        var response=http.exchange("http://127.0.0.1:"+port+"/api/webhooks/portone",HttpMethod.POST,new HttpEntity<>(body,headers),String.class);
        assertThat(response.getStatusCode().value()).isEqualTo(200);assertThat(stub.calls).hasValue(0);
        Long event=jdbc.queryForObject("SELECT id FROM payment_webhook_events WHERE attempt_id=?",Long.class,a);
        assertThat(jdbc.queryForObject("SELECT state FROM payment_webhook_events WHERE id=?",String.class,event)).isEqualTo("RECEIVED");
        assertThat(payments.findById(f.payment()).orElseThrow().getStatus()).isEqualTo(PaymentStatus.PROCESSING);
        webhooks.process(event);
        assertThat(payments.findById(f.payment()).orElseThrow().getStatus()).isEqualTo(PaymentStatus.PAID);
        assertThat(stub.calls).hasValue(1);
        assertThat(http.exchange("http://127.0.0.1:"+port+"/api/webhooks/portone",HttpMethod.POST,new HttpEntity<>(body,headers),String.class).getStatusCode().value()).isEqualTo(200);
        webhooks.process(event);assertThat(stub.calls).hasValue(1);
    }
    @Test void unsignedEventCannotReachInbox() throws Exception {
        long before=jdbc.queryForObject("SELECT COUNT(*) FROM payment_webhook_events",Long.class);
        var headers=new HttpHeaders();headers.setContentType(MediaType.APPLICATION_JSON);
        var response=http.exchange("http://127.0.0.1:"+port+"/api/webhooks/portone",HttpMethod.POST,new HttpEntity<>("{}",headers),String.class);
        assertThat(response.getStatusCode().value()).isEqualTo(400);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM payment_webhook_events",Long.class)).isEqualTo(before);
        assertThat(stub.calls).hasValue(0);
    }
}
