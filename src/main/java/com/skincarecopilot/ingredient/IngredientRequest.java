package com.skincarecopilot.ingredient;

import java.util.List;

public record IngredientRequest(String userId, Product first, Product second, String skinType,
                                String concern, List<String> confirmedIngredientAllergies,
                                boolean allergiesConfirmed, String question, String bodyArea,
                                String currentState, Boolean sensitive) {
    public IngredientRequest(String userId, Product first, Product second, String skinType,
                             String concern, List<String> confirmedIngredientAllergies,
                             boolean allergiesConfirmed, String question) {
        this(userId, first, second, skinType, concern, confirmedIngredientAllergies,
                allergiesConfirmed, question, null, null, null);
    }
    public record Product(String name, String ingredients) {}
}
