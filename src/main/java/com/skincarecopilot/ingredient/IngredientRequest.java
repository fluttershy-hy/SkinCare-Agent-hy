package com.skincarecopilot.ingredient;

import java.util.List;

public record IngredientRequest(String userId, Product first, Product second, String skinType,
                                String concern, List<String> confirmedIngredientAllergies,
                                boolean allergiesConfirmed, String question) {
    public record Product(String name, String ingredients) {}
}
