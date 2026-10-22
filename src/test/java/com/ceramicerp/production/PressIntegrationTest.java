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
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Step 4b: design lots, press & dryer entries, and the silo's oldest-first powder. */
class PressIntegrationTest extends IntegrationTestBase {

    private static final String LOTS = "/api/production/lots";
    private static final String ENTRIES = "/api/production/press-entries";
    private static final ZoneId INDIA = ZoneId.of("Asia/Kolkata");

    private final LocalDate today = LocalDate.now(INDIA);
    private final String fy = DocumentNumberService.financialYear(today);

    private String admin;
    private long companyId;
    private long plantId;
    private long yard, clay;
    private long mill, tank, sprayDryer, silo1, silo2, press1, press2, dryer;
    private long shiftA;
    private long marble, onyx;   // designs, both 600 x 1200 (0.72 sq m a tile)

    @BeforeEach
    void setUp() throws Exception {
        admin = adminToken();
        companyId = createCompany(admin, "PRSCO");
        plantId = createPlant(admin, companyId, "PRS-1");
        yard = read(postAs(admin, "/api/store/locations", Map.of("plantId", plantId, "code", "YARD", "name", "Clay yard"))
                .andExpect(status().isCreated())).get("id").asLong();
        clay = read(postAs(admin, "/api/masters/materials", Map.of("code", "CLAY-X", "name", "Clay",
                "groupId", idOf("material-groups", "BODY"), "unitId", idOf("units", "TON")))
                .andExpect(status().isCreated())).get("id").asLong();
        mill = createMachine("BM-1", "BALL_MILL");
        tank = createMachine("TANK-1", "SLIP_TANK");
        sprayDryer = createMachine("SD-1", "SPRAY_DRYER");
        silo1 = createMachine("SILO-1", "SILO");
        silo2 = createMachine("SILO-2", "SILO");
        press1 = createMachine("PRESS-1", "PRESS");
        press2 = createMachine("PRESS-2", "PRESS");
        dryer = createMachine("DRYER-1", "DRYER");
        shiftA = idOf("shifts", "A");
        marble = createDesign("MARBLE-X", "Statuario Marble");
        onyx = createDesign("ONYX-X", "Onyx Grey");

        Map<String, Object> opening = new HashMap<>();
        opening.put("plantId", plantId);
        opening.put("docType", "ADJUSTMENT");
        opening.put("docDate", today.toString());
        opening.put("reason", "OPENING");
        opening.put("lines", List.of(Map.of("materialId", clay, "locationId", yard, "quantity", 1000, "rate", 1000)));
        long adj = read(postAs(admin, "/api/store/documents", opening).andExpect(status().isCreated())).get("id").asLong();
        postAs(admin, "/api/store/documents/" + adj + "/post", Map.of()).andExpect(status().isOk());
    }

    // ------------------------------------------------------------------ lots

    @Test
    void aPressLineRunsOneDesignAtATime() throws Exception {
        JsonNode first = openLot(marble, press1, false);
        assertThat(first.get("lotNo").asText()).isEqualTo("PRS-1/LOT/" + fy + "/0001");
        assertThat(first.get("status").asText()).isEqualTo("OPEN");
        assertDecimal(first.get("sqmPerTile"), "0.72");

        postAs(admin, LOTS, lotBody(onyx, press1, false))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("PRESS-1 is running PRS-1/LOT/" + fy + "/0001 (Statuario Marble). Close it first."));

        // Changing the design: the running lot closes by itself.
        JsonNode second = openLot(onyx, press1, true);
        assertThat(second.get("status").asText()).isEqualTo("OPEN");
        getAs(admin, LOTS + "/" + first.get("id").asLong()).andExpect(jsonPath("$.status").value("CLOSED"));

