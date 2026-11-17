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
@Import(FinancialWorkflowMySqlIntegrationTest.FakeConfig.class)
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,properties={
        "spring.config.location=classpath:/application.yml","spring.profiles.active=issue57-processing-test",
        "server.address=127.0.0.1","spring.datasource.driver-class-name=com.mysql.cj.jdbc.Driver",
        "spring.flyway.enabled=true","spring.flyway.baseline-on-migrate=false","spring.flyway.validate-on-migrate=true",
        "spring.jpa.hibernate.ddl-auto=validate","spring.jpa.show-sql=false","logging.level.org.hibernate.SQL=OFF",
        "nts.business.enabled=false"})
class FinancialWorkflowMySqlIntegrationTest {
 @Autowired FakePayoutProvider fakePayout;
 @Autowired FakeTaxInvoiceProvider fakeTax;
 static final java.util.concurrent.atomic.AtomicBoolean taxAuthorized=new java.util.concurrent.atomic.AtomicBoolean();
 @Autowired InternalTaxExecutionPersistenceService taxPersistence;@Autowired InternalTaxExecutionService taxExecution;@Autowired InternalPayoutService internalPayouts;
    @TestConfiguration static class FakeConfig {
        @Bean @Primary FakeTaxInvoiceProvider fakeTax(){return new FakeTaxInvoiceProvider();}
        @Bean @Primary com.capdi.backend.domain.payment.config.TaxExecutionAuthorization taxAuthorization(){return taxAuthorized::get;}
        @Bean @Primary FakePayoutProvider fakePayout(){return new FakePayoutProvider();}
        @Bean @Primary FakePaymentGateway fakeGateway(){return new FakePaymentGateway();}
        @Bean @Primary FakeFinancialGateway fakeFinancialGateway(){return new FakeFinancialGateway();}
        @Bean @Primary com.capdi.backend.domain.payment.config.FinancialPolicy reviewedPolicy(){
            return new com.capdi.backend.domain.payment.config.FinancialPolicy(EnumSet.allOf(FinancialGateway.Kind.class),
                "SYNTHETIC_REVIEWED_V1",true,Set.of("1234567891"),"contract-synthetic");
        }
        @Bean @Primary FinancialVault syntheticVault(){return new FinancialVault("test",Map.of("test",new byte[32]));}

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
            .withDatabaseName("capdi_issue57_financial_test").withUsername("processing57_test")
            .withPassword(UUID.randomUUID().toString()).withTmpFs(Map.of("/var/lib/mysql","rw"))
            .withLabel("capdi.test.scope","issue57-financial-ephemeral");
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
        fake.reset();finance.reset();fakePayout.reset();fakeTax.reset();taxAuthorized.set(false);reset(financeOperations);((MutableClock)clock).reset();
        try(var c=dataSource.getConnection()) {
            assertThat(c.getCatalog()).isEqualTo("capdi_issue57_financial_test");
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
                    .status(AnnouncementStatusEnum.CLOSED).progressStep(AnnouncementProgressStepEnum.STEP_4_DIAGNOSIS_STARTED).build());
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


 @Autowired FakeFinancialGateway finance;
 @Autowired FinancialReviewService review;
 @Autowired FinancialWorkflowService workflow;
 @Autowired FinancialWorkflowPersistenceService persistenceFinance;
 @Autowired ExpertFileRepository expertFiles;@Autowired BusinessRegistrationInfoRepository businessInfos;
 @Autowired SettlementBalanceRepository balancesFinance;@Autowired FinancialTaxInvoiceRepository taxInvoices;
 @MockitoSpyBean FinancialOperationRepository financeOperations;

 record Ready(Fixture fixture,long admin,long seller,long account){}
 long newAdmin(){return users.saveAndFlush(User.builder().userType(UserTypeEnum.ADMIN).name("Synthetic Admin")
   .email(UUID.randomUUID()+"@example.test").phone("01000000000").build()).getId();}
 long id(Reply r){assertThat(r.status()).isEqualTo(200);return r.data().path("id").asLong();}
 Ready financialFixture() throws Exception {
  var f=order(PaymentMethod.CARD);long a=ready(f);assertThat(confirm(f,a,"financial-flow").status()).isEqualTo(200);long admin=newAdmin();
  Long infoId=new TransactionTemplate(manager).execute(tx->{
   var profile=profiles.findByUserId(f.expert()).orElseThrow();
   var file=expertFiles.saveAndFlush(ExpertFile.builder().expertProfile(profile).originalName("synthetic.pdf").storedName("synthetic.pdf")
     .filePath(uploads.resolve(UUID.randomUUID()+".pdf").toString()).fileSize(10L).mimeType(MimeTypeEnum.PDF).fileType(FileTypeEnum.BUSINESS_REGISTRATION).build());
   file.startOcr("synthetic",LocalDateTime.now());file.finishOcr("synthetic",OcrStatusEnum.SUCCEEDED,null);
   var info=businessInfos.saveAndFlush(BusinessRegistrationInfo.create(profile,file,"1234567891","Synthetic Person","Synthetic Company"));
   info.markConfirmed(file.getId(),file.getOcrRevision());return info.getId();
  });
  var seller=send(f.expert(),UserTypeEnum.EXPERT,HttpMethod.PUT,"/expert/me/seller-draft",Map.of("business_registration_info_id",infoId),null);
  long sellerId=id(seller),account=account(f);
  var enrollment=send(f.expert(),UserTypeEnum.EXPERT,HttpMethod.POST,"/expert/me/partner-enrollments",
    Map.of("account_id",account,"expected_seller_version",seller.data().path("version").asLong()),"enrollment");
  long op=id(enrollment);assertThat(execute(admin,op).data().path("state").asText()).isEqualTo("SUCCEEDED");
  assertThat(send(admin,UserTypeEnum.ADMIN,HttpMethod.POST,"/admin/payments/"+f.payment()+"/settlements",null,null).status()).isEqualTo(200);
  finance.reset();return new Ready(f,admin,sellerId,account);
 }
 long account(Fixture f) throws Exception {
  return id(send(f.expert(),UserTypeEnum.EXPERT,HttpMethod.POST,"/expert/me/settlement-accounts",
   Map.of("bank","KOOKMIN","number","000000123456","holder","Synthetic Person","contactName","Synthetic Person",
    "contactEmail","synthetic@example.test","contactPhone","01000000000"),null));
 }
 Reply execute(long admin,long operation) throws Exception {return send(admin,UserTypeEnum.ADMIN,HttpMethod.POST,"/admin/financial-operations/"+operation+"/execute",null,null);}
 Reply lookup(long admin,long operation) throws Exception {return send(admin,UserTypeEnum.ADMIN,HttpMethod.POST,"/admin/financial-operations/"+operation+"/reconciliations",null,null);}
 JsonNode balance(Ready r) throws Exception {var b=send(r.admin(),UserTypeEnum.ADMIN,HttpMethod.GET,"/admin/payments/"+r.fixture().payment()+"/settlement",null,null);assertThat(b.status()).isEqualTo(200);return b.data();}
 long refund(Ready r,int amount,String key) throws Exception {
  long rid=id(send(r.fixture().client(),UserTypeEnum.CLIENT,HttpMethod.POST,"/payments/"+r.fixture().payment()+"/refunds",Map.of("amount",amount),key));
  long version=send(r.fixture().client(),UserTypeEnum.CLIENT,HttpMethod.GET,"/payments/"+r.fixture().payment()+"/refund-requests/"+rid,null,null).data().path("version").asLong();
  long approval=id(send(r.admin(),UserTypeEnum.ADMIN,HttpMethod.POST,"/admin/refunds/"+rid+"/decisions",
    Map.of("decision","APPROVED","expected_refund_version",version,"reason_code","SYNTHETIC_REVIEW"),null));
  return id(send(r.admin(),UserTypeEnum.ADMIN,HttpMethod.POST,"/admin/refunds/"+rid+"/attempts",Map.of("approval_id",approval),key));
 }
 long payoutApproval(Ready r,int amount) throws Exception {
  return id(send(r.admin(),UserTypeEnum.ADMIN,HttpMethod.POST,"/admin/payments/"+r.fixture().payment()+"/payout-approvals",
    Map.of("decision","APPROVED","expected_balance_version",balance(r).path("version").asLong(),"account_id",r.account(),
     "expected_account_revision",1,"amount",amount,"reason_code","SYNTHETIC_REVIEW"),null));
 }
 long payout(Ready r,long approval,String key) throws Exception {return id(send(r.admin(),UserTypeEnum.ADMIN,HttpMethod.POST,"/admin/payments/"+r.fixture().payment()+"/payouts",Map.of("approval_id",approval),key));}
 void journalBalanced(Ready r){
  var sums=jdbc.query("SELECT SUM(signed_amount) FROM financial_ledger_entries WHERE payment_id=? GROUP BY movement_key",(rs,n)->rs.getBigDecimal(1),r.fixture().payment());
  assertThat(sums).isNotEmpty().allSatisfy(x->assertThat(x).isEqualByComparingTo("0"));
 }

