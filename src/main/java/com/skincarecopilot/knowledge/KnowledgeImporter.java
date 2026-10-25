package com.skincarecopilot.knowledge;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Offline import only: importing a URL never fetches or executes remote content. */
public final class KnowledgeImporter {
    static final int CHUNK_SIZE = 180;
    static final int OVERLAP = 30;

    private KnowledgeImporter() {
    }

    public static List<KnowledgeEntry> read(ObjectMapper json, InputStream input) throws IOException {
        List<KnowledgeEntry> entries = json.readValue(input, new TypeReference<>() {});
        if (entries == null || entries.size() > 100) {
            throw new IllegalArgumentException("知识条目必须是最多 100 项的数组");
        }
        Set<String> ids = new HashSet<>();
        for (KnowledgeEntry entry : entries) {
            if (entry == null || !filled(entry.id()) || !entry.id().matches("[a-z0-9-]{1,80}")
                    || !ids.add(entry.id()) || !filled(entry.title()) || !filled(entry.publisher())
                    || !filled(entry.section()) || !filled(entry.originalExcerpt()) || !filled(entry.text())
                    || entry.text().length() > 10000 || !filled(entry.verifiedOn()) || !filled(entry.url())) {
                throw new IllegalArgumentException("知识条目缺少来源、原文、译文或唯一 ID");
            }
            LocalDate date = LocalDate.parse(entry.verifiedOn());
            if (date.isAfter(LocalDate.now())) {
                throw new IllegalArgumentException("核实日期不能在未来");
            }
            URI uri = URI.create(entry.url());
            String expectedHost = switch (entry.publisher()) {
                case "AAD" -> "www.aad.org";
                case "NICE" -> "www.nice.org.uk";
                default -> throw new IllegalArgumentException("只导入人工核实的 AAD/NICE 条目");
            };
            if (!"https".equals(uri.getScheme()) || !expectedHost.equals(uri.getHost())
                    || uri.getUserInfo() != null || uri.getPort() != -1) {
                throw new IllegalArgumentException("来源必须是对应机构官网的 HTTPS 地址");
            }
        }
        return List.copyOf(entries);
    }

    public static List<KnowledgeChunk> split(List<KnowledgeEntry> entries) {
        List<KnowledgeChunk> chunks = new ArrayList<>();
        for (KnowledgeEntry entry : entries) {
            String text = entry.text();
            int start = 0;
            int index = 0;
            while (start < text.length()) {
                int remaining = text.codePointCount(start, text.length());
                int end = text.offsetByCodePoints(start, Math.min(CHUNK_SIZE, remaining));
                // Prefer a sentence boundary, keeping at least half a chunk before the overlap.
                if (end < text.length()) {
                    int halfway = text.offsetByCodePoints(start, CHUNK_SIZE / 2);
                    for (int i = end - 1; i >= halfway; i--) {
                        if ("。！？；\n".indexOf(text.charAt(i)) >= 0) {
                            end = i + 1;
                            break;
                        }
                    }
                }
                chunks.add(new KnowledgeChunk(entry.id() + ":" + index++, entry.id(), start, end,
                        text.substring(start, end), entry));
                if (end == text.length()) {
                    break;
                }
                start = text.offsetByCodePoints(end, -OVERLAP);
            }
        }
        return List.copyOf(chunks);
    }

    private static boolean filled(String value) {
        return value != null && !value.isBlank();
    }
}
