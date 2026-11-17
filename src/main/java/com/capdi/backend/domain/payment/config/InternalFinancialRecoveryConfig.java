package com.capdi.backend.domain.payment.config;
import com.capdi.backend.domain.payment.service.*;
import org.springframework.context.annotation.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.*;
@Configuration @EnableScheduling
@ConditionalOnProperty(name="financial.internal-recovery.enabled",havingValue="true")
public class InternalFinancialRecoveryConfig {
 @Bean LookupWorker internalLookupWorker(InternalPayoutService payouts,InternalTaxExecutionService tax){return new LookupWorker(payouts,tax);}
 public static final class LookupWorker {
  private final InternalPayoutService payouts;private final InternalTaxExecutionService tax;
  public LookupWorker(InternalPayoutService payouts,InternalTaxExecutionService tax){this.payouts=payouts;this.tax=tax;}
  @Scheduled(fixedDelayString="${financial.internal-recovery.delay-ms:30000}",initialDelayString="${financial.internal-recovery.delay-ms:30000}")
  public void lookupOnly(){try{payouts.runDue();}catch(RuntimeException ignored){}try{tax.runDue();}catch(RuntimeException ignored){}}
 }
}
