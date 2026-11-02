package com.ceramicerp.store;

import com.ceramicerp.IntegrationTestBase;
import com.ceramicerp.numbering.DocumentNumberService;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Step 10a: raw material lot by lot, and the trace from the truck to the design lot. */
class MaterialLotIntegrationTest extends IntegrationTestBase {

    private static final String DOCS = "/api/store/documents";
    private static final String LOTS = "/api/store/lots";
    private static final ZoneId INDIA = ZoneId.of("Asia/Kolkata");

    private final LocalDate today = LocalDate.now(INDIA);
    private final String fy = DocumentNumberService.financialYear(today);

    @Autowired
    private JdbcTemplate jdbc;

    @PersistenceContext
    private EntityManager entityManager;

    private String admin;
    private long companyId;
    private long plantId;
    private long yard;
    private long clay, feldspar;
    private long supplier;

    @BeforeEach
    void setUp() throws Exception {
        admin = adminToken();
        companyId = createCompany(admin, "LOTCO");
        plantId = createPlant(admin, companyId, "LOT-1");
        yard = read(postAs(admin, "/api/store/locations", Map.of("plantId", plantId, "code", "YARD", "name", "Clay yard"))
                .andExpect(status().isCreated())).get("id").asLong();
        clay = createMaterial("CLAY-L", "Clay");
        feldspar = createMaterial("FELD-L", "Feldspar");
        supplier = read(postAs(admin, "/api/parties", Map.of("partyType", "SUPPLIER", "name", "Shree Clay Mines",
                "gstRegistration", "UNREGISTERED")).andExpect(status().isCreated())).get("id").asLong();
    }

    // ------------------------------------------------------------------ in

    @Test
    void everyGrnLineIsALotOfItsOwn() throws Exception {
        Map<String, Object> second = grnLine(clay, "4", "1000");
        second.put("moisturePct", new BigDecimal("6.5"));
        JsonNode grn = post(grn(grnLine(clay, "10", "1000"), second));

        JsonNode lots = lots("");
        assertThat(lots.get("totalElements").asInt()).isEqualTo(2);
        JsonNode first = lots.at("/content/1");   // the full list is newest first
        assertThat(first.get("lotNo").asText()).isEqualTo("LOT-1/GRN/" + fy + "/0001-1");
        assertThat(first.get("materialCode").asText()).isEqualTo("CLAY-L");
        assertThat(first.get("supplierName").asText()).isEqualTo("Shree Clay Mines");
        assertThat(first.get("vehicleNo").asText()).isEqualTo("GJ36T1234");
        assertThat(first.get("documentId").asLong()).isEqualTo(grn.get("id").asLong());
        assertDecimal(first.get("quantityIn"), "10");
        assertDecimal(first.get("quantityLeft"), "10");
        assertDecimal(lots.at("/content/0/moisturePct"), "6.5");
    }

    @Test
    void whatWasRejectedInFullMakesNoLot() throws Exception {
        Map<String, Object> line = grnLine(clay, "10", "1000");
        line.put("rejectedQty", new BigDecimal("10"));
        post(grn(line));
        assertThat(lots("").get("totalElements").asInt()).isZero();
    }

    @Test
    void anAdjustmentThatAddsStockIsALotWithoutASupplier() throws Exception {
        post(adjustment("PHYSICAL_COUNT", adjLine(clay, "3", "900")));
        JsonNode lot = lots("").at("/content/0");
        assertThat(lot.get("lotNo").asText()).isEqualTo("LOT-1/ADJ/" + fy + "/0001-1");
        assertThat(lot.get("docType").asText()).isEqualTo("ADJUSTMENT");
        assertThat(lot.has("supplierName")).isFalse();
    }

    // ------------------------------------------------------------------ out

