package com.ceramicerp.user.dto;

import com.ceramicerp.user.Role;

/** Short form of a role, used inside other responses. */
public record RoleRef(Long id, String name) {
    public static RoleRef from(Role r) {
        return new RoleRef(r.getId(), r.getName());
    }
}
