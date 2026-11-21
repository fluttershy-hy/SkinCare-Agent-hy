package com.skincarecopilot.chat;

import com.skincarecopilot.knowledge.KnowledgeService;
import com.skincarecopilot.knowledge.KnowledgeRelevanceFilter;
import com.skincarecopilot.profile.ProfileService;
import com.skincarecopilot.profile.ProfileTools;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class ChatService {
    private static final Logger log = LoggerFactory.getLogger(ChatService.class);
    private static final String INSUFFICIENT = "目前资料不足，没有找到足够相关的内容。你可以在接下来的三天记录不适出现的时间、频率和近期使用的护肤品，咨询皮肤科时带上这份清单。近期若症状持续或加重，请及时就医。";
    private final String apiKey;
    private final String baseUrl;
    private final String modelName;
    private final ProfileService profiles;
    private final ChatMemoryRegistry memories;
    private final KnowledgeService knowledge;
    private final KnowledgeRelevanceFilter relevance;
    private final ChatRouter router;
    private final SkinConsultation consultation;
    private final ConcurrentHashMap<ConversationId, String> lastGroundedQuestion = new ConcurrentHashMap<>();

    @Autowired
    public ChatService(@Value("${SKINCARE_LLM_API_KEY:}") String apiKey,
                       @Value("${SKINCARE_LLM_BASE_URL:https://api.deepseek.com}") String baseUrl,
                       @Value("${SKINCARE_LLM_MODEL:deepseek-flash}") String modelName,
                       ProfileService profiles, ChatMemoryRegistry memories,
                       KnowledgeService knowledge, ChatRouter router, KnowledgeRelevanceFilter relevance) {
        this.apiKey = apiKey;
        this.baseUrl = baseUrl;
        this.modelName = modelName;
        this.profiles = profiles;
        this.memories = memories;
        this.knowledge = knowledge;
        this.relevance = relevance;
        this.router = router;
        this.consultation = new SkinConsultation();
    }

    // Retains the existing package-level test entry point for Day 3 memory isolation.
    ChatService(String apiKey, String baseUrl, String modelName, ProfileService profiles,
                ChatMemoryRegistry memories) {
        this(apiKey, baseUrl, modelName, profiles, memories, null, new ChatRouter(), new KnowledgeRelevanceFilter());
    }

    public String reply(String userId, String sessionId, String message) {
        return replyWithSources(userId, sessionId, message).answer();
    }

    public ChatResponse replyWithSources(String userId, String sessionId, String message) {
        ConversationId id = new ConversationId(userId, sessionId);
        var consultationResult = consultation.next(id, message);
        if (consultationResult != null && consultationResult.answer() != null)
            return new ChatResponse(consultationResult.answer(), ChatRoute.COMBINED, List.of(), consultationResult.quickReplies());
        if (consultationResult != null && consultationResult.retrievalQuery() != null) message = consultationResult.retrievalQuery();
        ChatRoute route = router.route(message);
        // A generic follow-up must re-retrieve with the previous question; chat memory alone
        // is not evidence and must never cause old citations to be silently reused.
        String previousQuestion = lastGroundedQuestion.get(id);
        boolean groundedFollowUp = route == ChatRoute.SMALL_TALK && previousQuestion != null
                && router.isContextualFollowUp(message);
        if (groundedFollowUp) route = ChatRoute.COMBINED;
        log.info("route={} userId={} sessionId={}", route, userId, sessionId);
        List<ChatResponse.Source> sources = List.of();
        String prompt = message;
        if (route == ChatRoute.KNOWLEDGE || route == ChatRoute.COMBINED) {
            if (knowledge == null) throw new IllegalStateException("知识检索服务未配置");
            var result = knowledge.search(groundedFollowUp ? previousQuestion + "。" + message : message, 3);
            var relevantHits = new java.util.ArrayList<>(relevance.relevant(groundedFollowUp ? previousQuestion + "。" + message : message, result.matches()));
            if (relevantHits.isEmpty()) {
                // Do not ask the model to invent an answer or a citation.
                if (consultationResult != null) consultation.observe(id);
                return new ChatResponse(INSUFFICIENT + (consultationResult == null ? "" : "\n\n需要我帮你整理观察记录吗？"), route, List.of(), consultationResult == null ? List.of() : List.of("需要记录", "暂不需要"));
            }
            var unique = new LinkedHashMap<String, ChatResponse.Source>();
            var context = new StringBuilder("先直接回答问题，资料支持时结合内分泌机制、日常护理和产品类型作自然解释，只使用真正相关的片段，不要逐条复述检索结果。先给实用建议，再在最后简短说明资料尚未覆盖的内容，不要反复讲资料不足。AAD/NICE 可用于其适用人群内的一般知识；Book 是带章节定位的书籍解释，不等同于最新临床指南；博主文章只能作为通俗解释和观点；体外论文不能外推到人体或成品。正文自然解释，不要反复报作者姓名；用到的知识点在句末标注来源编号，来源信息由页面在回答下方展示。无相关依据时简洁说明缺什么，不要用无关片段凑答案。\n");
            for (var hit : relevantHits) {
                var chunk = hit.chunk();
                var source = chunk.source();
                unique.putIfAbsent(source.id(), new ChatResponse.Source(source.id(), source.publisher(),
                        source.title(), source.section(), source.url(), source.verifiedOn(), source.originalExcerpt()));
                context.append('[').append(source.id()).append(" · ").append(source.publisher())
                        .append("] ").append(chunk.text()).append('\n');
            }
            sources = List.copyOf(unique.values());
            prompt = context.append("用户问题：").append(message).toString();
            if (consultationResult != null) prompt += "\n本轮请不要继续提问，程序会在回答后附上一个观察问题。";
            if (route == ChatRoute.COMBINED) {
                // Personal context comes only from confirmed H2 data, never inferred from chat.
                String confirmed = profiles.find(userId)
                        .map(profile -> "已确认肤质：" + profile.skinType() + "；已确认过敏原："
                                + (profile.allergens().isEmpty() ? "未登记" : String.join("、", profile.allergens())))
                        .orElse("当前用户尚无已确认画像");
                prompt += "\n" + confirmed + "。只在适用范围内结合画像；不得推断新的过敏史。";
            }
        }
        if (apiKey.isBlank()) throw new ModelNotConfiguredException();
        var memory = memories.get(id);
        var model = OpenAiChatModel.builder().apiKey(apiKey).baseUrl(baseUrl).modelName(modelName).build();
        Assistant assistant = AiServices.builder(Assistant.class)
                .chatModel(model)
                .chatMemoryProvider(memoryId -> memories.get((ConversationId) memoryId))
                .tools(new ProfileTools(profiles, userId)).build();
        synchronized (memory) {
            String answer = assistant.chat(id, prompt);
            if (!sources.isEmpty()) lastGroundedQuestion.put(id, groundedFollowUp ? previousQuestion : message);
            // Citations are attached by Java from actual matches, never invented by model text.
            if (consultationResult != null) {
                consultation.observe(id);
                answer += "\n\n接下来需要整理观察记录吗？";
                return new ChatResponse(answer, route, sources, List.of("需要记录", "暂不需要"));
            }
            return new ChatResponse(answer, route, sources);
        }
    }

    interface Assistant {
        @SystemMessage("你是一位专业、耐心、懂成分的护肤陪伴顾问，不提供医学诊断、处方或药物剂量。"
                + "根据参考资料和用户亲口提供的信息，用自己的话回答，不大段复制原文，不把未确认的诱因或过敏当成事实。"
                + "按【共情回应】【当前分析与建议】【下一步排查】组织，语气自然，建议具体简洁。"
                + "如果资料不足就明确说不知道，建议记录症状和近期护肤品以备咨询；不要重复列举无关来源。"
                + "每次最多询问一个关键问题，用户已明确回答的问题不要重复询问；工具读取的是已确认画像，切勿自动保存推断的过敏史。"
                + "引用片段只作为其适用范围内的依据，培养或细胞实验不能直接证明成品或个人效果；饮食不能被描述为治疗痤疮。"
                + "餐食方案由专用 Java 校验流程处理，聊天服务不生成菜单。")
        String chat(@MemoryId ConversationId id, @UserMessage String message);
    }
}