 @Test void fullAndPartialRefundsAdjustOnlyOnConfirmedEvidenceAndCannotExceedGross() throws Exception {
  var r=financialFixture();long op=refund(r,300,"partial");
  finance.submitFunction=c->finance.evidence(c,FinancialGateway.State.PENDING);
  assertThat(execute(r.admin(),op).data().path("state").asText()).isEqualTo("PENDING");
  assertThat(balance(r).path("refunded").decimalValue()).isEqualByComparingTo("0");
  assertThat(lookup(r.admin(),op).data().path("state").asText()).isEqualTo("SUCCEEDED");
  assertThat(balance(r).path("refunded").decimalValue()).isEqualByComparingTo("300");
  finance.submitFunction=c->finance.evidence(c,FinancialGateway.State.SUCCEEDED);
  long rest=refund(r,700,"remaining");execute(r.admin(),rest);
  assertThat(balance(r).path("net").decimalValue()).isEqualByComparingTo("0");
  assertThat(send(r.fixture().client(),UserTypeEnum.CLIENT,HttpMethod.POST,"/payments/"+r.fixture().payment()+"/refunds",Map.of("amount",1),"over").status()).isEqualTo(409);
  int writes=finance.submits.get();execute(r.admin(),rest);lookup(r.admin(),rest);assertThat(finance.submits).hasValue(writes);journalBalanced(r);
 }
 @Test void unknownRefundNeverResubmitsAndLookupRecoversOriginalOperation() throws Exception {
  var r=financialFixture();long op=refund(r,100,"timeout");
  finance.submitFunction=c->{throw new FinancialGatewayException(FinancialGatewayException.Failure.TIMEOUT);};
  assertThat(execute(r.admin(),op).data().path("state").asText()).isEqualTo("UNKNOWN");execute(r.admin(),op);assertThat(finance.submits).hasValue(1);
  assertThat(lookup(r.admin(),op).data().path("state").asText()).isEqualTo("SUCCEEDED");
  assertThat(balance(r).path("refunded").decimalValue()).isEqualByComparingTo("100");assertThat(finance.submits).hasValue(1);journalBalanced(r);
 }
 @Test void providerSuccessThenPersistenceFailureRecoversWithoutSecondRefund() throws Exception {
  var r=financialFixture();long op=refund(r,100,"db-failure");
  finance.submitFunction=c->{var e=finance.evidence(c,FinancialGateway.State.SUCCEEDED);doThrow(new IllegalStateException("synthetic storage failure")).when(financeOperations).flush();return e;};
  assertThat(execute(r.admin(),op).data().path("state").asText()).isEqualTo("UNKNOWN");reset(financeOperations);
  assertThat(balance(r).path("refunded").decimalValue()).isEqualByComparingTo("0");
  lookup(r.admin(),op);assertThat(balance(r).path("refunded").decimalValue()).isEqualByComparingTo("100");assertThat(finance.submits).hasValue(1);journalBalanced(r);
 }
 @Test void wrongProviderAmountDoesNotChangeFinancialBalances() throws Exception {
  var r=financialFixture();long op=refund(r,100,"wrong-proof");
  finance.submitFunction=c->{var e=finance.evidence(c,FinancialGateway.State.SUCCEEDED);return new FinancialGateway.Evidence(c.reference(),e.externalId(),e.state(),new BigDecimal("101"),"KRW",false,false,e.ntsState());};
  assertThat(execute(r.admin(),op).data().path("state").asText()).isEqualTo("UNKNOWN");
  assertThat(balance(r).path("refunded").decimalValue()).isEqualByComparingTo("0");
  assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM settlement_adjustments WHERE payment_id=?",Long.class,r.fixture().payment())).isZero();
 }
 @Test void partnerDuplicateRegistrationIsReusedAndPrivateFieldsAreEncrypted() throws Exception {
  var r=financialFixture();
  var seller=send(r.fixture().expert(),UserTypeEnum.EXPERT,HttpMethod.GET,"/expert/me/seller-draft",null,null);
  var repeated=send(r.fixture().expert(),UserTypeEnum.EXPERT,HttpMethod.POST,"/expert/me/partner-enrollments",
    Map.of("account_id",r.account(),"expected_seller_version",seller.data().path("version").asLong()),"another-key");
  assertThat(repeated.status()).isEqualTo(200);assertThat(finance.submits).hasValue(0);
  String cipher=jdbc.queryForObject("SELECT encrypted_details FROM expert_settlement_accounts WHERE id=?",String.class,r.account());
  assertThat(cipher).doesNotContain("000000123456","Synthetic Person","synthetic@example.test");
  var response=send(r.fixture().expert(),UserTypeEnum.EXPERT,HttpMethod.GET,"/expert/me/settlement-accounts/"+r.account(),null,null);
  assertThat(response.data().path("masked_number").asText()).isEqualTo("****3456");
  assertThat(response.data().toString()).doesNotContain("000000123456","encrypted_details");
 }
 @Test void accountIdorAndClientOrForgedAdminRolesAreDenied() throws Exception {
  var r=financialFixture();var other=order(PaymentMethod.CARD);
  assertThat(send(other.expert(),UserTypeEnum.EXPERT,HttpMethod.GET,"/expert/me/settlement-accounts/"+r.account(),null,null).status()).isEqualTo(403);
  assertThat(send(r.fixture().client(),UserTypeEnum.ADMIN,HttpMethod.GET,"/admin/payments/"+r.fixture().payment()+"/settlement",null,null).status()).isEqualTo(403);
  assertThat(send(r.fixture().expert(),UserTypeEnum.EXPERT,HttpMethod.POST,"/admin/payments/"+r.fixture().payment()+"/payouts",Map.of("approval_id",1),"fake").status()).isEqualTo(403);
  assertThat(send(null,null,HttpMethod.POST,"/admin/financial-operations/1/execute",null,null).status()).isEqualTo(401);
 }
 @Test void accountChangeInvalidatesPreviousApprovalAndUnsentReservation() throws Exception {
  var r=financialFixture();long approval=payoutApproval(r,900);long op=payout(r,approval,"payout-key");
  account(r.fixture());
  var stopped=execute(r.admin(),op);
  assertThat(stopped.data().path("state").asText()).isEqualTo("BLOCKED");assertThat(finance.submits).hasValue(0);
  assertThat(balance(r).path("reserved").decimalValue()).isEqualByComparingTo("0");
  assertThat(send(r.admin(),UserTypeEnum.ADMIN,HttpMethod.POST,"/admin/payments/"+r.fixture().payment()+"/payouts",Map.of("approval_id",approval),"another-key").status()).isEqualTo(409);
  journalBalanced(r);
 }
 @Test void payoutPendingIsNotPaidAndUnknownBlocksNewAccountChange() throws Exception {
  var r=financialFixture();long approval=payoutApproval(r,900);long op=payout(r,approval,"pending");
  finance.submitFunction=c->finance.evidence(c,FinancialGateway.State.PENDING);
  execute(r.admin(),op);assertThat(balance(r).path("paid").decimalValue()).isEqualByComparingTo("0");
  assertThat(balance(r).path("reserved").decimalValue()).isEqualByComparingTo("900");
  assertThat(send(r.fixture().expert(),UserTypeEnum.EXPERT,HttpMethod.POST,"/expert/me/settlement-accounts",
    Map.of("bank","KOOKMIN","number","000000123456","holder","Synthetic Person","contactName","Synthetic Person","contactEmail","synthetic@example.test","contactPhone","01000000000"),null).status()).isEqualTo(409);
  lookup(r.admin(),op);assertThat(balance(r).path("paid").decimalValue()).isEqualByComparingTo("900");
  execute(r.admin(),op);assertThat(finance.submits).hasValue(1);journalBalanced(r);
 }
 @Test void payoutFailureReleasesReservationAndRefundAfterPaidCreatesRecoverable() throws Exception {
  var r=financialFixture();long a=payoutApproval(r,900),op=payout(r,a,"failed");
  finance.submitFunction=c->finance.evidence(c,FinancialGateway.State.FAILED);execute(r.admin(),op);
  assertThat(balance(r).path("reserved").decimalValue()).isEqualByComparingTo("0");
  a=payoutApproval(r,900);op=payout(r,a,"retry-new-approval");
  finance.submitFunction=c->finance.evidence(c,FinancialGateway.State.SUCCEEDED);execute(r.admin(),op);
  long refund=refund(r,100,"post-payout-refund");execute(r.admin(),refund);
  assertThat(balance(r).path("recoverable").decimalValue()).isEqualByComparingTo("90");
  assertThat(balance(r).path("available").decimalValue()).isEqualByComparingTo("0");assertThat(balance(r).path("held").asBoolean()).isTrue();journalBalanced(r);
 }
 @Test void concurrentApprovalsRequireLatestBalanceVersion() throws Exception {
  var r=financialFixture();long version=balance(r).path("version").asLong();
  var request=Map.of("decision","APPROVED","expected_balance_version",version,"account_id",r.account(),"expected_account_revision",1,"amount",900,"reason_code","SYNTHETIC_REVIEW");
  var pool=Executors.newFixedThreadPool(2);
  try {
   var first=pool.submit(()->send(r.admin(),UserTypeEnum.ADMIN,HttpMethod.POST,"/admin/payments/"+r.fixture().payment()+"/payout-approvals",request,null).status());
   var second=pool.submit(()->send(r.admin(),UserTypeEnum.ADMIN,HttpMethod.POST,"/admin/payments/"+r.fixture().payment()+"/payout-approvals",request,null).status());
   assertThat(List.of(first.get(),second.get())).containsExactlyInAnyOrder(200,409);
  }finally{pool.shutdownNow();}
 }
 @Test void duplicatePayoutAndUnapprovedPaymentCannotReserveMoney() throws Exception {
  var r=financialFixture();
  assertThat(send(r.admin(),UserTypeEnum.ADMIN,HttpMethod.POST,"/admin/payments/"+r.fixture().payment()+"/payouts",Map.of("approval_id",999999),"none").status()).isEqualTo(404);
  long a=payoutApproval(r,900),op=payout(r,a,"same");
  assertThat(payout(r,a,"same")).isEqualTo(op);
  assertThat(send(r.admin(),UserTypeEnum.ADMIN,HttpMethod.POST,"/admin/payments/"+r.fixture().payment()+"/payouts",Map.of("approval_id",a),"different").status()).isEqualTo(409);
  assertThat(balance(r).path("reserved").decimalValue()).isEqualByComparingTo("900");assertThat(finance.submits).hasValue(0);
 }
 long party(Ready r,String role,Long subject) throws Exception {
  Map<String,Object> body=new HashMap<>(Map.of("role",role,"brn","1234567891","name","Synthetic Party","representativeName","Synthetic Person","email","synthetic@example.test"));
  if(subject!=null)body.put("subject_user_id",subject);
  return id(send(r.admin(),UserTypeEnum.ADMIN,HttpMethod.POST,"/admin/billing-party-snapshots",body,null));
 }
 @Test void invoiceIssuanceNtsFailureAndCorrectionPreserveOriginalDocument() throws Exception {
  var r=financialFixture();long supplier=party(r,"PLATFORM",null),recipient=party(r,"EXPERT",r.fixture().expert());
  long sd=id(send(r.admin(),UserTypeEnum.ADMIN,HttpMethod.POST,"/admin/payments/"+r.fixture().payment()+"/settlement-drafts",null,null));
  long draft=id(send(r.admin(),UserTypeEnum.ADMIN,HttpMethod.POST,"/admin/settlement-drafts/"+sd+"/tax-invoice-drafts",Map.of("kind","PLATFORM_TO_EXPERT"),"invoice"));
  var body=Map.of("supplier_snapshot_id",supplier,"recipient_snapshot_id",recipient,"supply",90,"vat",10,"taxation","TAXABLE","write_date","2026-01-01");
  long invoice=id(send(r.admin(),UserTypeEnum.ADMIN,HttpMethod.POST,"/admin/tax-invoice-drafts/"+draft+"/invoices",body,null));
  long op=id(send(r.admin(),UserTypeEnum.ADMIN,HttpMethod.POST,"/admin/financial-tax-invoices/"+invoice+"/attempts",null,"tax-op"));
  assertThat(id(send(r.admin(),UserTypeEnum.ADMIN,HttpMethod.POST,"/admin/financial-tax-invoices/"+invoice+"/attempts",null,"tax-op"))).isEqualTo(op);
  finance.submitFunction=c->{var e=finance.evidence(c,FinancialGateway.State.SUCCEEDED);return new FinancialGateway.Evidence(e.reference(),e.externalId(),e.state(),e.amount(),e.currency(),false,false,FinancialGateway.NtsState.FAILED);};
  execute(r.admin(),op);
  var result=send(r.admin(),UserTypeEnum.ADMIN,HttpMethod.GET,"/admin/financial-tax-invoices/"+invoice,null,null);
  assertThat(result.data().path("issued").asBoolean()).isTrue();assertThat(result.data().path("nts_state").asText()).isEqualTo("FAILED");
  lookup(r.admin(),op);int writes=finance.submits.get();
  long duplicateDraft=id(send(r.admin(),UserTypeEnum.ADMIN,HttpMethod.POST,"/admin/settlement-drafts/"+sd+"/tax-invoice-drafts",Map.of("kind","PLATFORM_TO_EXPERT"),"duplicate-base"));
  assertThat(send(r.admin(),UserTypeEnum.ADMIN,HttpMethod.POST,"/admin/tax-invoice-drafts/"+duplicateDraft+"/invoices",body,null).status()).isEqualTo(409);
  long correctionDraft=id(send(r.admin(),UserTypeEnum.ADMIN,HttpMethod.POST,"/admin/settlement-drafts/"+sd+"/tax-invoice-drafts",
    Map.of("kind","PLATFORM_TO_EXPERT","original_draft_id",draft),"corrected"));
  var correction=new HashMap<String,Object>(body);correction.put("supply",-90);correction.put("vat",-10);
  correction.put("original_invoice_id",invoice);correction.put("modification","CANCELLATION_OF_CONTRACT");
  long next=id(send(r.admin(),UserTypeEnum.ADMIN,HttpMethod.POST,"/admin/tax-invoice-drafts/"+correctionDraft+"/invoices",correction,null));
  assertThat(next).isNotEqualTo(invoice);
  assertThat(taxInvoices.findById(invoice).orElseThrow().getSupply()).isEqualByComparingTo("90");
  assertThat(taxInvoices.findById(next).orElseThrow().getSupply()).isEqualByComparingTo("-90");assertThat(finance.submits).hasValue(writes);
 }
 @Test void lookupLeasePreventsTwoWorkersAndExpiresWithoutResubmission() throws Exception {
  var r=financialFixture();long op=refund(r,100,"lease");
  finance.submitFunction=c->{throw new FinancialGatewayException(FinancialGatewayException.Failure.TIMEOUT);};execute(r.admin(),op);
  var first=persistenceFinance.claimWorker(op);var second=persistenceFinance.claimWorker(op);
  assertThat(first.execute()).isTrue();assertThat(second.execute()).isFalse();
  ((MutableClock)clock).advance(46);var recovered=persistenceFinance.claimWorker(op);assertThat(recovered.execute()).isTrue();
  assertThatThrownBy(()->persistenceFinance.complete(first,finance.evidence(first.command(),FinancialGateway.State.SUCCEEDED))).isInstanceOf(CustomException.class);
  persistenceFinance.complete(recovered,finance.evidence(recovered.command(),FinancialGateway.State.SUCCEEDED));
  assertThat(finance.submits).hasValue(1);journalBalanced(r);
 }
 @Test void lookupWorkerHasBoundedBackoffAndNeverSubmitsWrites() throws Exception {
  var r=financialFixture();long op=refund(r,100,"worker");
  finance.submitFunction=c->{throw new FinancialGatewayException(FinancialGatewayException.Failure.TIMEOUT);};execute(r.admin(),op);
  finance.lookupFunction=c->finance.evidence(c,FinancialGateway.State.PENDING);
  for(int i=0;i<8;i++){((MutableClock)clock).advance(4000);workflow.runDue();}
  assertThat(financeOperations.findById(op).orElseThrow().getState()).isEqualTo(FinancialOperation.State.BLOCKED);
  assertThat(financeOperations.findById(op).orElseThrow().getLookupCount()).isEqualTo(8);assertThat(finance.submits).hasValue(1);
  assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM financial_operation_audits WHERE operation_id=?",Long.class,op)).isGreaterThan(8);
 }

