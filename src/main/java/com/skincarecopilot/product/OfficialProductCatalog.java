package com.skincarecopilot.product;

import java.net.URI;
import java.text.Normalizer;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/** 由项目维护者核对的少量具体商品来源；用户不需要配置官网域名或 SKU。 */
@Component
public class OfficialProductCatalog {
    private static final List<Entry> ENTRIES = List.of(new Entry(
            "CeraVe Moisturizing Cream", "Moisturizing Cream", "US", "3606000537729",
            "https://www.cerave.com/skincare/moisturizers/moisturizing-cream",
            "2026-11-09", List.of("CeraVe Moisturizing Cream", "Moisturizing Cream")));

    public Optional<Entry> find(String name) {
        if (name == null || name.isBlank()) return Optional.empty();
        String wanted = key(name);
        return ENTRIES.stream().filter(entry -> entry.aliases().stream()
                .anyMatch(alias -> key(alias).equals(wanted))).findFirst();
    }

    public Set<String> approvedHosts() {
        return ENTRIES.stream().map(entry -> URI.create(entry.url()).getHost().toLowerCase(Locale.ROOT))
                .collect(Collectors.toUnmodifiableSet());
    }

    private static String key(String name) {
        return Normalizer.normalize(name.strip(), Normalizer.Form.NFKC)
                .replaceAll("\\s+", "").toLowerCase(Locale.ROOT);
    }

    public record Entry(String displayName, String officialPageName, String market, String sku,
                        String url, String checkedOn, List<String> aliases) {
        public OfficialIngredientService.Identity identity() {
            return new OfficialIngredientService.Identity(officialPageName, market, sku);
        }
    }
}
