package com.capdi.backend.domain.payment.config;
import com.capdi.backend.domain.payment.service.*;
import org.springframework.context.annotation.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.*;
@Configuration @EnableScheduling
@ConditionalOnProperty(name="financial.worker.enabled",havingValue="true")
public class FinancialLookupSchedulerConfig {
 @Bean LookupOnlyScheduler lookupOnlyScheduler(FinancialWorkflowService finance,PaymentReconciliationService payments){return new LookupOnlyScheduler(finance,payments);}
 public static final class LookupOnlyScheduler {
  private final FinancialWorkflowService finance;private final PaymentReconciliationService payments;
  public LookupOnlyScheduler(FinancialWorkflowService finance,PaymentReconciliationService payments){this.finance=finance;this.payments=payments;}
  @Scheduled(fixedDelayString="${financial.worker.delay-ms:30000}",initialDelayString="${financial.worker.delay-ms:30000}")
  public void lookupOnly(){try{finance.runDue();}catch(RuntimeException ignored){}try{payments.runDue();}catch(RuntimeException ignored){}}
 }
}
