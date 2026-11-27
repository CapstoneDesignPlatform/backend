package com.capdi.backend.domain.payment.service;

import com.capdi.backend.domain.payment.entity.FinancialOperation;
import com.capdi.backend.domain.payment.entity.InternalPayout;

/** Read-only projection from the canonical payout operation to the deprecated API contract. */
public final class PayoutCompatibilityStateMapper {
    private PayoutCompatibilityStateMapper() {}

    public record Projection(InternalPayout.State state, boolean dispatched, long version, String executionState) {}

    public static Projection from(FinancialOperation operation) {
        var state = switch (operation.getState()) {
            case READY -> InternalPayout.State.RESERVED;
            case REQUESTED, PENDING -> InternalPayout.State.REQUESTED;
            case SUCCEEDED -> InternalPayout.State.SUCCEEDED;
            case FAILED -> InternalPayout.State.FAILED;
            case UNKNOWN, BLOCKED -> InternalPayout.State.UNKNOWN;
        };
        return new Projection(state, operation.isSubmitClaimed(), operation.getVersion(), operation.getState().name());
    }

    public static Projection pendingExternalProvider() {
        return new Projection(InternalPayout.State.RESERVED, false, 0, "PENDING_EXTERNAL_PROVIDER");
    }
}
