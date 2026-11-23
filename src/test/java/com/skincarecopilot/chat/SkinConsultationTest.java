package com.skincarecopilot.chat;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SkinConsultationTest {
    @Test void asksAreaFirstAndUsesConfirmedProfileWithoutRepeatingSkinType() {
        var flow = new SkinConsultation();
        var id = new ConversationId("user-a", "s1");
        var start = flow.next(id, "皮肤为什么经常莫名其妙泛红", "干皮 敏感肌");
        assertTrue(start.answer().contains("部位"));
        assertEquals(3, start.quickReplies().size());
        assertTrue(flow.next(id, "脸颊", "干皮 敏感肌").answer().contains("更换护肤品"));
        var next = flow.next(id, "换了新护肤品", "干皮 敏感肌");
        assertTrue(next.answer().contains("刺痛"));
        assertFalse(next.answer().contains("肤质"));
        var completed = flow.next(id, "刺痛或瘙痒", "干皮 敏感肌");
        assertEquals(SkinConsultation.State.ADVICE, completed.state());
        assertTrue(completed.retrievalQuery().contains("脸颊"));
        assertTrue(completed.retrievalQuery().contains("干皮 敏感肌"));
        assertTrue(completed.retrievalQuery().contains("换了新护肤品"));
    }
    @Test void knownAreaAndSymptomAreNotAskedAgainAndUsersAreIsolated() {
        var flow = new SkinConsultation();
        var first = new ConversationId("user-a", "s1");
        var other = new ConversationId("user-b", "s1");
        var start = flow.next(first, "脸颊泛红刺痛");
        assertTrue(start.answer().contains("更换护肤品"));
        assertNull(flow.next(other, "换了新护肤品"));
        assertTrue(flow.next(first, "换了新护肤品").answer().contains("肤质"));
        var completed = flow.next(first, "偏干或敏感");
        assertEquals(SkinConsultation.State.ADVICE, completed.state());
        assertTrue(completed.retrievalQuery().contains("刺痛"));
    }
    @Test void naturalObservationReplyIsHandledWithoutModel() {
        var flow = new SkinConsultation(); var id = new ConversationId("user-a", "s1");
        flow.next(id, "泛红"); flow.observe(id);
        assertTrue(flow.next(id, "需要").answer().contains("记下"));
    }
    @Test void negatedUrgentSymptomDoesNotInterruptCollection() {
        var result = new SkinConsultation().next(new ConversationId("u", "s"), "脸颊泛红，没有呼吸困难");
        assertEquals(SkinConsultation.State.COLLECTING, result.state());
    }
    @Test void urgentSymptomsBypassCollection() {
        var result = new SkinConsultation().next(new ConversationId("u", "s"), "泛红并且呼吸困难");
        assertEquals(SkinConsultation.State.OBSERVE, result.state());
        assertTrue(result.answer().contains("急救"));
        assertTrue(result.quickReplies().isEmpty());
    }
}
