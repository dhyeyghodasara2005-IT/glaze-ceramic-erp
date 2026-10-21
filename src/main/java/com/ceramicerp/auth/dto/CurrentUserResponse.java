package com.ceramicerp.auth.dto;

import com.ceramicerp.user.dto.PlantRef;

import java.util.List;

/**
 * Everything the frontend needs about the logged-in user: which menus to show (permissions),
 * which plants to offer in the plant switcher, and whether to force a password change.
 */
public record CurrentUserResponse(
        Long id,
        String username,
        String fullName,
        String preferredLanguage,
        boolean mustChangePassword,
        boolean allPlantsAccess,
        List<String> roles,
        List<String> permissions,
        List<PlantRef> plants
) {
}
