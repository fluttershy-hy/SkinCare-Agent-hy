package com.skincarecopilot.chat;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/** 只保存当前会话中用户明确提供的信息；不会修改长期画像。 */
public class SkinConsultation {
    enum State { INITIAL, COLLECTING, ADVICE, OBSERVE }
    enum Slot { AREA, TRIGGER, SKIN, ASSOCIATED }
    record Step(String original, String area, String trigger, String skinType, String associated,
                Slot awaiting, State state) { }
    record Result(String answer, List<String> quickReplies, String retrievalQuery, State state) { }
    private final ConcurrentHashMap<ConversationId, Step> sessions = new ConcurrentHashMap<>();
    private static final Pattern AREA = Pattern.compile("脸颊|面颊|两颊|双颊|鼻翼|鼻子|额头|下巴|眼周|嘴周|口周|全脸|脖子|颈部|身体");
    private static final Pattern ASSOCIATED = Pattern.compile("刺痛|瘙痒|发痒|灼热|脱屑|起皮|紧绷|疼痛");

    private boolean urgent(String message) {
        var matcher = Pattern.compile("呼吸困难|喘不上气|喉咙肿|嘴唇肿|舌头肿|晕厥").matcher(message);
        while (matcher.find()) {
            String prefix = message.substring(Math.max(0, matcher.start() - 6), matcher.start());
            if (!prefix.matches(".*(?:没有出现|没有|未出现|不伴有|不存在|无)$")) return true;
        }
        return false;
    }

    public void observe(ConversationId id) {
        sessions.computeIfPresent(id, (key, s) -> new Step(s.original(), s.area(), s.trigger(), s.skinType(), s.associated(), s.awaiting(), State.OBSERVE));
    }

    public Result next(ConversationId id, String message) { return next(id, message, () -> ""); }

    public Result next(ConversationId id, String message, String confirmedSkin) { return next(id, message, () -> confirmedSkin); }

    public Result next(ConversationId id, String message, java.util.function.Supplier<String> confirmedSkinProvider) {
        if (urgent(message)) {
            sessions.remove(id);
            return new Result("如果出现呼吸困难、喉咙或舌头肿胀，请立即呼叫当地急救电话或前往急诊。", List.of(), null, State.OBSERVE);
        }
        Step s = sessions.get(id);
        if (s != null && s.state() == State.OBSERVE) {
            if (message.matches("^(需要|需要记录|要|好|好的|可以|想要)$")) {
                sessions.remove(id);
                return new Result("可以先记下泛红的时间、持续多久、使用过哪些护肤品，以及是否伴有刺痛或瘙痒。有需要时带上记录咨询皮肤科。", List.of(), null, State.OBSERVE);
            }
            if (message.matches("^(暂不需要|不需要|不用|不了)$")) {
                sessions.remove(id);
                return new Result("好的，之后有新的变化，也可以随时告诉我。", List.of(), null, State.OBSERVE);
            }
            sessions.remove(id);
            s = null;
        }
        if (s == null || s.state() != State.COLLECTING) {
            if (!message.matches(".*(泛红|发红|红肿|刺痛|灼热|瘙痒|起疹|皮疹|脱屑).*")) return null;
            var areaMatch = AREA.matcher(message);
            var symptomMatch = ASSOCIATED.matcher(message);
            String area = areaMatch.find() ? areaMatch.group() : "";
            String associated = symptomMatch.find() ? symptomMatch.group() : "";
            String confirmedSkin = confirmedSkinProvider.get();
            String skin = isKnownSkin(confirmedSkin) ? confirmedSkin.strip() : "";
            s = new Step(message, area, "", skin, associated, null, State.COLLECTING);
            return advance(id, s, true);
        }
        s = switch (s.awaiting()) {
            case AREA -> new Step(s.original(), message, s.trigger(), s.skinType(), s.associated(), null, State.COLLECTING);
            case TRIGGER -> new Step(s.original(), s.area(), message, s.skinType(), s.associated(), null, State.COLLECTING);
            case SKIN -> new Step(s.original(), s.area(), s.trigger(), message, s.associated(), null, State.COLLECTING);
            case ASSOCIATED -> new Step(s.original(), s.area(), s.trigger(), s.skinType(), message, null, State.COLLECTING);
        };
        return advance(id, s, false);
    }

    private static boolean isKnownSkin(String skin) {
        return skin != null && !skin.isBlank() && !skin.strip().matches("不确定|不知道|不清楚|未填写|未登记|未确认|未说明|未知");
    }

    private Result advance(ConversationId id, Step s, boolean first) {
        if (s.area().isBlank()) {
            sessions.put(id, withSlot(s, Slot.AREA));
            return new Result("皮肤反复泛红，又找不到原因，确实容易让人担心。咱们一起慢慢找线索：泛红主要出现在哪个部位？", List.of("脸颊", "全脸", "其他或不固定"), null, State.COLLECTING);
        }
        if (s.trigger().isBlank()) {
            sessions.put(id, withSlot(s, Slot.TRIGGER));
            String intro = first ? "反复泛红、刺痛会让人有些担心，我们一起慢慢找线索。" : "了解，是" + s.area() + "。";
            return new Result(intro + "最近有没有更换护肤品，或者出现其他明显变化？", List.of("换了新护肤品", "饮食或环境有变化", "都没有或记不清"), null, State.COLLECTING);
        }
        if (s.skinType().isBlank()) {
            sessions.put(id, withSlot(s, Slot.SKIN));
            return new Result("收到。为了让建议更贴合你，平时的肤质更接近哪一种？", List.of("偏干或敏感", "偏油或混合", "不确定"), null, State.COLLECTING);
        }
        if (s.associated().isBlank()) {
            sessions.put(id, withSlot(s, Slot.ASSOCIATED));
            return new Result("了解了。泛红时还会有刺痛、瘙痒或脱屑吗？", List.of("脱屑或紧绷", "刺痛或瘙痒", "都没有或不确定"), null, State.COLLECTING);
        }
        sessions.put(id, new Step(s.original(), s.area(), s.trigger(), s.skinType(), s.associated(), null, State.ADVICE));
        String query = "用户问题：" + s.original() + "；泛红或不适部位：" + s.area() + "；近期变化（用户所述，未确认因果关系）：" + s.trigger()
                + "；肤质背景：" + s.skinType() + "；用户所述伴随感受：" + s.associated();
        return new Result(null, List.of(), query, State.ADVICE);
    }
    private Step withSlot(Step s, Slot slot) {
        return new Step(s.original(), s.area(), s.trigger(), s.skinType(), s.associated(), slot, State.COLLECTING);
    }
}
