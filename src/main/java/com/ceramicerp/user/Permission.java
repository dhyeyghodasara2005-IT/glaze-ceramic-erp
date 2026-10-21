package com.ceramicerp.user;

/**
 * Every action the system can check. Permissions are fixed in code (because code checks them),
 * while ROLES are created freely by the admin and are just a named set of these permissions.
 *
 * To add a module later: add its permissions here. The "Super Admin" role picks them up
 * automatically on the next start (see BootstrapDataInitializer).
 */
public enum Permission {

    COMPANY_VIEW("Organization", "View companies"),
    COMPANY_MANAGE("Organization", "Create and edit companies"),
    PLANT_VIEW("Organization", "View plants"),
    PLANT_MANAGE("Organization", "Create and edit plants"),

    USER_VIEW("Users & Access", "View users"),
    USER_MANAGE("Users & Access", "Create, edit, deactivate users and reset passwords"),
    ROLE_VIEW("Users & Access", "View roles and permissions"),
    ROLE_MANAGE("Users & Access", "Create, edit and delete roles");

    private final String group;
    private final String description;

    Permission(String group, String description) {
        this.group = group;
        this.description = description;
    }

    public String getGroup() {
        return group;
    }

    public String getDescription() {
        return description;
    }
}
