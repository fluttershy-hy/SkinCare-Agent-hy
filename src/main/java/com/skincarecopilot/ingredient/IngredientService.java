package com.skincarecopilot.ingredient;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.skincarecopilot.knowledge.KnowledgeService;
import com.skincarecopilot.profile.ProfileService;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.UserMessage;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Service
public class IngredientService {
    private static final Logger log = LoggerFactory.getLogger(IngredientService.class);
    private final ProfileService profiles;
    private final KnowledgeService knowledge;
    private final CreatorArticleService creatorArticles;
    private final ObjectMapper json;
    private final String apiKey, baseUrl, modelName;

    public IngredientService(ProfileService profiles, KnowledgeService knowledge,
            CreatorArticleService creatorArticles, ObjectMapper json,
            @Value("${SKINCARE_LLM_API_KEY:}") String apiKey,
            @Value("${SKINCARE_LLM_BASE_URL:https://api.deepseek.com}") String baseUrl,
            @Value("${SKINCARE_LLM_MODEL:deepseek-flash}") String modelName) {
        this.profiles = profiles;
        this.knowledge = knowledge;
        this.creatorArticles = creatorArticles;
        this.json = json;
        this.apiKey = apiKey;
        this.baseUrl = baseUrl;
        this.modelName = modelName;
    }

