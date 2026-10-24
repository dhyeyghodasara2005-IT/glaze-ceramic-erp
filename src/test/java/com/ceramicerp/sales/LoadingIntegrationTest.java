package com.ceramicerp.sales;

import com.ceramicerp.IntegrationTestBase;
import com.ceramicerp.numbering.DocumentNumberService;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Step 6c: the loading plan, the boxes it keeps, the loading checklist and dispatch.
 * <p>
 * The godown: row A1..A6 with the loading point next to A6 (walk order A6 = 1 ... A1 = 6). Flavour Ivory Premium,
 * packed in this order: A6 200, A2 120, A1 60, A3 40 - and then 30 Onyx in front of the A3 boxes.
 */
class LoadingIntegrationTest extends IntegrationTestBase {

    private static final String ORDERS = "/api/sales/orders";

    private final LocalDate today = LocalDate.now(ZoneId.of("Asia/Kolkata"));
    private final String fy = DocumentNumberService.financialYear(today);

    private String admin;
    private long plantId, premium, flavour, onyx, principal;
    private final Map<String, Long> pillars = new HashMap<>();
    private int slips;

    @BeforeEach
    void setUp() throws Exception {
        admin = adminToken();
        plantId = createPlant(admin, createCompany(admin, "LDCO"), "LD-1");
        premium = idOf("grades", "PREMIUM");
        long size = idOf("sizes", "600X1200");
        flavour = design("FLV-LD", "Flavour Ivory", size);
        onyx = design("ONYX-LD", "Onyx Grey", size);
        principal = read(postAs(admin, "/api/parties", Map.of("partyType", "JOBWORK", "name", "Orient Vitrified",
                "gstRegistration", "UNREGISTERED")).andExpect(status().isCreated())).get("id").asLong();

        long godown = read(postAs(admin, "/api/fg/godowns", Map.of("plantId", plantId, "code", "FG-1", "name", "FG"))
                .andExpect(status().isCreated())).get("id").asLong();
        read(postAs(admin, "/api/fg/godowns/" + godown + "/pillars", Map.of("prefix", "A", "from", 1, "to", 6)))
                .get("pillars").forEach(p -> pillars.put(p.get("code").asText(), p.get("id").asLong()));
        putAs(admin, "/api/fg/godowns/" + godown + "/layout", Map.of("prefix", "A", "from", 1, "to", 6,
                "firstWalkOrder", 6, "step", -1)).andExpect(status().isOk());

        // one document each, so every stock row has its own arrival time
        opening("A6", flavour, 200);
        opening("A2", flavour, 120);
        opening("A1", flavour, 60);
        opening("A3", flavour, 40);
        opening("A3", onyx, 30);          // newer pallets in front of the A3 Flavour boxes
    }

    @Test
    void confirmingPicksTheBoxesByTheRulesInWalkOrder() throws Exception {
        JsonNode order = confirm(order(100));
        // A1 empties its pillar (better than the older A6); then the oldest, A6. A3 is behind Onyx: left alone.
        assertThat(pick(order, "A1").get("plannedBoxes").asInt()).isEqualTo(60);
        assertThat(pick(order, "A1").get("reason").asText()).isEqualTo("CLEARS_PILLAR");
        assertThat(pick(order, "A6").get("plannedBoxes").asInt()).isEqualTo(40);
        assertThat(pick(order, "A6").get("reason").asText()).isEqualTo("OLDEST");
        assertThat(order.get("picks").size()).isEqualTo(2);
        // the forklift's order: nearest the loading point first
        assertThat(order.at("/picks/0/pillarCode").asText()).isEqualTo("A6");
        assertThat(order.at("/picks/0/boxesPerPallet").asInt()).isEqualTo(50);
        assertThat(order.at("/lines/0/planned").asInt()).isEqualTo(100);
        assertThat(order.at("/totals/plannedBoxes").asInt()).isEqualTo(100);
        assertThat(order.at("/totals/picksToTick").asInt()).isEqualTo(2);
        assertThat(order.hasNonNull("plannedAt")).isTrue();
    }

