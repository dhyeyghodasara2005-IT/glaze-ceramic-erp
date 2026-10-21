package com.ceramicerp.user.dto;

import com.ceramicerp.user.Permission;

/** One permission with its UI group and description, for the role editor screen. */
public record PermissionResponse(String code, String group, String description) {
    public static PermissionResponse from(Permission p) {
        return new PermissionResponse(p.name(), p.getGroup(), p.getDescription());
    }
}
