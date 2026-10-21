package com.skincarecopilot.profile;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:profile-test;DB_CLOSE_DELAY=-1")
@AutoConfigureMockMvc
class ProfileControllerTest {
    @Autowired
    private MockMvc mvc;

    @Test
    void onlyConfirmedInputIsStoredAndUsersStaySeparate() throws Exception {
        mvc.perform(put("/api/profiles/demo-a").contentType("application/json")
                        .content("{\"skinType\":\"油性\",\"allergens\":[\"花生\"],\"confirmed\":false}"))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/profiles/demo-a")).andExpect(status().isNotFound());

        mvc.perform(put("/api/profiles/demo-a").contentType("application/json")
                        .content("{\"skinType\":\"油性\",\"allergens\":[\"花生\"],\"confirmed\":true}"))
                .andExpect(status().isOk());
        mvc.perform(get("/api/profiles/demo-a"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.skinType").value("油性"))
                .andExpect(jsonPath("$.allergens[0]").value("花生"));
        mvc.perform(get("/api/profiles/demo-b")).andExpect(status().isNotFound());
    }
}
