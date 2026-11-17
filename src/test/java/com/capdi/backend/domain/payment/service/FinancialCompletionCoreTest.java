package com.capdi.backend.domain.payment.service;
import com.capdi.backend.domain.payment.entity.*;
import com.capdi.backend.domain.payment.gateway.*;
import com.capdi.backend.domain.payment.config.*;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import java.time.*;
import java.math.BigDecimal;
import static org.assertj.core.api.Assertions.*;
class FinancialCompletionCoreTest {
 InternalTaxExecution execution(){return InternalTaxExecution.ready(1L,2L,"key","hash","policy","TEST_TAX","TEST");}
 @Test void issuanceAndTransmissionStatesNeverCollapse(){
  var e=execution();String token=e.claim(false,Instant.EPOCH);
  e.observe(token,InternalTaxExecution.State.ISSUED,TaxInvoiceProvider.TransmissionState.FAILED,"test-id",true,null,Instant.EPOCH);
  assertThat(e.getState()).isEqualTo(InternalTaxExecution.State.ISSUED);assertThat(e.isTestIssuance()).isTrue();
  token=e.claim(true,Instant.EPOCH.plusSeconds(60));e.observe(token,InternalTaxExecution.State.UNKNOWN,TaxInvoiceProvider.TransmissionState.SUCCEEDED,"test-id",true,null,Instant.EPOCH.plusSeconds(60));
  assertThat(e.getState()).isEqualTo(InternalTaxExecution.State.ISSUED);assertThat(e.getNtsState()).isEqualTo(TaxInvoiceProvider.TransmissionState.SUCCEEDED);
 }
 @Test void unknownTaxNeverAllowsAnotherIssuanceAndLookupBudgetIsBounded(){
  var e=execution();String token=e.claim(false,Instant.EPOCH);e.observe(token,InternalTaxExecution.State.UNKNOWN,TaxInvoiceProvider.TransmissionState.UNKNOWN,null,true,"TIMEOUT",Instant.EPOCH);
  assertThatThrownBy(()->e.claim(false,Instant.EPOCH.plusSeconds(60))).isInstanceOf(IllegalStateException.class);
  for(int n=0;n<8;n++){Instant now=Instant.EPOCH.plusSeconds((n+1)*100);token=e.claim(true,now);e.observe(token,InternalTaxExecution.State.UNKNOWN,TaxInvoiceProvider.TransmissionState.UNKNOWN,null,true,null,now);}
  assertThatThrownBy(()->e.claim(true,Instant.EPOCH.plusSeconds(900))).isInstanceOf(IllegalStateException.class);
  e.allowManualLookup(Instant.EPOCH.plusSeconds(900));assertThat(e.claim(true,Instant.EPOCH.plusSeconds(900))).isNotBlank();
 }
 @Test void workerAndExecutionAuthorizationDefaultDisabled(){
  try(var c=new AnnotationConfigApplicationContext(InternalFinancialRecoveryConfig.class,InternalProviderConfig.class)){
   assertThat(c.getBeansOfType(InternalFinancialRecoveryConfig.LookupWorker.class)).isEmpty();assertThat(c.getBean(TaxExecutionAuthorization.class).approved()).isFalse();
  }
 }
 @Test void canonicalInvoiceHasExactlyOneExecutionOwner(){
  var i=FinancialTaxInvoice.create(1L,2L,3L,4L,null,null,FinancialTaxInvoice.Taxation.TAXABLE,LocalDate.of(2026,1,1),new BigDecimal("90"),BigDecimal.TEN,"a".repeat(64));
  i.claimExecutionOwner("INTERNAL");i.claimExecutionOwner("INTERNAL");assertThatThrownBy(()->i.claimExecutionOwner("LEGACY")).isInstanceOf(IllegalStateException.class);
 }
 @Test void acceptedDocumentsAreRequiredForProfileApproval(){
  assertThatThrownBy(()->SettlementProfileReview.record(1L,2L,1,"key","hash",SettlementProfileReview.Decision.APPROVED,SettlementProfileReview.Documents.MISSING,"MISSING")).isInstanceOf(IllegalArgumentException.class);
 }
 @Test void sensitiveTaxRequestToStringIsRedacted(){
  var r=new TaxInvoiceProvider.Request("id","reference",null,"hash",BigDecimal.ONE,BigDecimal.ZERO,BigDecimal.ONE,"KRW",LocalDate.of(2026,1,1),"TAXABLE","SYNTHETIC_SUPPLIER_PRIVATE","SYNTHETIC_RECIPIENT_PRIVATE");
  assertThat(r.toString()).doesNotContain("SYNTHETIC_SUPPLIER_PRIVATE","SYNTHETIC_RECIPIENT_PRIVATE");
 }
}
