package com.ceramicerp.user;

import com.ceramicerp.IntegrationTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class UserManagementIntegrationTest extends IntegrationTestBase {

    @Test
    void userWithoutPermissionGetsForbidden() throws Exception {
        String admin = adminToken();
        long roleId = createRole(admin, "Plant Viewer", "PLANT_VIEW");
        createUser(admin, "viewer1", "password-1", roleId);

        getAs(login("viewer1", "password-1"), "/api/users").andExpect(status().isForbidden());
    }

    @Test
    void deactivatedUserCannotLogin() throws Exception {
        String admin = adminToken();
        long roleId = createRole(admin, "Plant Viewer", "PLANT_VIEW");
        long userId = createUser(admin, "leaver", "password-1", roleId);

        postAs(admin, "/api/users/" + userId + "/deactivate", Map.of()).andExpect(status().isOk());

        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("username", "leaver", "password", "password-1"))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void cannotDeactivateYourself() throws Exception {
        String admin = adminToken();
        long myId = read(getAs(admin, "/api/auth/me")).get("id").asLong();
        postAs(admin, "/api/users/" + myId + "/deactivate", Map.of())
                .andExpect(status().isConflict());
    }

    @Test
    void lastSuperAdminCannotLoseTheRole() throws Exception {
        String admin = adminToken();
        long myId = read(getAs(admin, "/api/auth/me")).get("id").asLong();
        long otherRole = createRole(admin, "Plain", "PLANT_VIEW");

        putAs(admin, "/api/users/" + myId, Map.of(
                "fullName", "Admin", "roleIds", new Long[]{otherRole}, "allPlantsAccess", true))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("At least one active Super Admin must remain"));
    }

    @Test
    void userManagerCannotEscalatePrivileges() throws Exception {
        String admin = adminToken();
        long hrRole = createRole(admin, "HR", "USER_VIEW", "USER_MANAGE", "ROLE_VIEW");
        createUser(admin, "hr1", "password-1", hrRole);
        String hr = login("hr1", "password-1");

        long superAdminId = -1;
        for (var role : read(getAs(admin, "/api/roles"))) {
            if (role.get("name").asText().equals(Role.SUPER_ADMIN)) {
                superAdminId = role.get("id").asLong();
            }
        }

        // HR cannot create a Super Admin...
        postAs(hr, "/api/users", Map.of("username", "sneaky", "fullName", "Sneaky", "password", "password-1",
                "roleIds", new Long[]{superAdminId}))
                .andExpect(status().isForbidden());

        // ...or reset the real admin's password
        long adminId = read(getAs(admin, "/api/auth/me")).get("id").asLong();
        postAs(hr, "/api/users/" + adminId + "/reset-password", Map.of("newPassword", "hijacked-1"))
                .andExpect(status().isForbidden());
    }

    @Test
    void roleInUseCannotBeDeleted() throws Exception {
        String admin = adminToken();
        long roleId = createRole(admin, "Dispatch Clerk", "PLANT_VIEW");
        createUser(admin, "clerk1", "password-1", roleId);

        mvc.perform(delete("/api/roles/" + roleId).header("Authorization", "Bearer " + admin))
                .andExpect(status().isConflict());
    }

    @Test
    void superAdminRoleIsProtected() throws Exception {
        String admin = adminToken();
        for (var role : read(getAs(admin, "/api/roles"))) {
            if (role.get("systemRole").asBoolean()) {
                putAs(admin, "/api/roles/" + role.get("id").asLong(),
                        Map.of("name", "Renamed", "permissions", new String[]{}))
                        .andExpect(status().isConflict());
            }
        }
    }

    @Test
    void searchFindsUsersByName() throws Exception {
        String admin = adminToken();
        long roleId = createRole(admin, "Viewer", "PLANT_VIEW");
        createUser(admin, "sureshbhai", "password-1", roleId);

        getAs(admin, "/api/users?search=SURESH")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].username").value("sureshbhai"));
    }
}
