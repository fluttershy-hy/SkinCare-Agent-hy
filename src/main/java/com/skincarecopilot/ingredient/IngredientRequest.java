package com.skincarecopilot.ingredient;

import java.util.List;

public record IngredientRequest(String userId, Product first, Product second, String skinType,
                                String concern, List<String> confirmedIngredientAllergies,
                                boolean allergiesConfirmed, String question, String bodyArea,
                                String currentState, Boolean sensitive, boolean productIngredientsConfirmed) {
    public IngredientRequest(String userId, Product first, Product second, String skinType,
                             String concern, List<String> confirmedIngredientAllergies,
                             boolean allergiesConfirmed, String question) {
        this(userId, first, second, skinType, concern, confirmedIngredientAllergies,
                allergiesConfirmed, question, null, null, null, false);
    }
    public IngredientRequest(String userId, Product first, Product second, String skinType,
                             String concern, List<String> confirmedIngredientAllergies,
                             boolean allergiesConfirmed, String question, String bodyArea,
                             String currentState, Boolean sensitive) {
        this(userId, first, second, skinType, concern, confirmedIngredientAllergies,
                allergiesConfirmed, question, bodyArea, currentState, sensitive, false);
    }
    public record Product(String name, String ingredients) {}
}
