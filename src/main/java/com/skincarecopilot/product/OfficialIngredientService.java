package com.skincarecopilot.product;

import java.io.IOException;
import java.net.URI;
import java.net.InetAddress;
import java.net.Proxy;
import java.net.ProxySelector;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.text.Normalizer;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.stereotype.Service;

/** 从公开 HTTPS 产品页提取候选成分；网页来源未经核实，不使用搜索摘要。 */
@Service
public class OfficialIngredientService {
    private final HttpClient http;

    public OfficialIngredientService() {
        this(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8))
                .followRedirects(HttpClient.Redirect.NEVER).build());
    }

    OfficialIngredientService(HttpClient http) {
        this.http = http;
    }

    public Optional<OfficialList> extract(Identity identity, String url) throws IOException, InterruptedException {
        validateIdentity(identity);
        URI uri = checkedUrl(url);
        checkPublicDestination(uri);
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
        catch (Exception e) { throw new IllegalArgumentException("产品页 URL 无效"); }
        String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase(Locale.ROOT);
        if (!"https".equalsIgnoreCase(uri.getScheme()) || host.isBlank()
                || host.equals("localhost") || host.endsWith(".localhost")
                || host.endsWith(".local") || host.endsWith(".internal")
                || host.matches("[0-9.]+") || host.contains(":")
                || uri.getUserInfo() != null || (uri.getPort() != -1 && uri.getPort() != 443)
                || uri.getFragment() != null) {
            throw new IllegalArgumentException("仅能读取公开 HTTPS 产品页，不能访问本地或内网地址");
        }
        return uri;
    }

    private void checkPublicDestination(URI uri) throws IOException {
        // 代理环境由出站代理解析并过滤目标；本地直连才执行 DNS 私网地址检查。
        ProxySelector selector = ProxySelector.getDefault();
        if (selector != null && selector.select(uri).stream()
                .anyMatch(proxy -> proxy.type() == Proxy.Type.HTTP)) return;
        for (InetAddress address : InetAddress.getAllByName(uri.getHost())) {
            if (address.isAnyLocalAddress() || address.isLoopbackAddress()
                    || address.isLinkLocalAddress() || address.isSiteLocalAddress()
                    || address.isMulticastAddress()) throw new IllegalArgumentException("不能访问本地或内网地址");
        }
    }

    static void validateIdentity(Identity identity) {
        if (identity == null || invalid(identity.productName(), 120)
                || invalidOptional(identity.market(), 50) || invalidOptional(identity.version(), 80))
            throw new IllegalArgumentException("需提供不超过 120 字的具体产品全名");
    }

    private static boolean invalid(String text, int max) {
        return text == null || text.isBlank() || text.length() > max
                || text.chars().anyMatch(Character::isISOControl);
    }

    private static boolean invalidOptional(String text, int max) {
        return text != null && (text.length() > max || text.chars().anyMatch(Character::isISOControl));
    }

    /** 包级可测的纯解析函数；页面上三项身份信息和全成分标签必须同时出现。 */
    static Optional<OfficialList> parse(Identity identity, String url, String html) {
        validateIdentity(identity);
        Document page = Jsoup.parse(html, url);
        String title = page.title();
        String visible = page.body() == null ? "" : page.body().text();
        if (!matchesProductName(title, identity.productName())
                || (identity.market() != null && !identity.market().isBlank()
                    && !marketMatches(page, visible, identity.market()))
                || (identity.version() != null && !identity.version().isBlank()
                    && !versionMatches(page, visible, identity.version()))) return Optional.empty();
        List<String> ingredients = List.of();
        // 一些公开成分页把“包装成分”放在独立表格。只读取名称列，忽略站点的风险/功效评分。
        for (Element label : page.select("b,strong")) {
            if (!containsNormalized(label.text(), identity.productName())
                    || !label.text().contains("成分表")) continue;
            Element wrapper = label.parent();
            Element table = null;
            for (int i = 0; wrapper != null && i < 3 && table == null; i++, wrapper = wrapper.nextElementSibling()) {
                table = wrapper.is("table.table") ? wrapper : wrapper.selectFirst("table.table");
            }
            if (table == null) continue;
            List<String> listed = new ArrayList<>(table.select("tr td.table_mark").eachText());
            // 同一包装表可能分为“主要成分”和紧随其后的“其他微量成分”两张表。
            var tables = page.select("table.table");
            if (tables.size() == 2 && tables.get(0) == table && page.select("b,strong").stream()
                    .anyMatch(node -> node.text().contains("其他微量成分"))) {
                listed.addAll(tables.get(1).select("tr td.table_mark").eachText());
            }
            if (plausible(listed)) { ingredients = listed; break; }
        }
        // data-full-ingredients 适合明确暴露完整 INCI 的官网模板。
        for (Element node : page.select("[data-full-ingredients]")) {
            if (plausible(ingredients)) break;
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
        if (!plausible(ingredients)) {
            // 有些产品页只写 Ingredients，取该独立区域的首段，不拼接 FAQ 或“关键成分”介绍。
            for (Element heading : page.select("button[aria-controls],h2,h3")) {
                if (!heading.text().strip().matches("(?i)^ingredients$")) continue;
                String target = heading.attr("aria-controls");
                Element section = !target.isBlank() ? page.getElementById(target) : heading.nextElementSibling();
                if (section == null && heading.parent() != null) section = heading.parent().nextElementSibling();
                Element first = section == null ? null : section.selectFirst("p");
                if (first == null) continue;
                ingredients = split(first.text());
                if (plausible(ingredients)) break;
            }
        }
        if (!plausible(ingredients)) return Optional.empty();
        return Optional.of(new OfficialList(identity.productName(), identity.market(), identity.version(),
                List.copyOf(ingredients), url, LocalDate.now().toString(),
                "产品网页成分区的候选列表；来源身份、完整性和包装版本尚需核对"));
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

    private static boolean matchesProductName(String title, String name) {
        String a = Normalizer.normalize(title.replace("™", "").replace("®", ""), Normalizer.Form.NFKC)
                .replaceAll("[^\\p{L}\\p{N}]", "").toLowerCase(Locale.ROOT);
        String b = Normalizer.normalize(name, Normalizer.Form.NFKC)
                .replaceAll("[^\\p{L}\\p{N}]", "").toLowerCase(Locale.ROOT);
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
