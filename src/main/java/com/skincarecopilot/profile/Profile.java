package com.skincarecopilot.profile;

import java.util.List;

public record Profile(String userId, String skinType, List<String> allergens) {
}
