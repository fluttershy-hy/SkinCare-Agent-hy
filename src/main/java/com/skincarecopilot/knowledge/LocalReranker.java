package com.skincarecopilot.knowledge;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.scoring.onnx.OnnxScoringModel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/** 可选的本地 BGE 交叉编码器；未配置模型文件时由调用方使用本地向量重排。 */
final class LocalReranker implements AutoCloseable {
    private final OnnxScoringModel model;
    LocalReranker(String modelPath, String tokenizerPath) { this(modelPath, tokenizerPath, false); }
    LocalReranker(String modelPath, String tokenizerPath, boolean autoDiscover) {
        if (modelPath.isBlank() && tokenizerPath.isBlank()) {
            if (!autoDiscover) { model = null; return; }
            Path defaultModel = Path.of("data/reranker/model.onnx");
            Path defaultTokenizer = Path.of("data/reranker/tokenizer.json");
            if (!Files.isRegularFile(defaultModel) || !Files.isRegularFile(defaultTokenizer)) { model = null; return; }
            modelPath = defaultModel.toString(); tokenizerPath = defaultTokenizer.toString();
        }
        if (modelPath.isBlank() || tokenizerPath.isBlank() || !Files.isRegularFile(Path.of(modelPath)) || !Files.isRegularFile(Path.of(tokenizerPath)))
            throw new IllegalArgumentException("重排模型和 tokenizer 必须同时配置为有效文件");
        model = new OnnxScoringModel(modelPath, tokenizerPath);
    }
    boolean available() { return model != null; }
    List<KnowledgeService.SearchHit> rerank(String query, List<KnowledgeService.SearchHit> candidates, int limit, double threshold) {
        if (model == null || candidates.isEmpty()) return List.of();
        var segments = candidates.stream().map(x -> TextSegment.from(x.chunk().text())).toList();
        var scores = model.scoreAll(segments, query).content();
        var hits = new ArrayList<KnowledgeService.SearchHit>();
        for (int i=0;i<scores.size();i++) {
            double value = 1.0 / (1.0 + Math.exp(-scores.get(i)));
            if (Double.isFinite(value) && value >= threshold) hits.add(new KnowledgeService.SearchHit(value, candidates.get(i).chunk()));
        }
        hits.sort(Comparator.comparingDouble(KnowledgeService.SearchHit::score).reversed());
        return hits.stream().limit(limit).toList();
    }
    @Override public void close() { if (model != null) model.close(); }
}
