package com.ceramicerp.master;

import com.ceramicerp.IntegrationTestBase;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.hamcrest.Matchers.contains;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class CategoryAndGradeIntegrationTest extends IntegrationTestBase {

    // ---- categories ----

    @Test
    void startingCategoriesCarryHsnAndGst() throws Exception {
        getAs(adminToken(), "/api/masters/categories")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.code == 'GVT')].hsnCode").value("6907"))
                .andExpect(jsonPath("$[?(@.code == 'GVT')].gstRate").value(18.0));
    }

    @Test
    void categoryValidatesGstAndHsn() throws Exception {
        postAs(adminToken(), "/api/masters/categories",
                Map.of("code", "FB", "name", "Full Body", "hsnCode", "69A7", "gstRate", 150))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.hsnCode").value("HSN code is 4 to 8 digits"))
                .andExpect(jsonPath("$.fieldErrors.gstRate").exists());
    }

    @Test
    void categoryCanBeCreated() throws Exception {
        postAs(adminToken(), "/api/masters/categories",
                Map.of("code", "fullbody", "name", "Full Body Vitrified", "hsnCode", "6907", "gstRate", 18))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value("FULLBODY"))
                .andExpect(jsonPath("$.gstRate").value(18));
    }

    // ---- grades ----

    @Test
    void gradesAreListedBestFirstAndRejectIsNotSellable() throws Exception {
        getAs(adminToken(), "/api/masters/grades")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].code", contains("PREMIUM", "STANDARD", "COMMERCIAL", "REJECT")))
                .andExpect(jsonPath("$[3].sellable").value(false));
    }

    @Test
    void newGradeIsPlacedBySortOrder() throws Exception {
        String admin = adminToken();
        postAs(admin, "/api/masters/grades",
                Map.of("code", "EXPORT", "name", "Export", "sortOrder", 0, "sellable", true))
                .andExpect(status().isCreated());

        getAs(admin, "/api/masters/grades").andExpect(jsonPath("$[0].code").value("EXPORT"));
    }

    @Test
    void gradeRequiresSellableFlag() throws Exception {
        postAs(adminToken(), "/api/masters/grades", Map.of("code", "BROKEN", "name", "Broken", "sortOrder", 10))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.sellable").exists());
    }
}
