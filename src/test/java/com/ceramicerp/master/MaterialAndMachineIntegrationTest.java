package com.ceramicerp.master;

import com.ceramicerp.IntegrationTestBase;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasItems;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class MaterialAndMachineIntegrationTest extends IntegrationTestBase {

    private long idOf(String token, String master, String code) throws Exception {
        for (JsonNode n : read(getAs(token, "/api/masters/" + master))) {
            if (n.get("code").asText().equals(code)) return n.get("id").asLong();
        }
        throw new AssertionError(code + " not found in " + master);
    }

    private Map<String, Object> material(String token, String code, String unit) throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("code", code);
        body.put("name", "Material " + code);
        body.put("groupId", idOf(token, "material-groups", "BODY"));
        body.put("unitId", idOf(token, "units", unit));
        body.put("hsnCode", "2508");
        body.put("gstRate", 5);
        body.put("reorderLevel", 10.5);
        return body;
    }

    private Map<String, Object> machine(long plantId, String code, String type) {
        Map<String, Object> body = new HashMap<>();
        body.put("plantId", plantId);
        body.put("code", code);
        body.put("name", "Machine " + code);
        body.put("type", type);
        body.put("capacity", 12000);
        body.put("capacityUnit", "sq m/day");
        return body;
    }

    // ---- units & groups (starting data) ----

    @Test
    void startingUnitsAndGroupsExist() throws Exception {
        String admin = adminToken();
        getAs(admin, "/api/masters/units").andExpect(jsonPath("$[*].code", hasItems("KG", "TON", "SCM", "BOX")));
        getAs(admin, "/api/masters/material-groups").andExpect(jsonPath("$[*].code", hasItems("BODY", "GLAZE", "INK")));
    }

    // ---- materials ----

    @Test
    void materialCreatedWithGroupAndUnit() throws Exception {
        String admin = adminToken();
        postAs(admin, "/api/masters/materials", material(admin, "clay-red", "TON"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value("CLAY-RED"))
                .andExpect(jsonPath("$.groupCode").value("BODY"))
                .andExpect(jsonPath("$.unitCode").value("TON"))
                .andExpect(jsonPath("$.reorderLevel").value(10.5));

        getAs(admin, "/api/masters/materials?search=clay").andExpect(jsonPath("$.totalElements").value(1));
        getAs(admin, "/api/search?q=clay-red").andExpect(jsonPath("$[*].type", hasItem("material")));
    }

    @Test
    void reorderLevelMustFitTheUnitDecimals() throws Exception {
        String admin = adminToken();
        postAs(admin, "/api/masters/materials", material(admin, "BOX-600", "BOX"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Reorder level in BOX can have at most 0 decimal places"));
    }

    @Test
    void inactiveUnitCannotBeChosen() throws Exception {
        String admin = adminToken();
        long rollId = idOf(admin, "units", "ROLL");
        putAs(admin, "/api/masters/units/" + rollId, Map.of("code", "ROLL", "name", "Roll", "decimals", 0, "active", false))
                .andExpect(status().isOk());
        Map<String, Object> body = material(admin, "TAPE", "ROLL");
        body.remove("reorderLevel");
        postAs(admin, "/api/masters/materials", body)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Unit ROLL is inactive. Choose an active one."));
    }

    // ---- machines ----

    @Test
    void machineCodesAreUniquePerPlant() throws Exception {
        String admin = adminToken();
        long company = createCompany(admin, "GRP");
        long plant1 = createPlant(admin, company, "U1");
        long plant2 = createPlant(admin, company, "U2");

        postAs(admin, "/api/masters/machines", machine(plant1, "kiln-1", "KILN"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value("KILN-1"))
                .andExpect(jsonPath("$.type").value("KILN"))
                .andExpect(jsonPath("$.plantCode").value("U1"));
        // same code in another plant is fine
        postAs(admin, "/api/masters/machines", machine(plant2, "KILN-1", "KILN")).andExpect(status().isCreated());
        // same code in the same plant is not
        postAs(admin, "/api/masters/machines", machine(plant1, "KILN-1", "KILN"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Machine code KILN-1 already exists in plant U1"));
    }

    @Test
    void unknownMachineTypeIsRejected() throws Exception {
        String admin = adminToken();
        long plant = createPlant(admin, createCompany(admin, "GRP2"), "U3");
        postAs(admin, "/api/masters/machines", machine(plant, "X-1", "TOASTER")).andExpect(status().isBadRequest());
    }

    @Test
    void machinesComeInProcessOrderNotAToZ() throws Exception {
        String admin = adminToken();
        long plant = createPlant(admin, createCompany(admin, "GRP4"), "U4");
        for (String[] m : new String[][] {{"KILN-1", "KILN"}, {"PRN-1", "DIGITAL_PRINTER"}, {"PRESS-2", "PRESS"}, {"PRESS-1", "PRESS"}}) {
            postAs(admin, "/api/masters/machines", machine(plant, m[0], m[1])).andExpect(status().isCreated());
        }
        // A-Z on the type's name would put DIGITAL_PRINTER first; the line runs press, printer, kiln
        getAs(admin, "/api/masters/machines?plantId=" + plant)
                .andExpect(jsonPath("$[*].code").value(contains("PRESS-1", "PRESS-2", "PRN-1", "KILN-1")));
    }

    @Test
    void machinesFollowPlantAccess() throws Exception {
        String admin = adminToken();
        long company = createCompany(admin, "GRP3");
        long mine = createPlant(admin, company, "MINE");
        long other = createPlant(admin, company, "OTHER");
        postAs(admin, "/api/masters/machines", machine(mine, "PRESS-1", "PRESS")).andExpect(status().isCreated());
        long otherMachine = read(postAs(admin, "/api/masters/machines", machine(other, "PRESS-9", "PRESS"))).get("id").asLong();

        long roleId = createRole(admin, "Machine manager", "MASTER_VIEW", "MASTER_MANAGE");
        createUser(admin, "mm1", "password-1", roleId, mine);
        String mm = login("mm1", "password-1");

        getAs(mm, "/api/masters/machines")
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].code").value("PRESS-1"));
        getAs(mm, "/api/masters/machines/" + otherMachine).andExpect(status().isForbidden());
        postAs(mm, "/api/masters/machines", machine(other, "PRESS-2", "PRESS")).andExpect(status().isForbidden());
    }
}
