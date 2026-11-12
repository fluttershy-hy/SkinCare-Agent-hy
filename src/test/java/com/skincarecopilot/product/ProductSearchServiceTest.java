package com.skincarecopilot.product;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ProductSearchServiceTest {
    private final ObjectMapper json = new ObjectMapper();

    @Test
    void needsExactProductAndServerSideSearchKey() throws Exception {
        var noKey = new ProductSearchService(json, HttpClient.newHttpClient(), "",
                URI.create("http://127.0.0.1:1/search"));
        assertThrows(IllegalArgumentException.class, () -> noKey.search(" "));
        assertTrue(noKey.search("示例保湿霜").note().contains("密钥"));
    }

    @Test
    void returnsNamedHttpsPagesAsUnverifiedCandidates() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/search", exchange -> {
            assertEquals("Bearer test-key", exchange.getRequestHeaders().getFirst("Authorization"));
            assertTrue(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8)
                    .contains("\\\"示例保湿霜\\\" 成分表"));
            String result = "{\"results\":["
                    + "{\"title\":\"示例™保湿霜 官方页面\",\"url\":\"https://brand.example/product\",\"content\":\"成分摘要可能不全\"},"
                    + "{\"title\":\"示例保湿霜\",\"url\":\"https://brand.example.evil.test/x\",\"content\":\"假页面\"},"
                    + "{\"title\":\"另一款产品\",\"url\":\"https://brand.example/other\",\"content\":\"错产品\"}]}";
            byte[] bytes = result.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();
        try {
            var service = new ProductSearchService(json, HttpClient.newHttpClient(), "test-key",
                    URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/search"));
            var result = service.search("示例保湿霜");
            assertEquals(2, result.officialPageCandidates().size());
            assertTrue(result.note().contains("来源未经核实"));
            assertTrue(result.note().contains("不能据此判断个人致敏成分"));
        } finally { server.stop(0); }
    }
}