    public IngredientResponse analyze(IngredientRequest request) {
        if (request == null) throw new IllegalArgumentException("请求不能为空");
        ProfileService.validateUserId(request.userId());
        if (request.first() == null) throw new IllegalArgumentException("至少填写一款产品");
        if (request.question() == null || request.question().isBlank() || request.question().length() > 500)
            throw new IllegalArgumentException("问题需为 1～500 字符");
        if (request.concern() != null && request.concern().length() > 200)
            throw new IllegalArgumentException("关注问题不能超过 200 字符");
        var products = new ArrayList<IngredientResponse.Product>();
        products.add(parseProduct(request.first()));
        if (request.second() != null) products.add(parseProduct(request.second()));

        String skinType = profiles.find(request.userId()).map(p -> p.skinType()).orElse("");
        if (request.skinType() != null && !request.skinType().isBlank()) skinType = request.skinType().strip();
        if (skinType.length() > 80) throw new IllegalArgumentException("肤质不能超过 80 字符");
        List<String> confirmed = request.confirmedIngredientAllergies() == null ? List.of()
                : request.confirmedIngredientAllergies();
        if (!confirmed.isEmpty() && !request.allergiesConfirmed())
            throw new IllegalArgumentException("请明确确认所填的是已确认过敏成分；怀疑项不能用于强制匹配");
        if (confirmed.size() > 20 || confirmed.stream().anyMatch(s -> s == null || s.isBlank() || s.length() > 80))
            throw new IllegalArgumentException("已确认过敏成分最多 20 项，每项 1～80 字符");
        // 强制匹配在模型调用之前完成，使用小型别名表；未知名称仅允许字面完全一致。
        var alerts = new ArrayList<IngredientResponse.Alert>();
        for (var product : products) for (var ingredient : product.ingredients()) for (String allergen : confirmed) {
            if (ingredient.normalized().equals(IngredientNames.canonical(allergen)))
                alerts.add(new IngredientResponse.Alert(product.name(), allergen,
                        ingredient.raw(), ingredient.position()));
        }

        // RAG 只供模型解释资料；命中片段和来源由 Java 选取，不接受模型自造引用。
        String searchQuery = request.question() + " " + (request.concern() == null ? "" : request.concern());
        if (products.stream().flatMap(p -> p.ingredients().stream())
                .anyMatch(i -> i.normalized().equals("FRAGRANCE"))) searchQuery += " 香精 无香料 刺激 玫瑰痤疮";
        var hits = knowledge.search(searchQuery.strip().substring(0, Math.min(500, searchQuery.strip().length())), 5).matches();
        Map<String, IngredientResponse.Source> sources = new LinkedHashMap<>();
        StringBuilder evidence = new StringBuilder();
        for (var hit : hits) {
            var entry = hit.chunk().source();
            // 小型库目前只允许与成分解读直接有关的两条来源进入提示词。
            if (!List.of("rosacea-fragrance-free", "contact-dermatitis-irritant-allergic").contains(entry.id())) continue;
            if (entry.id().equals("rosacea-fragrance-free") && !searchQuery.contains("香精")
                    && !searchQuery.contains("敏感")) continue;
            sources.putIfAbsent(entry.id(), new IngredientResponse.Source(entry.title(), entry.url(),
                    entry.section(), entry.verifiedOn()));
            evidence.append("[来源 ").append(entry.id()).append("] ").append(hit.chunk().text()).append('\n');
        }
        List<String> limits = List.of("成分表不提供准确浓度、配方工艺或实际使用效果；排序不能证明成分无效。",
                "成分表及一般刺激资料不能诊断个人过敏；未知名称和别名库外的同义词可能无法匹配。",
                "当前知识库很小；没有对应资料的成分功效应说明资料不足。");
        var readingSuggestions = creatorArticles.related(request.question() + " "
                + (request.concern() == null ? "" : request.concern()));
        String answer = null;
        String prompt = "你是谨慎的护肤成分讲解员。仅依据下面结构化输入和检索片段，先直接回答问题，再解释依据、双产品差异及局限。"
                + "绝不推断准确浓度、疗效或个人致敏原因；没有证据就明确不知道。"
                + "重要事实判断请在句中标明对应来源 ID；来源没有支持的结论不要写。"
                + "只输出 JSON 对象，字段 answer 为中文自然语言字符串，不要 Markdown 代码围栏。"
                + "不可自行增删过敏提示，Java 会另行显示。\n问题：" + request.question()
                + "\n肤质（用户输入优先，否则已确认画像）：" + skinType
                + "\n关注问题：" + request.concern() + "\n产品成分与顺序：" + write(products)
                + "\n已确认过敏成分命中：" + write(alerts) + "\n证据片段：" + evidence
                + "\n固定局限：" + write(limits);
        if (!apiKey.isBlank() && !sources.isEmpty()) {
            try {
                var model = OpenAiChatModel.builder().apiKey(apiKey).baseUrl(baseUrl).modelName(modelName).build();
                String raw = AiServices.create(Analyzer.class, model).answer(prompt).strip();
                var node = json.readTree(raw);
                answer = node.path("answer").asText("").strip();
                if (!node.isObject() || answer.isBlank() || answer.length() > 4000)
                    throw new IllegalStateException("模型未返回有效分析");
            } catch (Exception e) {
                log.warn("ingredient model analysis failed: {}", e.getClass().getSimpleName());
            }
        }
        if (answer == null) {
            answer = (sources.isEmpty() ? "当前已核实资料不足" : "当前模型不可用")
                    + "，暂时无法可靠回答“" + request.question().strip()
                    + "”。以下仅展示成分表的字面解析与已确认成分匹配；不能由此判断保湿效果、"
                    + "适用性或个人致敏原因。请核对成分表并稍后重试解读。";
        }
        // 不可信模型文本不能覆盖确定性结果；强制警示由业务代码加到回答顶部。
        if (!alerts.isEmpty()) {
            StringBuilder warning = new StringBuilder("已确认过敏成分命中：");
            for (var a : alerts) warning.append(a.product()).append("第 ").append(a.position())
                    .append(" 项 ").append(a.matchedIngredient()).append("（对应已确认：")
                    .append(a.confirmedName()).append("）；");
            answer = warning + "请避免把这款产品视为适合自己，向专业人员核实。\n\n" + answer;
        }
        return new IngredientResponse(answer, List.copyOf(products), List.copyOf(alerts),
                List.copyOf(sources.values()), readingSuggestions, limits);
    }

    private IngredientResponse.Product parseProduct(IngredientRequest.Product product) {
        if (product.name() == null || product.name().isBlank() || product.name().length() > 120)
            throw new IllegalArgumentException("产品名需为 1～120 字符");
        return new IngredientResponse.Product(product.name().strip(),
                IngredientNames.parse(product.name(), product.ingredients()));
    }

    private String write(Object value) {
        try { return json.writeValueAsString(value); }
        catch (Exception e) { throw new IllegalStateException(e); }
    }

    interface Analyzer { String answer(@UserMessage String prompt); }
}
