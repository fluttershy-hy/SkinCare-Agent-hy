package com.skincarecopilot.chat;

import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/** Day 6: conservative, inspectable rules. Factual skin/diet questions always retrieve. */
@Component
public class ChatRouter {
    private static final Pattern DIRECT_PROFILE = Pattern.compile("^(?:我的(?:肤质|皮肤类型|过敏原|画像|档案)(?:是什么|有哪些|呢|\\?|？)?|查询我的画像|查询我的肤质)$");
    private static final Pattern PROFILE = Pattern.compile("我的(?:肤质|皮肤类型|过敏原|画像|档案)|我(?:登记|保存|确认)(?:了|过)什么|记得我(?:的|是)");
    private static final Pattern FACT = Pattern.compile("痤疮|粉刺|护肤|皮肤|肤质|过敏|泛红|湿疹|玫瑰痤疮|接触性皮炎|保湿|防晒|清洁|成分|香料|饮食|升糖|乳制品|牛奶|食物|痘|发红|刺激|润肤|敏感|祛痘|治痘|烟酰胺|玻尿酸|水杨酸|维生素C|视黄醇|A醇|果酸|神经酰胺|防腐剂|精华|面霜|乳液|爽肤水|面膜|抗氧化|美白|控油|毛孔|刷酸|吃.*(?:痘|皮肤)");
    private static final Pattern PERSONAL = Pattern.compile("我(?:的|是|有|用|吃)|根据我|结合我|适合我|对我|给我|我该|我能|我可以|我应该");

    public ChatRoute route(String message) {
        if (DIRECT_PROFILE.matcher(message).matches()) return ChatRoute.PROFILE;
        boolean profile = PROFILE.matcher(message).find();
        boolean fact = FACT.matcher(message).find();
        if (fact && (profile || PERSONAL.matcher(message).find())) return ChatRoute.COMBINED;
        if (profile) return ChatRoute.PROFILE;
        if (fact) return ChatRoute.KNOWLEDGE;
        return ChatRoute.SMALL_TALK;
    }

    public boolean isContextualFollowUp(String message) {
        return message.matches(".*(?:结合我的情况|根据我的情况|接着说|那我该怎么办|给点建议|刚才说的|这个产品|它的成分).*之?.*");
    }
}
