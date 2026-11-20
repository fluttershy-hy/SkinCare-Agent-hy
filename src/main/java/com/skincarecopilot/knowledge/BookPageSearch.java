package com.skincarecopilot.knowledge;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Stream;

/** 检索本地 OCR 页面，页码指分卷 PDF 内页，不冒充纸书页码。 */
final class BookPageSearch {
    private final Path root;
    private volatile Map<Path, String> pages = Map.of();
    private volatile long refreshedAt;
    BookPageSearch(Path root) { this.root = root; }

    private synchronized Map<Path, String> pages() {
        if (System.currentTimeMillis() - refreshedAt < 30000) return pages;
        Map<Path, String> loaded = new HashMap<>();
        try (Stream<Path> files = Files.walk(root)) {
            files.filter(p -> p.toString().endsWith(".txt")).forEach(p -> {
                try { loaded.put(p, Files.readString(p).replaceAll("\\s+", " ").strip()); }
                catch (IOException ignored) { }
            });
            pages = Map.copyOf(loaded);
            refreshedAt = System.currentTimeMillis();
        } catch (IOException ignored) { }
        return pages;
    }

    static KnowledgeChunk chunk(Path root, Path p, String text, int start, int end) {
        String rel = root.relativize(p).toString().replace('\\', '/');
        String id = "ocr-" + Integer.toUnsignedString(rel.hashCode());
        String[] path = rel.split("/");
        String title = switch (path[0]) {
            case "williams-endocrinology-14" -> "Williams 内分泌学（第14版）";
            case "acne-and-rosacea-4" -> "痤疮与玫瑰痤疮（第4版）";
            case "practical-cosmetic-dermatology" -> "实用美容皮肤科学";
            case "precise-whitening" -> "走近精准美白";
            case "complete-skin-care" -> "护肤全书";
            case "beauty-king-2" -> "美容大王 2";
            default -> path[0].startsWith("epub-") ? path[0].substring(5) : path[0];
        };
        String location;
        if (path[0].startsWith("epub-")) {
            String encoded = path[path.length - 1].replaceFirst("\\.txt$", "");
            location = "EPUB 章节 " + new String(Base64.getUrlDecoder().decode(encoded), java.nio.charset.StandardCharsets.UTF_8);
        } else {
            location = "分卷 " + path[1] + "，PDF 内第 " + Integer.parseInt(path[path.length-1].replace(".txt", "")) + " 页（OCR）";
        }
        String part = text.substring(start, end);
        var source = new KnowledgeEntry(id, "Book", title, "book:local", location, "2026-11-19", "本地书籍片段", part, "BOOK", null, null, null, null, rel);
        return new KnowledgeChunk(id + ":" + start, id, start, end, part, source);
    }

    List<KnowledgeService.SearchHit> search(String query, int limit) {
        return search(query, limit, null);
    }

    List<KnowledgeService.SearchHit> search(String query, int limit, String collection) {
        if (!Files.isDirectory(root)) return List.of();
        var terms = new HashSet<String>();
        String compact = query.replaceAll("[^\\p{IsHan}a-zA-Z0-9]", "").toLowerCase(Locale.ROOT);
        for (int i = 0; i + 2 <= compact.length(); i++) terms.add(compact.substring(i, i + 2));
        if (terms.isEmpty()) return List.of();
        var results = new ArrayList<KnowledgeService.SearchHit>();
        pages().forEach((p, text) -> {
                try {
                    if (collection != null && !root.relativize(p).getName(0).toString().equals(collection)) return;
                    if (text.length() < 60) return;
                    int best = 0, bestStart = 0;
                    for (int start = 0; start < text.length(); start += 160) {
                        String part = text.substring(start, Math.min(text.length(), start + 220));
                        int score = 0;
                        for (String term : terms) if (part.contains(term)) score++;
                        if (score > best) { best = score; bestStart = start; }
                    }
                    if (best < 2) return;
                    KnowledgeChunk chunk = chunk(root, p, text, bestStart, Math.min(text.length(), bestStart + 220));
                    results.add(new KnowledgeService.SearchHit((double) best / terms.size(), chunk));
                } catch (RuntimeException ignored) { }
            });
        results.sort(Comparator.comparingDouble(KnowledgeService.SearchHit::score).reversed());
        return results.stream().limit(limit).toList();
    }
}
