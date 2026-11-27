package com.capdi.backend.domain.payment.gateway.portone;

import com.capdi.backend.domain.payment.gateway.PaymentGateway.*;
import com.capdi.backend.domain.payment.entity.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import static org.assertj.core.api.Assertions.*;

class PortOneV2GatewayTest {
    final String attempt="00000000-0000-0000-0000-000000000001";
    final String secret="whsec_"+Base64.getEncoder().encodeToString(new byte[32]);
    final Clock clock=Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"),ZoneOffset.UTC);
    final ObjectMapper mapper=new ObjectMapper();
    PortOneV2Gateway gateway;Reference reference;int calls;String json;
    PortOneSettings settings(boolean enabled,boolean approved,String env) {
        return new PortOneSettings(enabled,approved,env,"merchant-test","store-test","channel-test",
                "SYNTHETIC_API_SECRET",secret,Duration.ofSeconds(3));
    }
    PortOneSettings settingsWithoutMerchant() {
        return new PortOneSettings(true,true,"SANDBOX",null,"store-test","channel-test",
                "SYNTHETIC_API_SECRET",secret,Duration.ofSeconds(3));
    }
    @BeforeEach void setup(){
        gateway=new PortOneV2Gateway(settings(true,true,"SANDBOX"),(id,store,key,timeout)->{
            calls++;assertThat(id).isEqualTo("capdi_"+attempt);assertThat(store).isEqualTo("store-test");
            return new PortOneTransport.Reply(200,json.getBytes(StandardCharsets.UTF_8));
        },mapper,clock);
        reference=new Reference(gateway.identity(),"order-test",attempt,null,new BigDecimal("1000"),"KRW",PaymentMethod.CARD);
        json=body("PAID","CARD");
    }
    String body(String status,String method){
        return "{\"id\":\"capdi_"+attempt+"\",\"transactionId\":\"transaction-test\",\"merchantId\":\"merchant-test\","
                +"\"storeId\":\"store-test\",\"version\":\"V2\",\"status\":\""+status+"\",\"currency\":\"KRW\","
                +"\"amount\":{\"total\":1000},\"method\":"+methodJson(method)+","
                +"\"channel\":{\"type\":\"TEST\",\"key\":\"channel-test\"}}";
    }
    String methodJson(String method) {
        return switch(method) {
            case "CARD" -> "{\"type\":\"PaymentMethodCard\"}";
            case "TRANSFER" -> "{\"type\":\"PaymentMethodTransfer\"}";
            case "VIRTUAL_ACCOUNT" -> "{\"type\":\"PaymentMethodVirtualAccount\"}";
            default -> "{\"type\":\""+method+"\"}";
        };
    }
    @Test void prepareIsLocalAndDoesNotInventProviderTransaction(){
        var result=gateway.prepare(reference);
        assertThat(result.state()).isEqualTo(State.PREPARED);assertThat(result.transactionId()).isNull();assertThat(calls).isZero();
    }
    @Test void checkoutUsesPortOneTossV2CurrencyCode(){
        var checkout=gateway.checkout(reference);
        assertThat(checkout.getCurrency()).isEqualTo("KRW");
        assertThat(checkout.getTotalAmount()).isEqualByComparingTo("1000");
        assertThat(checkout.getPaymentId()).isEqualTo("capdi_"+attempt);
        assertThat(calls).isZero();
    }
    @Test void confirmationOnlyFetchesServerSnapshot(){
        assertThat(gateway.confirm(reference,"capdi_"+attempt).state()).isEqualTo(State.PAID);assertThat(calls).isEqualTo(1);
    }
    @Test void tosspayCardInsideEasyPayMatchesRequestedCardMethod() {
        json=json.replace("\"method\":{\"type\":\"PaymentMethodCard\"}",
                "\"method\":{\"type\":\"PaymentMethodEasyPay\",\"provider\":\"TOSSPAY\","
                        +"\"easyPayMethod\":{\"type\":\"PaymentMethodCard\"}}");
        assertThat(gateway.fetch(reference).state()).isEqualTo(State.PAID);
    }
    @Test void easyPayTransferDoesNotMatchRequestedCardMethod() {
        json=json.replace("\"method\":{\"type\":\"PaymentMethodCard\"}",
                "\"method\":{\"type\":\"PaymentMethodEasyPay\",\"provider\":\"TOSSPAY\","
                        +"\"easyPayMethod\":{\"type\":\"PaymentMethodTransfer\"}}");
        assertThatThrownBy(()->gateway.fetch(reference)).isInstanceOfSatisfying(GatewayException.class,
                e->assertThat(e.failure()).isEqualTo(PaymentProcessingFailure.RESPONSE_MISMATCH));
    }
    @Test void missingConfiguredMerchantStillRequiresAndStoresValidV2ResponseMerchant() {
        var unpinned=new PortOneV2Gateway(settingsWithoutMerchant(),(id,store,key,timeout)->{
            calls++;return new PortOneTransport.Reply(200,json.getBytes(StandardCharsets.UTF_8));
        },mapper,clock);
        var unpinnedReference=new Reference(unpinned.identity(),"order-test",attempt,null,
                new BigDecimal("1000"),"KRW",PaymentMethod.CARD);
        var result=unpinned.fetch(unpinnedReference);
        assertThat(result.identity().merchant()).isNull();
        assertThat(result.providerMerchantId()).isEqualTo("merchant-test");
        assertThat(result.identity().providerAccountId()).isEqualTo("provider:PORTONE_V2:store:store-test");
        assertThat(calls).isEqualTo(1);
    }
    @ParameterizedTest @ValueSource(strings={"", "bad merchant"})
    void absentOrMalformedResponseMerchantIsRejectedWhenNoExpectedMerchantIsConfigured(String value) {
        var unpinned=new PortOneV2Gateway(settingsWithoutMerchant(),(id,store,key,timeout)->
                new PortOneTransport.Reply(200,json.replace("merchant-test",value).getBytes(StandardCharsets.UTF_8)),mapper,clock);
        var unpinnedReference=new Reference(unpinned.identity(),"order-test",attempt,null,
                new BigDecimal("1000"),"KRW",PaymentMethod.CARD);
        assertThatThrownBy(()->unpinned.fetch(unpinnedReference)).isInstanceOf(GatewayException.class);
    }
    @Test void missingResponseMerchantIsRejectedEvenWithoutAnExpectedPin() {
        var unpinned=new PortOneV2Gateway(settingsWithoutMerchant(),(id,store,key,timeout)->
                new PortOneTransport.Reply(200,json.replace("\"merchantId\":\"merchant-test\",","").getBytes(StandardCharsets.UTF_8)),mapper,clock);
        var unpinnedReference=new Reference(unpinned.identity(),"order-test",attempt,null,
                new BigDecimal("1000"),"KRW",PaymentMethod.CARD);
        assertThatThrownBy(()->unpinned.fetch(unpinnedReference)).isInstanceOf(GatewayException.class);
    }
    @Test void storeBasedIdentityAcceptsLegacyAttemptOnlyForItsExactMerchantAndStore() {
        var legacy=new Identity("PORTONE_V2","merchant-test",PaymentEnvironment.SANDBOX,
                "provider:PORTONE_V2:legacy-merchant:merchant-test");
        assertThat(gateway.isIdentityCompatible(legacy)).isTrue();
        var wrongMerchant=new Identity("PORTONE_V2","other-merchant",PaymentEnvironment.SANDBOX,
                "provider:PORTONE_V2:legacy-merchant:other-merchant");
        assertThat(gateway.isIdentityCompatible(wrongMerchant)).isFalse();
        var wrongStore=new Identity("PORTONE_V2",null,PaymentEnvironment.SANDBOX,
                "provider:PORTONE_V2:store:other-store");
        assertThat(gateway.isIdentityCompatible(wrongStore)).isFalse();
    }
    @Test void providerNotFoundIsUnresolvedAndCanBeRetriedWithoutTrustingWebhook() {
        var missing=new PortOneV2Gateway(settings(true,true,"SANDBOX"),
                (id,store,key,timeout)->new PortOneTransport.Reply(404,new byte[0]),mapper,clock);
        assertThatThrownBy(()->missing.fetch(reference)).isInstanceOfSatisfying(GatewayException.class,
                e->assertThat(e.failure()).isEqualTo(PaymentProcessingFailure.PROVIDER_ERROR));
    }
    @Test void browserReferenceCannotSelectAnotherPayment(){
        assertThatThrownBy(()->gateway.confirm(reference,"other")).isInstanceOf(GatewayException.class);assertThat(calls).isZero();
    }
    @ParameterizedTest @ValueSource(strings={"merchant-test","store-test","channel-test","V2","KRW","1000","PaymentMethodCard","TEST"})
    void everyBindingMustMatch(String change){
        json=json.replace(change,"WRONG");assertThatThrownBy(()->gateway.fetch(reference)).isInstanceOf(GatewayException.class);
        assertThat(calls).isEqualTo(1);
    }
    @ParameterizedTest @ValueSource(strings={"PARTIAL_CANCELLED","UNRECOGNIZED","CANCEL_PENDING"})
    void unsupportedStateNeverBecomesPaid(String status){
        json=body(status,"CARD");assertThat(gateway.fetch(reference).authoritative()).isFalse();
    }
    @Test void virtualAccountIssueIsNotPaid(){
        reference=new Reference(gateway.identity(),"order-test",attempt,null,new BigDecimal("1000"),"KRW",PaymentMethod.VIRTUAL_ACCOUNT);
        json=body("VIRTUAL_ACCOUNT_ISSUED","VIRTUAL_ACCOUNT");
        assertThat(gateway.fetch(reference).state()).isEqualTo(State.WAITING_DEPOSIT);
    }
    @Test void virtualAccountStateCannotBeUsedForCard(){
        json=body("VIRTUAL_ACCOUNT_ISSUED","CARD");assertThatThrownBy(()->gateway.fetch(reference)).isInstanceOf(GatewayException.class);
    }
    @Test void duplicateJsonFieldsRejected(){
        json=json.replace("\"status\":\"PAID\"","\"status\":\"FAILED\",\"status\":\"PAID\"");
        assertThatThrownBy(()->gateway.fetch(reference)).isInstanceOf(GatewayException.class);
    }
    @Test void productionAndUnapprovedSandboxRejected(){
        assertThatThrownBy(()->settings(true,false,"SANDBOX")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->settings(true,true,"PRODUCTION")).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void disabledAdapterDoesNotInvokeTransport(){
        var disabled=new PortOneV2Gateway(settings(false,false,"DISABLED"),(a,b,c,d)->{throw new AssertionError();},mapper,clock);
        assertThatThrownBy(()->disabled.fetch(reference)).isInstanceOf(GatewayException.class);
        assertThat(disabled.supports(Capability.CANCEL)).isFalse();
        assertThat(settings(true,true,"SANDBOX").toString()).doesNotContain("SYNTHETIC_API_SECRET",secret);
    }
    @Test void networkFailureHasNoRetryAndNoSecretMessage(){
        var failing=new PortOneV2Gateway(settings(true,true,"SANDBOX"),(a,b,c,d)->{
            calls++;throw new GatewayException(PaymentProcessingFailure.TIMEOUT);
        },mapper,clock);
        assertThatThrownBy(()->failing.fetch(reference)).isInstanceOfSatisfying(GatewayException.class,
                e->{assertThat(e.failure()).isEqualTo(PaymentProcessingFailure.TIMEOUT);
                    assertThat(e.getMessage()).doesNotContain("SYNTHETIC_API_SECRET");});
        assertThat(calls).isEqualTo(1);
    }
    byte[] event(){
        return ("{\"type\":\"Transaction.Paid\",\"data\":{\"storeId\":\"store-test\",\"paymentId\":\"capdi_"+attempt+"\"}}")
                .getBytes(StandardCharsets.UTF_8);
    }
    Map<String,List<String>> signed(byte[] bytes,long epoch) throws Exception {
        String ts=Long.toString(epoch);
        Mac mac=Mac.getInstance("HmacSHA256");mac.init(new SecretKeySpec(new byte[32],"HmacSHA256"));
        mac.update(("event-test."+ts+".").getBytes(StandardCharsets.UTF_8));
        return Map.of("webhook-id",List.of("event-test"),"webhook-timestamp",List.of(ts),
                "webhook-signature",List.of("v1,"+Base64.getEncoder().encodeToString(mac.doFinal(bytes))));
    }
    @Test void verifiedWebhookResolvesAttemptButDoesNotTrustPaidBody() throws Exception {
        var e=gateway.verifyWebhook(signed(event(),clock.instant().getEpochSecond()),event());
        assertThat(e.attemptKey()).isEqualTo(attempt);assertThat(e.orderId()).isNull();assertThat(calls).isZero();
    }
    @ParameterizedTest @ValueSource(longs={-301,301,Long.MAX_VALUE})
    void oldFutureOrOverflowTimestampRejected(long delta) throws Exception {
        long ts=delta==Long.MAX_VALUE?Long.MAX_VALUE:clock.instant().getEpochSecond()+delta;
        var headers=signed(event(),ts);
        assertThatThrownBy(()->gateway.verifyWebhook(headers,event())).isInstanceOf(GatewayException.class);
    }
    @Test void tamperingAndDuplicateHeadersRejected() throws Exception {
        var headers=signed(event(),clock.instant().getEpochSecond());
        assertThatThrownBy(()->gateway.verifyWebhook(headers,"changed".getBytes())).isInstanceOf(GatewayException.class);
        var duplicate=new HashMap<>(headers);duplicate.put("Webhook-Id",List.of("other"));
        assertThatThrownBy(()->gateway.verifyWebhook(duplicate,event())).isInstanceOf(GatewayException.class);
    }
    @Test void oversizedBodyRejectedWithoutParsing() throws Exception {
        var bytes=new byte[65537];var headers=signed(bytes,clock.instant().getEpochSecond());
        assertThatThrownBy(()->gateway.verifyWebhook(headers,bytes)).isInstanceOf(GatewayException.class);
    }
    @Test void authenticatedUnknownEventIsIgnoredButUnsignedBodyIsRejected() throws Exception {
        byte[] unknown="{\"type\":\"Future.Event\",\"data\":{\"storeId\":\"store-test\"}}".getBytes(StandardCharsets.UTF_8);
        var headers=signed(unknown,clock.instant().getEpochSecond());
        assertThatThrownBy(()->gateway.verifyWebhook(headers,unknown)).isInstanceOf(PortOneIgnoredWebhookException.class);
        assertThatThrownBy(()->gateway.verifyWebhook(headers,event())).isInstanceOf(GatewayException.class);
    }
}
