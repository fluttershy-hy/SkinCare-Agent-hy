package com.skincarecopilot.ingredient;

import com.skincarecopilot.knowledge.KnowledgeChunk;
import com.skincarecopilot.knowledge.KnowledgeEntry;
import com.skincarecopilot.knowledge.KnowledgeService;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ProductAnalysisWorkflowTest {
    private final ProductAnalysisPlanner planner = new ProductAnalysisPlanner();
    private final IngredientEvidenceService judge = new IngredientEvidenceService();
    private final List<IngredientResponse.Product> products = List.of(new IngredientResponse.Product("示例面膜",
            IngredientNames.parse("示例面膜", "水、香精、甘油")));

    private IngredientRequest request(String question, String state) {
        return new IngredientRequest("demo", new IngredientRequest.Product("示例面膜", "水、香精、甘油"),
                null, "敏感肌", "保湿", List.of(), false, question, "面部", state, true);
    }

    @Test void unknownStateAsksForContextWithoutInventingAllergy() {
        var plan = planner.plan(request("这款面膜适合敏感肌吗", null), products);
        assertEquals("不确定", plan.currentState());
        var decision = judge.assess(products, List.of(), plan, 1);
        assertEquals("ASK_CURRENT_STATE", decision.decision());
        assertTrue(decision.nextQuestion().contains("泛红"));
    }

    @Test void currentDiscomfortAndConfirmedAllergenHaveDistinctDecisions() {
        var plan = planner.plan(request("我现在脸上泛红刺痛，这款能用吗", null), products);
        assertEquals("正在泛红或不适", plan.currentState());
        assertEquals("PAUSE_DURING_DISCOMFORT", judge.assess(products, List.of(), plan, 1).decision());
        var alert = new IngredientResponse.Alert("示例面膜", "香精", "香精", 2);
        assertEquals("AVOID_CONFIRMED_MATCH", judge.assess(products, List.of(alert), plan, 1).decision());
    }

    @Test void stableSkinWithoutRelevantEvidenceStillHasNoPersonalSuitabilityVerdict() {
        var plan = planner.plan(request("这款面膜适合敏感肌吗", "稳定"), products);
        assertEquals("EVIDENCE_GAP", judge.assess(products, List.of(), plan, 0).decision());
        assertEquals("EXPLAIN_WITH_LIMITS", judge.assess(products, List.of(), plan, 1).decision());
    }

    @Test void irrelevantCreatorAndLabArticlesDoNotEnterSensitiveMaskEvidence() {
        KnowledgeService knowledge = mock(KnowledgeService.class);
        var contact = entry("contact-dermatitis-irritant-allergic", "AAD", "刺激和过敏并不相同");
        var creator = entry("miriam-cleansing", "MiriamMibao", "洁面紧绷");
        var lab = entry("malassezia-fa-defined-medium-2025", "EuropePMC", "培养基生长");
        var hits = List.of(hit(contact), hit(creator), hit(lab));
        when(knowledge.search(anyString(), eq(5))).thenReturn(
                new KnowledgeService.SearchResult("query", "test", .75, hits));
        var selected = new ProductEvidenceSelector(knowledge).select(
                planner.plan(request("这款面膜适合敏感肌吗", null), products),
                request("这款面膜适合敏感肌吗", null), products);
        assertEquals(List.of("contact-dermatitis-irritant-allergic"),
                selected.entries().stream().map(KnowledgeEntry::id).toList());
        verify(knowledge, atLeast(2)).search(anyString(), eq(5));
    }

    @Test void relevantBookConceptCanJoinProfessionalSourceWithoutBecomingProductProof() {
        KnowledgeService knowledge = mock(KnowledgeService.class);
        var book = entry("book-skin-sensitive-distinction", "Book", "敏感皮肤不等于确认过敏");
        when(knowledge.search(anyString(), eq(5))).thenReturn(new KnowledgeService.SearchResult(
                "query", "test", .75, List.of(hit(book))));
        var selected = new ProductEvidenceSelector(knowledge).select(
                planner.plan(request("这款面膜适合敏感肌吗", null), products),
                request("这款面膜适合敏感肌吗", null), products);
        assertEquals(List.of("book-skin-sensitive-distinction"),
                selected.entries().stream().map(KnowledgeEntry::id).toList());
    }

    private KnowledgeEntry entry(String id, String publisher, String text) {
        return new KnowledgeEntry(id, publisher, id, "https://example.org/" + id,
                "section", "2026-11-14", text, text);
    }
    private KnowledgeService.SearchHit hit(KnowledgeEntry entry) {
        return new KnowledgeService.SearchHit(.9,
                new KnowledgeChunk(entry.id()+":0", entry.id(), 0, entry.text().length(), entry.text(), entry));
    }
}
