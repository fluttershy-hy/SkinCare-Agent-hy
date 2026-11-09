package com.skincarecopilot.product;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.text.Normalizer;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/** 只从预先登记的品牌官网、明确标为全成分的区域提取；不使用搜索摘要。 */
@Service
public class OfficialIngredientService {
    private final HttpClient http;
    private final Set<String> hosts;

    @Autowired
    public OfficialIngredientService(@Value("${SKINCARE_PRODUCT_OFFICIAL_HOSTS:}") String configuredHosts,
                                     OfficialProductCatalog catalog) {
        this(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8))
                .followRedirects(HttpClient.Redirect.NEVER).build(), configuredHosts, catalog.approvedHosts());
    }

    OfficialIngredientService(HttpClient http, String configuredHosts) {
        this(http, configuredHosts, Set.of());
    }

    private OfficialIngredientService(HttpClient http, String configuredHosts, Set<String> approvedHosts) {
        this.http = http;
        var configured = Arrays.stream(configuredHosts.split(","))
                .map(String::strip).map(s -> s.toLowerCase(Locale.ROOT)).filter(s -> !s.isBlank())
                .collect(Collectors.toUnmodifiableSet());
        this.hosts = java.util.stream.Stream.concat(configured.stream(), approvedHosts.stream())
                .collect(Collectors.toUnmodifiableSet());
    }

    public Optional<OfficialList> extract(Identity identity, String url) throws IOException, InterruptedException {
        validateIdentity(identity);
        URI uri = checkedUrl(url);
        HttpRequest request = HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(15))
                .header("Accept", "text/html").GET().build();
        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200 || !response.headers().firstValue("Content-Type")
                .orElse("").toLowerCase(Locale.ROOT).contains("text/html")) return Optional.empty();
        if (response.body().length() > 1_000_000) return Optional.empty();
        return parse(identity, uri.toString(), response.body());
    }

    URI checkedUrl(String url) {
        URI uri;
        try { uri = URI.create(url); }
        catch (Exception e) { throw new IllegalArgumentException("官网 URL 无效"); }
        String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase(Locale.ROOT);
        if (!"https".equalsIgnoreCase(uri.getScheme()) || !hosts.contains(host)
                || host.equals("localhost") || host.matches("[0-9.]+") || host.contains(":")
                || uri.getUserInfo() != null || (uri.getPort() != -1 && uri.getPort() != 443)
                || uri.getFragment() != null) {
            throw new IllegalArgumentException("仅能读取预先配置的 HTTPS 品牌官网域名");
        }
        return uri;
    }

    static void validateIdentity(Identity identity) {
        if (identity == null || invalid(identity.productName(), 120) || invalid(identity.market(), 50)
                || invalid(identity.version(), 80))
            throw new IllegalArgumentException("需提供具体产品全名、销售市场和页面明示的 SKU／型号／版本标识");
    }

    private static boolean invalid(String text, int max) {
        return text == null || text.isBlank() || text.length() > max
                || text.chars().anyMatch(Character::isISOControl);
    }

    /** 包级可测的纯解析函数；页面上三项身份信息和全成分标签必须同时出现。 */
    static Optional<OfficialList> parse(Identity identity, String url, String html) {
        validateIdentity(identity);
        Document page = Jsoup.parse(html, url);
        String title = page.title();
        String visible = page.body() == null ? "" : page.body().text();
        if (!containsNormalized(title, identity.productName())
                || !marketMatches(page, visible, identity.market())
                || !versionMatches(page, visible, identity.version())) return Optional.empty();
        List<String> ingredients = List.of();
        // data-full-ingredients 适合明确暴露完整 INCI 的官网模板。
        for (Element node : page.select("[data-full-ingredients]")) {
            ingredients = split(node.hasAttr("data-full-ingredients")
                    && !node.attr("data-full-ingredients").isBlank()
                    ? node.attr("data-full-ingredients") : node.text());
            if (plausible(ingredients)) break;
        }
        if (!plausible(ingredients)) {
            // CeraVe 的官网把全成分放在 details 中；只取第一段，排除后面的包装更新提示。
            for (Element details : page.select("details")) {
                Element summary = details.selectFirst("summary[data-open],summary[data-closed]");
                Element first = details.selectFirst(".keyIngredients-details__content > p:first-child");
                if (summary == null || first == null
                        || !(summary.attr("data-open") + summary.attr("data-closed"))
                        .toLowerCase(Locale.ROOT).contains("full ingredient list")) continue;
                ingredients = split(first.text());
                if (plausible(ingredients)) break;
            }
        }
        if (!plausible(ingredients)) {
            for (Element heading : page.select("h2,h3,h4,h5,dt,strong,button")) {
                if (!heading.text().strip().matches("(?i)^(full ingredients|full ingredient list:?|complete ingredient list|全成分|完整成分表)$"))
                    continue;
                Element next = heading.nextElementSibling();
                if (next == null && heading.parent() != null) next = heading.parent().nextElementSibling();
                String listed = next == null ? "" : next.text();
                if (listed.isBlank() && heading.parent() != null) listed = heading.parent().ownText();
                ingredients = split(listed);
                if (plausible(ingredients)) break;
            }
        }
        if (!plausible(ingredients)) return Optional.empty();
        return Optional.of(new OfficialList(identity.productName(), identity.market(), identity.version(),
                List.copyOf(ingredients), url, LocalDate.now().toString(),
                "品牌官网页面的明确全成分区域；SKU/型号不等于配方修订版本，具体包装批次仍需核对"));
    }

    private static boolean marketMatches(Document page, String visible, String market) {
        String requested = market.strip().toLowerCase(Locale.ROOT);
        if (Set.of("us", "usa", "united states").contains(requested)) {
            return "en-us".equalsIgnoreCase(page.selectFirst("html") == null ? ""
                    : page.selectFirst("html").attr("lang"))
                    || page.select("[data-country=US]").size() > 0;
        }
        return containsNormalized(visible, market);
    }

    private static boolean versionMatches(Document page, String visible, String version) {
        if (containsNormalized(visible, version)) return true;
        for (Element node : page.select("[data-tag-product-id],[data-tag-product_info],[itemprop=sku],[itemprop=mpn]")) {
            for (String attribute : List.of("data-tag-product-id", "data-tag-product_info", "content")) {
                if (node.hasAttr(attribute) && (node.attr(attribute).equals(version)
                        || node.attr(attribute).endsWith("::" + version))) return true;
            }
        }
        return false;
    }

    private static boolean containsNormalized(String haystack, String needle) {
        String a = Normalizer.normalize(haystack, Normalizer.Form.NFKC).replaceAll("\\s+", "")
                .toLowerCase(Locale.ROOT);
        String b = Normalizer.normalize(needle, Normalizer.Form.NFKC).replaceAll("\\s+", "")
                .toLowerCase(Locale.ROOT);
        return a.contains(b);
    }

    private static List<String> split(String text) {
        if (text.length() > 6000) return List.of();
        List<String> items = new ArrayList<>();
        for (String part : text.split("[,，、;；•\\n]+")) {
            String item = part.strip().replaceFirst("[.。]+$", "");
            if (item.isBlank() || item.length() > 120) return List.of();
            items.add(item);
        }
        return items;
    }

    private static boolean plausible(List<String> items) { return items.size() >= 5 && items.size() <= 150; }

    public record Identity(String productName, String market, String version) {}
    public record OfficialList(String productName, String market, String version, List<String> ingredients,
                               String sourceUrl, String checkedOn, String sourceNote) {}
}