 @Test void providerConfigurationChangeCannotMoveAnExistingOperationToAnotherStore() throws Exception {
  var r=financialFixture();long op=refund(r,100,"provider-change");
  finance.providerIdentity=new FinancialGateway.Identity("TEST_FAKE","synthetic-merchant","other-store","TEST");
  assertThat(execute(r.admin(),op).data().path("state").asText()).isEqualTo("BLOCKED");
  assertThat(finance.submits).hasValue(0);assertThat(balance(r).path("refunded").decimalValue()).isEqualByComparingTo("0");
 }
 @Test void reviewHoldCannotBeUsedAsRefundApproval() throws Exception {
  var r=financialFixture();long rid=id(send(r.fixture().client(),UserTypeEnum.CLIENT,HttpMethod.POST,"/payments/"+r.fixture().payment()+"/refunds",Map.of("amount",100),"hold"));
  long approval=id(send(r.admin(),UserTypeEnum.ADMIN,HttpMethod.POST,"/admin/refunds/"+rid+"/decisions",
    Map.of("decision","HOLD","expected_refund_version",0,"reason_code","MORE_INFORMATION"),null));
  assertThat(send(r.admin(),UserTypeEnum.ADMIN,HttpMethod.POST,"/admin/refunds/"+rid+"/attempts",Map.of("approval_id",approval),"hold-op").status()).isEqualTo(409);
  assertThat(finance.submits).hasValue(0);
 }
 @Test void readonlyHistoryAndClientRefundDecisionAreScopedAndContainNoPrivateDetails() throws Exception {
  var r=financialFixture();long op=refund(r,100,"history");execute(r.admin(),op);
  var accountList=send(r.fixture().expert(),UserTypeEnum.EXPERT,HttpMethod.GET,"/expert/me/settlement-accounts",null,null);
  assertThat(accountList.status()).isEqualTo(200);assertThat(accountList.data().isArray()).isTrue();
  assertThat(accountList.data().toString()).doesNotContain("000000123456","encrypted_details");
  var audit=send(r.admin(),UserTypeEnum.ADMIN,HttpMethod.GET,"/admin/financial-operations/"+op+"/audits",null,null);
  assertThat(audit.status()).isEqualTo(200);assertThat(audit.data().size()).isGreaterThanOrEqualTo(3);
  var lines=send(r.admin(),UserTypeEnum.ADMIN,HttpMethod.GET,"/admin/payments/"+r.fixture().payment()+"/ledger",null,null);
  assertThat(lines.status()).isEqualTo(200);assertThat(lines.data().size()).isGreaterThanOrEqualTo(6);
  Long refundId=financeOperations.findById(op).orElseThrow().getRefundRequestId();
  var decision=send(r.fixture().client(),UserTypeEnum.CLIENT,HttpMethod.GET,"/payments/"+r.fixture().payment()+"/refunds/"+refundId,null,null);
  assertThat(decision.status()).isEqualTo(200);assertThat(decision.data().path("approval_decision").asText()).isEqualTo("APPROVED");
  var other=order(PaymentMethod.CARD);
  assertThat(send(other.client(),UserTypeEnum.CLIENT,HttpMethod.GET,"/payments/"+r.fixture().payment()+"/refunds/"+refundId,null,null).status()).isEqualTo(403);
  assertThat(send(r.fixture().client(),UserTypeEnum.CLIENT,HttpMethod.GET,"/admin/financial-operations/"+op+"/audits",null,null).status()).isEqualTo(403);
 }
 @Test void rejectedRefundReleasesBusinessHoldWithoutRecordingProviderFailure() throws Exception {
  var r=financialFixture();long rid=id(send(r.fixture().client(),UserTypeEnum.CLIENT,HttpMethod.POST,"/payments/"+r.fixture().payment()+"/refunds",Map.of("amount",100),"reject"));
  assertThat(send(r.admin(),UserTypeEnum.ADMIN,HttpMethod.POST,"/admin/refunds/"+rid+"/decisions",
    Map.of("decision","REJECTED","expected_refund_version",0,"reason_code","NOT_ELIGIBLE"),null).status()).isEqualTo(200);
  var status=send(r.fixture().client(),UserTypeEnum.CLIENT,HttpMethod.GET,"/payments/"+r.fixture().payment()+"/refunds/"+rid,null,null);
  assertThat(status.data().path("state").asText()).isEqualTo("REJECTED");
  assertThat(payoutApproval(r,900)).isPositive();assertThat(finance.submits).hasValue(0);
 }
 @Test void payoutCanBeHeldBeforeAccountExistsButCannotBeApprovedWithoutAnAccount() throws Exception {
  var f=order(PaymentMethod.CARD);long attempt=ready(f);confirm(f,attempt,"hold-no-account");long admin=newAdmin();
  var initialized=send(admin,UserTypeEnum.ADMIN,HttpMethod.POST,"/admin/payments/"+f.payment()+"/settlements",null,null);
  var body=new HashMap<String,Object>(Map.of("decision","HOLD","expected_balance_version",initialized.data().path("version").asLong(),"amount",900,"reason_code","ACCOUNT_REQUIRED"));
  var hold=send(admin,UserTypeEnum.ADMIN,HttpMethod.POST,"/admin/payments/"+f.payment()+"/payout-approvals",body,null);
  assertThat(hold.status()).isEqualTo(200);assertThat(hold.data().path("reason_code").asText()).isEqualTo("ACCOUNT_REQUIRED");
  body.put("decision","APPROVED");body.put("expected_balance_version",hold.data().path("balance_version").asLong());
  assertThat(send(admin,UserTypeEnum.ADMIN,HttpMethod.POST,"/admin/payments/"+f.payment()+"/payout-approvals",body,null).status()).isEqualTo(400);
  assertThat(finance.submits).hasValue(0);
 }

