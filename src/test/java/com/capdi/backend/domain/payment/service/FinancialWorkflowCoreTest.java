package com.capdi.backend.domain.payment.service;
import com.capdi.backend.domain.payment.config.*;
import com.capdi.backend.domain.payment.entity.*;
import com.capdi.backend.domain.payment.gateway.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.test.util.ReflectionTestUtils;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

class FinancialWorkflowCoreTest {
 Payment payment(String amount){
  var p=Payment.create(1L,2L,"synthetic",3L,4L,new BigDecimal(amount),PaymentMethod.CARD, 1);
  ReflectionTestUtils.setField(p,"id",1L);p.beginAttempt(1L);p.recordAttempt(1L,PaymentStatus.PAID,Instant.EPOCH);return p;
 }
 @Test void defaultProviderAndSensitiveCollectionAreDisabled(){
  try(var c=new AnnotationConfigApplicationContext()){
   c.registerBean(ObjectMapper.class,(java.util.function.Supplier<ObjectMapper>)ObjectMapper::new);c.register(FinancialWorkflowConfig.class,FinancialLookupSchedulerConfig.class);c.refresh();
   var gateway=c.getBean(FinancialGateway.class);
   for(var kind:FinancialGateway.Kind.values())assertThat(gateway.canSubmit(kind)).isFalse();
   assertThat(c.getBean(FinancialVault.class).available()).isFalse();
   assertThat(c.getBean(FinancialPolicy.class).sensitiveCollectionApproved()).isFalse();
   assertThat(c.getBeansOfType(FinancialLookupSchedulerConfig.LookupOnlyScheduler.class)).isEmpty();
  }
 }
 @Test void gcmContextTamperingAndMissingKeysFailClosed(){
  var vault=new FinancialVault("v1",Map.of("v1",new byte[32]));
  String ciphertext=vault.seal("account:1:1","SYNTHETIC_PRIVATE_NUMBER");
  assertThat(ciphertext).doesNotContain("SYNTHETIC_PRIVATE_NUMBER");
  assertThat(vault.open("account:1:1",ciphertext)).isEqualTo("SYNTHETIC_PRIVATE_NUMBER");
  assertThatThrownBy(()->vault.open("account:2:1",ciphertext)).hasMessageNotContaining("SYNTHETIC_PRIVATE_NUMBER");
  assertThatThrownBy(()->new FinancialVault("v2",Map.of()).seal("x","private")).isInstanceOf(RuntimeException.class);
 }
 @Test void keyRotationRetainsOldCiphertextAndFingerprintWithKeyring(){
  byte[] second=new byte[32];Arrays.fill(second,(byte)1);
  var old=new FinancialVault("v1",Map.of("v1",new byte[32]));String cipher=old.seal("x","synthetic");
  var rotated=new FinancialVault("v2",Map.of("v1",new byte[32],"v2",second));
  assertThat(rotated.open("x",cipher)).isEqualTo("synthetic");
  assertThat(rotated.matchesFingerprint(cipher,"synthetic",old.fingerprint("synthetic"))).isTrue();
  assertThat(rotated.matchesFingerprint(cipher,"changed",old.fingerprint("synthetic"))).isFalse();
 }
 @ParameterizedTest @ValueSource(strings={"1","9","11","1001","9999999999999"})
 void fractionalWonAllocationsKeepExactSumAndCannotReserve(String amount){
  var b=SettlementBalance.from(payment(amount));
  assertThat(b.platform().add(b.expert())).isEqualByComparingTo(amount);
  assertThat(b.isHeld()).isTrue();
  assertThatThrownBy(()->b.reserve(BigDecimal.ONE)).isInstanceOf(IllegalStateException.class);
 }
 @Test void reservationAndPostPayoutRefundProduceRecoverableWithoutNegativeAvailability(){
  var b=SettlementBalance.from(payment("1000"));b.reserve(new BigDecimal("900"));
  assertThat(b.available()).isEqualByComparingTo("0");assertThatThrownBy(()->b.reserve(BigDecimal.ONE)).isInstanceOf(IllegalStateException.class);
  b.paid(new BigDecimal("900"));b.refund(new BigDecimal("100"));
  assertThat(b.platform()).isEqualByComparingTo("90");assertThat(b.expert()).isEqualByComparingTo("810");
  assertThat(b.getRecoverable()).isEqualByComparingTo("90");assertThat(b.available()).isEqualByComparingTo("0");assertThat(b.isHeld()).isTrue();
  assertThatThrownBy(()->b.refund(new BigDecimal("901"))).isInstanceOf(IllegalStateException.class);
 }
 @Test void paidAndReleaseRequireExistingReservation(){
  var b=SettlementBalance.from(payment("1000"));
  assertThatThrownBy(()->b.paid(BigDecimal.ONE)).isInstanceOf(IllegalStateException.class);
  assertThatThrownBy(()->b.release(BigDecimal.ONE)).isInstanceOf(IllegalStateException.class);
 }
 FinancialOperation operation(){
  return FinancialOperation.create("fin_00000000-0000-0000-0000-000000000001",FinancialGateway.Kind.REFUND,new FinancialGateway.Identity("TEST_FAKE","m","s","TEST"),1L,2L,null,null,3L,null,0,"k","h","encrypted",Instant.EPOCH);
 }
 @Test void recoveryNeverClaimsAnUnsentWriteAndWriteCannotBeRepeated(){
  var o=operation();assertThat(o.claim(true,Instant.EPOCH)).isNull();
  String token=o.claim(false,Instant.EPOCH);assertThat(token).isNotNull();
  assertThat(o.claim(false,Instant.EPOCH.plusSeconds(100))).isNull();
  assertThat(o.claim(true,Instant.EPOCH.plusSeconds(1))).isNull();
  String next=o.claim(true,Instant.EPOCH.plusSeconds(46));assertThat(next).isNotEqualTo(token);assertThat(o.owns(token)).isFalse();
 }
 @Test void lookupRetriesAreBoundedAndManualResetCannotAuthorizeWrite(){
  var o=operation();o.claim(false,Instant.EPOCH);o.uncertain("TIMEOUT",Instant.EPOCH);
  for(int i=0;i<8;i++){
   assertThat(o.claim(true,Instant.EPOCH.plusSeconds(10000L*(i+1)))).isNotNull();
   o.uncertain("TIMEOUT",Instant.EPOCH.plusSeconds(10000L*(i+1)));
  }
  assertThat(o.getState()).isEqualTo(FinancialOperation.State.BLOCKED);
  assertThat(o.getLookupCount()).isEqualTo(8);
  o.manualReview(Instant.EPOCH.plusSeconds(100000));assertThat(o.claim(false,Instant.EPOCH.plusSeconds(100000))).isNull();
 }
 @Test void unpaidSettlementAndInvalidTaxTotalsAreRejected(){
  var p=Payment.create(1L,2L,"synthetic",3L,4L,new BigDecimal("1000"),PaymentMethod.CARD, 1);
  assertThatThrownBy(()->SettlementBalance.from(p)).isInstanceOf(IllegalArgumentException.class);
  assertThatThrownBy(()->FinancialTaxInvoice.create(1L,1L,1L,2L,null,null,FinancialTaxInvoice.Taxation.FREE,LocalDate.now(),BigDecimal.TEN,BigDecimal.ONE,"0".repeat(64))).isInstanceOf(IllegalArgumentException.class);
 }
}
