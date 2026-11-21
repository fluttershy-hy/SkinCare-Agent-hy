package com.skincarecopilot.chat;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SkinConsultationTest {
    @Test void asksOneSlotAtATimeAndKeepsUsersIsolated() {
        var flow = new SkinConsultation();
        var first = new ConversationId("user-a", "s1");
        var other = new ConversationId("user-b", "s1");
        var start = flow.next(first, "脸颊泛红刺痛");
        assertEquals(3, start.quickReplies().size());
        assertEquals(SkinConsultation.State.COLLECTING, start.state());
        assertNull(flow.next(other, "换了新护肤品"));
        assertTrue(flow.next(first, "换了新护肤品").answer().contains("肤质"));
        assertTrue(flow.next(first, "偏干或敏感").answer().contains("还有"));
        var completed = flow.next(first, "刺痛或瘙痒");
        assertEquals(SkinConsultation.State.ADVICE, completed.state());
        assertTrue(completed.retrievalQuery().contains("换了新护肤品"));
        assertTrue(completed.retrievalQuery().contains("偏干或敏感"));
        assertNull(completed.answer());
    }
    @Test void negatedUrgentSymptomDoesNotInterruptCollection() {
        var result = new SkinConsultation().next(new ConversationId("u", "s"), "脸颊泛红，没有呼吸困难");
        assertEquals(SkinConsultation.State.COLLECTING, result.state());
    }
    @Test void urgentSymptomsBypassCollection() {
        var flow = new SkinConsultation();
        var result = flow.next(new ConversationId("u", "s"), "泛红并且呼吸困难");
        assertEquals(SkinConsultation.State.OBSERVE, result.state());
        assertTrue(result.answer().contains("急救"));
        assertTrue(result.quickReplies().isEmpty());
    }
}
