package com.ceramicerp.auth.dto;

import com.ceramicerp.user.dto.UserRules;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/** Settings a user may change for themselves (no admin permission needed). */
public record PreferencesRequest(
        @NotBlank @Pattern(regexp = UserRules.LANGUAGE, message = "Language must be en, gu or hi")
        String preferredLanguage
) {
}
