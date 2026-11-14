package com.skincarecopilot.chat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import com.skincarecopilot.product.ProductIngredientStore;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {"SKINCARE_LLM_API_KEY=", "SKINCARE_SEARCH_API_KEY=",
        "spring.datasource.url=jdbc:h2:mem:chat-test;DB_CLOSE_DELAY=-1"})
@AutoConfigureMockMvc
class ChatControllerTest {
    @Autowired
    private MockMvc mvc;
    @Autowired
    private ProductIngredientStore savedProducts;

    @Test
    void rejectsBlankMessageBeforeCallingModel() throws Exception {
        mvc.perform(post("/api/chat").contentType("application/json")
                        .content("{\"message\":\"   \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.answer").value("消息不能为空"));
    }

    @Test
    void explainsMissingModelKey() throws Exception {
        mvc.perform(post("/api/chat").contentType("application/json")
                        .content("{\"userId\":\"demo-user\",\"sessionId\":\"session-a\",\"message\":\"你好\"}"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.answer").value(org.hamcrest.Matchers.containsString("SKINCARE_LLM_API_KEY")));
    }

    @Test
    void requiresConfirmedProfileBeforeMealGeneration() throws Exception {
        mvc.perform(post("/api/chat").contentType("application/json")
                        .content("{\"userId\":\"demo-user\",\"sessionId\":\"session-a\",\"message\":\"我对虾过敏，给我一日饮食建议\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.route").value("MEAL_PLAN"))
                .andExpect(jsonPath("$.answer").value(org.hamcrest.Matchers.containsString("先为虚构用户保存")));
    }

    @Test
    void requiresSessionId() throws Exception {
        mvc.perform(post("/api/chat").contentType("application/json")
                        .content("{\"userId\":\"demo-user\",\"message\":\"你好\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.answer").value(org.hamcrest.Matchers.containsString("会话 ID")));
    }

    @Test
    void brandOnlyReactionRequestsSpecificProductWithoutClaimingAllergen() throws Exception {
        mvc.perform(post("/api/chat").contentType("application/json")
                        .content("{\"userId\":\"demo-user\",\"sessionId\":\"session-a\",\"message\":\"我可能对薇诺娜过敏\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.route").value("PRODUCT_SEARCH"))
                .andExpect(jsonPath("$.answer").value(org.hamcrest.Matchers.containsString("具体产品全名")));
    }

    @Test
    void exactProductReactionReportsMissingOfficialSearchConfig() throws Exception {
        mvc.perform(post("/api/chat").contentType("application/json")
                        .content("{\"userId\":\"demo-user\",\"sessionId\":\"session-a\",\"message\":\"我可能对示例品牌舒缓保湿霜过敏\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.route").value("PRODUCT_SEARCH"))
                .andExpect(jsonPath("$.sources").isEmpty())
                .andExpect(jsonPath("$.answer").value(org.hamcrest.Matchers.containsString("检索密钥")));
    }

    @Test
    void productUseQuestionSearchesBeforeAnyPersonalRecommendation() throws Exception {
        mvc.perform(post("/api/chat").contentType("application/json")
                        .content("{\"userId\":\"demo-user\",\"sessionId\":\"session-a\",\"message\":\"我可以用适乐肤神经酰胺屏障修护润肤霜吗\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.route").value("PRODUCT_SEARCH"))
                .andExpect(jsonPath("$.answer").value(org.hamcrest.Matchers.containsString("完整 INCI")))
                .andExpect(jsonPath("$.answer").value(org.hamcrest.Matchers.containsString("检索密钥")));
    }

    @Test
    void productFollowUpSearchesAgainWithinSameUserSession() throws Exception {
        String first = "{\"userId\":\"follow-up-user\",\"sessionId\":\"session-a\","
                + "\"message\":\"自然堂雪域百合补水面膜是否适合敏感肌\"}";
        mvc.perform(post("/api/chat").contentType("application/json").content(first))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.route").value("PRODUCT_SEARCH"));
        mvc.perform(post("/api/chat").contentType("application/json")
                        .content("{\"userId\":\"follow-up-user\",\"sessionId\":\"session-a\","
                                + "\"message\":\"你为什么没查到这个面膜的成分？\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.route").value("PRODUCT_SEARCH"))
                .andExpect(jsonPath("$.answer").value(org.hamcrest.Matchers.containsString("自然堂雪域百合补水面膜")));
        mvc.perform(post("/api/chat").contentType("application/json")
                        .content("{\"userId\":\"follow-up-user\",\"sessionId\":\"session-a\","
                                + "\"message\":\"我现在脸上泛红刺痛，这个面膜能用吗？\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.route").value("PRODUCT_SEARCH"))
                .andExpect(jsonPath("$.answer").value(org.hamcrest.Matchers.containsString("自然堂雪域百合补水面膜")));
    }

    @Test
    void confirmedLocalProductIsReadBeforeSearchAndAllergyIsEnforced() throws Exception {
        savedProducts.saveUserConfirmed("demo-saved", "虚构保湿面霜", "水、Parfum、甘油");
        mvc.perform(post("/api/chat").contentType("application/json")
                        .content("{\"userId\":\"demo-saved\",\"sessionId\":\"session-a\","
                                + "\"message\":\"我可以用虚构保湿面霜吗\",\"productName\":\"虚构保湿面霜\","
                                + "\"confirmedIngredientAllergies\":[\"香精\"],\"allergiesConfirmed\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.answer").value(org.hamcrest.Matchers.containsString("Parfum")))
                .andExpect(jsonPath("$.answer").value(org.hamcrest.Matchers.containsString("保存的包装成分表")));
        mvc.perform(post("/api/chat").contentType("application/json")
                        .content("{\"userId\":\"another-user\",\"sessionId\":\"session-a\","
                                + "\"message\":\"我可以用虚构保湿面霜吗\",\"productName\":\"虚构保湿面霜\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.answer").value(org.hamcrest.Matchers.containsString("本地没有")));
    }
}
