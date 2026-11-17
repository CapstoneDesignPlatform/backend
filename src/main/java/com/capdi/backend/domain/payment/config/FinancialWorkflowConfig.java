package com.capdi.backend.domain.payment.config;
import com.capdi.backend.domain.payment.gateway.*;
import com.capdi.backend.domain.payment.gateway.portone.*;
import com.capdi.backend.domain.payment.service.FinancialVault;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.*;
import org.springframework.core.env.Environment;
import java.util.*;

@Configuration
public class FinancialWorkflowConfig {
 @Bean @ConditionalOnMissingBean(FinancialPolicy.class)
 FinancialPolicy financialPolicy(Environment e){
  Set<FinancialGateway.Kind> reviewed=EnumSet.noneOf(FinancialGateway.Kind.class);
  for(var k:FinancialGateway.Kind.values())if(e.getProperty("financial.policy."+k.name().toLowerCase(Locale.ROOT)+"-reviewed",Boolean.class,false))reviewed.add(k);
  Set<String> suppliers=new HashSet<>();
  for(String v:e.getProperty("financial.tax.authorized-suppliers","").split(","))if(!v.isBlank())suppliers.add(v.trim());
  return new FinancialPolicy(reviewed,e.getProperty("financial.tax.policy-version","UNREVIEWED"),
   e.getProperty("financial.sensitive-collection-approved",Boolean.class,false),suppliers,e.getProperty("financial.partner.contract-id","UNCONFIGURED"));
 }
 @Bean @ConditionalOnMissingBean(FinancialVault.class)
 FinancialVault financialVault(Environment e,ObjectMapper mapper){
  try {
   String active=e.getProperty("financial.crypto.active-key-id","v1");
   String encoded=e.getProperty("financial.crypto.keyring-json","{}");
   if(encoded.length()>8192)throw new IllegalArgumentException();
   Map<String,byte[]> keys=new HashMap<>();var root=mapper.readTree(encoded);
   if(!root.isObject())throw new IllegalArgumentException();
   root.fields().forEachRemaining(entry->keys.put(entry.getKey(),Base64.getDecoder().decode(entry.getValue().textValue())));
   return new FinancialVault(active,keys);
  }catch(Exception ex){throw new IllegalStateException("Invalid financial encryption configuration");}
 }
 @Bean @ConditionalOnMissingBean(FinancialGateway.class)
 FinancialGateway financialGateway(Environment e,ObjectMapper mapper) {
  var settings=new PortOneSettings(e.getProperty("financial.portone.enabled",Boolean.class,false),
   e.getProperty("financial.portone.sandbox-approved",Boolean.class,false),e.getProperty("financial.portone.environment","DISABLED"),
   e.getProperty("financial.portone.merchant-id"),e.getProperty("financial.portone.store-id"),e.getProperty("financial.portone.channel-key"),
   e.getProperty("financial.portone.api-secret"),null,java.time.Duration.ofSeconds(10));
  Set<FinancialGateway.Kind> writes=EnumSet.noneOf(FinancialGateway.Kind.class);
  for(var k:FinancialGateway.Kind.values())if(e.getProperty("financial.portone."+k.name().toLowerCase(Locale.ROOT)+"-enabled",Boolean.class,false))writes.add(k);
  return new PortOneFinancialGateway(settings,new PortOneFinancialTransport.Jdk(settings),mapper,writes);
 }
}
