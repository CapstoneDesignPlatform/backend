package com.capdi.backend.domain.payment.service;
import com.capdi.backend.domain.payment.dto.*;
import com.capdi.backend.domain.payment.entity.*;
import com.capdi.backend.domain.payment.gateway.*;
import com.capdi.backend.domain.payment.config.PaymentProcessingConfig;
import com.capdi.backend.global.exception.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.dao.DataIntegrityViolationException;
import java.math.BigDecimal;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class PaymentExecutionServiceTest {
    PaymentExecutionPersistenceService persistence;
    FakePaymentGateway fake;PaymentExecutionService service;
    PaymentExecutionPersistenceService.Execution run;
    @BeforeEach void setup() {
        persistence=mock(PaymentExecutionPersistenceService.class);fake=new FakePaymentGateway();
        service=new PaymentExecutionService(persistence,fake);
        var reference=new PaymentGateway.Reference(fake.identity(),"order","attempt",null,new BigDecimal("1000"),"KRW",PaymentMethod.CARD);
        run=new PaymentExecutionPersistenceService.Execution(1L,2L,3L,"token",reference,null,true);
        when(persistence.prepare(1L,2L,"key",PaymentMethod.CARD)).thenReturn(run);
        when(persistence.failed(eq(run),any())).thenReturn(PaymentAttemptResponse.builder()
                .status(PaymentAttemptStatus.UNKNOWN).paymentStatus(PaymentStatus.UNKNOWN).build());
    }
    @Test void realDefaultGatewayIsDisabledAndHasNoCapabilities() {
        try(var context=new AnnotationConfigApplicationContext(PaymentProcessingConfig.class)) {
            var gateway=context.getBean(PaymentGateway.class);assertThat(gateway.enabled()).isFalse();
            assertThat(gateway.identity().environment()).isEqualTo(PaymentEnvironment.DISABLED);
            for(var c:PaymentGateway.Capability.values())assertThat(gateway.supports(c)).isFalse();
            assertThatThrownBy(()->PaymentGatewaySafety.require(gateway,PaymentGateway.Capability.PREPARE))
                    .isInstanceOfSatisfying(CustomException.class,e->assertThat(e.getErrorCode()).isEqualTo(ErrorCode.PAYMENT_GATEWAY_DISABLED));
        }
    }
    @Test void replayNeverInvokesGateway() {
        when(persistence.prepare(1L,2L,"key",PaymentMethod.CARD)).thenReturn(
                new PaymentExecutionPersistenceService.Execution(1L,2L,3L,null,run.reference(),null,false));
        service.prepare(1L,2L,"key",PaymentMethod.CARD);
        assertThat(fake.prepares).hasValue(0);
    }
    @ParameterizedTest @EnumSource(value=PaymentProcessingFailure.class,names={"TIMEOUT","NETWORK","PROVIDER_ERROR"})
    void ambiguousGatewayFailureIsNotFailedOrPaid(PaymentProcessingFailure failure) {
        fake.prepareFunction=r->{throw new PaymentGateway.GatewayException(failure);};
        assertThat(service.prepare(1L,2L,"key",PaymentMethod.CARD).getStatus()).isEqualTo(PaymentAttemptStatus.UNKNOWN);
        verify(persistence).failed(run,failure);verify(persistence,never()).complete(any(),any());
    }
    @Test void unexpectedProviderExceptionDoesNotExposeItsText() {
        fake.prepareFunction=r->{throw new IllegalStateException("SYNTHETIC_PRIVATE_PROVIDER_TEXT");};
        service.prepare(1L,2L,"key",PaymentMethod.CARD);verify(persistence).failed(run,PaymentProcessingFailure.PROVIDER_ERROR);
    }
    @Test void transactionCollisionIsHeldForReconciliation() {
        when(persistence.complete(eq(run),any())).thenThrow(new DataIntegrityViolationException("synthetic"));
        service.prepare(1L,2L,"key",PaymentMethod.CARD);verify(persistence).failed(run,PaymentProcessingFailure.TRANSACTION_CONFLICT);
    }
    @Test void gatewaySuccessThenStorageFailureDoesNotPrepareAgain() {
        when(persistence.complete(eq(run),any())).thenThrow(new IllegalStateException("synthetic"));
        service.prepare(1L,2L,"key",PaymentMethod.CARD);
        assertThat(fake.prepares).hasValue(1);verify(persistence).failed(run,PaymentProcessingFailure.STORAGE_FAILURE);
    }
    @Test void storageFallbackFailureIs503RatherThanFakeSuccess() {
        when(persistence.complete(eq(run),any())).thenThrow(new IllegalStateException("synthetic"));
        when(persistence.failed(eq(run),any())).thenThrow(new IllegalStateException("synthetic"));
        assertThatThrownBy(()->service.prepare(1L,2L,"key",PaymentMethod.CARD))
                .isInstanceOfSatisfying(CustomException.class,e->assertThat(e.getErrorCode()).isEqualTo(ErrorCode.PAYMENT_EXECUTION_STORAGE_FAILED));
    }
    @Test void staleCompletionCannotBeRetriedAsUnknownWrite() {
        when(persistence.complete(eq(run),any())).thenThrow(new CustomException(ErrorCode.PAYMENT_EXECUTION_STALE));
        assertThatThrownBy(()->service.prepare(1L,2L,"key",PaymentMethod.CARD)).isInstanceOf(CustomException.class);
        verify(persistence,never()).failed(any(),any());
    }
}
