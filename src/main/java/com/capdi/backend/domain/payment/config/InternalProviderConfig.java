package com.capdi.backend.domain.payment.config;
import com.capdi.backend.domain.payment.gateway.*;
import com.capdi.backend.global.exception.*;
import org.springframework.context.annotation.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
@Configuration public class InternalProviderConfig {
 @Bean @ConditionalOnMissingBean(TaxExecutionAuthorization.class) public TaxExecutionAuthorization taxExecutionAuthorization(org.springframework.core.env.Environment env){return ()->env.getProperty("financial.internal-tax.execution-approved",Boolean.class,false);}
 private static CustomException disabled(){return new CustomException(ErrorCode.PAYMENT_FINANCIAL_POLICY_UNCONFIRMED);}
 @Bean @ConditionalOnMissingBean(PayoutProvider.class) public PayoutProvider payoutProvider(){return new PayoutProvider(){
  public Identity identity(){return new Identity("DISABLED","DISABLED");}
  public boolean enabled(){return false;}
  public Result request(Request r){throw disabled();}
  public Result lookup(String identity,String external){throw disabled();}
 };}
 @Bean @ConditionalOnMissingBean(TaxInvoiceProvider.class) public TaxInvoiceProvider taxInvoiceProvider(){return new TaxInvoiceProvider(){
  public boolean enabled(){return false;}
  public Result request(Request r){throw disabled();}
  public Result lookup(String identity,String external){throw disabled();}
 };}
}
