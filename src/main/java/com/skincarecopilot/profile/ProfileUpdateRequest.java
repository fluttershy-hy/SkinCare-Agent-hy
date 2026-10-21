package com.skincarecopilot.profile;

import java.util.List;

public record ProfileUpdateRequest(String skinType, List<String> allergens, boolean confirmed) {
}
