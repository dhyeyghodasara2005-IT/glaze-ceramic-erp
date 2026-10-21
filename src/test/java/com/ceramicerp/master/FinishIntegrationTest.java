package com.ceramicerp.master;

import com.ceramicerp.IntegrationTestBase;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.hamcrest.Matchers.contains;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class FinishIntegrationTest extends IntegrationTestBase {

    private static final String URL = "/api/masters/finishes";

    @Test
    void startingFinishesAreListedAlphabetically() throws Exception {
        getAs(adminToken(), URL)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].code", contains("CARVING", "GLOSSY", "MATT", "SATIN")));
    }

    @Test
    void createUppercasesCodeAndRejectsDuplicates() throws Exception {
        String admin = adminToken();
        postAs(admin, URL, Map.of("code", "high-gloss", "name", "High Gloss"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value("HIGH-GLOSS"))
                .andExpect(jsonPath("$.active").value(true));

        postAs(admin, URL, Map.of("code", "glossy", "name", "Another glossy"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Finish code already exists: GLOSSY"));
    }

    @Test
    void finishCanBeDeactivated() throws Exception {
        String admin = adminToken();
        long id = read(postAs(admin, URL, Map.of("code", "RUSTIC", "name", "Rustic"))).get("id").asLong();

        putAs(admin, URL + "/" + id, Map.of("code", "RUSTIC", "name", "Rustic", "active", false))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false));
    }

    @Test
    void blankNameIsRejected() throws Exception {
        postAs(adminToken(), URL, Map.of("code", "X1", "name", " "))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.name").exists());
    }
}
