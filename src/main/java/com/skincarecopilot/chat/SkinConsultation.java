package com.skincarecopilot.chat;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/** 只在当前用户和当前会话内记录用户亲口提供的信息，不写入长期画像。 */
public class SkinConsultation {
    enum State { INITIAL, COLLECTING, ADVICE, OBSERVE }
    record Step(String original, String symptom, String trigger, String skinType, String associated,
                int awaiting, State state) { }
    record Result(String answer, List<String> quickReplies, String retrievalQuery, State state) { }
    private final ConcurrentHashMap<ConversationId, Step> sessions = new ConcurrentHashMap<>();

    private boolean urgent(String message) {
        var matcher = java.util.regex.Pattern.compile("呼吸困难|喘不上气|喉咙肿|嘴唇肿|舌头肿|晕厥").matcher(message);
        while (matcher.find()) {
            String prefix = message.substring(Math.max(0, matcher.start() - 6), matcher.start());
            if (!prefix.matches(".*(?:没有出现|没有|未出现|不伴有|不存在|无)$")) return true;
        }
        return false;
    }

    public void observe(ConversationId id) {
        sessions.computeIfPresent(id, (key, step) -> new Step(step.original(), step.symptom(), step.trigger(), step.skinType(), step.associated(), step.awaiting(), State.OBSERVE));
    }

    public Result next(ConversationId id, String message) {
        if (urgent(message)) {
            sessions.remove(id);
            return new Result("如果出现呼吸困难、喉咙或舌头肿胀，请立即呼叫当地急救电话或前往急诊。", List.of(), null, State.OBSERVE);
        }
        Step previous = sessions.get(id);
        if (previous != null && previous.state() == State.OBSERVE) {
            if (message.equals("需要记录")) {
                sessions.remove(id);
                return new Result("可以从今天开始记录：每天什么时间出现不适、持续多久、使用了哪些护肤品，以及是否出现新的变化。带着这些记录咨询皮肤科时会更方便。", List.of(), null, State.OBSERVE);
            }
            if (message.equals("暂不需要")) {
                sessions.remove(id);
                return new Result("好的，有新的变化或其他护肤问题时可以继续告诉我。", List.of(), null, State.OBSERVE);
            }
            sessions.remove(id);
            previous = null;
        }
        if (previous == null || previous.state() != State.COLLECTING) {
            if (!message.matches(".*(泛红|发红|红肿|刺痛|灼热|瘙痒|起疹|皮疹|脱屑).*")) return null;
            previous = new Step(message, message, "", "", "", 0, State.COLLECTING);
            sessions.put(id, previous);
            return new Result("【共情回应】脸颊不舒服确实会让人在意。\n\n【当前分析与建议】先记录近期使用的产品和皮肤变化，我们一起梳理。\n\n【下一步排查】最近有没有更换护肤品，或者其他明显变化？", List.of("换了新护肤品", "饮食或环境有变化", "都没有或记不清"), null, State.COLLECTING);
        }
        String trigger = previous.trigger(), skin = previous.skinType(), associated = previous.associated();
        switch (previous.awaiting()) {
            case 0 -> trigger = message;
            case 1 -> skin = message;
            case 2 -> associated = message;
            default -> throw new IllegalStateException("未知槽位");
        }
        int next = previous.awaiting() + 1;
        if (next == 1) {
            sessions.put(id, new Step(previous.original(), previous.symptom(), trigger, skin, associated, next, State.COLLECTING));
            return new Result("了解了。你平时的肤质更接近哪一种？", List.of("偏干或敏感", "偏油或混合", "不确定"), null, State.COLLECTING);
        }
        if (next == 2) {
            sessions.put(id, new Step(previous.original(), previous.symptom(), trigger, skin, associated, next, State.COLLECTING));
            return new Result("收到。除了之前说的不适，还有哪些感受？", List.of("脱屑或紧绷", "刺痛或瘙痒", "都没有或不确定"), null, State.COLLECTING);
        }
        var completed = new Step(previous.original(), previous.symptom(), trigger, skin, associated, next, State.ADVICE);
        sessions.put(id, completed);
        return new Result(null, List.of(), "用户最初的问题：" + completed.original() + "；用户所述近期变化：" + trigger + "；自述肤质：" + skin + "；伴随感受：" + associated, State.ADVICE);
    }
}