 @Test void payoutTimeoutKeepsReservationAndLookupCompletesWithoutSendingAgain() throws Exception {
  var r=financialFixture();long approval=payoutApproval(r,900),op=payout(r,approval,"payout-timeout");
  finance.submitFunction=c->{throw new FinancialGatewayException(FinancialGatewayException.Failure.TIMEOUT);};
  assertThat(execute(r.admin(),op).data().path("state").asText()).isEqualTo("UNKNOWN");
  assertThat(balance(r).path("reserved").decimalValue()).isEqualByComparingTo("900");
  assertThat(balance(r).path("paid").decimalValue()).isEqualByComparingTo("0");
  execute(r.admin(),op);assertThat(finance.submits).hasValue(1);
  assertThat(lookup(r.admin(),op).data().path("state").asText()).isEqualTo("SUCCEEDED");
  assertThat(balance(r).path("paid").decimalValue()).isEqualByComparingTo("900");
  assertThat(finance.submits).hasValue(1);journalBalanced(r);
 }
 @Test void sameProviderCancellationCannotBeAssignedToTwoFinancialTransactions() throws Exception {
  var first=financialFixture();var second=financialFixture();
  long one=refund(first,100,"first-provider-id"),two=refund(second,100,"second-provider-id");
  finance.submitFunction=c->new FinancialGateway.Evidence(c.reference(),"synthetic-shared-cancellation",FinancialGateway.State.SUCCEEDED,
    c.payload().path("amount").decimalValue(),"KRW",false,false,FinancialGateway.NtsState.NOT_SENT);
  assertThat(execute(first.admin(),one).data().path("state").asText()).isEqualTo("SUCCEEDED");
  assertThat(execute(second.admin(),two).data().path("state").asText()).isEqualTo("UNKNOWN");
  assertThat(balance(first).path("refunded").decimalValue()).isEqualByComparingTo("100");
  assertThat(balance(second).path("refunded").decimalValue()).isEqualByComparingTo("0");
  journalBalanced(first);journalBalanced(second);
 }
 @Test void twoConcurrentWorkersOnlyPerformOneProviderLookupAndOneAdjustment() throws Exception {
  var r=financialFixture();long op=refund(r,100,"concurrent-workers");
  finance.submitFunction=c->{throw new FinancialGatewayException(FinancialGatewayException.Failure.TIMEOUT);};
  execute(r.admin(),op);
  CountDownLatch entered=new CountDownLatch(1),release=new CountDownLatch(1);
  finance.lookupFunction=c->{
   entered.countDown();
   try {if(!release.await(10,TimeUnit.SECONDS))throw new AssertionError("Synthetic lookup latch timeout");}
   catch(InterruptedException e){Thread.currentThread().interrupt();throw new AssertionError();}
   return finance.evidence(c,FinancialGateway.State.SUCCEEDED);
  };
  var pool=Executors.newFixedThreadPool(2);
  try {
   var first=pool.submit(()->workflow.reconcile(r.admin(),op));
   assertThat(entered.await(10,TimeUnit.SECONDS)).isTrue();
   var second=pool.submit(()->workflow.reconcile(r.admin(),op));
   assertThat(second.get(10,TimeUnit.SECONDS).getState()).isEqualTo("UNKNOWN");
   assertThat(finance.lookups).hasValue(1);release.countDown();
   assertThat(first.get(10,TimeUnit.SECONDS).getState()).isEqualTo("SUCCEEDED");
   assertThat(balance(r).path("refunded").decimalValue()).isEqualByComparingTo("100");
   assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM settlement_adjustments WHERE operation_id=?",Long.class,op)).isEqualTo(1);
  }finally{release.countDown();pool.shutdownNow();}
 }
 @Test void exhaustedUnknownPayoutStillBlocksAccountChangesAndNewPayouts() throws Exception {
  var r=financialFixture();long approval=payoutApproval(r,300),op=payout(r,approval,"exhausted-payout");
  finance.submitFunction=c->{throw new FinancialGatewayException(FinancialGatewayException.Failure.TIMEOUT);};execute(r.admin(),op);
  finance.lookupFunction=c->finance.evidence(c,FinancialGateway.State.PENDING);
  for(int i=0;i<8;i++){((MutableClock)clock).advance(4000);workflow.runDue();}
  assertThat(financeOperations.findById(op).orElseThrow().getState()).isEqualTo(FinancialOperation.State.BLOCKED);
  assertThat(send(r.fixture().expert(),UserTypeEnum.EXPERT,HttpMethod.POST,"/expert/me/settlement-accounts",
    Map.of("bank","KOOKMIN","number","000000123456","holder","Synthetic Person","contactName","Synthetic Person","contactEmail","synthetic@example.test","contactPhone","01000000000"),null).status()).isEqualTo(409);
  assertThat(send(r.admin(),UserTypeEnum.ADMIN,HttpMethod.POST,"/admin/payments/"+r.fixture().payment()+"/payout-approvals",
    Map.of("decision","APPROVED","expected_balance_version",balance(r).path("version").asLong(),"account_id",r.account(),"expected_account_revision",1,"amount",100,"reason_code","REVIEWED"),null).status()).isEqualTo(409);
  assertThat(balance(r).path("reserved").decimalValue()).isEqualByComparingTo("300");assertThat(finance.submits).hasValue(1);
 }
 @Test void exhaustedUnknownRefundDoesNotPermitAnotherCancelAttempt() throws Exception {
  var r=financialFixture();long op=refund(r,100,"exhausted-refund");
  finance.submitFunction=c->{throw new FinancialGatewayException(FinancialGatewayException.Failure.TIMEOUT);};execute(r.admin(),op);
  finance.lookupFunction=c->finance.evidence(c,FinancialGateway.State.PENDING);
  for(int i=0;i<8;i++){((MutableClock)clock).advance(4000);workflow.runDue();}
  long next=id(send(r.fixture().client(),UserTypeEnum.CLIENT,HttpMethod.POST,"/payments/"+r.fixture().payment()+"/refunds",Map.of("amount",100),"new-request"));
  long approval=id(send(r.admin(),UserTypeEnum.ADMIN,HttpMethod.POST,"/admin/refunds/"+next+"/decisions",
    Map.of("decision","APPROVED","expected_refund_version",0,"reason_code","REVIEWED"),null));
  assertThat(send(r.admin(),UserTypeEnum.ADMIN,HttpMethod.POST,"/admin/refunds/"+next+"/attempts",Map.of("approval_id",approval),"new-cancel").status()).isEqualTo(409);
  assertThat(finance.submits).hasValue(1);
 }

