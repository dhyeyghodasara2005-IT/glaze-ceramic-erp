package com.ceramicerp.user.dto;

import com.ceramicerp.user.User;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;

/** A user as shown in the admin's user list. Never includes the password hash. */
public record UserResponse(
        Long id,
        String username,
        String fullName,
        String phone,
        String email,
        boolean active,
        boolean mustChangePassword,
        boolean allPlantsAccess,
        String preferredLanguage,
        Instant lastLoginAt,
        List<RoleRef> roles,
        List<PlantRef> plants,
        Long version
) {
    public static UserResponse from(User u) {
        return new UserResponse(u.getId(), u.getUsername(), u.getFullName(), u.getPhone(), u.getEmail(),
                u.isActive(), u.isMustChangePassword(), u.isAllPlantsAccess(), u.getPreferredLanguage(),
                u.getLastLoginAt(),
                u.getRoles().stream().map(RoleRef::from).sorted(Comparator.comparing(RoleRef::name)).toList(),
                u.getPlants().stream().map(PlantRef::from).sorted(Comparator.comparing(PlantRef::name)).toList(),
                u.getVersion());
    }
}