    @Test
    void anIssueTakesTheOldestLotFirst() throws Exception {
        post(grn(grnLine(clay, "10", "1000")));
        post(grn(grnLine(clay, "10", "1000")));
        JsonNode issue = post(issue(issueLine(clay, "14")));

        JsonNode lots = lots("&open=true");
        assertThat(lots.get("totalElements").asInt()).isEqualTo(1);
        assertThat(lots.at("/content/0/lotNo").asText()).isEqualTo("LOT-1/GRN/" + fy + "/0002-1");
        assertDecimal(lots.at("/content/0/quantityLeft"), "6");
        assertThat(lots("&open=false").at("/content/0/lotNo").asText()).isEqualTo("LOT-1/GRN/" + fy + "/0001-1");

        JsonNode behind = read(getAs(admin, LOTS + "/of-document/" + issue.get("id").asLong()).andExpect(status().isOk()));
        assertThat(behind).hasSize(2);
        assertDecimal(behind.at("/0/quantity"), "10");
        assertDecimal(behind.at("/1/quantity"), "4");
        assertLotsAddUpToStock(clay);
    }

    @Test
    void anAdjustmentThatReducesStockTakesTheOldestLotToo() throws Exception {
        post(grn(grnLine(clay, "10", "1000")));
        post(grn(grnLine(clay, "5", "1000")));
        post(adjustment("DAMAGE", adjLine(clay, "-12", null)));
        assertDecimal(lots("&open=true").at("/content/0/quantityLeft"), "3");
        assertLotsAddUpToStock(clay);
    }

    @Test
    void lotsDontChangeTheValueOfStock() throws Exception {
        post(grn(grnLine(clay, "10", "1000")));
        post(grn(grnLine(clay, "10", "2000")));
        // only the first truck is drawn, at 1000 a ton, but stock is still valued at the godown's average of 1500
        JsonNode issue = post(issue(issueLine(clay, "5")));
        assertDecimal(issue.at("/lines/0/value"), "7500");
    }

    @Test
    void aLotShowsEverythingThatUsedIt() throws Exception {
        post(grn(grnLine(clay, "10", "1000")));
        post(issue(issueLine(clay, "4")));
        JsonNode second = post(issue(issueLine(clay, "3")));
        long lotId = lots("").at("/content/0/id").asLong();

        JsonNode detail = read(getAs(admin, LOTS + "/" + lotId).andExpect(status().isOk()));
        assertThat(detail.get("uses")).hasSize(2);
        assertThat(detail.at("/uses/0/docNo").asText()).isEqualTo(second.get("docNo").asText());
        assertThat(detail.at("/uses/0/issuedTo").asText()).isEqualTo("Ball mill section");
        assertDecimal(detail.at("/uses/0/quantity"), "3");
    }

    // ------------------------------------------------------------------ cancelling

    @Test
    void cancellingAnIssuePutsItBackOnTheSameLots() throws Exception {
        post(grn(grnLine(clay, "10", "1000")));
        post(grn(grnLine(clay, "10", "1000")));
        JsonNode issue = post(issue(issueLine(clay, "14")));
        cancel(issue);

        JsonNode lots = lots("&open=true");
        assertDecimal(lots.at("/content/0/quantityLeft"), "10");
        assertDecimal(lots.at("/content/1/quantityLeft"), "10");
        assertLotsAddUpToStock(clay);
        // nothing used it any more
        assertThat(read(getAs(admin, LOTS + "/" + lots.at("/content/0/id").asLong())).get("uses")).isEmpty();
        assertThat(read(getAs(admin, LOTS + "/of-document/" + issue.get("id").asLong()))).isEmpty();
        // and the oldest is used next again
        post(issue(issueLine(clay, "2")));
        assertDecimal(lots("&open=true").at("/content/0/quantityLeft"), "8");
    }

    @Test
    void aGrnWhoseLotWasUsedCantBeCancelledEvenWithOtherStockInTheGodown() throws Exception {
        JsonNode first = post(grn(grnLine(clay, "10", "1000")));
        post(grn(grnLine(clay, "10", "1000")));
        JsonNode issue = post(issue(issueLine(clay, "4")));

        postAs(admin, DOCS + "/" + first.get("id").asLong() + "/cancel", Map.of("reason", "Wrong truck"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Can't cancel LOT-1/GRN/" + fy + "/0001. Lot LOT-1/GRN/" + fy
                        + "/0001-1 is already used by " + issue.get("docNo").asText()
                        + ". Cancel that first, or correct the stock with an adjustment."));
    }

