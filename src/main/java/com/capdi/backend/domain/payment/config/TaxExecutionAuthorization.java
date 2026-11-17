package com.capdi.backend.domain.payment.config;
/** An explicit legal/tax review gate, independent of an internal administrator decision. */
@FunctionalInterface public interface TaxExecutionAuthorization {boolean approved();}
