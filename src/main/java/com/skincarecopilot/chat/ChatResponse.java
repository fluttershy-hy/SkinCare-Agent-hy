package com.skincarecopilot.chat;

import java.util.List;

public record ChatResponse(String answer, ChatRoute route, List<Source> sources, List<String> quickReplies) {
    public ChatResponse(String answer, ChatRoute route, List<Source> sources) { this(answer, route, sources, List.of()); }
    public ChatResponse(String answer) {
        this(answer, null, List.of(), List.of());
    }

    public record Source(String id, String publisher, String title, String section, String url,
                         String verifiedOn, String excerpt) {
    }
}
