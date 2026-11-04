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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Press & dryer entries, the silo's oldest-first powder, and design lots - which run on the digital printer, since
 * the press only makes green tiles. A lot's trace back to the body goes through the press entries of its shifts.
 */
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
    private long mill, tank, sprayDryer, silo1, silo2, press1, press2, dryer, glaze1, glaze2, printer1, printer2;
    private long shiftA, shiftB;
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
        glaze1 = createMachine("GL-1", "GLAZE_LINE");
        glaze2 = createMachine("GL-2", "GLAZE_LINE");
        printer1 = createMachine("DP-1", "DIGITAL_PRINTER");
        printer2 = createMachine("DP-2", "DIGITAL_PRINTER");
        shiftA = idOf("shifts", "A");
        shiftB = idOf("shifts", "B");
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

    // ------------------------------------------------------------------ lots (on the printer)

    @Test
    void aPrinterRunsOneDesignAtATime() throws Exception {
        JsonNode first = openLot(marble, printer1, false);
        assertThat(first.get("lotNo").asText()).isEqualTo("PRS-1/LOT/" + fy + "/0001");
        assertThat(first.get("status").asText()).isEqualTo("OPEN");
        assertThat(first.get("printerCode").asText()).isEqualTo("DP-1");
        assertDecimal(first.get("sqmPerTile"), "0.72");

        postAs(admin, LOTS, lotBody(onyx, printer1, false))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("DP-1 is running PRS-1/LOT/" + fy + "/0001 (Statuario Marble). Close it first."));

        // Changing the design: the running lot closes by itself.
        JsonNode second = openLot(onyx, printer1, true);
        assertThat(second.get("status").asText()).isEqualTo("OPEN");
        getAs(admin, LOTS + "/" + first.get("id").asLong()).andExpect(jsonPath("$.status").value("CLOSED"));

        // Another printer may run the same design at the same time.
        assertThat(openLot(marble, printer2, false).get("status").asText()).isEqualTo("OPEN");
        // A design is started on a printer, never on a press.
        postAs(admin, LOTS, lotBody(marble, press1, false))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("PRESS-1 is a press, not a digital printer"));
    }

    @Test
    void aLotCanOnlyBeCancelledBeforeItIsGlazed() throws Exception {
        long empty = openLot(marble, printer1, false).get("id").asLong();
        postAs(admin, LOTS + "/" + empty + "/cancel", Map.of("reason", "Wrong design"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));

        long used = openLot(onyx, printer1, false).get("id").asLong();
        glaze(used, glaze1, shiftA, 500);
        postAs(admin, LOTS + "/" + used + "/cancel", Map.of("reason", "Oops"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("PRS-1/LOT/" + fy + "/0002 has posted glaze entries. Cancel them first."));
    }

    // ------------------------------------------------------------------ press entries & powder

    @Test
    void aPressEntryIsJustGreenTiles() throws Exception {
        powderInto(silo1, "50", 10);
        JsonNode posted = postEntry(entry(press1, silo1, 1000, 10, 10, "21"));
        assertThat(posted.get("pressCode").asText()).isEqualTo("PRESS-1");
        assertThat(posted.get("goodCount").asInt()).isEqualTo(980);
        assertThat(posted.has("lotId")).isFalse();
        assertThat(posted.has("designName")).isFalse();
        assertThat(posted.has("goodSqm")).isFalse();       // the press doesn't know the size: no design yet

        getAs(admin, ENTRIES + "?plantId=" + plantId + "&search=press-1")
                .andExpect(jsonPath("$.content[0].pressCode").value("PRESS-1"));
    }

    @Test
    void pressEntryDrawsTheOldestPowderFirst() throws Exception {
        long older = powderInto(silo1, "30", 20);   // made 20 hours ago
        long newer = powderInto(silo1, "30", 10);   // made 10 hours ago

        // 2,000 tiles x 21 kg = 42 T: 30 T from the older run, 12 T from the newer one.
        JsonNode posted = postEntry(entry(press1, silo1, 2000, 0, 0, "21"));
        assertThat(posted.get("entryNo").asText()).isEqualTo("PRS-1/PR/" + fy + "/0001");
        assertDecimal(posted.get("powderTon"), "42");
        assertDecimal(posted.get("untracedTon"), "0");
        assertThat(posted.at("/powders/0/runId").asLong()).isEqualTo(older);
        assertDecimal(posted.at("/powders/0/tonnes"), "30");
        assertThat(posted.at("/powders/1/runId").asLong()).isEqualTo(newer);
        assertDecimal(posted.at("/powders/1/tonnes"), "12");

        assertDecimal(siloStatus("SILO-1").get("tonnes"), "18");
        assertDecimal(read(getAs(admin, "/api/production/spray-runs/" + newer)).get("powderLeft"), "18");

        // A lot glazed in that shift traces back through it: which runs, how much, and the body batches behind them.
        long lot = openLot(marble, printer1, false).get("id").asLong();
        assertThat(read(getAs(admin, LOTS + "/" + lot)).get("trace").size()).isZero();   // not glazed yet
        glaze(lot, glaze1, shiftA, 1950);
        JsonNode trace = read(getAs(admin, LOTS + "/" + lot)).get("trace");
        assertThat(trace.size()).isEqualTo(2);
        assertDecimal(trace.at("/0/tonnes"), "30");
        assertThat(trace.at("/0/batches/0/batchNo").asText()).startsWith("PRS-1/BB/");
    }

    @Test
    void powderBeyondTheSiloRecordIsAllowedButUntraced() throws Exception {
        powderInto(silo1, "10", 5);
        Map<String, Object> body = entry(press1, silo1, 700, 0, 0, null);
        body.put("powderTon", 15);

        JsonNode posted = postEntry(body);
        assertDecimal(posted.get("untracedTon"), "5");
        assertDecimal(siloStatus("SILO-1").get("tonnes"), "0");

        long lot = openLot(marble, printer1, false).get("id").asLong();
        glaze(lot, glaze1, shiftA, 700);
        assertDecimal(read(getAs(admin, LOTS + "/" + lot)).at("/pressWork/untracedTon"), "5");
    }

    @Test
    void aLotShowsThePressWorkOfItsShifts() throws Exception {
        powderInto(silo1, "50", 10);
        postEntry(entry(press1, silo1, 2000, 30, 20, "21"));
        long lot = openLot(marble, printer1, false).get("id").asLong();
        glaze(lot, glaze1, shiftA, 1900);

        JsonNode detail = read(getAs(admin, LOTS + "/" + lot));
        JsonNode work = detail.get("pressWork");
        assertThat(work.get("entries").asLong()).isEqualTo(1);
        assertThat(work.get("pressed").asLong()).isEqualTo(2000);
        assertThat(work.get("good").asLong()).isEqualTo(1950);
        assertDecimal(work.get("lossPct"), "2.5");         // 50 of 2,000
        assertThat(detail.at("/entries/0/pressCode").asText()).isEqualTo("PRESS-1");
        // the funnel starts at the glaze line: press losses are the plant's, not the design's
        assertThat(detail.at("/flow/glazeIn").asLong()).isEqualTo(1900);
        assertThat(detail.get("flow").has("pressed")).isFalse();

        getAs(admin, LOTS + "?plantId=" + plantId)
                .andExpect(jsonPath("$.content[0].glazed").value(1900))
                .andExpect(jsonPath("$.content[0].printerCode").value("DP-1"));
    }

    @Test
    void pressEntriesAreChecked() throws Exception {
        expectConflict(entry(press1, silo1, 100, 80, 30, "21"), "Rejects can't be more than the tiles pressed");
        expectConflict(entry(press1, silo1, 100, 0, 0, null), "Enter the green tile weight or the powder used");
        expectConflict(entry(press1, press2, 100, 0, 0, "21"), "PRESS-2 is a press, not a silo");
        expectConflict(entry(silo1, silo1, 100, 0, 0, "21"), "SILO-1 is a silo, not a press");

        Map<String, Object> later = entry(press1, silo1, 100, 0, 0, "21");
        later.put("prodDate", today.plusDays(1).toString());
        postAs(admin, ENTRIES, later).andExpect(status().isConflict());
    }

    @Test
    void cancellingAnEntryPutsThePowderBack() throws Exception {
        powderInto(silo1, "30", 20);
        powderInto(silo1, "30", 10);
        long id = postEntry(entry(press1, silo1, 2000, 0, 0, "21")).get("id").asLong();
        assertDecimal(siloStatus("SILO-1").get("tonnes"), "18");
        long lot = openLot(marble, printer1, false).get("id").asLong();
        glaze(lot, glaze1, shiftA, 2000);
        assertThat(read(getAs(admin, LOTS + "/" + lot)).get("trace").size()).isEqualTo(2);

        postAs(admin, ENTRIES + "/" + id + "/cancel", Map.of("reason", "Typed for the wrong line"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));
        assertDecimal(siloStatus("SILO-1").get("tonnes"), "60");
        assertThat(read(getAs(admin, LOTS + "/" + lot)).get("trace").size()).isZero();
    }

    @Test
    void aSprayRunWhosePowderWasPressedCantBeCancelled() throws Exception {
        long run = powderInto(silo1, "30", 10);
        postEntry(entry(press1, silo1, 500, 0, 0, "21"));

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

    // ------------------------------------------------------------------ the trace on different line-ups

    /** One line, two presses taking turns in front of one printer: each lot traces to whichever press ran. */
    @Test
    void oneLineTwoPressesTakingTurns() throws Exception {
        link(press1, glaze1);
        link(press2, glaze1);
        link(glaze1, printer1);
        long runA = powderInto(silo1, "30", 20);
        long runB = powderInto(silo2, "30", 10);
        postEntry(entry(press1, silo1, 1000, 0, 0, "21"));                   // shift A: PRESS-1 from SILO-1
        Map<String, Object> night = entry(press2, silo2, 1000, 0, 0, "21");  // shift B: PRESS-2 from SILO-2
        night.put("shiftId", shiftB);
        postEntry(night);

        long lot = openLot(marble, printer1, false).get("id").asLong();
        glaze(lot, glaze1, shiftA, 1000);
        assertThat(runsIn(lot)).containsExactly(runA);

        glaze(lot, glaze1, shiftB, 1000);                                    // the design kept running at night
        assertThat(runsIn(lot)).containsExactlyInAnyOrder(runA, runB);
        assertThat(read(getAs(admin, LOTS + "/" + lot)).at("/pressWork/entries").asLong()).isEqualTo(2);
    }

    /** Two separate lines in the same shift: once the layout says so, each lot keeps to its own line's press. */
    @Test
    void twoLinesKeepTheirOwnPresses() throws Exception {
        long runA = powderInto(silo1, "30", 20);
        long runB = powderInto(silo2, "30", 10);
        postEntry(entry(press1, silo1, 1000, 0, 0, "21"));
        postEntry(entry(press2, silo2, 1000, 0, 0, "21"));
        long marbleLot = openLot(marble, printer1, false).get("id").asLong();
        long onyxLot = openLot(onyx, printer2, false).get("id").asLong();
        glaze(marbleLot, glaze1, shiftA, 1000);
        glaze(onyxLot, glaze2, shiftA, 1000);

        // No layout yet: every press of the plant counts.
        assertThat(runsIn(marbleLot)).containsExactlyInAnyOrder(runA, runB);

        link(press1, dryer);
        link(dryer, glaze1);
        link(glaze1, printer1);
        link(press2, glaze2);
        link(glaze2, printer2);
        assertThat(runsIn(marbleLot)).containsExactly(runA);
        assertThat(runsIn(onyxLot)).containsExactly(runB);
    }

    // ------------------------------------------------------------------ home screen, permissions, search

    @Test
    void thePressHomeShowsEachPressAndItsPieces() throws Exception {
        powderInto(silo1, "50", 10);
        postEntry(entry(press1, silo1, 1000, 10, 10, "21"));

        JsonNode overview = read(getAs(admin, "/api/production/press/overview?plantId=" + plantId));
        assertThat(overview.get("lines").size()).isEqualTo(2);
        JsonNode line1 = overview.get("lines").get(0).get("pressCode").asText().equals("PRESS-1")
                ? overview.get("lines").get(0) : overview.get("lines").get(1);
        assertThat(line1.has("lot")).isFalse();                              // no design at the press
        if (overview.get("prodDate").asText().equals(today.toString())) {
            assertThat(line1.get("pressed").asLong()).isEqualTo(1000);
            assertThat(line1.get("good").asLong()).isEqualTo(980);
        }
        assertThat(overview.get("silos").size()).isEqualTo(2);
    }

    @Test
    void eachSupervisorKeepsToTheirOwnDivision() throws Exception {
        long pressRole = createRole(admin, "Press supervisor T", "PRODUCTION_VIEW", "PRESS_ENTRY");
        long glazeRole = createRole(admin, "Glaze supervisor T4", "PRODUCTION_VIEW", "GLAZE_ENTRY");
        long bodyRole = createRole(admin, "Body supervisor T2", "PRODUCTION_VIEW", "BODY_PREP_ENTRY");
        createUser(admin, "press.t", "password-1", pressRole, plantId);
        createUser(admin, "glaze.t4", "password-1", glazeRole, plantId);
        createUser(admin, "body.t2", "password-1", bodyRole, plantId);
        String pressSup = login("press.t", "password-1");
        String glazeSup = login("glaze.t4", "password-1");
        String bodySup = login("body.t2", "password-1");

        powderInto(silo1, "50", 10);
        long id = read(postAs(pressSup, ENTRIES, entry(press1, silo1, 500, 0, 0, "21")).andExpect(status().isCreated())).get("id").asLong();
        postAs(pressSup, ENTRIES + "/" + id + "/post", Map.of()).andExpect(status().isOk());

        // Designs are started at the printer: the glaze & print supervisor's job, not the press supervisor's.
        postAs(pressSup, LOTS, lotBody(marble, printer1, false)).andExpect(status().isForbidden());
        postAs(glazeSup, LOTS, lotBody(marble, printer1, false)).andExpect(status().isCreated());
        getAs(glazeSup, "/api/production/lookups/designs").andExpect(jsonPath("$[*].code", hasItem("MARBLE-X")));

        // The press supervisor can't charge mills, and the body supervisor can't start designs.
        // (A well-formed body on purpose: Spring validates @Valid @RequestBody while preparing the call,
        // BEFORE @PreAuthorize runs - an empty body would get 400, not 403.)
        postAs(pressSup, "/api/production/body-batches", batchBody(LocalDateTime.now(INDIA).withNano(0).minusHours(2)))
                .andExpect(status().isForbidden());
        postAs(bodySup, LOTS, lotBody(onyx, printer2, false)).andExpect(status().isForbidden());
        // Both may mark a silo empty: one fills it, the other empties it.
        postAs(bodySup, "/api/production/silos/" + silo2 + "/empty", Map.of()).andExpect(status().isOk());
        postAs(pressSup, "/api/production/silos/" + silo2 + "/empty", Map.of()).andExpect(status().isOk());
    }

    @Test
    void lotsAreFoundByNumberOrDesign() throws Exception {
        openLot(onyx, printer1, false);
        getAs(admin, "/api/search?q=onyx grey")
                .andExpect(jsonPath("$[*].type", hasItem("lot")))
                .andExpect(jsonPath("$[*].code", hasItem("PRS-1/LOT/" + fy + "/0001")));
    }

    // ------------------------------------------------------------------ helpers

    private Map<String, Object> lotBody(long designId, long printerId, boolean closeCurrent) {
        Map<String, Object> body = new HashMap<>();
        body.put("plantId", plantId);
        body.put("designId", designId);
        body.put("printerId", printerId);
        body.put("openedDate", today.toString());
        body.put("shiftId", shiftA);
        body.put("closeCurrent", closeCurrent);
        return body;
    }

    private JsonNode openLot(long designId, long printerId, boolean closeCurrent) throws Exception {
        return read(postAs(admin, LOTS, lotBody(designId, printerId, closeCurrent)).andExpect(status().isCreated()));
    }

    private Map<String, Object> entry(long pressId, long siloId, int pressed, int pressRejects, int dryerRejects,
                                      String greenWeight) {
        Map<String, Object> body = new HashMap<>();
        body.put("plantId", plantId);
        body.put("pressId", pressId);
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

    /** A posted glaze entry for a lot, today, in the given shift. */
    private void glaze(long lotId, long glazeLine, long shiftId, int tilesIn) throws Exception {
        long id = read(postAs(admin, "/api/production/line-entries", Map.of("plantId", plantId, "stage", "GLAZE",
                "lotId", lotId, "prodDate", today.toString(), "shiftId", shiftId, "machineId", glazeLine,
                "tilesIn", tilesIn, "rejects", 0)).andExpect(status().isCreated())).get("id").asLong();
        postAs(admin, "/api/production/line-entries/" + id + "/post", Map.of()).andExpect(status().isOk());
    }

    /** One arrow of the layout: from feeds into to (keeping what from already fed). */
    private void link(long from, long to) throws Exception {
        List<Long> feeds = new java.util.ArrayList<>();
        for (JsonNode l : read(getAs(admin, "/api/masters/machines/links?plantId=" + plantId))) {
            if (l.get("fromMachineId").asLong() == from) feeds.add(l.get("toMachineId").asLong());
        }
        feeds.add(to);
        putAs(admin, "/api/masters/machines/" + from + "/feeds", Map.of("toMachineIds", feeds)).andExpect(status().isOk());
    }

    /** The spray runs in a lot's trace. */
    private List<Long> runsIn(long lotId) throws Exception {
        List<Long> runs = new java.util.ArrayList<>();
        read(getAs(admin, LOTS + "/" + lotId)).get("trace").forEach(r -> runs.add(r.get("runId").asLong()));
        return runs;
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
