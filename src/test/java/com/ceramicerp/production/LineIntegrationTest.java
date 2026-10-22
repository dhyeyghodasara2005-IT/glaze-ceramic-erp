package com.ceramicerp.production;

import com.ceramicerp.IntegrationTestBase;
import com.ceramicerp.numbering.DocumentNumberService;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Step 4c: glaze & print and kiln entries, and a lot's funnel from press to kiln. */
class LineIntegrationTest extends IntegrationTestBase {

    private static final String ENTRIES = "/api/production/line-entries";
    private static final ZoneId INDIA = ZoneId.of("Asia/Kolkata");

    private final LocalDate today = LocalDate.now(INDIA);
    private final String fy = DocumentNumberService.financialYear(today);

    private String admin;
    private long plantId;
    private long glazeLine, printer, kiln, silo;
    private long shiftA;
    private long lotId;

    /** A lot with one posted press entry: 2,000 pressed, 50 rejected -> 1,950 good. */
    @BeforeEach
    void setUp() throws Exception {
        admin = adminToken();
        long companyId = createCompany(admin, "LINCO");
        plantId = createPlant(admin, companyId, "LIN-1");
        long press = createMachine("PRESS-1", "PRESS");
        glazeLine = createMachine("GL-1", "GLAZE_LINE");
        printer = createMachine("DP-1", "DIGITAL_PRINTER");
        kiln = createMachine("KILN-1", "KILN");
        silo = createMachine("SILO-1", "SILO");
        shiftA = idOf("shifts", "A");
        long design = read(postAs(admin, "/api/masters/designs", Map.of("code", "CARRARA-L", "name", "Carrara White",
                "categoryId", idOf("categories", "GVT"), "sizeId", idOf("sizes", "600X1200"),
                "finishId", idOf("finishes", "GLOSSY"), "faces", 4)).andExpect(status().isCreated())).get("id").asLong();

        lotId = read(postAs(admin, "/api/production/lots", Map.of("plantId", plantId, "designId", design, "pressId", press,
                "openedDate", today.toString(), "shiftId", shiftA, "closeCurrent", false))
                .andExpect(status().isCreated())).get("id").asLong();
        // The silo is empty on record, so the powder is "untraced" - fine for these tests.
        Map<String, Object> pressEntry = new HashMap<>();
        pressEntry.put("plantId", plantId);
        pressEntry.put("lotId", lotId);
        pressEntry.put("prodDate", today.toString());
        pressEntry.put("shiftId", shiftA);
        pressEntry.put("siloId", silo);
        pressEntry.put("pressedCount", 2000);
        pressEntry.put("pressRejects", 30);
        pressEntry.put("dryerRejects", 20);
        pressEntry.put("powderTon", 42);
        long id = read(postAs(admin, "/api/production/press-entries", pressEntry).andExpect(status().isCreated())).get("id").asLong();
        postAs(admin, "/api/production/press-entries/" + id + "/post", Map.of()).andExpect(status().isOk());
    }

    @Test
    void theLotFunnelFollowsTheTilesFromPressToKiln() throws Exception {
        JsonNode glaze = post(entry("GLAZE", glazeLine, 1900, 40));
        assertThat(glaze.get("entryNo").asText()).isEqualTo("LIN-1/GL/" + fy + "/0001");
        assertThat(glaze.get("tilesOut").asInt()).isEqualTo(1860);

        Map<String, Object> kilnBody = entry("KILN", kiln, 1800, 36);
        kilnBody.put("gasScm", 3000);
        kilnBody.put("maxTemp", 1205);
        JsonNode fired = post(kilnBody);
        assertThat(fired.get("entryNo").asText()).isEqualTo("LIN-1/KL/" + fy + "/0001");
        assertDecimal(fired.get("sqmOut"), "1270.08");      // 1,764 x 0.72
        assertDecimal(fired.get("gasPerSqm"), "2.362");     // 3,000 SCM / 1,270.08 sq m

        JsonNode flow = read(getAs(admin, "/api/production/lots/" + lotId)).get("flow");
        assertThat(flow.get("pressGood").asLong()).isEqualTo(1950);
        assertThat(flow.get("waitingGlaze").asLong()).isEqualTo(50);    // 1,950 good - 1,900 glazed
        assertThat(flow.get("glazeOut").asLong()).isEqualTo(1860);
        assertThat(flow.get("waitingKiln").asLong()).isEqualTo(60);     // 1,860 - 1,800 into the kiln
        assertThat(flow.get("kilnOut").asLong()).isEqualTo(1764);
        assertDecimal(flow.get("firedSqm"), "1270.08");
        assertDecimal(flow.get("yieldPct"), "88.2");                    // 1,764 of 2,000 pressed
    }