 long internalProfile(Ready r,long expected) throws Exception {
  Long info=jdbc.queryForObject("SELECT business_info_id FROM expert_seller_drafts WHERE id=?",Long.class,r.seller());
  var reply=send(r.fixture().expert(),UserTypeEnum.EXPERT,HttpMethod.POST,"/expert/me/settlement-profiles",
   Map.of("business_info_id",info,"business_type","SOLE_PROPRIETOR","virtual_token","virtual_synthetic_account","expected_revision",expected),null);
  assertThat(reply.status()).isEqualTo(200);assertThat(reply.data().path("bank_verified").asBoolean()).isFalse();
  assertThat(reply.data().toString()).doesNotContain("virtual_synthetic_account");
  var reviewed=send(r.admin(),UserTypeEnum.ADMIN,HttpMethod.POST,"/admin/settlement-profiles/"+reply.data().path("id").asLong()+"/reviews",
   Map.of("profile_revision",reply.data().path("revision").asLong(),"decision","APPROVED","documents","ACCEPTED","reason_code","SYNTHETIC_DOCUMENT_REVIEW"),"profile-review");assertThat(reviewed.status()).isEqualTo(200);
  return reply.data().path("revision").asLong();
 }
 @Test void internalReviewWorksWithoutProviderAndAccountChangeInvalidatesIt() throws Exception {
  var r=financialFixture();finance.reset();long profile=internalProfile(r,0);
  finance.enabled=false;
  var path="/admin/payments/"+r.fixture().payment()+"/settlement-reviews";
  var request=Map.of("balance_version",balance(r).path("version").asLong(),"profile_revision",profile);
  var review=send(r.admin(),UserTypeEnum.ADMIN,HttpMethod.POST,path,request,"internal-review");long reviewId=id(review);
  assertThat(send(r.admin(),UserTypeEnum.ADMIN,HttpMethod.POST,path,request,"internal-review").data().path("id").asLong()).isEqualTo(reviewId);
  var decision=Map.of("decision","APPROVED","expected_version",0,"reason_code","LOCAL_REVIEW");
  var approved=send(r.admin(),UserTypeEnum.ADMIN,HttpMethod.POST,path+"/"+reviewId+"/decisions",decision,null);
  assertThat(approved.status()).isEqualTo(200);assertThat(approved.data().path("state").asText()).isEqualTo("APPROVED");
  assertThat(approved.data().path("execution_state").asText()).isEqualTo("POLICY_HOLD");assertThat(approved.data().path("payout_executable").asBoolean()).isFalse();
  assertThat(send(r.admin(),UserTypeEnum.ADMIN,HttpMethod.POST,path+"/"+reviewId+"/decisions",decision,null).status()).isEqualTo(200);
  internalProfile(r,1);
  var history=send(r.admin(),UserTypeEnum.ADMIN,HttpMethod.GET,path,null,null);assertThat(history.data().get(0).path("snapshot_current").asBoolean()).isFalse();
  assertThat(finance.submits).hasValue(0);assertThat(finance.lookups).hasValue(0);journalBalanced(r);
 }
 @Test void internalSettlementListsEnforceRolesAndOwnership() throws Exception {
  var r=financialFixture();internalProfile(r,0);
  var list=send(r.fixture().expert(),UserTypeEnum.EXPERT,HttpMethod.GET,"/expert/me/settlements",null,null);assertThat(list.status()).isEqualTo(200);
  assertThat(send(r.fixture().client(),UserTypeEnum.CLIENT,HttpMethod.GET,"/expert/me/settlements",null,null).status()).isEqualTo(403);
  assertThat(send(r.fixture().expert(),UserTypeEnum.EXPERT,HttpMethod.GET,"/admin/settlements",null,null).status()).isEqualTo(403);
  assertThat(send(r.fixture().client(),UserTypeEnum.ADMIN,HttpMethod.GET,"/admin/settlements",null,null).status()).isEqualTo(403);
  var other=order(PaymentMethod.CARD);
  assertThat(send(other.expert(),UserTypeEnum.EXPERT,HttpMethod.GET,"/expert/me/settlements/"+r.fixture().payment(),null,null).status()).isEqualTo(403);
  assertThat(send(other.expert(),UserTypeEnum.EXPERT,HttpMethod.GET,"/expert/me/settlements/"+r.fixture().payment()+"/payouts",null,null).status()).isEqualTo(403);
  assertThat(send(r.admin(),UserTypeEnum.ADMIN,HttpMethod.GET,"/admin/settlements?size=101",null,null).status()).isEqualTo(400);
 }
 @Test void concurrentInternalReviewsCannotBothDecide() throws Exception {
  var r=financialFixture();internalProfile(r,0);var path="/admin/payments/"+r.fixture().payment()+"/settlement-reviews";
  long review=id(send(r.admin(),UserTypeEnum.ADMIN,HttpMethod.POST,path,Map.of("balance_version",balance(r).path("version").asLong(),"profile_revision",1),"race"));
  long second=newAdmin();var pool=Executors.newFixedThreadPool(2);
  try{
   var one=pool.submit(()->send(r.admin(),UserTypeEnum.ADMIN,HttpMethod.POST,path+"/"+review+"/decisions",Map.of("decision","APPROVED","expected_version",0,"reason_code","CHECKED"),null).status());
   var two=pool.submit(()->send(second,UserTypeEnum.ADMIN,HttpMethod.POST,path+"/"+review+"/decisions",Map.of("decision","REJECTED","expected_version",0,"reason_code","DECLINED"),null).status());
   assertThat(List.of(one.get(),two.get())).containsExactlyInAnyOrder(200,409);
  }finally{pool.shutdownNow();}
 }

 @Test void internalPayoutUnknownUsesLookupAndNeverResends() throws Exception {
  var r=financialFixture();internalProfile(r,0);fakePayout.reset();
  var path="/admin/payments/"+r.fixture().payment()+"/settlement-reviews";
  long review=id(send(r.admin(),UserTypeEnum.ADMIN,HttpMethod.POST,path,Map.of("balance_version",balance(r).path("version").asLong(),"profile_revision",1),"internal-payout"));
  assertThat(send(r.admin(),UserTypeEnum.ADMIN,HttpMethod.POST,path+"/"+review+"/decisions",Map.of("decision","APPROVED","expected_version",0,"reason_code","INTERNAL_REVIEW"),null).status()).isEqualTo(200);
  String reservePath=path+"/"+review+"/payout-reservations";
  long payout=id(send(r.admin(),UserTypeEnum.ADMIN,HttpMethod.POST,reservePath,null,null));
  assertThat(id(send(r.admin(),UserTypeEnum.ADMIN,HttpMethod.POST,reservePath,null,null))).isEqualTo(payout);
  fakePayout.next=PayoutProvider.State.UNKNOWN;
  var result=send(r.admin(),UserTypeEnum.ADMIN,HttpMethod.POST,"/admin/internal-payouts/"+payout+"/executions",null,null);
  assertThat(result.status()).isEqualTo(200);assertThat(result.data().path("state").asText()).isEqualTo("UNKNOWN");
  assertThat(send(r.admin(),UserTypeEnum.ADMIN,HttpMethod.POST,"/admin/internal-payouts/"+payout+"/executions",null,null).status()).isEqualTo(409);
  assertThat(send(r.admin(),UserTypeEnum.ADMIN,HttpMethod.POST,"/admin/internal-payouts/"+payout+"/cancellations",null,null).status()).isEqualTo(409);
  fakePayout.next=PayoutProvider.State.SUCCEEDED;
  var restored=send(r.admin(),UserTypeEnum.ADMIN,HttpMethod.POST,"/admin/internal-payouts/"+payout+"/reconciliations",null,null);
  assertThat(restored.status()).isEqualTo(200);assertThat(restored.data().path("state").asText()).isEqualTo("SUCCEEDED");
  assertThat(restored.data().path("real_transfer_confirmed").asBoolean()).isFalse();
  assertThat(fakePayout.requests).hasValue(1);assertThat(fakePayout.lookups).hasValue(1);
  assertThat(balance(r).path("paid").decimalValue()).isEqualByComparingTo("900");journalBalanced(r);
 }
 @Test void internalPayoutAccountChangeBlocksDispatchAndCanReleaseReservation() throws Exception {
  var r=financialFixture();internalProfile(r,0);fakePayout.reset();var path="/admin/payments/"+r.fixture().payment()+"/settlement-reviews";
  long review=id(send(r.admin(),UserTypeEnum.ADMIN,HttpMethod.POST,path,Map.of("balance_version",balance(r).path("version").asLong(),"profile_revision",1),"stale-account"));
  assertThat(send(r.admin(),UserTypeEnum.ADMIN,HttpMethod.POST,path+"/"+review+"/decisions",Map.of("decision","APPROVED","expected_version",0,"reason_code","CHECKED"),null).status()).isEqualTo(200);
  long payout=id(send(r.admin(),UserTypeEnum.ADMIN,HttpMethod.POST,path+"/"+review+"/payout-reservations",null,null));internalProfile(r,1);
  assertThat(send(r.admin(),UserTypeEnum.ADMIN,HttpMethod.POST,"/admin/internal-payouts/"+payout+"/executions",null,null).status()).isEqualTo(409);
  assertThat(send(r.admin(),UserTypeEnum.ADMIN,HttpMethod.POST,"/admin/internal-payouts/"+payout+"/cancellations",null,null).status()).isEqualTo(200);
  assertThat(balance(r).path("reserved").decimalValue()).isEqualByComparingTo("0");assertThat(fakePayout.requests).hasValue(0);journalBalanced(r);
 }

