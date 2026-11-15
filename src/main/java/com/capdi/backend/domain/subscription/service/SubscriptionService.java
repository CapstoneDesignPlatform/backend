package com.capdi.backend.domain.subscription.service;

import com.capdi.backend.domain.subscription.dto.SubscriptionDtos.*;
import com.capdi.backend.domain.subscription.exception.SubscriptionException;
import com.capdi.backend.domain.subscription.gateway.SubscriptionPgClient;
import com.capdi.backend.domain.subscription.gateway.SubscriptionPgClient.Outcome;
import com.capdi.backend.domain.subscription.model.SubscriptionModels.*;
import com.capdi.backend.global.exception.CustomException;
import com.capdi.backend.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;

/** Orchestrates first enrollment only. No remote request runs with a DB transaction open. */
@Service
@RequiredArgsConstructor
@Transactional(propagation = Propagation.NOT_SUPPORTED)
public class SubscriptionService {
    private final SubscriptionTransactions transactions;
    private final SubscriptionPgClient pg;
    private final BillingKeyCipher cipher;

    public List<PlanResponse> plans() { return transactions.plans(); }

    public RegistrationResponse prepare(long userId, long planId) {
        transactions.requireExpert(userId);
        requireProvider();
        return transactions.prepare(userId, planId, pg.provider());
    }

    public CreationResult create(long userId, String idempotencyKey, CreateRequest request) {
        if (idempotencyKey == null || !idempotencyKey.matches("[A-Za-z0-9._:-]{1,128}") || request == null
                || request.planId() == null || request.planId() <= 0 || request.registrationId() == null
                || !request.registrationId().matches("[a-fA-F0-9-]{36}") || request.registrationToken() == null
                || request.registrationToken().isBlank() || request.registrationToken().length() > 4096)
            throw new CustomException(ErrorCode.INVALID_INPUT);
        transactions.requireExpert(userId);
        requireProvider();
        String requestHash = hash("v1\n" + request.planId() + "\n" + request.registrationId() + "\n" + hash(request.registrationToken()));
        Prepared prepared = transactions.begin(userId, hash(idempotencyKey), requestHash, request, pg.provider());
        if (!prepared.execute()) return new CreationResult(PaymentResponse.from(prepared.payment()), false);
        String orderId = prepared.payment().orderId();
        try {
            var sub = prepared.subscription();
            var registration = pg.register(request.registrationToken(), sub.customerRef(), sub.registrationId());
            handleRegistration(orderId, sub, registration);
            executeReadyCharge(orderId);
        } catch (RuntimeException ignored) {
            // Never expose/log provider exceptions or submitted tokens. An external success may have occurred.
            transactions.unknown(orderId, "PROVIDER_RESULT_UNKNOWN");
        }
        return new CreationResult(PaymentResponse.from(transactions.payment(orderId)), true);
    }

    /** Internal recovery entrypoint. Wire to an authenticated provider webhook or worker after selecting a PG.
     * It never reuses a one-time registration token and never blindly recharges PROCESSING/UNKNOWN orders.
     */
    public void reconcile(String orderId) {
        requireProvider();
        Payment payment = transactions.payment(orderId);
        if (payment.status() == PaymentStatus.PAID || payment.status() == PaymentStatus.FAILED) return;
        Subscription sub = transactions.subscription(payment.subscriptionId());
        if (!sub.provider().equals(pg.provider())) throw SubscriptionException.unavailable();
        try {
            if (sub.encryptedBillingKey() == null) {
                handleRegistration(orderId, sub, pg.findRegistration(sub.customerRef(), sub.registrationId()));
            }
            payment = transactions.payment(orderId);
            if (payment.status() == PaymentStatus.READY) executeReadyCharge(orderId);
            else if (payment.status() == PaymentStatus.PROCESSING || payment.status() == PaymentStatus.UNKNOWN) {
                sub = transactions.subscription(payment.subscriptionId());
                if (sub.encryptedBillingKey() != null)
                    transactions.complete(orderId, pg.findCharge(sub.customerRef(), orderId));
            }
        } catch (RuntimeException ignored) {
            transactions.unknown(orderId, "PROVIDER_RESULT_UNKNOWN");
        }
    }

    public CurrentResponse current(long userId) { return transactions.current(userId); }
    public List<PaymentResponse> payments(long userId, int page) { return transactions.payments(userId, page); }
    public PaymentResponse payment(long userId, String orderId) { return transactions.ownPayment(userId, orderId); }

    private void handleRegistration(String orderId, Subscription sub, SubscriptionPgClient.Registration result) {
        if (result == null || result.outcome() == null || result.outcome() == Outcome.UNKNOWN) {
            transactions.unknown(orderId, "REGISTRATION_RESULT_UNKNOWN");
        } else if (!sub.customerRef().equals(result.customerRef())) {
            transactions.unknown(orderId, "REGISTRATION_CUSTOMER_MISMATCH");
        } else if (result.outcome() == Outcome.FAILED) {
            transactions.registrationDeclined(orderId);
        } else if (result.billingKey() == null || result.billingKey().isBlank() || result.billingKey().length() > 4096) {
            transactions.unknown(orderId, "REGISTRATION_RESULT_INVALID");
        } else {
            transactions.registered(orderId, cipher.encrypt(result.billingKey(), sub.customerRef()));
        }
    }

    private void executeReadyCharge(String orderId) {
        if (!transactions.claimCharge(orderId)) return;
        Payment p = transactions.payment(orderId);
        Subscription s = transactions.subscription(p.subscriptionId());
        String key = cipher.decrypt(s.encryptedBillingKey(), s.customerRef());
        transactions.complete(orderId, pg.charge(key, s.customerRef(), orderId, p.amount(), p.currency()));
    }

    private void requireProvider() {
        if (!pg.available() || pg.provider() == null || !pg.provider().matches("[A-Z0-9_]{1,40}")
                || "DISABLED".equals(pg.provider())) throw SubscriptionException.unavailable();
        cipher.requireConfigured();
    }

    private String hash(String value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException("SHA-256 unavailable"); }
    }
}
