package com.skincarecopilot.chat;

import dev.langchain4j.model.openai.OpenAiChatModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class ChatService {
    private final String apiKey;
    private final String baseUrl;
    private final String modelName;

    public ChatService(@Value("${SKINCARE_LLM_API_KEY:}") String apiKey,
                       @Value("${SKINCARE_LLM_BASE_URL:https://api.openai.com/v1}") String baseUrl,
                       @Value("${SKINCARE_LLM_MODEL:gpt-4o-mini}") String modelName) {
        this.apiKey = apiKey;
        this.baseUrl = baseUrl;
        this.modelName = modelName;
    }

    public String reply(String message) {
        if (apiKey.isBlank()) {
            throw new ModelNotConfiguredException();
        }
        // Day 1 每次仅发送本次消息：目前没有会话记忆，也不读取用户画像。
        var model = OpenAiChatModel.builder()
                .apiKey(apiKey)
                .baseUrl(baseUrl)
                .modelName(modelName)
                .build();
        return model.chat("你是 SkinCare Copilot 的演示助手。仅做日常信息整理，不诊断疾病，"
                + "不提供处方、药物剂量或保证有效的治疗方案。尚无知识库，遇到需要核实的"
                + "护肤或饮食事实，请说明目前无法核实来源，不要编造引用。用户消息：" + message);
    }
}
