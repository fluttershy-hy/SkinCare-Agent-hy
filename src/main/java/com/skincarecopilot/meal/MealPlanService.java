package com.skincarecopilot.meal;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.skincarecopilot.chat.ChatResponse;
import com.skincarecopilot.chat.ChatRoute;
import com.skincarecopilot.knowledge.KnowledgeEntry;
import com.skincarecopilot.knowledge.KnowledgeService;
import com.skincarecopilot.profile.ProfileService;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class MealPlanService {
    private static final Logger log = LoggerFactory.getLogger(MealPlanService.class);
    private static final String FAILURE = "连续生成的餐食草稿未能通过已确认过敏原校验，无法给出经过校验的方案。"
            + "请核对画像中的过敏原并咨询专业人员；本演示只覆盖已建模名称和别名。";
    private final ProfileService profiles;
    private final KnowledgeService knowledge;
    private final MealDraftGenerator generator;
    private final MealAllergenValidator validator;
    private final ObjectMapper json;

    public MealPlanService(ProfileService profiles, KnowledgeService knowledge, MealDraftGenerator generator,
                           MealAllergenValidator validator, ObjectMapper json) {
        this.profiles = profiles;
        this.knowledge = knowledge;
        this.generator = generator;
        this.validator = validator;
        this.json = json;
    }

    public ChatResponse plan(String userId, String request) {
        Instant start = Instant.now();
        var profile = profiles.find(userId);
        if (profile.isEmpty()) return new ChatResponse("请先为虚构用户保存并确认画像，再请求餐食建议。"
                + "未确认的过敏信息不会自动成为校验依据。", ChatRoute.MEAL_PLAN, List.of());
        // 精确取经核对的 AAD 饮食条目，避免相似度命中其他皮肤主题后误当食谱依据。
        KnowledgeEntry evidence = knowledge.catalog().entries().stream()
                .filter(e -> e.id().equals("acne-diet-evidence")).findFirst().orElse(null);
        if (evidence == null) return new ChatResponse("当前缺少已核实的饮食资料，无法生成有依据的演示餐食。",
                ChatRoute.MEAL_PLAN, List.of());
        var source = new ChatResponse.Source(evidence.id(), evidence.publisher(), evidence.title(),
                evidence.section(), evidence.url(), evidence.verifiedOn(), evidence.originalExcerpt());
        String base = "你为虚构用户生成普通日常餐食演示草稿。不得声称食谱治疗痤疮，"
                + "不得诊断、开处方或保证无过敏风险。只返回严格 JSON："
                + "{\"meals\":[{\"name\":\"早餐\",\"description\":\"简短做法\",\"ingredients\":[\"食材1\",\"食材2\"]}]}。"
                + "每个食材单列一项，不得用‘其他配料’或复合菜名代替成分；给出早餐、午餐、晚餐。"
                + "已确认过敏原必须避开，包含常见别名和复合食材；未知交叉接触风险不能保证。"
                + "\n用户请求：" + request + "\n已确认肤质：" + profile.get().skinType()
                + "\n已确认过敏原：" + String.join("、", profile.get().allergens())
                + "\n资料：" + evidence.text() + " 来源：" + evidence.url();
        List<MealAllergenValidator.Violation> previous = List.of();
        for (int attempt = 0; attempt < 3; attempt++) {
            String prompt = base;
            if (!previous.isEmpty()) prompt += "\n上一草稿不合格，具体命中食材：" + write(previous)
                    + "。请重新生成，不要重复违规食材。";
            try {
                String raw = generator.generate(prompt);
                MealDraft draft = json.readValue(stripFence(raw), MealDraft.class);
                checkShape(draft);
                previous = validator.validate(draft, profile.get().allergens());
                log.info("route=MEAL_PLAN userId={} source={} attempt={} validation={} hits={} elapsedMs={}",
                        userId, evidence.id(), attempt + 1, previous.isEmpty() ? "PASS" : "REJECT",
                        previous.size(), Duration.between(start, Instant.now()).toMillis());
                if (previous.isEmpty()) {
                    return new ChatResponse(format(draft) + "\n\n这只是已建模过敏原名称校验过的演示餐食，"
                            + "不能保证现实过敏风险。AAD 指出饮食与痤疮的研究结果不一致，"
                            + "不能把这份餐食当作治疗方案。", ChatRoute.MEAL_PLAN, List.of(source));
                }
            } catch (com.skincarecopilot.chat.ModelNotConfiguredException e) { throw e; }
            catch (Exception e) {
                log.warn("route=MEAL_PLAN userId={} source={} attempt={} validation=ERROR error={} elapsedMs={}",
                        userId, evidence.id(), attempt + 1, e.getClass().getSimpleName(),
                        Duration.between(start, Instant.now()).toMillis());
                // 无法解析的草稿不能通过校验，下一轮要求完整重做。
                previous = List.of();
            }
        }
        log.info("route=MEAL_PLAN userId={} source={} validation=FAILED elapsedMs={}", userId,
                evidence.id(), Duration.between(start, Instant.now()).toMillis());
        return new ChatResponse(FAILURE, ChatRoute.MEAL_PLAN, List.of());
    }

    private void checkShape(MealDraft draft) {
        if (draft == null || draft.meals() == null || draft.meals().size() != 3)
            throw new IllegalArgumentException("需要三餐结构");
        if (!draft.meals().stream().map(MealDraft.Meal::name).collect(java.util.stream.Collectors.toSet())
                .equals(Set.of("早餐", "午餐", "晚餐")))
            throw new IllegalArgumentException("餐次名称无效");
        for (var meal : draft.meals()) {
            if (meal == null || meal.name() == null || meal.name().isBlank() || meal.name().length() > 30
                    || meal.description() == null || meal.description().length() > 300
                    || meal.ingredients() == null || meal.ingredients().isEmpty() || meal.ingredients().size() > 20
                    || meal.ingredients().stream().anyMatch(i -> i == null || i.isBlank() || i.length() > 80))
                throw new IllegalArgumentException("餐食结构无效");
        }
    }

    private String format(MealDraft draft) {
        StringBuilder answer = new StringBuilder("按已确认画像给你的日常餐食示例：\n");
        for (var meal : draft.meals()) answer.append(meal.name()).append("：")
                .append(String.join("、", meal.ingredients())).append("。\n");
        return answer.toString().strip();
    }

    private String stripFence(String raw) {
        if (raw == null) throw new IllegalArgumentException("模型未返回草稿");
        String text = raw.strip();
        if (text.startsWith("```")) text = text.replaceFirst("^```(?:json)?\\s*", "")
                .replaceFirst("\\s*```$", "");
        return text;
    }

    private String write(Object value) {
        try { return json.writeValueAsString(value); }
        catch (Exception e) { throw new IllegalStateException(e); }
    }
}
