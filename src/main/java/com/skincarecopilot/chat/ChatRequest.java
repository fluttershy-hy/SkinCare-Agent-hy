package com.skincarecopilot.chat;

import java.util.List;

public record ChatRequest(String userId, String sessionId, String message, String bodyArea,
                          String currentState, Boolean sensitive, String concern, String productName,
                          List<String> confirmedIngredientAllergies, boolean allergiesConfirmed,
                          String productMarket, String productVersion) {
    public ChatRequest(String userId, String sessionId, String message) {
        this(userId, sessionId, message, null, null, null, null, null, List.of(), false, null, null);
    }
}
