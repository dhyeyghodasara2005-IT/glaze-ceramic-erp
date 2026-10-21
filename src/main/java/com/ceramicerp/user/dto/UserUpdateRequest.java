package com.ceramicerp.user.dto;

import com.ceramicerp.common.ValidationPatterns;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.Set;

/** Username and password are changed through separate endpoints, so they are not here. */
public record UserUpdateRequest(
        @NotBlank @Size(max = 150)
        String fullName,

        @Pattern(regexp = ValidationPatterns.PHONE, message = "Invalid phone number")
        String phone,

        @Email @Size(max = 150)
        String email,

        @NotEmpty(message = "Select at least one role")
        Set<Long> roleIds,

        Set<Long> plantIds,

        boolean allPlantsAccess,

        @Pattern(regexp = UserRules.LANGUAGE, message = "Language must be en, gu or hi")
        String preferredLanguage
) {
}
