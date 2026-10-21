package com.ceramicerp.organization;

import com.ceramicerp.IntegrationTestBase;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class OrganizationIntegrationTest extends IntegrationTestBase {

    @Test
    void createCompanyNormalisesCodesAndRejectsDuplicates() throws Exception {
        String admin = adminToken();
        postAs(admin, "/api/companies", Map.of("code", "acme", "name", "Acme Ceramics",
                "gstin", "24abcde1234f1z5", "city", "Morbi", "state", "Gujarat", "pincode", "363641"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value("ACME"))
                .andExpect(jsonPath("$.gstin").value("24ABCDE1234F1Z5"));

        postAs(admin, "/api/companies", Map.of("code", "ACME", "name", "Duplicate"))
                .andExpect(status().isConflict());
    }

    @Test
    void invalidGstinIsReportedPerField() throws Exception {
        postAs(adminToken(), "/api/companies", Map.of("code", "BAD", "name", "Bad", "gstin", "12345"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.gstin").value("Invalid GSTIN"));
    }

    @Test
    void usersOnlySeeTheirAssignedPlants() throws Exception {
        String admin = adminToken();
        long companyId = createCompany(admin, "GRP");
        long plantA = createPlant(admin, companyId, "UNIT-1");
        long plantB = createPlant(admin, companyId, "UNIT-2");

        long roleId = createRole(admin, "Supervisor", "PLANT_VIEW");
        createUser(admin, "sup1", "password-1", roleId, plantA);
        String sup = login("sup1", "password-1");

        getAs(sup, "/api/plants")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].code").value("UNIT-1"));
        getAs(sup, "/api/plants/" + plantB).andExpect(status().isForbidden());

        // admin (all plants access) sees both
        getAs(admin, "/api/plants?companyId=" + companyId).andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    void updateCompanyChangesName() throws Exception {
        String admin = adminToken();
        long companyId = createCompany(admin, "VER");
        putAs(admin, "/api/companies/" + companyId, Map.of("code", "VER", "name", "Renamed"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Renamed"));
    }
}
