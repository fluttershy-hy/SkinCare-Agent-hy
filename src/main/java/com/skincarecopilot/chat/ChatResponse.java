package com.skincarecopilot.chat;

import java.util.List;

public record ChatResponse(String answer, ChatRoute route, List<Source> sources) {
    public ChatResponse(String answer) {
        this(answer, null, List.of());
    }

    public record Source(String id, String publisher, String title, String section, String url,
                         String verifiedOn, String excerpt) {
    }
}
