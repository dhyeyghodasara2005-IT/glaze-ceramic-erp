package com.ceramicerp.master;

import com.ceramicerp.IntegrationTestBase;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class TileSizeIntegrationTest extends IntegrationTestBase {

    private static final String URL = "/api/masters/sizes";

    /** A valid request body. Each test copies it and changes one thing. */
    private Map<String, Object> size(String code, int lengthMm, int widthMm, int pcsPerBox) {
        Map<String, Object> body = new HashMap<>();
        body.put("code", code);
        body.put("name", lengthMm + " x " + widthMm + " mm");
        body.put("lengthMm", lengthMm);
        body.put("widthMm", widthMm);
        body.put("thicknessMm", 9.0);
        body.put("pcsPerBox", pcsPerBox);
        return body;
    }

    @Test
    void startingDataIncludes600x1200() throws Exception {
        getAs(adminToken(), URL)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].code", hasItem("600X1200")));
    }

    @Test
    void createCalculatesSqmPerBoxAndUppercasesCode() throws Exception {
        postAs(adminToken(), URL, size("800x1600", 800, 1600, 2))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value("800X1600"))
                .andExpect(jsonPath("$.sqmPerBox").value(2.56));
    }

    @Test
    void updateRecalculatesSqmPerBox() throws Exception {
        String admin = adminToken();
        Map<String, Object> body = size("600X600", 600, 600, 4);
        long id = read(postAs(admin, URL, body).andExpect(status().isCreated())).get("id").asLong();

        body.put("pcsPerBox", 3);
        putAs(admin, URL + "/" + id, body)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sqmPerBox").value(1.08));
    }

    @Test
    void duplicateCodeIsRejected() throws Exception {
        // "600X1200" already exists from the V2 starting data; lower-case must still be caught.
        postAs(adminToken(), URL, size("600x1200", 600, 1200, 2))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Size code already exists: 600X1200"));
    }

    @Test
    void invalidValuesAreReportedPerField() throws Exception {
        Map<String, Object> body = size("BAD", 0, 600, 2);
        body.remove("pcsPerBox");
        postAs(adminToken(), URL, body)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.lengthMm").exists())
                .andExpect(jsonPath("$.fieldErrors.pcsPerBox").exists());
    }

    @Test
    void viewerCanListButCannotCreate() throws Exception {
        String admin = adminToken();
        long roleId = createRole(admin, "Master Viewer", "MASTER_VIEW");
        createUser(admin, "viewer.m", "password-1", roleId);
        String viewer = login("viewer.m", "password-1");

        getAs(viewer, URL).andExpect(status().isOk());
        postAs(viewer, URL, size("300X600", 300, 600, 8)).andExpect(status().isForbidden());
    }
}