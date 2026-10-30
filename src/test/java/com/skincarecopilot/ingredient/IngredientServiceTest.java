package com.skincarecopilot.ingredient;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.skincarecopilot.knowledge.KnowledgeService;
import com.skincarecopilot.profile.ProfileService;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class IngredientServiceTest {
    private final ProfileService profiles = mock(ProfileService.class);
    private final KnowledgeService knowledge = mock(KnowledgeService.class);
    private final IngredientService service = new IngredientService(profiles, knowledge,
            new ObjectMapper(), "", "https://example.test", "unused");

    IngredientServiceTest() {
        when(profiles.find("demo")).thenReturn(Optional.empty());
        when(knowledge.search(anyString(), eq(5)))
                .thenReturn(new KnowledgeService.SearchResult("test", "test", 0.75, List.of()));
    }

    @Test
    void enforcesConfirmedAliasHitEvenWithoutModel() {
        var response = service.analyze(new IngredientRequest("demo",
                new IngredientRequest.Product("A", "水、Parfum、甘油"),
                new IngredientRequest.Product("B", "水、甘油"), "敏感肌", "保湿",
                List.of("香精"), true, "两款有什么区别？"));
        assertEquals(1, response.allergyAlerts().size());
        assertEquals("A", response.allergyAlerts().get(0).product());
        assertEquals(2, response.allergyAlerts().get(0).position());
        assertTrue(response.answer().startsWith("已确认过敏成分命中"));
        assertEquals(2, response.products().size());
        assertTrue(response.answer().contains("资料不足"));
    }

    @Test
    void refusesUnconfirmedAllergyInput() {
        var request = new IngredientRequest("demo", new IngredientRequest.Product("A", "香精"),
                null, null, null, List.of("香精"), false, "能用吗？");
        assertThrows(IllegalArgumentException.class, () -> service.analyze(request));
        verify(knowledge, never()).search(anyString(), anyInt());
    }
}
