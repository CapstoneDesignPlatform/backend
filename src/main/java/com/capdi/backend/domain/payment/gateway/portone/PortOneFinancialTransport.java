package com.capdi.backend.domain.payment.gateway.portone;
import com.capdi.backend.domain.payment.gateway.FinancialGatewayException;
import static com.capdi.backend.domain.payment.gateway.FinancialGatewayException.Failure.*;
import com.fasterxml.jackson.databind.JsonNode;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
public interface PortOneFinancialTransport {
 record Reply(int status,byte[] body){@Override public String toString(){return "FinancialReply[REDACTED]";}}
 Reply send(String method,String path,Map<String,String> query,JsonNode body,String operationKey);
 final class Jdk implements PortOneFinancialTransport {
  private final PortOneSettings settings; private final PortOneProxySelector proxySelector; private final HttpClient client;
  public Jdk(PortOneSettings settings){this(settings,null);}
  public Jdk(PortOneSettings settings,PortOneProxySelector proxySelector){this.settings=settings;this.proxySelector=proxySelector;
   var builder=HttpClient.newBuilder().connectTimeout(java.time.Duration.ofSeconds(5)).followRedirects(HttpClient.Redirect.NEVER);
   if(proxySelector!=null)builder.proxy(proxySelector);this.client=builder.build();}
  public Reply send(String method,String path,Map<String,String> query,JsonNode body,String operationKey){
   if(!settings.enabled())throw new FinancialGatewayException(DISABLED);
   if(proxySelector==null)throw new FinancialGatewayException(NETWORK);
   if(!Set.of("GET","POST","PATCH").contains(method) || !path.matches("/[A-Za-z0-9_./-]{1,250}") || path.contains(".."))throw new FinancialGatewayException(RESPONSE_MISMATCH);
   String q=query.entrySet().stream().map(e->URLEncoder.encode(e.getKey(),StandardCharsets.UTF_8)+"="+URLEncoder.encode(e.getValue(),StandardCharsets.UTF_8))
     .collect(java.util.stream.Collectors.joining("&"));
   var builder=HttpRequest.newBuilder(URI.create("https://api.portone.io"+path+(q.isEmpty()?"":"?"+q)))
     .timeout(settings.timeout()).header("Authorization","PortOne "+settings.apiSecret()).header("Accept","application/json");
   if(!"GET".equals(method)){
    if(operationKey==null || !operationKey.matches("[A-Za-z0-9_-]{16,100}"))throw new FinancialGatewayException(RESPONSE_MISMATCH);
    builder.header("Idempotency-Key","\""+operationKey+"\"").header("Content-Type","application/json");
   }
   byte[] bytes=body==null?new byte[0]:body.toString().getBytes(StandardCharsets.UTF_8);
   if(bytes.length>65536)throw new FinancialGatewayException(RESPONSE_MISMATCH);
   builder.method(method,"GET".equals(method)?HttpRequest.BodyPublishers.noBody():HttpRequest.BodyPublishers.ofByteArray(bytes));
   try {var response=client.send(builder.build(),info->new PortOneTransport.Jdk.LimitedBody());return new Reply(response.statusCode(),response.body());}
   catch(HttpTimeoutException e){throw new FinancialGatewayException(TIMEOUT);}
   catch(InterruptedException e){Thread.currentThread().interrupt();throw new FinancialGatewayException(NETWORK);}
   catch(Exception e){throw new FinancialGatewayException(NETWORK);}
  }
 }
}
