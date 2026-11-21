package com.skincarecopilot.knowledge;

import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class LocalRerankerTest {
    @Test void rejectsIrrelevantPassageWhenModelIsConfigured() {
        String model = System.getenv().getOrDefault("SKINCARE_RERANK_MODEL", "");
        String tokenizer = System.getenv().getOrDefault("SKINCARE_RERANK_TOKENIZER", "");
        assumeTrue(!model.isBlank() && !tokenizer.isBlank(), "未配置本地重排模型");
        var relevant = hit("a", "敏感皮肤可能出现泛红刺痛，使用温和无香料护肤品减少刺激。");
        var unrelated = hit("b", "火箭发动机产生推力，飞船进入太空轨道。");
        try (var reranker = new LocalReranker(model, tokenizer)) {
            var hits = reranker.rerank("泛红刺痛应该怎样护理", List.of(unrelated, relevant), 3, .1);
            assertEquals(1, hits.size());
            assertEquals("a", hits.get(0).chunk().entryId());
        }
    }
    private KnowledgeService.SearchHit hit(String id, String text) {
        var source = new KnowledgeEntry(id,"AAD",id,"https://www.aad.org","s","2026-01-01","excerpt",text);
        return new KnowledgeService.SearchHit(0, new KnowledgeChunk(id,id,0,text.length(),text,source));
    }
}