    @Test
    void keptBoxesAreNotPlannedTwice() throws Exception {
        long first = confirm(order(100)).get("id").asLong();     // keeps A1 60 + A6 40

        // 250 more: A2 empties its pillar; then A6's free boxes (in front) before the buried A3 boxes
        JsonNode second = confirm(order(250));
        assertThat(pick(second, "A2").get("plannedBoxes").asInt()).isEqualTo(120);
        assertThat(pick(second, "A2").get("reason").asText()).isEqualTo("CLEARS_PILLAR");
        assertThat(pick(second, "A6").get("plannedBoxes").asInt()).isEqualTo(130);
        assertThat(pick(second, "A6").get("reason").asText()).isEqualTo("IN_FRONT");
        assertThat(second.at("/lines/0/inStock").asInt()).isEqualTo(420);
        assertThat(second.at("/lines/0/keptElsewhere").asInt()).isEqualTo(100);
        assertThat(second.at("/lines/0/shortBy").asInt()).isZero();

        // only 70 are free now (A6 30, A3 40): a third order gets those and shows the rest as short
        JsonNode third = confirm(order(100));
        assertThat(third.at("/lines/0/planned").asInt()).isEqualTo(70);
        assertThat(third.at("/lines/0/shortBy").asInt()).isEqualTo(30);
        assertThat(pick(third, "A3").get("reason").asText()).isEqualTo("ONLY_STOCK");
        long thirdId = third.get("id").asLong();

        // a draft sees what the confirmed orders keep
        long draft = create(order(60));
        assertThat(read(getAs(admin, ORDERS + "/" + draft)).at("/lines/0/shortBy").asInt()).isEqualTo(60);

        // more boxes packed, then plan again; cancelling the first order frees its boxes too
        opening("A4", flavour, 20);
        assertThat(read(postAs(admin, ORDERS + "/" + thirdId + "/plan", Map.of()).andExpect(status().isOk()))
                .at("/lines/0/planned").asInt()).isEqualTo(90);
        postAs(admin, ORDERS + "/" + first + "/cancel", Map.of("reason", "Principal cancelled")).andExpect(status().isOk());
        assertThat(read(postAs(admin, ORDERS + "/" + thirdId + "/plan", Map.of())).at("/lines/0/planned").asInt())
                .isEqualTo(100);
    }

    @Test
    void theLoaderTicksChangesAndConfirmsThenTheBoxesLeave() throws Exception {
        JsonNode order = confirm(order(100));
        long id = order.get("id").asLong();
        String base = ORDERS + "/" + id;
        long a6 = pick(order, "A6").get("id").asLong();
        long a1 = pick(order, "A1").get("id").asLong();
        long line = order.at("/lines/0/id").asLong();

        postAs(admin, base + "/dispatch", Map.of())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Enter the truck no. first"));
        JsonNode truck = read(putAs(admin, base + "/truck", Map.of("truckNo", "gj-36 t 4521", "driverName", "Ramesh",
                "driverMobile", "9876543210")).andExpect(status().isOk()));
        assertThat(truck.get("truckNo").asText()).isEqualTo("GJ36T4521");
        postAs(admin, base + "/dispatch", Map.of())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Tick every row of the loading plan first: 2 not ticked yet"));

        // "Loaded ✓" at A6; at A1 two boxes were broken: 58 loaded
        putAs(admin, base + "/picks/" + a6, Map.of("loadedBoxes", 40)).andExpect(status().isOk());
        JsonNode changed = read(putAs(admin, base + "/picks/" + a1, Map.of("loadedBoxes", 58)).andExpect(status().isOk()));
        assertThat(changed.at("/lines/0/loaded").asInt()).isEqualTo(98);
        assertThat(changed.at("/totals/picksToTick").asInt()).isZero();
        putAs(admin, base + "/picks/" + a6, Map.of("loadedBoxes", 45))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Row 1 ordered 100 boxes: this would make 103"));

        // the two missing boxes come from another pillar, nearest first
        JsonNode options = read(getAs(admin, base + "/lines/" + line + "/stock").andExpect(status().isOk()));
        assertThat(options.findValuesAsText("pillarCode")).containsExactly("A3", "A2");
        JsonNode added = read(postAs(admin, base + "/lines/" + line + "/picks",
                Map.of("stockId", stockAt("A2", flavour), "boxes", 2)).andExpect(status().isOk()));
        assertThat(pick(added, "A2").get("reason").asText()).isEqualTo("ADDED");
        assertThat(pick(added, "A2").get("plannedBoxes").asInt()).isZero();
        assertThat(added.at("/lines/0/loaded").asInt()).isEqualTo(100);

        JsonNode dispatched = read(postAs(admin, base + "/dispatch", Map.of()).andExpect(status().isOk()));
        assertThat(dispatched.get("status").asText()).isEqualTo("DISPATCHED");
        assertThat(dispatched.get("dispatchNo").asText()).isEqualTo("LD-1/DP/" + fy + "/0001");
        assertThat(dispatched.get("dispatchedBy").asText()).isEqualTo(adminUsername);
        assertThat(boxesAt("A6", flavour)).isEqualTo(160);
        assertThat(boxesAt("A1", flavour)).isEqualTo(2);
        assertThat(boxesAt("A2", flavour)).isEqualTo(118);
        JsonNode ledger = read(getAs(admin, "/api/fg/stock/" + stockAt("A1", flavour)));
        JsonNode last = ledger.get("moves").get(ledger.get("moves").size() - 1);
        assertThat(last.get("source").asText()).isEqualTo("DISPATCH");
        assertThat(last.get("boxes").asInt()).isEqualTo(-58);

        // dispatched: nothing more to tick, plan, reopen or cancel
        putAs(admin, base + "/picks/" + a6, Map.of("loadedBoxes", 40)).andExpect(status().isConflict());
        postAs(admin, base + "/reopen", Map.of()).andExpect(status().isConflict());
        postAs(admin, base + "/cancel", Map.of("reason", "x")).andExpect(status().isConflict());

        // the truck was unloaded again: the boxes go back; confirming again keeps the same number
        JsonNode undone = read(postAs(admin, base + "/undo-dispatch", Map.of("reason", "Wrong truck")).andExpect(status().isOk()));
        assertThat(undone.get("status").asText()).isEqualTo("CONFIRMED");
        assertThat(undone.get("undoReason").asText()).isEqualTo("Wrong truck");
        assertThat(boxesAt("A1", flavour)).isEqualTo(60);
        assertThat(read(postAs(admin, base + "/dispatch", Map.of())).get("dispatchNo").asText())
                .isEqualTo("LD-1/DP/" + fy + "/0001");
    }

