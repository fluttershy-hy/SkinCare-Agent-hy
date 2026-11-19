package com.skincarecopilot.knowledge;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import static org.junit.jupiter.api.Assertions.*;

class BookPageSearchTest {
    @TempDir Path root;
    @Test void findsPageAndRetainsVolumeAndPage() throws Exception {
        Path volume = root.resolve("williams-endocrinology-14/volume-01");
        Files.createDirectories(volume);
        Files.writeString(volume.resolve("0023.txt"), "痤疮和雄激素及皮脂腺功能之间存在关系。".repeat(8));
        var hits = new BookPageSearch(root).search("雄激素和皮脂腺", 2);
        assertEquals(1, hits.size());
        assertTrue(hits.get(0).chunk().source().title().contains("内分泌学"));
        assertTrue(hits.get(0).chunk().source().section().contains("23 页"));
        assertTrue(new BookPageSearch(root).search("维生素缺乏", 2).isEmpty());
    }

    @Test void findsEpubChapterAndLimitsEndocrineSearchToItsCollection() throws Exception {
        Path epub = root.resolve("epub-护肤书/章节");
        Path endocrine = root.resolve("williams-endocrinology-14/volume-01");
        Files.createDirectories(epub);
        Files.createDirectories(endocrine);
        String chapter = java.util.Base64.getUrlEncoder().withoutPadding().encodeToString("OEBPS/chapter1.xhtml".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        Files.writeString(epub.resolve(chapter + ".txt"), "激素影响皮脂腺，皮肤护理需要温和清洁。".repeat(8));
        Files.writeString(endocrine.resolve("0005.txt"), "激素影响皮脂腺，内分泌变化具有多种原因。".repeat(8));
        var search = new BookPageSearch(root);
        var all = search.search("激素影响皮脂腺", 5);
        assertTrue(all.stream().anyMatch(hit -> hit.chunk().source().section().contains("OEBPS/chapter1.xhtml")));
        var filtered = search.search("激素影响皮脂腺", 5, "williams-endocrinology-14");
        assertEquals(1, filtered.size());
        assertTrue(filtered.get(0).chunk().source().title().contains("内分泌学"));
    }
}
