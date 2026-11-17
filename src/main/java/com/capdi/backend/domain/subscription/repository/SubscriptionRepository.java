package com.capdi.backend.domain.subscription.repository;

import com.capdi.backend.domain.expert.entity.MembershipGradeEnum;
import com.capdi.backend.domain.subscription.model.SubscriptionModels.*;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/** Scalar reads avoid stale JPA profile snapshots. Writers are called only by the transaction service. */
@Repository
@RequiredArgsConstructor
public class SubscriptionRepository {
    private final JdbcTemplate jdbc;

    public record Expert(long profileId, MembershipGradeEnum grade) { }

    public Optional<String> userRole(long userId, boolean lock) {
        return jdbc.query("SELECT user_type FROM users WHERE id = ?" + (lock ? " FOR UPDATE" : ""),
                (rs, row) -> rs.getString(1), userId).stream().findFirst();
    }

    public Optional<Expert> expert(long userId, boolean lock) {
        return jdbc.query("SELECT id, membership_grade FROM expert_profiles WHERE user_id = ?" + (lock ? " FOR UPDATE" : ""),
                (rs, row) -> new Expert(rs.getLong(1), MembershipGradeEnum.valueOf(rs.getString(2))), userId)
                .stream().findFirst();
    }

    public List<Plan> plans() {
        return jdbc.query("SELECT * FROM subscription_plans WHERE is_active = TRUE ORDER BY amount, id", PLAN);
    }

    public Optional<Plan> plan(long id) {
        return jdbc.query("SELECT * FROM subscription_plans WHERE id = ?", PLAN, id).stream().findFirst();
    }

    public Optional<Subscription> current(long profileId) {
        return jdbc.query("SELECT * FROM expert_subscriptions WHERE current_expert_id = ?", SUBSCRIPTION, profileId)
                .stream().findFirst();
    }

    public Optional<Subscription> registration(String registrationId) {
        return jdbc.query("SELECT * FROM expert_subscriptions WHERE registration_id = ?", SUBSCRIPTION, registrationId)
                .stream().findFirst();
    }

    public Subscription subscription(long id) {
        return jdbc.queryForObject("SELECT * FROM expert_subscriptions WHERE id = ?", SUBSCRIPTION, id);
    }

    public Subscription insertRegistration(long expertId, long planId, String registrationId, String customerRef,
                                           String provider, LocalDateTime now) {
        long id = insert("""
                INSERT INTO expert_subscriptions (expert_profile_id, current_expert_id, plan_id,
                    registration_id, customer_ref, provider, registration_expires_at, status, auto_renew,
                    created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?, 'REGISTERING', FALSE, ?, ?)
                """, expertId, expertId, planId, registrationId, customerRef, provider, now.plusMinutes(15), now, now);
        return subscription(id);
    }

    public void expireRegistration(long id, LocalDateTime now) {
        jdbc.update("UPDATE expert_subscriptions SET status = 'EXPIRED', current_expert_id = NULL, updated_at = ? WHERE id = ?", now, id);
    }

    public Optional<Payment> byIdempotency(long userId, String keyHash) {
        return jdbc.query("SELECT * FROM subscription_payments WHERE expert_user_id = ? AND idempotency_hash = ?",
                PAYMENT, userId, keyHash).stream().findFirst();
    }

    public Optional<Payment> byOrder(String orderId) {
        return jdbc.query("SELECT * FROM subscription_payments WHERE order_id = ?", PAYMENT, orderId).stream().findFirst();
    }

    public Optional<Payment> firstPayment(long subscriptionId) {
        return jdbc.query("SELECT * FROM subscription_payments WHERE subscription_id = ? AND cycle_number = 1",
                PAYMENT, subscriptionId).stream().findFirst();
    }

    public List<Payment> payments(long userId, int offset) {
        return jdbc.query("SELECT * FROM subscription_payments WHERE expert_user_id = ? ORDER BY id DESC LIMIT 20 OFFSET ?",
                PAYMENT, userId, offset);
    }

    public Payment insertPayment(long userId, Subscription sub, Plan plan, String orderId, String keyHash,
                                 String requestHash, LocalDateTime now) {
        insert("""
                INSERT INTO subscription_payments (subscription_id, expert_user_id, order_id, cycle_number,
                    idempotency_hash, request_hash, grade, plan_version, amount, currency, period_months,
                    status, provider, created_at, updated_at)
                VALUES (?, ?, ?, 1, ?, ?, ?, ?, ?, ?, ?, 'REGISTERING', ?, ?, ?)
                """, sub.id(), userId, orderId, keyHash, requestHash, plan.grade().name(), plan.version(),
                plan.amount(), plan.currency(), plan.periodMonths(), sub.provider(), now, now);
        jdbc.update("UPDATE expert_subscriptions SET status = 'PENDING_PAYMENT', updated_at = ? WHERE id = ?", now, sub.id());
        return byOrder(orderId).orElseThrow();
    }

