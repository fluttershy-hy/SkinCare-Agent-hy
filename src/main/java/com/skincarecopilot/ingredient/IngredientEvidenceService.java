package com.skincarecopilot.ingredient;

import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;

/** 把来源支持的窄线索分层展示；不从单一成分推算整款产品或个人风险。 */
@Service
public class IngredientEvidenceService {
    public Assessment assess(List<IngredientResponse.Product> products,
                             List<IngredientResponse.Alert> allergyAlerts) {
        return assess(products, allergyAlerts, null, 0);
    }

    public Assessment assess(List<IngredientResponse.Product> products,
                             List<IngredientResponse.Alert> allergyAlerts,
                             ProductAnalysisPlanner.Plan plan, int relevantSources) {
        List<Signal> signals = new ArrayList<>();
        for (var product : products) for (var ingredient : product.ingredients()) {
            switch (ingredient.normalized()) {
                case "FRAGRANCE" -> signals.add(new Signal(product.name(), ingredient.raw(),
                        "人群限定的护肤线索", "AAD 针对玫瑰痤疮易受刺激皮肤建议选择无香料产品。",
                        "不代表含香精产品一定刺激，也不能据此确认用户过敏。",
                        "https://www.aad.org/public/diseases/rosacea/triggers/tips"));
                case "PALMITIC ACID" -> signals.add(new Signal(product.name(), ingredient.raw(),
                        "体外培养研究", "特定培养条件下，两种马拉色菌对游离棕榈酸 C16:0 生长明显。",
                        "不是成品配方或人体皮炎研究，不能直接给产品判高风险。",
                        "https://www.ebi.ac.uk/europepmc/articles/PMC12416284"));
                default -> { }
            }
        }
        String decision = "UNKNOWN";
        String reason = "缺少证据判断整款产品对个人是否适用。";
        String nextQuestion = "";
        if (!allergyAlerts.isEmpty()) {
            decision = "AVOID_CONFIRMED_MATCH";
            reason = "与本次明确确认的过敏成分发生名称匹配；不要把该产品作为可用方案。";
        } else if (plan != null && plan.currentState().equals("正在泛红或不适")) {
            decision = "PAUSE_DURING_DISCOMFORT";
            reason = "用户自述目前泛红或不适，不应仅凭成分表推荐现在尝试新产品。";
            nextQuestion = "目前是哪个部位不适？是否在使用这款产品后才出现？";
        } else if (plan != null && plan.suitabilityQuestion() && plan.currentState().equals("不确定")) {
            decision = "ASK_CURRENT_STATE";
            reason = "缺少当前皮肤状态，暂不能给个人适用建议。";
            nextQuestion = "你准备把它用在什么部位？现在皮肤稳定，还是正在泛红、刺痛或破损？";
        } else if (plan != null && plan.suitabilityQuestion() && relevantSources == 0) {
            decision = "EVIDENCE_GAP";
            reason = "没有检索到直接相关的专业资料，只能解释成分表，不能给出个人适用判断。";
        } else if (plan != null) {
            decision = "EXPLAIN_WITH_LIMITS";
            reason = "可解释已识别的成分和相关资料，但不能保证产品功效或个人耐受。";
        }
        return new Assessment(decision, reason,
                "未核实具体地区、用途和浓度，不能判定法规禁限用状态。",
                List.copyOf(signals), nextQuestion);
    }

    public record Signal(String product, String ingredient, String evidenceType, String observation,
                         String limitation, String sourceUrl) {}
    public record Assessment(String decision, String reason, String regulatoryStatus,
                             List<Signal> signals, String nextQuestion) {}
}
