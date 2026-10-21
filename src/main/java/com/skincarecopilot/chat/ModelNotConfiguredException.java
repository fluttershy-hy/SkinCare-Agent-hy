package com.skincarecopilot.chat;

public class ModelNotConfiguredException extends RuntimeException {
    public ModelNotConfiguredException() {
        super("尚未配置 SKINCARE_LLM_API_KEY；请在运行环境设置模型密钥。");
    }
}
