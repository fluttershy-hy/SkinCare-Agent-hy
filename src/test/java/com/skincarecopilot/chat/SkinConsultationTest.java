package com.skincarecopilot.chat;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SkinConsultationTest {
    @Test void exerciseClueDrivesFollowupAndReachesRetrieval() {
        var flow = new SkinConsultation(); var id = new ConversationId("runner", "s1");
        var initial = flow.next(id, "为什么运动后皮肤会泛红有些刺痛", "干皮 敏感肌");
        assertTrue(initial.answer().contains("部位"));
        var duration = flow.next(id, "脸颊", "干皮 敏感肌");
        assertTrue(duration.answer().contains("运动后"));
        assertTrue(duration.answer().contains("多久"));
        assertFalse(duration.answer().contains("更换护肤品"));
        var reaction = flow.next(id, "超过30分钟", "干皮 敏感肌");
        assertTrue(reaction.answer().contains("持续时间"));
        assertTrue(reaction.answer().contains("凸起"));
        var done = flow.next(id, "没有凸起", "干皮 敏感肌");
        assertEquals(SkinConsultation.State.ADVICE, done.state());
        assertTrue(done.retrievalQuery().contains("运动后"));
        assertTrue(done.retrievalQuery().contains("超过30分钟"));
        assertTrue(done.retrievalQuery().contains("没有凸起"));
        flow.observe(id);
        assertTrue(flow.canInvestigate(id));
        var heatQuestion = flow.next(id, "继续排查", "干皮 敏感肌");
        assertTrue(heatQuestion.answer().contains("热水澡"));
        var updated = flow.next(id, "也会出现", "干皮 敏感肌");
        assertEquals(SkinConsultation.State.ADVICE, updated.state());
        assertTrue(updated.retrievalQuery().contains("遇热时是否有类似不适：也会出现"));
        flow.observe(id);
        assertFalse(flow.canInvestigate(id));
    }
    @Test void negatedExerciseDoesNotCreateExerciseClue() {
        var flow = new SkinConsultation(); var id = new ConversationId("runner", "s2");
        var initial = flow.next(id, "不是运动后，但脸颊会泛红刺痛", "干皮");
        assertTrue(initial.answer().contains("更换护肤品"));
        assertFalse(initial.answer().contains("运动后出现"));
    }
    @Test void exerciseBeforeSymptomsIsNotMisreportedAsExerciseAfter() {
        var flow = new SkinConsultation(); var id = new ConversationId("runner", "before");
        var initial = flow.next(id, "运动前脸颊泛红刺痛", "干皮");
        assertFalse(initial.answer().contains("运动后出现"));
        assertTrue(initial.answer().contains("更换护肤品"));
    }
    @Test void explicitlyAbsentSymptomIsNotRewrittenAsPresent() {
        var flow = new SkinConsultation(); var id = new ConversationId("runner", "s4");
        flow.next(id, "运动后脸颊泛红但没有刺痛", "干皮");
        flow.next(id, "几分钟内缓解", "干皮");
        var done = flow.next(id, "没有凸起", "干皮");
        assertTrue(done.retrievalQuery().contains("没有刺痛"));
        assertFalse(done.retrievalQuery().contains("用户所述伴随感受：刺痛；"));
    }
    @Test void urgentSymptomsDuringExerciseFollowupBypassQuestions() {
        var flow = new SkinConsultation(); var id = new ConversationId("runner", "s3");
        flow.next(id, "运动后脸颊泛红刺痛", "干皮");
        var urgent = flow.next(id, "而且有呼吸困难", "干皮");
        assertEquals(SkinConsultation.State.OBSERVE, urgent.state());
        assertTrue(urgent.answer().contains("急救"));
    }
    @Test void productChangeLeadsToTimeRelationshipQuestion() {
        var flow = new SkinConsultation(); var id = new ConversationId("user", "product");
        flow.next(id, "脸颊泛红刺痛", "干皮");
        var done = flow.next(id, "换了新护肤品", "干皮");
        assertEquals(SkinConsultation.State.ADVICE, done.state());
        flow.observe(id);
        assertTrue(flow.next(id, "继续排查", "干皮").answer().contains("多久出现"));
        var updated = flow.next(id, "隔了一段时间", "干皮");
        assertTrue(updated.retrievalQuery().contains("用户所述新产品与不适的时间关系：隔了一段时间"));
        assertFalse(flow.canInvestigate(id));
    }
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
