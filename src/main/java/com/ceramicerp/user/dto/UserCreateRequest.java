package com.ceramicerp.user.dto;

import com.ceramicerp.common.ValidationPatterns;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.Set;

public record UserCreateRequest(
        @NotBlank @Pattern(regexp = UserRules.USERNAME, message = "3-50 letters, digits, . _ or -")
        String username,

        @NotBlank @Size(max = 150)
        String fullName,

        @Pattern(regexp = ValidationPatterns.PHONE, message = "Invalid phone number")
        String phone,

        @Email @Size(max = 150)
        String email,

        /** Temporary password. The user must change it at first login. */
        @NotBlank @Size(min = UserRules.PASSWORD_MIN, max = UserRules.PASSWORD_MAX,
                message = "Password must be 8-72 characters")
        String password,

        @NotEmpty(message = "Select at least one role")
        Set<Long> roleIds,

        Set<Long> plantIds,

        boolean allPlantsAccess,

        @Pattern(regexp = UserRules.LANGUAGE, message = "Language must be en, gu or hi")
        String preferredLanguage
) {
}
