package com.skincarecopilot.chat;

import com.skincarecopilot.profile.ProfileService;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PhotoConversationMemoryTest {
    @Test void remembersOnlyTextForTheRightUserAndSession() {
        var memories = new ChatMemoryRegistry();
        var service = new ChatService("", "http://127.0.0.1:1/v1", "unused", mock(ProfileService.class), memories);
        service.rememberPhotoTurn("u1", "s1", "看看痘痘", "脸颊有红色丘疹样表现", "可以先温和护理。");
        var items = memories.get(new ConversationId("u1", "s1")).messages();
        assertEquals(2, items.size());
        assertTrue(items.get(0).toString().contains("丘疹样"));
        assertFalse(items.toString().contains("base64"));
        assertTrue(memories.get(new ConversationId("u2", "s1")).messages().isEmpty());
        assertTrue(memories.get(new ConversationId("u1", "s2")).messages().isEmpty());
    }
}
