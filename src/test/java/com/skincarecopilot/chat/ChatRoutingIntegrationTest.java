package com.skincarecopilot.chat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.skincarecopilot.knowledge.KnowledgeChunk;
import com.skincarecopilot.knowledge.KnowledgeEntry;
import com.skincarecopilot.knowledge.KnowledgeService;
import com.skincarecopilot.profile.ProfileService;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class ChatRoutingIntegrationTest {
    private final ObjectMapper json = new ObjectMapper();
    private final KnowledgeEntry source = new KnowledgeEntry("aad-demo", "AAD", "Verified demo title",
            "https://www.aad.org/example", "section", "2026-10-24", "Original excerpt", "玫瑰痤疮诱因因人而异。");

    @Test
    void retrievalContextAndRealSourcesReachTheResponse() throws Exception {
        AtomicReference<JsonNode> requestBody = new AtomicReference<>();
        HttpServer server = fakeModel(requestBody);
        try {
            KnowledgeService knowledge = mock(KnowledgeService.class);
            var hit = new KnowledgeService.SearchHit(.9,
                    new KnowledgeChunk("aad-demo:0", "aad-demo", 0, source.text().length(), source.text(), source));
            when(knowledge.search(anyString(), eq(3))).thenReturn(
                    new KnowledgeService.SearchResult("question", KnowledgeService.MODEL, .75, List.of(hit)));
            ChatService service = new ChatService("fake-key", endpoint(server), "fake-model",
                    mock(ProfileService.class), new ChatMemoryRegistry(), knowledge, new ChatRouter());
            ChatResponse response = service.replyWithSources("demo-user", "session-1", "玫瑰痤疮有什么诱因");
            assertEquals(ChatRoute.KNOWLEDGE, response.route());
            assertEquals("测试回答", response.answer());
            assertEquals("aad-demo", response.sources().get(0).id());
            assertEquals("Original excerpt", response.sources().get(0).excerpt());
            assertTrue(requestBody.get().toString().contains(source.text()));
            assertTrue(requestBody.get().toString().contains("只依据下列已核实片段回答"));
        } finally { server.stop(0); }
    }

    @Test
    void noEvidenceStopsBeforeModelEvenWhenNoKeyIsConfigured() {
        KnowledgeService knowledge = mock(KnowledgeService.class);
        when(knowledge.search(anyString(), eq(3))).thenReturn(
                new KnowledgeService.SearchResult("question", KnowledgeService.MODEL, .75, List.of()));
        ChatService service = new ChatService("", "http://127.0.0.1:1/v1", "fake-model",
                mock(ProfileService.class), new ChatMemoryRegistry(), knowledge, new ChatRouter());
        ChatResponse response = service.replyWithSources("demo-user", "session-1", "烟酰胺能祛痘吗");
        assertEquals(ChatRoute.KNOWLEDGE, response.route());
        assertTrue(response.answer().contains("资料不足"));
        assertTrue(response.sources().isEmpty());
        verify(knowledge).search(anyString(), eq(3));
    }

    private HttpServer fakeModel(AtomicReference<JsonNode> requestBody) throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/chat/completions", exchange -> {
            requestBody.set(json.readTree(exchange.getRequestBody()));
            byte[] body = json.writeValueAsBytes(new Completion("fake-1", "chat.completion", 1, "fake-model",
                    List.of(new Choice(0, new Message("assistant", "测试回答"), "stop")), new Usage(1, 1, 2)));
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        return server;
    }

    private String endpoint(HttpServer server) {
        return "http://127.0.0.1:" + server.getAddress().getPort() + "/v1";
    }

    private record Completion(String id, String object, long created, String model, List<Choice> choices, Usage usage) {}
    private record Choice(int index, Message message, String finish_reason) {}
    private record Message(String role, String content) {}
    private record Usage(int prompt_tokens, int completion_tokens, int total_tokens) {}
}
