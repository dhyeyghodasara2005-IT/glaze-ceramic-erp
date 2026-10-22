package com.ceramicerp.production;

import com.ceramicerp.IntegrationTestBase;
import com.ceramicerp.numbering.DocumentNumberService;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasItems;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ProductionIntegrationTest extends IntegrationTestBase {

    private static final String BATCHES = "/api/production/body-batches";
    private static final String RUNS = "/api/production/spray-runs";
    private static final ZoneId INDIA = ZoneId.of("Asia/Kolkata");

    private final LocalDate today = LocalDate.now(INDIA);
    private final String fy = DocumentNumberService.financialYear(today);

    private String admin;
    private long companyId;
    private long plantId;
    private long yard;                       // godown "Clay yard"
    private long clay, feldspar, defloc;     // TON, TON, KG
    private long mill1, mill2, tank, dryer, silo, press;
    private long shiftA;

    @BeforeEach
    void setUp() throws Exception {
        admin = adminToken();
        companyId = createCompany(admin, "PRDCO");
        plantId = createPlant(admin, companyId, "PRD-1");
        yard = read(postAs(admin, "/api/store/locations", Map.of("plantId", plantId, "code", "YARD", "name", "Clay yard"))
                .andExpect(status().isCreated())).get("id").asLong();
        clay = createMaterial("CLAY-P", "TON", "BODY");
        feldspar = createMaterial("FELD-P", "TON", "BODY");
        defloc = createMaterial("DEFLOC-P", "KG", "CHEMICAL");
        mill1 = createMachine("BM-1", "BALL_MILL");
        mill2 = createMachine("BM-2", "BALL_MILL");
        tank = createMachine("TANK-1", "SLIP_TANK");
        dryer = createMachine("SD-1", "SPRAY_DRYER");
        silo = createMachine("SILO-1", "SILO");
        press = createMachine("PRESS-1", "PRESS");
        shiftA = idOf("shifts", "A");

        // Opening stock in the clay yard, so mills can be charged.
        Map<String, Object> opening = new HashMap<>();
        opening.put("plantId", plantId);
        opening.put("docType", "ADJUSTMENT");
        opening.put("docDate", today.toString());
        opening.put("reason", "OPENING");
        opening.put("lines", List.of(
                Map.of("materialId", clay, "locationId", yard, "quantity", 100, "rate", 1000),
                Map.of("materialId", feldspar, "locationId", yard, "quantity", 100, "rate", 2000),
                Map.of("materialId", defloc, "locationId", yard, "quantity", 500, "rate", 50)));
        long adj = read(postAs(admin, "/api/store/documents", opening).andExpect(status().isCreated())).get("id").asLong();
        postAs(admin, "/api/store/documents/" + adj + "/post", Map.of()).andExpect(status().isOk());
    }

    // ------------------------------------------------------------------ masters

    @Test
    void twoTwelveHourShiftsAreReadyToUse() throws Exception {
        getAs(admin, "/api/masters/shifts")
                .andExpect(jsonPath("$[*].code", hasItems("A", "B")))
                .andExpect(jsonPath("$[1].crossesMidnight").value(true));
        getAs(admin, "/api/production/lookups/current-shift").andExpect(jsonPath("$.prodDate").exists());
    }

    @Test
    void aRecipeListsEachMaterialOnce() throws Exception {
        Map<String, Object> recipe = new HashMap<>();
        recipe.put("code", "GVT-STD");
        recipe.put("name", "GVT standard body");
        recipe.put("lines", List.of(Map.of("materialId", clay, "qtyPerTon", 0.3),
                Map.of("materialId", clay, "qtyPerTon", 0.2)));
        postAs(admin, "/api/masters/body-recipes", recipe)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("CLAY-P is in the recipe twice"));

        recipe.put("lines", List.of(Map.of("materialId", clay, "qtyPerTon", 0.4),
                Map.of("materialId", feldspar, "qtyPerTon", 0.6),
                Map.of("materialId", defloc, "qtyPerTon", 3)));
        postAs(admin, "/api/masters/body-recipes", recipe)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.lines.length()").value(3))
                .andExpect(jsonPath("$.lines[2].unitCode").value("KG"));
    }

    // ------------------------------------------------------------------ charging a mill

    @Test
    void chargingAMillIssuesTheRawMaterialFromTheStore() throws Exception {
        long id = create(batch(mill1, line(clay, "12"), line(feldspar, "28"), line(defloc, "120")));
        JsonNode draft = read(getAs(admin, BATCHES + "/" + id));
        assertThat(draft.get("status").asText()).isEqualTo("DRAFT");
        assertThat(draft.hasNonNull("batchNo")).isFalse();
        assertDecimal(stockOf("CLAY-P"), "100");                 // a draft touches nothing

        JsonNode charged = charge(id);
        assertThat(charged.get("status").asText()).isEqualTo("CHARGED");
        assertThat(charged.get("batchNo").asText()).isEqualTo("PRD-1/BB/" + fy + "/0001");
        assertThat(charged.get("issueDocNo").asText()).isEqualTo("PRD-1/ISS/" + fy + "/0001");
        assertDecimal(stockOf("CLAY-P"), "88");
        assertDecimal(stockOf("DEFLOC-P"), "380");

        // The issue belongs to production: the store can see it, but not change it.
        long issueId = charged.get("issueDocumentId").asLong();
        getAs(admin, "/api/store/documents/" + issueId)
                .andExpect(jsonPath("$.origin").value("BODY_BATCH"))
                .andExpect(jsonPath("$.machineCode").value("BM-1"))
                .andExpect(jsonPath("$.issuedTo").value("Body batch PRD-1/BB/" + fy + "/0001"));
        postAs(admin, "/api/store/documents/" + issueId + "/cancel", Map.of("reason", "Oops"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("PRD-1/ISS/" + fy + "/0001 was made automatically "
                        + "(Body batch PRD-1/BB/" + fy + "/0001). Change or cancel it from Production."));
    }

    @Test
    void aBallMillHoldsOneChargeAtATime() throws Exception {
        long first = create(batch(mill1, line(clay, "10")));
        charge(first);
        long second = create(batch(mill1, line(clay, "10")));
        postAs(admin, BATCHES + "/" + second + "/charge", Map.of())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Ball mill BM-1 still holds PRD-1/BB/" + fy + "/0001. Discharge it first."));

        discharge(first, "1700");
        assertThat(charge(second).get("status").asText()).isEqualTo("CHARGED");
    }

    @Test
    void onlyABallMillCanBeCharged() throws Exception {
        postAs(admin, BATCHES, batch(press, line(clay, "10")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("PRESS-1 is a press, not a ball mill"));
    }

    @Test
    void theChargeStopsWhenTheStoreHasTooLittle() throws Exception {
        long id = create(batch(mill1, line(clay, "150")));
        postAs(admin, BATCHES + "/" + id + "/charge", Map.of())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Not enough CLAY-P in Clay yard: available 100 TON, needed 150 TON"));
    }

    // ------------------------------------------------------------------ discharge

    @Test
    void dischargeRecordsTheSlipTestAndTank() throws Exception {
        long id = create(batch(mill1, line(clay, "10")));
        charge(id);

        Map<String, Object> body = dischargeBody(tank, "1700");
        body.put("slipTankId", press);
        postAs(admin, BATCHES + "/" + id + "/discharge", body)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("PRESS-1 is a press, not a slip tank"));

        body = dischargeBody(tank, "1700");
        body.put("millingEnd", millingStart().minusHours(1).toString());
        postAs(admin, BATCHES + "/" + id + "/discharge", body)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("The milling end can't be before the start"));

        JsonNode done = discharge(id, "1700");
        assertThat(done.get("status").asText()).isEqualTo("DISCHARGED");
        assertThat(done.get("slipTankCode").asText()).isEqualTo("TANK-1");
        assertDecimal(done.get("slipDensity"), "1700");

        // Lab corrects the reading later: allowed until the spray dryer uses the batch up.
        assertDecimal(discharge(id, "1712").get("slipDensity"), "1712");
    }

    // ------------------------------------------------------------------ spray dryer

    @Test
    void aSprayRunUsesUpTheFinishedBatches() throws Exception {
        long b1 = chargedAndDischarged(mill1);
        long b2 = chargedAndDischarged(mill2);
        long b3 = create(batch(mill1, line(clay, "5")));
        charge(b3);                                              // still in the mill

        postAs(admin, RUNS, run(Map.of(b3, true)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("PRD-1/BB/" + fy + "/0003 is not waiting in a slip tank (it is charged)"));

        getAs(admin, RUNS + "/available-batches?plantId=" + plantId).andExpect(jsonPath("$.length()").value(2));

        long runId = read(postAs(admin, RUNS, run(Map.of(b1, true, b2, false))).andExpect(status().isCreated())).get("id").asLong();
        JsonNode posted = read(postAs(admin, RUNS + "/" + runId + "/post", Map.of()).andExpect(status().isOk()));
        assertThat(posted.get("runNo").asText()).isEqualTo("PRD-1/SD/" + fy + "/0001");
        assertThat(posted.get("siloCode").asText()).isEqualTo("SILO-1");

        getAs(admin, BATCHES + "/" + b1)
                .andExpect(jsonPath("$.status").value("CONSUMED"))
                .andExpect(jsonPath("$.sprayRuns[0].runNo").value("PRD-1/SD/" + fy + "/0001"));
        getAs(admin, BATCHES + "/" + b2).andExpect(jsonPath("$.status").value("DISCHARGED"));

        JsonNode overview = read(getAs(admin, "/api/production/body/overview?plantId=" + plantId));
        assertThat(overview.get("inMills").size()).isEqualTo(1);       // b3
        assertThat(overview.get("waiting").size()).isEqualTo(1);       // b2
        if (overview.get("prodDate").asText().equals(today.toString())) {
            assertThat(overview.at("/charged/count").asInt()).isEqualTo(3);
            assertDecimal(overview.at("/sprayed/tonnes"), "45.5");
        }
    }

    @Test
    void cancellingARunGivesItsBatchesBack() throws Exception {
        long b1 = chargedAndDischarged(mill1);
        long runId = read(postAs(admin, RUNS, run(Map.of(b1, true))).andExpect(status().isCreated())).get("id").asLong();
        postAs(admin, RUNS + "/" + runId + "/post", Map.of()).andExpect(status().isOk());

        postAs(admin, RUNS + "/" + runId + "/cancel", Map.of("reason", "Wrong silo"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));
        getAs(admin, BATCHES + "/" + b1).andExpect(jsonPath("$.status").value("DISCHARGED"));
    }

    @Test
    void cancellingABatchPutsTheRawMaterialBack() throws Exception {
        long id = create(batch(mill1, line(clay, "30")));
        long issueId = charge(id).get("issueDocumentId").asLong();
        assertDecimal(stockOf("CLAY-P"), "70");

        postAs(admin, BATCHES + "/" + id + "/cancel", Map.of("reason", "Wrong recipe loaded"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));
        assertDecimal(stockOf("CLAY-P"), "100");
        getAs(admin, "/api/store/documents/" + issueId).andExpect(jsonPath("$.status").value("CANCELLED"));
    }

    @Test
    void aBatchInAPostedRunCantBeCancelled() throws Exception {
        long b1 = chargedAndDischarged(mill1);
        long runId = read(postAs(admin, RUNS, run(Map.of(b1, false))).andExpect(status().isCreated())).get("id").asLong();
        postAs(admin, RUNS + "/" + runId + "/post", Map.of()).andExpect(status().isOk());

        postAs(admin, BATCHES + "/" + b1 + "/cancel", Map.of("reason", "Test"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("PRD-1/BB/" + fy + "/0001 is used in spray dryer run PRD-1/SD/"
                        + fy + "/0001. Cancel that run first."));
    }

    // ------------------------------------------------------------------ who may do what

    @Test
    void eachDivisionSupervisorWorksOnlyWithTheirOwnPermission() throws Exception {
        long otherPlant = createPlant(admin, companyId, "PRD-2");
        long viewerRole = createRole(admin, "Production viewer T", "PRODUCTION_VIEW");
        long bodyRole = createRole(admin, "Body supervisor T", "PRODUCTION_VIEW", "BODY_PREP_ENTRY");
        createUser(admin, "viewer.t", "password-1", viewerRole, plantId);
        createUser(admin, "body.t", "password-1", bodyRole, plantId);
        String viewer = login("viewer.t", "password-1");
        String supervisor = login("body.t", "password-1");

        postAs(viewer, BATCHES, batch(mill1, line(clay, "5"))).andExpect(status().isForbidden());

        // The supervisor has no store or master rights, yet can charge: the store issue is made by the system.
        long id = read(postAs(supervisor, BATCHES, batch(mill1, line(clay, "5"))).andExpect(status().isCreated())).get("id").asLong();
        postAs(supervisor, BATCHES + "/" + id + "/charge", Map.of()).andExpect(status().isOk());
        getAs(supervisor, "/api/store/stock?plantId=" + plantId).andExpect(status().isForbidden());
        getAs(supervisor, "/api/masters/shifts").andExpect(status().isForbidden());

        // ...and gets every dropdown it needs from the production lookups.
        getAs(supervisor, "/api/production/lookups/materials").andExpect(jsonPath("$[*].code", hasItem("CLAY-P")));
        getAs(supervisor, "/api/production/lookups/godowns?plantId=" + plantId).andExpect(jsonPath("$[0].code").value("YARD"));
        getAs(supervisor, "/api/production/lookups/stock?plantId=" + plantId).andExpect(jsonPath("$.length()").value(3));
        getAs(supervisor, "/api/production/lookups/machines?plantId=" + plantId).andExpect(jsonPath("$[*].code", hasItem("BM-1")));
        getAs(supervisor, "/api/production/lookups/shifts").andExpect(jsonPath("$[*].code", hasItems("A", "B")));

        getAs(supervisor, BATCHES + "?plantId=" + otherPlant).andExpect(status().isForbidden());
        postAs(supervisor, BATCHES + "/" + id + "/cancel", Map.of("reason", "Oops")).andExpect(status().isForbidden());
    }

    @Test
    void batchNumbersAreFoundBySearch() throws Exception {
        charge(create(batch(mill1, line(clay, "5"))));
        getAs(admin, "/api/search?q=PRD-1/BB")
                .andExpect(jsonPath("$[*].type", hasItem("body-batch")))
                .andExpect(jsonPath("$[*].code", hasItem("PRD-1/BB/" + fy + "/0001")));
    }

    // ------------------------------------------------------------------ helpers

    private LocalDateTime millingStart() {
        return LocalDateTime.now(INDIA).minusHours(10).withNano(0);
    }

    @SafeVarargs
    private Map<String, Object> batch(long millId, Map<String, Object>... lines) {
        Map<String, Object> body = new HashMap<>();
        body.put("plantId", plantId);
        body.put("prodDate", today.toString());
        body.put("shiftId", shiftA);
        body.put("ballMillId", millId);
        body.put("chargeTon", 40);
        body.put("waterLitres", 14000);
        body.put("millingStart", millingStart().toString());
        body.put("lines", List.of(lines));
        return body;
    }

    private Map<String, Object> line(long materialId, String qty) {
        return Map.of("materialId", materialId, "locationId", yard, "quantity", new BigDecimal(qty));
    }

    private Map<String, Object> dischargeBody(long tankId, String density) {
        Map<String, Object> body = new HashMap<>();
        body.put("millingEnd", LocalDateTime.now(INDIA).minusHours(1).withNano(0).toString());
        body.put("slipTankId", tankId);
        body.put("slipDensity", new BigDecimal(density));
        body.put("slipViscosity", 42);
        body.put("slipResidue", 2.5);
        return body;
    }

    /** Spray run using the given batches (batch id -> finished). */
    private Map<String, Object> run(Map<Long, Boolean> batches) {
        Map<String, Object> body = new HashMap<>();
        body.put("plantId", plantId);
        body.put("prodDate", today.toString());
        body.put("shiftId", shiftA);
        body.put("sprayDryerId", dryer);
        body.put("siloId", silo);
        body.put("runStart", LocalDateTime.now(INDIA).minusMinutes(30).withNano(0).toString());
        body.put("powderTon", 45.5);
        body.put("powderMoisture", 6.2);
        body.put("batches", batches.entrySet().stream()
                .map(e -> Map.of("bodyBatchId", e.getKey(), "finished", e.getValue())).toList());
        return body;
    }

    private long create(Map<String, Object> body) throws Exception {
        return read(postAs(admin, BATCHES, body).andExpect(status().isCreated())).get("id").asLong();
    }

    private JsonNode charge(long id) throws Exception {
        return read(postAs(admin, BATCHES + "/" + id + "/charge", Map.of()).andExpect(status().isOk()));
    }

    private JsonNode discharge(long id, String density) throws Exception {
        return read(postAs(admin, BATCHES + "/" + id + "/discharge", dischargeBody(tank, density)).andExpect(status().isOk()));
    }

    private long chargedAndDischarged(long millId) throws Exception {
        long id = create(batch(millId, line(clay, "10")));
        charge(id);
        discharge(id, "1700");
        return id;
    }

    private JsonNode stockOf(String materialCode) throws Exception {
        for (JsonNode row : read(getAs(admin, "/api/store/stock?plantId=" + plantId).andExpect(status().isOk()))) {
            if (row.get("materialCode").asText().equals(materialCode)) return row.get("quantity");
        }
        throw new AssertionError(materialCode + " not in stock list");
    }

    private static void assertDecimal(JsonNode node, String expected) {
        assertThat(node.decimalValue()).isEqualByComparingTo(expected);
    }

    private long createMaterial(String code, String unit, String group) throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("code", code);
        body.put("name", "Material " + code);
        body.put("groupId", idOf("material-groups", group));
        body.put("unitId", idOf("units", unit));
        return read(postAs(admin, "/api/masters/materials", body).andExpect(status().isCreated())).get("id").asLong();
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