    @Test
    void aGrnCanBeCancelledOnceWhatUsedItIsCancelled() throws Exception {
        JsonNode first = post(grn(grnLine(clay, "10", "1000")));
        post(grn(grnLine(clay, "10", "1000")));
        cancel(post(issue(issueLine(clay, "4"))));
        cancel(first);
        assertLotsAddUpToStock(clay);
    }

    @Test
    void aCancelledGrnsLotIsOnlyInTheFullList() throws Exception {
        JsonNode grn = post(grn(grnLine(clay, "10", "1000")));
        cancel(grn);

        assertThat(lots("&open=true").get("totalElements").asInt()).isZero();
        assertThat(lots("&open=false").get("totalElements").asInt()).isZero();
        JsonNode all = lots("");
        assertThat(all.at("/content/0/cancelled").asBoolean()).isTrue();
        assertDecimal(all.at("/content/0/quantityLeft"), "0");
        assertThat(read(getAs(admin, LOTS + "/of-document/" + grn.get("id").asLong()))).isEmpty();
    }

    @Test
    void anIssueFromBeforeLotsComesBackAsOldStock() throws Exception {
        post(grn(grnLine(clay, "10", "1000")));
        JsonNode issue = post(issue(issueLine(clay, "4")));
        // as if it was posted before lots began: its stock is out, but no draw says where from
        entityManager.flush();
        jdbc.update("delete from material_lot_draws");
        entityManager.clear();

        cancel(issue);
        JsonNode lots = lots("&open=true");
        assertThat(lots.get("totalElements").asInt()).isEqualTo(2);
        JsonNode old = lots.at("/content/1");
        assertThat(old.get("lotNo").asText()).startsWith("OLD-T");
        assertThat(old.has("documentId")).isFalse();
        assertDecimal(old.get("quantityLeft"), "4");
    }

    // ------------------------------------------------------------------ the list

    @Test
    void lotsAreFoundByMaterialSupplierVehicleOrNumber() throws Exception {
        post(grn(grnLine(clay, "10", "1000")));
        post(grn(grnLine(feldspar, "10", "1000")));

        assertThat(lots("&materialId=" + feldspar).at("/content/0/materialCode").asText()).isEqualTo("FELD-L");
        assertThat(lots("&locationId=" + yard).get("totalElements").asInt()).isEqualTo(2);
        assertThat(lots("&search=feld").get("totalElements").asInt()).isEqualTo(1);
        assertThat(lots("&search=shree").get("totalElements").asInt()).isEqualTo(2);
        assertThat(lots("&search=GJ36T").get("totalElements").asInt()).isEqualTo(2);
        assertThat(lots("&search=/0002-1").get("totalElements").asInt()).isEqualTo(1);
        assertThat(lots("&search=nothing").get("totalElements").asInt()).isZero();
    }

    @Test
    void lotsStayInsideThePlantAndNeedStoreView() throws Exception {
        post(grn(grnLine(clay, "10", "1000")));
        long lotId = lots("").at("/content/0/id").asLong();
        long otherPlant = createPlant(admin, companyId, "LOT-2");

        long keeper = createRole(admin, "Keeper L", "STORE_VIEW");
        createUser(admin, "keeper.l", "password-1", keeper, otherPlant);
        String other = login("keeper.l", "password-1");
        getAs(other, LOTS + "?plantId=" + plantId).andExpect(status().isForbidden());
        getAs(other, LOTS + "/" + lotId).andExpect(status().isForbidden());

        long viewer = createRole(admin, "Press L", "PRODUCTION_VIEW");
        createUser(admin, "press.l", "password-1", viewer, plantId);
        getAs(login("press.l", "password-1"), LOTS + "?plantId=" + plantId).andExpect(status().isForbidden());
    }

    // ------------------------------------------------------------------ the trace

