package com.skincarecopilot.meal;

import java.util.List;

/** 模型输出的结构化草稿；仅通过 Java 校验后才可展示。 */
public record MealDraft(List<Meal> meals) {
    public record Meal(String name, String description, List<String> ingredients) {}
}
