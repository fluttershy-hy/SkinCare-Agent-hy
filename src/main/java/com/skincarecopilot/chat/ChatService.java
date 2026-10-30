package com.skincarecopilot.chat;

import com.skincarecopilot.knowledge.KnowledgeService;
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
    private static final String INSUFFICIENT = "当前已核实资料不足，无法可靠回答这个问题。请查看知识片段与来源，或咨询皮肤科专业人员。";
    private final String apiKey;
    private final String baseUrl;
    private final String modelName;
    private final ProfileService profiles;
    private final ChatMemoryRegistry memories;
    private final KnowledgeService knowledge;
    private final ChatRouter router;
    private final ConcurrentHashMap<ConversationId, String> lastGroundedQuestion = new ConcurrentHashMap<>();

    @Autowired
    public ChatService(@Value("${SKINCARE_LLM_API_KEY:}") String apiKey,
                       @Value("${SKINCARE_LLM_BASE_URL:https://api.deepseek.com}") String baseUrl,
                       @Value("${SKINCARE_LLM_MODEL:deepseek-flash}") String modelName,
                       ProfileService profiles, ChatMemoryRegistry memories,
                       KnowledgeService knowledge, ChatRouter router) {
        this.apiKey = apiKey;
        this.baseUrl = baseUrl;
        this.modelName = modelName;
        this.profiles = profiles;
        this.memories = memories;
        this.knowledge = knowledge;
        this.router = router;
    }

    // Retains the existing package-level test entry point for Day 3 memory isolation.
    ChatService(String apiKey, String baseUrl, String modelName, ProfileService profiles,
                ChatMemoryRegistry memories) {
        this(apiKey, baseUrl, modelName, profiles, memories, null, new ChatRouter());
    }

    public String reply(String userId, String sessionId, String message) {
        return replyWithSources(userId, sessionId, message).answer();
    }

    public ChatResponse replyWithSources(String userId, String sessionId, String message) {
        ChatRoute route = router.route(message);
        ConversationId id = new ConversationId(userId, sessionId);
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
            if (result.matches().isEmpty()) {
                // Do not ask the model to invent an answer or a citation.
                return new ChatResponse(INSUFFICIENT, route, List.of());
            }
            var unique = new LinkedHashMap<String, ChatResponse.Source>();
            var context = new StringBuilder("只依据下列片段回答。AAD/NICE 是专业资料；MiriamMibao 是温唯的文章观点，必须以‘作者认为’归属，不能提升为医学事实。不同来源冲突时说明差异。资料不足时说不知道；保留适用范围。\n");
            for (var hit : result.matches()) {
                var chunk = hit.chunk();
                var source = chunk.source();
                unique.putIfAbsent(source.id(), new ChatResponse.Source(source.id(), source.publisher(),
                        source.title(), source.section(), source.url(), source.verifiedOn(), source.originalExcerpt()));
                context.append('[').append(source.id()).append(" · ").append(source.publisher())
                        .append("] ").append(chunk.text()).append('\n');
            }
            sources = List.copyOf(unique.values());
            prompt = context.append("用户问题：").append(message).toString();
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
            return new ChatResponse(answer, route, sources);
        }
    }

    interface Assistant {
        @SystemMessage("你是 SkinCare Copilot 信息整理演示助手。不诊断疾病，不开处方，不给药物剂量或保证有效的方案。"
                + "回答像认真、会讲人话的护肤知识讲解员：先直接回应用户关心的事，再用少量自然段解释理由和不确定之处；不要机械堆砌标题或列表。"
                + "只要提供了检索片段，必须只根据片段回答，说明适用范围和不确定性；没有依据就说明资料不足。"
                + "博主文章是作者观点，不等同于临床证据；用‘作者认为’归属，不能把作者判断当作确定的产品功效或个人致敏结论。"
                + "不要声称饮食能治疗痤疮，不要把泛红推断成过敏或确诊疾病。询问当前用户画像时必须先调用画像查询工具；"
                + "绝不把聊天推断当作已确认画像。不要生成餐食方案、菜单、具体食材搭配或忌口建议。")
        String chat(@MemoryId ConversationId id, @UserMessage String message);
    }
}
