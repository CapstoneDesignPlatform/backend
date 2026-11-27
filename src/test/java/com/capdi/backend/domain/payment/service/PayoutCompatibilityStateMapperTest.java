package com.capdi.backend.domain.payment.service;

import com.capdi.backend.domain.payment.entity.FinancialOperation;
import com.capdi.backend.domain.payment.entity.InternalPayout;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PayoutCompatibilityStateMapperTest {
    @Test void mapsCanonicalStatesWithoutChangingTheLegacyResponseVocabulary() {
        assertThat(project(FinancialOperation.State.READY).state()).isEqualTo(InternalPayout.State.RESERVED);
        assertThat(project(FinancialOperation.State.REQUESTED).state()).isEqualTo(InternalPayout.State.REQUESTED);
        assertThat(project(FinancialOperation.State.PENDING).state()).isEqualTo(InternalPayout.State.REQUESTED);
        assertThat(project(FinancialOperation.State.UNKNOWN).state()).isEqualTo(InternalPayout.State.UNKNOWN);
        assertThat(project(FinancialOperation.State.SUCCEEDED).state()).isEqualTo(InternalPayout.State.SUCCEEDED);
        assertThat(project(FinancialOperation.State.FAILED).state()).isEqualTo(InternalPayout.State.FAILED);
        var blocked=project(FinancialOperation.State.BLOCKED);
        assertThat(blocked.state()).isEqualTo(InternalPayout.State.UNKNOWN);
        assertThat(blocked.executionState()).isEqualTo("BLOCKED");
    }

    @Test void missingProviderRemainsExplicitlyPendingWithoutAClaimOrVersion() {
        assertThat(PayoutCompatibilityStateMapper.pendingExternalProvider()).isEqualTo(
            new PayoutCompatibilityStateMapper.Projection(InternalPayout.State.RESERVED,false,0,"PENDING_EXTERNAL_PROVIDER"));
    }

    private PayoutCompatibilityStateMapper.Projection project(FinancialOperation.State state) {
        var operation=mock(FinancialOperation.class);
        when(operation.getState()).thenReturn(state);
        when(operation.isSubmitClaimed()).thenReturn(state==FinancialOperation.State.REQUESTED
            || state==FinancialOperation.State.PENDING || state==FinancialOperation.State.UNKNOWN
            || state==FinancialOperation.State.SUCCEEDED || state==FinancialOperation.State.FAILED);
        when(operation.getVersion()).thenReturn(7L);
        return PayoutCompatibilityStateMapper.from(operation);
    }
}