    @Test
    void theTraceRunsFromTheTruckToTheDesignLotAndBack() throws Exception {
        long tank = machine("TANK-L", "SLIP_TANK");
        long sprayDryer = machine("SD-L", "SPRAY_DRYER");
        long silo = machine("SILO-L", "SILO");
        long press = machine("PRESS-L", "PRESS");
        long dryer = machine("DRYER-L", "DRYER");
        long mill = machine("BM-L", "BALL_MILL");
        long shift = idOf("shifts", "A");

        Map<String, Object> firstLine = grnLine(clay, "6", "1000");
        firstLine.put("moisturePct", new BigDecimal("7.5"));
        post(grn(firstLine));
        post(grn(grnLine(clay, "20", "1000")));
        long truck1 = lots("").at("/content/1/id").asLong();
        long truck2 = lots("").at("/content/0/id").asLong();

        // the mill charge takes all of the first truck and 4 T of the second
        long batch = chargeAndSpray(mill, tank, sprayDryer, silo, shift, "10");

        JsonNode behindBatch = read(getAs(admin, "/api/production/trace/body-batches/" + batch).andExpect(status().isOk()));
        assertThat(behindBatch).hasSize(2);
        assertThat(behindBatch.at("/0/lotId").asLong()).isEqualTo(truck1);
        assertDecimal(behindBatch.at("/0/quantity"), "6");
        assertDecimal(behindBatch.at("/0/moisturePct"), "7.5");
        assertThat(behindBatch.at("/0/supplierName").asText()).isEqualTo("Shree Clay Mines");
        assertDecimal(behindBatch.at("/1/quantity"), "4");

        // nothing pressed yet: the truck reached the mill charge, but no design lot
        JsonNode reach = read(getAs(admin, "/api/production/trace/material-lots/" + truck1).andExpect(status().isOk()));
        assertThat(reach.get("batches")).hasSize(1);
        assertThat(reach.get("designLots")).isEmpty();

        long design = read(postAs(admin, "/api/masters/designs", Map.of("code", "TRACE-L", "name", "Trace Marble",
                "categoryId", idOf("categories", "GVT"), "sizeId", idOf("sizes", "600X1200"),
                "finishId", idOf("finishes", "GLOSSY"), "faces", 4)).andExpect(status().isCreated())).get("id").asLong();
        Map<String, Object> open = new HashMap<>();
        open.put("plantId", plantId);
        open.put("designId", design);
        open.put("pressId", press);
        open.put("openedDate", today.toString());
        open.put("shiftId", shift);
        open.put("closeCurrent", false);
        JsonNode designLot = read(postAs(admin, "/api/production/lots", open).andExpect(status().isCreated()));
        long designLotId = designLot.get("id").asLong();

        // no powder used yet
        assertThat(read(getAs(admin, "/api/production/trace/lots/" + designLotId).andExpect(status().isOk()))).isEmpty();

        Map<String, Object> entry = new HashMap<>();
        entry.put("plantId", plantId);
        entry.put("lotId", designLotId);
        entry.put("prodDate", today.toString());
        entry.put("shiftId", shift);
        entry.put("dryerId", dryer);
        entry.put("siloId", silo);
        entry.put("pressedCount", 100);
        entry.put("pressRejects", 0);
        entry.put("dryerRejects", 0);
        entry.put("greenWeightKg", new BigDecimal("30"));
        entry.put("pressure", 380);
        entry.put("cyclesPerMin", 5.5);
        long entryId = read(postAs(admin, "/api/production/press-entries", entry).andExpect(status().isCreated())).get("id").asLong();
        postAs(admin, "/api/production/press-entries/" + entryId + "/post", Map.of()).andExpect(status().isOk());

        JsonNode behindLot = read(getAs(admin, "/api/production/trace/lots/" + designLotId).andExpect(status().isOk()));
        assertThat(behindLot).hasSize(2);
        assertThat(behindLot.at("/1/lotId").asLong()).isEqualTo(truck2);
        assertThat(behindLot.at("/1/batches/0").asText()).startsWith("LOT-1/BB/");

        reach = read(getAs(admin, "/api/production/trace/material-lots/" + truck2).andExpect(status().isOk()));
        assertThat(reach.at("/batches/0/batchId").asLong()).isEqualTo(batch);
        assertDecimal(reach.at("/batches/0/quantity"), "4");
        assertThat(reach.at("/designLots/0/lotId").asLong()).isEqualTo(designLotId);
        assertThat(reach.at("/designLots/0/designCode").asText()).isEqualTo("TRACE-L");

        // the press entry is cancelled: the design lot no longer used that powder
        postAs(admin, "/api/production/press-entries/" + entryId + "/cancel", Map.of("reason", "Wrong lot")).andExpect(status().isOk());
        assertThat(read(getAs(admin, "/api/production/trace/lots/" + designLotId))).isEmpty();
        assertThat(read(getAs(admin, "/api/production/trace/material-lots/" + truck2)).get("designLots")).isEmpty();
    }

