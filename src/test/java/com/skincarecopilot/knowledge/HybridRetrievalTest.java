package com.skincarecopilot.knowledge;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class HybridRetrievalTest {
    @Test void expandsAndFusesKeywordAndVectorRanks() {
        assertTrue(HybridRetrieval.expand("脸颊泛红").contains("红斑"));
        var a = chunk("a", "皮肤泛红和红斑需要识别诱因");
        var b = chunk("b", "保湿产品帮助皮肤屏障");
        var lexical = HybridRetrieval.bm25("泛红 红斑", List.of(a,b), 2);
        assertEquals("a", lexical.get(0).chunk().id());
        var semantic = List.of(new KnowledgeService.SearchHit(.9,b),new KnowledgeService.SearchHit(.8,a));
        var fused = HybridRetrieval.fuse(lexical,semantic, 2);
        assertEquals("a", fused.get(0).chunk().id());
        assertTrue(HybridRetrieval.bm25("飞船发动机", List.of(a,b), 2).isEmpty());
    }
    private KnowledgeChunk chunk(String id, String text) {
        var source = new KnowledgeEntry(id,"AAD",id,"https://www.aad.org", "s", "2026-01-01", "excerpt", text);
        return new KnowledgeChunk(id,id,0,text.length(),text,source);
    }
}