 @Test void internalTaxDraftsValidateTotalsStayOnPolicyHoldAndPreserveOriginal() throws Exception {
  var r=financialFixture();long supplier=party(r,"PLATFORM",null),recipient=party(r,"EXPERT",r.fixture().expert());finance.reset();finance.enabled=false;
  var body=new HashMap<String,Object>(Map.of("kind","PLATFORM_TO_EXPERT","supplier_snapshot_id",supplier,"recipient_snapshot_id",recipient,"taxation","TAXABLE","write_date","2026-01-01","supply",90,"vat",10,"total",100,"items",List.of(Map.of("item_code","PLATFORM_SERVICE","supply",90,"vat",10))));
  String path="/admin/payments/"+r.fixture().payment()+"/tax-documents";
  var created=send(r.admin(),UserTypeEnum.ADMIN,HttpMethod.POST,path,body,"tax-local");long document=id(created);
  assertThat(created.data().path("state").asText()).isEqualTo("POLICY_HOLD");assertThat(created.data().path("legally_issued").asBoolean()).isFalse();
  assertThat(id(send(r.admin(),UserTypeEnum.ADMIN,HttpMethod.POST,path,body,"tax-local"))).isEqualTo(document);
  var reviewed=send(r.admin(),UserTypeEnum.ADMIN,HttpMethod.POST,"/admin/tax-documents/"+document+"/reviews",Map.of("approve",true,"expected_version",0,"reason_code","TAX_REVIEW_PENDING"),null);
  assertThat(reviewed.status()).isEqualTo(200);assertThat(reviewed.data().path("issuance_executable").asBoolean()).isFalse();
  body.put("original_document_id",document);body.put("modification","CANCELLATION_OF_CONTRACT");body.put("supply",-90);body.put("vat",-10);body.put("total",-100);body.put("items",List.of(Map.of("item_code","CANCELLATION","supply",-90,"vat",-10)));
  var correction=send(r.admin(),UserTypeEnum.ADMIN,HttpMethod.POST,path,body,"correction");assertThat(correction.status()).isEqualTo(200);
  assertThat(correction.data().path("original_document_id").asLong()).isEqualTo(document);
  var original=send(r.admin(),UserTypeEnum.ADMIN,HttpMethod.GET,"/admin/tax-documents/"+document,null,null);
  assertThat(original.status()).isEqualTo(200);
  assertThat(original.data().path("total").decimalValue()).isEqualByComparingTo("100");assertThat(original.data().path("nts_state").asText()).isEqualTo("NOT_SENT");
  assertThat(finance.submits).hasValue(0);assertThat(finance.lookups).hasValue(0);
 }
 @Test void internalTaxDraftRejectsInvalidTotalsAndExpertAccess() throws Exception {
  var r=financialFixture();long supplier=party(r,"PLATFORM",null),recipient=party(r,"EXPERT",r.fixture().expert());
  var body=Map.of("kind","PLATFORM_TO_EXPERT","supplier_snapshot_id",supplier,"recipient_snapshot_id",recipient,"taxation","TAXABLE","write_date","2026-01-01","supply",90,"vat",10,"total",101,"items",List.of(Map.of("item_code","SERVICE","supply",90,"vat",10)));
  String path="/admin/payments/"+r.fixture().payment()+"/tax-documents";
  assertThat(send(r.admin(),UserTypeEnum.ADMIN,HttpMethod.POST,path,body,"bad-total").status()).isEqualTo(400);
  assertThat(send(r.fixture().expert(),UserTypeEnum.EXPERT,HttpMethod.POST,path,body,"expert").status()).isEqualTo(403);
 }
 @Test void refundReviewIsLocalButProviderExecutionRemainsBlocked() throws Exception {
  var r=financialFixture();finance.reset();finance.enabled=false;
  long refund=id(send(r.fixture().client(),UserTypeEnum.CLIENT,HttpMethod.POST,"/payments/"+r.fixture().payment()+"/refunds",Map.of("amount",100),"local-refund"));
  var response=send(r.admin(),UserTypeEnum.ADMIN,HttpMethod.POST,"/admin/refunds/"+refund+"/decisions",Map.of("decision","APPROVED","expected_refund_version",0,"reason_code","LOCAL_REVIEW"),null);
  long approval=id(response);
  assertThat(send(r.admin(),UserTypeEnum.ADMIN,HttpMethod.POST,"/admin/refunds/"+refund+"/attempts",Map.of("approval_id",approval),"blocked-execution").status()).isEqualTo(503);
  assertThat(finance.submits).hasValue(0);assertThat(balance(r).path("refunded").decimalValue()).isEqualByComparingTo("0");
 }

 @Test void internalPayoutThenRefundCreatesRecoveryWithoutChangingGross() throws Exception {
  var r=financialFixture();internalProfile(r,0);fakePayout.reset();var path="/admin/payments/"+r.fixture().payment()+"/settlement-reviews";
  long review=id(send(r.admin(),UserTypeEnum.ADMIN,HttpMethod.POST,path,Map.of("balance_version",balance(r).path("version").asLong(),"profile_revision",1),"pay-then-refund"));
  assertThat(send(r.admin(),UserTypeEnum.ADMIN,HttpMethod.POST,path+"/"+review+"/decisions",Map.of("decision","APPROVED","expected_version",0,"reason_code","CHECKED"),null).status()).isEqualTo(200);
  long payout=id(send(r.admin(),UserTypeEnum.ADMIN,HttpMethod.POST,path+"/"+review+"/payout-reservations",null,null));
  assertThat(send(r.admin(),UserTypeEnum.ADMIN,HttpMethod.POST,"/admin/internal-payouts/"+payout+"/executions",null,null).status()).isEqualTo(200);
  long refund=refund(r,300,"after-payout");assertThat(execute(r.admin(),refund).data().path("state").asText()).isEqualTo("SUCCEEDED");
  assertThat(balance(r).path("gross").decimalValue()).isEqualByComparingTo("1000");
  assertThat(balance(r).path("recoverable").decimalValue()).isEqualByComparingTo("270");
  assertThat(balance(r).path("held").asBoolean()).isTrue();journalBalanced(r);
 }
 @Test void internalPayoutReservationRaceKeepsOneReservation() throws Exception {
  var r=financialFixture();internalProfile(r,0);fakePayout.reset();var path="/admin/payments/"+r.fixture().payment()+"/settlement-reviews";
  long review=id(send(r.admin(),UserTypeEnum.ADMIN,HttpMethod.POST,path,Map.of("balance_version",balance(r).path("version").asLong(),"profile_revision",1),"reserve-race"));
  assertThat(send(r.admin(),UserTypeEnum.ADMIN,HttpMethod.POST,path+"/"+review+"/decisions",Map.of("decision","APPROVED","expected_version",0,"reason_code","CHECKED"),null).status()).isEqualTo(200);
  var pool=Executors.newFixedThreadPool(2);long second=newAdmin();String endpoint=path+"/"+review+"/payout-reservations";
  try{
   var one=pool.submit(()->send(r.admin(),UserTypeEnum.ADMIN,HttpMethod.POST,endpoint,null,null));
   var two=pool.submit(()->send(second,UserTypeEnum.ADMIN,HttpMethod.POST,endpoint,null,null));
   var first=one.get();var last=two.get();assertThat(first.status()).isEqualTo(200);assertThat(last.status()).isEqualTo(200);assertThat(first.data().path("id").asLong()).isEqualTo(last.data().path("id").asLong());
  }finally{pool.shutdownNow();}
  assertThat(balance(r).path("reserved").decimalValue()).isEqualByComparingTo("900");assertThat(fakePayout.requests).hasValue(0);journalBalanced(r);
 }

