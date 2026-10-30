package com.skincarecopilot.knowledge;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {"SKINCARE_LLM_API_KEY=", "spring.datasource.url=jdbc:h2:mem:knowledge-test;DB_CLOSE_DELAY=-1"})
@AutoConfigureMockMvc
class KnowledgeControllerTest {
    @Autowired
    private MockMvc mvc;

    @Test
    void exposesCatalogWithoutChatCredentials() throws Exception {
        mvc.perform(get("/api/knowledge/sources"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.model").value("bge-small-zh-v1.5-q"))
                .andExpect(jsonPath("$.dimensions").value(512))
                .andExpect(jsonPath("$.entryCount").value(20))
                .andExpect(jsonPath("$.chunkCount").value(20))
                .andExpect(jsonPath("$.entries").isArray());
    }

    @Test
    void standaloneSearchFindsReviewedRosaceaPassageAndItsSource() throws Exception {
        mvc.perform(post("/api/knowledge/search").contentType("application/json")
                        .content("{\"query\":\"玫瑰痤疮泛红有什么诱因\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.matches").isArray())
                .andExpect(jsonPath("$.matches[0].chunk.entryId").value("rosacea-triggers-vary"))
                .andExpect(jsonPath("$.matches[0].chunk.source.publisher").value("AAD"))
                .andExpect(jsonPath("$.matches[0].chunk.source.originalExcerpt").isString())
                .andExpect(jsonPath("$.answer").doesNotExist());
    }

    @Test
    void pediatricEczemaResultKeepsItsAgeLimitedNiceSource() throws Exception {
        mvc.perform(post("/api/knowledge/search").contentType("application/json")
                        .content("{\"query\":\"儿童特应性湿疹用什么润肤剂\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.matches[0].chunk.entryId").value("eczema-child-emollients"))
                .andExpect(jsonPath("$.matches[0].chunk.source.publisher").value("NICE"))
                .andExpect(jsonPath("$.matches[0].chunk.source.section").value("1.5 Treatment · 1.5.1.1 and 1.5.1.4"));
    }

    @Test
    void creatorViewpointIsSearchableWithItsOwnPublisherAndLink() throws Exception {
        mvc.perform(post("/api/knowledge/search").contentType("application/json")
                        .content("{\"query\":\"A醇 HPR 有什么区别？\",\"topK\":3}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.matches[0].chunk.source.publisher").value("MiriamMibao"))
                .andExpect(jsonPath("$.matches[0].chunk.source.url")
                        .value("https://miriammibao.com/guide-to-apply-retinol-and-pigment-test/"));
    }

    @Test
    void rejectsMissingBlankOversizedQueriesAndInvalidLimits() throws Exception {
        for (String payload : new String[]{"{}", "{\"query\":\"  \"}",
                "{\"query\":\"" + "字".repeat(501) + "\"}",
                "{\"query\":\"清洁\",\"topK\":0}", "{\"query\":\"清洁\",\"topK\":6}"}) {
            mvc.perform(post("/api/knowledge/search").contentType("application/json").content(payload))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error").isString());
        }
    }
}
