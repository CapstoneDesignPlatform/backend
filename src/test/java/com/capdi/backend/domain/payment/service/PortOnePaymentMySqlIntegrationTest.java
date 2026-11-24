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
        // The default conditional gateway can also be registered before the primary test gateway.
        // Keep its fail-closed receiver validation consistent with the synthetic stub settings.
        registry.add("payment.portone.enabled",()->true);
        registry.add("payment.portone.sandbox-approved",()->true);
        registry.add("payment.portone.environment",()->"SANDBOX");
        registry.add("payment.portone.merchant-id",()->"merchant-test");
        registry.add("payment.portone.store-id",()->"store-test");
        registry.add("payment.portone.channel-key",()->"channel-test");
        // Synthetic loopback proxy settings satisfy startup validation; the primary PortOneStub is used and no network request is made.
        registry.add("payment.portone.egress-proxy.host",()->"127.0.0.1");
        registry.add("payment.portone.egress-proxy.port",()->3128);
        registry.add("payment.portone.api-secret",()->"SYNTHETIC_API_SECRET");
        registry.add("payment.portone.webhook-secret",()->WEBHOOK_SECRET);
    }
    @Autowired TestRestTemplate http;@Autowired ObjectMapper mapper;@Autowired JdbcTemplate jdbc;
    @Autowired org.springframework.context.ApplicationContext context;
    @Autowired DataSource dataSource;@Autowired Flyway flyway;@Autowired UserRepository users;
    @Autowired ClientRepository clients;@Autowired ExpertProfileRepository profiles;
    @Autowired AnnouncementRepository announcements;@Autowired BidRepository bids;
    @MockitoSpyBean PaymentRepository payments;
    @Autowired PaymentAttemptRepository attempts;@Autowired PaymentReconciliationJobRepository jobs;
    @Autowired PaymentWebhookEventRepository events;@Autowired PaymentExecutionService execution;
    @Autowired PaymentExecutionPersistenceService persistence;@Autowired PaymentWebhookService webhooks;
    @Autowired PaymentWebhookRetryPolicy webhookRetryPolicy;
    @Autowired PaymentWebhookPersistenceService webhookPersistence;
    @Autowired PaymentReconciliationService recovery;@Autowired PlatformTransactionManager manager;
    @Autowired EntityManager em;@Autowired JwtUtil jwt;@org.springframework.test.context.bean.override.convention.TestBean(name="paymentClock",methodName="testPaymentClock",enforceOverride=true) Clock clock;
    @Autowired @org.springframework.beans.factory.annotation.Qualifier("requestMappingHandlerMapping") RequestMappingHandlerMapping handlerMapping;
    @MockitoBean GoogleVisionOcrClient vision;@MockitoBean NtsHttpTransport nts;
    @LocalServerPort int port;
    record Fixture(long client,long expert,long payment) { }
    record Reply(int status,JsonNode data) { }
    @BeforeEach void setup() throws Exception {
        assertThat(context.getBean(PaymentGateway.class)).isSameAs(context.getBean("portOneGateway"));
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
        try {
            var r=ref(attempt);String type="VIRTUAL_ACCOUNT_ISSUED".equals(hint)?"Transaction.VirtualAccountIssued":"Transaction.Paid";
            byte[] body=mapper.writeValueAsBytes(Map.of("type",type,"data",Map.of("storeId","store-test","paymentId","capdi_"+r.attemptKey())));
            String timestamp=Long.toString(clock.instant().getEpochSecond());var mac=javax.crypto.Mac.getInstance("HmacSHA256");
            mac.init(new javax.crypto.spec.SecretKeySpec(new byte[32],"HmacSHA256"));
            mac.update((event+"."+timestamp+".").getBytes(StandardCharsets.UTF_8));
            String signature="v1,"+Base64.getEncoder().encodeToString(mac.doFinal(body));
            return webhooks.receive(Map.of("webhook-id",List.of(event),"webhook-timestamp",List.of(timestamp),
                    "webhook-signature",List.of(signature)),body);
        } catch(Exception e) {throw new AssertionError("Synthetic webhook fixture failed",e);}
    }
    long countAttempts(long payment){return jdbc.queryForObject("SELECT COUNT(*) FROM payment_attempts WHERE payment_id=?",Long.class,payment);}


    @Autowired PortOneStub stub;
    static final String WEBHOOK_SECRET="whsec_"+Base64.getEncoder().encodeToString(new byte[32]);
    static class PortOneStub implements com.capdi.backend.domain.payment.gateway.portone.PortOneTransport {
        final Map<String,String> payloads=new ConcurrentHashMap<>();
        final Set<String> timeouts=ConcurrentHashMap.newKeySet();
        final java.util.concurrent.atomic.AtomicInteger calls=new java.util.concurrent.atomic.AtomicInteger();
        void reset(){payloads.clear();timeouts.clear();calls.set(0);}
        public Reply get(String id,String store,String key,Duration timeout) {
            assertThat(org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            calls.incrementAndGet();if(timeouts.contains(id))throw new PaymentGateway.GatewayException(PaymentProcessingFailure.TIMEOUT);
            String value=payloads.get(id);
            return new Reply(value==null?404:200,value==null?new byte[0]:value.getBytes(StandardCharsets.UTF_8));
        }
    }
    void publish(long attempt,String state,PaymentMethod method) throws Exception {
        var r=ref(attempt);
        String portOneMethod=switch(method){case CARD->"PaymentMethodCard";case TRANSFER->"PaymentMethodTransfer";case VIRTUAL_ACCOUNT->"PaymentMethodVirtualAccount";};
        stub.payloads.put("capdi_"+r.attemptKey(),mapper.writeValueAsString(Map.of(
                "id","capdi_"+r.attemptKey(),"transactionId","synthetic-"+r.attemptKey(),
                "merchantId","merchant-test","storeId","store-test","version","V2","status",state,"currency","KRW",
                "amount",Map.of("total",1000),"method",Map.of("type",portOneMethod),"channel",Map.of("type","TEST","key","channel-test"))));
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
        var persisted=attempts.findById(a).orElseThrow();
        assertThat(persisted.reference().identity().providerAccountId()).isEqualTo("provider:PORTONE_V2:store:store-test");
        assertThat(persisted.getMerchantId()).isEqualTo("merchant-test");
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
        assertThat(jdbc.queryForObject("SELECT provider_account_id FROM payment_webhook_events WHERE id=?",String.class,event))
                .isEqualTo("provider:PORTONE_V2:store:store-test");
        assertThat(jdbc.queryForObject("SELECT merchant_id FROM payment_webhook_events WHERE id=?",String.class,event))
                .isEqualTo("merchant-test");
        assertThat(payments.findById(f.payment()).orElseThrow().getStatus()).isEqualTo(PaymentStatus.PROCESSING);
        webhooks.process(event);
        assertThat(payments.findById(f.payment()).orElseThrow().getStatus()).isEqualTo(PaymentStatus.PAID);
        assertThat(stub.calls).hasValue(1);
        assertThat(http.exchange("http://127.0.0.1:"+port+"/api/webhooks/portone",HttpMethod.POST,new HttpEntity<>(body,headers),String.class).getStatusCode().value()).isEqualTo(200);
        webhooks.process(event);assertThat(stub.calls).hasValue(1);
    }
    @Test void springSchedulerProcessesMixedBatchAndIsolatesTransientAndPermanentFailures() throws Exception {
        var successA=order(PaymentMethod.CARD);long attemptA=ready(successA);publish(attemptA,"PAID",PaymentMethod.CARD);
        var transientFixture=order(PaymentMethod.CARD);long attemptTransient=ready(transientFixture);
        stub.timeouts.add("capdi_"+ref(attemptTransient).attemptKey()); // synthetic timeout, no external request
        var successC=order(PaymentMethod.CARD);long attemptC=ready(successC);publish(attemptC,"PAID",PaymentMethod.CARD);
        var permanent=order(PaymentMethod.CARD);long attemptPermanent=ready(permanent);publish(attemptPermanent,"PAID",PaymentMethod.CARD);
        String permanentPaymentId="capdi_"+ref(attemptPermanent).attemptKey();
        stub.payloads.computeIfPresent(permanentPaymentId,(key,value)->value.replace("\"total\":1000","\"total\":2000"));
        var receiptA=receive(attemptA,"scheduled-success-a","PAID");
        var receiptB=receive(attemptTransient,"scheduled-transient-b","PAID");
        var receiptC=receive(attemptC,"scheduled-success-c","PAID");
        var receiptD=receive(attemptPermanent,"scheduled-permanent-d","PAID");
        try(var scheduled=new org.springframework.context.annotation.AnnotationConfigApplicationContext()) {
            scheduled.getEnvironment().getPropertySources().addFirst(new org.springframework.core.env.MapPropertySource(
                    "webhook-scheduler-test",Map.of("payment.webhook.consumer.enabled","true",
                    "payment.webhook.consumer.delay-ms","40","payment.webhook.consumer.batch-size","5")));
            scheduled.getBeanFactory().registerSingleton("paymentWebhookEventRepository",events);
            scheduled.getBeanFactory().registerSingleton("paymentWebhookService",webhooks);
            scheduled.getBeanFactory().registerSingleton("paymentClock",clock);
            scheduled.register(com.capdi.backend.domain.payment.config.PaymentWebhookConsumerConfig.class,
                    PaymentWebhookConsumer.class,com.capdi.backend.domain.payment.config.FinancialLookupSchedulerConfig.class,
                    com.capdi.backend.domain.payment.config.InternalFinancialRecoveryConfig.class);
            scheduled.refresh();
            assertThat(scheduled.getBeansOfType(PaymentWebhookConsumer.class)).hasSize(1);
            assertThat(scheduled.getBeansOfType(com.capdi.backend.domain.payment.config.FinancialLookupSchedulerConfig.LookupOnlyScheduler.class)).isEmpty();
            assertThat(scheduled.getBeansOfType(com.capdi.backend.domain.payment.config.InternalFinancialRecoveryConfig.LookupWorker.class)).isEmpty();
            long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(5);
            while(System.nanoTime()<deadline) {
                String aState=jdbc.queryForObject("SELECT state FROM payment_webhook_events WHERE id=?",String.class,receiptA.id());
                String bFailure=jdbc.queryForObject("SELECT last_failure FROM payment_webhook_events WHERE id=?",String.class,receiptB.id());
                String cState=jdbc.queryForObject("SELECT state FROM payment_webhook_events WHERE id=?",String.class,receiptC.id());
                Boolean dDead=jdbc.queryForObject("SELECT dead_letter FROM payment_webhook_events WHERE id=?",Boolean.class,receiptD.id());
                if("PROCESSED".equals(aState) && "TIMEOUT".equals(bFailure) && "PROCESSED".equals(cState) && dDead)break;
                Thread.sleep(10);
            }
            assertThat(jdbc.queryForObject("SELECT state FROM payment_webhook_events WHERE id=?",String.class,receiptA.id())).isEqualTo("PROCESSED");
            assertThat(jdbc.queryForObject("SELECT state FROM payment_webhook_events WHERE id=?",String.class,receiptC.id())).isEqualTo("PROCESSED");
            assertThat(jdbc.queryForObject("SELECT attempt_count FROM payment_webhook_events WHERE id=?",Integer.class,receiptB.id())).isEqualTo(1);
            assertThat(jdbc.queryForObject("SELECT dead_letter FROM payment_webhook_events WHERE id=?",Boolean.class,receiptB.id())).isFalse();
            assertThat(jdbc.queryForObject("SELECT last_failure FROM payment_webhook_events WHERE id=?",String.class,receiptB.id())).isEqualTo("TIMEOUT");
            assertThat(jdbc.queryForObject("SELECT next_attempt_at FROM payment_webhook_events WHERE id=?",
                    (org.springframework.jdbc.core.RowMapper<LocalDateTime>)(rs,n)->rs.getObject(1,LocalDateTime.class),receiptB.id()))
                    .isEqualTo(LocalDateTime.ofInstant(clock.instant().plusSeconds(30),ZoneOffset.UTC));
            assertThat(jdbc.queryForObject("SELECT dead_letter FROM payment_webhook_events WHERE id=?",Boolean.class,receiptD.id())).isTrue();
            assertThat(jdbc.queryForObject("SELECT last_failure FROM payment_webhook_events WHERE id=?",String.class,receiptD.id())).isEqualTo("RESPONSE_MISMATCH");
            assertThat(payments.findById(successA.payment()).orElseThrow().getStatus()).isEqualTo(PaymentStatus.PAID);
            assertThat(payments.findById(successC.payment()).orElseThrow().getStatus()).isEqualTo(PaymentStatus.PAID);
            assertThat(payments.findById(transientFixture.payment()).orElseThrow().getStatus()).isNotEqualTo(PaymentStatus.PAID);
            assertThat(payments.findById(permanent.payment()).orElseThrow().getStatus()).isNotEqualTo(PaymentStatus.PAID);
            Thread.sleep(100); // allow another scheduled poll; completed inbox rows must not be selected again
            assertThat(stub.calls).hasValue(4);
            assertThat(jdbc.queryForObject("SELECT attempt_count FROM payment_webhook_events WHERE id=?",Integer.class,receiptA.id())).isEqualTo(1);
            assertThat(jdbc.queryForObject("SELECT attempt_count FROM payment_webhook_events WHERE id=?",Integer.class,receiptB.id())).isEqualTo(1);
        }
    }
    @Test void webhookRetryMetadataPersistsBackoffAndDuplicateReceiptDoesNotResetIt() throws Exception {
        var f=order(PaymentMethod.CARD);long a=ready(f);var receipt=receive(a,"retry-event","PAID");
        assertThat(jdbc.queryForObject("SELECT attempt_count FROM payment_webhook_events WHERE id=?",Integer.class,receipt.id())).isZero();
        webhooks.process(receipt.id());
        assertThat(jdbc.queryForObject("SELECT attempt_count FROM payment_webhook_events WHERE id=?",Integer.class,receipt.id())).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT last_failure FROM payment_webhook_events WHERE id=?",String.class,receipt.id())).isEqualTo("PROVIDER_ERROR");
        LocalDateTime due=jdbc.queryForObject("SELECT next_attempt_at FROM payment_webhook_events WHERE id=?",
                (org.springframework.jdbc.core.RowMapper<LocalDateTime>)(rs,n)->rs.getObject(1,LocalDateTime.class),receipt.id());
        assertThat(due).isEqualTo(LocalDateTime.ofInstant(clock.instant().plusSeconds(30),ZoneOffset.UTC));
        assertThat(events.findDueIds(clock.instant(),100)).doesNotContain(receipt.id());
        assertThat(webhookPersistence.claim(receipt.id()).run()).isFalse();
        assertThat(jdbc.queryForObject("SELECT attempt_count FROM payment_webhook_events WHERE id=?",Integer.class,receipt.id())).isEqualTo(1);
        var duplicate=receive(a,"retry-event","PAID");assertThat(duplicate.duplicate()).isTrue();
        assertThat(jdbc.queryForObject("SELECT attempt_count FROM payment_webhook_events WHERE id=?",Integer.class,receipt.id())).isEqualTo(1);
        LocalDateTime duplicateDue=jdbc.queryForObject("SELECT next_attempt_at FROM payment_webhook_events WHERE id=?",
                (org.springframework.jdbc.core.RowMapper<LocalDateTime>)(rs,n)->rs.getObject(1,LocalDateTime.class),receipt.id());
        assertThat(duplicateDue).isEqualTo(due);
        ((MutableClock)clock).advance(30);assertThat(events.findDueIds(clock.instant(),100)).contains(receipt.id());
        webhooks.process(receipt.id());assertThat(jdbc.queryForObject("SELECT attempt_count FROM payment_webhook_events WHERE id=?",Integer.class,receipt.id())).isEqualTo(2);
        LocalDateTime secondDue=jdbc.queryForObject("SELECT next_attempt_at FROM payment_webhook_events WHERE id=?",
                (org.springframework.jdbc.core.RowMapper<LocalDateTime>)(rs,n)->rs.getObject(1,LocalDateTime.class),receipt.id());
        assertThat(secondDue).isEqualTo(LocalDateTime.ofInstant(clock.instant().plusSeconds(60),ZoneOffset.UTC));
        ((MutableClock)clock).advance(60);
        publish(a,"PAID",PaymentMethod.CARD);webhooks.process(receipt.id());
        assertThat(jdbc.queryForObject("SELECT state FROM payment_webhook_events WHERE id=?",String.class,receipt.id())).isEqualTo("PROCESSED");
        assertThat(jdbc.queryForObject("SELECT attempt_count FROM payment_webhook_events WHERE id=?",Integer.class,receipt.id())).isEqualTo(3);
        assertThat(jdbc.queryForObject("SELECT last_failure FROM payment_webhook_events WHERE id=?",String.class,receipt.id())).isNull();
    }
    @Test void virtualAccountIssuedWebhookIsProcessedAsWaitingDepositUntilPaidEvent() throws Exception {
        var f=order(PaymentMethod.VIRTUAL_ACCOUNT);var prepared=prepare(f,"vbank-attempt",PaymentMethod.VIRTUAL_ACCOUNT);
        assertThat(prepared.status()).isEqualTo(200);long a=prepared.data().path("attempt_id").asLong();
        publish(a,"VIRTUAL_ACCOUNT_ISSUED",PaymentMethod.VIRTUAL_ACCOUNT);
        var issued=receive(a,"vbank-issued","VIRTUAL_ACCOUNT_ISSUED");webhooks.process(issued.id());
        assertThat(payments.findById(f.payment()).orElseThrow().getStatus()).isEqualTo(PaymentStatus.WAITING_DEPOSIT);
        assertThat(events.findById(issued.id()).orElseThrow().getState()).isEqualTo(PaymentWebhookEvent.State.PROCESSED);
        publish(a,"PAID",PaymentMethod.VIRTUAL_ACCOUNT);
        var paid=receive(a,"vbank-paid","PAID");webhooks.process(paid.id());
        assertThat(payments.findById(f.payment()).orElseThrow().getStatus()).isEqualTo(PaymentStatus.PAID);
        assertThat(events.findById(paid.id()).orElseThrow().getState()).isEqualTo(PaymentWebhookEvent.State.PROCESSED);
    }

    @Test void outOfOrderVirtualAccountIssueCannotDowngradePaidPayment() throws Exception {
        var f=order(PaymentMethod.VIRTUAL_ACCOUNT);var prepared=prepare(f,"vbank-ordering",PaymentMethod.VIRTUAL_ACCOUNT);
        long a=prepared.data().path("attempt_id").asLong();

        publish(a,"PAID",PaymentMethod.VIRTUAL_ACCOUNT);
        var paid=receive(a,"vbank-paid-first","PAID");webhooks.process(paid.id());
        assertThat(payments.findById(f.payment()).orElseThrow().getStatus()).isEqualTo(PaymentStatus.PAID);

        // A delayed issue event is only a lookup trigger. Its stale provider snapshot cannot rewind PAID.
        publish(a,"VIRTUAL_ACCOUNT_ISSUED",PaymentMethod.VIRTUAL_ACCOUNT);
        var issued=receive(a,"vbank-issued-late","VIRTUAL_ACCOUNT_ISSUED");webhooks.process(issued.id());

        assertThat(payments.findById(f.payment()).orElseThrow().getStatus()).isEqualTo(PaymentStatus.PAID);
        assertThat(attempts.findById(a).orElseThrow().getStatus()).isEqualTo(PaymentAttemptStatus.SUCCEEDED);
        assertThat(events.findById(issued.id()).orElseThrow().isDeadLetter()).isTrue();
    }
    @Test void maximumTransientAttemptsBecomeDurableDeadLetter() throws Exception {
        var f=order(PaymentMethod.CARD);long a=ready(f);var receipt=receive(a,"retry-limit","PAID");
        for(int attempt=1;attempt<=webhookRetryPolicy.maxAttempts();attempt++) {
            webhooks.process(receipt.id());
            if(attempt<webhookRetryPolicy.maxAttempts())
                ((MutableClock)clock).advance(webhookRetryPolicy.delayAfterAttempt(attempt).toSeconds());
        }
        assertThat(jdbc.queryForObject("SELECT attempt_count FROM payment_webhook_events WHERE id=?",Integer.class,receipt.id()))
                .isEqualTo(webhookRetryPolicy.maxAttempts());
        assertThat(jdbc.queryForObject("SELECT dead_letter FROM payment_webhook_events WHERE id=?",Boolean.class,receipt.id())).isTrue();
        assertThat(jdbc.queryForObject("SELECT last_failure FROM payment_webhook_events WHERE id=?",String.class,receipt.id())).isEqualTo("PROVIDER_ERROR");
        em.clear();var persistedDeadLetter=events.findById(receipt.id()).orElseThrow();
        assertThat(persistedDeadLetter.isDeadLetter()).isTrue();assertThat(persistedDeadLetter.getAttemptCount()).isEqualTo(webhookRetryPolicy.maxAttempts());
        assertThat(events.findDueIds(clock.instant().plus(Duration.ofDays(1)),100)).doesNotContain(receipt.id());
        int calls=stub.calls.get();webhooks.process(receipt.id());assertThat(stub.calls).hasValue(calls);
    }
    @Test void providerIdentityMismatchDeadLettersWithoutApplyingWebhookPayload() throws Exception {
        var f=order(PaymentMethod.CARD);long a=ready(f);publish(a,"PAID",PaymentMethod.CARD);
        var reference=ref(a);String paymentId="capdi_"+reference.attemptKey();
        stub.payloads.computeIfPresent(paymentId,(key,value)->value.replace("\"total\":1000","\"total\":2000"));
        var receipt=receive(a,"mismatch-event","PAID");webhooks.process(receipt.id());
        assertThat(jdbc.queryForObject("SELECT dead_letter FROM payment_webhook_events WHERE id=?",Boolean.class,receipt.id())).isTrue();
        assertThat(jdbc.queryForObject("SELECT last_failure FROM payment_webhook_events WHERE id=?",String.class,receipt.id())).isEqualTo("RESPONSE_MISMATCH");
        assertThat(payments.findById(f.payment()).orElseThrow().getStatus()).isNotEqualTo(PaymentStatus.PAID);
    }
    @Test void mysqlLeaseSerializesWorkersAndRejectsExpiredWorkersStaleFinish() throws Exception {
        var f=order(PaymentMethod.CARD);long a=ready(f);var receipt=receive(a,"lease-race","PAID");
        var barrier=new CyclicBarrier(2);var pool=Executors.newFixedThreadPool(2);
        try {
            var first=pool.submit(()->{var due=events.findDueIds(clock.instant(),1000);assertThat(due).contains(receipt.id());
                barrier.await(10,TimeUnit.SECONDS);return webhookPersistence.claim(receipt.id());});
            var second=pool.submit(()->{var due=events.findDueIds(clock.instant(),1000);assertThat(due).contains(receipt.id());
                barrier.await(10,TimeUnit.SECONDS);return webhookPersistence.claim(receipt.id());});
            var c1=first.get(10,TimeUnit.SECONDS);var c2=second.get(10,TimeUnit.SECONDS);
            assertThat(List.of(c1.run(),c2.run())).containsExactlyInAnyOrder(true,false);
            var owner=c1.run()?c1:c2;assertThat(owner.attemptCount()).isEqualTo(1);
            em.clear();
            var afterFirstClaim=events.findById(receipt.id()).orElseThrow();
            assertThat(afterFirstClaim.getAttemptCount()).isEqualTo(1);
            assertThat(afterFirstClaim.getState()).isEqualTo(PaymentWebhookEvent.State.PROCESSING);
            assertThat(afterFirstClaim.getLeaseUntil()).isEqualTo(clock.instant().plus(webhookRetryPolicy.lease()));
            ((MutableClock)clock).advance(webhookRetryPolicy.lease().toSeconds()+1);
            em.clear(); // simulate a newly started worker/persistence context after process loss
            var recovered=webhookPersistence.claim(receipt.id());assertThat(recovered.run()).isTrue();
            assertThat(recovered.attemptCount()).isEqualTo(2);
            assertThat(webhookPersistence.finish(owner,PaymentWebhookEvent.Outcome.SUCCESS,null)).isFalse();
            assertThat(webhookPersistence.finish(recovered,PaymentWebhookEvent.Outcome.SUCCESS,null)).isTrue();
            assertThat(events.findById(receipt.id()).orElseThrow().getState()).isEqualTo(PaymentWebhookEvent.State.PROCESSED);
            assertThat(stub.calls).hasValue(0);
        } finally {pool.shutdownNow();}
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
