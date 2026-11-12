package com.skincarecopilot.product;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.text.Normalizer;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Read-only search. Search results are unverified page leads, never official-source proof. */
@Service
public class ProductSearchService {
    private static final Logger log = LoggerFactory.getLogger(ProductSearchService.class);
    private final ObjectMapper json;
    private final HttpClient http;
    private final String apiKey;
    private final URI endpoint;

    @Autowired
    public ProductSearchService(ObjectMapper json,
                                @Value("${SKINCARE_SEARCH_API_KEY:}") String apiKey) {
        this(json, HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8)).build(), apiKey,
                URI.create("https://api.tavily.com/search"));
    }

    ProductSearchService(ObjectMapper json, HttpClient http, String apiKey, URI endpoint) {
        this.json = json;
        this.http = http;
        this.apiKey = apiKey;
        this.endpoint = endpoint;
    }

    public ProductSearchResult search(String exactProductName) throws IOException, InterruptedException {
        if (exactProductName == null || exactProductName.isBlank() || exactProductName.length() > 120
                || exactProductName.chars().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException("请提供不超过 120 字的具体产品全名");
        }
        String product = exactProductName.strip();
        if (apiKey.isBlank()) {
            return new ProductSearchResult(product, List.of(), "服务端尚未配置联网检索密钥 SKINCARE_SEARCH_API_KEY。");
        }
        // 中文产品页常写“成分表”而不写 INCI；加引号可减少同品牌其他商品的噪声。
        String body = json.writeValueAsString(java.util.Map.of("query", "\"" + product + "\" 成分表",
                "max_results", 5, "include_answer", false));
        HttpRequest request = HttpRequest.newBuilder(endpoint).timeout(Duration.ofSeconds(15))
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body)).build();
        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new IOException("联网搜索服务暂不可用，HTTP " + response.statusCode());
        }
        JsonNode root = json.readTree(response.body());
        List<ProductPage> pages = new ArrayList<>();
        for (JsonNode result : root.path("results")) {
            String url = result.path("url").asText("");
            String title = result.path("title").asText("");
            URI page;
            try { page = URI.create(url); } catch (IllegalArgumentException e) { continue; }
            if (!"https".equalsIgnoreCase(page.getScheme()) || page.getHost() == null
                    || page.getUserInfo() != null || (page.getPort() != -1 && page.getPort() != 443)
                    || page.getFragment() != null || !key(title).contains(key(product))) {
                continue;
            }
            String snippet = result.path("content").asText("");
            pages.add(new ProductPage(title, url, snippet.substring(0, Math.min(snippet.length(), 1000))));
        }
        log.info("product search candidates returned={} accepted={}", root.path("results").size(), pages.size());
        return new ProductSearchResult(product, List.copyOf(pages), pages.isEmpty()
                ? "未找到名称匹配的具体产品页面；品牌名或搜索摘要不足以确定完整成分表。"
                : "找到名称匹配的网页候选。网页来源未经核实，摘要可能不完整；不能据此判断个人致敏成分。");
    }

    private static String key(String value) {
        return Normalizer.normalize(value.replace("™", "").replace("®", ""), Normalizer.Form.NFKC)
                .replaceAll("[^\\p{L}\\p{N}]", "").toLowerCase(Locale.ROOT);
    }

    public record ProductPage(String title, String url, String searchSnippet) {}
    public record ProductSearchResult(String productName, List<ProductPage> officialPageCandidates, String note) {}
}
