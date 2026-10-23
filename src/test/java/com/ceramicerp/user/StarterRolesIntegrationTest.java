package com.ceramicerp.user;

import com.ceramicerp.IntegrationTestBase;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.DataSourceUtils;
import org.springframework.jdbc.datasource.init.ScriptUtils;

import javax.sql.DataSource;
import java.sql.Connection;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** V9, V10 and V11 seed one ready-made role per job. They must be ordinary, editable roles - and never touch the user's own. */
class StarterRolesIntegrationTest extends IntegrationTestBase {

    @Autowired
    DataSource dataSource;

    @Test
    void oneStarterRolePerJobIsReady() throws Exception {
        String admin = adminToken();
        assertThat(permissionsOf(admin, "Store keeper")).containsExactlyInAnyOrder("STORE_VIEW", "STORE_ENTRY");
        assertThat(permissionsOf(admin, "Body prep supervisor")).containsExactlyInAnyOrder("PRODUCTION_VIEW", "BODY_PREP_ENTRY");
        assertThat(permissionsOf(admin, "Press supervisor")).containsExactlyInAnyOrder("PRODUCTION_VIEW", "PRESS_ENTRY");
        assertThat(permissionsOf(admin, "Glaze supervisor")).containsExactlyInAnyOrder("PRODUCTION_VIEW", "GLAZE_ENTRY");
        assertThat(permissionsOf(admin, "Kiln supervisor")).containsExactlyInAnyOrder("PRODUCTION_VIEW", "KILN_ENTRY");
        assertThat(permissionsOf(admin, "Polish supervisor")).containsExactlyInAnyOrder("PRODUCTION_VIEW", "POLISH_ENTRY");
        // V11: the packing supervisor also sees finished-goods stock (they choose pillars by it)
        assertThat(permissionsOf(admin, "Packing supervisor")).containsExactlyInAnyOrder("PRODUCTION_VIEW", "PACKING_ENTRY", "FG_VIEW");
        assertThat(permissionsOf(admin, "FG warehouse keeper")).containsExactlyInAnyOrder("FG_VIEW", "FG_ENTRY");
        assertThat(permissionsOf(admin, "Quality checker")).containsExactlyInAnyOrder("PRODUCTION_VIEW", "QUALITY_CHECK");
        // V10 gave the starter manager the new divisions too
        assertThat(permissionsOf(admin, "Production manager")).contains("PRODUCTION_CANCEL", "KILN_ENTRY", "BODY_PREP_ENTRY",
                "POLISH_ENTRY", "PACKING_ENTRY", "QUALITY_CHECK");
        assertThat(permissionsOf(admin, "Viewer (read only)")).allMatch(p -> p.endsWith("_VIEW"));
    }

    @Test
    void starterRolesAreOrdinaryRoles() throws Exception {
        String admin = adminToken();
        JsonNode keeper = role(admin, "Kiln supervisor");
        assertThat(keeper.get("systemRole").asBoolean()).isFalse();

        // They can be renamed and changed...
        putAs(admin, "/api/roles/" + keeper.get("id").asLong(),
                Map.of("name", "Kiln in-charge", "permissions", new String[]{"PRODUCTION_VIEW", "KILN_ENTRY", "PRODUCTION_CANCEL"}))
                .andExpect(status().isOk());
        // ...and deleted while nobody uses them.
        mvc.perform(delete("/api/roles/" + role(admin, "Glaze supervisor").get("id").asLong())
                        .header("Authorization", "Bearer " + admin))
                .andExpect(status().isNoContent());
    }

    @Test
    void runningTheScriptAgainNeverTouchesTheUsersOwnRoles() throws Exception {
        String admin = adminToken();
        // The user deleted the starter "Store keeper" and made their own with that name.
        mvc.perform(delete("/api/roles/" + role(admin, "Store keeper").get("id").asLong())
                .header("Authorization", "Bearer " + admin)).andExpect(status().isNoContent());
        createRole(admin, "Store keeper", "STORE_VIEW");

        // Run V9 again on the same connection (inside this test's transaction).
        Connection connection = DataSourceUtils.getConnection(dataSource);
        ScriptUtils.executeSqlScript(connection, new ClassPathResource("db/migration/V9__starter_roles.sql"));

        // Their role kept exactly its one permission, and no starter role was doubled.
        assertThat(permissionsOf(admin, "Store keeper")).containsExactly("STORE_VIEW");
        long kilnRoles = 0;
        for (JsonNode r : read(getAs(admin, "/api/roles"))) {
            if (r.get("name").asText().equals("Kiln supervisor")) kilnRoles++;
        }
        assertThat(kilnRoles).isEqualTo(1);
    }

    private JsonNode role(String token, String name) throws Exception {
        for (JsonNode r : read(getAs(token, "/api/roles"))) {
            if (r.get("name").asText().equals(name)) return r;
        }
        throw new AssertionError("No role named " + name);
    }

    private List<String> permissionsOf(String token, String name) throws Exception {
        List<String> permissions = new ArrayList<>();
        role(token, name).get("permissions").forEach(p -> permissions.add(p.asText()));
        return permissions;
    }
}
