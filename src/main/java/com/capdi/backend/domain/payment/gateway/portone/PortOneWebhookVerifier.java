package com.capdi.backend.domain.payment.gateway.portone;

import com.capdi.backend.domain.payment.gateway.PaymentGateway.*;
import com.capdi.backend.domain.payment.entity.PaymentProcessingFailure;
import com.fasterxml.jackson.databind.ObjectMapper;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.util.*;

/** Standard Webhooks v1 symmetric verification over the exact raw bytes; replay bounded to 5 minutes. */
final class PortOneWebhookVerifier {
    final PortOneSettings settings;final Clock clock;final ObjectMapper mapper;
    PortOneWebhookVerifier(PortOneSettings s,Clock c,ObjectMapper m){settings=s;clock=c;mapper=m;}
    boolean configured(){return settings.webhookSecret()!=null && !settings.webhookSecret().isBlank();}
    VerifiedEvent verify(Identity identity,Map<String,List<String>> headers,byte[] body) {
        try {
            if(body==null || body.length==0 || body.length>65536)throw new IllegalArgumentException();
            String id=header(headers,"webhook-id"),time=header(headers,"webhook-timestamp"),signatures=header(headers,"webhook-signature");
            if(!id.matches("[A-Za-z0-9_-]{1,200}") || !time.matches("[0-9]{1,12}") || signatures.length()>2048)
                throw new IllegalArgumentException();
            long ts=Long.parseLong(time), now=clock.instant().getEpochSecond();
            if(ts<now-300 || ts>now+300)throw new IllegalArgumentException();
            String secret=settings.webhookSecret();
            if(secret.startsWith("whsec_"))secret=secret.substring(6);
            byte[] key=Base64.getDecoder().decode(secret);
            if(key.length<24 || key.length>64)throw new IllegalArgumentException();
            Mac mac=Mac.getInstance("HmacSHA256");mac.init(new SecretKeySpec(key,"HmacSHA256"));
            mac.update((id+"."+time+".").getBytes(StandardCharsets.UTF_8));
            byte[] expected=mac.doFinal(body);boolean valid=false;
            for(String signature:signatures.split(" ")) {
                if(signature.startsWith("v1,")) {
                    try {valid|=MessageDigest.isEqual(expected,Base64.getDecoder().decode(signature.substring(3)));}
                    catch(IllegalArgumentException ignored){ }
                }
            }
            Arrays.fill(key,(byte)0);
            if(!valid)throw new IllegalArgumentException();
            var n=mapper.readTree(body);var data=n.path("data");
            if(!settings.storeId().equals(data.path("storeId").asText()))throw new IllegalArgumentException();
            String type=n.path("type").asText();
            if(!Set.of("Transaction.Ready","Transaction.Paid","Transaction.VirtualAccountIssued","Transaction.PartialCancelled",
                    "Transaction.Cancelled","Transaction.Failed","Transaction.PayPending","Transaction.CancelPending").contains(type))
                throw new PortOneIgnoredWebhookException();
            String paymentId=data.path("paymentId").asText();
            if(!settings.storeId().equals(data.path("storeId").asText()) || !paymentId.matches("capdi_[0-9a-f-]{36}"))
                throw new IllegalArgumentException();
            // The core resolves the order from the attempt; webhook body does not prove amount or PAID.
            return new VerifiedEvent(identity,id,paymentId.substring(6),null);
        } catch(PortOneIgnoredWebhookException ignored){throw ignored;}
        catch(Exception e){throw new GatewayException(PaymentProcessingFailure.RESPONSE_MISMATCH);}
    }
    private String header(Map<String,List<String>> h,String name) {
        var entries=h.entrySet().stream().filter(e->name.equalsIgnoreCase(e.getKey())).toList();
        if(entries.size()!=1 || entries.getFirst().getValue()==null || entries.getFirst().getValue().size()!=1)
            throw new IllegalArgumentException();
        return entries.getFirst().getValue().getFirst();
    }
}
