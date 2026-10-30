package com.skincarecopilot.ingredient;

import java.util.List;

public record IngredientResponse(String answer, List<Product> products, List<Alert> allergyAlerts,
                                 List<Source> sources, List<CreatorArticleService.Article> readingSuggestions,
                                 List<String> limitations) {
    public record Product(String name, List<IngredientNames.Parsed> ingredients) {}
    public record Alert(String product, String confirmedName, String matchedIngredient, int position) {}
    public record Source(String title, String url, String section, String verifiedOn) {}
}
