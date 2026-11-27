package com.capdi.backend.domain.payment.entity;
import com.capdi.backend.global.entity.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;
import java.util.UUID;
@Entity @Table(name="internal_tax_executions") @Getter @NoArgsConstructor(access=AccessLevel.PROTECTED)
public class InternalTaxExecution extends BaseTimeEntity {
 @Column(name="document_version",nullable=false,updatable=false) private long documentVersion;
 public void captureDocumentVersion(long version){documentVersion=version;}
 @Column(name="financial_operation_id",unique=true) private Long financialOperationId;
 public void linkFinancialOperation(Long operation){
  if(operation==null || financialOperationId!=null && !financialOperationId.equals(operation))
   throw new IllegalStateException("Canonical operation link is immutable");
  financialOperationId=operation;
 }
 public enum State { READY, REQUESTED, UNKNOWN, ISSUED, FAILED }
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
 @Column(name="document_id",nullable=false,unique=true,updatable=false) private Long documentId;
 @Column(name="invoice_id",nullable=false,unique=true,updatable=false) private Long invoiceId;
 @Column(nullable=false,unique=true,length=50,updatable=false) private String identity;
 @Column(name="request_key",nullable=false,length=64,updatable=false) private String requestKey;
 @Column(name="snapshot_hash",nullable=false,length=64,updatable=false) private String snapshotHash;
 @Column(name="policy_version",nullable=false,length=100,updatable=false) private String policyVersion;
 @Column(name="provider_code",nullable=false,length=50,updatable=false) private String providerCode;
 @Column(name="provider_environment",nullable=false,length=30,updatable=false) private String providerEnvironment;
 @Enumerated(EnumType.STRING) @Column(nullable=false,length=20) private State state;
 @Enumerated(EnumType.STRING) @Column(name="nts_state",nullable=false,length=20) private com.capdi.backend.domain.payment.gateway.TaxInvoiceProvider.TransmissionState ntsState;
 @Column(name="external_id",unique=true,length=200) private String externalId;
 @Column(nullable=false) private boolean dispatched;
 @Column(name="test_issuance",nullable=false) private boolean testIssuance;
 @Column(length=50) private String lease;
 @Column(name="lease_until") private Instant leaseUntil;
 @Column(name="lookup_count",nullable=false) private int lookupCount;
 @Column(name="next_lookup_at") private Instant nextLookupAt;
 @Column(name="failure_code",length=50) private String failureCode;
 @Version @Column(nullable=false) private long version;
 public static InternalTaxExecution ready(Long document,Long invoice,String key,String hash,String policy,String provider,String environment){
  var e=new InternalTaxExecution();e.documentId=document;e.invoiceId=invoice;e.requestKey=key;e.snapshotHash=hash;e.policyVersion=policy;e.providerCode=provider;e.providerEnvironment=environment;
  e.identity="tax_"+UUID.randomUUID();e.state=State.READY;e.ntsState=com.capdi.backend.domain.payment.gateway.TaxInvoiceProvider.TransmissionState.NOT_SENT;return e;
 }
 public String claim(boolean lookup,Instant now){
  if(leaseUntil!=null && leaseUntil.isAfter(now) || lookup && (!dispatched || lookupCount>=8 || state==State.FAILED || state==State.ISSUED && ntsState==com.capdi.backend.domain.payment.gateway.TaxInvoiceProvider.TransmissionState.SUCCEEDED)
   || !lookup && (dispatched || state!=State.READY))throw new IllegalStateException("Execution cannot be claimed");
  if(lookup)lookupCount++;else dispatched=true;
  state=state==State.ISSUED?state:State.REQUESTED;lease=UUID.randomUUID().toString();leaseUntil=now.plusSeconds(45);return lease;
 }
 public boolean owns(String token){return token!=null && token.equals(lease);}
 public void observe(String token,State next,com.capdi.backend.domain.payment.gateway.TaxInvoiceProvider.TransmissionState nts,String external,boolean test,String failure,Instant now){
  if(!owns(token))throw new IllegalStateException("Stale observation");
  if(externalId!=null && external!=null && !externalId.equals(external))throw new IllegalStateException("Provider identity changed");
  if(external!=null)externalId=external;
  if(state!=State.ISSUED)state=next;
  if(ntsState!=com.capdi.backend.domain.payment.gateway.TaxInvoiceProvider.TransmissionState.SUCCEEDED)ntsState=nts;
  testIssuance=testIssuance || test;failureCode=failure;lease=null;leaseUntil=null;
  nextLookupAt=now.plusSeconds(Math.min(3600,30L<<Math.min(lookupCount,6)));
 }
 public void allowManualLookup(Instant now){if(!dispatched || leaseUntil!=null && leaseUntil.isAfter(now))throw new IllegalStateException("Busy");lookupCount=0;nextLookupAt=now;}
}
