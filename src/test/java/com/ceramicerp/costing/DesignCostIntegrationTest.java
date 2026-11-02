package com.ceramicerp.costing;

import com.ceramicerp.IntegrationTestBase;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Step 9c: a month's costs shared out per design, each by what shows who used it. Everything happens in the month
 * before this one, so the month is whole whatever day the test runs.
 */
class DesignCostIntegrationTest extends IntegrationTestBase {

    private static final String DOCS = "/api/store/documents";

    private final YearMonth month = YearMonth.now(ZoneId.of("Asia/Kolkata")).minusMonths(1);
    private final LocalDate day = month.atDay(5);

    private String admin;
    private long plantId, shiftA, store, silo, glazeLine, sorter, pillar;
    private int presses;

    @BeforeEach
    void setUp() throws Exception {
        admin = adminToken();
        plantId = createPlant(admin, createCompany(admin, "DCCO"), "DC-1");
        shiftA = idOf("shifts", "A");
        store = read(postAs(admin, "/api/store/locations", Map.of("plantId", plantId, "code", "MAIN", "name", "Main store"))
                .andExpect(status().isCreated())).get("id").asLong();
        silo = machine("SILO-1", "SILO");
        glazeLine = machine("GL-1", "GLAZE_LINE");
        sorter = machine("SORT-1", "SORTING_LINE");
        long godown = read(postAs(admin, "/api/fg/godowns", Map.of("plantId", plantId, "code", "FG-1", "name", "FG"))
                .andExpect(status().isCreated())).get("id").asLong();
        pillar = read(postAs(admin, "/api/fg/godowns/" + godown + "/pillars",
                Map.of("prefix", "A", "from", 1, "to", 1, "capacityPallets", 200))).at("/pillars/0/id").asLong();
    }

    @Test
    void eachCostIsSharedByWhatShowsWhoUsedIt() throws Exception {
        issue("BODY", "50000");
        issue("GLAZE", "8000");
        issue("INK", "4000");
        issue("PACKING", "2000");
        issue("CHEMICAL", "400");
        monthly(4000);
        //  design   powder t  tiles  grams per tile  ink L  boxes (1.44 m²)
        run("DA",     "30",     1000,  100, 200,       "10",  300);
        run("DB",     "10",     1000,  null, 100,      "30",  100);
        run("DC",     "10",     0,     null, null,     null,  0);      // pressed, still on the line

        JsonNode d = designs();
        JsonNode a = row(d, "DA");
        assertDecimal(a.get("body"), "30000");       // 30 of 50 t of powder
        assertDecimal(a.get("glaze"), "6000");       // 3,00,000 of 4,00,000 g
        assertDecimal(a.get("ink"), "1000");         // 10 of 40 L
        assertDecimal(a.get("packing"), "1500");     // 300 of 400 boxes
        assertDecimal(a.get("otherMaterial"), "300");// 432 of 576 m²
        assertDecimal(a.get("monthly"), "3000");     // 432 of 576 m²
        assertDecimal(a.get("total"), "41800");
        assertDecimal(a.get("sqm"), "432");
        assertDecimal(a.get("perSqm"), "96.76");
        assertDecimal(a.get("perBox"), "139.33");

        // less tile packed, more ink: dearer per m² than the plant's biggest seller
        JsonNode b = row(d, "DB");
        assertDecimal(b.get("ink"), "3000");
        assertDecimal(b.get("total"), "16600");
        assertDecimal(b.get("perSqm"), "115.28");

        // pressed but not packed: it took its powder, and has no cost per m² yet
        JsonNode c = row(d, "DC");
        assertDecimal(c.get("body"), "10000");
        assertThat(c.has("perSqm")).isFalse();

        // the dearest first, what isn't packed last
        List<String> order = new ArrayList<>();
        d.get("designs").forEach(r -> order.add(r.get("code").asText()));
        assertThat(order).containsExactly("DB", "DA", "DC");

        // the designs add up to the plant, to the paisa
        assertDecimal(d.get("total"), "68400");
        BigDecimal sum = BigDecimal.ZERO;
        for (JsonNode r : d.get("designs")) sum = sum.add(r.get("total").decimalValue());
        assertThat(sum).isEqualByComparingTo("68400");
        assertDecimal(d.get("unshared"), "0");
        assertDecimal(d.get("perSqm"), "118.75");
        assertThat(d.get("sharedByArea").size()).isZero();
        assertThat(d.get("glazeEntriesWithoutUsage").asInt()).isZero();
    }

