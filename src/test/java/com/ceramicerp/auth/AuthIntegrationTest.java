package com.ceramicerp.auth;

import com.ceramicerp.IntegrationTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.util.Map;

import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AuthIntegrationTest extends IntegrationTestBase {

    @Test
    void adminCanLoginAndSeeAllPermissions() throws Exception {
        String token = adminToken();
        getAs(token, "/api/auth/me")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value(adminUsername))
                .andExpect(jsonPath("$.roles", hasItem("Super Admin")))
                .andExpect(jsonPath("$.permissions", hasItem("USER_MANAGE")))
                .andExpect(jsonPath("$.allPlantsAccess").value(true));
    }

    @Test
    void wrongPasswordIsRejected() throws Exception {
        mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("username", adminUsername, "password", "wrong-password"))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid username or password"));
    }

    @Test
    void protectedEndpointNeedsToken() throws Exception {
        mvc.perform(get("/api/users"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));
    }

    @Test
    void requestMistakesAreNotServerErrors() throws Exception {
        String token = adminToken();
        mvc.perform(get("/api/no-such-thing").header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
        mvc.perform(get("/api/dashboard").param("plantId", "1").header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Required parameter 'from' is not present."));
    }

    @Test
    void tamperedTokenIsRejected() throws Exception {
        // Change the first character of the signature part: the signature no longer matches.
        String token = adminToken();
        int i = token.lastIndexOf('.') + 1;
        char flipped = token.charAt(i) == 'A' ? 'B' : 'A';
        String tampered = token.substring(0, i) + flipped + token.substring(i + 1);
        getAs(tampered, "/api/auth/me").andExpect(status().isUnauthorized());
    }

    @Test
    void newUserMustChangePasswordThenCanUseNewOne() throws Exception {
        String admin = adminToken();
        long roleId = createRole(admin, "Viewer", "PLANT_VIEW");
        createUser(admin, "ramesh", "temp-pass-1", roleId);

        String token = login("ramesh", "temp-pass-1");
        getAs(token, "/api/auth/me").andExpect(jsonPath("$.mustChangePassword").value(true));

        postAs(token, "/api/auth/change-password",
                Map.of("currentPassword", "temp-pass-1", "newPassword", "new-pass-123"))
                .andExpect(status().isNoContent());

        String newToken = login("ramesh", "new-pass-123");
        getAs(newToken, "/api/auth/me").andExpect(jsonPath("$.mustChangePassword").value(false));
    }

    @Test
    void anyUserCanSaveTheirLanguage() throws Exception {
        String admin = adminToken();
        long roleId = createRole(admin, "Viewer", "PLANT_VIEW");
        createUser(admin, "kiran", "password-1", roleId);
        String token = login("kiran", "password-1");

        mvc.perform(patch("/api/auth/me/preferences").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content(json(Map.of("preferredLanguage", "gu"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.preferredLanguage").value("gu"));

        mvc.perform(patch("/api/auth/me/preferences").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content(json(Map.of("preferredLanguage", "fr"))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void changePasswordRequiresCorrectCurrentPassword() throws Exception {
        postAs(adminToken(), "/api/auth/change-password",
                Map.of("currentPassword", "not-it", "newPassword", "whatever-123"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Current password is incorrect"));
    }

    /** A password copied out of a PDF or a chat often brings an invisible space or line break: it still logs in. */
    @Test
    void aPasswordCopiedWithASpaceOrLineBreakStillLogsIn() throws Exception {
        String admin = adminToken();
        createUser(admin, "copied", "Test-Abc12345", createRole(admin, "Viewer C", "PLANT_VIEW"));

        for (String typed : new String[]{"Test-Abc12345 ", " Test-Abc12345", "Test-Abc12345\n", "\tTest-Abc12345\r\n"}) {
            mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                            .content(json(Map.of("username", "copied", "password", typed))))
                    .andExpect(status().isOk());
        }
        // ...but only spaces at the ends are forgiven: a wrong character is still wrong.
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("username", "copied", "password", "Test-Abc1234 "))))
                .andExpect(status().isUnauthorized());
        // Changing it works with a copied current password too.
        postAs(login("copied", "Test-Abc12345"), "/api/auth/change-password",
                Map.of("currentPassword", "Test-Abc12345 ", "newPassword", "Test-New12345"))
                .andExpect(status().isNoContent());
    }

    /** Since login ignores spaces at the ends, no password may be set with one. */
    @Test
    void aNewPasswordCantStartOrEndWithASpace() throws Exception {
        String admin = adminToken();
        long role = createRole(admin, "Viewer S", "PLANT_VIEW");
        postAs(admin, "/api/users", Map.of("username", "spacey", "fullName", "Spacey Test", "password", "Test-Abc12345 ",
                "roleIds", new Long[]{role}, "plantIds", new Long[]{}, "allPlantsAccess", false))
                .andExpect(status().isBadRequest());
        long id = createUser(admin, "spacey", "Test-Abc12345", role);
        postAs(admin, "/api/users/" + id + "/reset-password", Map.of("newPassword", " Test-Abc99999"))
                .andExpect(status().isBadRequest());
        postAs(login("spacey", "Test-Abc12345"), "/api/auth/change-password",
                Map.of("currentPassword", "Test-Abc12345", "newPassword", "Test-New12345 "))
                .andExpect(status().isBadRequest());
        // A space in the middle is fine.
        postAs(admin, "/api/users/" + id + "/reset-password", Map.of("newPassword", "Test Abc 99999"))
                .andExpect(status().isOk());
    }
}
