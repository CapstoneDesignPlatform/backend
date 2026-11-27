package com.capdi.backend.domain.payment.config;

import com.capdi.backend.domain.payment.gateway.PaymentGateway.GatewayException;
import com.capdi.backend.domain.payment.gateway.portone.PortOneProxyConfiguration;
import com.capdi.backend.domain.payment.gateway.portone.PortOneSettings;
import com.capdi.backend.domain.payment.gateway.portone.PortOneTransport;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/** Explicit, one-shot, read-only GET /payments probe. Never logs response data or credentials. */
@Component
@Profile("portone-get-payments-probe")
public final class PortOneGetPaymentsProbeRunner implements ApplicationRunner {
    private final Environment env;
    private final ObjectMapper mapper;

    public PortOneGetPaymentsProbeRunner(Environment env, ObjectMapper mapper) {
        this.env=env;
        this.mapper=mapper;
    }

    @Override public void run(ApplicationArguments args) {
        boolean enabled=env.getProperty("payment.portone.enabled",Boolean.class,false);
        boolean sandboxApproved=env.getProperty("payment.portone.sandbox-approved",Boolean.class,false);
        String storeId=env.getProperty("payment.portone.store-id");
        String channelKey=env.getProperty("payment.portone.channel-key");
        String environment=env.getProperty("payment.portone.environment","DISABLED");
        String proxyHost=env.getProperty("payment.portone.egress-proxy.host");
        Integer proxyPort=env.getProperty("payment.portone.egress-proxy.port",Integer.class);
        if (!enabled || !sandboxApproved || !"SANDBOX".equals(environment)
                || !PortOneSettings.identifier(storeId) || !PortOneSettings.identifier(channelKey)
                || env.getProperty("payment.portone.api-secret")!=null
                || env.getProperty("payment.portone.webhook-secret")!=null
                || env.getProperty("payment.portone.webhook-enabled",Boolean.class,false)
                || env.getProperty("payment.webhook.consumer.enabled",Boolean.class,false)
                || env.getProperty("financial.portone.enabled",Boolean.class,false)
                || env.getProperty("financial.portone.refund-enabled",Boolean.class,false)
                || env.getProperty("financial.portone.partner-enabled",Boolean.class,false)
                || env.getProperty("financial.portone.payout-enabled",Boolean.class,false)
                || env.getProperty("financial.portone.tax_invoice-enabled",Boolean.class,false)
                || env.getProperty("financial.worker.enabled",Boolean.class,false)) {
            throw new IllegalStateException("READ_ONLY_TEST_CONFIGURATION_INVALID");
        }
        if (!"portone-egress-proxy".equals(proxyHost) || proxyPort==null || proxyPort!=3128
                || env.getProperty("financial.internal-recovery.enabled",Boolean.class,false)
                || env.getProperty("financial.internal-tax.execution-approved",Boolean.class,false)
                || env.getProperty("subscription.portone.enabled",Boolean.class,false)) {
            throw new IllegalStateException("READ_ONLY_TEST_CONFIGURATION_INVALID");
        }
        String secret=PortOneSecretFiles.resolve(env,"api-secret");
        long timeoutMs=env.getProperty("payment.portone.timeout-ms",Long.class,10000L);
        new PortOneSettings(true,true,"SANDBOX",env.getProperty("payment.portone.merchant-id"),storeId,channelKey,
                secret,null,java.time.Duration.ofMillis(timeoutMs));
        var selector=PortOneProxyConfiguration.selector(env,"payment.portone",true);
        if (!env.getProperty("payment.portone.read-only-get-payments.approved", Boolean.class, false)) {
            System.out.println("PORTONE_GET_PAYMENTS PREFLIGHT=PASS REQUEST=NOT_SENT");
            return;
        }
        var transport=new PortOneTransport.Jdk(selector);
        try {
            var reply=transport.getPayments(storeId,secret,
                    java.time.Duration.ofMillis(timeoutMs));
            if(reply.status()!=200) {
                System.out.println("PORTONE_GET_PAYMENTS HTTP_STATUS="+reply.status());
                return;
            }
            JsonNode response=mapper.readTree(reply.body());
            JsonNode items=response.path("items");
            if(!items.isArray() || items.size()>1) {
                System.out.println("PORTONE_GET_PAYMENTS HTTP_STATUS=200 RESPONSE_SHAPE=UNEXPECTED");
                return;
            }
            System.out.println("PORTONE_GET_PAYMENTS HTTP_STATUS=200 ITEMS_RETURNED="+items.size());
        } catch (GatewayException ex) {
            System.out.println("PORTONE_GET_PAYMENTS ERROR="+(ex.failure()==null?"PROVIDER_ERROR":ex.failure().name()));
        } catch (Exception ex) {
            // Do not include exception messages, response bodies, headers, or causes.
            System.out.println("PORTONE_GET_PAYMENTS ERROR=RESPONSE_INVALID");
        }
    }
}
