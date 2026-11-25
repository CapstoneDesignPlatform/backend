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
@Import(PaymentProcessingMySqlIntegrationTest.FakeConfig.class)
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,properties={
        "spring.config.location=classpath:/application.yml","spring.profiles.active=issue57-processing-test",
        "server.address=127.0.0.1","spring.datasource.driver-class-name=com.mysql.cj.jdbc.Driver",
        "spring.flyway.enabled=true","spring.flyway.baseline-on-migrate=false","spring.flyway.validate-on-migrate=true",
        "spring.jpa.hibernate.ddl-auto=validate","spring.jpa.show-sql=false","logging.level.org.hibernate.SQL=OFF",
        "nts.business.enabled=false"})
class PaymentProcessingMySqlIntegrationTest {
    @TestConfiguration static class FakeConfig {
        @Bean @Primary FakePaymentGateway fakeGateway(){return new FakePaymentGateway();}
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
            .withDatabaseName("capdi_issue57_processing_test").withUsername("processing57_test")
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
    @Autowired EntityManager em;@Autowired JwtUtil jwt;@Autowired FakePaymentGateway fake;@org.springframework.test.context.bean.override.convention.TestBean(name="paymentClock",methodName="testPaymentClock",enforceOverride=true) Clock clock;
    @Autowired @org.springframework.beans.factory.annotation.Qualifier("requestMappingHandlerMapping") RequestMappingHandlerMapping handlerMapping;
    @MockitoBean GoogleVisionOcrClient vision;@MockitoBean NtsHttpTransport nts;
    @LocalServerPort int port;
    record Fixture(long client,long expert,long payment) { }
    record Reply(int status,JsonNode data) { }
    @BeforeEach void setup() throws Exception {
        fake.reset();((MutableClock)clock).reset();
        try(var c=dataSource.getConnection()) {
            assertThat(c.getCatalog()).isEqualTo("capdi_issue57_processing_test");
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
    String progress(Fixture f) {
        return jdbc.queryForObject("SELECT a.progress_step FROM announcement a JOIN payments p ON p.announcement_id=a.id WHERE p.id=?",
                String.class,f.payment());
    }
    void progress(Fixture f,String step) {
        jdbc.update("UPDATE announcement SET progress_step=? WHERE id=(SELECT announcement_id FROM payments WHERE id=?)",step,f.payment());
    }

    @Test void orderAndPreparationStayAtStepThreeUntilVerifiedPayment() throws Exception {
        Fixture f=order(PaymentMethod.CARD);
        assertThat(progress(f)).isEqualTo("STEP_3_EXPERT_SELECTION");
        long a=ready(f);
        assertThat(progress(f)).isEqualTo("STEP_3_EXPERT_SELECTION");
        assertThat(confirm(f,a,"start-diagnosis").data().path("payment_status").asText()).isEqualTo("PAID");
        assertThat(progress(f)).isEqualTo("STEP_4_DIAGNOSIS_STARTED");
        assertThat(confirm(f,a,"start-diagnosis").status()).isEqualTo(200);
        assertThat(progress(f)).isEqualTo("STEP_4_DIAGNOSIS_STARTED");
    }

    @ParameterizedTest @ValueSource(strings={"STEP_4_DIAGNOSIS_STARTED","STEP_5_ASSOCIATION_REVIEW",
            "STEP_6_REPORT_SENT","STEP_7_COMPLETED"})
    void repeatedPaidLookupNeverRewindsProgress(String step) throws Exception {
        Fixture f=order(PaymentMethod.CARD);long a=ready(f);
        assertThat(confirm(f,a,"paid").data().path("payment_status").asText()).isEqualTo("PAID");
        progress(f,step);
        assertThat(reconcile(f,a).data().path("payment_status").asText()).isEqualTo("PAID");
        assertThat(progress(f)).isEqualTo(step);
    }

    @Test void existingLegacyStepFourOrderCanStillComplete() throws Exception {
        Fixture f=order(PaymentMethod.CARD);progress(f,"STEP_4_DIAGNOSIS_STARTED");long a=ready(f);
        assertThat(confirm(f,a,"legacy").data().path("payment_status").asText()).isEqualTo("PAID");
        assertThat(progress(f)).isEqualTo("STEP_4_DIAGNOSIS_STARTED");
    }

    @ParameterizedTest @ValueSource(strings={"rejected","amount","cancelled","owner","expert","multiple","step","round"})
    void changedSelectionCannotAdvanceAnnouncement(String change) throws Exception {
        Fixture f=order(PaymentMethod.CARD);long a=ready(f);
        Payment p=payments.findById(f.payment()).orElseThrow();
        switch(change) {
            case "rejected" -> jdbc.update("UPDATE bids SET status='REJECTED' WHERE id=?",p.getBidId());
            case "amount" -> jdbc.update("UPDATE bids SET final_amount=2000 WHERE id=?",p.getBidId());
            case "cancelled" -> jdbc.update("UPDATE announcement SET status='CANCELLED' WHERE id=?",p.getAnnouncementId());
            case "owner" -> jdbc.update("UPDATE announcement SET user_id=NULL WHERE id=?",p.getAnnouncementId());
            case "expert" -> jdbc.update("UPDATE bids SET expert_user_id=? WHERE id=?",f.client(),p.getBidId());
            case "multiple" -> new TransactionTemplate(manager).execute(tx->{
                Bid other=Bid.create(announcements.findById(p.getAnnouncementId()).orElseThrow(),
                        users.findById(f.expert()).orElseThrow(),new BigDecimal("1000"));
                other.select(new BigDecimal("1000"));return bids.saveAndFlush(other);
            });
            case "step" -> progress(f,"STEP_2_BID_CLOSED");
            case "round" -> jdbc.update("UPDATE announcement SET selection_round=selection_round+1 WHERE id=?",p.getAnnouncementId());
        }
        String before=progress(f);
        var reply=confirm(f,a,"changed-source");
        assertThat(reply.data().path("failure").asText()).isEqualTo("INCONSISTENT_STATE");
        assertThat(payments.findById(f.payment()).orElseThrow().getStatus()).isEqualTo(PaymentStatus.UNKNOWN);
        assertThat(progress(f)).isEqualTo(before);
    }

    @Test void preparePersistsOnlyPreparedAndDoesNotPay() throws Exception {
        Fixture f=order(PaymentMethod.CARD);long a=ready(f);
        assertThat(attempts.findById(a).orElseThrow().getStatus()).isEqualTo(PaymentAttemptStatus.PREPARED);
        assertThat(payments.findById(f.payment()).orElseThrow().getStatus()).isEqualTo(PaymentStatus.PROCESSING);
        assertThat(fake.prepares).hasValue(1);assertThat(fake.confirms).hasValue(0);
        assertThat(ref(a).amount()).isEqualByComparingTo("1000");assertThat(ref(a).identity().environment()).isEqualTo(PaymentEnvironment.TEST);
    }
    @Test void sameRequestReplaysAndDifferentKeyIsBlocked() throws Exception {
        Fixture f=order(PaymentMethod.CARD);long a=ready(f);
        assertThat(prepare(f,"attempt",PaymentMethod.CARD).data().path("attempt_id").asLong()).isEqualTo(a);
        assertThat(prepare(f,"another",PaymentMethod.CARD).status()).isEqualTo(409);
        assertThat(countAttempts(f.payment())).isEqualTo(1);assertThat(fake.prepares).hasValue(1);
    }
    @Test void authoritativeFakeConfirmationPaysExactlyOnce() throws Exception {
        Fixture f=order(PaymentMethod.CARD);long a=ready(f);
        assertThat(confirm(f,a,"synthetic-confirm").data().path("payment_status").asText()).isEqualTo("PAID");
        Instant paid=payments.findById(f.payment()).orElseThrow().getPaidAt();
        assertThat(confirm(f,a,"synthetic-confirm").status()).isEqualTo(200);
        assertThat(confirm(f,a,"different-reference").status()).isEqualTo(409);
        assertThat(prepare(f,"new-attempt",PaymentMethod.CARD).status()).isEqualTo(409);
        assertThat(fake.confirms).hasValue(1);assertThat(payments.findById(f.payment()).orElseThrow().getPaidAt()).isEqualTo(paid);
    }
    @ParameterizedTest @ValueSource(strings={"amount","currency","order","merchant","environment","provider","unverified","missing_tx"})
    void changedGatewayProofCannotMarkPaid(String field) throws Exception {
        Fixture f=order(PaymentMethod.CARD);long a=ready(f);
        fake.confirmFunction=r->{
            var id=r.identity();String order=r.orderId(),currency=r.currency(),tx="synthetic-tx";
            var amount=r.amount();boolean authoritative=true;
            switch(field) {
                case "amount" -> amount=amount.add(BigDecimal.ONE);
                case "currency" -> currency="USD";
                case "order" -> order="synthetic-other";
                case "merchant" -> id=new PaymentGateway.Identity(id.code(),"other",id.environment());
                case "environment" -> id=new PaymentGateway.Identity(id.code(),id.merchant(),PaymentEnvironment.PRODUCTION);
                case "provider" -> id=new PaymentGateway.Identity("OTHER",id.merchant(),id.environment());
                case "unverified" -> authoritative=false;
                case "missing_tx" -> tx=null;
            }
            return new PaymentGateway.Observation(id,order,amount,currency,tx,"PAID",PaymentGateway.State.PAID,authoritative);
        };
        Reply r=confirm(f,a,"synthetic-confirm");
        assertThat(r.data().path("payment_status").asText()).isEqualTo("UNKNOWN");
        assertThat(r.data().path("failure").asText()).isEqualTo("RESPONSE_MISMATCH");
        assertThat(payments.findById(f.payment()).orElseThrow().getPaidAt()).isNull();
        assertThat(progress(f)).isEqualTo("STEP_3_EXPERT_SELECTION");
    }
    @Test void responseMismatchIsRecoveredByExplicitLookupWithoutRepeatingConfirmation() throws Exception {
        Fixture f=order(PaymentMethod.CARD);long a=ready(f);
        fake.confirmFunction=r->new PaymentGateway.Observation(r.identity(),r.orderId(),r.amount(),"USD",
                "synthetic-tx-"+r.attemptKey(),"PAID",PaymentGateway.State.PAID,true);
        Reply mismatch=confirm(f,a,"browser-reference");
        assertThat(mismatch.data().path("payment_status").asText()).isEqualTo("UNKNOWN");
        assertThat(mismatch.data().path("failure").asText()).isEqualTo("RESPONSE_MISMATCH");
        assertThat(payments.findById(f.payment()).orElseThrow().getStatus()).isEqualTo(PaymentStatus.UNKNOWN);
        assertThat(fake.confirms).hasValue(1);

        // Same confirmation is an idempotent replay and must not make another provider call.
        assertThat(confirm(f,a,"browser-reference").data().path("status").asText()).isEqualTo("UNKNOWN");
        assertThat(fake.confirms).hasValue(1);
        assertThat(fake.fetches).hasValue(0);

        fake.fetchFunction=r->new PaymentGateway.Observation(r.identity(),r.orderId(),r.amount(),r.currency(),
                "synthetic-tx-"+r.attemptKey(),"PAID",PaymentGateway.State.PAID,true);
        Reply recovered=reconcile(f,a);
        assertThat(recovered.data().path("payment_status").asText()).isEqualTo("PAID");
        assertThat(recovered.data().path("status").asText()).isEqualTo("SUCCEEDED");
        assertThat(recovered.data().path("failure").asText()).isEqualTo("NONE");
        assertThat(payments.findById(f.payment()).orElseThrow().getStatus()).isEqualTo(PaymentStatus.PAID);
        assertThat(attempts.findById(a).orElseThrow().getStatus()).isEqualTo(PaymentAttemptStatus.SUCCEEDED);
        assertThat(fake.confirms).hasValue(1);
        assertThat(fake.fetches).hasValue(1);
    }
    @Test void duplicateProviderTransactionIsNotAssignedToTwoPayments() throws Exception {
        Fixture one=order(PaymentMethod.CARD),two=order(PaymentMethod.CARD);long a=ready(one),b=ready(two);
        fake.confirmFunction=r->new PaymentGateway.Observation(r.identity(),r.orderId(),r.amount(),r.currency(),
                "synthetic-shared-tx","PAID",PaymentGateway.State.PAID,true);
        assertThat(confirm(one,a,"confirm-one").data().path("payment_status").asText()).isEqualTo("PAID");
        assertThat(confirm(two,b,"confirm-two").data().path("failure").asText()).isEqualTo("TRANSACTION_CONFLICT");
        assertThat(payments.findById(two.payment()).orElseThrow().getStatus()).isEqualTo(PaymentStatus.UNKNOWN);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM payment_attempts WHERE provider_transaction_id='synthetic-shared-tx'",Long.class)).isEqualTo(1);
    }
    @ParameterizedTest @EnumSource(value=PaymentProcessingFailure.class,names={"TIMEOUT","NETWORK","PROVIDER_ERROR"})
    void ambiguousExternalFailureBlocksNewCharge(PaymentProcessingFailure failure) throws Exception {
        Fixture f=order(PaymentMethod.CARD);long a=ready(f);
        fake.confirmFunction=r->{throw new PaymentGateway.GatewayException(failure);};
        assertThat(confirm(f,a,"uncertain").data().path("status").asText()).isEqualTo("UNKNOWN");
        assertThat(prepare(f,"another",PaymentMethod.CARD).status()).isEqualTo(409);
        assertThat(confirm(f,a,"uncertain").data().path("status").asText()).isEqualTo("UNKNOWN");
        assertThat(fake.confirms).hasValue(1);assertThat(countAttempts(f.payment())).isEqualTo(1);
    }
    @Test void timeoutAfterRemoteSuccessIsRecoveredByFetchOnly() throws Exception {
        Fixture f=order(PaymentMethod.CARD);long a=ready(f);
        fake.confirmFunction=r->{fake.publish(r,PaymentGateway.State.PAID);throw new PaymentGateway.GatewayException(PaymentProcessingFailure.TIMEOUT);};
        assertThat(confirm(f,a,"timeout").data().path("payment_status").asText()).isEqualTo("UNKNOWN");
        assertThat(progress(f)).isEqualTo("STEP_3_EXPERT_SELECTION");
        assertThat(reconcile(f,a).data().path("payment_status").asText()).isEqualTo("PAID");
        assertThat(progress(f)).isEqualTo("STEP_4_DIAGNOSIS_STARTED");
        assertThat(fake.confirms).hasValue(1);assertThat(fake.fetches).hasValue(1);
    }
    @Test void remoteSuccessThenDatabaseFailureIsRecoveredWithoutSecondConfirmation() throws Exception {
        Fixture f=order(PaymentMethod.CARD);long a=ready(f);
        doThrow(new IllegalStateException("SYNTHETIC_COMPLETION_STORAGE_FAILURE")).when(payments).flush();
        Reply uncertain=confirm(f,a,"storage");
        assertThat(uncertain.data().path("failure").asText()).isEqualTo("STORAGE_FAILURE");
        assertThat(payments.findById(f.payment()).orElseThrow().getStatus()).isEqualTo(PaymentStatus.UNKNOWN);
        assertThat(progress(f)).isEqualTo("STEP_3_EXPERT_SELECTION");
        reset(payments);
        assertThat(reconcile(f,a).data().path("payment_status").asText()).isEqualTo("PAID");
        assertThat(progress(f)).isEqualTo("STEP_4_DIAGNOSIS_STARTED");
        assertThat(fake.confirms).hasValue(1);
    }
    @Test void virtualAccountIssuanceNeedsActualDepositProof() throws Exception {
        Fixture f=order(PaymentMethod.VIRTUAL_ACCOUNT);
        fake.prepareFunction=r->fake.publish(r,PaymentGateway.State.WAITING_DEPOSIT);
        Reply first=prepare(f,"vbank",PaymentMethod.VIRTUAL_ACCOUNT);long a=first.data().path("attempt_id").asLong();
        assertThat(first.data().path("payment_status").asText()).isEqualTo("WAITING_DEPOSIT");
        assertThat(progress(f)).isEqualTo("STEP_3_EXPERT_SELECTION");
        assertThat(payments.findById(f.payment()).orElseThrow().getPaidAt()).isNull();
        fake.publish(ref(a),PaymentGateway.State.PAID);var receipt=receive(a,"deposit","CLAIMED_DEPOSIT");webhooks.process(receipt.id());
        assertThat(payments.findById(f.payment()).orElseThrow().getStatus()).isEqualTo(PaymentStatus.PAID);
        assertThat(progress(f)).isEqualTo("STEP_4_DIAGNOSIS_STARTED");
        assertThat(fake.confirms).hasValue(0);assertThat(fake.fetches).hasValue(1);
    }
    @Test void verifiedInboxIsDurableAndDuplicateEventIsIdempotent() throws Exception {
        Fixture f=order(PaymentMethod.CARD);long a=ready(f);
        fake.publish(ref(a),PaymentGateway.State.PAID);
        var first=receive(a,"event-one","PAID");var duplicate=receive(a,"event-one","PAID");
        assertThat(duplicate.id()).isEqualTo(first.id());assertThat(duplicate.duplicate()).isTrue();
        assertThat(events.findById(first.id()).orElseThrow().getState()).isEqualTo(PaymentWebhookEvent.State.RECEIVED);
        assertThat(payments.findById(f.payment()).orElseThrow().getStatus()).isEqualTo(PaymentStatus.PROCESSING);
        webhooks.process(first.id());webhooks.process(first.id());
        assertThat(events.findById(first.id()).orElseThrow().getState()).isEqualTo(PaymentWebhookEvent.State.PROCESSED);
        assertThat(fake.fetches).hasValue(1);assertThat(fake.confirms).hasValue(0);
    }
    @Test void rawUnverifiedEventCannotCreateInboxOrPay() throws Exception {
        Fixture f=order(PaymentMethod.CARD);long a=ready(f);var r=ref(a);
        long before=events.count();
        assertThatThrownBy(()->webhooks.receive(Map.of(),("bad|"+r.attemptKey()+"|"+r.orderId()).getBytes(StandardCharsets.UTF_8)))
                .isInstanceOf(CustomException.class);
        assertThat(events.count()).isEqualTo(before);
        assertThat(payments.findById(f.payment()).orElseThrow().getStatus()).isEqualTo(PaymentStatus.PROCESSING);
    }
    @Test void eventIdentityReuseWithDifferentBodyIsConflict() throws Exception {
        Fixture f=order(PaymentMethod.CARD);long a=ready(f);receive(a,"same-event","FIRST");
        assertThatThrownBy(()->receive(a,"same-event","DIFFERENT")).isInstanceOfSatisfying(CustomException.class,
                e->assertThat(e.getErrorCode()).isEqualTo(ErrorCode.PAYMENT_EVENT_CONFLICT));
    }
    @Test void reversedEventsUseLatestFetchInsteadOfEventClaimedState() throws Exception {
        Fixture f=order(PaymentMethod.CARD);long a=ready(f);fake.publish(ref(a),PaymentGateway.State.PAID);
        var newer=receive(a,"newer","PAID");webhooks.process(newer.id());
        Instant paid=payments.findById(f.payment()).orElseThrow().getPaidAt();
        var older=receive(a,"older","PREPARED");webhooks.process(older.id());
        assertThat(payments.findById(f.payment()).orElseThrow().getStatus()).isEqualTo(PaymentStatus.PAID);
        assertThat(payments.findById(f.payment()).orElseThrow().getPaidAt()).isEqualTo(paid);
        assertThat(fake.confirms).hasValue(0);
    }
    @Test void unsupportedReversalIsHeldWithoutDowngradingPaid() throws Exception {
        Fixture f=order(PaymentMethod.CARD);long a=ready(f);confirm(f,a,"paid");
        fake.publish(ref(a),PaymentGateway.State.CANCELED);
        var event=receive(a,"unsupported-reversal","CANCELED");webhooks.process(event.id());
        assertThat(payments.findById(f.payment()).orElseThrow().getStatus()).isEqualTo(PaymentStatus.PAID);
        assertThat(attempts.findById(a).orElseThrow().getFailure()).isEqualTo(PaymentProcessingFailure.INCONSISTENT_STATE);
        assertThat(jobs.findByAttemptId(a).orElseThrow().getState()).isEqualTo(PaymentReconciliationJob.State.BLOCKED);
        assertThat(events.findById(event.id()).orElseThrow().getState()).isEqualTo(PaymentWebhookEvent.State.RECEIVED);
    }
    @Test void productionDisabledPolicyCreatesNoAttemptOrCheckout() throws Exception {
        Fixture f=order(PaymentMethod.CARD);fake.enabled=false;
        Reply r=prepare(f,"disabled",PaymentMethod.CARD);assertThat(r.status()).isEqualTo(503);
        assertThat(countAttempts(f.payment())).isZero();assertThat(fake.prepares).hasValue(0);
        assertThat(payments.findById(f.payment()).orElseThrow().getStatus()).isEqualTo(PaymentStatus.CREATED);
    }
    @Test void ownerRoleAndJsonChecksRunThroughRealHttp() throws Exception {
        Fixture f=order(PaymentMethod.CARD),other=order(PaymentMethod.CARD);String path="/payments/"+f.payment()+"/attempts";
        assertThat(send(other.client(),UserTypeEnum.CLIENT,HttpMethod.POST,path,Map.of("method","CARD"),"other").status()).isEqualTo(403);
        assertThat(send(null,null,HttpMethod.POST,path,Map.of("method","CARD"),"missing").status()).isEqualTo(401);
        for(var role:List.of(UserTypeEnum.EXPERT,UserTypeEnum.ADMIN))
            assertThat(send(f.client(),role,HttpMethod.POST,path,Map.of("method","CARD"),"wrong-role").status()).isEqualTo(403);
        assertThat(send(f.client(),UserTypeEnum.CLIENT,HttpMethod.POST,path,Map.of(),"invalid").status()).isEqualTo(400);
        assertThat(send(f.client(),UserTypeEnum.CLIENT,HttpMethod.POST,path,Map.of("method","INVALID"),"invalid").status()).isEqualTo(400);
        assertThat(send(f.client(),UserTypeEnum.CLIENT,HttpMethod.POST,path,Map.of("method","CARD"),null).status()).isEqualTo(400);
        assertThat(countAttempts(f.payment())).isZero();
    }
    @Test void noPublicWebhookOrTestSuccessEndpointIsRegistered() {
        var paths=handlerMapping.getHandlerMethods().keySet().stream().flatMap(m->m.getPatternValues().stream()).toList();
        assertThat(paths).noneMatch(p->p.contains("webhook") || p.contains("force-paid") || p.contains("test-success"));
    }
    @Test void interruptedExecutionHasDurableRecoveryAndNeverRecharges() throws Exception {
        Fixture f=order(PaymentMethod.CARD);
        var run=persistence.prepare(f.client(),f.payment(),"interrupted",PaymentMethod.CARD);
        assertThat(jobs.findByAttemptId(run.attempt()).orElseThrow().getState()).isEqualTo(PaymentReconciliationJob.State.PENDING);
        ((MutableClock)clock).advance(31);assertThat(recovery.runDue()).isGreaterThanOrEqualTo(1);
        assertThat(payments.findById(f.payment()).orElseThrow().getStatus()).isEqualTo(PaymentStatus.UNKNOWN);
        assertThat(fake.prepares).hasValue(0);assertThat(fake.confirms).hasValue(0);assertThat(fake.fetches.get()).isGreaterThanOrEqualTo(1);
    }
    @Test void allEntitiesValidateAgainstV33AndCoreIsStillProviderNeutral() {
        assertThat(Arrays.stream(flyway.info().applied()).map(i->i.getVersion().getVersion()).toList())
                .containsExactlyElementsOf(IntStream.rangeClosed(1,33).mapToObj(String::valueOf).toList());
        flyway.validate();
        assertThat(jdbc.queryForObject("SELECT IS_NULLABLE FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='payment_attempts' AND column_name='merchant_id'",String.class)).isEqualTo("YES");
        assertThat(jdbc.queryForObject("SELECT IS_NULLABLE FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='payment_webhook_events' AND column_name='merchant_id'",String.class)).isEqualTo("YES");
        assertThat(jdbc.queryForObject("SELECT IS_NULLABLE FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='financial_operations' AND column_name='provider_merchant_id'",String.class)).isEqualTo("YES");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND column_name='provider_account_id' AND table_name IN ('payment_attempts','payment_webhook_events','financial_operations')",Integer.class)).isEqualTo(3);
        assertThat(em.getEntityManagerFactory().getMetamodel().getEntities()).extracting(t->t.getJavaType().getName())
                .contains(PaymentWebhookEvent.class.getName(),PaymentReconciliationJob.class.getName());
    }

    @Test void concurrentDifferentKeysCannotStartTwoProviderCalls() throws Exception {
        Fixture f=order(PaymentMethod.CARD);var pool=Executors.newFixedThreadPool(2);var barrier=new CyclicBarrier(2);
        try {
            var one=pool.submit(()->{barrier.await(10,TimeUnit.SECONDS);return prepare(f,"race-one",PaymentMethod.CARD);});
            var two=pool.submit(()->{barrier.await(10,TimeUnit.SECONDS);return prepare(f,"race-two",PaymentMethod.CARD);});
            assertThat(List.of(one.get(30,TimeUnit.SECONDS).status(),two.get(30,TimeUnit.SECONDS).status()))
                    .containsExactlyInAnyOrder(200,409);
            assertThat(countAttempts(f.payment())).isEqualTo(1);assertThat(fake.prepares).hasValue(1);
        } finally {pool.shutdownNow();}
    }
    @Test void concurrentConfirmationsInvokeProviderOnlyOnce() throws Exception {
        Fixture f=order(PaymentMethod.CARD);long a=ready(f);var pool=Executors.newFixedThreadPool(2);var barrier=new CyclicBarrier(2);
        try {
            var one=pool.submit(()->{barrier.await(10,TimeUnit.SECONDS);return confirm(f,a,"same-confirmation");});
            var two=pool.submit(()->{barrier.await(10,TimeUnit.SECONDS);return confirm(f,a,"same-confirmation");});
            assertThat(one.get(30,TimeUnit.SECONDS).status()).isEqualTo(200);
            assertThat(two.get(30,TimeUnit.SECONDS).status()).isEqualTo(200);
            assertThat(fake.confirms).hasValue(1);
            assertThat(payments.findById(f.payment()).orElseThrow().getStatus()).isEqualTo(PaymentStatus.PAID);
            assertThat(progress(f)).isEqualTo("STEP_4_DIAGNOSIS_STARTED");
        } finally {pool.shutdownNow();}
    }
    @Test void stalePrepareCompletionCannotOverwriteReconciledPaidState() throws Exception {
        Fixture f=order(PaymentMethod.CARD);var entered=new CountDownLatch(1);var release=new CountDownLatch(1);
        fake.prepareFunction=r->{
            fake.publish(r,PaymentGateway.State.PAID);entered.countDown();
            try {if(!release.await(15,TimeUnit.SECONDS))throw new AssertionError("Synthetic gate timeout");}
            catch(InterruptedException e){Thread.currentThread().interrupt();throw new AssertionError("Interrupted synthetic gate");}
            return fake.observation(r,PaymentGateway.State.PREPARED);
        };
        var pool=Executors.newSingleThreadExecutor();
        try {
            var old=pool.submit(()->prepare(f,"old-execution",PaymentMethod.CARD));assertThat(entered.await(10,TimeUnit.SECONDS)).isTrue();
            long a=attempts.findFirstByPaymentIdOrderByAttemptNumberDesc(f.payment()).orElseThrow().getId();
            ((MutableClock)clock).advance(31);
            assertThat(reconcile(f,a).data().path("payment_status").asText()).isEqualTo("PAID");
            release.countDown();assertThat(old.get(15,TimeUnit.SECONDS).status()).isEqualTo(409);
            assertThat(payments.findById(f.payment()).orElseThrow().getStatus()).isEqualTo(PaymentStatus.PAID);
            assertThat(attempts.findById(a).orElseThrow().getStatus()).isEqualTo(PaymentAttemptStatus.SUCCEEDED);
            assertThat(fake.prepares).hasValue(1);assertThat(fake.fetches).hasValue(1);
        } finally {release.countDown();pool.shutdownNow();}
    }
    @Test void webhookAndConfirmationRacePreservesInboxUntilLeaseReleased() throws Exception {
        Fixture f=order(PaymentMethod.CARD);long a=ready(f);
        var entered=new CountDownLatch(1);var release=new CountDownLatch(1);
        fake.confirmFunction=r->{
            var paid=fake.publish(r,PaymentGateway.State.PAID);entered.countDown();
            try {if(!release.await(15,TimeUnit.SECONDS))throw new AssertionError("Synthetic gate timeout");}
            catch(InterruptedException e){Thread.currentThread().interrupt();throw new AssertionError("Interrupted synthetic gate");}
            return paid;
        };
        var pool=Executors.newSingleThreadExecutor();
        try {
            var completing=pool.submit(()->confirm(f,a,"race-confirm"));assertThat(entered.await(10,TimeUnit.SECONDS)).isTrue();
            var receipt=receive(a,"during-confirmation","PAID");webhooks.process(receipt.id());
            assertThat(events.findById(receipt.id()).orElseThrow().getState()).isEqualTo(PaymentWebhookEvent.State.RECEIVED);
            assertThat(fake.fetches).hasValue(0);
            release.countDown();assertThat(completing.get(15,TimeUnit.SECONDS).data().path("payment_status").asText()).isEqualTo("PAID");
            ((MutableClock)clock).advance(30); // honor the persisted first transient retry delay
            webhooks.process(receipt.id());
            assertThat(events.findById(receipt.id()).orElseThrow().getState()).isEqualTo(PaymentWebhookEvent.State.PROCESSED);
            assertThat(fake.confirms).hasValue(1);assertThat(fake.fetches).hasValue(1);
        } finally {release.countDown();pool.shutdownNow();}
    }
    @Test void oldOsivEntitiesDoNotPermitStaleTokenCompletion() throws Exception {
        Fixture f=order(PaymentMethod.CARD);
        var original=persistence.prepare(f.client(),f.payment(),"osiv",PaymentMethod.CARD);
        new TransactionTemplate(manager).execute(tx->{
            Payment oldPayment=em.find(Payment.class,f.payment());PaymentAttempt oldAttempt=em.find(PaymentAttempt.class,original.attempt());
            assertThat(oldAttempt.getExecutionToken()).isEqualTo(original.token());((MutableClock)clock).advance(31);
            fake.publish(original.reference(),PaymentGateway.State.PAID);
            var paid=execution.reconcile(f.client(),f.payment(),original.attempt());
            assertThat(paid.getPaymentStatus()).isEqualTo(PaymentStatus.PAID);
            assertThat(oldPayment.getStatus()).isEqualTo(PaymentStatus.PROCESSING);
            assertThat(oldAttempt.getExecutionToken()).isEqualTo(original.token());
            assertThatThrownBy(()->persistence.complete(original,fake.observation(original.reference(),PaymentGateway.State.PREPARED)))
                    .isInstanceOfSatisfying(CustomException.class,e->assertThat(e.getErrorCode()).isEqualTo(ErrorCode.PAYMENT_EXECUTION_STALE));
            return null;
        });
        assertThat(payments.findById(f.payment()).orElseThrow().getStatus()).isEqualTo(PaymentStatus.PAID);
    }
    @Test void reconciliationWithoutProviderLeavesUncertainStateAndDoesNotCharge() throws Exception {
        Fixture f=order(PaymentMethod.CARD);long a=ready(f);
        fake.confirmFunction=r->{throw new PaymentGateway.GatewayException(PaymentProcessingFailure.TIMEOUT);};
        confirm(f,a,"timeout-disabled");fake.enabled=false;
        assertThat(reconcile(f,a).status()).isEqualTo(503);
        assertThat(payments.findById(f.payment()).orElseThrow().getStatus()).isEqualTo(PaymentStatus.UNKNOWN);
        assertThat(fake.confirms).hasValue(1);assertThat(fake.fetches).hasValue(0);
    }

    @Test void missingCallbackAndWebhookAreRecoveredFromPreparedJobByLookupOnly() throws Exception {
        Fixture f=order(PaymentMethod.CARD);long a=ready(f);
        assertThat(jobs.findByAttemptId(a).orElseThrow().getState()).isEqualTo(PaymentReconciliationJob.State.PENDING);
        fake.publish(ref(a),PaymentGateway.State.PAID);((MutableClock)clock).advance(31);
        recovery.runDue();
        assertThat(payments.findById(f.payment()).orElseThrow().getStatus()).isEqualTo(PaymentStatus.PAID);
        assertThat(progress(f)).isEqualTo("STEP_4_DIAGNOSIS_STARTED");
        assertThat(fake.confirms).hasValue(0);
        assertThat(fake.fetches.get()).isGreaterThanOrEqualTo(1);
    }
    @ParameterizedTest @EnumSource(value=PaymentGateway.State.class,names={"FAILED","CANCELED"})
    void definitiveProviderTerminalEvidenceIsRecordedWithoutNewAttempt(PaymentGateway.State state) throws Exception {
        Fixture f=order(PaymentMethod.CARD);long a=ready(f);
        fake.confirmFunction=r->fake.publish(r,state);
        assertThat(confirm(f,a,"terminal").data().path("payment_status").asText()).isEqualTo(state.name());
        assertThat(progress(f)).isEqualTo("STEP_3_EXPERT_SELECTION");
        assertThat(payments.findById(f.payment()).orElseThrow().getPaidAt()).isNull();
        assertThat(prepare(f,"new",PaymentMethod.CARD).status()).isEqualTo(409);
        assertThat(fake.confirms).hasValue(1);
    }

    @Autowired FinancialReviewService financial;
    @Autowired ExpertFileRepository expertFiles;
    @Autowired BusinessRegistrationInfoRepository businessInfos;

    long admin() {
        return users.saveAndFlush(User.builder().userType(UserTypeEnum.ADMIN).name("Synthetic Admin")
                .email(UUID.randomUUID()+"@example.test").phone("01000000000").build()).getId();
    }
    long paid(Fixture f) throws Exception {long a=ready(f);assertThat(confirm(f,a,"financial-test").status()).isEqualTo(200);return a;}

    @Test void refundDraftReservesAmountAndReplaysWithoutReversingPayment() throws Exception {
        var f=order(PaymentMethod.CARD);paid(f);
        var r=send(f.client(),UserTypeEnum.CLIENT,HttpMethod.POST,"/payments/"+f.payment()+"/refund-requests",
                Map.of("amount",300),"refund-key");
        assertThat(r.status()).isEqualTo(200);assertThat(r.data().path("state").asText()).isEqualTo("POLICY_HOLD");
        assertThat(r.data().path("external_executed").asBoolean()).isFalse();
        assertThat(send(f.client(),UserTypeEnum.CLIENT,HttpMethod.POST,"/payments/"+f.payment()+"/refund-requests",
                Map.of("amount",300),"refund-key").data().path("id")).isEqualTo(r.data().path("id"));
        assertThat(send(f.client(),UserTypeEnum.CLIENT,HttpMethod.POST,"/payments/"+f.payment()+"/refund-requests",
                Map.of("amount",400),"refund-key").status()).isEqualTo(409);
        assertThat(send(f.client(),UserTypeEnum.CLIENT,HttpMethod.POST,"/payments/"+f.payment()+"/refund-requests",
                Map.of("amount",800),"another-refund").status()).isEqualTo(409);
        assertThat(payments.findById(f.payment()).orElseThrow().getStatus()).isEqualTo(PaymentStatus.PAID);
        assertThat(fake.confirms).hasValue(1);
    }
    @Test void unpaidOrFractionalRefundCannotBeDrafted() throws Exception {
        var f=order(PaymentMethod.CARD);
        assertThat(send(f.client(),UserTypeEnum.CLIENT,HttpMethod.POST,"/payments/"+f.payment()+"/refund-requests",
                Map.of("amount",100),"unpaid").status()).isEqualTo(409);
        paid(f);
        assertThat(send(f.client(),UserTypeEnum.CLIENT,HttpMethod.POST,"/payments/"+f.payment()+"/refund-requests",
                Map.of("amount",new BigDecimal("1.5")),"fraction").status()).isEqualTo(400);
        assertThat(send(f.client(),UserTypeEnum.CLIENT,HttpMethod.POST,"/payments/"+f.payment()+"/refund-requests",
                Map.of("amount",new BigDecimal("100.00")),"whole-decimal").status()).isEqualTo(200);
    }
    @Test void refundIdorAndWrongDatabaseRoleAreDenied() throws Exception {
        var f=order(PaymentMethod.CARD);paid(f);var other=order(PaymentMethod.CARD);
        var r=send(f.client(),UserTypeEnum.CLIENT,HttpMethod.POST,"/payments/"+f.payment()+"/refund-requests",Map.of("amount",100),"r");
        assertThat(send(other.client(),UserTypeEnum.CLIENT,HttpMethod.GET,"/payments/"+f.payment()+"/refund-requests/"+r.data().path("id").asLong(),null,null).status()).isEqualTo(403);
        assertThat(send(f.expert(),UserTypeEnum.CLIENT,HttpMethod.POST,"/payments/"+f.payment()+"/refund-requests",Map.of("amount",100),"fake-role").status()).isEqualTo(403);
    }
    @Test void settlementAndTaxDraftsNeverAuthorizePayoutOrIssuance() throws Exception {
        var f=order(PaymentMethod.CARD);paid(f);progress(f,"STEP_7_COMPLETED");long admin=admin();
        var s=send(admin,UserTypeEnum.ADMIN,HttpMethod.POST,"/admin/payments/"+f.payment()+"/settlement-drafts",null,null);
        assertThat(s.status()).isEqualTo(200);assertThat(s.data().path("platform_amount").decimalValue()).isEqualByComparingTo("100");
        assertThat(s.data().path("expert_amount").decimalValue()).isEqualByComparingTo("900");
        long sid=s.data().path("id").asLong();
        assertThat(send(admin,UserTypeEnum.ADMIN,HttpMethod.POST,"/admin/payments/"+f.payment()+"/settlement-drafts",null,null).data().path("id").asLong()).isEqualTo(sid);
        assertThat(send(admin,UserTypeEnum.ADMIN,HttpMethod.POST,"/admin/settlement-drafts/"+sid+"/payout-approvals",null,null).status()).isEqualTo(503);
        var invoice=send(admin,UserTypeEnum.ADMIN,HttpMethod.POST,"/admin/settlement-drafts/"+sid+"/tax-invoice-drafts",
                Map.of("kind","PLATFORM_TO_EXPERT"),"invoice-key");
        assertThat(invoice.status()).isEqualTo(200);assertThat(invoice.data().path("state").asText()).isEqualTo("TAX_REVIEW_HOLD");
        long iid=invoice.data().path("id").asLong();
        assertThat(send(admin,UserTypeEnum.ADMIN,HttpMethod.POST,"/admin/tax-invoice-drafts/"+iid+"/issue",null,null).status()).isEqualTo(503);
        assertThat(send(admin,UserTypeEnum.ADMIN,HttpMethod.POST,"/admin/settlement-drafts/"+sid+"/tax-invoice-drafts",
                Map.of("kind","EXPERT_TO_CLIENT"),"invoice-key").status()).isEqualTo(409);
        assertThat(send(admin,UserTypeEnum.ADMIN,HttpMethod.POST,"/admin/settlement-drafts/"+sid+"/tax-invoice-drafts",
                Map.of("kind","PLATFORM_TO_EXPERT","original_draft_id",iid),"revision-key").status()).isEqualTo(200);
        assertThat(send(f.client(),UserTypeEnum.CLIENT,HttpMethod.POST,"/payments/"+f.payment()+"/refund-requests",Map.of("amount",10),"late").status()).isEqualTo(409);
        assertThat(payments.findById(f.payment()).orElseThrow().getStatus()).isEqualTo(PaymentStatus.PAID);
    }
    @Test void unpaidPaymentAndUntrustedAdminRoleCannotCreateSettlement() throws Exception {
        var f=order(PaymentMethod.CARD);long admin=admin();
        assertThat(send(admin,UserTypeEnum.ADMIN,HttpMethod.POST,"/admin/payments/"+f.payment()+"/settlement-drafts",null,null).status()).isEqualTo(409);
        assertThat(send(f.client(),UserTypeEnum.ADMIN,HttpMethod.POST,"/admin/payments/"+f.payment()+"/settlement-drafts",null,null).status()).isEqualTo(403);
        assertThat(send(f.client(),UserTypeEnum.CLIENT,HttpMethod.POST,"/admin/payments/"+f.payment()+"/settlement-drafts",null,null).status()).isEqualTo(403);
    }
    @Test void sellerDraftTracksConfirmedSnapshotWithoutClaimingRegistration() throws Exception {
        var f=order(PaymentMethod.CARD);
        Long[] ids=new TransactionTemplate(manager).execute(tx->{
            var profile=profiles.findByUserId(f.expert()).orElseThrow();
            var file=expertFiles.saveAndFlush(ExpertFile.builder().expertProfile(profile).originalName("synthetic.pdf")
                    .storedName("synthetic.pdf").filePath(uploads.resolve("synthetic.pdf").toString()).fileSize(10L)
                    .mimeType(MimeTypeEnum.PDF).fileType(FileTypeEnum.BUSINESS_REGISTRATION).build());
            file.startOcr("synthetic",LocalDateTime.now());file.finishOcr("synthetic",OcrStatusEnum.SUCCEEDED,null);
            var info=businessInfos.saveAndFlush(BusinessRegistrationInfo.create(profile,file,"1234567891","Synthetic Person","Synthetic Company"));
            info.markConfirmed(file.getId(),file.getOcrRevision());
            return new Long[]{file.getId(),info.getId()};
        });
        var draft=send(f.expert(),UserTypeEnum.EXPERT,HttpMethod.PUT,"/expert/me/seller-draft",Map.of("business_registration_info_id",ids[1]),null);
        assertThat(draft.status()).isEqualTo(200);assertThat(draft.data().path("snapshot_current").asBoolean()).isTrue();
        assertThat(draft.data().path("state").asText()).isEqualTo("CONTRACT_HOLD");
        assertThat(send(f.client(),UserTypeEnum.EXPERT,HttpMethod.PUT,"/expert/me/seller-draft",Map.of("business_registration_info_id",ids[1]),null).status()).isEqualTo(403);
        new TransactionTemplate(manager).execute(tx->{var file=expertFiles.findById(ids[0]).orElseThrow();file.startOcr("new",LocalDateTime.now());return null;});
        assertThat(send(f.expert(),UserTypeEnum.EXPERT,HttpMethod.GET,"/expert/me/seller-draft",null,null).data().path("snapshot_current").asBoolean()).isFalse();
        assertThat(send(f.expert(),UserTypeEnum.EXPERT,HttpMethod.PUT,"/expert/me/seller-draft",Map.of("business_registration_info_id",ids[1]),null).status()).isEqualTo(409);
    }
    @Test void financialRoutesRequireAuthenticationAndCheckoutIsNotAvailableForFakeProvider() throws Exception {
        var f=order(PaymentMethod.CARD);long a=ready(f);
        assertThat(send(null,null,HttpMethod.POST,"/payments/"+f.payment()+"/refund-requests",Map.of("amount",100),"unauth").status()).isEqualTo(401);
        assertThat(send(f.client(),UserTypeEnum.CLIENT,HttpMethod.GET,"/payments/"+f.payment()+"/attempts/"+a+"/checkout",null,null).status()).isEqualTo(503);
        assertThat(send(f.expert(),UserTypeEnum.EXPERT,HttpMethod.GET,"/payments/"+f.payment()+"/attempts/"+a+"/checkout",null,null).status()).isEqualTo(403);
    }

    record SelectionFixture(long client,long expertA,long expertB,long announcement,long bidA,long bidB,String code,boolean guest) { }
    SelectionFixture selectionFixture(boolean guest) {
        return new TransactionTemplate(manager).execute(tx->{
            User client=users.saveAndFlush(User.builder().userType(UserTypeEnum.CLIENT).name("Selection Client")
                    .email(UUID.randomUUID()+"@example.test").phone("01000000000").build());
            var info=clients.saveAndFlush(ClientInfo.builder().user(guest?null:client).representativeName("Selection Client")
                    .contact("01000000000").email("selection@example.test").companyName("Synthetic Company")
                    .businessNumber("1234567891").address("Synthetic Address").build());
            User expertA=users.saveAndFlush(User.builder().userType(UserTypeEnum.EXPERT).name("Expert A")
                    .email(UUID.randomUUID()+"@example.test").phone("01000000000").build());
            User expertB=users.saveAndFlush(User.builder().userType(UserTypeEnum.EXPERT).name("Expert B")
                    .email(UUID.randomUUID()+"@example.test").phone("01000000000").build());
            for(User expert:List.of(expertA,expertB)) {
                var profile=ExpertProfile.createDefault(expert,"Synthetic Company");
                profile.updateVerificationStatus(VerificationStatusEnum.APPROVED,null);profiles.saveAndFlush(profile);
            }
            var announcement=announcements.saveAndFlush(Announcement.builder().user(guest?null:client).clientInfo(info)
                    .announcementCode("CANCEL-"+UUID.randomUUID()).industry(IndustryTypeEnum.CONSTRUCTION)
                    .purpose(AnnouncementPurposeEnum.REQUIRED_LICENSE).jobType(JobTypeEnum.LICENSE)
                    .businessOwnerType(BusinessOwnerTypeEnum.CORPORATION).capital(new BigDecimal("1000000"))
                    .status(AnnouncementStatusEnum.CLOSED).progressStep(AnnouncementProgressStepEnum.STEP_3_EXPERT_SELECTION).build());
            var a=bids.saveAndFlush(Bid.create(announcement,expertA,new BigDecimal("1100")));
            var b=bids.saveAndFlush(Bid.create(announcement,expertB,new BigDecimal("1200")));
            return new SelectionFixture(client.getId(),expertA.getId(),expertB.getId(),announcement.getId(),a.getId(),b.getId(),
                    announcement.getAnnouncementCode(),guest);
        });
    }
    String selectionRoute(SelectionFixture f,long bid,String action) {
        return "/announcements/"+(f.guest()?"guest/":"")+f.code()+"/bids/"+bid+"/"+action;
    }
    Reply select(SelectionFixture f,long bid,int amount) throws Exception {
        return send(f.guest()?null:f.client(),f.guest()?null:UserTypeEnum.CLIENT,HttpMethod.PATCH,
                selectionRoute(f,bid,"select"),Map.of("final_amount",amount),null);
    }
    Reply cancel(SelectionFixture f,long bid,long round) throws Exception {
        return send(f.guest()?null:f.client(),f.guest()?null:UserTypeEnum.CLIENT,HttpMethod.PATCH,
                selectionRoute(f,bid,"cancel-selection"),Map.of("selection_round",round,"reason","Changed expert"),null);
    }
    Reply selectionOrder(SelectionFixture f,long bid,String key) throws Exception {
        return send(f.client(),UserTypeEnum.CLIENT,HttpMethod.POST,"/announcements/"+f.code()+"/payments",
                Map.of("bid_id",bid,"method","CARD"),key);
    }
    String bidStatus(long bid) {return jdbc.queryForObject("SELECT status FROM bids WHERE id=?",String.class,bid);}
    int selectedCount(long user) {return jdbc.queryForObject("SELECT selected_count FROM expert_profiles WHERE user_id=?",Integer.class,user);}
    long historyCount(SelectionFixture f) {return jdbc.queryForObject("SELECT COUNT(*) FROM bid_selection_history WHERE announcement_id=?",Long.class,f.announcement());}

    @ParameterizedTest @ValueSource(booleans={false,true})
    void memberAndGuestCanCancelAndReselectAnotherExpert(boolean guest) throws Exception {
        var f=selectionFixture(guest);
        var selected=select(f,f.bidA(),1000);
        assertThat(selected.status()).isEqualTo(200);assertThat(selected.data().path("selection_round").asLong()).isEqualTo(1);
        assertThat(bidStatus(f.bidB())).isEqualTo("REJECTED");assertThat(selectedCount(f.expertA())).isEqualTo(1);
        assertThat(cancel(f,f.bidA(),1).status()).isEqualTo(200);
        assertThat(bidStatus(f.bidA())).isEqualTo("PENDING");assertThat(bidStatus(f.bidB())).isEqualTo("PENDING");
        assertThat(jdbc.queryForObject("SELECT final_amount FROM bids WHERE id=?",BigDecimal.class,f.bidA())).isNull();
        assertThat(jdbc.queryForObject("SELECT bid_amount FROM bids WHERE id=?",BigDecimal.class,f.bidA())).isEqualByComparingTo("1100");
        assertThat(selectedCount(f.expertA())).isZero();
        assertThat(cancel(f,f.bidA(),1).status()).isEqualTo(200);assertThat(selectedCount(f.expertA())).isZero();
        assertThat(historyCount(f)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT cancel_reason FROM bid_selection_history WHERE announcement_id=?",String.class,f.announcement()))
                .isEqualTo("Changed expert");
        assertThat(jdbc.queryForObject("SELECT cancelled_by FROM bid_selection_history WHERE announcement_id=?",Long.class,f.announcement()))
                .isEqualTo(guest?null:f.client());
        var next=select(f,f.bidB(),1200);assertThat(next.status()).isEqualTo(200);
        assertThat(next.data().path("selection_round").asLong()).isEqualTo(2);
        assertThat(bidStatus(f.bidA())).isEqualTo("REJECTED");assertThat(bidStatus(f.bidB())).isEqualTo("SELECTED");
        assertThat(selectedCount(f.expertB())).isEqualTo(1);assertThat(historyCount(f)).isEqualTo(2);
        assertThat(cancel(f,f.bidA(),1).status()).isEqualTo(409);
        assertThat(bidStatus(f.bidB())).isEqualTo("SELECTED");
    }

    @Test void reselectingSameExpertCreatesNewOrderAndCannotReviveOldOrderOrCancellation() throws Exception {
        var f=selectionFixture(false);assertThat(select(f,f.bidA(),1000).status()).isEqualTo(200);
        var first=selectionOrder(f,f.bidA(),"round-one");assertThat(first.status()).isEqualTo(201);
        long oldPayment=first.data().path("payment_id").asLong();
        assertThat(cancel(f,f.bidA(),1).status()).isEqualTo(200);
        assertThat(payments.findById(oldPayment).orElseThrow().getStatus()).isEqualTo(PaymentStatus.CANCELED);
        assertThat(select(f,f.bidB(),1200).data().path("selection_round").asLong()).isEqualTo(2);
        assertThat(cancel(f,f.bidB(),2).status()).isEqualTo(200);
        assertThat(select(f,f.bidA(),2000).data().path("selection_round").asLong()).isEqualTo(3);
        var next=selectionOrder(f,f.bidA(),"round-three");assertThat(next.status()).isEqualTo(201);
        long payment=next.data().path("payment_id").asLong();assertThat(payment).isNotEqualTo(oldPayment);
        assertThat(next.data().path("selection_round").asLong()).isEqualTo(3);
        assertThat(next.data().path("amount").decimalValue()).isEqualByComparingTo("2000");
        var replay=selectionOrder(f,f.bidA(),"round-one");assertThat(replay.status()).isEqualTo(200);
        assertThat(replay.data().path("payment_id").asLong()).isEqualTo(oldPayment);
        assertThat(replay.data().path("status").asText()).isEqualTo("CANCELED");
        assertThat(prepare(new Fixture(f.client(),f.expertA(),oldPayment),"stale",PaymentMethod.CARD).status()).isEqualTo(409);
        assertThat(fake.prepares).hasValue(0);assertThat(countAttempts(oldPayment)).isZero();
        assertThat(cancel(f,f.bidA(),1).status()).isEqualTo(409);
        assertThat(bidStatus(f.bidA())).isEqualTo("SELECTED");assertThat(selectedCount(f.expertA())).isEqualTo(1);
        var current=new Fixture(f.client(),f.expertA(),payment);long a=ready(current);
        assertThat(confirm(current,a,"new-round-payment").data().path("payment_status").asText()).isEqualTo("PAID");
        assertThat(progress(current)).isEqualTo("STEP_4_DIAGNOSIS_STARTED");
        assertThat(cancel(f,f.bidA(),3).status()).isEqualTo(409);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM payments WHERE bid_id=?",Long.class,f.bidA())).isEqualTo(2);
    }

    @Test void cancellationDoesNotRestoreRejectionsOutsideItsRound() throws Exception {
        var f=selectionFixture(false);
        // This bid was independently rejected before selection, so it has no rejection round.
        jdbc.update("UPDATE bids SET status='REJECTED' WHERE id=?",f.bidB());
        assertThat(select(f,f.bidA(),1000).status()).isEqualTo(200);
        assertThat(cancel(f,f.bidA(),1).status()).isEqualTo(200);
        assertThat(bidStatus(f.bidA())).isEqualTo("PENDING");assertThat(bidStatus(f.bidB())).isEqualTo("REJECTED");
    }

    @Test void cancellationChecksRoleOwnershipTargetAndRound() throws Exception {
        var f=selectionFixture(false);var other=selectionFixture(false);var guest=selectionFixture(true);
        assertThat(select(f,f.bidA(),1000).status()).isEqualTo(200);
        assertThat(select(guest,guest.bidA(),1000).status()).isEqualTo(200);
        String route=selectionRoute(f,f.bidA(),"cancel-selection");var body=Map.of("selection_round",1,"reason","Changed expert");
        assertThat(send(null,null,HttpMethod.PATCH,route,body,null).status()).isEqualTo(401);
        assertThat(send(f.expertA(),UserTypeEnum.EXPERT,HttpMethod.PATCH,route,body,null).status()).isEqualTo(403);
        assertThat(send(f.expertA(),UserTypeEnum.CLIENT,HttpMethod.PATCH,route,body,null).status()).isEqualTo(403);
        assertThat(send(other.client(),UserTypeEnum.CLIENT,HttpMethod.PATCH,route,body,null).status()).isEqualTo(403);
        assertThat(cancel(f,f.bidB(),1).status()).isEqualTo(409);
        assertThat(cancel(f,other.bidA(),1).status()).isEqualTo(404);
        assertThat(cancel(f,f.bidA(),99).status()).isEqualTo(409);
        assertThat(send(null,null,HttpMethod.PATCH,"/announcements/guest/"+f.code()+"/bids/"+f.bidA()+"/cancel-selection",body,null).status()).isEqualTo(403);
        assertThat(send(f.client(),UserTypeEnum.CLIENT,HttpMethod.PATCH,"/announcements/"+guest.code()+"/bids/"+guest.bidA()+"/cancel-selection",body,null).status()).isEqualTo(403);
        for(Object invalid:List.of(Map.of("reason","missing round"),Map.of("selection_round",0,"reason","zero round"),
                Map.of("selection_round",1,"reason"," "),Map.of("selection_round",1,"reason","x".repeat(501)))) {
            assertThat(send(f.client(),UserTypeEnum.CLIENT,HttpMethod.PATCH,route,invalid,null).status()).isEqualTo(400);
        }
        assertThat(bidStatus(f.bidA())).isEqualTo("SELECTED");assertThat(selectedCount(f.expertA())).isEqualTo(1);
    }

    @ParameterizedTest @EnumSource(PaymentStatus.class)
    void anyExistingPaymentAttemptBlocksSelectionCancellationEvenIfStatusLooksUnpaid(PaymentStatus status) throws Exception {
        var f=selectionFixture(false);assertThat(select(f,f.bidA(),1000).status()).isEqualTo(200);
        long payment=selectionOrder(f,f.bidA(),"order").data().path("payment_id").asLong();
        var paidFixture=new Fixture(f.client(),f.expertA(),payment);ready(paidFixture);
        jdbc.update("UPDATE payments SET status=? WHERE id=?",status.name(),payment);
        assertThat(cancel(f,f.bidA(),1).status()).isEqualTo(409);
        assertThat(bidStatus(f.bidA())).isEqualTo("SELECTED");assertThat(bidStatus(f.bidB())).isEqualTo("REJECTED");
        assertThat(selectedCount(f.expertA())).isEqualTo(1);
        assertThat(payments.findById(payment).orElseThrow().getStatus()).isEqualTo(status);
    }

    @Test void selectionCancellationAndPaymentPreparationCannotBothSucceed() throws Exception {
        var f=selectionFixture(false);assertThat(select(f,f.bidA(),1000).status()).isEqualTo(200);
        long payment=selectionOrder(f,f.bidA(),"order").data().path("payment_id").asLong();
        var order=new Fixture(f.client(),f.expertA(),payment);var pool=Executors.newFixedThreadPool(2);var start=new CyclicBarrier(2);
        try {
            var cancellation=pool.submit(()->{start.await(10,TimeUnit.SECONDS);return cancel(f,f.bidA(),1);});
            var preparation=pool.submit(()->{start.await(10,TimeUnit.SECONDS);return prepare(order,"race",PaymentMethod.CARD);});
            int cancelled=cancellation.get(30,TimeUnit.SECONDS).status(),prepared=preparation.get(30,TimeUnit.SECONDS).status();
            assertThat(List.of(cancelled,prepared)).containsExactlyInAnyOrder(200,409);
            if(cancelled==200) {
                assertThat(bidStatus(f.bidA())).isEqualTo("PENDING");assertThat(countAttempts(payment)).isZero();
                assertThat(payments.findById(payment).orElseThrow().getStatus()).isEqualTo(PaymentStatus.CANCELED);
                assertThat(fake.prepares).hasValue(0);
            } else {
                assertThat(bidStatus(f.bidA())).isEqualTo("SELECTED");assertThat(countAttempts(payment)).isEqualTo(1);
                assertThat(payments.findById(payment).orElseThrow().getStatus()).isEqualTo(PaymentStatus.PROCESSING);
            }
        } finally {pool.shutdownNow();}
    }

    @Test void orderCreationAndCancellationRaceNeverLeavesPayableCanceledSelection() throws Exception {
        var f=selectionFixture(false);assertThat(select(f,f.bidA(),1000).status()).isEqualTo(200);
        var pool=Executors.newFixedThreadPool(2);var start=new CyclicBarrier(2);
        try {
            var cancellation=pool.submit(()->{start.await(10,TimeUnit.SECONDS);return cancel(f,f.bidA(),1);});
            var creation=pool.submit(()->{start.await(10,TimeUnit.SECONDS);return selectionOrder(f,f.bidA(),"race");});
            assertThat(cancellation.get(30,TimeUnit.SECONDS).status()).isEqualTo(200);
            assertThat(creation.get(30,TimeUnit.SECONDS).status()).isIn(201,409);
            assertThat(bidStatus(f.bidA())).isEqualTo("PENDING");
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM payments WHERE announcement_id=? AND status<>'CANCELED'",Long.class,f.announcement())).isZero();
        } finally {pool.shutdownNow();}
    }

    @Test void simultaneousDuplicateCancellationsDecrementOnlyOnce() throws Exception {
        var f=selectionFixture(false);assertThat(select(f,f.bidA(),1000).status()).isEqualTo(200);
        var pool=Executors.newFixedThreadPool(2);var start=new CyclicBarrier(2);
        try {
            var first=pool.submit(()->{start.await(10,TimeUnit.SECONDS);return cancel(f,f.bidA(),1);});
            var second=pool.submit(()->{start.await(10,TimeUnit.SECONDS);return cancel(f,f.bidA(),1);});
            assertThat(first.get(30,TimeUnit.SECONDS).status()).isEqualTo(200);
            assertThat(second.get(30,TimeUnit.SECONDS).status()).isEqualTo(200);
            assertThat(selectedCount(f.expertA())).isZero();assertThat(historyCount(f)).isEqualTo(1);
        } finally {pool.shutdownNow();}
    }

    @Test void cancellationRollsBackPaymentBidsCountsAndHistoryTogether() throws Exception {
        var f=selectionFixture(false);assertThat(select(f,f.bidA(),1000).status()).isEqualTo(200);
        long payment=selectionOrder(f,f.bidA(),"order").data().path("payment_id").asLong();
        doThrow(new IllegalStateException("SYNTHETIC_CANCEL_STORAGE_FAILURE")).when(payments).flush();
        try {assertThat(cancel(f,f.bidA(),1).status()).isEqualTo(500);} finally {reset(payments);}
        assertThat(payments.findById(payment).orElseThrow().getStatus()).isEqualTo(PaymentStatus.CREATED);
        assertThat(bidStatus(f.bidA())).isEqualTo("SELECTED");assertThat(bidStatus(f.bidB())).isEqualTo("REJECTED");
        assertThat(selectedCount(f.expertA())).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM bid_selection_history WHERE announcement_id=? AND cancelled_at IS NOT NULL",Long.class,f.announcement())).isZero();
        assertThat(cancel(f,f.bidA(),1).status()).isEqualTo(200);
    }
}