 long reservedInternal(Ready r,String key) throws Exception {
  internalProfile(r,0);fakePayout.reset();String path="/admin/payments/"+r.fixture().payment()+"/settlement-reviews";
  long review=id(send(r.admin(),UserTypeEnum.ADMIN,HttpMethod.POST,path,Map.of("balance_version",balance(r).path("version").asLong(),"profile_revision",1),key));
  assertThat(send(r.admin(),UserTypeEnum.ADMIN,HttpMethod.POST,path+"/"+review+"/decisions",Map.of("decision","APPROVED","expected_version",0,"reason_code","CHECKED"),null).status()).isEqualTo(200);
  return id(send(r.admin(),UserTypeEnum.ADMIN,HttpMethod.POST,path+"/"+review+"/payout-reservations",null,null));
 }
 @Test void internalPayoutRejectsWrongAmountWithoutReleasingReservation() throws Exception {
  var r=financialFixture();long payout=reservedInternal(r,"wrong-amount");fakePayout.amountOverride=BigDecimal.ONE;
  var result=send(r.admin(),UserTypeEnum.ADMIN,HttpMethod.POST,"/admin/internal-payouts/"+payout+"/executions",null,null);
  assertThat(result.status()).isEqualTo(200);assertThat(result.data().path("state").asText()).isEqualTo("UNKNOWN");
  assertThat(balance(r).path("reserved").decimalValue()).isEqualByComparingTo("900");assertThat(balance(r).path("paid").decimalValue()).isEqualByComparingTo("0");journalBalanced(r);
 }
 @Test void internalPayoutRejectsProviderIdentityChangeBeforeAnyRequest() throws Exception {
  var r=financialFixture();long payout=reservedInternal(r,"provider-change");fakePayout.providerCode="OTHER_PROVIDER";
  assertThat(send(r.admin(),UserTypeEnum.ADMIN,HttpMethod.POST,"/admin/internal-payouts/"+payout+"/executions",null,null).status()).isEqualTo(409);
  assertThat(fakePayout.requests).hasValue(0);
 }
 record TaxFlow(Ready ready,long document,Map<String,Object> body){}
 TaxFlow taxFlow() throws Exception {
  var r=financialFixture();internalProfile(r,0);taxAuthorized.set(true);
  long supplier=party(r,"PLATFORM",null),recipient=party(r,"EXPERT",r.fixture().expert());
  var body=new HashMap<String,Object>(Map.of("kind","PLATFORM_TO_EXPERT","supplier_snapshot_id",supplier,"recipient_snapshot_id",recipient,"taxation","TAXABLE","write_date","2026-01-01","supply",90,"vat",10,"total",100,"items",List.of(Map.of("item_code","SERVICE","supply",90,"vat",10))));
  long doc=id(send(r.admin(),UserTypeEnum.ADMIN,HttpMethod.POST,"/admin/payments/"+r.fixture().payment()+"/tax-documents",body,"completion-tax"));
  assertThat(send(r.admin(),UserTypeEnum.ADMIN,HttpMethod.POST,"/admin/tax-documents/"+doc+"/reviews",Map.of("approve",true,"expected_version",0,"reason_code","SYNTHETIC_TAX_REVIEW"),null).data().path("state").asText()).isEqualTo("APPROVED");
  return new TaxFlow(r,doc,body);
 }
 Reply taxPrepare(TaxFlow f,String key) throws Exception {return send(f.ready().admin(),UserTypeEnum.ADMIN,HttpMethod.POST,"/admin/tax-documents/"+f.document()+"/execution-requests",Map.of("expected_version",1),key);}
 Reply taxExecute(TaxFlow f,long id,boolean lookup) throws Exception {return send(f.ready().admin(),UserTypeEnum.ADMIN,HttpMethod.POST,"/admin/tax-document-executions/"+id+(lookup?"/reconciliations":"/executions"),null,null);}
 @Test void taxTimeoutRecoversByLookupAndNtsTransmissionIsIndependent() throws Exception {
  var f=taxFlow();long execution=id(taxPrepare(f,"issue-once"));fakeTax.timeout=true;
  assertThat(taxExecute(f,execution,false).data().path("state").asText()).isEqualTo("UNKNOWN");
  assertThat(taxExecute(f,execution,false).status()).isEqualTo(409);
  fakeTax.timeout=false;fakeTax.transmission=TaxInvoiceProvider.TransmissionState.FAILED;
  var issued=taxExecute(f,execution,true);assertThat(issued.status()).isEqualTo(200);
  assertThat(issued.data().path("state").asText()).isEqualTo("ISSUED");assertThat(issued.data().path("nts_state").asText()).isEqualTo("FAILED");
  assertThat(issued.data().path("test_issuance").asBoolean()).isTrue();assertThat(issued.data().path("legally_issued").asBoolean()).isFalse();
  fakeTax.transmission=TaxInvoiceProvider.TransmissionState.SUCCEEDED;
  assertThat(taxExecute(f,execution,true).data().path("nts_state").asText()).isEqualTo("SUCCEEDED");
  assertThat(fakeTax.requests).hasValue(1);assertThat(fakeTax.lookups).hasValue(2);
  var events=send(f.ready().admin(),UserTypeEnum.ADMIN,HttpMethod.GET,"/admin/tax-document-executions/"+execution+"/events",null,null);
  assertThat(events.status()).isEqualTo(200);assertThat(events.data().size()).isGreaterThanOrEqualTo(7);
  assertThat(events.data().toString()).doesNotContain("supplierJson","1234567891","lease","snapshot_hash");
 }
 @Test void canonicalInvoiceCannotBeIssuedByBothOldAndNewApis() throws Exception {
  var f=taxFlow();var first=taxPrepare(f,"canonical");long execution=id(first);
  assertThat(id(taxPrepare(f,"canonical"))).isEqualTo(execution);assertThat(taxPrepare(f,"different").status()).isEqualTo(409);
  long invoice=first.data().path("invoice_id").asLong();
  assertThat(send(f.ready().admin(),UserTypeEnum.ADMIN,HttpMethod.POST,"/admin/financial-tax-invoices/"+invoice+"/attempts",null,"legacy-bypass").status()).isEqualTo(409);
  assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM financial_tax_invoices WHERE payment_id=?",Integer.class,f.ready().fixture().payment())).isEqualTo(1);
  assertThat(fakeTax.requests).hasValue(0);
 }
 @Test void taxIssuancePolicyRemainsClosedUntilExplicitlyReviewed() throws Exception {
  var f=taxFlow();taxAuthorized.set(false);
  assertThat(taxPrepare(f,"policy-closed").status()).isEqualTo(503);
  assertThat(fakeTax.requests).hasValue(0);
 }
 @Test void correctionIssuancePreservesOriginalAndDeduplicatesCancellation() throws Exception {
  var f=taxFlow();long original=id(taxPrepare(f,"original"));assertThat(taxExecute(f,original,false).data().path("state").asText()).isEqualTo("ISSUED");
  var body=new HashMap<>(f.body());body.put("original_document_id",f.document());body.put("modification","CANCELLATION_OF_CONTRACT");body.put("supply",-90);body.put("vat",-10);body.put("total",-100);body.put("items",List.of(Map.of("item_code","CANCELLATION","supply",-90,"vat",-10)));
  String path="/admin/payments/"+f.ready().fixture().payment()+"/tax-documents";
  long doc=id(send(f.ready().admin(),UserTypeEnum.ADMIN,HttpMethod.POST,path,body,"correct-once"));
  assertThat(send(f.ready().admin(),UserTypeEnum.ADMIN,HttpMethod.POST,"/admin/tax-documents/"+doc+"/reviews",Map.of("approve",true,"expected_version",0,"reason_code","CORRECTION_REVIEW"),null).status()).isEqualTo(200);
  var corrected=new TaxFlow(f.ready(),doc,body);long execution=id(taxPrepare(corrected,"correction-issue"));assertThat(taxExecute(corrected,execution,false).data().path("state").asText()).isEqualTo("ISSUED");
  long duplicate=id(send(f.ready().admin(),UserTypeEnum.ADMIN,HttpMethod.POST,path,body,"different-correction-draft"));
  assertThat(send(f.ready().admin(),UserTypeEnum.ADMIN,HttpMethod.POST,"/admin/tax-documents/"+duplicate+"/reviews",Map.of("approve",true,"expected_version",0,"reason_code","REVIEW"),null).status()).isEqualTo(200);
  assertThat(taxPrepare(new TaxFlow(f.ready(),duplicate,body),"duplicate-cancel").status()).isEqualTo(409);
  var originalDoc=send(f.ready().admin(),UserTypeEnum.ADMIN,HttpMethod.GET,"/admin/tax-documents/"+f.document(),null,null);
  assertThat(originalDoc.data().path("total").decimalValue()).isEqualByComparingTo("100");assertThat(originalDoc.data().path("legally_issued").asBoolean()).isFalse();
 }
 @Test void refundInvalidatesApprovalAndCreatesSeparateDraftRevision() throws Exception {
  var f=taxFlow();long op=refund(f.ready(),300,"refund-before-issue");assertThat(execute(f.ready().admin(),op).data().path("state").asText()).isEqualTo("SUCCEEDED");
  var stale=send(f.ready().admin(),UserTypeEnum.ADMIN,HttpMethod.GET,"/admin/tax-documents/"+f.document(),null,null);
  assertThat(stale.data().path("snapshot_current").asBoolean()).isFalse();assertThat(taxPrepare(f,"stale").status()).isEqualTo(409);
  var changed=new HashMap<>(f.body());changed.put("supply",63);changed.put("vat",7);changed.put("total",70);changed.put("items",List.of(Map.of("item_code","SERVICE","supply",63,"vat",7)));
  long revised=id(send(f.ready().admin(),UserTypeEnum.ADMIN,HttpMethod.POST,"/admin/tax-documents/"+f.document()+"/revisions",Map.of("expected_version",1,"draft",changed),"revised"));
  assertThat(send(f.ready().admin(),UserTypeEnum.ADMIN,HttpMethod.GET,"/admin/tax-documents/"+f.document(),null,null).data().path("total").decimalValue()).isEqualByComparingTo("100");
  assertThat(send(f.ready().admin(),UserTypeEnum.ADMIN,HttpMethod.POST,"/admin/tax-documents/"+revised+"/reviews",Map.of("approve",true,"expected_version",0,"reason_code","REFUND_REVIEW"),null).data().path("state").asText()).isEqualTo("APPROVED");
  assertThat(taxPrepare(new TaxFlow(f.ready(),revised,changed),"new-snapshot").status()).isEqualTo(200);journalBalanced(f.ready());
 }
 @Test void processDeathAfterIssuanceRequestIsRecoveredWithoutReissue() throws Exception {
  var f=taxFlow();long execution=id(taxPrepare(f,"crash"));var run=taxPersistence.claimAdmin(f.ready().admin(),execution,false);
  fakeTax.request(run.request());((MutableClock)clock).advance(46);assertThat(taxExecution.runDue()).isGreaterThanOrEqualTo(1);
  var restored=send(f.ready().admin(),UserTypeEnum.ADMIN,HttpMethod.GET,"/admin/tax-document-executions/"+execution,null,null);
  assertThat(restored.data().path("state").asText()).isEqualTo("ISSUED");assertThat(fakeTax.requests).hasValue(1);
 }
 @Test void taxAmountMismatchIsUnknownAndCannotBeRetriedAsIssuance() throws Exception {
  var f=taxFlow();long execution=id(taxPrepare(f,"wrong-evidence"));fakeTax.wrongAmount=true;
  assertThat(taxExecute(f,execution,false).data().path("state").asText()).isEqualTo("UNKNOWN");
  assertThat(taxExecute(f,execution,false).status()).isEqualTo(409);fakeTax.wrongAmount=false;
  assertThat(taxExecute(f,execution,true).data().path("state").asText()).isEqualTo("ISSUED");assertThat(fakeTax.requests).hasValue(1);
 }
 @Test void profileReviewChangeInvalidatesPriorSettlementAndTaxApprovals() throws Exception {
  var f=taxFlow();var r=f.ready();long profile=send(r.fixture().expert(),UserTypeEnum.EXPERT,HttpMethod.GET,"/expert/me/settlement-profiles",null,null).data().get(0).path("id").asLong();
  var history=send(r.admin(),UserTypeEnum.ADMIN,HttpMethod.GET,"/admin/settlement-profiles/"+profile+"/reviews",null,null);
  long previous=history.data().get(0).path("id").asLong();
  String settlementPath="/admin/payments/"+r.fixture().payment()+"/settlement-reviews";
  long review=id(send(r.admin(),UserTypeEnum.ADMIN,HttpMethod.POST,settlementPath,Map.of("balance_version",balance(r).path("version").asLong(),"profile_revision",1),"source-review"));
  assertThat(send(r.admin(),UserTypeEnum.ADMIN,HttpMethod.POST,settlementPath+"/"+review+"/decisions",Map.of("decision","APPROVED","expected_version",0,"reason_code","REVIEW"),null).status()).isEqualTo(200);
  var held=send(r.admin(),UserTypeEnum.ADMIN,HttpMethod.POST,"/admin/settlement-profiles/"+profile+"/reviews",Map.of("profile_revision",1,"expected_review_id",previous,"decision","ON_HOLD","documents","MISSING","reason_code","NEW_DOCUMENT_REQUIRED"),"hold-profile");
  assertThat(held.status()).isEqualTo(200);assertThat(taxPrepare(f,"held-documents").status()).isEqualTo(409);
  var stale=send(r.admin(),UserTypeEnum.ADMIN,HttpMethod.GET,settlementPath,null,null);assertThat(stale.data().get(0).path("snapshot_current").asBoolean()).isFalse();
  assertThat(send(r.admin(),UserTypeEnum.ADMIN,HttpMethod.POST,settlementPath+"/"+review+"/payout-reservations",null,null).status()).isEqualTo(409);
  assertThat(send(r.fixture().client(),UserTypeEnum.CLIENT,HttpMethod.GET,"/expert/me/settlement-profiles/"+profile+"/reviews",null,null).status()).isEqualTo(403);
 }
 @Test void profileReviewIsIdempotentAndCompetingAdminsUseExpectedReview() throws Exception {
  var r=financialFixture();internalProfile(r,0);long profile=send(r.fixture().expert(),UserTypeEnum.EXPERT,HttpMethod.GET,"/expert/me/settlement-profiles",null,null).data().get(0).path("id").asLong();
  long previous=send(r.admin(),UserTypeEnum.ADMIN,HttpMethod.GET,"/admin/settlement-profiles/"+profile+"/reviews",null,null).data().get(0).path("id").asLong();String path="/admin/settlement-profiles/"+profile+"/reviews";
  var body=Map.of("profile_revision",1,"expected_review_id",previous,"decision","APPROVED","documents","ACCEPTED","reason_code","REVIEW");
  var pool=Executors.newFixedThreadPool(2);long second=newAdmin();
  try{var one=pool.submit(()->send(r.admin(),UserTypeEnum.ADMIN,HttpMethod.POST,path,body,"review-one").status());var two=pool.submit(()->send(second,UserTypeEnum.ADMIN,HttpMethod.POST,path,body,"review-two").status());assertThat(List.of(one.get(),two.get())).containsExactlyInAnyOrder(200,409);}finally{pool.shutdownNow();}
  var other=order(PaymentMethod.CARD);assertThat(send(other.expert(),UserTypeEnum.EXPERT,HttpMethod.GET,"/expert/me/settlement-profiles/"+profile+"/reviews",null,null).status()).isEqualTo(403);
 }
 @Test void recoveryWorkersOnlyLookupAndAppendOneClaimPerPayout() throws Exception {
  var r=financialFixture();long payout=reservedInternal(r,"worker-payout");fakePayout.next=PayoutProvider.State.UNKNOWN;
  assertThat(send(r.admin(),UserTypeEnum.ADMIN,HttpMethod.POST,"/admin/internal-payouts/"+payout+"/executions",null,null).status()).isEqualTo(200);
  fakePayout.next=PayoutProvider.State.SUCCEEDED;((MutableClock)clock).advance(31);var pool=Executors.newFixedThreadPool(2);
  try{var a=pool.submit(()->internalPayouts.runDue());var b=pool.submit(()->internalPayouts.runDue());a.get();b.get();}finally{pool.shutdownNow();}
  assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM financial_audit_events WHERE payout_id=? AND code='LOOKUP_CLAIM'",Integer.class,payout)).isEqualTo(1);
  assertThat(fakePayout.requests).hasValue(1);assertThat(balance(r).path("paid").decimalValue()).isEqualByComparingTo("900");journalBalanced(r);
  var log=send(r.admin(),UserTypeEnum.ADMIN,HttpMethod.GET,"/admin/internal-payouts/"+payout+"/events",null,null);assertThat(log.status()).isEqualTo(200);assertThat(log.data().size()).isGreaterThanOrEqualTo(5);
 }
 @Test void concurrentTaxExecutionRequestsCreateOneCanonicalInvoice() throws Exception {
  var f=taxFlow();var pool=Executors.newFixedThreadPool(2);
  try{var a=pool.submit(()->taxPrepare(f,"race-tax"));var b=pool.submit(()->taxPrepare(f,"race-tax"));var one=a.get();var two=b.get();assertThat(one.status()).isEqualTo(200);assertThat(two.status()).isEqualTo(200);assertThat(one.data().path("id").asLong()).isEqualTo(two.data().path("id").asLong());}finally{pool.shutdownNow();}
  assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM financial_tax_invoices WHERE payment_id=?",Integer.class,f.ready().fixture().payment())).isEqualTo(1);
 }

