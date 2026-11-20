package com.skincarecopilot.knowledge;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.embedding.onnx.bgesmallzhv15q.BgeSmallZhV15QuantizedEmbeddingModel;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.stream.Stream;

/** 每页独立持久化，生成中断后按文本哈希续跑；源文件变更后自动重算。 */
final class BookVectorIndex {
    static final int SIZE = 220, STEP = 180;
    private final Path texts, vectors;
    private final ObjectMapper json = new ObjectMapper();
    private volatile List<Indexed> cache = List.of();
    private volatile long refreshedAt;
    record Part(int start, int end, float[] vector) { }
    record Page(String relative, String sha256, List<Part> parts) { }
    record Indexed(KnowledgeChunk chunk, float[] vector, String collection) { }

    BookVectorIndex(Path texts, Path vectors) { this.texts = texts; this.vectors = vectors; }
    static String hash(String text) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8)));
    }
    static String normalized(Path p) throws IOException { return Files.readString(p).replaceAll("\\s+", " ").strip(); }
    Path vectorFile(String relative) { return vectors.resolve(relative + ".json"); }

    void build(EmbeddingModel model) throws Exception {
        if (!Files.isDirectory(texts)) throw new IOException("书籍文字目录不存在: " + texts);
        int done = 0, skipped = 0;
        try (Stream<Path> stream = Files.walk(texts)) {
            for (Path p : stream.filter(Files::isRegularFile).filter(x -> x.toString().endsWith(".txt")).sorted().toList()) {
                String relative = texts.relativize(p).toString().replace('\\', '/');
                String text = normalized(p), digest = hash(text);
                Path target = vectorFile(relative);
                if (Files.isRegularFile(target)) {
                    try { if (json.readValue(target.toFile(), Page.class).sha256().equals(digest)) { skipped++; continue; } }
                    catch (Exception ignored) { }
                }
                var parts = new ArrayList<Part>();
                for (int start = 0; start < text.length(); start += STEP) {
                    int end = Math.min(text.length(), start + SIZE);
                    if (end - start < 30) break;
                    parts.add(new Part(start, end, model.embed(text.substring(start, end)).content().vector()));
                    if (end == text.length()) break;
                }
                Files.createDirectories(target.getParent());
                Path tmp = target.resolveSibling(target.getFileName() + ".tmp");
                json.writeValue(tmp.toFile(), new Page(relative, digest, parts));
                try { Files.move(tmp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
                catch (AtomicMoveNotSupportedException e) { Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING); }
                done++;
                if (done % 20 == 0) System.out.println("已向量化文件=" + done + "，跳过未变化=" + skipped + "，最近=" + relative);
            }
        }
        System.out.println("完成：新建或更新=" + done + "，跳过未变化=" + skipped);
    }

    private synchronized List<Indexed> load() {
        if (System.currentTimeMillis() - refreshedAt < 30000) return cache;
        if (!Files.isDirectory(vectors)) return List.of();
        var result = new ArrayList<Indexed>();
        try (Stream<Path> stream = Files.walk(vectors)) {
            for (Path p : stream.filter(x -> x.toString().endsWith(".txt.json")).sorted().toList()) {
                try {
                    Page page = json.readValue(p.toFile(), Page.class);
                    Path source = texts.resolve(page.relative()).normalize();
                    if (!source.startsWith(texts.normalize()) || !Files.isRegularFile(source)) continue;
                    String text = normalized(source);
                    if (!hash(text).equals(page.sha256())) continue;
                    for (Part part : page.parts()) {
                        if (part.end() > text.length() || part.vector().length != 512) continue;
                        var chunk = BookPageSearch.chunk(texts, source, text, part.start(), part.end());
                        result.add(new Indexed(chunk, part.vector(), texts.relativize(source).getName(0).toString()));
                    }
                } catch (Exception ignored) { }
            }
            cache = List.copyOf(result); refreshedAt = System.currentTimeMillis();
        } catch (IOException ignored) { }
        return cache;
    }
    List<KnowledgeService.SearchHit> search(float[] query, int limit, String collection, double minScore) {
        var matches = new PriorityQueue<KnowledgeService.SearchHit>(Comparator.comparingDouble(KnowledgeService.SearchHit::score));
        for (Indexed item : load()) {
            if (collection != null && !collection.equals(item.collection())) continue;
            float[] v = item.vector(); double dot = 0, a = 0, b = 0;
            for (int i = 0; i < query.length && i < v.length; i++) { dot += query[i]*v[i]; a += query[i]*query[i]; b += v[i]*v[i]; }
            double score = a == 0 || b == 0 ? 0 : dot / Math.sqrt(a*b);
            if (score < minScore) continue;
            matches.add(new KnowledgeService.SearchHit(score, item.chunk()));
            if (matches.size() > limit) matches.poll();
        }
        var result = new ArrayList<>(matches); result.sort(Comparator.comparingDouble(KnowledgeService.SearchHit::score).reversed());
        return result;
    }
    public static void main(String[] args) throws Exception {
        Path text = Path.of(args.length > 0 ? args[0] : "data/book-text");
        Path vectors = Path.of(args.length > 1 ? args[1] : "data/book-vectors");
        new BookVectorIndex(text, vectors).build(new BgeSmallZhV15QuantizedEmbeddingModel(Runnable::run));
    }
}
