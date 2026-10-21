package com.ceramicerp.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Admin sets a new temporary password; the user must change it at next login. */
public record ResetPasswordRequest(
        @NotBlank @Size(min = UserRules.PASSWORD_MIN, max = UserRules.PASSWORD_MAX,
                message = "Password must be 8-72 characters")
        String newPassword
) {
}
