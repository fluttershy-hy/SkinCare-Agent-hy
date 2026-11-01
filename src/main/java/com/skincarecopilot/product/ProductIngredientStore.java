package com.skincarecopilot.product;

import com.skincarecopilot.profile.ProfileService;
import java.text.Normalizer;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/** 每个虚构用户自己确认的包装成分表；不冒充品牌官方资料。 */
@Service
public class ProductIngredientStore {
    private final JdbcTemplate jdbc;

    public ProductIngredientStore(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public void saveUserConfirmed(String userId, String productName, String ingredients) {
        ProfileService.validateUserId(userId);
        String key = key(productName);
        if (key.isBlank() || key.length() > 120 || ingredients == null || ingredients.isBlank()
                || ingredients.length() > 6000) throw new IllegalArgumentException("产品名或成分表无效");
        jdbc.update("MERGE INTO user_product_ingredients "
                        + "(user_id, product_key, product_name, ingredients_text, source_label) "
                        + "KEY(user_id, product_key) VALUES (?, ?, ?, ?, ?)",
                userId, key, productName.strip(), ingredients.strip(), "用户确认的包装成分表");
    }

    public Optional<SavedProduct> find(String userId, String productName) {
        ProfileService.validateUserId(userId);
        List<SavedProduct> found = jdbc.query("SELECT product_name, ingredients_text, source_label "
                        + "FROM user_product_ingredients WHERE user_id = ? AND product_key = ?",
                (row, index) -> new SavedProduct(row.getString(1), row.getString(2), row.getString(3)),
                userId, key(productName));
        return found.stream().findFirst();
    }

    private static String key(String name) {
        return name == null ? "" : Normalizer.normalize(name.strip(), Normalizer.Form.NFKC)
                .replaceAll("\\s+", "").toUpperCase(Locale.ROOT);
    }

    public record SavedProduct(String name, String ingredients, String sourceLabel) {}
}
