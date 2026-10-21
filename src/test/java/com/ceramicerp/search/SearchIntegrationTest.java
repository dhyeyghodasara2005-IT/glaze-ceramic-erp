package com.ceramicerp.search;

import com.ceramicerp.IntegrationTestBase;
import org.junit.jupiter.api.Test;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class SearchIntegrationTest extends IntegrationTestBase {

    @Test
    void findsMastersByCodeWithBestMatchFirst() throws Exception {
        getAs(adminToken(), "/api/search?q=600")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].type").value("size"))
                .andExpect(jsonPath("$[0].code").value("600X1200"))
                .andExpect(jsonPath("$[0].subtitle").value("1.44 sq m / box"));
    }

    @Test
    void findsAcrossModules() throws Exception {
        String admin = adminToken();
        createCompany(admin, "SUNRISE");
        getAs(admin, "/api/search?q=sunrise").andExpect(jsonPath("$[*].type", hasItem("company")));
        getAs(admin, "/api/search?q=glossy").andExpect(jsonPath("$[*].type", hasItem("finish")));
        getAs(admin, "/api/search?q=6907").andExpect(jsonPath("$[*].type", hasItem("category")));
        getAs(admin, "/api/search?q=super").andExpect(jsonPath("$[*].type", hasItem("role")));
    }

    @Test
    void onlyReturnsWhatTheUserMaySee() throws Exception {
        String admin = adminToken();
        long companyId = createCompany(admin, "GRP");
        long mine = createPlant(admin, companyId, "UNIT-A");
        createPlant(admin, companyId, "UNIT-B");
        long roleId = createRole(admin, "Plant Viewer", "PLANT_VIEW");
        createUser(admin, "viewer.s", "password-1", roleId, mine);
        String viewer = login("viewer.s", "password-1");

        // no MASTER_VIEW -> no masters; only the plant they are assigned to
        getAs(viewer, "/api/search?q=600").andExpect(jsonPath("$.length()").value(0));
        getAs(viewer, "/api/search?q=unit")
                .andExpect(jsonPath("$[*].code", hasItem("UNIT-A")))
                .andExpect(jsonPath("$[*].code", not(hasItem("UNIT-B"))));
    }

    @Test
    void wildcardCharactersAreTakenLiterally() throws Exception {
        // "%" must not match everything
        getAs(adminToken(), "/api/search?q=%25").andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void blankQueryReturnsNothing() throws Exception {
        getAs(adminToken(), "/api/search?q=%20").andExpect(jsonPath("$.length()").value(0));
    }
}