    @Test
    void eachStageUsesItsOwnMachineAndReadings() throws Exception {
        postAs(admin, ENTRIES, entry("GLAZE", kiln, 100, 0))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("KILN-1 is a kiln, not a glaze line"));
        postAs(admin, ENTRIES, entry("KILN", glazeLine, 100, 0))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("GL-1 is a glaze line, not a kiln"));
        postAs(admin, ENTRIES, entry("GLAZE", glazeLine, 100, 101))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Rejects can't be more than the tiles in"));

        // A kiln reading sent with a glaze entry is dropped; the printer is kept.
        Map<String, Object> glaze = entry("GLAZE", glazeLine, 100, 0);
        glaze.put("printerId", printer);
        glaze.put("glazeGpt", 380);
        glaze.put("maxTemp", 1200);
        JsonNode saved = read(postAs(admin, ENTRIES, glaze).andExpect(status().isCreated()));
        assertThat(saved.get("printerCode").asText()).isEqualTo("DP-1");
        assertDecimal(saved.get("glazeGpt"), "380");
        assertThat(saved.hasNonNull("maxTemp")).isFalse();

        // ...and a kiln entry never keeps a printer.
        Map<String, Object> kilnBody = entry("KILN", kiln, 100, 0);
        kilnBody.put("printerId", printer);
        assertThat(read(postAs(admin, ENTRIES, kilnBody).andExpect(status().isCreated())).hasNonNull("printerId")).isFalse();
    }

    @Test
    void cancellingAnEntryTakesItOutOfTheFunnel() throws Exception {
        long id = post(entry("GLAZE", glazeLine, 1900, 40)).get("id").asLong();
        postAs(admin, ENTRIES + "/" + id + "/cancel", Map.of("reason", "Wrong lot"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));
        JsonNode flow = read(getAs(admin, "/api/production/lots/" + lotId)).get("flow");
        assertThat(flow.get("glazeIn").asLong()).isZero();
        assertThat(flow.get("waitingGlaze").asLong()).isEqualTo(1950);
    }

    @Test
    void eachStageNeedsItsOwnSupervisor() throws Exception {
        long glazeRole = createRole(admin, "Glaze supervisor T", "PRODUCTION_VIEW", "GLAZE_ENTRY");
        long kilnRole = createRole(admin, "Kiln supervisor T", "PRODUCTION_VIEW", "KILN_ENTRY");
        long pressRole = createRole(admin, "Press supervisor T3", "PRODUCTION_VIEW", "PRESS_ENTRY");
        createUser(admin, "glaze.t", "password-1", glazeRole, plantId);
        createUser(admin, "kiln.t", "password-1", kilnRole, plantId);
        createUser(admin, "press.t3", "password-1", pressRole, plantId);
        String glazeSup = login("glaze.t", "password-1");
        String kilnSup = login("kiln.t", "password-1");
        String pressSup = login("press.t3", "password-1");

        postAs(glazeSup, ENTRIES, entry("GLAZE", glazeLine, 100, 0)).andExpect(status().isCreated());
        postAs(glazeSup, ENTRIES, entry("KILN", kiln, 100, 0)).andExpect(status().isForbidden());    // the service says no
        postAs(kilnSup, ENTRIES, entry("KILN", kiln, 100, 0)).andExpect(status().isCreated());
        postAs(kilnSup, ENTRIES, entry("GLAZE", glazeLine, 100, 0)).andExpect(status().isForbidden());
        postAs(pressSup, ENTRIES, entry("GLAZE", glazeLine, 100, 0)).andExpect(status().isForbidden()); // the controller says no
    }

    @Test
    void theLineHomeShowsRunningLotsAndTodaysKiln() throws Exception {
        post(entry("GLAZE", glazeLine, 1900, 40));
        Map<String, Object> kilnBody = entry("KILN", kiln, 1800, 36);
        kilnBody.put("gasScm", 3000);
        post(kilnBody);

        JsonNode overview = read(getAs(admin, "/api/production/line/overview?plantId=" + plantId));
        assertThat(overview.get("lots").size()).isEqualTo(1);
        assertThat(overview.at("/lots/0/lot/designName").asText()).isEqualTo("Carrara White");
        assertThat(overview.at("/lots/0/flow/waitingKiln").asLong()).isEqualTo(60);
        if (overview.get("prodDate").asText().equals(today.toString())) {
            assertDecimal(overview.at("/today/firedSqm"), "1270.08");
            assertDecimal(overview.at("/today/gasPerSqm"), "2.362");
        }
    }

    @Test
    void lineEntriesAreFoundByNumber() throws Exception {
        post(entry("KILN", kiln, 500, 5));
        getAs(admin, "/api/search?q=LIN-1/KL")
                .andExpect(jsonPath("$[*].type", hasItem("kiln-entry")))
                .andExpect(jsonPath("$[*].code", hasItem("LIN-1/KL/" + fy + "/0001")));
    }

    // ------------------------------------------------------------------ helpers

    private Map<String, Object> entry(String stage, long machineId, int tilesIn, int rejects) {
        Map<String, Object> body = new HashMap<>();
        body.put("plantId", plantId);
        body.put("stage", stage);
        body.put("lotId", lotId);
        body.put("prodDate", today.toString());
        body.put("shiftId", shiftA);
        body.put("machineId", machineId);
        body.put("tilesIn", tilesIn);
        body.put("rejects", rejects);
        return body;
    }

    private JsonNode post(Map<String, Object> body) throws Exception {
        long id = read(postAs(admin, ENTRIES, body).andExpect(status().isCreated())).get("id").asLong();
        return read(postAs(admin, ENTRIES + "/" + id + "/post", Map.of()).andExpect(status().isOk()));
    }

    private static void assertDecimal(JsonNode node, String expected) {
        assertThat(node.decimalValue()).isEqualByComparingTo(expected);
    }

    private long createMachine(String code, String type) throws Exception {
        return read(postAs(admin, "/api/masters/machines", Map.of("plantId", plantId, "code", code,
                "name", "Machine " + code, "type", type)).andExpect(status().isCreated())).get("id").asLong();
    }

    private long idOf(String master, String code) throws Exception {
        for (JsonNode n : read(getAs(admin, "/api/masters/" + master))) {
            if (n.get("code").asText().equals(code)) return n.get("id").asLong();
        }
        throw new AssertionError(code + " not found in " + master);
    }
}
