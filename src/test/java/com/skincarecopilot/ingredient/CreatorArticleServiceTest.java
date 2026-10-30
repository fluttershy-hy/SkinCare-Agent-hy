package com.skincarecopilot.ingredient;

import static org.junit.jupiter.api.Assertions.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class CreatorArticleServiceTest {
    @Test
    void importsOnlyCuratedQuestionMetadataAndFindsRelevantReading() throws Exception {
        var service = new CreatorArticleService(new ObjectMapper());
        assertEquals(14, service.catalog().size());
        assertEquals("miriam-ingredient", service.related("烟酰胺排序靠后是否没用").get(0).id());
        assertEquals("miriam-comedones-ingredients-in-skin-care", service.related("致粉刺评分可信吗").get(0).id());
        assertTrue(service.related("今天下雨吗").isEmpty());
        assertTrue(service.catalog().stream().allMatch(a -> a.url().startsWith("https://miriammibao.com/")));
    }
}
