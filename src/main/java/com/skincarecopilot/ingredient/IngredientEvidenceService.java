package com.skincarecopilot.ingredient;

import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;

/** 把来源支持的窄线索分层展示；不从单一成分推算整款产品或个人风险。 */
@Service
public class IngredientEvidenceService {
    public Assessment assess(List<IngredientResponse.Product> products,
                             List<IngredientResponse.Alert> allergyAlerts) {
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
        return new Assessment(allergyAlerts.isEmpty() ? "UNKNOWN" : "AVOID_CONFIRMED_MATCH",
                allergyAlerts.isEmpty() ? "缺少证据判断整款产品对个人是否适用。"
                        : "与本次明确确认的过敏成分发生名称匹配；不要把该产品作为可用方案。",
                "未核实具体地区、用途和浓度，不能判定法规禁限用状态。",
                List.copyOf(signals));
    }

    public record Signal(String product, String ingredient, String evidenceType, String observation,
                         String limitation, String sourceUrl) {}
    public record Assessment(String decision, String reason, String regulatoryStatus,
                             List<Signal> signals) {}
}
