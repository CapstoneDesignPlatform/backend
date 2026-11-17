package com.capdi.backend.domain.payment.gateway;
/** Issuance and NTS delivery are independent provider facts; test receipts have no legal effect. */
public interface TaxInvoiceProvider {
 record Identity(String code,String environment){}
 default Identity identity(){return new Identity("DISABLED","DISABLED");}
 enum IssuanceState { REQUESTED, UNKNOWN, ISSUED, FAILED }
 enum TransmissionState { NOT_SENT, PENDING, SUCCEEDED, FAILED, UNKNOWN }
 record Item(String code,java.math.BigDecimal supply,java.math.BigDecimal vat){}
 record Request(String documentIdentity,String encryptedSnapshotReference,String originalDocumentIdentity,
 String snapshotHash,java.math.BigDecimal supply,java.math.BigDecimal vat,java.math.BigDecimal total,String currency,
 java.time.LocalDate writeDate,String taxation,String supplierJson,String recipientJson,String modification,java.util.List<Item> items) {
  public Request(String identity,String reference,String original,String hash,java.math.BigDecimal supply,java.math.BigDecimal vat,java.math.BigDecimal total,String currency,java.time.LocalDate date,String taxation,String supplier,String recipient){this(identity,reference,original,hash,supply,vat,total,currency,date,taxation,supplier,recipient,null,java.util.List.of());}
  public Request(String identity,String snapshot,String original){this(identity,snapshot,original,null,null,null,null,null,null,null,null,null);}
  @Override public String toString(){return "TaxInvoiceRequest[REDACTED]";}
 }
 record Result(String documentIdentity,String providerDocumentId,IssuanceState issuance,
  TransmissionState transmission,boolean authoritative,boolean testEnvironment,
 String snapshotHash,java.math.BigDecimal supply,java.math.BigDecimal vat,java.math.BigDecimal total,String currency) {
  public Result(String identity,String external,IssuanceState issue,TransmissionState transmission,boolean authoritative,boolean test){this(identity,external,issue,transmission,authoritative,test,null,null,null,null,null);}
  public boolean legallyIssued(){return authoritative && !testEnvironment && issuance==IssuanceState.ISSUED;}
 }
 boolean enabled();
 Result request(Request request);
 Result lookup(String documentIdentity,String providerDocumentId);
}
