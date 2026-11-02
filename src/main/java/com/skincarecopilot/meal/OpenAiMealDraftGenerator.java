package com.skincarecopilot.meal;

import com.skincarecopilot.chat.ModelNotConfiguredException;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.UserMessage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
class OpenAiMealDraftGenerator implements MealDraftGenerator {
    private final String apiKey, baseUrl, modelName;

    OpenAiMealDraftGenerator(@Value("${SKINCARE_LLM_API_KEY:}") String apiKey,
                             @Value("${SKINCARE_LLM_BASE_URL:https://api.deepseek.com}") String baseUrl,
                             @Value("${SKINCARE_LLM_MODEL:deepseek-flash}") String modelName) {
        this.apiKey = apiKey;
        this.baseUrl = baseUrl;
        this.modelName = modelName;
    }

    @Override public String generate(String prompt) {
        if (apiKey.isBlank()) throw new ModelNotConfiguredException();
        var model = OpenAiChatModel.builder().apiKey(apiKey).baseUrl(baseUrl).modelName(modelName).build();
        return AiServices.create(Generator.class, model).generate(prompt);
    }

    interface Generator { String generate(@UserMessage String prompt); }
}
