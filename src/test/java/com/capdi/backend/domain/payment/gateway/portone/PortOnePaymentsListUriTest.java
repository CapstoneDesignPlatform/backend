package com.capdi.backend.domain.payment.gateway.portone;

import org.junit.jupiter.api.Test;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PortOnePaymentsListUriTest {
    @Test void requestsOnlyOneStoreScopedTestV2Payment() {
        var uri=PortOneTransport.Jdk.paymentsListUri("store-test");
        assertThat(uri.getScheme()).isEqualTo("https");
        assertThat(uri.getHost()).isEqualTo("api.portone.io");
        assertThat(uri.getPath()).isEqualTo("/payments");
        var query=URLDecoder.decode(uri.getRawQuery(),StandardCharsets.UTF_8);
        assertThat(query).isEqualTo("requestBody={\"page\":{\"number\":0,\"size\":1},\"filter\":{"+
                "\"storeId\":\"store-test\",\"isTest\":true,\"version\":\"V2\"}}");
        assertThat(uri.toString()).doesNotContain("Authorization");
    }

    @Test void rejectsInvalidStoreIdentityBeforeBuildingRequest() {
        assertThatThrownBy(()->PortOneTransport.Jdk.paymentsListUri("not a store"))
                .isInstanceOf(com.capdi.backend.domain.payment.gateway.PaymentGateway.GatewayException.class);
    }
}
