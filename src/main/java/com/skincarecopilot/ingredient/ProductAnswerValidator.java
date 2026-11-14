package com.skincarecopilot.ingredient;

import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/** 模型只负责措辞；虚构来源和绝对安全承诺不进入最终回答。 */
@Component
public class ProductAnswerValidator {
    private static final Pattern ABSOLUTE = Pattern.compile(
            "绝对安全|保证有效|一定适合|肯定适合|百分之百安全|100%安全|敏感肌必(?:烂脸|安全)");
    private static final Pattern SOURCE_ID = Pattern.compile("\\[来源\\s+([a-z0-9-]+)");

    public boolean valid(String answer, Set<String> allowedSourceIds) {
        if (answer == null || answer.isBlank() || answer.length() > 4000 || ABSOLUTE.matcher(answer).find())
            return false;
        var found = SOURCE_ID.matcher(answer);
        while (found.find()) if (!allowedSourceIds.contains(found.group(1))) return false;
        return true;
    }
}
