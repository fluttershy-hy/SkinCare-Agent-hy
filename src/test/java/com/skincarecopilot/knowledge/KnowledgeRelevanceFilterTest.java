package com.skincarecopilot.knowledge;

import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class KnowledgeRelevanceFilterTest {
    @Test void dropsLabAndCleansingResultsFromBarrierQuestion() {
        var filter = new KnowledgeRelevanceFilter();
        var barrier = hit("book-skin-barrier");
        var lab = hit("malassezia-lipids-keratinocytes-2024");
        var cleansing = hit("book-binghan-cleansing-sebum");
        assertEquals(List.of(barrier), filter.relevant("皮肤屏障角质层脂质与干燥有什么关系",
                List.of(barrier, lab, cleansing)));
    }
    private KnowledgeService.SearchHit hit(String id) {
        var entry = new KnowledgeEntry(id, "Book", "测试", "book:9787122323750",
                "章节", "2026-11-17", "短摘录", "合成测试文本");
        return new KnowledgeService.SearchHit(.9,
                new KnowledgeChunk(id+":0", id, 0, entry.text().length(), entry.text(), entry));
    }
}
