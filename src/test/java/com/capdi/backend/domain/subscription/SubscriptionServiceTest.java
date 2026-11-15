package com.capdi.backend.domain.subscription;

import com.capdi.backend.domain.expert.entity.MembershipGradeEnum;
import com.capdi.backend.domain.subscription.dto.SubscriptionDtos.*;
import com.capdi.backend.domain.subscription.exception.SubscriptionException;
import com.capdi.backend.domain.subscription.gateway.SubscriptionPgClient;
import com.capdi.backend.domain.subscription.model.SubscriptionModels.PaymentStatus;
import com.capdi.backend.domain.subscription.repository.SubscriptionRepository;
import com.capdi.backend.domain.subscription.service.*;
import com.capdi.backend.global.exception.CustomException;
import com.capdi.backend.global.exception.ErrorCode;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.*;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import javax.sql.DataSource;
import java.math.BigDecimal;
import java.time.*;
import java.util.Base64;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.assertj.core.api.Assertions.*;

/** Real JDBC transactions, schema constraints and competing requests; no network or real billing. */
@SpringJUnitConfig(SubscriptionServiceTest.Config.class)
class SubscriptionServiceTest {
    static final Instant NOW = Instant.parse("2026-01-31T10:00:00Z");
    @Configuration @EnableTransactionManagement
    @Import({SubscriptionRepository.class, SubscriptionTransactions.class, SubscriptionService.class})
    static class Config {
        @Bean DataSource dataSource() {
            return new DriverManagerDataSource("jdbc:h2:mem:subscription;MODE=MySQL;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=5000", "sa", "");
        }
        @Bean JdbcTemplate jdbc(DataSource dataSource) { return new JdbcTemplate(dataSource); }
        @Bean PlatformTransactionManager transactionManager(DataSource dataSource) { return new DataSourceTransactionManager(dataSource); }
        @Bean("subscriptionClock") Clock clock() { return Clock.fixed(NOW, ZoneOffset.UTC); }
        @Bean BillingKeyCipher cipher() { return new BillingKeyCipher(Base64.getEncoder().encodeToString(new byte[32])); }
        @Bean FakePg pg() { return new FakePg(); }
    }

    @Autowired SubscriptionService service;
    @Autowired SubscriptionTransactions transactions;
    @Autowired JdbcTemplate jdbc;
    @Autowired DataSource dataSource;
    @Autowired FakePg pg;

    @BeforeEach void setup() {
        pg.reset();
        jdbc.execute("DROP ALL OBJECTS");
        jdbc.execute("CREATE TABLE users (id BIGINT PRIMARY KEY, user_type VARCHAR(20) NOT NULL)");
        jdbc.execute("CREATE TABLE expert_profiles (id BIGINT PRIMARY KEY, user_id BIGINT UNIQUE, membership_grade VARCHAR(20) DEFAULT 'BUSINESS', updated_at TIMESTAMP)");
        new ResourceDatabasePopulator(new ClassPathResource("db/migration/V19__add_expert_subscriptions.sql")).execute(dataSource);
        jdbc.update("INSERT INTO users VALUES (1, 'EXPERT'), (2, 'EXPERT'), (3, 'CLIENT'), (4, 'ADMIN')");
        jdbc.update("INSERT INTO expert_profiles (id, user_id) VALUES (11, 1), (12, 2)");
        jdbc.update("""
                INSERT INTO subscription_plans (id, grade, plan_version, amount, currency, period_months, is_active, created_at)
                VALUES (1, 'SPECIAL', 1, 12000, 'KRW', 1, TRUE, CURRENT_TIMESTAMP),
                       (2, 'PREMIUM', 1, 24000, 'KRW', 1, TRUE, CURRENT_TIMESTAMP)
                """);
    }

    CreateRequest request() {
        var registration = service.prepare(1L, 1L);
        return new CreateRequest(1L, registration.registrationId(), "test-only-registration-token");
    }
    String grade() { return jdbc.queryForObject("SELECT membership_grade FROM expert_profiles WHERE id=11", String.class); }
    int payments() { return jdbc.queryForObject("SELECT COUNT(*) FROM subscription_payments", Integer.class); }

