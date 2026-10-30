package com.skincarecopilot.ingredient;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class IngredientNamesTest {
    @Test
    void preservesOrderAndOriginalWhileMatchingOnlyKnownAliases() {
        var parts = IngredientNames.parse("演示", "甘油，Parfum\n烟酰胺; 未收录成分");
        assertEquals(4, parts.size());
        assertEquals("Parfum", parts.get(1).raw());
        assertEquals(2, parts.get(1).position());
        assertEquals("FRAGRANCE", parts.get(1).normalized());
        assertEquals("FRAGRANCE", IngredientNames.canonical("香精"));
        assertFalse(parts.get(3).recognized());
        assertNotEquals("FRAGRANCE", IngredientNames.canonical("香精复合物"));
    }

    @Test
    void rejectsMissingOrExcessiveLists() {
        assertThrows(IllegalArgumentException.class, () -> IngredientNames.parse("A", "  "));
        assertThrows(IllegalArgumentException.class, () -> IngredientNames.parse("A", "水,".repeat(151)));
    }
}
