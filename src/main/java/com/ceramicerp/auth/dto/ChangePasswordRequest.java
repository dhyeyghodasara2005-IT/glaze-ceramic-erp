package com.ceramicerp.auth.dto;

import com.ceramicerp.user.dto.UserRules;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ChangePasswordRequest(
        @NotBlank String currentPassword,

        @NotBlank @Size(min = UserRules.PASSWORD_MIN, max = UserRules.PASSWORD_MAX,
                message = "Password must be 8-72 characters")
        String newPassword
) {
}
