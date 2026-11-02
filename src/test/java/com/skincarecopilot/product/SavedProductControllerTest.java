package com.skincarecopilot.product;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {"SKINCARE_LLM_API_KEY=", "spring.datasource.url=jdbc:h2:mem:saved-product-test;DB_CLOSE_DELAY=-1"})
@AutoConfigureMockMvc
class SavedProductControllerTest {
    @Autowired MockMvc mvc;
    @Autowired ProductIngredientStore store;

    @Test
    void listsOnlyOwnersProductsAndAllowsRemoval() throws Exception {
        store.saveUserConfirmed("owner-demo", "虚构面霜 A", "水、甘油");
        store.saveUserConfirmed("other-demo", "虚构面霜 B", "水、香精");
        mvc.perform(get("/api/products/saved/owner-demo"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("虚构面霜 A"))
                .andExpect(jsonPath("$[0].sourceLabel").value("用户确认的包装成分表"))
                .andExpect(jsonPath("$[1]").doesNotExist());
        mvc.perform(delete("/api/products/saved/owner-demo").param("productName", "虚构面霜 B"))
                .andExpect(status().isNotFound());
        mvc.perform(delete("/api/products/saved/owner-demo").param("productName", "虚构面霜 A"))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/products/saved/owner-demo"))
                .andExpect(status().isOk()).andExpect(jsonPath("$").isEmpty());
        mvc.perform(get("/api/products/saved/other-demo"))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].name").value("虚构面霜 B"));
    }
}
