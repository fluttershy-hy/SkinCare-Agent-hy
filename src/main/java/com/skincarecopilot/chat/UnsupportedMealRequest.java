package com.skincarecopilot.chat;

import java.util.regex.Pattern;

final class UnsupportedMealRequest {
    private static final Pattern MEAL_REQUEST = Pattern.compile(
            "餐食|食谱|菜谱|菜单|三餐|早餐|午餐|晚餐|加餐|饮食建议|饮食计划|饮食框架|"
                    + "一日饮食|吃什么|能吃什么|怎么吃|如何吃|推荐.*(?:食物|食材|吃的)|meal plan",
            Pattern.CASE_INSENSITIVE);

    private UnsupportedMealRequest() {
    }

    static boolean matches(String message) {
        return MEAL_REQUEST.matcher(message).find();
    }
}
