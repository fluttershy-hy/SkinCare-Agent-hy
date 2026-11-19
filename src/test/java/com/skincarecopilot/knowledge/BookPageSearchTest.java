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
}
