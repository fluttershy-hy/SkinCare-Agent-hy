package com.skincarecopilot.knowledge;

import dev.langchain4j.model.embedding.onnx.bgesmallzhv15q.BgeSmallZhV15QuantizedEmbeddingModel;
import java.nio.file.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class BookVectorIndexTest {
    @TempDir Path root;
    @Test void indexSurvivesRestartAndRejectsStalePages() throws Exception {
        Path texts = root.resolve("texts"), vectors = root.resolve("vectors");
        Path page = texts.resolve("williams-endocrinology-14/volume-01/0005.txt");
        Files.createDirectories(page.getParent());
        Files.writeString(page, "雄激素会影响皮脂腺，皮脂分泌与皮肤状态有关。".repeat(12));
        var model = new BgeSmallZhV15QuantizedEmbeddingModel(Runnable::run);
        new BookVectorIndex(texts, vectors).build(model);
        var query = model.embed("为这个句子生成表示以用于检索相关文章：雄激素和皮脂腺").content().vector();
        var loaded = new BookVectorIndex(texts, vectors);
        var hits = loaded.search(query, 2, "williams-endocrinology-14", 0.0);
        assertFalse(hits.isEmpty());
        assertTrue(hits.get(0).chunk().source().section().contains("5 页"));
        assertTrue(loaded.search(query, 2, "another-book", 0.0).isEmpty());
        Files.writeString(page, "内容已经修改，需要重新生成向量。".repeat(12));
        assertTrue(new BookVectorIndex(texts, vectors).search(query, 2, null, 0.0).isEmpty());
        new BookVectorIndex(texts, vectors).build(model);
        assertFalse(new BookVectorIndex(texts, vectors).search(query, 2, null, 0.0).isEmpty());
    }
}
