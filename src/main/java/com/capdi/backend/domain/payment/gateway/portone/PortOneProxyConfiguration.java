package com.capdi.backend.domain.payment.gateway.portone;

import org.springframework.core.env.Environment;

public final class PortOneProxyConfiguration {
    private PortOneProxyConfiguration() { }

    public static PortOneProxySelector selector(Environment env, String prefix, boolean providerEnabled) {
        if (!providerEnabled) return null;
        String host = env.getProperty(prefix + ".egress-proxy.host");
        Integer port = env.getProperty(prefix + ".egress-proxy.port", Integer.class);
        if (host == null || port == null) {
            throw new IllegalArgumentException("PortOne egress proxy configuration required");
        }
        return new PortOneProxySelector(host, port);
    }
}
