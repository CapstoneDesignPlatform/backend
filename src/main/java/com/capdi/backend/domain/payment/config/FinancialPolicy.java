package com.capdi.backend.domain.payment.config;
import com.capdi.backend.domain.payment.gateway.FinancialGateway;
import java.util.Set;
/** Reviews authorize business processing, never fabricate bank verification or provider success. */
public record FinancialPolicy(Set<FinancialGateway.Kind> reviewedKinds,String taxPolicyVersion,boolean sensitiveCollectionApproved,Set<String> authorizedSupplierNumbers,String partnerContractId) {
 public FinancialPolicy { reviewedKinds=Set.copyOf(reviewedKinds);authorizedSupplierNumbers=Set.copyOf(authorizedSupplierNumbers); }
 public boolean reviewed(FinancialGateway.Kind kind){return reviewedKinds.contains(kind);}
}
