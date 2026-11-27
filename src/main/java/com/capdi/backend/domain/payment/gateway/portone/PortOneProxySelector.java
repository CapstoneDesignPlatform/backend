package com.capdi.backend.domain.payment.gateway.portone;

import java.io.IOException;
import java.net.*;
import java.util.List;

/** Routes only the fixed PortOne HTTPS origin through one explicit proxy; never returns DIRECT. */
public final class PortOneProxySelector extends ProxySelector {
    private final Proxy proxy;

    public PortOneProxySelector(String host, int port) {
        if (!validHost(host) || port < 1 || port > 65535) {
            throw new IllegalArgumentException("PortOne egress proxy configuration invalid");
        }
        this.proxy = new Proxy(Proxy.Type.HTTP, InetSocketAddress.createUnresolved(host, port));
    }

    private static boolean validHost(String host) {
        if (host == null || host.length() > 253) return false;
        if (host.matches("[0-9.]+")) {
            String[] octets=host.split("\\.",-1);
            if(octets.length!=4)return false;
            for(String octet:octets)try{if(octet.isEmpty()||Integer.parseInt(octet)>255)return false;}
                catch(NumberFormatException ex){return false;}
            return true;
        }
        for(String label:host.split("\\.",-1))
            if(label.isEmpty()||label.length()>63||!label.matches("[A-Za-z0-9](?:[A-Za-z0-9-]*[A-Za-z0-9])?"))return false;
        return true;
    }

    @Override public List<Proxy> select(URI uri) {
        if (uri == null || !"https".equalsIgnoreCase(uri.getScheme())
                || !"api.portone.io".equalsIgnoreCase(uri.getHost())
                || (uri.getPort() != -1 && uri.getPort() != 443)) {
            throw new IllegalArgumentException("PortOne destination is not allowed");
        }
        return List.of(proxy);
    }

    /** A proxy failure is terminal. There is deliberately no DIRECT fallback. */
    @Override public void connectFailed(URI uri, SocketAddress sa, IOException ioe) { }
}
