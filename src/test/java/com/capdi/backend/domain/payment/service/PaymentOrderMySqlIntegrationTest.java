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
import com.capdi.backend.domain.payment.entity.*;
import com.capdi.backend.domain.payment.repository.*;
import com.capdi.backend.domain.user.entity.*;
import com.capdi.backend.domain.user.repository.UserRepository;
import com.capdi.backend.global.jwt.JwtUtil;
import com.fasterxml.jackson.databind.*;
import jakarta.persistence.EntityManager;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.*;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.*;
import javax.sql.DataSource;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Real loopback HTTP + JWT + Flyway + all production entities, only a new ephemeral database. */
@Tag("mysql-integration")
@Testcontainers
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT, properties={
        "spring.config.location=classpath:/application.yml", "spring.profiles.active=issue57-payment-test",
        "server.address=127.0.0.1", "spring.datasource.driver-class-name=com.mysql.cj.jdbc.Driver",
        "spring.flyway.enabled=true", "spring.flyway.baseline-on-migrate=false",
        "spring.flyway.validate-on-migrate=true", "spring.jpa.hibernate.ddl-auto=validate",
        "spring.jpa.show-sql=false", "logging.level.org.hibernate.SQL=OFF", "nts.business.enabled=false"})
class PaymentOrderMySqlIntegrationTest {
    @Container static final MySQLContainer<?> MYSQL=new MySQLContainer<>("mysql:8.4")
            .withDatabaseName("capdi_issue57_payment_test").withUsername("issue57_test")
            .withPassword(UUID.randomUUID().toString()).withTmpFs(Map.of("/var/lib/mysql","rw"))
            .withLabel("capdi.test.scope","issue57-payment-ephemeral");
    @TempDir static Path uploads;
    @DynamicPropertySource static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url",MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username",MYSQL::getUsername);
        registry.add("spring.datasource.password",MYSQL::getPassword);
        registry.add("file.storage.upload-dir",()->uploads.toString());
        registry.add("jwt.secret",()->UUID.randomUUID()+"-isolated-payment-test");
    }
    @Autowired TestRestTemplate http;
    @Autowired ObjectMapper mapper;
    @Autowired JdbcTemplate jdbc;
    @Autowired DataSource dataSource;
    @Autowired Flyway flyway;
    @Autowired UserRepository users;
    @Autowired ClientRepository clients;
    @Autowired ExpertProfileRepository profiles;
    @Autowired AnnouncementRepository announcements;
    @Autowired BidRepository bids;
    @Autowired PaymentRepository payments;
    @Autowired PaymentOrderService orderService;
    @org.springframework.test.context.bean.override.mockito.MockitoSpyBean PaymentIdempotencyKeyRepository keyBindings;
    @Autowired PaymentAttemptRepository attempts;
    @Autowired PlatformTransactionManager manager;
    @Autowired EntityManager entityManager;
    @Autowired JwtUtil jwt;
    @LocalServerPort int port;
    @MockitoBean GoogleVisionOcrClient vision;
    @MockitoBean NtsHttpTransport nts;
    record Fixture(long client,long expertUser,long expertProfile,long announcement,long bid,String code) { }
    record Reply(int status,JsonNode data) { }
    @BeforeEach void guardDatabase() throws Exception {
        try(var connection=dataSource.getConnection()) {
            assertThat(connection.getCatalog()).isEqualTo("capdi_issue57_payment_test");
            assertThat(connection.getMetaData().getURL()).startsWith(MYSQL.getJdbcUrl());
        }
    }
    @AfterEach void noExternalProviders() { verifyNoInteractions(vision,nts); }
    Fixture fixture() { return fixture(null); }
    Fixture fixture(Long existingClient) {
        return new TransactionTemplate(manager).execute(tx->{
            User client=existingClient==null ? users.saveAndFlush(User.builder().userType(UserTypeEnum.CLIENT)
                    .name("Synthetic Client").email(UUID.randomUUID()+"@example.test").phone("01000000000").build())
                    : users.findById(existingClient).orElseThrow();
            ClientInfo info=clients.findByUser_Id(client.getId()).orElseGet(()->clients.saveAndFlush(ClientInfo.builder()
                    .user(client).representativeName("Synthetic Client").contact("01000000000")
                    .email("billing@example.test").companyName("Synthetic Client Company")
                    .businessNumber("1234567891").address("Synthetic Test Address").build()));
            User expert=users.saveAndFlush(User.builder().userType(UserTypeEnum.EXPERT).name("Synthetic Expert")
                    .email(UUID.randomUUID()+"@example.test").phone("01000000000").build());
            ExpertProfile profile=ExpertProfile.createDefault(expert,"Synthetic Company");
            profile.updateVerificationStatus(VerificationStatusEnum.APPROVED,null);
            profiles.saveAndFlush(profile);
            Announcement a=announcements.saveAndFlush(Announcement.builder().user(client).clientInfo(info)
                    .announcementCode("S57-"+UUID.randomUUID()).industry(IndustryTypeEnum.CONSTRUCTION)
                    .purpose(AnnouncementPurposeEnum.REQUIRED_LICENSE).jobType(JobTypeEnum.LICENSE)
                    .businessOwnerType(BusinessOwnerTypeEnum.CORPORATION).capital(new BigDecimal("1000000"))
                    .status(AnnouncementStatusEnum.CLOSED)
                    .progressStep(AnnouncementProgressStepEnum.STEP_4_DIAGNOSIS_STARTED).build());
            Bid bid=Bid.create(a,expert,new BigDecimal("1100000"));bid.select(new BigDecimal("1000000"));
            bids.saveAndFlush(bid);
            return new Fixture(client.getId(),expert.getId(),profile.getId(),a.getId(),bid.getId(),a.getAnnouncementCode());
        });
    }
    Reply send(Long user,UserTypeEnum role,HttpMethod method,String route,Object body,String key) throws Exception {
        HttpHeaders headers=new HttpHeaders();headers.setContentType(MediaType.APPLICATION_JSON);
        if(user!=null)headers.setBearerAuth(jwt.generateToken(user,role));
        if(key!=null)headers.set("Idempotency-Key",key);
        var response=http.exchange("http://127.0.0.1:"+port+"/api"+route,method,
                new HttpEntity<>(body,headers),String.class);
        // Never dump request/response/token on failure.
        JsonNode root=mapper.readTree(response.getBody());
        return new Reply(response.getStatusCode().value(),root.path("data"));
    }
    Reply create(Fixture f,String key) throws Exception {
        return send(f.client(),UserTypeEnum.CLIENT,HttpMethod.POST,"/announcements/"+f.code()+"/payments",
                Map.of("bid_id",f.bid(),"method","CARD"),key);
    }
    int orderCount(Fixture f) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM payments WHERE bid_id=?",Integer.class,f.bid());
    }
    @Test void createsViaRealHttpAndPersistsFinancialSnapshotWithoutPgOrAttempt() throws Exception {
        Fixture f=fixture();Reply r=create(f,"first");
        assertThat(r.status()).isEqualTo(201);long id=r.data().path("payment_id").asLong();
        assertThat(r.data().path("amount").decimalValue()).isEqualByComparingTo("1000000");
        assertThat(r.data().path("status").asText()).isEqualTo("CREATED");
        assertThat(r.data().path("created_at").asText()).isNotBlank();
        assertThat(r.data().properties()).extracting(Map.Entry::getKey).containsExactlyInAnyOrder(
                "payment_id","order_id","announcement_code","bid_id","amount","currency","status","created_at");
        Payment p=payments.findById(id).orElseThrow();
        assertThat(p.getClientUserId()).isEqualTo(f.client());assertThat(p.getExpertProfileId()).isEqualTo(f.expertProfile());
        assertThat(p.getAnnouncementId()).isEqualTo(f.announcement());assertThat(p.getVersion()).isZero();
        assertThat(p.getPlatformFeeRate()).isEqualByComparingTo("0.1000");
        assertThat(p.getExpertShareRate()).isEqualByComparingTo("0.9000");
        assertThat(p.getFeePolicyVersion()).isEqualTo("BASE_ALLOCATION_10_90_V1");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM payment_attempts WHERE payment_id=?",Integer.class,id)).isZero();
        assertThat(jdbc.queryForObject("SELECT LENGTH(key_hash) FROM payment_idempotency_keys WHERE payment_id=?",
                Integer.class,id)).isEqualTo(64);
        assertThat(send(f.client(),UserTypeEnum.CLIENT,HttpMethod.GET,"/payments/"+id,null,null).status()).isEqualTo(200);
    }
    @Test void sameKeyAndDifferentKeySameBidBothReuseWithoutCreatingAttempts() throws Exception {
        Fixture f=fixture();Reply first=create(f,"first"),same=create(f,"first"),alias=create(f,"alias");
        assertThat(first.status()).isEqualTo(201);assertThat(same.status()).isEqualTo(200);assertThat(alias.status()).isEqualTo(200);
        assertThat(same.data().path("payment_id")).isEqualTo(first.data().path("payment_id"));
        assertThat(alias.data().path("order_id")).isEqualTo(first.data().path("order_id"));
        assertThat(orderCount(f)).isEqualTo(1);
    }
    @Test void sameKeyDifferentMethodIs409AndAliasKeyRemainsBound() throws Exception {
        Fixture f=fixture();create(f,"first");create(f,"alias");
        var r=send(f.client(),UserTypeEnum.CLIENT,HttpMethod.POST,"/announcements/"+f.code()+"/payments",
                Map.of("bid_id",f.bid(),"method","TRANSFER"),"alias");
        assertThat(r.status()).isEqualTo(409);assertThat(orderCount(f)).isEqualTo(1);
    }
    @Test void differentKeyCannotChangeMethodOfExistingOrder() throws Exception {
        Fixture f=fixture();create(f,"first");
        assertThat(send(f.client(),UserTypeEnum.CLIENT,HttpMethod.POST,"/announcements/"+f.code()+"/payments",
                Map.of("bid_id",f.bid(),"method","TRANSFER"),"other").status()).isEqualTo(409);
    }
    @Test void sameKeyDifferentAnnouncementIs409() throws Exception {
        Fixture f=fixture(),other=fixture(f.client());create(f,"shared");
        assertThat(create(other,"shared").status()).isEqualTo(409);assertThat(orderCount(other)).isZero();
    }
    @Test void keyScopeIsPerClient() throws Exception {
        Fixture first=fixture(),second=fixture();
        assertThat(create(first,"shared-key").status()).isEqualTo(201);
        assertThat(create(second,"shared-key").status()).isEqualTo(201);
    }
    @Test void rejectsOtherAnnouncementBidAndOtherOwner() throws Exception {
        Fixture first=fixture(),other=fixture();
        assertThat(send(first.client(),UserTypeEnum.CLIENT,HttpMethod.POST,"/announcements/"+first.code()+"/payments",
                Map.of("bid_id",other.bid(),"method","CARD"),"wrong-bid").status()).isEqualTo(404);
        assertThat(send(other.client(),UserTypeEnum.CLIENT,HttpMethod.POST,"/announcements/"+first.code()+"/payments",
                Map.of("bid_id",first.bid(),"method","CARD"),"wrong-owner").status()).isEqualTo(403);
        assertThat(orderCount(first)).isZero();
    }
    @Test void readIsProtectedAgainstIdorAndUnknownIdIs404() throws Exception {
        Fixture first=fixture(),other=fixture();long id=create(first,"read").data().path("payment_id").asLong();
        assertThat(send(other.client(),UserTypeEnum.CLIENT,HttpMethod.GET,"/payments/"+id,null,null).status()).isEqualTo(403);
        assertThat(send(first.client(),UserTypeEnum.CLIENT,HttpMethod.GET,"/payments/9223372036854775807",null,null).status()).isEqualTo(404);
    }
    @Test void realSecurityRejectsNoJwtAndExpert() throws Exception {
        Fixture f=fixture();String path="/announcements/"+f.code()+"/payments";
        var body=Map.of("bid_id",f.bid(),"method","CARD");
        assertThat(send(null,null,HttpMethod.POST,path,body,"no-jwt").status()).isEqualTo(401);
        assertThat(send(f.expertUser(),UserTypeEnum.EXPERT,HttpMethod.POST,path,body,"expert").status()).isEqualTo(403);
        assertThat(orderCount(f)).isZero();
    }
    @Test void missingOrInvalidKeyIs400() throws Exception {
        Fixture f=fixture();
        assertThat(create(f,null).status()).isEqualTo(400);
        assertThat(create(f,"key with space").status()).isEqualTo(400);
        assertThat(orderCount(f)).isZero();
    }
    @Test void missingAnnouncementIs404() throws Exception {
        Fixture f=fixture();
        assertThat(send(f.client(),UserTypeEnum.CLIENT,HttpMethod.POST,"/announcements/DOES-NOT-EXIST/payments",
                Map.of("bid_id",f.bid(),"method","CARD"),"missing").status()).isEqualTo(404);
    }
    @ParameterizedTest @ValueSource(strings={"PENDING","REJECTED"})
    void rejectsNotSelectedBid(String state) throws Exception {
        Fixture f=fixture();jdbc.update("UPDATE bids SET status=? WHERE id=?",state,f.bid());
        assertThat(create(f,"not-selected").status()).isEqualTo(409);assertThat(orderCount(f)).isZero();
    }
    @ParameterizedTest @NullSource @ValueSource(strings={"0","-1","0.01","100.50"})
    void invalidOrLegacyAmountIs409(String amount) throws Exception {
        Fixture f=fixture();jdbc.update("UPDATE bids SET final_amount=? WHERE id=?",
                amount==null?null:new BigDecimal(amount),f.bid());
        assertThat(create(f,"amount").status()).isEqualTo(409);assertThat(orderCount(f)).isZero();
    }
    @Test void earlierSelectionStepCannotOpenPayment() throws Exception {
        Fixture f=fixture();jdbc.update("UPDATE announcement SET progress_step='STEP_3_EXPERT_SELECTION' WHERE id=?",f.announcement());
        assertThat(create(f,"step").status()).isEqualTo(409);
    }
    @Test void requestCannotOverrideDbAmountExpertOrFeeRate() throws Exception {
        Fixture f=fixture();
        var r=send(f.client(),UserTypeEnum.CLIENT,HttpMethod.POST,"/announcements/"+f.code()+"/payments",
                Map.of("bid_id",f.bid(),"method","CARD","amount",1,"expert_profile_id",999,"platform_fee_rate",0),"untrusted");
        assertThat(r.status()).isEqualTo(201);assertThat(r.data().path("amount").decimalValue()).isEqualByComparingTo("1000000");
        assertThat(payments.findById(r.data().path("payment_id").asLong()).orElseThrow().getExpertProfileId()).isEqualTo(f.expertProfile());
    }
    @Test void sourceChangeCannotOverwriteAmountAndSameKeyOnlyReplaysSnapshot() throws Exception {
        Fixture f=fixture();Reply first=create(f,"first");
        jdbc.update("UPDATE bids SET final_amount=2000000 WHERE id=?",f.bid());
        Reply replay=create(f,"first");assertThat(replay.status()).isEqualTo(200);
        assertThat(replay.data().path("amount").decimalValue()).isEqualByComparingTo("1000000");
        assertThat(create(f,"new-key").status()).isEqualTo(409);
        assertThat(payments.findById(first.data().path("payment_id").asLong()).orElseThrow().getAmount()).isEqualByComparingTo("1000000");
    }
    @Test void simultaneousDifferentKeysProduceOneOrder() throws Exception {
        Fixture f=fixture();var pool=Executors.newFixedThreadPool(2);var barrier=new CyclicBarrier(2);
        try {
            var a=pool.submit(()->{barrier.await(10,TimeUnit.SECONDS);return create(f,"race-a");});
            var b=pool.submit(()->{barrier.await(10,TimeUnit.SECONDS);return create(f,"race-b");});
            Reply ra=a.get(30,TimeUnit.SECONDS),rb=b.get(30,TimeUnit.SECONDS);
            assertThat(List.of(ra.status(),rb.status())).containsExactlyInAnyOrder(201,200);
            assertThat(ra.data().path("payment_id")).isEqualTo(rb.data().path("payment_id"));assertThat(orderCount(f)).isEqualTo(1);
        } finally {pool.shutdownNow();}
    }
    @Test void simultaneousSameKeyAcrossAnnouncementsCannotBindTwoRequests() throws Exception {
        Fixture f=fixture(),other=fixture(f.client());var pool=Executors.newFixedThreadPool(2);var barrier=new CyclicBarrier(2);
        try {
            var a=pool.submit(()->{barrier.await(10,TimeUnit.SECONDS);return create(f,"cross-race");});
            var b=pool.submit(()->{barrier.await(10,TimeUnit.SECONDS);return create(other,"cross-race");});
            assertThat(List.of(a.get(30,TimeUnit.SECONDS).status(),b.get(30,TimeUnit.SECONDS).status()))
                    .containsExactlyInAnyOrder(201,409);
            assertThat(orderCount(f)+orderCount(other)).isEqualTo(1);
        } finally {pool.shutdownNow();}
    }
    @Test void databaseUniqueForeignKeyAndChecksProtectOrders() throws Exception {
        Fixture f=fixture();long id=create(f,"constraints").data().path("payment_id").asLong();
        assertThatThrownBy(()->new TransactionTemplate(manager).execute(tx->
                payments.saveAndFlush(Payment.create(f.bid(),f.announcement(),f.code(),f.client(),f.expertProfile(),
                        new BigDecimal("1000000"),PaymentMethod.CARD)))).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(()->jdbc.update("DELETE FROM bids WHERE id=?",f.bid())).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(()->jdbc.update("UPDATE payments SET amount=0 WHERE id=?",id))
                .isInstanceOfSatisfying(org.springframework.jdbc.UncategorizedSQLException.class,
                        e->assertThat(e.getSQLException().getErrorCode()).isEqualTo(3819));
        assertThat(orderCount(f)).isEqualTo(1);
    }
    @Test void attemptStructurePersistsDisabledAndRejectsDuplicateNumber() throws Exception {
        Fixture f=fixture();long id=create(f,"attempt-structure").data().path("payment_id").asLong();
        var saved=new TransactionTemplate(manager).execute(tx->attempts.saveAndFlush(PaymentAttempt.prepare(payments.findById(id).orElseThrow(),1)));
        assertThat(saved.getProvider()).isEqualTo(PaymentProvider.DISABLED);assertThat(saved.getStatus()).isEqualTo(PaymentAttemptStatus.PREPARED);
        assertThatThrownBy(()->new TransactionTemplate(manager).execute(tx->
                attempts.saveAndFlush(PaymentAttempt.prepare(payments.findById(id).orElseThrow(),1))))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
    @Test void fullFlywayAndHibernateSchemaValidate() {
        assertThat(flyway.info().applied()).hasSize(17);
        assertThat(Arrays.stream(flyway.info().applied()).map(i->i.getVersion().getVersion()).toList())
                .containsExactlyElementsOf(IntStream.rangeClosed(1,17).mapToObj(String::valueOf).toList());
        flyway.validate();
        assertThat(entityManager.getEntityManagerFactory().getMetamodel().getEntities())
                .extracting(t->t.getJavaType().getName()).contains(Payment.class.getName(),PaymentAttempt.class.getName(),PaymentIdempotencyKey.class.getName());
    }
    @Test void originalBidSelectionApiPersistsSameContractThenCanOpenOrder() throws Exception {
        Fixture f=fixture();jdbc.update("UPDATE bids SET status='PENDING',final_amount=NULL WHERE id=?",f.bid());
        jdbc.update("UPDATE announcement SET progress_step='STEP_3_EXPERT_SELECTION' WHERE id=?",f.announcement());
        var r=send(f.client(),UserTypeEnum.CLIENT,HttpMethod.PATCH,
                "/announcements/"+f.code()+"/bids/"+f.bid()+"/select",Map.of("final_amount",1000000),null);
        assertThat(r.status()).isEqualTo(200);assertThat(r.data().path("bid_id").asLong()).isEqualTo(f.bid());
        assertThat(r.data().path("final_amount").decimalValue()).isEqualByComparingTo("1000000");
        assertThat(create(f,"after-selection").status()).isEqualTo(201);
    }

    @Test void simultaneousSameKeySameRequestReturnsSingleBinding() throws Exception {
        Fixture f=fixture();var pool=Executors.newFixedThreadPool(2);var barrier=new CyclicBarrier(2);
        try {
            var a=pool.submit(()->{barrier.await(10,TimeUnit.SECONDS);return create(f,"same-key-race");});
            var b=pool.submit(()->{barrier.await(10,TimeUnit.SECONDS);return create(f,"same-key-race");});
            Reply ra=a.get(30,TimeUnit.SECONDS),rb=b.get(30,TimeUnit.SECONDS);
            assertThat(List.of(ra.status(),rb.status())).containsExactlyInAnyOrder(201,200);
            assertThat(ra.data().path("payment_id")).isEqualTo(rb.data().path("payment_id"));
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM payment_idempotency_keys WHERE client_user_id=?",
                    Integer.class,f.client())).isEqualTo(1);
        } finally {pool.shutdownNow();}
    }
    @Test void unrelatedClientsCanCreateConcurrently() throws Exception {
        Fixture f=fixture(),other=fixture();var pool=Executors.newFixedThreadPool(2);var barrier=new CyclicBarrier(2);
        try {
            var a=pool.submit(()->{barrier.await(10,TimeUnit.SECONDS);return create(f,"parallel");});
            var b=pool.submit(()->{barrier.await(10,TimeUnit.SECONDS);return create(other,"parallel");});
            assertThat(List.of(a.get(30,TimeUnit.SECONDS).status(),b.get(30,TimeUnit.SECONDS).status()))
                    .containsExactly(201,201);
        } finally {pool.shutdownNow();}
    }
    @Test void missingExpertProfileAndAmbiguousSelectedRowsAreRejected() throws Exception {
        Fixture missing=fixture();
        jdbc.update("DELETE FROM expert_profiles WHERE id=?",missing.expertProfile());
        assertThat(create(missing,"missing-profile").status()).isEqualTo(404);
        Fixture ambiguous=fixture();
        jdbc.update("INSERT INTO bids (announcement_code,expert_user_id,bid_amount,final_amount,bid_start_date,status,"+
                "submitted_at,created_at,updated_at) SELECT announcement_code,expert_user_id,bid_amount,final_amount,"+
                "bid_start_date,status,submitted_at,created_at,updated_at FROM bids WHERE id=?",ambiguous.bid());
        assertThat(create(ambiguous,"ambiguous").status()).isEqualTo(409);
    }
    @Test void staleManagedBidDoesNotSupplyOrderAmount() {
        Fixture f=fixture();
        new TransactionTemplate(manager).execute(tx->{
            Bid stale=entityManager.find(Bid.class,f.bid());
            assertThat(stale.getFinalAmount()).isEqualByComparingTo("1000000");
            TransactionTemplate change=new TransactionTemplate(manager);
            change.setPropagationBehavior(org.springframework.transaction.TransactionDefinition.PROPAGATION_REQUIRES_NEW);
            change.execute(inner->{jdbc.update("UPDATE bids SET final_amount=2000000 WHERE id=?",f.bid());return null;});
            com.capdi.backend.domain.payment.dto.PaymentOrderRequest request;
            try {request=mapper.readValue("{\"bid_id\":"+f.bid()+",\"method\":\"CARD\"}",
                    com.capdi.backend.domain.payment.dto.PaymentOrderRequest.class);}
            catch(Exception e){throw new AssertionError("Synthetic DTO failure");}
            var result=orderService.create(f.client(),f.code(),"fresh-source",request);
            assertThat(result.response().getAmount()).isEqualByComparingTo("2000000");
            assertThat(stale.getFinalAmount()).isEqualByComparingTo("1000000");
            return null;
        });
    }
    @Test void bindingStorageFailureRollsBackPaymentAndRetryDoesNotDuplicate() throws Exception {
        Fixture f=fixture();
        doThrow(new DataIntegrityViolationException("Synthetic binding storage failure")).when(keyBindings).saveAndFlush(any());
        try {
            assertThat(create(f,"rollback").status()).isEqualTo(500);
            assertThat(orderCount(f)).isZero();
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM payment_idempotency_keys WHERE client_user_id=?",
                    Integer.class,f.client())).isZero();
        } finally {reset(keyBindings);}
        assertThat(create(f,"rollback").status()).isEqualTo(201);
        assertThat(orderCount(f)).isEqualTo(1);
    }
}