    @Test
    void anIssueThatIsNotAMillChargeLeadsToNoTiles() throws Exception {
        post(grn(grnLine(clay, "10", "1000")));
        post(issue(issueLine(clay, "4")));
        long lotId = lots("").at("/content/0/id").asLong();
        JsonNode reach = read(getAs(admin, "/api/production/trace/material-lots/" + lotId).andExpect(status().isOk()));
        assertThat(reach.get("batches")).isEmpty();
        assertThat(reach.get("designLots")).isEmpty();
    }

    @Test
    void theTraceNeedsProductionViewAndStaysInsideThePlant() throws Exception {
        post(grn(grnLine(clay, "10", "1000")));
        long lotId = lots("").at("/content/0/id").asLong();
        long keeper = createRole(admin, "Store only L", "STORE_VIEW");
        createUser(admin, "store.l", "password-1", keeper, plantId);
        getAs(login("store.l", "password-1"), "/api/production/trace/material-lots/" + lotId).andExpect(status().isForbidden());

        long otherPlant = createPlant(admin, companyId, "LOT-3");
        long viewer = createRole(admin, "Viewer L", "PRODUCTION_VIEW");
        createUser(admin, "viewer.l", "password-1", viewer, otherPlant);
        getAs(login("viewer.l", "password-1"), "/api/production/trace/material-lots/" + lotId).andExpect(status().isForbidden());
    }

    // ------------------------------------------------------------------ helpers

    /** Charges a mill with `quantity` of clay, discharges it and sprays all of it into the silo. */
    private long chargeAndSpray(long mill, long tank, long sprayDryer, long silo, long shift, String quantity) throws Exception {
        LocalDateTime now = LocalDateTime.now(INDIA).withNano(0);
        Map<String, Object> batch = new HashMap<>();
        batch.put("plantId", plantId);
        batch.put("prodDate", today.toString());
        batch.put("shiftId", shift);
        batch.put("ballMillId", mill);
        batch.put("chargeTon", 40);
        batch.put("millingStart", now.minusHours(12).toString());
        batch.put("lines", List.of(Map.of("materialId", clay, "locationId", yard, "quantity", new BigDecimal(quantity))));
        long batchId = read(postAs(admin, "/api/production/body-batches", batch).andExpect(status().isCreated())).get("id").asLong();
        postAs(admin, "/api/production/body-batches/" + batchId + "/charge", Map.of()).andExpect(status().isOk());
        postAs(admin, "/api/production/body-batches/" + batchId + "/discharge", Map.of(
                "millingEnd", now.minusHours(2).toString(), "slipTankId", tank,
                "slipDensity", 1700, "slipResidue", 2.5)).andExpect(status().isOk());

        Map<String, Object> run = new HashMap<>();
        run.put("plantId", plantId);
        run.put("prodDate", today.toString());
        run.put("shiftId", shift);
        run.put("sprayDryerId", sprayDryer);
        run.put("siloId", silo);
        run.put("runStart", now.minusHours(1).toString());
        run.put("powderTon", new BigDecimal("30"));
        run.put("batches", List.of(Map.of("bodyBatchId", batchId, "finished", true)));
        long runId = read(postAs(admin, "/api/production/spray-runs", run).andExpect(status().isCreated())).get("id").asLong();
        postAs(admin, "/api/production/spray-runs/" + runId + "/post", Map.of()).andExpect(status().isOk());
        return batchId;
    }

