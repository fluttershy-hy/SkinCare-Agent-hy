package com.skincarecopilot.product;

import java.util.Optional;
import java.util.regex.Pattern;

/** 只识别带具体产品类型的常见问句；模糊句子继续走普通知识路由。 */
public final class ProductQuestionDetector {
    private static final Pattern PRODUCT_TYPE = Pattern.compile(".*(?:面霜|润肤霜|乳液|精华液|精华|防晒霜|洁面乳|洗面奶|面膜)$");
    private static final Pattern[] QUESTIONS = {
            Pattern.compile("(?:我|脸上|身上)?(?:可以|能|适合)(?:用|使用|涂)(.{2,100}?)(?:吗|么|[？?])?$"),
            Pattern.compile("(.{2,100}?)(?:适合我|适合脂皮|能用吗|可以用吗|怎么样|好用吗)[？?]?$"),
            Pattern.compile("(?:帮我看看|看看|分析一下)(.{2,100}?)(?:的成分|适不适合|怎么样)?[？?]?$"),
    };

    private ProductQuestionDetector() {}

    public static Optional<String> exactProductName(String message) {
        if (message == null) return Optional.empty();
        String input = message.strip();
        for (var pattern : QUESTIONS) {
            var matcher = pattern.matcher(input);
            if (!matcher.find()) continue;
            String name = matcher.group(1).strip();
            if (name.length() >= 6 && PRODUCT_TYPE.matcher(name).matches()) return Optional.of(name);
        }
        return Optional.empty();
    }
}
