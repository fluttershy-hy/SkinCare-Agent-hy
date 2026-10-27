package com.skincarecopilot.product;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/** Read-only search. Search snippets are leads to verify, never allergy diagnoses or complete INCI lists. */
@Service
public class ProductSearchService {
    private final ObjectMapper json;
    private final HttpClient http;
    private final String apiKey;
    private final URI endpoint;
    private final Set<String> officialHosts;

    @Autowired
    public ProductSearchService(ObjectMapper json,
                                @Value("${SKINCARE_SEARCH_API_KEY:}") String apiKey,
                                @Value("${SKINCARE_PRODUCT_OFFICIAL_HOSTS:}") String hosts) {
        this(json, HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8)).build(), apiKey,
                URI.create("https://api.tavily.com/search"), hosts);
    }

    ProductSearchService(ObjectMapper json, HttpClient http, String apiKey, URI endpoint, String hosts) {
        this.json = json;
        this.http = http;
        this.apiKey = apiKey;
        this.endpoint = endpoint;
        this.officialHosts = Arrays.stream(hosts.split(",")).map(String::trim)
                .map(value -> value.toLowerCase(Locale.ROOT)).filter(value -> !value.isEmpty())
                .collect(Collectors.toUnmodifiableSet());
    }

    public ProductSearchResult search(String exactProductName) throws IOException, InterruptedException {
        if (exactProductName == null || exactProductName.isBlank() || exactProductName.length() > 120
                || exactProductName.chars().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException("请提供不超过 120 字的具体产品全名");
        }
        String product = exactProductName.strip();
        if (officialHosts.isEmpty()) {
            return new ProductSearchResult(product, List.of(), "尚未配置可信的品牌官网域名，无法核实成分来源。");
        }
        if (apiKey.isBlank()) {
            return new ProductSearchResult(product, List.of(), "尚未配置联网检索密钥 SKINCARE_SEARCH_API_KEY。");
        }
        String body = json.writeValueAsString(java.util.Map.of("query", product + " 官方 全成分 INCI",
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
                    || !officialHosts.contains(page.getHost().toLowerCase(Locale.ROOT))
                    || page.getUserInfo() != null || !title.replace(" ", "").contains(product.replace(" ", ""))) {
                continue;
            }
            String snippet = result.path("content").asText("");
            pages.add(new ProductPage(title, url, snippet.substring(0, Math.min(snippet.length(), 1000))));
        }
        return new ProductSearchResult(product, List.copyOf(pages), pages.isEmpty()
                ? "未找到可核对的具体产品官网页面；品牌名或搜索摘要不足以确定完整成分表。"
                : "找到官网候选页面。搜索摘要可能不完整；须核对该具体产品的官方全成分表，不能据此判断个人致敏成分。");
    }

    public record ProductPage(String title, String url, String searchSnippet) {}
    public record ProductSearchResult(String productName, List<ProductPage> officialPageCandidates, String note) {}
}
