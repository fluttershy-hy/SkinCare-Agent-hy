package com.skincarecopilot.meal;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

/** 只覆盖明列的食材和别名；这是演示校验，不是现实过敏风险保证。 */
@Component
public class MealAllergenValidator {
    private static final Map<String, List<String>> GROUPS = new LinkedHashMap<>();
    static {
        GROUPS.put("花生", List.of("花生", "花生酱", "花生油", "peanut", "groundnut"));
        GROUPS.put("牛奶", List.of("牛奶", "奶粉", "奶油", "酸奶", "奶酪", "乳酪", "乳清", "黄油", "milk", "cheese", "yogurt", "whey"));
        GROUPS.put("鸡蛋", List.of("鸡蛋", "蛋", "蛋液", "蛋黄", "蛋白", "egg"));
        GROUPS.put("大豆", List.of("大豆", "黄豆", "豆浆", "豆腐", "酱油", "soy", "soya"));
        GROUPS.put("小麦", List.of("小麦", "面粉", "面条", "面包", "馒头", "wheat"));
        GROUPS.put("虾", List.of("虾", "虾仁", "虾皮", "shrimp", "prawn"));
        GROUPS.put("蟹", List.of("蟹", "蟹肉", "crab"));
        GROUPS.put("鱼", List.of("鱼", "鱼肉", "fish"));
        GROUPS.put("贝类", List.of("贝类", "蛤蜊", "扇贝", "生蚝", "牡蛎", "shellfish", "clam", "oyster"));
        GROUPS.put("海鲜", List.of("海鲜", "seafood"));
        GROUPS.put("芝麻", List.of("芝麻", "芝麻酱", "香油", "sesame"));
    }
    private static final Set<String> SEAFOOD = Set.of("虾", "蟹", "鱼", "贝类", "海鲜");

    public List<Violation> validate(MealDraft draft, List<String> confirmedAllergens) {
        if (draft == null || draft.meals() == null) throw new IllegalArgumentException("餐食草稿缺少餐次");
        Set<String> target = new LinkedHashSet<>();
        for (String allergen : confirmedAllergens) {
            String key = normalize(allergen);
            if (key.equals("海鲜") || key.equals("seafood")) target.addAll(SEAFOOD);
            boolean recognized = key.equals("海鲜") || key.equals("seafood");
            for (var group : GROUPS.entrySet()) if (group.getValue().stream()
                    .map(MealAllergenValidator::normalize).anyMatch(key::equals)) {
                target.add(group.getKey());
                recognized = true;
            }
            if (!recognized && !key.isBlank()) target.add(key);
        }
        List<Violation> found = new ArrayList<>();
        for (var meal : draft.meals()) for (String ingredient : meal.ingredients()) {
            String item = normalize(ingredient);
            for (String allergen : target) {
                List<String> aliases = GROUPS.get(allergen);
                boolean match = aliases == null ? item.equals(allergen) : aliases.stream()
                        .map(MealAllergenValidator::normalize).anyMatch(alias -> matchesIngredient(item, alias));
                if (match) found.add(new Violation(meal.name(), ingredient, allergen));
            }
        }
        return List.copyOf(found);
    }

    private static boolean matchesIngredient(String item, String alias) {
        // 食材是结构化字段，接受“清蒸虾仁”“花生酱 10g”等修饰；不扫描整段回答。
        if (alias.matches("[a-z ]+")) return item.matches(".*(?:^|[^a-z])" + java.util.regex.Pattern.quote(alias)
                + "(?:$|[^a-z]).*");
        return item.contains(alias);
    }

    private static String normalize(String value) {
        return value == null ? "" : Normalizer.normalize(value.strip(), Normalizer.Form.NFKC)
                .toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
    }

    public record Violation(String meal, String ingredient, String matchedAllergen) {}
}
