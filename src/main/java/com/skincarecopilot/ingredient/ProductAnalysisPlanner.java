package com.skincarecopilot.ingredient;

import java.util.List;
import org.springframework.stereotype.Component;

/** 将用户本次描述整理成临时分析条件，不写入已确认画像。 */
@Component
public class ProductAnalysisPlanner {
    public Plan plan(IngredientRequest request, List<IngredientResponse.Product> products) {
        String question = request.question().strip();
        String state = request.currentState();
        if (state == null || state.isBlank() || state.equals("不确定")) {
            if (question.matches(".*(?:现在|正在|目前|最近).{0,12}(?:泛红|刺痛|不适|破损|灼热|发痒).*"))
                state = "正在泛红或不适";
            else state = "不确定";
        }
        String area = request.bodyArea();
        if (area == null || area.isBlank() || area.equals("未说明")) {
            if (question.matches(".*(?:脸|面部|面颊|T区).*")) area = "面部";
            else if (question.matches(".*(?:身体|手臂|腿|背部).*")) area = "身体";
            else area = "未说明";
        }
        boolean suitability = question.matches(".*(?:适合|能用|可以用|值得尝试|能不能用|推荐|该不该|选哪款|安全吗).*" );
        boolean sensitive = Boolean.TRUE.equals(request.sensitive()) || question.contains("敏感肌")
                || question.contains("易敏") || question.contains("敏感皮肤");
        String concern = request.concern() == null || request.concern().isBlank() ? question : request.concern().strip();
        boolean hasFragrance = products.stream().flatMap(p -> p.ingredients().stream())
                .anyMatch(i -> i.normalized().equals("FRAGRANCE"));
        boolean hasPalmitic = products.stream().flatMap(p -> p.ingredients().stream())
                .anyMatch(i -> i.normalized().equals("PALMITIC ACID"));
        return new Plan(area, state, suitability, sensitive, concern, hasFragrance, hasPalmitic);
    }

    public record Plan(String bodyArea, String currentState, boolean suitabilityQuestion,
                       boolean sensitive, String concern, boolean hasFragrance, boolean hasPalmiticAcid) {}
}