    @Test void firstPaymentUsesServerPriceEncryptsKeyAndActivatesAtomically() {
        var result = service.create(1L, "first", request());
        assertThat(result.created()).isTrue();
        assertThat(result.payment().status()).isEqualTo(PaymentStatus.PAID);
        assertThat(result.payment().amount()).isEqualByComparingTo("12000");
        assertThat(result.payment().periodStart()).isEqualTo(LocalDateTime.ofInstant(NOW, ZoneOffset.UTC));
        assertThat(result.payment().periodEnd()).isEqualTo(LocalDateTime.of(2026, 2, 28, 10, 0));
        assertThat(grade()).isEqualTo("SPECIAL");
        assertThat(service.current(1).membershipGrade()).isEqualTo(MembershipGradeEnum.SPECIAL);
        assertThat(service.current(1).autoRenew()).isFalse();
        String stored = jdbc.queryForObject("SELECT encrypted_billing_key FROM expert_subscriptions", String.class);
        assertThat(stored).startsWith("v1.").doesNotContain("test-only-billing-key");
        assertThat(pg.chargeCalls.get()).isEqualTo(1);
        jdbc.update("UPDATE subscription_plans SET amount=13000 WHERE id=1");
        assertThat(service.payment(1, result.payment().orderId()).amount()).isEqualByComparingTo("12000");
    }

    @Test void sameKeyReplaysWithoutReusingOneTimeTokenOrRecharging() {
        var request = request();
        var first = service.create(1, "same", request);
        var replay = service.create(1, "same", request);
        assertThat(replay.created()).isFalse();
        assertThat(replay.payment()).isEqualTo(first.payment());
        assertThat(pg.registerCalls.get()).isEqualTo(1);
        assertThat(pg.chargeCalls.get()).isEqualTo(1);
        assertThat(payments()).isEqualTo(1);
    }

    @Test void sameKeyDifferentRequestAndDifferentKeySameRegistrationCannotChargeAgain() {
        var request = request();
        service.create(1, "same", request);
        assertThatThrownBy(() -> service.create(1, "same", new CreateRequest(2L, request.registrationId(), request.registrationToken())))
                .isInstanceOf(SubscriptionException.class).hasMessageContaining("멱등키");
        assertThatThrownBy(() -> service.create(1, "different", request)).isInstanceOf(SubscriptionException.class);
        assertThat(pg.chargeCalls.get()).isEqualTo(1);
    }