    @Test
    void whatHasNoUsageRecordedIsSharedByAreaAndSaysSo() throws Exception {
        issue("INK", "3000");
        run("DA", "10", 1000, 100, 100, null, 200);   // no ink typed
        run("DB", "10", 1000, 100, 100, null, 100);

        JsonNode d = designs();
        assertDecimal(row(d, "DA").get("ink"), "2000");
        assertDecimal(row(d, "DB").get("ink"), "1000");
        assertThat(d.get("sharedByArea").get(0).asText()).isEqualTo("Digital inks");
        assertThat(d.get("glazeEntriesWithoutUsage").asInt()).isEqualTo(2);
    }

    @Test
    void costWithNothingToShareItByStaysOnThePlant() throws Exception {
        monthly(9000);
        JsonNode d = designs();
        assertThat(d.get("designs").size()).isZero();
        assertDecimal(d.get("total"), "9000");
        assertDecimal(d.get("unshared"), "9000");
    }

    @Test
    void thePaiseAlwaysAddUp() {
        Map<Long, BigDecimal> driver = new LinkedHashMap<>();
        driver.put(1L, BigDecimal.ONE);
        driver.put(2L, BigDecimal.ONE);
        driver.put(3L, BigDecimal.ONE);
        driver.put(4L, BigDecimal.ZERO);       // used none of it: gets none, and isn't "the last one"
        Map<Long, BigDecimal> shares = DesignCostService.share(new BigDecimal("100"), driver);
        assertThat(shares.keySet()).containsExactly(1L, 2L, 3L);
        assertThat(shares.get(1L)).isEqualByComparingTo("33.33");
        assertThat(shares.get(3L)).isEqualByComparingTo("33.34");
        assertThat(DesignCostService.share(new BigDecimal("100"), Map.of(1L, BigDecimal.ZERO))).isEmpty();
    }

    @Test
    void moneyStaysWithAccounts() throws Exception {
        createUser(admin, "pm.test", "Manager@123", roleId("Production manager"), plantId);
        getAs(login("pm.test", "Manager@123"), "/api/costing/designs?plantId=" + plantId).andExpect(status().isForbidden());
        createUser(admin, "acc.test", "Accounts@123", roleId("Accounts"), plantId);
        getAs(login("acc.test", "Accounts@123"), "/api/costing/designs?plantId=" + plantId).andExpect(status().isOk());
    }

    // ------------------------------------------------------------------ helpers

    private JsonNode designs() throws Exception {
        return read(getAs(admin, "/api/costing/designs?plantId=" + plantId + "&month=" + month).andExpect(status().isOk()));
    }

    private static JsonNode row(JsonNode d, String code) {
        for (JsonNode r : d.get("designs")) {
            if (r.get("code").asText().equals(code)) return r;
        }
        throw new AssertionError("No design " + code + " in " + d.get("designs"));
    }

    private void monthly(int amount) throws Exception {
        putAs(admin, "/api/costing/monthly", Map.of("plantId", plantId, "month", month.toString(),
                "lines", List.of(Map.of("head", "ADMIN", "amount", amount)))).andExpect(status().isOk());
    }

    /** Puts one unit worth the amount in stock, and issues it: that group has cost this much this month. */
    private void issue(String group, String value) throws Exception {
        long material = read(postAs(admin, "/api/masters/materials", Map.of("code", "M-" + group, "name", "Material " + group,
                "groupId", idOf("material-groups", group), "unitId", idOf("units", "NOS"), "hsnCode", "2508", "gstRate", 5))
                .andExpect(status().isCreated())).get("id").asLong();
        post(new HashMap<>(Map.of("plantId", plantId, "docType", "ADJUSTMENT", "reason", "OPENING", "docDate", month.atDay(1).toString(),
                "lines", List.of(Map.of("materialId", material, "locationId", store, "quantity", 1, "rate", new BigDecimal(value))))));
        post(new HashMap<>(Map.of("plantId", plantId, "docType", "ISSUE", "docDate", month.atDay(2).toString(),
                "lines", List.of(Map.of("materialId", material, "locationId", store, "quantity", 1)))));
    }

