package com.ceramicerp.user.dto;

import com.ceramicerp.user.Permission;
import com.ceramicerp.user.Role;

import java.util.List;

public record RoleResponse(
        Long id,
        String name,
        String description,
        boolean systemRole,
        List<String> permissions,
        long userCount,
        Long version
) {
    public static RoleResponse from(Role r, long userCount) {
        List<String> perms = r.getPermissions().stream().map(Permission::name).sorted().toList();
        return new RoleResponse(r.getId(), r.getName(), r.getDescription(), r.isSystemRole(), perms,
                userCount, r.getVersion());
    }
}
