package com.ceramicerp.dashboard;

import com.ceramicerp.IntegrationTestBase;
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
 * Step 7: the owner's dashboard. One lot run today through every stage (600 x 1200, 2 pieces = 1.44 m² a box):
 * 2,000 pressed -> 1,950 dried -> 1,860 glazed -> 1,764 fired (1,500 SCM gas) -> 1,680 polished (900 kWh)
 * -> 790 boxes packed (700 Premium, 80 Standard, 10 Commercial = 1,580 pieces) at pillar A1 (10 pallets).
 */
class DashboardIntegrationTest extends IntegrationTestBase {

    private final LocalDate today = LocalDate.now(ZoneId.of("Asia/Kolkata"));

    private String admin;
    private long plantId, lotId, shiftA, design, premium, standard, commercial, pillar, sorter;

    @BeforeEach
    void setUp() throws Exception {
        admin = adminToken();
        plantId = createPlant(admin, createCompany(admin, "DSHCO"), "DSH-1");
        long press = createMachine("PRESS-1", "PRESS");
        long glazeLine = createMachine("GL-1", "GLAZE_LINE");
        long kiln = createMachine("KILN-1", "KILN");
        long polisher = createMachine("POL-1", "POLISHING_LINE");
        sorter = createMachine("SORT-1", "SORTING_LINE");
        long silo = createMachine("SILO-1", "SILO");
        shiftA = idOf("shifts", "A");
        premium = idOf("grades", "PREMIUM");
        standard = idOf("grades", "STANDARD");
        commercial = idOf("grades", "COMMERCIAL");
        design = read(postAs(admin, "/api/masters/designs", Map.of("code", "STATUARIO-D", "name", "Statuario Gold",
                "categoryId", idOf("categories", "GVT"), "sizeId", idOf("sizes", "600X1200"),
                "finishId", idOf("finishes", "GLOSSY"), "faces", 4)).andExpect(status().isCreated())).get("id").asLong();
        lotId = read(postAs(admin, "/api/production/lots", Map.of("plantId", plantId, "designId", design, "pressId", press,
                "openedDate", today.toString(), "shiftId", shiftA, "closeCurrent", false))
                .andExpect(status().isCreated())).get("id").asLong();

        Map<String, Object> pressEntry = new HashMap<>(Map.of("plantId", plantId, "lotId", lotId, "prodDate", today.toString(),
                "shiftId", shiftA, "siloId", silo, "pressedCount", 2000, "pressRejects", 30, "dryerRejects", 20, "powderTon", 42));
        long id = read(postAs(admin, "/api/production/press-entries", pressEntry).andExpect(status().isCreated())).get("id").asLong();
        postAs(admin, "/api/production/press-entries/" + id + "/post", Map.of()).andExpect(status().isOk());
        postLine(line("GLAZE", glazeLine, 1900, 40));
        Map<String, Object> fired = line("KILN", kiln, 1800, 36);
        fired.put("gasScm", 1500);
        postLine(fired);
        Map<String, Object> polished = line("POLISH", polisher, 1700, 20);
        polished.put("powerKwh", 900);
        postLine(polished);

        long godown = read(postAs(admin, "/api/fg/godowns", Map.of("plantId", plantId, "code", "FG-1", "name", "FG"))
                .andExpect(status().isCreated())).get("id").asLong();
        pillar = read(postAs(admin, "/api/fg/godowns/" + godown + "/pillars",
                Map.of("prefix", "A", "from", 1, "to", 2, "capacityPallets", 10))).at("/pillars/0/id").asLong();
        Map<String, Object> pack = new HashMap<>(Map.of("plantId", plantId, "lotId", lotId, "prodDate", today.toString(),
                "shiftId", shiftA, "machineId", sorter, "broken", 16, "lines", List.of(row(premium, 700), row(standard, 80),
                        row(commercial, 10))));
        long packId = read(postAs(admin, "/api/production/pack-entries", pack).andExpect(status().isCreated())).get("id").asLong();
        postAs(admin, "/api/production/pack-entries/" + packId + "/post", Map.of()).andExpect(status().isOk());
    }

