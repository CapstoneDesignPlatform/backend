package com.capdi.backend.domain.payment.gateway.portone;

import org.junit.jupiter.api.Test;
import java.io.IOException;
import java.net.*;
import static org.assertj.core.api.Assertions.*;

class PortOneProxySelectorTest {
    @Test void selectsOneExplicitProxyAndNeverDirect() throws Exception {
        var selector=new PortOneProxySelector("127.0.0.1",3128);
        var selected=selector.select(URI.create("https://api.portone.io/payments/test?storeId=store"));
        assertThat(selected).hasSize(1);
        assertThat(selected.getFirst().type()).isEqualTo(Proxy.Type.HTTP);
        assertThat(((InetSocketAddress)selected.getFirst().address()).getPort()).isEqualTo(3128);
        selector.connectFailed(URI.create("https://api.portone.io/"),
            new InetSocketAddress("127.0.0.1",3128),new IOException("synthetic"));
        assertThat(selector.select(URI.create("https://api.portone.io/"))).hasSize(1);
    }

    @Test void rejectsNonPortOneOriginAndInvalidConfiguration() {
        var selector=new PortOneProxySelector("proxy-internal",3128);
        assertThatThrownBy(()->selector.select(URI.create("https://evil.example/")))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->selector.select(URI.create("http://api.portone.io/")))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->new PortOneProxySelector("proxy/path",3128))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->new PortOneProxySelector("256.1.1.1",3128))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->new PortOneProxySelector("proxy",0))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test void transportWithoutExplicitProxyFailsBeforeAnySocketRequest() {
        var transport=new PortOneTransport.Jdk();
        assertThatThrownBy(()->transport.get("capdi_test_attempt","store-test",
            "SYNTHETIC_ONLY_NOT_A_SECRET",java.time.Duration.ofSeconds(1)))
            .isInstanceOf(com.capdi.backend.domain.payment.gateway.PaymentGateway.GatewayException.class);
    }
}
