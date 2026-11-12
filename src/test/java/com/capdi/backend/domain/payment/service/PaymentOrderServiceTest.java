package com.capdi.backend.domain.payment.service;

import com.capdi.backend.domain.payment.dto.*;
import com.capdi.backend.domain.payment.entity.*;
import com.capdi.backend.domain.payment.repository.*;
import com.capdi.backend.domain.payment.repository.PaymentSourceRepository.*;
import com.capdi.backend.global.exception.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import java.util.stream.Stream;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class PaymentOrderServiceTest {
    PaymentRepository payments;
    PaymentIdempotencyKeyRepository keys;
    PaymentSourceRepository sources;
    PaymentOrderService service;
    static final String CODE = "SYNTHETIC-57";
    static final String KEY = "synthetic-key";
    @BeforeEach void setup() {
        payments = mock(PaymentRepository.class); keys = mock(PaymentIdempotencyKeyRepository.class);
        sources = mock(PaymentSourceRepository.class); service = new PaymentOrderService(payments, keys, sources);
        when(sources.userType(1L, true)).thenReturn(Optional.of("CLIENT"));
        when(sources.userType(1L, false)).thenReturn(Optional.of("CLIENT"));
        when(sources.lockAnnouncement(CODE)).thenReturn(Optional.of(new AnnouncementSnapshot(2L,1L,"CLOSED","STEP_4_DIAGNOSIS_STARTED")));
        when(sources.lockBid(10L,CODE)).thenReturn(Optional.of(new BidSnapshot(10L,3L,"SELECTED",new BigDecimal("1000000.00"))));
        when(sources.lockSelectedBidIds(CODE)).thenReturn(List.of(10L));
        when(sources.expertProfileId(3L)).thenReturn(Optional.of(4L));
        when(keys.findForUpdate(anyLong(), anyString())).thenReturn(Optional.empty());
        when(payments.findByBidIdForUpdate(10L)).thenReturn(Optional.empty());
        when(payments.saveAndFlush(any())).thenAnswer(call -> {
            Payment p=call.getArgument(0);ReflectionTestUtils.setField(p,"id",100L);
            ReflectionTestUtils.setField(p,"createdAt",java.time.LocalDateTime.of(2026,1,1,0,0));return p;
        });
    }
    PaymentOrderRequest request(long bid,String method) throws Exception {
        return new ObjectMapper().readValue("{\"bid_id\":"+bid+",\"method\":\""+method+"\"}",PaymentOrderRequest.class);
    }
    Payment existing() {
        Payment p=Payment.create(10L,2L,CODE,1L,4L,new BigDecimal("1000000"),PaymentMethod.CARD);
        ReflectionTestUtils.setField(p,"id",100L);return p;
    }
    void expect(ErrorCode code, org.assertj.core.api.ThrowableAssert.ThrowingCallable call) {
        assertThatThrownBy(call).isInstanceOfSatisfying(CustomException.class,e->assertThat(e.getErrorCode()).isEqualTo(code));
    }
    String hash(String text) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8)));
    }
    @Test void createsImmutableAmountAndAllocationSnapshotWithoutAttempt() throws Exception {
        var result=service.create(1L,CODE,KEY,request(10,"CARD"));
        assertThat(result.created()).isTrue();assertThat(result.response().getAmount()).isEqualByComparingTo("1000000");
        var c=ArgumentCaptor.forClass(Payment.class);verify(payments).saveAndFlush(c.capture());
        Payment p=c.getValue();assertThat(p.getStatus()).isEqualTo(PaymentStatus.CREATED);
        assertThat(p.getCurrency()).isEqualTo("KRW");assertThat(p.getExpertProfileId()).isEqualTo(4L);
        assertThat(p.getClientUserId()).isEqualTo(1L);assertThat(p.getAnnouncementId()).isEqualTo(2L);
        assertThat(p.getOrderId()).startsWith("pay_").hasSize(40);
        assertThat(p.getFeePolicyVersion()).isEqualTo("BASE_ALLOCATION_10_90_V1");
        assertThat(p.getPlatformFeeRate()).isEqualByComparingTo("0.1");
        assertThat(p.getExpertShareRate()).isEqualByComparingTo("0.9");assertThat(p.getVersion()).isZero();
        var k=ArgumentCaptor.forClass(PaymentIdempotencyKey.class);verify(keys).saveAndFlush(k.capture());
        assertThat(k.getValue().getKeyHash()).isEqualTo(hash(KEY)).doesNotContain(KEY);
    }
    @ParameterizedTest @ValueSource(strings={"0","-1","0.01","100.50","10000000000000"})
    void rejectsInvalidAmount(String amount) throws Exception {
        when(sources.lockBid(10L,CODE)).thenReturn(Optional.of(new BidSnapshot(10L,3L,"SELECTED",new BigDecimal(amount))));
        expect(ErrorCode.PAYMENT_INVALID_AMOUNT,()->service.create(1L,CODE,KEY,request(10,"CARD")));
        verify(payments,never()).saveAndFlush(any());verify(keys,never()).saveAndFlush(any());
    }
    @Test void rejectsLegacyNullAmount() throws Exception {
        when(sources.lockBid(10L,CODE)).thenReturn(Optional.of(new BidSnapshot(10L,3L,"SELECTED",null)));
        expect(ErrorCode.PAYMENT_INVALID_AMOUNT,()->service.create(1L,CODE,KEY,request(10,"CARD")));
    }
    @ParameterizedTest @ValueSource(strings={"ACTIVE","CANCELLED"})
    void rejectsNonClosedAnnouncement(String state) throws Exception {
        when(sources.lockAnnouncement(CODE)).thenReturn(Optional.of(new AnnouncementSnapshot(2L,1L,state,"STEP_4_DIAGNOSIS_STARTED")));
        expect(ErrorCode.PAYMENT_NOT_ELIGIBLE,()->service.create(1L,CODE,KEY,request(10,"CARD")));
    }
    @ParameterizedTest @ValueSource(strings={"STEP_1_REGISTERED","STEP_3_EXPERT_SELECTION","UNKNOWN"})
    void rejectsUnselectedStep(String step) throws Exception {
        when(sources.lockAnnouncement(CODE)).thenReturn(Optional.of(new AnnouncementSnapshot(2L,1L,"CLOSED",step)));
        expect(ErrorCode.PAYMENT_NOT_ELIGIBLE,()->service.create(1L,CODE,KEY,request(10,"CARD")));
    }
    @Test void rejectsOtherOwner() throws Exception {
        when(sources.lockAnnouncement(CODE)).thenReturn(Optional.of(new AnnouncementSnapshot(2L,99L,"CLOSED","STEP_4_DIAGNOSIS_STARTED")));
        expect(ErrorCode.ANNOUNCEMENT_FORBIDDEN,()->service.create(1L,CODE,KEY,request(10,"CARD")));
    }
    @Test void rejectsMissingAnnouncement() throws Exception {
        when(sources.lockAnnouncement(CODE)).thenReturn(Optional.empty());
        expect(ErrorCode.ANNOUNCEMENT_NOT_FOUND,()->service.create(1L,CODE,KEY,request(10,"CARD")));
    }
    @Test void rejectsBidFromAnotherAnnouncement() throws Exception {
        when(sources.lockBid(10L,CODE)).thenReturn(Optional.empty());
        expect(ErrorCode.BID_NOT_FOUND,()->service.create(1L,CODE,KEY,request(10,"CARD")));
    }
    @ParameterizedTest @ValueSource(strings={"PENDING","REJECTED"})
    void rejectsNotSelectedBid(String state) throws Exception {
        when(sources.lockBid(10L,CODE)).thenReturn(Optional.of(new BidSnapshot(10L,3L,state,new BigDecimal("1000000"))));
        expect(ErrorCode.PAYMENT_NOT_ELIGIBLE,()->service.create(1L,CODE,KEY,request(10,"CARD")));
    }
    @Test void rejectsAmbiguousMultipleSelections() throws Exception {
        when(sources.lockSelectedBidIds(CODE)).thenReturn(List.of(10L,11L));
        expect(ErrorCode.PAYMENT_NOT_ELIGIBLE,()->service.create(1L,CODE,KEY,request(10,"CARD")));
    }
    @Test void rejectsMissingExpertProfile() throws Exception {
        when(sources.expertProfileId(3L)).thenReturn(Optional.empty());
        expect(ErrorCode.EXPERT_PROFILE_NOT_FOUND,()->service.create(1L,CODE,KEY,request(10,"CARD")));
    }
    @Test void sameKeyReplaysOriginalWithoutSourceChangesOrNewAttempt() throws Exception {
        var binding=PaymentIdempotencyKey.bind(1L,hash(KEY),hash("v1\n"+CODE+"\n10\nCARD"),existing());
        when(keys.findForUpdate(1L,hash(KEY))).thenReturn(Optional.of(binding));
        var r=service.create(1L,CODE,KEY,request(10,"CARD"));assertThat(r.created()).isFalse();
        assertThat(r.response().getPaymentId()).isEqualTo(100L);verify(sources,never()).lockAnnouncement(anyString());
        verify(payments,never()).saveAndFlush(any());verify(keys,never()).saveAndFlush(any());
    }
    @Test void sameKeyDifferentRequestConflictsBeforeCreatingAnything() throws Exception {
        when(keys.findForUpdate(anyLong(),anyString())).thenReturn(Optional.of(PaymentIdempotencyKey.bind(1L,hash(KEY),"other",existing())));
        expect(ErrorCode.PAYMENT_IDEMPOTENCY_CONFLICT,()->service.create(1L,CODE,KEY,request(10,"TRANSFER")));
        verify(payments,never()).saveAndFlush(any());
    }
    @Test void differentKeySameBidAndMethodReturnsExistingAndBindsAlias() throws Exception {
        when(payments.findByBidIdForUpdate(10L)).thenReturn(Optional.of(existing()));
        var r=service.create(1L,CODE,"other-key",request(10,"CARD"));assertThat(r.created()).isFalse();
        verify(keys).saveAndFlush(any());verify(payments,never()).saveAndFlush(any());
    }
    @Test void differentKeySameBidDifferentMethodConflicts() throws Exception {
        when(payments.findByBidIdForUpdate(10L)).thenReturn(Optional.of(existing()));
        expect(ErrorCode.PAYMENT_ORDER_CONFLICT,()->service.create(1L,CODE,"other-key",request(10,"TRANSFER")));
    }
    @Test void changedSourceDoesNotOverwriteExistingSnapshot() throws Exception {
        Payment p=existing();when(payments.findByBidIdForUpdate(10L)).thenReturn(Optional.of(p));
        when(sources.lockBid(10L,CODE)).thenReturn(Optional.of(new BidSnapshot(10L,3L,"SELECTED",new BigDecimal("2000000"))));
        expect(ErrorCode.PAYMENT_ORDER_CONFLICT,()->service.create(1L,CODE,"new-key",request(10,"CARD")));
        assertThat(p.getAmount()).isEqualByComparingTo("1000000");
    }
    @ParameterizedTest @NullSource @ValueSource(strings={""," ","a b","a\nb"})
    void rejectsUnsafeOrMissingIdempotencyKey(String key) throws Exception {
        expect(ErrorCode.INVALID_INPUT,()->service.create(1L,CODE,key,request(10,"CARD")));
        verifyNoInteractions(sources);
    }
    @Test void rejectsExpertEvenWhenServiceIsCalledDirectly() throws Exception {
        when(sources.userType(1L,true)).thenReturn(Optional.of("EXPERT"));
        expect(ErrorCode.FORBIDDEN,()->service.create(1L,CODE,KEY,request(10,"CARD")));
    }
    @Test void ownerCanReadButOtherClientCannot() {
        Payment p=existing();when(payments.findById(100L)).thenReturn(Optional.of(p));
        assertThat(service.get(1L,100L).getAmount()).isEqualByComparingTo("1000000");
        when(sources.userType(99L,false)).thenReturn(Optional.of("CLIENT"));
        expect(ErrorCode.FORBIDDEN,()->service.get(99L,100L));
    }
    @Test void missingOrderIs404() { expect(ErrorCode.PAYMENT_NOT_FOUND,()->service.get(1L,999L)); }
    @Test void attemptIsOnlyPreparedAndProviderIsDisabled() {
        PaymentAttempt a=PaymentAttempt.prepare(existing(),1);
        assertThat(a.getStatus()).isEqualTo(PaymentAttemptStatus.PREPARED);
        assertThat(a.getProvider()).isEqualTo(PaymentProvider.DISABLED);
        assertThat(a.getAttemptKey()).hasSize(36);
    }
}
