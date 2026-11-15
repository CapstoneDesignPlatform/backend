package com.capdi.backend.domain.subscription.service;

import com.capdi.backend.domain.expert.entity.MembershipGradeEnum;
import com.capdi.backend.domain.subscription.dto.SubscriptionDtos.*;
import com.capdi.backend.domain.subscription.exception.SubscriptionException;
import com.capdi.backend.domain.subscription.gateway.SubscriptionPgClient;
import com.capdi.backend.domain.subscription.gateway.SubscriptionPgClient.Outcome;
import com.capdi.backend.domain.subscription.model.SubscriptionModels.*;
import com.capdi.backend.domain.subscription.repository.SubscriptionRepository;
import com.capdi.backend.global.exception.CustomException;
import com.capdi.backend.global.exception.ErrorCode;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Short DB-only transactions. Every mutation locks user -> expert, including completion/recovery. */
@Service
@Transactional(propagation = Propagation.REQUIRES_NEW, isolation = Isolation.READ_COMMITTED)
public class SubscriptionTransactions {
    private final SubscriptionRepository repository;
    private final Clock clock;

    public SubscriptionTransactions(SubscriptionRepository repository, @Qualifier("subscriptionClock") Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    public List<PlanResponse> plans() {
        return repository.plans().stream().map(PlanResponse::from).toList();
    }

    public RegistrationResponse prepare(long userId, long planId, String provider) {
        var expert = expert(userId, true);
        Plan plan = activePlan(planId);
        var current = repository.current(expert.profileId());
        if (current.isPresent()) {
            var sub = current.get();
            if (sub.status() == Status.REGISTERING && repository.firstPayment(sub.id()).isEmpty()) {
                if (!sub.registrationExpiresAt().isAfter(now())) {
                    repository.expireRegistration(sub.id(), now());
                } else if (sub.planId() == planId && sub.provider().equals(provider)) {
                    return registrationResponse(sub, plan);
                } else throw conflict("SUBSCRIPTION_REGISTRATION_EXISTS", "진행 중인 구독 등록이 있습니다.");
            } else throw conflict("SUBSCRIPTION_EXISTS", "진행 중이거나 활성화된 구독이 있습니다.");
        }
        // Existing paid grades must not be overwritten by this initial-enrollment flow.
        if (expert.grade() != MembershipGradeEnum.BUSINESS)
            throw conflict("SUBSCRIPTION_GRADE_CONFLICT", "현재 회원등급을 확인한 후 구독을 신청해주세요.");
        var sub = repository.insertRegistration(expert.profileId(), planId, UUID.randomUUID().toString(),
                "sub_" + UUID.randomUUID(), provider, now());
        return registrationResponse(sub, plan);
    }

    public Prepared begin(long userId, String keyHash, String requestHash, CreateRequest request, String provider) {
        var expert = expert(userId, true);
        var existing = repository.byIdempotency(userId, keyHash);
        if (existing.isPresent()) {
            var payment = existing.get();
            if (!payment.requestHash().equals(requestHash))
                throw conflict("SUBSCRIPTION_IDEMPOTENCY_CONFLICT", "동일 멱등키의 요청 내용이 다릅니다.");
            return new Prepared(payment, repository.subscription(payment.subscriptionId()), false);
        }
        var sub = repository.registration(request.registrationId()).orElseThrow(() ->
                new SubscriptionException(HttpStatus.NOT_FOUND, "SUBSCRIPTION_REGISTRATION_NOT_FOUND", "구독 등록 정보를 찾을 수 없습니다."));
        if (sub.expertProfileId() != expert.profileId()) throw new CustomException(ErrorCode.FORBIDDEN);
        if (sub.planId() != request.planId() || !sub.provider().equals(provider))
            throw conflict("SUBSCRIPTION_REGISTRATION_MISMATCH", "구독 등록 정보와 요청이 다릅니다.");
        // Different keys cannot cause a second first-cycle charge.
        if (repository.firstPayment(sub.id()).isPresent())
            throw conflict("SUBSCRIPTION_ORDER_EXISTS", "첫 결제 주문이 이미 있습니다. 기존 멱등키로 조회하거나 재요청해주세요.");
        if (sub.status() != Status.REGISTERING || !sub.registrationExpiresAt().isAfter(now()))
            throw conflict("SUBSCRIPTION_REGISTRATION_EXPIRED", "구독 등록이 만료되었습니다. 다시 등록해주세요.");
        if (expert.grade() != MembershipGradeEnum.BUSINESS)
            throw conflict("SUBSCRIPTION_GRADE_CONFLICT", "현재 회원등급을 확인해주세요.");
        Plan plan = activePlan(request.planId());
        Payment payment = repository.insertPayment(userId, sub, plan, "sub_" + UUID.randomUUID(), keyHash, requestHash, now());
        return new Prepared(payment, repository.subscription(sub.id()), true);
    }

    public void registered(String orderId, String encryptedKey) {
        Payment p = lockedPayment(orderId);
        if (terminal(p)) return;
        Subscription s = repository.subscription(p.subscriptionId());
        // Concurrent recovery must never reset an already claimed charge to READY.
        if (s.encryptedBillingKey() != null) return;
        if (p.status() != PaymentStatus.REGISTERING && p.status() != PaymentStatus.UNKNOWN) return;
        repository.saveBillingKey(p, encryptedKey, now());
    }

    public boolean claimCharge(String orderId) {
        Payment p = lockedPayment(orderId);
        return repository.claimCharge(p.id(), now());
    }

    public void unknown(String orderId, String code) {
        Payment p = lockedPayment(orderId);
        if (!terminal(p)) repository.unknown(p.id(), code, now());
    }

    public void registrationDeclined(String orderId) {
        Payment p = lockedPayment(orderId);
        if (!terminal(p) && repository.subscription(p.subscriptionId()).encryptedBillingKey() == null)
            repository.fail(p, "REGISTRATION_DECLINED", now());
    }

    public void complete(String orderId, SubscriptionPgClient.Charge charge) {
        Payment p = lockedPayment(orderId);
        if (terminal(p)) return;
        Subscription s = repository.subscription(p.subscriptionId());
        if (charge == null || charge.outcome() == null || charge.outcome() == Outcome.UNKNOWN) {
            repository.unknown(p.id(), "PAYMENT_RESULT_UNKNOWN", now());
            return;
        }
        boolean matches = Objects.equals(charge.orderId(), p.orderId())
                && Objects.equals(charge.customerRef(), s.customerRef())
                && Objects.equals(charge.currency(), p.currency())
                && charge.amount() != null && charge.amount().compareTo(p.amount()) == 0;
        if (!matches) {
            repository.unknown(p.id(), "PAYMENT_RESULT_MISMATCH", now());
            return;
        }
        if (charge.outcome() == Outcome.FAILED) {
            repository.fail(p, "PAYMENT_DECLINED", now());
            return;
        }
        if (s.encryptedBillingKey() == null || charge.paymentId() == null || charge.paymentId().isBlank()
                || charge.paymentId().length() > 200 || charge.approvedAt() == null
                || charge.approvedAt().isAfter(clock.instant().plusSeconds(300))) {
            repository.unknown(p.id(), "PAYMENT_RESULT_MISMATCH", now());
            return;
        }
        var approvedAt = LocalDateTime.ofInstant(charge.approvedAt(), ZoneOffset.UTC);
        // Do not grant an already expired term during delayed recovery; require policy/operator review.
        if (!approvedAt.plusMonths(p.periodMonths()).isAfter(now())) {
            repository.unknown(p.id(), "PAYMENT_PERIOD_REVIEW_REQUIRED", now());
            return;
        }
        repository.activate(p, charge.paymentId(), approvedAt, now());
    }

    public Payment payment(String orderId) {
        return repository.byOrder(orderId).orElseThrow(() -> new CustomException(ErrorCode.PAYMENT_NOT_FOUND));
    }

    public Subscription subscription(long id) { return repository.subscription(id); }

    public CurrentResponse current(long userId) {
        var expert = expert(userId, false);
        return repository.current(expert.profileId())
                .map(s -> new CurrentResponse(expert.grade(), s.id(), s.status(), s.periodStart(), s.periodEnd(), s.nextBillingAt(), s.autoRenew()))
                .orElseGet(() -> new CurrentResponse(expert.grade(), null, null, null, null, null, false));
    }

    public List<PaymentResponse> payments(long userId, int page) {
        expert(userId, false);
        if (page < 1 || page > 1000000) throw new CustomException(ErrorCode.INVALID_INPUT);
        return repository.payments(userId, (page - 1) * 20).stream().map(PaymentResponse::from).toList();
    }

    public PaymentResponse ownPayment(long userId, String orderId) {
        expert(userId, false);
        Payment p = payment(orderId);
        if (p.expertUserId() != userId) throw new CustomException(ErrorCode.FORBIDDEN);
        return PaymentResponse.from(p);
    }

    public void requireExpert(long userId) { expert(userId, false); }

    private SubscriptionRepository.Expert expert(long userId, boolean lock) {
        String role = repository.userRole(userId, lock).orElseThrow(() -> new CustomException(ErrorCode.UNAUTHORIZED));
        if (!"EXPERT".equals(role)) throw new CustomException(ErrorCode.FORBIDDEN);
        return repository.expert(userId, lock).orElseThrow(() -> new CustomException(ErrorCode.EXPERT_PROFILE_NOT_FOUND));
    }

    private Payment lockedPayment(String orderId) {
        Payment p = payment(orderId);
        expert(p.expertUserId(), true);
        return payment(orderId);
    }

    private Plan activePlan(long id) {
        Plan plan = repository.plan(id).orElseThrow(() -> new SubscriptionException(HttpStatus.NOT_FOUND,
                "SUBSCRIPTION_PLAN_NOT_FOUND", "구독 상품을 찾을 수 없습니다."));
        if (!plan.active() || plan.grade() == MembershipGradeEnum.BUSINESS || plan.amount().signum() <= 0
                || plan.amount().stripTrailingZeros().scale() > 0 || plan.amount().compareTo(new BigDecimal("9999999999999")) > 0
                || !"KRW".equals(plan.currency()) || plan.periodMonths() < 1 || plan.periodMonths() > 120)
            throw conflict("SUBSCRIPTION_PLAN_UNAVAILABLE", "판매 가능한 구독 상품이 아닙니다.");
        return plan;
    }

    private RegistrationResponse registrationResponse(Subscription s, Plan p) {
        return new RegistrationResponse(s.registrationId(), s.customerRef(), s.provider(), s.registrationExpiresAt(), PlanResponse.from(p));
    }

    private boolean terminal(Payment p) { return p.status() == PaymentStatus.PAID || p.status() == PaymentStatus.FAILED; }
    private LocalDateTime now() { return LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC); }
    private SubscriptionException conflict(String code, String message) { return SubscriptionException.conflict(code, message); }
}