    private void post(Map<String, Object> body) throws Exception {
        long id = read(postAs(admin, DOCS, body).andExpect(status().isCreated())).get("id").asLong();
        postAs(admin, DOCS + "/" + id + "/post", Map.of()).andExpect(status().isOk());
    }

    /** One design's run on its own press: pressed, and as far along the line as the figures say. */
    private void run(String code, String powderTon, int tilesGlazed, Integer engobe, Integer glaze, String ink, int boxes) throws Exception {
        long press = machine("PRESS-" + (++presses), "PRESS");
        long design = read(postAs(admin, "/api/masters/designs", Map.of("code", code, "name", "Design " + code,
                "categoryId", idOf("categories", "GVT"), "sizeId", idOf("sizes", "600X1200"),
                "finishId", idOf("finishes", "GLOSSY"), "faces", 4)).andExpect(status().isCreated())).get("id").asLong();
        long lot = read(postAs(admin, "/api/production/lots", Map.of("plantId", plantId, "designId", design, "pressId", press,
                "openedDate", day.toString(), "shiftId", shiftA, "closeCurrent", false))
                .andExpect(status().isCreated())).get("id").asLong();
        long id = read(postAs(admin, "/api/production/press-entries", Map.of("plantId", plantId, "lotId", lot, "prodDate", day.toString(),
                "shiftId", shiftA, "siloId", silo, "pressedCount", 2000, "pressRejects", 10, "dryerRejects", 10,
                "powderTon", new BigDecimal(powderTon))).andExpect(status().isCreated())).get("id").asLong();
        postAs(admin, "/api/production/press-entries/" + id + "/post", Map.of()).andExpect(status().isOk());
        if (tilesGlazed > 0) {
            Map<String, Object> entry = new HashMap<>(Map.of("plantId", plantId, "stage", "GLAZE", "lotId", lot, "prodDate", day.toString(),
                    "shiftId", shiftA, "machineId", glazeLine, "tilesIn", tilesGlazed, "rejects", 0));
            if (engobe != null) entry.put("engobeGpt", engobe);
            if (glaze != null) entry.put("glazeGpt", glaze);
            if (ink != null) entry.put("inkLitres", new BigDecimal(ink));
            id = read(postAs(admin, "/api/production/line-entries", entry).andExpect(status().isCreated())).get("id").asLong();
            postAs(admin, "/api/production/line-entries/" + id + "/post", Map.of()).andExpect(status().isOk());
        }
        if (boxes > 0) {
            id = read(postAs(admin, "/api/production/pack-entries", Map.of("plantId", plantId, "lotId", lot, "prodDate", day.toString(),
                    "shiftId", shiftA, "machineId", sorter, "broken", 0, "lines", List.of(Map.of("gradeId", idOf("grades", "PREMIUM"),
                            "shade", "01", "caliber", "C1", "boxes", boxes, "pillarId", pillar))))
                    .andExpect(status().isCreated())).get("id").asLong();
            postAs(admin, "/api/production/pack-entries/" + id + "/post", Map.of()).andExpect(status().isOk());
        }
    }

    private long machine(String code, String type) throws Exception {
        return read(postAs(admin, "/api/masters/machines", Map.of("plantId", plantId, "code", code,
                "name", "Machine " + code, "type", type)).andExpect(status().isCreated())).get("id").asLong();
    }

    private long roleId(String name) throws Exception {
        for (JsonNode r : read(getAs(admin, "/api/roles"))) {
            if (r.get("name").asText().equals(name)) return r.get("id").asLong();
        }
        throw new AssertionError("No role " + name);
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
