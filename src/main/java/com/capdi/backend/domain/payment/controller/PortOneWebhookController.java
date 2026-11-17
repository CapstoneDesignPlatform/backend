package com.capdi.backend.domain.payment.controller;
import com.capdi.backend.domain.payment.service.PaymentWebhookService;
import com.capdi.backend.global.exception.*;
import com.capdi.backend.global.response.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.*;

/** Explicit opt-in receiver. A durable inbox ACK never asserts payment completion. */
@RestController @RequiredArgsConstructor
@ConditionalOnProperty(name="payment.portone.webhook-enabled",havingValue="true")
@RequestMapping("/webhooks/portone")
public class PortOneWebhookController {
    private final PaymentWebhookService service;
    @PostMapping(consumes="application/json")
    public ResponseEntity<ApiResponse<Void>> receive(HttpServletRequest request) {
        try {
            byte[] body=request.getInputStream().readNBytes(65537);
            if(body.length>65536)throw new CustomException(ErrorCode.PAYMENT_EVENT_INVALID);
            Map<String,List<String>> headers=new HashMap<>();
            for(String name:List.of("webhook-id","webhook-timestamp","webhook-signature"))
                headers.put(name,Collections.list(request.getHeaders(name)));
            service.receive(headers,body);
            return ResponseEntity.ok(ApiResponse.ok(null));
        } catch(java.io.IOException ignored) {throw new CustomException(ErrorCode.PAYMENT_EVENT_INVALID);}
    }
}
