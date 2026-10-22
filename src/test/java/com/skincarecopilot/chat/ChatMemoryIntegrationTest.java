package com.skincarecopilot.chat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.skincarecopilot.profile.ProfileService;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;

class ChatMemoryIntegrationTest {
    private final ObjectMapper json = new ObjectMapper();

    @Test
    void historyFollowsOnlyTheSameUserAndSession() throws Exception {
        HttpServer fakeModel = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        fakeModel.createContext("/v1/chat/completions", exchange -> {
            JsonNode request = json.readTree(exchange.getRequestBody());
            String answer = "记住了";
            if (request.toString().contains("暗号是什么")) {
                answer = request.toString().contains("暗号是蓝鲸") ? "看到了上一轮" : "没有上一轮";
            }
            byte[] body = json.writeValueAsString(new FakeCompletion(answer)).getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        fakeModel.start();
        try {
            ChatService service = new ChatService("test-key",
                    "http://127.0.0.1:" + fakeModel.getAddress().getPort() + "/v1",
                    "fake-model", mock(ProfileService.class), new ChatMemoryRegistry());

            assertEquals("记住了", service.reply("user-a", "session-1", "暗号是蓝鲸"));
            assertEquals("看到了上一轮", service.reply("user-a", "session-1", "暗号是什么"));
            assertEquals("没有上一轮", service.reply("user-b", "session-1", "暗号是什么"));
            assertEquals("没有上一轮", service.reply("user-a", "session-2", "暗号是什么"));
        } finally {
            fakeModel.stop(0);
        }
    }

    private record FakeCompletion(String id, String object, long created, String model,
                                  java.util.List<Choice> choices, Usage usage) {
        FakeCompletion(String answer) {
            this("fake-1", "chat.completion", 1, "fake-model",
                    java.util.List.of(new Choice(0, new Message("assistant", answer), "stop")),
                    new Usage(1, 1, 2));
        }
    }

    private record Choice(int index, Message message, String finish_reason) {
    }

    private record Message(String role, String content) {
    }

    private record Usage(int prompt_tokens, int completion_tokens, int total_tokens) {
    }
}
