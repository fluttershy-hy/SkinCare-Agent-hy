package com.skincarecopilot.product;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class OfficialProductCatalogTest {
    @Test void knownProductNeedsNoUserConfiguredHostOrSku() {
        var catalog = new OfficialProductCatalog();
        var entry = catalog.find("CeraVe Moisturizing Cream").orElseThrow();
        assertEquals("www.cerave.com", java.net.URI.create(entry.url()).getHost());
        assertEquals("US", entry.identity().market());
        assertEquals("3606000537729", entry.identity().version());
        assertTrue(catalog.approvedHosts().contains("www.cerave.com"));
        assertTrue(catalog.find("虚构品牌面霜").isEmpty());
    }
}
