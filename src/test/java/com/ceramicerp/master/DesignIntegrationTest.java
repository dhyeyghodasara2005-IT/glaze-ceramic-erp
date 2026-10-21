package com.ceramicerp.master;

import com.ceramicerp.IntegrationTestBase;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class DesignIntegrationTest extends IntegrationTestBase {

    private static final String URL = "/api/masters/designs";

    /** Finds the id of a starting-data master by its code, e.g. idOf(token, "sizes", "600X1200"). */
    private long idOf(String token, String master, String code) throws Exception {
        for (JsonNode n : read(getAs(token, "/api/masters/" + master))) {
            if (n.get("code").asText().equals(code)) {
                return n.get("id").asLong();
            }
        }
        throw new AssertionError(code + " not found in " + master);
    }

    /** A valid design body using GVT / 600X1200 / GLOSSY from the V2 starting data. */
    private Map<String, Object> design(String token, String code) throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("code", code);
        body.put("name", "Design " + code);
        body.put("categoryId", idOf(token, "categories", "GVT"));
        body.put("sizeId", idOf(token, "sizes", "600X1200"));
        body.put("finishId", idOf(token, "finishes", "GLOSSY"));
        body.put("faces", 4);
        body.put("launchDate", "2026-10-01");
        return body;
    }

    @Test
    void createReturnsLinkedNamesAndSqm() throws Exception {
        String admin = adminToken();
        postAs(admin, URL, design(admin, "marble-01"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value("MARBLE-01"))
                .andExpect(jsonPath("$.categoryCode").value("GVT"))
                .andExpect(jsonPath("$.sizeCode").value("600X1200"))
                .andExpect(jsonPath("$.sqmPerBox").value(1.44))
                .andExpect(jsonPath("$.finishName").value("Glossy"))
                .andExpect(jsonPath("$.faces").value(4))
                .andExpect(jsonPath("$.launchDate").value("2026-10-01"));
    }

    @Test
    void duplicateCodeIsRejected() throws Exception {
        String admin = adminToken();
        postAs(admin, URL, design(admin, "STONE-1")).andExpect(status().isCreated());
        postAs(admin, URL, design(admin, "stone-1"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Design code already exists: STONE-1"));
    }

    @Test
    void searchAndFiltersWork() throws Exception {
        String admin = adminToken();
        postAs(admin, URL, design(admin, "WOOD-OAK")).andExpect(status().isCreated());
        Map<String, Object> matt = design(admin, "WOOD-ASH");
        matt.put("finishId", idOf(admin, "finishes", "MATT"));
        postAs(admin, URL, matt).andExpect(status().isCreated());

        getAs(admin, URL + "?search=wood").andExpect(jsonPath("$.totalElements").value(2));
        getAs(admin, URL + "?search=wood&finishId=" + idOf(admin, "finishes", "MATT"))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].code").value("WOOD-ASH"));
    }

    @Test
    void inactiveFinishCannotBeChosen() throws Exception {
        String admin = adminToken();
        long satin = idOf(admin, "finishes", "SATIN");
        putAs(admin, "/api/masters/finishes/" + satin, Map.of("code", "SATIN", "name", "Satin", "active", false))
                .andExpect(status().isOk());

        Map<String, Object> body = design(admin, "NEW-SATIN");
        body.put("finishId", satin);
        postAs(admin, URL, body)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Finish SATIN is inactive. Choose an active one."));
    }

    @Test
    void existingDesignStillEditableAfterItsSizeIsRetired() throws Exception {
        String admin = adminToken();
        Map<String, Object> body = design(admin, "OLD-1");
        long id = read(postAs(admin, URL, body).andExpect(status().isCreated())).get("id").asLong();

        long sizeId = (Long) body.get("sizeId");
        putAs(admin, "/api/masters/sizes/" + sizeId, Map.of("code", "600X1200", "name", "600 x 1200 mm",
                "lengthMm", 600, "widthMm", 1200, "thicknessMm", 9.0, "pcsPerBox", 2, "active", false))
                .andExpect(status().isOk());

        body.put("name", "Renamed old design");
        putAs(admin, URL + "/" + id, body)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Renamed old design"));
    }

    @Test
    void missingSizeAndBadFacesAreReported() throws Exception {
        String admin = adminToken();
        Map<String, Object> body = design(admin, "BAD-1");
        body.put("faces", 0);
        body.remove("sizeId");
        postAs(admin, URL, body)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.sizeId").value("Select a size"))
                .andExpect(jsonPath("$.fieldErrors.faces").exists());
    }
}
