package com.capdi.backend.domain.payment.service;
import com.capdi.backend.domain.payment.entity.*;
import com.capdi.backend.global.exception.CustomException;
import com.capdi.backend.domain.payment.gateway.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.util.ReflectionTestUtils;
import java.math.BigDecimal;
import java.time.Instant;
import static org.assertj.core.api.Assertions.*;

class PaymentGatewaySafetyTest {
    Payment p;PaymentAttempt a;FakePaymentGateway fake;
    @BeforeEach void setup() {
        fake=new FakePaymentGateway();p=Payment.create(1L,2L,"SYNTHETIC",3L,4L,new BigDecimal("1000"),PaymentMethod.CARD, 1);
        ReflectionTestUtils.setField(p,"id",5L);a=PaymentAttempt.start(p,1,"hash",fake.identity(),Instant.parse("2026-01-01T00:00:00Z"));
        ReflectionTestUtils.setField(a,"id",6L);p.beginAttempt(6L);
    }
    @Test void matchingAuthoritativeObservationIsAllowed() {assertThat(PaymentGatewaySafety.matches(a,fake.observation(a.reference(),PaymentGateway.State.PAID))).isTrue();}
    @ParameterizedTest @ValueSource(strings={"amount","currency","order","merchant","provider","environment","unverified","missing_tx","invalid_raw","waiting_card"})
    void rejectsEveryIncorrectPaymentProofDimension(String field) {
        var id=fake.identity();String order=a.getOrderId(),currency="KRW",tx="synthetic-tx",raw="PAID";
        var amount=new BigDecimal("1000");boolean verified=true;var state=PaymentGateway.State.PAID;
        switch(field) {
            case "amount" -> amount=new BigDecimal("999");
            case "currency" -> currency="USD";
            case "order" -> order="another-order";
            case "merchant" -> id=new PaymentGateway.Identity(id.code(),"another-merchant",id.environment());
            case "provider" -> id=new PaymentGateway.Identity("OTHER",id.merchant(),id.environment());
            case "environment" -> id=new PaymentGateway.Identity(id.code(),id.merchant(),PaymentEnvironment.PRODUCTION);
            case "unverified" -> verified=false;
            case "missing_tx" -> tx=null;
            case "invalid_raw" -> raw="private provider text";
            case "waiting_card" -> state=PaymentGateway.State.WAITING_DEPOSIT;
        }
        assertThat(PaymentGatewaySafety.matches(a,new PaymentGateway.Observation(id,order,amount,currency,tx,raw,state,verified))).isFalse();
    }
    @Test void paidAtIsWrittenOnceAndCannotBeRegressed() {
        Instant now=Instant.parse("2026-01-01T00:00:00Z");p.recordAttempt(6L,PaymentStatus.PAID,now);
        p.recordAttempt(6L,PaymentStatus.PAID,now.plusSeconds(20));assertThat(p.getPaidAt()).isEqualTo(now);
        assertThatThrownBy(()->p.recordAttempt(6L,PaymentStatus.UNKNOWN,now)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(()->p.recordAttempt(7L,PaymentStatus.PAID,now)).isInstanceOf(IllegalStateException.class);
    }
    @Test void leaseAndTokenRotateOnlyAfterExpiry() {
        Instant now=Instant.parse("2026-01-01T00:00:00Z");String token=a.getExecutionToken();
        assertThatThrownBy(()->a.claim(now.plusSeconds(1))).isInstanceOf(IllegalStateException.class);
        a.claim(now.plusSeconds(31));assertThat(a.tokenMatches(token)).isFalse();
    }
    @Test void inboxLeasePreventsConcurrentAndDuplicateProcessing() {
        var e=PaymentWebhookEvent.receive(6L,"TEST_FAKE","synthetic",PaymentEnvironment.TEST,"hash","digest");
        Instant now=Instant.parse("2026-01-01T00:00:00Z");
        assertThat(e.claim("first",now)).isTrue();assertThat(e.claim("second",now)).isFalse();
        assertThat(e.claim("second",now.plusSeconds(31))).isTrue();e.finish("first",true);
        assertThat(e.getState()).isEqualTo(PaymentWebhookEvent.State.PROCESSING);e.finish("second",true);
        assertThat(e.claim("third",now.plusSeconds(100))).isFalse();
    }

    @Test void gatewayWithoutLookupCannotStartFinancialExecution() {
        fake.capabilities.remove(PaymentGateway.Capability.LOOKUP);
        assertThatThrownBy(()->PaymentGatewaySafety.require(fake,PaymentGateway.Capability.PREPARE)).isInstanceOf(CustomException.class);
        assertThatThrownBy(()->PaymentGatewaySafety.require(fake,PaymentGateway.Capability.CONFIRM)).isInstanceOf(CustomException.class);
    }
}
