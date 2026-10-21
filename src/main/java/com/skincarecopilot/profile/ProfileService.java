package com.skincarecopilot.profile;

import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProfileService {
    private final JdbcTemplate jdbc;

    public ProfileService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional
    public Profile saveConfirmed(String userId, ProfileUpdateRequest request) {
        validateUserId(userId);
        if (request == null || !request.confirmed()) {
            throw new IllegalArgumentException("请先确认画像内容，再保存");
        }
        String skinType = request.skinType() == null ? "" : request.skinType().trim();
        if (skinType.isEmpty() || skinType.length() > 80) {
            throw new IllegalArgumentException("肤质需填写，且不能超过 80 字符");
        }
        List<String> allergens = request.allergens() == null ? List.of() : request.allergens().stream()
                .map(value -> value == null ? "" : value.trim())
                .filter(value -> !value.isEmpty())
                .distinct().toList();
        if (allergens.size() > 20 || allergens.stream().anyMatch(value -> value.length() > 80)) {
            throw new IllegalArgumentException("过敏原最多 20 项，每项最多 80 字符");
        }
        // 只有用户明确确认的请求才会进入数据库；聊天和模型工具均不调用此方法。
        jdbc.update("MERGE INTO user_profile (user_id, skin_type) KEY(user_id) VALUES (?, ?)", userId, skinType);
        jdbc.update("DELETE FROM user_allergen WHERE user_id = ?", userId);
        for (String allergen : allergens) {
            jdbc.update("INSERT INTO user_allergen (user_id, allergen) VALUES (?, ?)", userId, allergen);
        }
        return new Profile(userId, skinType, allergens);
    }

    public Optional<Profile> find(String userId) {
        validateUserId(userId);
        List<String> skinTypes = jdbc.queryForList(
                "SELECT skin_type FROM user_profile WHERE user_id = ?", String.class, userId);
        if (skinTypes.isEmpty()) {
            return Optional.empty();
        }
        List<String> allergens = jdbc.queryForList(
                "SELECT allergen FROM user_allergen WHERE user_id = ? ORDER BY allergen", String.class, userId);
        return Optional.of(new Profile(userId, skinTypes.get(0), allergens));
    }

    public static void validateUserId(String userId) {
        if (userId == null || !userId.matches("[A-Za-z0-9_-]{1,40}")) {
            throw new IllegalArgumentException("用户 ID 只能由 1～40 位字母、数字、下划线或短横线组成");
        }
    }
}
