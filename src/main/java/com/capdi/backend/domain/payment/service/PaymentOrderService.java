package com.capdi.backend.domain.payment.service;

import com.capdi.backend.domain.announcement.entity.AnnouncementProgressStepEnum;
import com.capdi.backend.domain.payment.dto.*;
import com.capdi.backend.domain.payment.entity.*;
import com.capdi.backend.domain.payment.repository.*;
import com.capdi.backend.global.exception.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PaymentOrderService {
    private final PaymentRepository payments;
    private final PaymentIdempotencyKeyRepository keys;
    private final PaymentSourceRepository sources;

    // A fresh READ_COMMITTED transaction avoids absent-key gap locks across unrelated clients.
    @Transactional(propagation = Propagation.REQUIRES_NEW, isolation = Isolation.READ_COMMITTED)
    public OrderResult create(Long clientId, String code, String idempotencyKey, PaymentOrderRequest request) {
        if (idempotencyKey == null || !idempotencyKey.matches("[A-Za-z0-9._:-]{1,128}")
                || code == null || request == null || request.getBidId() == null || request.getBidId() <= 0
                || request.getMethod() == null) throw new CustomException(ErrorCode.INVALID_INPUT);
        // Consistent lock order: payer -> idempotency binding -> announcement -> bids -> payment.
        // Payer lock serializes the same client's key across DIFFERENT announcements.
        requireClient(clientId, true);
        String keyHash = hash(idempotencyKey);
        String requestHash = hash("v1\n" + code + "\n" + request.getBidId() + "\n" + request.getMethod().name());
        var prior = keys.findForUpdate(clientId, keyHash);
        if (prior.isPresent()) {
            if (!prior.get().getRequestHash().equals(requestHash)) {
                throw new CustomException(ErrorCode.PAYMENT_IDEMPOTENCY_CONFLICT);
            }
            Payment payment = prior.get().getPayment();
            if (!payment.getClientUserId().equals(clientId)) throw new CustomException(ErrorCode.FORBIDDEN);
            // Replay returns the original snapshot. It never starts/retries an external attempt.
            return new OrderResult(PaymentResponse.from(payment), false);
        }
        var announcement = sources.lockAnnouncement(code)
                .orElseThrow(() -> new CustomException(ErrorCode.ANNOUNCEMENT_NOT_FOUND));
        if (!clientId.equals(announcement.ownerId())) throw new CustomException(ErrorCode.ANNOUNCEMENT_FORBIDDEN);
        if (!"CLOSED".equals(announcement.status()) || !selectedStep(announcement.progressStep())) {
            throw new CustomException(ErrorCode.PAYMENT_NOT_ELIGIBLE);
        }
        var bid = sources.lockBid(request.getBidId(), code)
                .orElseThrow(() -> new CustomException(ErrorCode.BID_NOT_FOUND));
        if (!"SELECTED".equals(bid.status())
                || !sources.lockSelectedBidIds(code).equals(java.util.List.of(bid.id()))) {
            throw new CustomException(ErrorCode.PAYMENT_NOT_ELIGIBLE);
        }
        BigDecimal amount = validAmount(bid.finalAmount());
        Long expert = sources.expertProfileId(bid.expertUserId())
                .orElseThrow(() -> new CustomException(ErrorCode.EXPERT_PROFILE_NOT_FOUND));
        var priorPayment = payments.findByBidIdForUpdate(bid.id());
        Payment payment;
        boolean created = priorPayment.isEmpty();
        if (created) {
            payment = payments.saveAndFlush(Payment.create(bid.id(), announcement.id(), code, clientId,
                    expert, amount, request.getMethod()));
        } else {
            payment = priorPayment.get();
            if (payment.getMethod() != request.getMethod() || payment.getAmount().compareTo(amount) != 0
                    || !payment.getExpertProfileId().equals(expert)
                    || !payment.getClientUserId().equals(clientId)
                    || !payment.getAnnouncementId().equals(announcement.id())) {
                throw new CustomException(ErrorCode.PAYMENT_ORDER_CONFLICT);
            }
        }
        keys.saveAndFlush(PaymentIdempotencyKey.bind(clientId, keyHash, requestHash, payment));
        return new OrderResult(PaymentResponse.from(payment), created);
    }

    public PaymentResponse get(Long clientId, Long id) {
        requireClient(clientId, false);
        Payment payment = payments.findById(id).orElseThrow(() -> new CustomException(ErrorCode.PAYMENT_NOT_FOUND));
        if (!payment.getClientUserId().equals(clientId)) throw new CustomException(ErrorCode.FORBIDDEN);
        return PaymentResponse.from(payment);
    }

    private void requireClient(Long id, boolean lock) {
        if (id == null) throw new CustomException(ErrorCode.UNAUTHORIZED);
        String role = sources.userType(id, lock).orElseThrow(() -> new CustomException(ErrorCode.UNAUTHORIZED));
        if (!"CLIENT".equals(role)) throw new CustomException(ErrorCode.FORBIDDEN);
    }

    private boolean selectedStep(String step) {
        try {
            return AnnouncementProgressStepEnum.valueOf(step).getStepNumber()
                    >= AnnouncementProgressStepEnum.STEP_4_DIAGNOSIS_STARTED.getStepNumber();
        } catch (IllegalArgumentException | NullPointerException e) { return false; }
    }

    private BigDecimal validAmount(BigDecimal amount) {
        if (amount == null || amount.signum() <= 0 || amount.stripTrailingZeros().scale() > 0
                || amount.compareTo(new BigDecimal("9999999999999")) > 0) {
            throw new CustomException(ErrorCode.PAYMENT_INVALID_AMOUNT);
        }
        return amount.setScale(2);
    }

    private String hash(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) { throw new IllegalStateException("SHA-256 unavailable"); }
    }

    public record OrderResult(PaymentResponse response, boolean created) { }
}
