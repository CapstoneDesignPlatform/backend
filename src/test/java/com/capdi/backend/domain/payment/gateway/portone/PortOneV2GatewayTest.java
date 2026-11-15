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
                +"\"amount\":{\"total\":1000},\"method\":{\"type\":\""+method+"\"},"
                +"\"channel\":{\"type\":\"TEST\",\"key\":\"channel-test\"}}";
    }
    @Test void prepareIsLocalAndDoesNotInventProviderTransaction(){
        var result=gateway.prepare(reference);
        assertThat(result.state()).isEqualTo(State.PREPARED);assertThat(result.transactionId()).isNull();assertThat(calls).isZero();
    }
    @Test void confirmationOnlyFetchesServerSnapshot(){
        assertThat(gateway.confirm(reference,"capdi_"+attempt).state()).isEqualTo(State.PAID);assertThat(calls).isEqualTo(1);
    }
    @Test void browserReferenceCannotSelectAnotherPayment(){
        assertThatThrownBy(()->gateway.confirm(reference,"other")).isInstanceOf(GatewayException.class);assertThat(calls).isZero();
    }
    @ParameterizedTest @ValueSource(strings={"merchant-test","store-test","channel-test","V2","KRW","1000","CARD","TEST"})
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
