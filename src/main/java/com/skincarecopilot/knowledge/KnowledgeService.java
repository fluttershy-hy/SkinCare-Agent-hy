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
    private final java.util.concurrent.ConcurrentMap<String, float[]> bookVectors = new java.util.concurrent.ConcurrentHashMap<>();
    private final BookPageSearch bookPages;
    private final InMemoryEmbeddingStore<KnowledgeChunk> store = new InMemoryEmbeddingStore<>();
    private final List<KnowledgeEntry> entries;
    private final int chunkCount;

    @Autowired
    public KnowledgeService(ObjectMapper json) throws IOException {
        this(loadEntries(json), new BgeSmallZhV15QuantizedEmbeddingModel(Runnable::run), new BookPageSearch(java.nio.file.Path.of(System.getenv().getOrDefault("SKINCARE_BOOK_TEXT_DIR", "data/book-text"))));
    }

    KnowledgeService(List<KnowledgeEntry> entries, EmbeddingModel model) {
        this(entries, model, new BookPageSearch(java.nio.file.Path.of("/nonexistent-skincare-test-data")));
    }

    private KnowledgeService(List<KnowledgeEntry> entries, EmbeddingModel model, BookPageSearch bookPages) {
        this.bookPages = bookPages;
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
            var web = KnowledgeImporter.read(json, input);
            try (InputStream books = new ClassPathResource("knowledge/books.json").getInputStream()) {
                var combined = new java.util.ArrayList<>(web);
                combined.addAll(KnowledgeImporter.read(json, books));
                if (combined.stream().map(KnowledgeEntry::id).distinct().count() != combined.size())
                    throw new IllegalArgumentException("网页和书籍知识条目 ID 重复");
                return List.copyOf(combined);
            }
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
        var results = new java.util.ArrayList<SearchHit>();
        if (!matches.isEmpty()) results.add(new SearchHit(matches.get(0).score(), matches.get(0).embedded()));
        results.addAll(rankBookPages(normalized, bookPages.search(normalized, 20), Math.min(2, Math.max(0, topK - results.size()))));
        results.addAll(matches.stream().skip(1).map(match -> new SearchHit(match.score(), match.embedded())).toList());
        return new SearchResult(normalized, MODEL, MIN_SCORE, results.stream().limit(topK).toList());
    }

    public List<SearchHit> acneCare() {
        return entries.stream().filter(e -> e.id().equals("aad-acne-gentle-skin-care"))
                .map(e -> new SearchHit(1.0, KnowledgeImporter.split(List.of(e)).get(0))).toList();
    }

    public List<SearchHit> searchEndocrinology(String query, int limit) {
        String expanded = query;
        if (query.matches(".*(痤疮|长痘|出油|月经|经期).*")) expanded = "雄激素 皮脂腺 皮脂分泌 痤疮";
        var results = new java.util.ArrayList<SearchHit>();
        if (query.matches(".*(痤疮|长痘|出油|月经|经期).*")) {
            entries.stream().filter(e -> e.id().equals("book-williams-androgen-sebum"))
                    .findFirst().ifPresent(e -> results.add(new SearchHit(1.0, KnowledgeImporter.split(List.of(e)).get(0))));
        }
        results.addAll(rankBookPages(expanded, bookPages.search(expanded, 30, "williams-endocrinology-14"), Math.max(0, limit - results.size()), 0.68));
        return results.stream().limit(limit).toList();
    }

    private List<SearchHit> rankBookPages(String query, List<SearchHit> candidates, int limit) {
        return rankBookPages(query, candidates, limit, MIN_SCORE);
    }

    private List<SearchHit> rankBookPages(String query, List<SearchHit> candidates, int limit, double minimumScore) {
        if (limit <= 0 || candidates.isEmpty()) return List.of();
        float[] q = model.embed("为这个句子生成表示以用于检索相关文章：" + query).content().vector();
        var ranked = new java.util.ArrayList<SearchHit>();
        for (var candidate : candidates) {
            var chunk = candidate.chunk();
            String key = chunk.id() + ":" + chunk.start() + ":" + chunk.text().hashCode();
            float[] vector = bookVectors.computeIfAbsent(key, unused -> model.embed(chunk.text()).content().vector());
            double dot = 0, qnorm = 0, vnorm = 0;
            for (int i = 0; i < q.length && i < vector.length; i++) {
                dot += q[i] * vector[i]; qnorm += q[i] * q[i]; vnorm += vector[i] * vector[i];
            }
            double score = qnorm == 0 || vnorm == 0 ? 0 : dot / Math.sqrt(qnorm * vnorm);
            if (score >= minimumScore) ranked.add(new SearchHit(score, chunk));
        }
        ranked.sort(java.util.Comparator.comparingDouble(SearchHit::score).reversed());
        return ranked.stream().limit(limit).toList();
    }

    public record Catalog(String model, int dimensions, int chunkSize, int overlap, double minScore,
                          int entryCount, int chunkCount, List<KnowledgeEntry> entries) {
    }

    public record SearchHit(double score, KnowledgeChunk chunk) {
    }

    public record SearchResult(String query, String model, double minScore, List<SearchHit> matches) {
    }
}
