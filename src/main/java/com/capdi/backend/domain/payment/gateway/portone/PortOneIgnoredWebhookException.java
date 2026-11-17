package com.capdi.backend.domain.payment.gateway.portone;
/** Only thrown after authenticating the exact body and checking its store. No payload retained. */
public final class PortOneIgnoredWebhookException extends RuntimeException {
    public PortOneIgnoredWebhookException(){super("Unsupported authenticated event");}
}
