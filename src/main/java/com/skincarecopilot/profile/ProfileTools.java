package com.skincarecopilot.profile;

import dev.langchain4j.agent.tool.Tool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ProfileTools {
    private static final Logger log = LoggerFactory.getLogger(ProfileTools.class);
    private final ProfileService profiles;
    private final String userId;

    public ProfileTools(ProfileService profiles, String userId) {
        this.profiles = profiles;
        this.userId = userId;
    }

    @Tool("查询当前用户主动确认并保存的肤质和过敏原；回答个人画像问题前调用。此工具只读，不会保存聊天中提到的信息。")
    public String getConfirmedProfile() {
        // 每个请求新建工具实例，模型无需提供 userId，也不能通过工具参数改查其他用户。
        log.info("tool=getConfirmedProfile userId={}", userId);
        return profiles.find(userId)
                .map(profile -> "已确认肤质：" + profile.skinType() + "；已确认过敏原："
                        + (profile.allergens().isEmpty() ? "未登记" : String.join("、", profile.allergens())))
                .orElse("当前用户尚未登记确认的画像");
    }
}
