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
    ROLE_MANAGE("Users & Access", "Create, edit and delete roles"),

    MASTER_VIEW("Masters", "View product masters (sizes, finishes, categories, grades, designs)"),
    MASTER_MANAGE("Masters", "Create and edit product masters"),

    PARTY_VIEW("Parties", "View dealers, suppliers and transporters"),
    PARTY_MANAGE("Parties", "Create and edit dealers, suppliers and transporters"),

    STORE_VIEW("Store", "View stock, godowns and store documents"),
    STORE_ENTRY("Store", "Create and post GRNs, issues and stock adjustments; manage godowns"),
    STORE_CANCEL("Store", "Cancel posted store documents (reverses stock)"),

    // One ENTRY permission per production division: give it to that division's supervisor role.
    PRODUCTION_VIEW("Production", "View production entries, batches and runs"),
    BODY_PREP_ENTRY("Production", "Body preparation supervisor: ball mill charges, slip tests, spray dryer runs"),
    PRESS_ENTRY("Production", "Press & dryer supervisor: design lots, press and dryer shift entries"),
    GLAZE_ENTRY("Production", "Glaze & print supervisor: glaze line and digital printer shift entries"),
    KILN_ENTRY("Production", "Kiln supervisor: kiln shift entries"),
    QUALITY_CHECK("Production", "Quality checker: bend, size and thickness checks in the cooling area"),
    POLISH_ENTRY("Production", "Polishing supervisor: polishing line shift entries"),
    PACKING_ENTRY("Production", "Packing supervisor: sorting & packing entries - boxes by grade, shade and caliber"),
    PRODUCTION_CANCEL("Production", "Cancel posted production entries (reverses their stock)"),

    FG_VIEW("Finished goods", "View finished-goods stock, godowns and pillars"),
    FG_ENTRY("Finished goods", "Warehouse: godowns and pillars, opening stock, breakage and regrading"),
    FG_CANCEL("Finished goods", "Cancel posted finished-goods documents (reverses their stock)"),

    SALES_VIEW("Sales", "View sales orders (loading slips)"),
    SALES_ENTRY("Sales", "Enter, match and confirm sales orders from loading slips"),
    SALES_CANCEL("Sales", "Cancel confirmed sales orders");

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