    @Test
    void theDayInNumbers() throws Exception {
        JsonNode p = dashboard(admin, today, today).get("production");
        assertThat(p.get("boxesPacked").asInt()).isEqualTo(790);
        assertDecimal(p.get("sqmPacked"), "1137.60");
        assertDecimal(p.get("sqmPackedBefore"), "0");
        assertThat(p.get("topGradeName").asText()).isEqualTo("Premium");
        assertDecimal(p.get("topGradePct"), "88.6");
        assertThat(p.at("/grades/1/code").asText()).isEqualTo("STANDARD");
        assertDecimal(p.at("/grades/1/pct"), "10.1");

        // each stage's own entries: 2,000 -> 1,950 -> 1,860 -> 1,764 -> 1,680 -> 1,580 pieces
        assertThat(p.get("stages").findValuesAsText("key"))
                .containsExactly("PRESSED", "DRIED", "GLAZED", "FIRED", "POLISHED", "PACKED");
        assertThat(p.get("stages").findValues("pieces").stream().map(JsonNode::asInt).toList())
                .containsExactly(2000, 1950, 1860, 1764, 1680, 1580);
        assertDecimal(p.get("yieldPct"), "79.0");

        // 1,764 tiles x 0.72 m² = 1,270.08 m² fired on 1,500 SCM; 900 kWh over 1,137.60 m² packed
        assertDecimal(p.get("sqmFired"), "1270.08");
        assertDecimal(p.get("gasPerSqm"), "1.181");
        assertDecimal(p.get("powerPerSqm"), "0.791");
    }

    @Test
    void stockOrdersAndWhatNeedsALook() throws Exception {
        long principal = read(postAs(admin, "/api/parties", Map.of("partyType", "JOBWORK", "name", "Orient Vitrified",
                "gstRegistration", "UNREGISTERED")).andExpect(status().isCreated())).get("id").asLong();
        // one order is loaded today; a second one needs more Premium than is free
        long first = confirm(order("1", principal, 100));
        confirm(order("2", principal, 650));
        String base = "/api/sales/orders/" + first;
        putAs(admin, base + "/truck", Map.of("truckNo", "GJ36T1")).andExpect(status().isOk());
        for (JsonNode pick : read(getAs(admin, base)).get("picks")) {
            putAs(admin, base + "/picks/" + pick.get("id").asLong(), Map.of("loadedBoxes", pick.get("plannedBoxes").asInt()))
                    .andExpect(status().isOk());
        }
        postAs(admin, base + "/dispatch", Map.of()).andExpect(status().isOk());

        // a raw material below its reorder level
        Map<String, Object> clay = new HashMap<>(Map.of("code", "CLAY-D", "name", "Red clay", "groupId", idOf("material-groups", "BODY"),
                "unitId", idOf("units", "TON"), "hsnCode", "2508", "gstRate", 5, "reorderLevel", 10));
        postAs(admin, "/api/masters/materials", clay).andExpect(status().isCreated());

        JsonNode d = dashboard(admin, today.minusDays(6), today);
        JsonNode fg = d.get("finishedGoods");
        assertThat(fg.get("boxes").asInt()).isEqualTo(690);
        assertThat(fg.get("keptBoxes").asInt()).isEqualTo(600);
        assertThat(fg.at("/topDesigns/0/name").asText()).isEqualTo("Statuario Gold");
        assertThat(fg.get("palletsUsed").asInt()).isEqualTo(15);          // 600 -> 12, 80 -> 2, 10 -> 1
        assertThat(fg.get("palletsCapacity").asInt()).isEqualTo(20);      // two pillars of 10
        assertThat(fg.get("pillarsUsed").asInt()).isEqualTo(1);
        assertThat(fg.get("pillarsTotal").asInt()).isEqualTo(2);

        JsonNode dispatch = d.get("dispatch");
        assertThat(dispatch.get("ordersDispatched").asInt()).isEqualTo(1);
        assertThat(dispatch.get("boxesDispatched").asInt()).isEqualTo(100);
        assertDecimal(dispatch.get("sqmDispatched"), "144.00");
        assertThat(dispatch.get("ordersWaiting").asInt()).isEqualTo(1);
        assertThat(dispatch.get("boxesWaiting").asInt()).isEqualTo(650);
        assertThat(dispatch.get("boxesNotPlanned").asInt()).isEqualTo(50);

        // one row per day, today last
        assertThat(d.get("days").size()).isEqualTo(7);
        assertDecimal(d.at("/days/6/packedSqm"), "1137.60");
        assertDecimal(d.at("/days/6/dispatchedSqm"), "144.00");
        assertDecimal(d.at("/days/0/packedSqm"), "0");

        assertThat(d.get("store").get("lowMaterials").asInt()).isEqualTo(1);
        assertThat(kinds(d)).containsExactlyInAnyOrder("PILLARS_FULL", "SHORT_FOR_ORDERS", "LOW_MATERIALS");
        JsonNode shortRow = attention(d, "SHORT_FOR_ORDERS");
        assertThat(shortRow.get("what").asText()).isEqualTo("Statuario Gold Premium");
        assertThat(shortRow.get("boxes").asInt()).isEqualTo(50);
        assertThat(attention(d, "PILLARS_FULL").get("what").asText()).isEqualTo("A1");

        // boxes kept for the second order break in the godown: its plan needs a look
        long a1Premium = 0;
        for (JsonNode r : read(getAs(admin, "/api/fg/stock?plantId=" + plantId))) {
            if (r.get("gradeCode").asText().equals("PREMIUM")) a1Premium = r.get("id").asLong();
        }
        long breakage = read(postAs(admin, "/api/fg/documents", Map.of("plantId", plantId, "docType", "BREAKAGE",
                "docDate", today.toString(), "lines", List.of(Map.of("fromStockId", a1Premium, "boxes", 5, "reason", "HANDLING")))))
                .get("id").asLong();
        postAs(admin, "/api/fg/documents/" + breakage + "/post", Map.of()).andExpect(status().isOk());
        assertThat(attention(dashboard(admin, today, today), "PLAN_TOO_FEW").get("count").asInt()).isEqualTo(1);
    }

