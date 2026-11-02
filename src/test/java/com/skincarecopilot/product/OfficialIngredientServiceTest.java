package com.skincarecopilot.product;

import java.net.http.HttpClient;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class OfficialIngredientServiceTest {
    private final OfficialIngredientService.Identity identity = new OfficialIngredientService.Identity(
            "虚构品牌 A 面霜", "中国大陆", "2026 配方版");

    @Test void extractsOnlyExplicitFullListWithMatchingIdentity() {
        String html = "<html><head><title>虚构品牌 A 面霜 - 品牌官网</title></head><body>"
                + "<p>中国大陆；2026 配方版</p><h2>全成分</h2>"
                + "<p>水，甘油，烟酰胺，神经酰胺 NP，透明质酸钠</p></body></html>";
        var result = OfficialIngredientService.parse(identity, "https://brand.example/a", html);
        assertTrue(result.isPresent());
        assertEquals(5, result.get().ingredients().size());
        assertEquals("https://brand.example/a", result.get().sourceUrl());
        assertTrue(OfficialIngredientService.parse(new OfficialIngredientService.Identity(
                "虚构品牌 A 面霜", "中国大陆", "2025 配方版"),
                "https://brand.example/a", html).isEmpty());
        assertTrue(OfficialIngredientService.parse(new OfficialIngredientService.Identity(
                "虚构品牌 B 面霜", "中国大陆", "2026 配方版"),
                "https://brand.example/a", html).isEmpty());
    }

    @Test void refusesSearchSummaryOrShortUnlabelledList() {
        String html = "<html><title>虚构品牌 A 面霜</title><body>中国大陆 2026 配方版 "
                + "主要成分：水，甘油，烟酰胺，神经酰胺 NP，透明质酸钠</body></html>";
        assertTrue(OfficialIngredientService.parse(identity, "https://brand.example/a", html).isEmpty());
        assertTrue(OfficialIngredientService.parse(identity, "https://brand.example/a",
                html.replace("主要成分：", "<h2>全成分</h2><p>").replace("</body>", "</p></body>"))
                .isPresent());
    }

    @Test void exactHostAllowlistBlocksLookalikeAndLocalTargets() {
        var service = new OfficialIngredientService(HttpClient.newHttpClient(), "brand.example");
        assertEquals("brand.example", service.checkedUrl("https://brand.example/a").getHost());
        assertThrows(IllegalArgumentException.class,
                () -> service.checkedUrl("https://brand.example.evil.test/a"));
        assertThrows(IllegalArgumentException.class,
                () -> service.checkedUrl("http://brand.example/a"));
        assertThrows(IllegalArgumentException.class,
                () -> service.checkedUrl("https://brand.example@evil.test/a"));
        assertThrows(IllegalArgumentException.class,
                () -> service.checkedUrl("https://localhost/a"));
    }
}
