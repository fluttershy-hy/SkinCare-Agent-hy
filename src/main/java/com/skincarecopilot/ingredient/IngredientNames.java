package com.skincarecopilot.ingredient;

import java.text.Normalizer;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** 演示用小型名称表；未知名称仅做字面标准化，不猜测其 INCI 身份。 */
final class IngredientNames {
    private static final Map<String, String> ALIASES = Map.ofEntries(
            Map.entry("香精", "FRAGRANCE"), Map.entry("香料", "FRAGRANCE"),
            Map.entry("PARFUM", "FRAGRANCE"), Map.entry("FRAGRANCE", "FRAGRANCE"),
            Map.entry("烟酰胺", "NIACINAMIDE"), Map.entry("NIACINAMIDE", "NIACINAMIDE"),
            Map.entry("甘油", "GLYCERIN"), Map.entry("丙三醇", "GLYCERIN"),
            Map.entry("GLYCERIN", "GLYCERIN"));
    static final Set<String> KNOWN = Set.copyOf(ALIASES.values());

    static String key(String text) {
        return Normalizer.normalize(text.strip(), Normalizer.Form.NFKC)
                .replaceAll("[\\s\\u00a0]+", " ").toUpperCase(java.util.Locale.ROOT);
    }

    static String canonical(String text) {
        String key = key(text);
        return ALIASES.getOrDefault(key, key);
    }

    static List<Parsed> parse(String label, String text) {
        if (text == null || text.isBlank() || text.length() > 6000) {
            throw new IllegalArgumentException("每款产品需要 1～6000 字符的完整成分表");
        }
        String[] parts = text.split("[,，、;；\\n]+", -1);
        if (parts.length > 150) throw new IllegalArgumentException("每款产品最多 150 项成分");
        List<Parsed> result = new java.util.ArrayList<>();
        for (String part : parts) {
            String raw = part.strip();
            if (raw.isEmpty()) continue;
            if (raw.length() > 120) throw new IllegalArgumentException("单项成分名称不能超过 120 字符");
            String canonical = canonical(raw);
            result.add(new Parsed(result.size() + 1, raw, canonical, KNOWN.contains(canonical)));
        }
        if (result.isEmpty()) throw new IllegalArgumentException(label + "的成分表为空");
        return List.copyOf(result);
    }

    record Parsed(int position, String raw, String normalized, boolean recognized) {}
}