    @Test void anotherExpertCannotUseRegistrationOrReadPayment() {
        var request = request();
        assertThatThrownBy(() -> service.create(2, "theft", request)).isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException)e).getErrorCode()).isEqualTo(ErrorCode.FORBIDDEN);
        assertThat(pg.registerCalls.get()).isZero();
        var payment = service.create(1, "owner", request).payment();
        assertThat(service.payments(2, 1)).isEmpty();
        assertThatThrownBy(() -> service.payment(2, payment.orderId())).isInstanceOf(CustomException.class);
    }

    @ParameterizedTest @ValueSource(longs = {3, 4})
    void nonExpertsAreRejectedAtServiceBoundary(long userId) {
        assertThatThrownBy(() -> service.prepare(userId, 1)).isInstanceOf(CustomException.class);
        assertThatThrownBy(() -> service.current(userId)).isInstanceOf(CustomException.class);
        assertThat(payments()).isZero();
    }

    @Test void disabledProviderAndMissingEncryptionConfigurationCannotCreateOrCharge() {
        pg.enabled = false;
        assertThatThrownBy(() -> service.prepare(1, 1)).isInstanceOf(SubscriptionException.class);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM expert_subscriptions", Integer.class)).isZero();
        pg.enabled = true;
        var noKey = new SubscriptionService(transactions, pg, new BillingKeyCipher(""));
        assertThatThrownBy(() -> noKey.prepare(1, 1)).isInstanceOf(SubscriptionException.class);
        assertThat(pg.chargeCalls.get()).isZero();
    }

    @Test void inactivePlanAndExpiredRegistrationAreRejectedBeforeProviderCall() {
        jdbc.update("UPDATE subscription_plans SET is_active=FALSE WHERE id=2");
        assertThatThrownBy(() -> service.prepare(1, 2)).isInstanceOf(SubscriptionException.class);
        var request = request();
        jdbc.update("UPDATE expert_subscriptions SET registration_expires_at='2020-01-01 00:00:00'");
        assertThatThrownBy(() -> service.create(1, "expired", request)).isInstanceOf(SubscriptionException.class);
        assertThat(pg.registerCalls.get()).isZero();
        assertThat(service.prepare(1, 1).registrationId()).isNotEqualTo(request.registrationId());
    }

    @Test void registrationProofForDifferentCustomerCannotBecomeCharge() {
        pg.wrongRegistrationCustomer = true;
        var result = service.create(1, "customer", request());
        assertThat(result.payment().status()).isEqualTo(PaymentStatus.UNKNOWN);
        assertThat(pg.chargeCalls.get()).isZero();
        assertThat(grade()).isEqualTo("BUSINESS");
    }

    @ParameterizedTest @ValueSource(strings = {"amount", "currency", "customer", "order", "paymentId", "approvedAt"})
    void mismatchedPaymentNeverGrantsMembership(String mismatch) {
        pg.mismatch = mismatch;
        var payment = service.create(1, "mismatch", request()).payment();
        assertThat(payment.status()).isEqualTo(PaymentStatus.UNKNOWN);
        assertThat(grade()).isEqualTo("BUSINESS");
    }

    @Test void definitiveDeclineDoesNotActivateAndAllowsFreshRegistration() {
        pg.outcome = SubscriptionPgClient.Outcome.FAILED;
        var request = request();
        var payment = service.create(1, "declined", request).payment();
        assertThat(payment.status()).isEqualTo(PaymentStatus.FAILED);
        assertThat(grade()).isEqualTo("BUSINESS");
        assertThat(service.prepare(1, 2).registrationId()).isNotEqualTo(request.registrationId());
        assertThat(service.create(1, "declined", request).payment().status()).isEqualTo(PaymentStatus.FAILED);
        assertThat(pg.chargeCalls.get()).isEqualTo(1);
    }

    @Test void chargeTimeoutIsRecoveredByLookupWithoutSecondChargeOrPeriodExtension() {
        pg.chargeTimeout = true;
        var request = request();
        var payment = service.create(1, "timeout", request).payment();
        assertThat(payment.status()).isEqualTo(PaymentStatus.UNKNOWN);
        assertThat(grade()).isEqualTo("BUSINESS");
        service.create(1, "timeout", request);
        service.reconcile(payment.orderId());
        var paid = service.payment(1, payment.orderId());
        assertThat(paid.status()).isEqualTo(PaymentStatus.PAID);
        service.reconcile(payment.orderId());
        assertThat(service.payment(1, payment.orderId())).isEqualTo(paid);
        assertThat(pg.chargeCalls.get()).isEqualTo(1);
        assertThat(pg.lookupCalls.get()).isEqualTo(1);
    }

    @Test void registrationTimeoutRecoversWithoutReplayingRegistrationToken() {
        pg.registrationTimeout = true;
        var payment = service.create(1, "registration-timeout", request()).payment();
        assertThat(payment.status()).isEqualTo(PaymentStatus.UNKNOWN);
        assertThat(pg.chargeCalls.get()).isZero();
        service.reconcile(payment.orderId());
        assertThat(service.payment(1, payment.orderId()).status()).isEqualTo(PaymentStatus.PAID);
        assertThat(pg.registerCalls.get()).isEqualTo(1);
        assertThat(pg.chargeCalls.get()).isEqualTo(1);
    }

    @Test void databaseFailureAfterPgSuccessRollsBackActivationThenLookupRepairsIt() {
        jdbc.execute("ALTER TABLE expert_profiles ADD CONSTRAINT test_reject_paid_grade CHECK (membership_grade = 'BUSINESS')");
        var payment = service.create(1, "db-failure", request()).payment();
        assertThat(payment.status()).isEqualTo(PaymentStatus.UNKNOWN);
        assertThat(service.current(1).periodEnd()).isNull();
        assertThat(grade()).isEqualTo("BUSINESS");
        jdbc.execute("ALTER TABLE expert_profiles DROP CONSTRAINT test_reject_paid_grade");
        service.reconcile(payment.orderId());
        assertThat(service.payment(1, payment.orderId()).status()).isEqualTo(PaymentStatus.PAID);
        assertThat(grade()).isEqualTo("SPECIAL");
        assertThat(pg.chargeCalls.get()).isEqualTo(1);
    }

    @Test void concurrentSameRequestReturnsPendingWithoutCallingProviderAgain() throws Exception {
        var request = request();
        pg.entered = new CountDownLatch(1); pg.release = new CountDownLatch(1);
        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> service.create(1, "concurrent", request));
            assertThat(pg.entered.await(5, TimeUnit.SECONDS)).isTrue();
            var second = executor.submit(() -> service.create(1, "concurrent", request)).get(5, TimeUnit.SECONDS);
            assertThat(second.created()).isFalse();
            assertThat(second.payment().status()).isEqualTo(PaymentStatus.PROCESSING);
            pg.release.countDown();
            assertThat(first.get(5, TimeUnit.SECONDS).payment().status()).isEqualTo(PaymentStatus.PAID);
        } finally { pg.release.countDown(); }
        assertThat(pg.chargeCalls.get()).isEqualTo(1);
        assertThat(payments()).isEqualTo(1);
    }

    @Test void databaseConstraintsRejectDuplicateCycleAndInvalidPlan() {
        service.create(1, "one", request());
        assertThatThrownBy(() -> jdbc.update("UPDATE subscription_plans SET grade='BUSINESS' WHERE id=1"))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("UPDATE subscription_plans SET amount=1.5 WHERE id=1"))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO subscription_payments (subscription_id, expert_user_id, order_id, cycle_number,
                    idempotency_hash, request_hash, grade, plan_version, amount, currency, period_months,
                    status, provider, created_at, updated_at)
                SELECT subscription_id, expert_user_id, 'duplicate', cycle_number, 'other', request_hash,
                    grade, plan_version, amount, currency, period_months, status, provider, created_at, updated_at
                FROM subscription_payments
                """)).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    }

    static class FakePg implements SubscriptionPgClient {
        boolean enabled, wrongRegistrationCustomer, registrationTimeout, chargeTimeout;
        String mismatch;
        Outcome outcome;
        final AtomicInteger registerCalls = new AtomicInteger(), chargeCalls = new AtomicInteger(), lookupCalls = new AtomicInteger();
        volatile Charge lastCharge;
        CountDownLatch entered, release;
        void reset() {
            enabled = true; wrongRegistrationCustomer = registrationTimeout = chargeTimeout = false;
            mismatch = ""; outcome = Outcome.SUCCEEDED; lastCharge = null; entered = release = null;
            registerCalls.set(0); chargeCalls.set(0); lookupCalls.set(0);
        }
        public String provider() { return "TEST_ONLY"; }
        public boolean available() { return enabled; }
        void outsideTransaction() { assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse(); }
        public Registration register(String token, String customer, String registrationId) {
            outsideTransaction(); registerCalls.incrementAndGet();
            if (registrationTimeout) throw new IllegalStateException("test-only-provider-error-must-not-leak");
            return new Registration(Outcome.SUCCEEDED, wrongRegistrationCustomer ? "other" : customer, "test-only-billing-key");
        }
        public Registration findRegistration(String customer, String registrationId) {
            outsideTransaction(); return new Registration(Outcome.SUCCEEDED, customer, "test-only-billing-key");
        }
        public Charge charge(String key, String customer, String order, BigDecimal amount, String currency) {
            outsideTransaction(); chargeCalls.incrementAndGet(); assertThat(key).isEqualTo("test-only-billing-key");
            if (entered != null) {
                entered.countDown();
                try { if (!release.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("Test timeout"); }
                catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new IllegalStateException(); }
            }
            lastCharge = new Charge(outcome, mismatch.equals("customer") ? "other" : customer,
                    mismatch.equals("order") ? "other" : order, mismatch.equals("paymentId") ? null : "pg_" + order,
                    mismatch.equals("amount") ? BigDecimal.ONE : amount, mismatch.equals("currency") ? "USD" : currency,
                    mismatch.equals("approvedAt") ? null : NOW);
            if (chargeTimeout) throw new IllegalStateException("test-only-timeout");
            return lastCharge;
        }
        public Charge findCharge(String customer, String order) {
            outsideTransaction(); lookupCalls.incrementAndGet(); return lastCharge;
        }
    }
}
