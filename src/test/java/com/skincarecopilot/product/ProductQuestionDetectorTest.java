package com.skincarecopilot.product;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class ProductQuestionDetectorTest {
    @Test
    void recognizesSpecificProductButNotIngredientOrGenericQuestion() {
        assertEquals("适乐肤神经酰胺屏障修护润肤霜",
                ProductQuestionDetector.exactProductName("我可以用适乐肤神经酰胺屏障修护润肤霜吗").orElseThrow());
        assertTrue(ProductQuestionDetector.exactProductName("我可以用烟酰胺吗").isEmpty());
        assertTrue(ProductQuestionDetector.exactProductName("面霜适合我吗").isEmpty());
    }
}