        // Another press may run the same design at the same time.
        assertThat(openLot(marble, press2, false).get("status").asText()).isEqualTo("OPEN");
    }

    @Test
    void aLotCanOnlyBeCancelledBeforeAnythingIsPosted() throws Exception {
        long empty = openLot(marble, press1, false).get("id").asLong();
        postAs(admin, LOTS + "/" + empty + "/cancel", Map.of("reason", "Wrong design"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));

        powderInto(silo1, "50", 10);
        long used = openLot(onyx, press1, false).get("id").asLong();
        postEntry(entry(used, silo1, 1000, 0, 0, "21"));
        postAs(admin, LOTS + "/" + used + "/cancel", Map.of("reason", "Oops"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("PRS-1/LOT/" + fy + "/0002 has posted press entries. Cancel them first."));
    }

    // ------------------------------------------------------------------ press entries & powder

    @Test
    void pressEntryDrawsTheOldestPowderFirst() throws Exception {
        long older = powderInto(silo1, "30", 20);   // made 20 hours ago
        long newer = powderInto(silo1, "30", 10);   // made 10 hours ago
        long lot = openLot(marble, press1, false).get("id").asLong();

        // 2,000 tiles x 21 kg = 42 T: 30 T from the older run, 12 T from the newer one.
        JsonNode posted = postEntry(entry(lot, silo1, 2000, 0, 0, "21"));
        assertThat(posted.get("entryNo").asText()).isEqualTo("PRS-1/PR/" + fy + "/0001");
        assertDecimal(posted.get("powderTon"), "42");
        assertDecimal(posted.get("untracedTon"), "0");
        assertThat(posted.at("/powders/0/runId").asLong()).isEqualTo(older);
        assertDecimal(posted.at("/powders/0/tonnes"), "30");
        assertThat(posted.at("/powders/1/runId").asLong()).isEqualTo(newer);
        assertDecimal(posted.at("/powders/1/tonnes"), "12");

        assertDecimal(siloStatus("SILO-1").get("tonnes"), "18");
        assertDecimal(read(getAs(admin, "/api/production/spray-runs/" + newer)).get("powderLeft"), "18");

        // The lot's trace: which runs, how much, and the body batches behind them.
        JsonNode trace = read(getAs(admin, LOTS + "/" + lot)).get("trace");
        assertThat(trace.size()).isEqualTo(2);
        assertDecimal(trace.at("/0/tonnes"), "30");
        assertThat(trace.at("/0/batches/0/batchNo").asText()).startsWith("PRS-1/BB/");
    }

    @Test
    void powderBeyondTheSiloRecordIsAllowedButUntraced() throws Exception {
        powderInto(silo1, "10", 5);
        long lot = openLot(marble, press1, false).get("id").asLong();
        Map<String, Object> body = entry(lot, silo1, 700, 0, 0, null);
        body.put("powderTon", 15);

        JsonNode posted = postEntry(body);
        assertDecimal(posted.get("untracedTon"), "5");
        assertDecimal(read(getAs(admin, LOTS + "/" + lot)).at("/totals/untracedTon"), "5");
        assertDecimal(siloStatus("SILO-1").get("tonnes"), "0");
    }

    @Test
    void lotTotalsGiveGoodTilesSquareMetresAndLoss() throws Exception {
        powderInto(silo1, "50", 10);
        long lot = openLot(marble, press1, false).get("id").asLong();
        postEntry(entry(lot, silo1, 2000, 30, 20, "21"));

        JsonNode totals = read(getAs(admin, LOTS + "/" + lot)).get("totals");
        assertThat(totals.get("pressed").asLong()).isEqualTo(2000);
        assertThat(totals.get("good").asLong()).isEqualTo(1950);
        assertDecimal(totals.get("goodSqm"), "1404");      // 1,950 x 0.72
        assertDecimal(totals.get("lossPct"), "2.5");       // 50 of 2,000

        getAs(admin, LOTS + "?plantId=" + plantId)
                .andExpect(jsonPath("$.content[0].good").value(1950));
    }

    @Test
    void pressEntriesAreChecked() throws Exception {
        long lot = openLot(marble, press1, false).get("id").asLong();

        expectConflict(entry(lot, silo1, 100, 80, 30, "21"), "Rejects can't be more than the tiles pressed");
        expectConflict(entry(lot, silo1, 100, 0, 0, null), "Enter the green tile weight or the powder used");
        expectConflict(entry(lot, press2, 100, 0, 0, "21"), "PRESS-2 is a press, not a silo");

        Map<String, Object> early = entry(lot, silo1, 100, 0, 0, "21");
        early.put("prodDate", today.minusDays(1).toString());
        expectConflict(early, "The date can't be before the lot started ("
                + today.format(DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.ENGLISH)) + ")");
    }

    @Test
    void cancellingAnEntryPutsThePowderBack() throws Exception {
        powderInto(silo1, "30", 20);
        powderInto(silo1, "30", 10);
        long lot = openLot(marble, press1, false).get("id").asLong();
        long id = postEntry(entry(lot, silo1, 2000, 0, 0, "21")).get("id").asLong();
        assertDecimal(siloStatus("SILO-1").get("tonnes"), "18");

        postAs(admin, ENTRIES + "/" + id + "/cancel", Map.of("reason", "Typed for the wrong line"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));
        assertDecimal(siloStatus("SILO-1").get("tonnes"), "60");
        assertThat(read(getAs(admin, LOTS + "/" + lot)).get("trace").size()).isZero();
    }

    @Test
    void aSprayRunWhosePowderWasPressedCantBeCancelled() throws Exception {
        long run = powderInto(silo1, "30", 10);
        long lot = openLot(marble, press1, false).get("id").asLong();
        postEntry(entry(lot, silo1, 500, 0, 0, "21"));

        postAs(admin, "/api/production/spray-runs/" + run + "/cancel", Map.of("reason", "Test"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Powder from PRS-1/SD/" + fy + "/0001 was already used by press entry PRS-1/PR/"
                        + fy + "/0001. Cancel that entry first."));
    }

    @Test
    void markingASiloEmptyClearsItsRecord() throws Exception {
        powderInto(silo1, "25", 30);
        powderInto(silo2, "40", 5);

        JsonNode silos = read(postAs(admin, "/api/production/silos/" + silo1 + "/empty", Map.of()).andExpect(status().isOk()));
        assertDecimal(find(silos, "SILO-1").get("tonnes"), "0");
        assertDecimal(find(silos, "SILO-2").get("tonnes"), "40");     // other silos untouched
    }

    // ------------------------------------------------------------------ home screen, permissions, search

    @Test
    void thePressHomeShowsEachLineAndItsDesign() throws Exception {
        powderInto(silo1, "50", 10);
        long lot = openLot(marble, press1, false).get("id").asLong();
        postEntry(entry(lot, silo1, 1000, 10, 10, "21"));

        JsonNode overview = read(getAs(admin, "/api/production/press/overview?plantId=" + plantId));
        assertThat(overview.get("lines").size()).isEqualTo(2);
        JsonNode line1 = overview.get("lines").get(0).get("pressCode").asText().equals("PRESS-1")
                ? overview.get("lines").get(0) : overview.get("lines").get(1);
        assertThat(line1.at("/lot/designName").asText()).isEqualTo("Statuario Marble");
        assertThat(line1.at("/lot/good").asLong()).isEqualTo(980);
        assertThat(overview.get("silos").size()).isEqualTo(2);
    }

    @Test
    void eachSupervisorKeepsToTheirOwnDivision() throws Exception {
        long pressRole = createRole(admin, "Press supervisor T", "PRODUCTION_VIEW", "PRESS_ENTRY");
        long bodyRole = createRole(admin, "Body supervisor T2", "PRODUCTION_VIEW", "BODY_PREP_ENTRY");
        createUser(admin, "press.t", "password-1", pressRole, plantId);
        createUser(admin, "body.t2", "password-1", bodyRole, plantId);
        String pressSup = login("press.t", "password-1");
        String bodySup = login("body.t2", "password-1");

        powderInto(silo1, "50", 10);
        long lot = read(postAs(pressSup, LOTS, lotBody(marble, press1, false)).andExpect(status().isCreated())).get("id").asLong();
        long id = read(postAs(pressSup, ENTRIES, entry(lot, silo1, 500, 0, 0, "21")).andExpect(status().isCreated())).get("id").asLong();
        postAs(pressSup, ENTRIES + "/" + id + "/post", Map.of()).andExpect(status().isOk());
        getAs(pressSup, "/api/production/lookups/designs").andExpect(jsonPath("$[*].code", hasItem("MARBLE-X")));

        // The press supervisor can't charge mills, and the body supervisor can't start designs.
        // (A well-formed body on purpose: Spring validates @Valid @RequestBody while preparing the call,
        // BEFORE @PreAuthorize runs - an empty body would get 400, not 403.)
        postAs(pressSup, "/api/production/body-batches", batchBody(LocalDateTime.now(INDIA).withNano(0).minusHours(2)))
                .andExpect(status().isForbidden());
        postAs(bodySup, LOTS, lotBody(onyx, press2, false)).andExpect(status().isForbidden());
        // Both may mark a silo empty: one fills it, the other empties it.
        postAs(bodySup, "/api/production/silos/" + silo2 + "/empty", Map.of()).andExpect(status().isOk());
        postAs(pressSup, "/api/production/silos/" + silo2 + "/empty", Map.of()).andExpect(status().isOk());
    }

    @Test
    void lotsAreFoundByNumberOrDesign() throws Exception {
        openLot(onyx, press1, false);
        getAs(admin, "/api/search?q=onyx grey")
                .andExpect(jsonPath("$[*].type", hasItem("lot")))
                .andExpect(jsonPath("$[*].code", hasItem("PRS-1/LOT/" + fy + "/0001")));
    }

    // ------------------------------------------------------------------ helpers

    private Map<String, Object> lotBody(long designId, long pressId, boolean closeCurrent) {
        Map<String, Object> body = new HashMap<>();
        body.put("plantId", plantId);
        body.put("designId", designId);
        body.put("pressId", pressId);
        body.put("openedDate", today.toString());
        body.put("shiftId", shiftA);
        body.put("closeCurrent", closeCurrent);
        return body;
    }

    private JsonNode openLot(long designId, long pressId, boolean closeCurrent) throws Exception {
        return read(postAs(admin, LOTS, lotBody(designId, pressId, closeCurrent)).andExpect(status().isCreated()));
    }

    private Map<String, Object> entry(long lotId, long siloId, int pressed, int pressRejects, int dryerRejects,
                                      String greenWeight) {
        Map<String, Object> body = new HashMap<>();
        body.put("plantId", plantId);
        body.put("lotId", lotId);
        body.put("prodDate", today.toString());
        body.put("shiftId", shiftA);
        body.put("dryerId", dryer);
        body.put("siloId", siloId);
        body.put("pressedCount", pressed);
        body.put("pressRejects", pressRejects);
        body.put("dryerRejects", dryerRejects);
        body.put("greenWeightKg", greenWeight == null ? null : new BigDecimal(greenWeight));
        body.put("pressure", 380);
        body.put("cyclesPerMin", 5.5);
        return body;
    }

    private JsonNode postEntry(Map<String, Object> body) throws Exception {
        long id = read(postAs(admin, ENTRIES, body).andExpect(status().isCreated())).get("id").asLong();
        return read(postAs(admin, ENTRIES + "/" + id + "/post", Map.of()).andExpect(status().isOk()));
    }

    private void expectConflict(Map<String, Object> body, String message) throws Exception {
        postAs(admin, ENTRIES, body).andExpect(status().isConflict()).andExpect(jsonPath("$.message").value(message));
    }

    private Map<String, Object> batchBody(LocalDateTime millingStart) {
        Map<String, Object> batch = new HashMap<>();
        batch.put("plantId", plantId);
        batch.put("prodDate", today.toString());
        batch.put("shiftId", shiftA);
        batch.put("ballMillId", mill);
        batch.put("chargeTon", 40);
        batch.put("millingStart", millingStart.toString());
        batch.put("lines", List.of(Map.of("materialId", clay, "locationId", yard, "quantity", 10)));
        return batch;
    }

    /** Charge, discharge and spray one body batch: puts `tonnes` of powder into a silo, made `hoursAgo`. */
    private long powderInto(long siloId, String tonnes, int hoursAgo) throws Exception {
        LocalDateTime now = LocalDateTime.now(INDIA).withNano(0);
        Map<String, Object> batch = batchBody(now.minusHours(hoursAgo + 12));
        long batchId = read(postAs(admin, "/api/production/body-batches", batch).andExpect(status().isCreated())).get("id").asLong();
        postAs(admin, "/api/production/body-batches/" + batchId + "/charge", Map.of()).andExpect(status().isOk());
        postAs(admin, "/api/production/body-batches/" + batchId + "/discharge", Map.of(
                "millingEnd", now.minusHours(hoursAgo + 1).toString(), "slipTankId", tank,
                "slipDensity", 1700, "slipResidue", 2.5)).andExpect(status().isOk());

        Map<String, Object> run = new HashMap<>();
        run.put("plantId", plantId);
        run.put("prodDate", today.toString());
        run.put("shiftId", shiftA);
        run.put("sprayDryerId", sprayDryer);
        run.put("siloId", siloId);
        run.put("runStart", now.minusHours(hoursAgo).toString());
        run.put("powderTon", new BigDecimal(tonnes));
        run.put("batches", List.of(Map.of("bodyBatchId", batchId, "finished", true)));
        long runId = read(postAs(admin, "/api/production/spray-runs", run).andExpect(status().isCreated())).get("id").asLong();
        postAs(admin, "/api/production/spray-runs/" + runId + "/post", Map.of()).andExpect(status().isOk());
        return runId;
    }

    private JsonNode siloStatus(String code) throws Exception {
        return find(read(getAs(admin, "/api/production/silos?plantId=" + plantId).andExpect(status().isOk())), code);
    }

    private static JsonNode find(JsonNode silos, String code) {
        for (JsonNode s : silos) {
            if (s.get("code").asText().equals(code)) return s;
        }
        throw new AssertionError(code + " not found");
    }

    private static void assertDecimal(JsonNode node, String expected) {
        assertThat(node.decimalValue()).isEqualByComparingTo(expected);
    }

    private long createMachine(String code, String type) throws Exception {
        return read(postAs(admin, "/api/masters/machines", Map.of("plantId", plantId, "code", code,
                "name", "Machine " + code, "type", type)).andExpect(status().isCreated())).get("id").asLong();
    }

    private long createDesign(String code, String name) throws Exception {
        return read(postAs(admin, "/api/masters/designs", Map.of("code", code, "name", name,
                "categoryId", idOf("categories", "GVT"), "sizeId", idOf("sizes", "600X1200"),
                "finishId", idOf("finishes", "GLOSSY"), "faces", 4)).andExpect(status().isCreated())).get("id").asLong();
    }

    private long idOf(String master, String code) throws Exception {
        for (JsonNode n : read(getAs(admin, "/api/masters/" + master))) {
            if (n.get("code").asText().equals(code)) return n.get("id").asLong();
        }
        throw new AssertionError(code + " not found in " + master);
    }
}
