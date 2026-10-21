package com.skincarecopilot.chat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {"SKINCARE_LLM_API_KEY=", "spring.datasource.url=jdbc:h2:mem:chat-test;DB_CLOSE_DELAY=-1"})
@AutoConfigureMockMvc
class ChatControllerTest {
    @Autowired
    private MockMvc mvc;

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
                        .content("{\"userId\":\"demo-user\",\"message\":\"你好\"}"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.answer").value(org.hamcrest.Matchers.containsString("SKINCARE_LLM_API_KEY")));
    }
}
