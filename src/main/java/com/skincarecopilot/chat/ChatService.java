package com.skincarecopilot.chat;

import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.SystemMessage;
import com.skincarecopilot.profile.ProfileService;
import com.skincarecopilot.profile.ProfileTools;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class ChatService {
    private final String apiKey;
    private final String baseUrl;
    private final String modelName;
    private final ProfileService profiles;

    public ChatService(@Value("${SKINCARE_LLM_API_KEY:}") String apiKey,
                       @Value("${SKINCARE_LLM_BASE_URL:https://api.deepseek.com}") String baseUrl,
                       @Value("${SKINCARE_LLM_MODEL:deepseek-flash}") String modelName,
                       ProfileService profiles) {
        this.apiKey = apiKey;
        this.baseUrl = baseUrl;
        this.modelName = modelName;
        this.profiles = profiles;
    }

    public String reply(String userId, String message) {
        if (apiKey.isBlank()) {
            throw new ModelNotConfiguredException();
        }
        // Day 2 仍是单轮对话；每次请求只绑定当前用户的只读画像工具。
        var model = OpenAiChatModel.builder()
                .apiKey(apiKey)
                .baseUrl(baseUrl)
                .modelName(modelName)
                .build();
        Assistant assistant = AiServices.builder(Assistant.class)
                .chatModel(model)
                .tools(new ProfileTools(profiles, userId))
                .build();
        return assistant.chat(message);
    }

    interface Assistant {
        @SystemMessage("你是 SkinCare Copilot 的演示助手。仅做日常信息整理，不诊断疾病，"
                + "不提供处方、药物剂量或保证有效的治疗方案。询问当前用户画像时，必须先调用画像查询工具；"
                + "绝不把聊天中的推断当作已确认画像。尚无知识库和餐食过敏原校验，"
                + "不要生成餐食方案、菜单、具体食材搭配或忌口建议。遇到需要核实的护肤或饮食事实，"
                + "说明目前无法核实来源，不编造引用。")
        String chat(String message);
    }
}