    /** The promise the whole design rests on: a godown's lots add up to its stock of that material. */
    private void assertLotsAddUpToStock(long materialId) {
        entityManager.flush();   // the test's own transaction: plain SQL only sees what has been written
        BigDecimal lots = jdbc.queryForObject(
                "select coalesce(sum(quantity_left), 0) from material_lots where location_id = ? and material_id = ?",
                BigDecimal.class, yard, materialId);
        BigDecimal stock = jdbc.queryForObject(
                "select coalesce(sum(quantity), 0) from stock_balances where location_id = ? and material_id = ?",
                BigDecimal.class, yard, materialId);
        assertThat(lots).isEqualByComparingTo(stock);
    }

    private JsonNode lots(String query) throws Exception {
        return read(getAs(admin, LOTS + "?plantId=" + plantId + query).andExpect(status().isOk()));
    }

    /** Saves and posts a document. */
    private JsonNode post(Map<String, Object> body) throws Exception {
        long id = read(postAs(admin, DOCS, body).andExpect(status().isCreated())).get("id").asLong();
        return read(postAs(admin, DOCS + "/" + id + "/post", Map.of()).andExpect(status().isOk()));
    }

    private void cancel(JsonNode doc) throws Exception {
        postAs(admin, DOCS + "/" + doc.get("id").asLong() + "/cancel", Map.of("reason", "Test")).andExpect(status().isOk());
    }

    @SafeVarargs
    private Map<String, Object> grn(Map<String, Object>... lines) {
        Map<String, Object> body = header("GRN", lines);
        body.put("supplierId", supplier);
        body.put("vehicleNo", "gj 36 t 1234");
        return body;
    }

    @SafeVarargs
    private Map<String, Object> issue(Map<String, Object>... lines) {
        Map<String, Object> body = header("ISSUE", lines);
        body.put("issuedTo", "Ball mill section");
        return body;
    }

    @SafeVarargs
    private Map<String, Object> adjustment(String reason, Map<String, Object>... lines) {
        Map<String, Object> body = header("ADJUSTMENT", lines);
        body.put("reason", reason);
        return body;
    }

    private Map<String, Object> header(String type, Map<String, Object>[] lines) {
        Map<String, Object> body = new HashMap<>();
        body.put("plantId", plantId);
        body.put("docType", type);
        body.put("docDate", LocalDate.now().toString());
        body.put("lines", new ArrayList<>(List.of(lines)));
        return body;
    }

    private Map<String, Object> grnLine(long materialId, String received, String rate) {
        Map<String, Object> line = line(materialId);
        line.put("receivedQty", new BigDecimal(received));
        line.put("rate", new BigDecimal(rate));
        return line;
    }

    private Map<String, Object> issueLine(long materialId, String quantity) {
        Map<String, Object> line = line(materialId);
        line.put("quantity", new BigDecimal(quantity));
        return line;
    }

    private Map<String, Object> adjLine(long materialId, String quantity, String rate) {
        Map<String, Object> line = issueLine(materialId, quantity);
        line.put("rate", rate == null ? null : new BigDecimal(rate));
        return line;
    }

    private Map<String, Object> line(long materialId) {
        Map<String, Object> line = new HashMap<>();
        line.put("materialId", materialId);
        line.put("locationId", yard);
        return line;
    }

    private long machine(String code, String type) throws Exception {
        return read(postAs(admin, "/api/masters/machines", Map.of("plantId", plantId, "code", code,
                "name", "Machine " + code, "type", type)).andExpect(status().isCreated())).get("id").asLong();
    }

    private long createMaterial(String code, String name) throws Exception {
        return read(postAs(admin, "/api/masters/materials", Map.of("code", code, "name", name,
                "groupId", idOf("material-groups", "BODY"), "unitId", idOf("units", "TON")))
                .andExpect(status().isCreated())).get("id").asLong();
    }

    private long idOf(String master, String code) throws Exception {
        for (JsonNode n : read(getAs(admin, "/api/masters/" + master))) {
            if (n.get("code").asText().equals(code)) return n.get("id").asLong();
        }
        throw new AssertionError(code + " not found in " + master);
    }

    private static void assertDecimal(JsonNode node, String expected) {
        assertThat(node.decimalValue()).isEqualByComparingTo(expected);
    }
}
