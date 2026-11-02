package com.skincarecopilot.meal;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.skincarecopilot.chat.ChatRoute;
import com.skincarecopilot.knowledge.KnowledgeEntry;
import com.skincarecopilot.knowledge.KnowledgeService;
import com.skincarecopilot.profile.Profile;
import com.skincarecopilot.profile.ProfileService;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class MealPlanServiceTest {
    private final ProfileService profiles = mock(ProfileService.class);
    private final KnowledgeService knowledge = mock(KnowledgeService.class);
    private final MealDraftGenerator generator = mock(MealDraftGenerator.class);
    private final MealPlanService service = new MealPlanService(profiles, knowledge, generator,
            new MealAllergenValidator(), new ObjectMapper());

    private void setup() {
        when(profiles.find("demo")).thenReturn(Optional.of(new Profile("demo", "油性", List.of("海鲜", "花生"))));
        var entry = new KnowledgeEntry("acne-diet-evidence", "AAD", "Can the right diet get rid of acne?",
                "https://www.aad.org/public/diseases/acne/causes/diet", "Diet", "2026-10-24",
                "More research is needed", "研究结果不一致");
        when(knowledge.catalog()).thenReturn(new KnowledgeService.Catalog("test", 1, 1, 0,
                0.75, 1, 1, List.of(entry)));
    }

    private static String draft(String lunch) {
        return "{\"meals\":["
                + "{\"name\":\"早餐\",\"description\":\"演示\",\"ingredients\":[\"燕麦\",\"苹果\"]},"
                + "{\"name\":\"午餐\",\"description\":\"演示\",\"ingredients\":[\"" + lunch + "\",\"米饭\"]},"
                + "{\"name\":\"晚餐\",\"description\":\"演示\",\"ingredients\":[\"青菜\",\"土豆\"]}]}";
    }

    @Test void rejectsFixedViolationThenReturnsOnlyCorrectedPlan() {
        setup();
        when(generator.generate(anyString())).thenReturn(draft("清蒸虾仁"), draft("鸡胸肉"));
        var response = service.plan("demo", "给我一日餐食建议");
        assertEquals(ChatRoute.MEAL_PLAN, response.route());
        assertTrue(response.answer().contains("鸡胸肉"));
        assertFalse(response.answer().contains("虾仁"));
        assertEquals(1, response.sources().size());
        verify(generator, times(2)).generate(anyString());
        verify(generator).generate(contains("清蒸虾仁"));
    }

    @Test void threeViolationsReturnNoDraft() {
        setup();
        when(generator.generate(anyString())).thenReturn(draft("花生酱"));
        var response = service.plan("demo", "给我一日餐食建议");
        assertTrue(response.answer().contains("无法给出经过校验的方案"));
        assertFalse(response.answer().contains("花生酱"));
        assertTrue(response.sources().isEmpty());
        verify(generator, times(3)).generate(anyString());
    }

    @Test void noConfirmedProfileNeverCallsModel() {
        when(profiles.find("demo")).thenReturn(Optional.empty());
        assertTrue(service.plan("demo", "给我一日餐食建议").answer().contains("先为虚构用户保存"));
        verifyNoInteractions(generator);
    }

    @Test void malformedDraftCannotBeDisplayed() {
        setup();
        when(generator.generate(anyString())).thenReturn("普通文字");
        assertTrue(service.plan("demo", "给我一日餐食建议").answer().contains("无法给出"));
        verify(generator, times(3)).generate(anyString());
    }
}