    @Test
    void oncePalletsAreTickedThePlanStays() throws Exception {
        JsonNode order = confirm(order(100));
        String base = ORDERS + "/" + order.get("id").asLong();
        putAs(admin, base + "/picks/" + pick(order, "A6").get("id").asLong(), Map.of("loadedBoxes", 40))
                .andExpect(status().isOk());
        String started = "Loading has started for slip " + order.get("slipNo").asText() + ". Untick the loaded boxes first.";
        postAs(admin, base + "/plan", Map.of()).andExpect(status().isConflict()).andExpect(jsonPath("$.message").value(started));
        postAs(admin, base + "/reopen", Map.of()).andExpect(status().isConflict()).andExpect(jsonPath("$.message").value(started));

        // untick: reopening is possible again, and the boxes are free for others
        putAs(admin, base + "/picks/" + pick(order, "A6").get("id").asLong(), new HashMap<>(Map.of()))
                .andExpect(status().isOk());
        JsonNode draft = read(postAs(admin, base + "/reopen", Map.of()).andExpect(status().isOk()));
        assertThat(draft.get("picks").size()).isZero();
        assertThat(pick(confirm(order(400)), "A1").get("plannedBoxes").asInt()).isEqualTo(60);
    }

    @Test
    void boxesBrokenAfterPlanningAreFlaggedAndCantLeave() throws Exception {
        JsonNode order = confirm(order(100));
        String base = ORDERS + "/" + order.get("id").asLong();
        long fgDoc = read(postAs(admin, "/api/fg/documents", Map.of("plantId", plantId, "docType", "BREAKAGE",
                "docDate", today.toString(), "lines", List.of(Map.of("fromStockId", stockAt("A1", flavour), "boxes", 10,
                        "reason", "HANDLING")))).andExpect(status().isCreated())).get("id").asLong();
        postAs(admin, "/api/fg/documents/" + fgDoc + "/post", Map.of()).andExpect(status().isOk());

        JsonNode now = read(getAs(admin, base));
        assertThat(pick(now, "A1").get("tooFew").asBoolean()).isTrue();
        assertThat(pick(now, "A6").get("tooFew").asBoolean()).isFalse();

        putAs(admin, base + "/truck", Map.of("truckNo", "GJ36T1")).andExpect(status().isOk());
        for (JsonNode p : now.get("picks")) {
            putAs(admin, base + "/picks/" + p.get("id").asLong(), Map.of("loadedBoxes", p.get("plannedBoxes").asInt()))
                    .andExpect(status().isOk());
        }
        postAs(admin, base + "/dispatch", Map.of())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Not enough Flavour Ivory Premium shade 01 caliber C1 (B-1) at pillar A1: "
                        + "50 boxes there, 60 needed"));
        assertThat(boxesAt("A6", flavour)).isEqualTo(200);      // nothing left: all or nothing
    }

    @Test
    void whoMayLoad() throws Exception {
        long loaderRole = createRole(admin, "Loader test", "SALES_VIEW", "LOADING_ENTRY");
        createUser(admin, "loader.test", "Loader@123", loaderRole, plantId);
        String loader = login("loader.test", "Loader@123");
        long officeRole = createRole(admin, "Office test", "SALES_VIEW", "SALES_ENTRY");
        createUser(admin, "office.test", "Office@123", officeRole, plantId);
        String office = login("office.test", "Office@123");

        JsonNode order = confirm(order(100));
        String base = ORDERS + "/" + order.get("id").asLong();
        long a6 = pick(order, "A6").get("id").asLong();

        postAs(loader, ORDERS, order(5)).andExpect(status().isForbidden());
        postAs(loader, base + "/plan", Map.of()).andExpect(status().isForbidden());
        putAs(office, base + "/picks/" + a6, Map.of("loadedBoxes", 40)).andExpect(status().isForbidden());
        putAs(office, base + "/truck", Map.of("truckNo", "GJ1")).andExpect(status().isForbidden());

        putAs(loader, base + "/truck", Map.of("truckNo", "GJ36T4521")).andExpect(status().isOk());
        for (JsonNode p : order.get("picks")) {
            putAs(loader, base + "/picks/" + p.get("id").asLong(), Map.of("loadedBoxes", p.get("plannedBoxes").asInt()))
                    .andExpect(status().isOk());
        }
        postAs(loader, base + "/dispatch", Map.of()).andExpect(status().isOk());
        postAs(loader, base + "/undo-dispatch", Map.of("reason", "x")).andExpect(status().isForbidden());
    }

    // ------------------------------------------------------------------ helpers

    /** A new jobwork slip for Flavour Ivory Premium. */
    private Map<String, Object> order(int boxes) {
        Map<String, Object> line = new HashMap<>();
        line.put("description", "O7VM 600X1200-2 FLAVOUR IVORY PDR-P");
        line.put("designId", flavour);
        line.put("gradeId", premium);
        line.put("boxes", boxes);
        Map<String, Object> body = new HashMap<>();
        body.put("plantId", plantId);
        body.put("slipNo", String.valueOf(460100 + ++slips));
        body.put("slipDate", today.toString());
        body.put("orderType", "JOBWORK");
        body.put("partyId", principal);
        body.put("lines", new ArrayList<>(List.of(line)));
        return body;
    }

    private long create(Map<String, Object> body) throws Exception {
        return read(postAs(admin, ORDERS, body).andExpect(status().isCreated())).get("id").asLong();
    }

    private JsonNode confirm(Map<String, Object> body) throws Exception {
        return read(postAs(admin, ORDERS + "/" + create(body) + "/confirm", Map.of()).andExpect(status().isOk()));
    }

    private static JsonNode pick(JsonNode order, String pillar) {
        for (JsonNode p : order.get("picks")) {
            if (p.get("pillarCode").asText().equals(pillar)) return p;
        }
        throw new AssertionError("No pick at " + pillar + " in " + order.get("picks"));
    }

    private void opening(String pillar, long designId, int boxes) throws Exception {
        Map<String, Object> row = Map.of("pillarId", pillars.get(pillar), "designId", designId, "batchCode", "B-1",
                "gradeId", premium, "shade", "01", "caliber", "C1", "boxes", boxes);
        long doc = read(postAs(admin, "/api/fg/documents", Map.of("plantId", plantId, "docType", "OPENING",
                "docDate", today.toString(), "lines", List.of(row))).andExpect(status().isCreated())).get("id").asLong();
        postAs(admin, "/api/fg/documents/" + doc + "/post", Map.of()).andExpect(status().isOk());
    }

    private JsonNode stockRow(String pillar, long designId) throws Exception {
        for (JsonNode r : read(getAs(admin, "/api/fg/stock?plantId=" + plantId))) {
            if (r.get("pillarCode").asText().equals(pillar) && r.get("designId").asLong() == designId) return r;
        }
        return null;
    }

    private long stockAt(String pillar, long designId) throws Exception {
        return stockRow(pillar, designId).get("id").asLong();
    }

    private int boxesAt(String pillar, long designId) throws Exception {
        JsonNode row = stockRow(pillar, designId);
        return row == null ? 0 : row.get("boxes").asInt();
    }

    private long design(String code, String name, long sizeId) throws Exception {
        return read(postAs(admin, "/api/masters/designs", Map.of("code", code, "name", name,
                "categoryId", idOf("categories", "GVT"), "sizeId", sizeId, "finishId", idOf("finishes", "GLOSSY"),
                "faces", 1)).andExpect(status().isCreated())).get("id").asLong();
    }

    private long idOf(String master, String code) throws Exception {
        for (JsonNode n : read(getAs(admin, "/api/masters/" + master))) {
            if (n.get("code").asText().equals(code)) return n.get("id").asLong();
        }
        throw new AssertionError(code + " not found in " + master);
    }
}
