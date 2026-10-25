package com.skincarecopilot.knowledge;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.embedding.onnx.bgesmallzhv15q.BgeSmallZhV15QuantizedEmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.inmemory.InMemoryEmbeddingStore;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

@Service
public class KnowledgeService {
    public static final String MODEL = "bge-small-zh-v1.5-q";
    public static final double MIN_SCORE = 0.75;
    private final EmbeddingModel model;
    private final InMemoryEmbeddingStore<KnowledgeChunk> store = new InMemoryEmbeddingStore<>();
    private final List<KnowledgeEntry> entries;
    private final int chunkCount;

    @Autowired
    public KnowledgeService(ObjectMapper json) throws IOException {
        this(loadEntries(json), new BgeSmallZhV15QuantizedEmbeddingModel(Runnable::run));
    }

    KnowledgeService(List<KnowledgeEntry> entries, EmbeddingModel model) {
        this.entries = List.copyOf(entries);
        this.model = model;
        List<KnowledgeChunk> chunks = KnowledgeImporter.split(entries);
        // Small curated corpus: serial embedding bounds CPU use, with no remote API or chat key.
        for (KnowledgeChunk chunk : chunks) {
            store.add(chunk.id(), model.embed(chunk.text()).content(), chunk);
        }
        this.chunkCount = chunks.size();
    }

    private static List<KnowledgeEntry> loadEntries(ObjectMapper json) throws IOException {
        try (InputStream input = new ClassPathResource("knowledge/entries.json").getInputStream()) {
            return KnowledgeImporter.read(json, input);
        }
    }

    public Catalog catalog() {
        return new Catalog(MODEL, 512, KnowledgeImporter.CHUNK_SIZE, KnowledgeImporter.OVERLAP,
                MIN_SCORE, entries.size(), chunkCount, entries);
    }

    public SearchResult search(String query, int topK) {
        if (query == null || query.isBlank() || query.length() > 500) {
            throw new IllegalArgumentException("检索内容需为 1～500 字符");
        }
        if (topK < 1 || topK > 5) {
            throw new IllegalArgumentException("topK 必须为 1～5");
        }
        String normalized = query.strip();
        if (chunkCount == 0) {
            return new SearchResult(normalized, MODEL, MIN_SCORE, List.of());
        }
        var embedding = model.embed("为这个句子生成表示以用于检索相关文章：" + normalized).content();
        var matches = store.search(EmbeddingSearchRequest.builder().queryEmbedding(embedding)
                .maxResults(topK).minScore(MIN_SCORE).build()).matches();
        return new SearchResult(normalized, MODEL, MIN_SCORE, matches.stream()
                .map(match -> new SearchHit(match.score(), match.embedded())).toList());
    }

    public record Catalog(String model, int dimensions, int chunkSize, int overlap, double minScore,
                          int entryCount, int chunkCount, List<KnowledgeEntry> entries) {
    }

    public record SearchHit(double score, KnowledgeChunk chunk) {
    }

    public record SearchResult(String query, String model, double minScore, List<SearchHit> matches) {
    }
}