    public void saveBillingKey(Payment payment, String encrypted, LocalDateTime now) {
        jdbc.update("UPDATE expert_subscriptions SET encrypted_billing_key = ?, updated_at = ? WHERE id = ?",
                encrypted, now, payment.subscriptionId());
        jdbc.update("UPDATE subscription_payments SET status = 'READY', failure_code = NULL, updated_at = ? WHERE id = ?",
                now, payment.id());
    }

    public boolean claimCharge(long id, LocalDateTime now) {
        return jdbc.update("UPDATE subscription_payments SET status = 'PROCESSING', updated_at = ? WHERE id = ? AND status = 'READY'",
                now, id) == 1;
    }

    public void unknown(long id, String code, LocalDateTime now) {
        jdbc.update("UPDATE subscription_payments SET status = 'UNKNOWN', failure_code = ?, updated_at = ? WHERE id = ?",
                code, now, id);
    }

    public void fail(Payment payment, String code, LocalDateTime now) {
        jdbc.update("UPDATE subscription_payments SET status = 'FAILED', failure_code = ?, updated_at = ? WHERE id = ?",
                code, now, payment.id());
        jdbc.update("UPDATE expert_subscriptions SET status = 'FAILED', current_expert_id = NULL, updated_at = ? WHERE id = ?",
                now, payment.subscriptionId());
    }

    public void activate(Payment payment, String providerPaymentId, LocalDateTime paidAt, LocalDateTime now) {
        LocalDateTime end = paidAt.plusMonths(payment.periodMonths());
        jdbc.update("""
                UPDATE subscription_payments SET status = 'PAID', provider_payment_id = ?, failure_code = NULL,
                    paid_at = ?, period_start = ?, period_end = ?, updated_at = ? WHERE id = ?
                """, providerPaymentId, paidAt, paidAt, end, now, payment.id());
        // Recurring charging is not enabled until the renewal/cancellation policy and scheduler are implemented.
        jdbc.update("""
                UPDATE expert_subscriptions SET status = 'ACTIVE', current_period_start = ?, current_period_end = ?,
                    next_billing_at = NULL, auto_renew = FALSE, updated_at = ? WHERE id = ?
                """, paidAt, end, now, payment.subscriptionId());
        jdbc.update("UPDATE expert_profiles SET membership_grade = ?, updated_at = ? WHERE user_id = ?",
                payment.grade().name(), now, payment.expertUserId());
    }

    private long insert(String sql, Object... values) {
        GeneratedKeyHolder holder = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            var statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            for (int i = 0; i < values.length; i++) statement.setObject(i + 1, values[i]);
            return statement;
        }, holder);
        return holder.getKey().longValue();
    }

    private static final RowMapper<Plan> PLAN = (rs, row) -> new Plan(rs.getLong("id"),
            MembershipGradeEnum.valueOf(rs.getString("grade")), rs.getInt("plan_version"), rs.getBigDecimal("amount"),
            rs.getString("currency"), rs.getInt("period_months"), rs.getBoolean("is_active"));

    private static final RowMapper<Subscription> SUBSCRIPTION = (rs, row) -> new Subscription(rs.getLong("id"),
            rs.getLong("expert_profile_id"), rs.getLong("plan_id"), rs.getString("registration_id"), rs.getString("customer_ref"),
            rs.getString("provider"), rs.getObject("registration_expires_at", LocalDateTime.class),
            rs.getString("encrypted_billing_key"), Status.valueOf(rs.getString("status")),
            rs.getObject("current_period_start", LocalDateTime.class), rs.getObject("current_period_end", LocalDateTime.class),
            rs.getObject("next_billing_at", LocalDateTime.class), rs.getBoolean("auto_renew"));

    private static final RowMapper<Payment> PAYMENT = (rs, row) -> new Payment(rs.getLong("id"),
            rs.getLong("subscription_id"), rs.getLong("expert_user_id"), rs.getString("order_id"), rs.getString("request_hash"),
            MembershipGradeEnum.valueOf(rs.getString("grade")), rs.getInt("plan_version"), rs.getBigDecimal("amount"),
            rs.getString("currency"), rs.getInt("period_months"), PaymentStatus.valueOf(rs.getString("status")),
            rs.getString("provider"), rs.getString("provider_payment_id"), rs.getString("failure_code"),
            rs.getObject("paid_at", LocalDateTime.class), rs.getObject("period_start", LocalDateTime.class),
            rs.getObject("period_end", LocalDateTime.class));
}
