package com.skincarecopilot.acne;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AcnePhotoController.class)
class AcnePhotoControllerTest {
    @Autowired MockMvc mvc;
    @MockitoBean AcnePhotoService service;
    @Test void statusDoesNotRevealConfigurationSecrets() throws Exception {
        when(service.configured()).thenReturn(false);
        mvc.perform(get("/api/acne/status")).andExpect(status().isOk())
                .andExpect(jsonPath("$.configured").value(false)).andExpect(jsonPath("$.key").doesNotExist());
    }
    @Test void sendsFriendlyErrorForInvalidImage() throws Exception {
        when(service.analyze(anyString(),anyString(),any(),any())).thenThrow(new IllegalArgumentException("仅支持 JPEG 或 PNG 图片。"));
        mvc.perform(multipart("/api/acne/analyze").file(new MockMultipartFile("image","x.txt",MediaType.TEXT_PLAIN_VALUE,"not image".getBytes()))
                        .param("userId","demo-user").param("sessionId","session-1"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error").value("仅支持 JPEG 或 PNG 图片。"));
    }
}
