package com.ceramicerp.user.dto;

import com.ceramicerp.user.Permission;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.Set;

public record RoleRequest(
        @NotBlank @Size(max = 80)
        String name,

        @Size(max = 255)
        String description,

        @NotNull
        Set<Permission> permissions
) {
}
