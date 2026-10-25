package com.skincarecopilot.knowledge;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class KnowledgeImporterTest {
    private final ObjectMapper json = new ObjectMapper();

    static KnowledgeEntry fixture(String id, String text) {
        // Synthetic data for pipeline tests; not AAD content and never imported into the application.
        return new KnowledgeEntry(id, "AAD", "测试夹具（并非官网资料）", "https://www.aad.org/",
                "合成测试", "2026-01-01", "Synthetic fixture, not medical guidance.", text);
    }

    private List<KnowledgeEntry> read(List<KnowledgeEntry> entries) throws Exception {
        return KnowledgeImporter.read(json, new ByteArrayInputStream(json.writeValueAsBytes(entries)));
    }

    @Test
    void importsReviewedPassageWithoutLosingMetadata() throws Exception {
        var entry = fixture("fixture", "合成条目，用于检验元数据传递。");
        assertEquals(List.of(entry), read(List.of(entry)));
    }

    @Test
    void refusesDuplicateIdsAndUntrustedOrMismatchedSourceUrls() {
        var entry = fixture("fixture", "合成条目");
        assertThrows(IllegalArgumentException.class, () -> read(List.of(entry, entry)));
        for (String url : List.of("http://www.aad.org/", "https://www.aad.org.evil.example/",
                "https://www.nice.org.uk/", "https://user@www.aad.org/", "javascript:alert(1)")) {
            var bad = new KnowledgeEntry(entry.id(), entry.publisher(), entry.title(), url,
                    entry.section(), entry.verifiedOn(), entry.originalExcerpt(), entry.text());
            assertThrows(IllegalArgumentException.class, () -> read(List.of(bad)), url);
        }
    }

    @Test
    void refusesPassagesWithoutOriginalEvidence() {
        var bad = new KnowledgeEntry("fixture", "AAD", "测试", "https://www.aad.org/", "章节",
                "2026-01-01", "", "译文");
        assertThrows(IllegalArgumentException.class, () -> read(List.of(bad)));
    }

    @Test
    void emptyCatalogIsAllowedButNotSilentlyPopulatedWithUnverifiedText() throws Exception {
        assertTrue(KnowledgeImporter.read(json,
                new ByteArrayInputStream("[]".getBytes(StandardCharsets.UTF_8))).isEmpty());
    }

    @Test
    void chunksLongTextWithOverlapStableIdsExactOffsetsAndNoLostCharacters() {
        String text = "演示卡片使用蓝色边框。".repeat(55);
        var chunks = KnowledgeImporter.split(List.of(fixture("fixture", text)));
        assertTrue(chunks.size() > 1);
        StringBuilder reconstructed = new StringBuilder();
        int previousEnd = 0;
        for (int i = 0; i < chunks.size(); i++) {
            var chunk = chunks.get(i);
            assertEquals("fixture:" + i, chunk.id());
            assertEquals(text.substring(chunk.start(), chunk.end()), chunk.text());
            assertTrue(chunk.text().codePointCount(0, chunk.text().length()) <= KnowledgeImporter.CHUNK_SIZE);
            if (i > 0) {
                assertEquals(KnowledgeImporter.OVERLAP, text.codePointCount(chunk.start(), previousEnd));
            }
            reconstructed.append(text, previousEnd, chunk.end());
            previousEnd = chunk.end();
        }
        assertEquals(text, reconstructed.toString());
        assertEquals(chunks, KnowledgeImporter.split(List.of(fixture("fixture", text))));
    }

    @Test
    void neverSplitsUnicodeSurrogatePairsOrMergesDifferentSources() {
        String text = "😀".repeat(400);
        var chunks = KnowledgeImporter.split(List.of(fixture("one", text), fixture("two", "第二个来源")));
        for (var chunk : chunks) {
            assertFalse(Character.isLowSurrogate(chunk.text().charAt(0)));
            assertFalse(Character.isHighSurrogate(chunk.text().charAt(chunk.text().length() - 1)));
            assertEquals(chunk.entryId(), chunk.source().id());
        }
        assertEquals("two:0", chunks.get(chunks.size() - 1).id());
    }
}
