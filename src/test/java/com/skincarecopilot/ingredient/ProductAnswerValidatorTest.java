package com.skincarecopilot.ingredient;

import java.util.Set;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ProductAnswerValidatorTest {
    private final ProductAnswerValidator validator = new ProductAnswerValidator();
    @Test void rejectsInventedSourcesAndAbsoluteClaims() {
        assertFalse(validator.valid("这款绝对安全。", Set.of()));
        assertFalse(validator.valid("可以参考[来源 fake-id]。", Set.of("aad-real")));
        assertTrue(validator.valid("刺激与过敏不同[来源 aad-real]；个人反应仍未知。", Set.of("aad-real")));
    }
}
