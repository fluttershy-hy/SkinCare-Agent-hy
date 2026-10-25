package com.skincarecopilot.knowledge;

import dev.langchain4j.model.embedding.onnx.bgesmallzhv15q.BgeSmallZhV15QuantizedEmbeddingModel;
import java.util.List;
import org.junit.jupiter.api.Test;

import static com.skincarecopilot.knowledge.KnowledgeImporterTest.fixture;
import static org.junit.jupiter.api.Assertions.*;

class KnowledgeSearchTest {
    @Test
    void realLocalChineseEmbeddingRanksRelevantPassageAndPreservesEvidence() {
        var model = new BgeSmallZhV15QuantizedEmbeddingModel(Runnable::run);
        assertEquals(512, model.embed("中文检索测试").content().dimension());
        var cards = fixture("cards", "界面演示卡片的边框使用蓝色，卡片标题放在顶部。背景颜色使用白色。");
        var trains = fixture("trains", "虚构列车时刻表列出火车到站时间和发车时间，旅客可以查询车次。");
        var service = new KnowledgeService(List.of(cards, trains), model);

        var result = service.search("演示卡片的蓝色边框和标题", 2);
        assertFalse(result.matches().isEmpty());
        assertEquals("cards", result.matches().get(0).chunk().entryId());
        assertEquals(cards, result.matches().get(0).chunk().source());
        assertEquals(cards.text(), result.matches().get(0).chunk().text());
        assertTrue(result.matches().stream().allMatch(hit -> hit.score() >= KnowledgeService.MIN_SCORE));
        for (int i = 1; i < result.matches().size(); i++) {
            assertTrue(result.matches().get(i - 1).score() >= result.matches().get(i).score());
        }
        assertEquals(1, service.search("火车到站时间和发车时间", 1).matches().size());
        assertEquals("trains", service.search("火车到站时间和发车时间", 1).matches().get(0).chunk().entryId());
        assertEquals(2, service.catalog().entryCount());
        assertEquals(2, service.catalog().chunkCount());
    }
}
