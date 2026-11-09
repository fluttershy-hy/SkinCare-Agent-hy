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

    @Test void publicHttpsPagesNeedNoBrandConfigurationButLocalTargetsAreBlocked() {
        var service = new OfficialIngredientService(HttpClient.newHttpClient());
        assertEquals("brand.example", service.checkedUrl("https://brand.example/a").getHost());
        assertEquals("brand.example.evil.test",
                service.checkedUrl("https://brand.example.evil.test/a").getHost());
        assertThrows(IllegalArgumentException.class,
                () -> service.checkedUrl("http://brand.example/a"));
        assertThrows(IllegalArgumentException.class,
                () -> service.checkedUrl("https://brand.example@evil.test/a"));
        assertThrows(IllegalArgumentException.class,
                () -> service.checkedUrl("https://localhost/a"));
    }

    @Test void usMarketAndSkuCanBeCheckedWithoutCallingSkuAFormulaRevision() {
        var us = new OfficialIngredientService.Identity("Moisturizing Cream", "US", "3606000537729");
        String html = "<html lang='en-US'><head><title>Moisturizing Cream | Brand</title></head><body>"
                + "<div data-tag-product-id='3606000537729'></div>"
                + "<details><summary data-open='Close Full Ingredient List'></summary>"
                + "<div class='keyIngredients-details__content'><p>WATER, GLYCERIN, CETEARYL ALCOHOL, "
                + "PETROLATUM, CERAMIDE NP</p><p>Ingredient lists may change.</p></div></details></body></html>";
        var result = OfficialIngredientService.parse(us, "https://brand.example/cream", html);
        assertTrue(result.isPresent());
        assertEquals(5, result.get().ingredients().size());
        assertTrue(result.get().sourceNote().contains("来源身份"));
        assertTrue(OfficialIngredientService.parse(new OfficialIngredientService.Identity(
                "Moisturizing Cream", "US", "other-sku"), "https://brand.example/cream", html).isEmpty());
    }

    @Test void productNameAloneCanParseClearlyLabelledList() {
        String html = "<html><title>虚构面霜 A</title><body><h2>全成分</h2>"
                + "<p>水，甘油，烟酰胺，神经酰胺 NP，透明质酸钠</p></body></html>";
        assertEquals(5, OfficialIngredientService.parse(
                new OfficialIngredientService.Identity("虚构面霜 A", null, null),
                "https://brand.example/a", html).orElseThrow().ingredients().size());
    }

    @Test void extractsStandaloneIngredientsPanelAsUnverifiedCandidate() {
        String html = "<html><title>Vanicream™ Moisturizing Cream</title><body>"
                + "<button aria-controls='panel-ingredients'>Ingredients</button>"
                + "<div id='panel-ingredients'><p>water, petrolatum, sorbitol, cetearyl alcohol, "
                + "propylene glycol, sorbic acid</p><p>Ingredient Information</p></div></body></html>";
        var result = OfficialIngredientService.parse(new OfficialIngredientService.Identity(
                "Vanicream Moisturizing Cream", null, null), "https://example.com/product", html).orElseThrow();
        assertEquals(6, result.ingredients().size());
        assertEquals("sorbic acid", result.ingredients().get(5));
        assertTrue(result.sourceNote().contains("完整性"));
    }
}
