package com.skincarecopilot.chat;

public record ChatRequest(String userId, String sessionId, String message) {
}
