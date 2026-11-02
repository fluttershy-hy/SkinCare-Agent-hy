package com.skincarecopilot.meal;

import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MealAllergenValidatorTest {
    private final MealAllergenValidator validator = new MealAllergenValidator();

    @Test void seafoodAndPeanutAliasesMatchStructuredIngredients() {
        var draft = new MealDraft(List.of(new MealDraft.Meal("午餐", "", List.of("清蒸虾仁", "花生酱 10g", "米饭"))));
        var hits = validator.validate(draft, List.of("海鲜", "peanut"));
        assertEquals(2, hits.size());
        assertEquals("虾", hits.get(0).matchedAllergen());
        assertEquals("花生", hits.get(1).matchedAllergen());
    }

    @Test void unknownAllergenMatchesOnlyExactName() {
        var draft = new MealDraft(List.of(new MealDraft.Meal("午餐", "", List.of("火龙果", "米饭"))));
        assertEquals(1, validator.validate(draft, List.of("火龙果")).size());
        assertTrue(validator.validate(draft, List.of("苹果")).isEmpty());
    }
}
