package com.skincarecopilot;

import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.ProxySelector;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/** 云环境有 HTTPS_PROXY 时让 JDK HttpClient 使用同一代理；本机请求保持直连。 */
final class ProxyNetworkSetup {
    private ProxyNetworkSetup() {}

    static void configure() {
        String configured = System.getenv("HTTPS_PROXY");
        if (configured == null || configured.isBlank()) configured = System.getenv("https_proxy");
        if (configured == null || configured.isBlank()) return;
        URI uri = URI.create(configured);
        if (uri.getHost() == null || uri.getPort() < 1) return;
        Proxy proxy = new Proxy(Proxy.Type.HTTP, new InetSocketAddress(uri.getHost(), uri.getPort()));
        ProxySelector.setDefault(new ProxySelector() {
            @Override public List<Proxy> select(URI target) {
                String host = target.getHost();
                if (host == null || host.equals("localhost") || host.equals("127.0.0.1")
                        || host.equals("::1")) return List.of(Proxy.NO_PROXY);
                return List.of(proxy);
            }
            @Override public void connectFailed(URI target, java.net.SocketAddress address,
                                                java.io.IOException failure) {}
        });
        Path systemCa = Path.of("/etc/ssl/certs/java/cacerts");
        if (System.getProperty("javax.net.ssl.trustStore") == null && Files.isRegularFile(systemCa)) {
            System.setProperty("javax.net.ssl.trustStore", systemCa.toString());
        }
    }
}