 @Test void legacyCanonicalInvoiceAlsoBlocksNewExecutionPath() throws Exception {
  var f=taxFlow();var r=f.ready();
  long settlement=id(send(r.admin(),UserTypeEnum.ADMIN,HttpMethod.POST,"/admin/payments/"+r.fixture().payment()+"/settlement-drafts",null,null));
  long draft=id(send(r.admin(),UserTypeEnum.ADMIN,HttpMethod.POST,"/admin/settlement-drafts/"+settlement+"/tax-invoice-drafts",Map.of("kind","PLATFORM_TO_EXPERT"),"legacy-first"));
  var body=Map.of("supplier_snapshot_id",f.body().get("supplier_snapshot_id"),"recipient_snapshot_id",f.body().get("recipient_snapshot_id"),"supply",90,"vat",10,"taxation","TAXABLE","write_date","2026-01-01");
  assertThat(send(r.admin(),UserTypeEnum.ADMIN,HttpMethod.POST,"/admin/tax-invoice-drafts/"+draft+"/invoices",body,null).status()).isEqualTo(200);
  assertThat(taxPrepare(f,"new-bypass").status()).isEqualTo(409);assertThat(fakeTax.requests).hasValue(0);
 }
 @Test void taxExecutionAndAuditEndpointsRejectWrongRoles() throws Exception {
  var f=taxFlow();long execution=id(taxPrepare(f,"secure-execution"));var r=f.ready();
  assertThat(send(r.fixture().client(),UserTypeEnum.CLIENT,HttpMethod.GET,"/admin/tax-document-executions/"+execution,null,null).status()).isEqualTo(403);
  assertThat(send(r.fixture().expert(),UserTypeEnum.EXPERT,HttpMethod.POST,"/admin/tax-document-executions/"+execution+"/executions",null,null).status()).isEqualTo(403);
  assertThat(send(r.fixture().client(),UserTypeEnum.ADMIN,HttpMethod.GET,"/admin/tax-document-executions/"+execution+"/events",null,null).status()).isEqualTo(403);
  assertThat(fakeTax.requests).hasValue(0);
 }

 @Test void membershipPlansAndDiagnosisSettlementRemainIndependentInMySql() throws Exception {
  var r=financialFixture();
  assertThat(jdbc.queryForObject("SELECT membership_grade FROM expert_profiles WHERE user_id=?",String.class,r.fixture().expert())).isEqualTo("BUSINESS");
  String verification=jdbc.queryForObject("SELECT verification_status FROM expert_profiles WHERE user_id=?",String.class,r.fixture().expert());
  jdbc.update("INSERT INTO subscription_plans(grade,plan_version,amount,currency,period_months,is_active,created_at) VALUES('PREMIUM',1,24000,'KRW',1,TRUE,NOW())");
  jdbc.update("UPDATE expert_profiles SET membership_grade='PREMIUM' WHERE user_id=?",r.fixture().expert());
  var plans=send(r.fixture().expert(),UserTypeEnum.EXPERT,HttpMethod.GET,"/subscription-plans",null,null);
  assertThat(plans.status()).isEqualTo(200);assertThat(plans.data().isArray()).isTrue();
  assertThat(plans.data()).anySatisfy(plan->assertThat(plan.path("amount").decimalValue()).isEqualByComparingTo("24000"));
  assertThat(jdbc.queryForObject("SELECT amount FROM payments WHERE id=?",BigDecimal.class,r.fixture().payment())).isEqualByComparingTo("1000");
  assertThat(balance(r).path("platform").decimalValue()).isEqualByComparingTo("100");assertThat(balance(r).path("expert").decimalValue()).isEqualByComparingTo("900");
  assertThat(jdbc.queryForObject("SELECT verification_status FROM expert_profiles WHERE user_id=?",String.class,r.fixture().expert())).isEqualTo(verification);
  assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM subscription_payments WHERE expert_user_id=?",Integer.class,r.fixture().expert())).isZero();
  assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM payment_attempts WHERE payment_id=?",Integer.class,r.fixture().payment())).isEqualTo(1);
  journalBalanced(r);assertThat(finance.submits).hasValue(0);
 }
}
