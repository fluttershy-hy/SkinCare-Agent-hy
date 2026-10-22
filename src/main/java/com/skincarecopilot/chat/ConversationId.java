package com.skincarecopilot.chat;

// 复合键避免不同用户使用同一个会话 ID 时共享聊天历史。
public record ConversationId(String userId, String sessionId) {
}
