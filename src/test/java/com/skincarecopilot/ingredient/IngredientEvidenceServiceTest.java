package com.skincarecopilot.ingredient;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import org.junit.jupiter.api.Test;

class IngredientEvidenceServiceTest {
    private final IngredientEvidenceService service = new IngredientEvidenceService();

    @Test
    void inVitroFindingDoesNotBecomePersonalHighRisk() {
        var product = new IngredientResponse.Product("演示", IngredientNames.parse("演示", "棕榈酸、香精"));
        var assessment = service.assess(List.of(product), List.of());
        assertEquals("UNKNOWN", assessment.decision());
        assertEquals(2, assessment.signals().size());
        assertTrue(assessment.signals().get(0).limitation().contains("不能直接给产品判高风险"));
    }

    @Test
    void onlyConfirmedMatchProducesAvoidDecision() {
        var product = new IngredientResponse.Product("演示", IngredientNames.parse("演示", "Parfum"));
        var alert = new IngredientResponse.Alert("演示", "香精", "Parfum", 1);
        assertEquals("AVOID_CONFIRMED_MATCH", service.assess(List.of(product), List.of(alert)).decision());
    }
}
