package com.skincarecopilot.chat;

import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

@Component
public class ChatMemoryRegistry {
    private final ConcurrentHashMap<ConversationId, MessageWindowChatMemory> memories = new ConcurrentHashMap<>();

    public MessageWindowChatMemory get(ConversationId id) {
        // 只保存最近 20 条消息；整个注册表只在当前进程内存在。
        return memories.computeIfAbsent(id, key -> MessageWindowChatMemory.builder()
                .id(key)
                .maxMessages(20)
                .build());
    }
}
