package com.skincarecopilot.ingredient;

import com.skincarecopilot.knowledge.KnowledgeEntry;
import com.skincarecopilot.knowledge.KnowledgeService;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/** 先按产品问题规划检索，再过滤适用范围；向量相似度本身不是证据质量。 */
@Component
public class ProductEvidenceSelector {
    private final KnowledgeService knowledge;
    public ProductEvidenceSelector(KnowledgeService knowledge) { this.knowledge = knowledge; }

    public Evidence select(ProductAnalysisPlanner.Plan plan, IngredientRequest request,
                           List<IngredientResponse.Product> products) {
        List<String> queries = new ArrayList<>();
        String issue = plan.concern();
        queries.add(trim(issue + " " + request.question()));
        if (plan.sensitive() || plan.currentState().equals("正在泛红或不适"))
            queries.add("接触性皮炎 刺激 过敏 泛红 敏感 皮肤");
        if (plan.sensitive() || issue.matches(".*(?:干燥|保湿|屏障).*"))
            queries.add("皮肤屏障 角质层 脂质 敏感 干燥");
        if (plan.hasFragrance() && plan.sensitive())
            queries.add("玫瑰痤疮 易受刺激皮肤 香精 无香料");
        if (plan.hasPalmiticAcid() && (issue.contains("马拉色菌") || issue.contains("脂溢性皮炎")))
            queries.add("马拉色菌 游离棕榈酸 体外培养 条件");
        Map<String, KnowledgeEntry> selected = new LinkedHashMap<>();
        for (String query : queries) for (var hit : knowledge.search(query, 5).matches()) {
            KnowledgeEntry entry = hit.chunk().source();
            if (relevant(entry, plan, request, products)) selected.putIfAbsent(entry.id(), entry);
            if (selected.size() >= 4) break;
        }
        return new Evidence(List.copyOf(selected.values()), List.copyOf(queries));
    }

    private boolean relevant(KnowledgeEntry entry, ProductAnalysisPlanner.Plan plan,
                             IngredientRequest request, List<IngredientResponse.Product> products) {
        String id = entry.id();
        if (id.equals("contact-dermatitis-irritant-allergic"))
            return plan.sensitive() || request.question().matches(".*(?:过敏|刺激|泛红).*" );
        if (id.equals("rosacea-fragrance-free")) return plan.hasFragrance() && plan.sensitive();
        if (id.startsWith("malassezia-"))
            return plan.hasPalmiticAcid() && plan.concern().matches(".*(?:马拉色菌|脂溢性皮炎).*" );
        if (id.startsWith("book-skin-")) {
            boolean sensitive = plan.sensitive() || request.question().matches(".*(?:刺激|过敏|泛红).*" );
            return switch (id) {
                case "book-skin-sensitive-distinction", "book-skin-sensitive-symptoms" -> sensitive;
                case "book-skin-barrier", "book-skin-dryness-lipids" ->
                    sensitive || plan.concern().matches(".*(?:干燥|保湿|屏障).*" );
                case "book-skin-acne-mechanisms" -> plan.concern().matches(".*(?:痤疮|痘|粉刺).*" );
                case "book-skin-structure" -> request.question().contains("表皮")
                    || request.question().contains("真皮");
                default -> false;
            };
        }
        // 博主观点仅在问题明确涉及同一主题时进入上下文，不按向量相似度硬套。
        if (id.startsWith("miriam-")) {
            String text = entry.title() + " " + entry.text();
            boolean topic = request.question().matches(".*(?:A醇|视黄醇|烟酰胺|维生素C|防腐剂|致粉刺|表面活性剂|洁面).*" );
            return topic && products.stream().flatMap(p -> p.ingredients().stream())
                    .anyMatch(i -> i.raw().length() >= 2 && text.contains(i.raw()));
        }
        return false;
    }

    private static String trim(String text) { return text.substring(0, Math.min(text.length(), 500)); }
    public record Evidence(List<KnowledgeEntry> entries, List<String> queries) {}
}