    @Test
    void everyoneSeesOnlyTheirOwnModules() throws Exception {
        long role = createRole(admin, "FG only", "FG_VIEW");
        createUser(admin, "fg.only", "FgOnly@123", role, plantId);
        String fgOnly = login("fg.only", "FgOnly@123");
        JsonNode d = dashboard(fgOnly, today, today);
        assertThat(d.hasNonNull("finishedGoods")).isTrue();
        assertThat(d.hasNonNull("production")).isFalse();
        assertThat(d.hasNonNull("dispatch")).isFalse();
        assertThat(d.hasNonNull("store")).isFalse();
        assertThat(d.at("/days/0").hasNonNull("packedSqm")).isFalse();

        long masters = createRole(admin, "Masters only", "MASTER_VIEW");
        createUser(admin, "masters.only", "Masters@123", masters, plantId);
        getAs(login("masters.only", "Masters@123"), url(plantId, today, today)).andExpect(status().isForbidden());

        long other = createPlant(admin, createCompany(admin, "DSHCO2"), "DSH-2");
        getAs(fgOnly, url(other, today, today)).andExpect(status().isForbidden());

        getAs(admin, url(plantId, today, today.minusDays(1)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("The period starts after it ends"));
        getAs(admin, url(plantId, today.minusDays(400), today))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Choose a period of at most a year"));
    }

    // ------------------------------------------------------------------ helpers

    private JsonNode dashboard(String token, LocalDate from, LocalDate to) throws Exception {
        return read(getAs(token, url(plantId, from, to)).andExpect(status().isOk()));
    }

    private static String url(long plant, LocalDate from, LocalDate to) {
        return "/api/dashboard?plantId=" + plant + "&from=" + from + "&to=" + to;
    }

    private static List<String> kinds(JsonNode d) {
        List<String> kinds = new ArrayList<>();
        d.get("attention").forEach(a -> kinds.add(a.get("kind").asText()));
        return kinds;
    }

    private static JsonNode attention(JsonNode d, String kind) {
        for (JsonNode a : d.get("attention")) {
            if (a.get("kind").asText().equals(kind)) return a;
        }
        throw new AssertionError("No " + kind + " in " + d.get("attention"));
    }

    private Map<String, Object> order(String slip, long principal, int boxes) {
        Map<String, Object> line = Map.of("description", "STATUARIO GOLD-P", "designId", design, "gradeId", premium, "boxes", boxes);
        return new HashMap<>(Map.of("plantId", plantId, "slipNo", slip, "slipDate", today.toString(), "orderType", "JOBWORK",
                "partyId", principal, "lines", List.of(line)));
    }

    private long confirm(Map<String, Object> body) throws Exception {
        long id = read(postAs(admin, "/api/sales/orders", body).andExpect(status().isCreated())).get("id").asLong();
        postAs(admin, "/api/sales/orders/" + id + "/confirm", Map.of()).andExpect(status().isOk());
        return id;
    }

    private Map<String, Object> line(String stage, long machineId, int tilesIn, int rejects) {
        return new HashMap<>(Map.of("plantId", plantId, "stage", stage, "lotId", lotId, "prodDate", today.toString(),
                "shiftId", shiftA, "machineId", machineId, "tilesIn", tilesIn, "rejects", rejects));
    }

    private void postLine(Map<String, Object> body) throws Exception {
        long id = read(postAs(admin, "/api/production/line-entries", body).andExpect(status().isCreated())).get("id").asLong();
        postAs(admin, "/api/production/line-entries/" + id + "/post", Map.of()).andExpect(status().isOk());
    }

    private Map<String, Object> row(long gradeId, int boxes) {
        return Map.of("gradeId", gradeId, "shade", "01", "caliber", "C1", "boxes", boxes, "pillarId", pillar);
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
