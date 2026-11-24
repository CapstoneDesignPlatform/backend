package com.capdi.backend.domain.payment.gateway.portone;
import com.capdi.backend.domain.payment.gateway.*;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.time.Duration;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
class PortOneFinancialGatewayTest {
 final ObjectMapper mapper=new ObjectMapper();
 final String ref="fin_00000000-0000-0000-0000-000000000001";
 record Call(String method,String path,Map<String,String> query,JsonNode body,String key){}
 final List<Call> calls=new ArrayList<>();final Deque<JsonNode> replies=new ArrayDeque<>();
 PortOneFinancialGateway gateway;
 @BeforeEach void setup(){
  var settings=new PortOneSettings(true,true,"SANDBOX","merchant-test","store-test","channel-test","SYNTHETIC_API_SECRET",null,Duration.ofSeconds(3));
  gateway=new PortOneFinancialGateway(settings,(m,p,q,b,k)->{calls.add(new Call(m,p,q,b,k));
   if(replies.isEmpty())throw new FinancialGatewayException(FinancialGatewayException.Failure.TIMEOUT);
   return new PortOneFinancialTransport.Reply(200,replies.removeFirst().toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));},mapper,EnumSet.allOf(FinancialGateway.Kind.class));
 }
 PortOneFinancialGateway gatewayWithoutMerchant(){
  var settings=new PortOneSettings(true,true,"SANDBOX",null,"store-test","channel-test","SYNTHETIC_API_SECRET",null,Duration.ofSeconds(3));
  return new PortOneFinancialGateway(settings,(m,p,q,b,k)->{calls.add(new Call(m,p,q,b,k));
   if(replies.isEmpty())throw new FinancialGatewayException(FinancialGatewayException.Failure.TIMEOUT);
   return new PortOneFinancialTransport.Reply(200,replies.removeFirst().toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));},mapper,EnumSet.allOf(FinancialGateway.Kind.class));
 }
 ObjectNode node(String json){try{return (ObjectNode)mapper.readTree(json);}catch(Exception e){throw new AssertionError();}}
 FinancialGateway.Command refund(){
  return new FinancialGateway.Command(ref,FinancialGateway.Kind.REFUND,node("{\"paymentId\":\"capdi_test\",\"method\":\"CARD\",\"gross\":1000,\"amount\":100,\"previousRefunded\":0,\"currency\":\"KRW\"}"));
 }
 ObjectNode payment(){
  return node("{\"id\":\"capdi_test\",\"merchantId\":\"merchant-test\",\"storeId\":\"store-test\",\"version\":\"V2\",\"status\":\"PAID\","
    +"\"channel\":{\"type\":\"TEST\",\"key\":\"channel-test\"},\"method\":{\"type\":\"CARD\"},\"currency\":\"KRW\","
    +"\"amount\":{\"total\":1000,\"cancelled\":0,\"discount\":0},\"cancellations\":[]}");
 }
 ObjectNode cancel(String status,int amount){return node("{\"status\":\""+status+"\",\"id\":\"cancel-test\",\"reason\":\""+ref+"\",\"totalAmount\":"+amount+"}");}
 @Test void financeUsesStoreAccountIdentityAndAllowsAbsentExpectedMerchantPin(){
  var unpinned=gatewayWithoutMerchant();
  assertThat(unpinned.identity().merchant()).isNull();
  assertThat(unpinned.identity().providerAccountId()).isEqualTo("provider:PORTONE_V2:store:store-test");
  replies.add(payment());replies.add(mapper.createObjectNode().set("cancellation",cancel("REQUESTED",100)));
  assertThat(unpinned.submit(refund()).state()).isEqualTo(FinancialGateway.State.PENDING);
 }
 @Test void financeStillRequiresWellFormedResponseMerchantWithoutConfiguredPin(){
  var malformed=payment();malformed.put("merchantId","");replies.add(malformed);
  assertThatThrownBy(()->gatewayWithoutMerchant().lookup(refund(),null)).isInstanceOf(FinancialGatewayException.class);
 }
 @Test void refundUsesCancellableAmountAndStableReferenceBut200RequestedIsPending(){
  replies.add(payment());replies.add(mapper.createObjectNode().set("cancellation",cancel("REQUESTED",100)));
  assertThat(gateway.submit(refund()).state()).isEqualTo(FinancialGateway.State.PENDING);
  assertThat(calls).hasSize(2);var request=calls.get(1);
  assertThat(request.method()).isEqualTo("POST");assertThat(request.path()).isEqualTo("/payments/capdi_test/cancel");
  assertThat(request.body().path("currentCancellableAmount").asLong()).isEqualTo(1000);
  assertThat(request.body().path("reason").asText()).isEqualTo(ref);assertThat(request.key()).isEqualTo(ref);
 }
 @Test void refundTimeoutRecoveryOnlyFetchesExistingCancellation(){
  ObjectNode p=payment();p.put("status","PARTIAL_CANCELLED");p.withObject("/amount").put("cancelled",100);p.withArray("/cancellations").add(cancel("SUCCEEDED",100));replies.add(p);
  assertThat(gateway.lookup(refund(),null).state()).isEqualTo(FinancialGateway.State.SUCCEEDED);
  assertThat(calls).hasSize(1);assertThat(calls.getFirst().method()).isEqualTo("GET");
 }
 @ParameterizedTest @ValueSource(ints={0,99,101,1001})
 void wrongCancellationAmountNeverConfirmsRefund(int amount){
  replies.add(payment());replies.add(mapper.createObjectNode().set("cancellation",cancel("SUCCEEDED",amount)));
  assertThatThrownBy(()->gateway.submit(refund())).isInstanceOf(FinancialGatewayException.class);
 }
 @Test void overRefundAndTimeoutNeverTriggerAutomaticCancelRetry(){
  ObjectNode p=payment();p.withObject("/amount").put("cancelled",1001);replies.add(p);
  assertThatThrownBy(()->gateway.submit(refund())).isInstanceOf(FinancialGatewayException.class);assertThat(calls).hasSize(1);
  calls.clear();assertThatThrownBy(()->gateway.submit(refund())).isInstanceOf(FinancialGatewayException.class);assertThat(calls).hasSize(1);
 }
 FinancialGateway.Command partner(){
  return new FinancialGateway.Command(ref,FinancialGateway.Kind.PARTNER,node("{\"partnerId\":\"capdi_partner_1\",\"contractId\":\"contract-test\","
   +"\"companyName\":\"Synthetic Company\",\"businessNumber\":\"1234567891\",\"representativeName\":\"Synthetic Person\","
   +"\"details\":{\"bank\":\"KOOKMIN\",\"number\":\"000000123456\",\"holder\":\"Synthetic Person\",\"contactName\":\"Synthetic Person\","
   +"\"contactEmail\":\"synthetic@example.test\",\"contactPhone\":\"01000000000\"}}"));
 }
 ObjectNode registered(){
  return node("{\"id\":\"capdi_partner_1\",\"status\":\"APPROVED\",\"isForTest\":true,\"defaultContractId\":\"contract-test\","
   +"\"type\":{\"type\":\"BUSINESS\",\"businessRegistrationNumber\":\"1234567891\",\"companyName\":\"Synthetic Company\",\"representativeName\":\"Synthetic Person\"},"
   +"\"account\":{\"bank\":\"KOOKMIN\",\"currency\":\"KRW\",\"number\":\"000000123456\",\"holder\":\"Synthetic Person\",\"status\":\"VERIFIED\"}}");
 }
 @Test void partnerRegistrationPreservesTestScopeAndDoesNotInferKycFromAccount(){
  replies.add(mapper.createObjectNode().set("partner",registered()));var e=gateway.submit(partner());
  assertThat(e.accountVerified()).isTrue();assertThat(e.kycVerified()).isFalse();
  var call=calls.getFirst();assertThat(call.query()).containsEntry("test","true");
  assertThat(call.body().path("type").path("business").path("businessRegistrationNumber").asText()).isEqualTo("1234567891");
 }
 @Test void livePartnerOrDifferentAccountRejected(){
  var p=registered();p.put("isForTest",false);replies.add(p);
  assertThatThrownBy(()->gateway.lookup(partner(),null)).isInstanceOf(FinancialGatewayException.class);
  p=registered();p.withObject("/account").put("number","999999999999");replies.add(p);
  assertThatThrownBy(()->gateway.lookup(partner(),null)).isInstanceOf(FinancialGatewayException.class);
 }
 FinancialGateway.Command invoice(){
  return new FinancialGateway.Command(ref,FinancialGateway.Kind.TAX_INVOICE,node("{\"amount\":1000,\"supply\":900,\"vat\":100,\"currency\":\"KRW\","
   +"\"taxation\":\"TAXABLE\",\"writeDate\":\"2026-01-01\",\"supplier\":{\"brn\":\"1234567891\",\"name\":\"Synthetic Supplier\","
   +"\"representativeName\":\"Synthetic Person\",\"email\":\"synthetic@example.test\"},"
   +"\"recipient\":{\"brn\":\"1234567891\",\"name\":\"Synthetic Recipient\",\"representativeName\":\"Synthetic Person\",\"email\":\"recipient@example.test\"}}"));
 }
 ObjectNode issued(String status){
  var c=invoice().payload();ObjectNode n=mapper.createObjectNode();
  n.put("id","tax-test");n.put("status",status);n.put("supplierDocumentKey",ref);
  n.set("supplier",c.path("supplier"));n.set("recipient",c.path("recipient"));
  n.put("totalAmount",1000);n.put("totalSupplyAmount",900);n.put("totalTaxAmount",100);
  n.put("taxationType","TAXABLE");n.put("writeDate","2026-01-01");return n;
 }
 @Test void taxIssuanceAndNtsTransmissionFailureRemainIndependent(){
  replies.add(mapper.createObjectNode().set("taxInvoice",issued("SENDING_FAILED")));
  var e=gateway.submit(invoice());assertThat(e.state()).isEqualTo(FinancialGateway.State.SUCCEEDED);assertThat(e.ntsState()).isEqualTo(FinancialGateway.NtsState.FAILED);
  assertThat(calls.getFirst().query()).containsEntry("test","true");
  assertThat(calls.getFirst().body().path("taxInvoice").path("supplierDocumentKey").asText()).isEqualTo(ref);
 }
 @Test void taxLookupUsesDocumentKeyAndNeverIssuesAnotherInvoice(){
  replies.add(issued("SENDING_COMPLETED"));var e=gateway.lookup(invoice(),null);
  assertThat(e.ntsState()).isEqualTo(FinancialGateway.NtsState.SUCCEEDED);
  assertThat(calls.getFirst().method()).isEqualTo("GET");
  assertThat(calls.getFirst().query()).containsEntry("taxInvoiceKeyType","SUPPLIER").containsEntry("test","true");
 }
 @Test void invoiceAmountAndUnknownStatusFailClosed(){
  var n=issued("ISSUED");n.put("totalAmount",999);replies.add(n);
  assertThatThrownBy(()->gateway.lookup(invoice(),"tax-test")).isInstanceOf(FinancialGatewayException.class);
  replies.add(issued("FUTURE_STATUS"));assertThat(gateway.lookup(invoice(),"tax-test").state()).isEqualTo(FinancialGateway.State.UNKNOWN);
 }
 @Test void correctionHasSeparateDocumentKeyAndExplicitOriginalReference(){
  var p=(ObjectNode)invoice().payload();p.put("originalExternalId","original-tax");p.put("modification","CORRECTION_OF_ENTRY_ERRORS");
  replies.add(mapper.createObjectNode().set("taxInvoice",issued("ISSUED")));
  gateway.submit(new FinancialGateway.Command(ref,FinancialGateway.Kind.TAX_INVOICE,p));
  assertThat(calls.getFirst().body().path("modification").path("taxInvoiceKey").asText()).isEqualTo("original-tax");
  assertThat(calls.getFirst().body().path("modification").path("taxInvoiceKeyType").asText()).isEqualTo("TAX_INVOICE_ID");
 }
 @Test void payoutCompletionRecordingApiIsNeverUsedToSendMoney(){
  assertThat(gateway.canSubmit(FinancialGateway.Kind.PAYOUT)).isFalse();
  assertThatThrownBy(()->gateway.submit(new FinancialGateway.Command(ref,FinancialGateway.Kind.PAYOUT,node("{}")))).isInstanceOf(FinancialGatewayException.class);
  assertThat(calls).isEmpty();
 }
 @Test void bankVerificationFailureIsNotNtsMatchOrKycApproval(){
  var p=registered();p.withObject("/account").put("status","VERIFY_FAILED");replies.add(p);
  var e=gateway.lookup(partner(),null);
  assertThat(e.bankState()).isEqualTo(FinancialGateway.BankState.REJECTED);
  assertThat(e.accountVerified()).isFalse();assertThat(e.kycVerified()).isFalse();
 }
 @Test void virtualAccountRefundWithoutApprovedAccountContractNeverSendsRequest(){
  var p=(ObjectNode)refund().payload();p.put("method","VIRTUAL_ACCOUNT");
  assertThatThrownBy(()->gateway.submit(new FinancialGateway.Command(ref,FinancialGateway.Kind.REFUND,p))).isInstanceOf(FinancialGatewayException.class);
  assertThat(calls).isEmpty();
 }
}
