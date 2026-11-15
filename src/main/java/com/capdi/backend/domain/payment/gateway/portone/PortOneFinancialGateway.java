package com.capdi.backend.domain.payment.gateway.portone;
import com.capdi.backend.domain.payment.gateway.*;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.core.JsonParser;
import java.math.BigDecimal;
import java.util.*;
import static com.capdi.backend.domain.payment.gateway.FinancialGatewayException.Failure.*;

/** V2 Sandbox-only adapter. No public payout-transfer contract is assumed. */
public final class PortOneFinancialGateway implements FinancialGateway {
 private final PortOneSettings settings;private final PortOneFinancialTransport transport;private final Set<Kind> writes;private final ObjectMapper mapper;
 public PortOneFinancialGateway(PortOneSettings settings,PortOneFinancialTransport transport,ObjectMapper mapper,Set<Kind> writes) {
  this.settings=settings;this.transport=transport;this.writes=Set.copyOf(writes);
  this.mapper=mapper.copy().enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION).enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
 }
 public Identity identity(){return settings.enabled()?new Identity("PORTONE_V2",settings.merchantId(),settings.storeId(),"SANDBOX"):new Identity("DISABLED","DISABLED","DISABLED","DISABLED");}
 public boolean canSubmit(Kind kind){return settings.enabled() && kind!=Kind.PAYOUT && writes.contains(kind);}
 public boolean canLookup(Kind kind){return settings.enabled();}
 public Evidence submit(Command c){
  if(!canSubmit(c.kind()))throw new FinancialGatewayException(c.kind()==Kind.PAYOUT?UNSUPPORTED:DISABLED);
  return guarded(()->switch(c.kind()){
   case REFUND->refund(c,true);
   case PARTNER->partner(c,true);
   case TAX_INVOICE->invoice(c,true,null);
   case PAYOUT->throw new FinancialGatewayException(UNSUPPORTED);
  });
 }
 public Evidence lookup(Command c,String externalId) {
  if(!canLookup(c.kind()))throw new FinancialGatewayException(DISABLED);
  return guarded(()->switch(c.kind()){
   case REFUND->refund(c,false);
   case PARTNER->partner(c,false);
   case TAX_INVOICE->invoice(c,false,externalId);
   case PAYOUT->payout(c,externalId);
  });
 }
 private Evidence guarded(java.util.function.Supplier<Evidence> call){
  try{return call.get();}catch(FinancialGatewayException e){throw e;}catch(Exception e){throw new FinancialGatewayException(RESPONSE_MISMATCH);}
 }
 private JsonNode send(String method,String path,Map<String,String> query,JsonNode body,Command command) {
  var r=transport.send(method,path,query,body,command.reference());
  if(r.status()==404 && "GET".equals(method))return null;
  if(r.status()!=200 && r.status()!=201 && r.status()!=202)throw new FinancialGatewayException(PROVIDER_ERROR);
  if(r.body()==null || r.body().length>65536)throw new FinancialGatewayException(RESPONSE_MISMATCH);
  try{return mapper.readTree(r.body());}catch(Exception e){throw new FinancialGatewayException(RESPONSE_MISMATCH);}
 }
 private Evidence refund(Command c,boolean write) {
  var p=c.payload();if(!Set.of("CARD","TRANSFER").contains(text(p,"method")))throw new FinancialGatewayException(UNSUPPORTED);
  String paymentId=id(text(p,"paymentId"));
  JsonNode payment=send("GET","/payments/"+paymentId,Map.of("storeId",settings.storeId()),null,c);
  if(payment==null)return unknown(c,null);
  require(text(payment,"id").equals(paymentId) && text(payment,"storeId").equals(settings.storeId())
    && text(payment,"merchantId").equals(settings.merchantId()) && "V2".equals(text(payment,"version"))
    && "TEST".equals(text(payment.path("channel"),"type")) && settings.channelKey().equals(text(payment.path("channel"),"key"))
    && text(payment.path("method"),"type").equals(text(p,"method")) && "KRW".equals(text(payment,"currency"))
    && integer(payment.path("amount").path("total")).compareTo(p.path("gross").decimalValue())==0);
  BigDecimal cancelled=integer(payment.path("amount").path("cancelled"));
  require(cancelled.signum()>=0 && cancelled.compareTo(p.path("gross").decimalValue())<=0);
  String paymentStatus=text(payment,"status");
  require(cancelled.signum()==0?"PAID".equals(paymentStatus):
    cancelled.compareTo(p.path("gross").decimalValue())==0?"CANCELLED".equals(paymentStatus):"PARTIAL_CANCELLED".equals(paymentStatus));
  if(payment.path("amount").path("discount").asLong(0)!=0)throw new FinancialGatewayException(UNSUPPORTED);
  JsonNode cancellation=null;
  if(write) {
   require(cancelled.compareTo(p.path("previousRefunded").decimalValue())==0);
   ObjectNode body=mapper.createObjectNode();body.put("storeId",settings.storeId());body.put("amount",whole(p,"amount"));
   body.put("currentCancellableAmount",p.path("gross").decimalValue().subtract(cancelled).longValueExact());
   body.put("reason",c.reference());body.put("requester","ADMIN");
   var response=send("POST","/payments/"+paymentId+"/cancel",Map.of(),body,c);
   if(response==null)return unknown(c,null);cancellation=response.path("cancellation");
  } else {
   for(var item:payment.path("cancellations"))if(c.reference().equals(item.path("reason").asText())){
    require(cancellation==null);cancellation=item;
   }
   if(cancellation==null)return unknown(c,null);
  }
  require(text(cancellation,"reason").equals(c.reference()) && integer(cancellation.path("totalAmount")).compareTo(p.path("amount").decimalValue())==0);
  String external=id(text(cancellation,"id"));
  State state=switch(text(cancellation,"status")){case "SUCCEEDED"->State.SUCCEEDED;case "FAILED"->State.FAILED;case "REQUESTED"->State.PENDING;default->State.UNKNOWN;};
  if(!write && state==State.SUCCEEDED)require(cancelled.compareTo(p.path("previousRefunded").decimalValue().add(p.path("amount").decimalValue()))==0);
  return money(c,external,state,NtsState.NOT_SENT);
 }
 private Evidence partner(Command c,boolean write) {
  var p=c.payload();String partner=id(text(p,"partnerId"));JsonNode response;
  if(write) {
   var d=p.path("details");ObjectNode body=mapper.createObjectNode();
   body.put("id",partner);body.put("name",text(p,"companyName"));body.put("defaultContractId",text(p,"contractId"));body.putArray("tags");
   body.putObject("contact").put("name",text(d,"contactName")).put("phoneNumber",text(d,"contactPhone")).put("email",text(d,"contactEmail"));
   body.putObject("account").put("bank",text(d,"bank")).put("currency","KRW").put("number",text(d,"number")).put("holder",text(d,"holder"));
   body.putObject("type").putObject("business").put("companyName",text(p,"companyName"))
     .put("businessRegistrationNumber",text(p,"businessNumber")).put("representativeName",text(p,"representativeName"));
   boolean update=p.hasNonNull("existingPartnerId");
   if(update){require(partner.equals(text(p,"existingPartnerId")));body.remove("id");}
   response=send(update?"PATCH":"POST",update?"/platform/partners/"+partner:"/platform/partners",Map.of("test","true"),body,c);
   if(response==null)return unknown(c,null);response=response.path("partner");
  } else response=send("GET","/platform/partners/"+partner,Map.of("test","true"),null,c);
  if(response==null)return unknown(c,null);
  require(partner.equals(text(response,"id")) && response.path("isForTest").isBoolean() && response.path("isForTest").booleanValue()
    && text(p,"contractId").equals(text(response,"defaultContractId")) && "BUSINESS".equals(text(response.path("type"),"type"))
    && text(p,"businessNumber").equals(text(response.path("type"),"businessRegistrationNumber"))
    && text(p,"companyName").equals(text(response.path("type"),"companyName"))
    && text(p,"representativeName").equals(text(response.path("type"),"representativeName")));
  JsonNode a=response.path("account"),d=p.path("details");
  require("KRW".equals(text(a,"currency")) && text(a,"number").equals(text(d,"number"))
    && text(a,"holder").equals(text(d,"holder")) && text(a,"bank").equals(text(d,"bank")));
  State state=switch(text(response,"status")){case "APPROVED"->State.SUCCEEDED;case "PENDING"->State.PENDING;case "REJECTED"->State.FAILED;default->State.UNKNOWN;};
  BankState bank=switch(text(a,"status")){case "VERIFIED"->BankState.VERIFIED;case "VERIFY_FAILED"->BankState.REJECTED;case "NOT_VERIFIED"->BankState.UNVERIFIED;default->BankState.UNKNOWN;};
  return new Evidence(c.reference(),partner,state,null,"KRW",bank==BankState.VERIFIED,false,NtsState.NOT_SENT,bank);
 }
 private Evidence invoice(Command c,boolean write,String external) {
  var p=c.payload();JsonNode response;
  if(write) {
   ObjectNode body=mapper.createObjectNode(),invoice=body.putObject("taxInvoice");
   invoice.put("supplierDocumentKey",c.reference());invoice.put("taxationType",text(p,"taxation"));
   invoice.put("writeDate",text(p,"writeDate"));invoice.put("purposeType","RECEIPT");
   invoice.put("totalSupplyAmount",whole(p,"supply"));invoice.put("totalTaxAmount",whole(p,"vat"));invoice.put("totalAmount",whole(p,"amount"));
   invoice.set("supplier",company(p.path("supplier")));invoice.set("recipient",company(p.path("recipient")));
   if(p.hasNonNull("originalExternalId"))body.putObject("modification").put("type",text(p,"modification"))
      .put("taxInvoiceKey",text(p,"originalExternalId")).put("taxInvoiceKeyType","TAX_INVOICE_ID");
   response=send("POST","/b2b/tax-invoices/issue-immediately",Map.of("test","true"),body,c);
   if(response==null)return unknown(c,external);response=response.path("taxInvoice");
  } else response=send("GET","/b2b/tax-invoices/"+id(external==null?c.reference():external),
    Map.of("test","true","taxInvoiceKeyType",external==null?"SUPPLIER":"TAX_INVOICE_ID","brn",text(p.path("supplier"),"brn")),null,c);
  if(response==null)return unknown(c,external);
  require(c.reference().equals(text(response,"supplierDocumentKey"))
    && text(response.path("supplier"),"brn").equals(text(p.path("supplier"),"brn"))
    && text(response.path("recipient"),"brn").equals(text(p.path("recipient"),"brn"))
    && integer(response.path("totalAmount")).compareTo(p.path("amount").decimalValue())==0
    && integer(response.path("totalSupplyAmount")).compareTo(p.path("supply").decimalValue())==0
    && integer(response.path("totalTaxAmount")).compareTo(p.path("vat").decimalValue())==0
    && text(response,"taxationType").equals(text(p,"taxation")) && text(response,"writeDate").equals(text(p,"writeDate")));
  String status=text(response,"status");String id=id(text(response,"id"));if(external!=null)require(external.equals(id));
  State state=Set.of("ISSUED","BEFORE_SENDING","WAITING_SENDING","SENDING","SENDING_COMPLETED","SENDING_FAILED","SENDING_PENDING").contains(status)
    ?State.SUCCEEDED:Set.of("ISSUE_FAILED","REQUEST_FAILED","ISSUANCE_CANCELLED","REQUEST_REFUSED").contains(status)?State.FAILED:Set.of("DRAFTED","DRAFT_PENDING","REQUESTED","REQUEST_PENDING","ISSUE_PENDING","CANCEL_PENDING").contains(status)?State.PENDING:State.UNKNOWN;
  NtsState nts=switch(status){case "SENDING_COMPLETED"->NtsState.SUCCEEDED;case "SENDING_FAILED"->NtsState.FAILED;
    case "WAITING_SENDING","SENDING","SENDING_PENDING"->NtsState.PENDING;default->NtsState.NOT_SENT;};
  return money(c,id,state,nts);
 }
 private Evidence payout(Command c,String external) {
  if(external==null)throw new FinancialGatewayException(UNSUPPORTED);
  ObjectNode filter=mapper.createObjectNode();filter.putObject("criteria").put("payoutId",id(external));
  ObjectNode query=mapper.createObjectNode();query.put("isForTest",true);query.set("filter",filter);query.putObject("page").put("number",0).put("size",2);
  var result=send("GET","/platform/payouts",Map.of("test","true","requestBody",query.toString()),null,c);
  if(result==null || !result.path("items").isArray() || result.path("items").size()!=1)return unknown(c,external);
  var n=result.path("items").get(0);var p=c.payload();var a=p.path("account");
  require(external.equals(text(n,"id")) && text(n.path("partner"),"id").equals(text(p,"partnerId"))
    && n.path("partner").path("isForTest").isBoolean() && n.path("partner").path("isForTest").booleanValue()
    && "KRW".equals(text(n,"currency")) && integer(n.path("amount")).compareTo(p.path("amount").decimalValue())==0
    && text(n.path("account"),"number").equals(text(a,"number")) && text(n.path("account"),"bank").equals(text(a,"bank")));
  if(!"AGENCY".equals(text(n,"method")))throw new FinancialGatewayException(UNSUPPORTED);
  // A payout record is not yet a verified PG transfer receipt. Never infer real transfer completion.
  State state=switch(text(n,"status")){
   case "CONFIRMED","PREPARED","PROCESSING","SCHEDULED"->State.PENDING;default->State.UNKNOWN;};
  return money(c,external,state,NtsState.NOT_SENT);
 }
 private ObjectNode company(JsonNode p){
  ObjectNode n=mapper.createObjectNode();n.put("brn",text(p,"brn"));n.put("name",text(p,"name"));n.put("representativeName",text(p,"representativeName"));
  n.putObject("contact").put("email",text(p,"email"));return n;
 }
 private Evidence unknown(Command c,String external){return new Evidence(c.reference(),external,State.UNKNOWN,c.payload().has("amount")?c.payload().path("amount").decimalValue():null,"KRW",false,false,NtsState.UNKNOWN);}
 private Evidence money(Command c,String external,State state,NtsState nts){return new Evidence(c.reference(),external,state,c.payload().path("amount").decimalValue(),"KRW",false,false,nts);}
 private static void require(boolean b){if(!b)throw new FinancialGatewayException(RESPONSE_MISMATCH);}
 private static String text(JsonNode n,String key){var v=n.path(key);if(!v.isTextual() || v.textValue().isBlank())throw new FinancialGatewayException(RESPONSE_MISMATCH);return v.textValue();}
 private static String id(String value){require(value!=null && value.matches("[A-Za-z0-9_.:-]{1,200}"));return value;}
 private static long whole(JsonNode p,String name){return p.path(name).decimalValue().longValueExact();}
 private static BigDecimal integer(JsonNode n){require(n.isIntegralNumber());return n.decimalValue();}
}
