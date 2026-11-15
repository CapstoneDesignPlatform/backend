package com.capdi.backend.domain.payment.service;
import com.capdi.backend.domain.payment.entity.*;
import com.capdi.backend.domain.payment.gateway.*;
import com.capdi.backend.domain.payment.config.InternalProviderConfig;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import java.math.BigDecimal;
import static org.assertj.core.api.Assertions.*;
class InternalSettlementCoreTest {
 @Test void reviewRequiresExplicitDecisionAndCannotBeOverwritten(){
  var r=SettlementReview.request(1L,"key",2,3,new BigDecimal("900"),4L);
  assertThat(r.getState()).isEqualTo(SettlementReview.State.PENDING_APPROVAL);
  r.decide(5L,SettlementReview.State.APPROVED,"CHECKED");
  assertThat(r.getReviewerId()).isEqualTo(5L);
  assertThatThrownBy(()->r.decide(6L,SettlementReview.State.REJECTED,"CHANGED")).isInstanceOf(IllegalStateException.class);
 }
 @Test void balanceOrProfileChangeInvalidatesReview(){
  var r=SettlementReview.request(1L,"key",2,3,new BigDecimal("900"),4L);
  assertThat(r.current(2,3)).isTrue();assertThat(r.current(3,3)).isFalse();assertThat(r.current(2,4)).isFalse();
 }
 @Test void fractionalWonCannotBeApproved(){assertThatThrownBy(()->SettlementReview.request(1L,"key",0,1,new BigDecimal("0.9"),1L)).isInstanceOf(IllegalArgumentException.class);}
 @Test void bankNumberCannotReplaceVirtualToken(){
  assertThatThrownBy(()->ExpertSettlementProfile.create(1L,1,ExpertSettlementProfile.BusinessType.INDIVIDUAL,"1234567890",1L,1,1,1L)).isInstanceOf(IllegalArgumentException.class);
  assertThat(ExpertSettlementProfile.create(1L,1,ExpertSettlementProfile.BusinessType.INDIVIDUAL,"virtual_synthetic",1L,1,1,1L).getRevision()).isEqualTo(1);
 }
 @Test void providersDefaultDisabledAndNeverFallbackToFake(){try(var c=new AnnotationConfigApplicationContext(InternalProviderConfig.class)){
  var p=c.getBean(PayoutProvider.class);var t=c.getBean(TaxInvoiceProvider.class);
  assertThat(p.enabled()).isFalse();assertThat(t.enabled()).isFalse();
  assertThatThrownBy(()->p.request(null)).isInstanceOf(RuntimeException.class);assertThatThrownBy(()->t.request(null)).isInstanceOf(RuntimeException.class);
 }}
 @Test void testIssuanceHasNoLegalEffectEvenWhenNtsReportsSuccess(){
  var r=new TaxInvoiceProvider.Result("test","test-id",TaxInvoiceProvider.IssuanceState.ISSUED,TaxInvoiceProvider.TransmissionState.SUCCEEDED,true,true);
  assertThat(r.legallyIssued()).isFalse();
 }
}
