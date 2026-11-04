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
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Step 9b: what a m² cost in a month, added up from what the other modules record. Everything happens in the month
 * before this one, so the month is whole whatever day the test runs.
 */
class CostingIntegrationTest extends IntegrationTestBase {

    private static final String SHEET = "/api/costing/sheet";
    private static final String RATES = "/api/costing/rates";
    private static final String MONTHLY = "/api/costing/monthly";
    private static final String DOCS = "/api/store/documents";

    private final YearMonth month = YearMonth.now(ZoneId.of("Asia/Kolkata")).minusMonths(1);

    private String admin;
    private long plantId, kiln, shiftA, store;

    @BeforeEach
    void setUp() throws Exception {
        admin = adminToken();
        plantId = createPlant(admin, createCompany(admin, "CSCO"), "CS-1");
        kiln = machine("KILN-1", "KILN");
        shiftA = idOf("shifts", "A");
        store = read(postAs(admin, "/api/store/locations", Map.of("plantId", plantId, "code", "MAIN", "name", "Main store"))
                .andExpect(status().isCreated())).get("id").asLong();
    }

    @Test
    void aMonthAddsUpFromEveryModule() throws Exception {
        // material: only what left the store, valued at average cost
        long clay = material("CLAY-1", "Red clay", "BODY", "TON");
        long box = material("BOX-1", "Carton", "PACKING", "NOS");
        long coal = material("COAL-1", "Coal", "FUEL", "TON");
        post(document("ADJUSTMENT", month.atDay(1), Map.of("reason", "OPENING"),
                stockLine(clay, "100", "1000"), stockLine(box, "1000", "20"), stockLine(coal, "10", "9000")));
        post(document("ISSUE", month.atDay(2), Map.of(), issueLine(clay, "10"), issueLine(box, "100"), issueLine(coal, "1")));
        // an issue cancelled later doesn't count
        long cancelled = post(document("ISSUE", month.atDay(2), Map.of(), issueLine(clay, "5")));
        postAs(admin, DOCS + "/" + cancelled + "/cancel", Map.of("reason", "wrong material")).andExpect(status().isOk());

        // energy: gas from the main meter only, the rate changes on the 3rd
        long gasMain = meter("GG-MAIN", "GAS", "SCM", true, null, "DAY");
        long kilnGas = meter("KILN-GAS", "GAS", "M3", false, kiln, "SHIFT");
        long pressPower = meter("PRESS-PWR", "POWER", "KWH", false, null, "SHIFT");
        long coalUsed = meter("SD-COAL", "COAL", "KG", false, null, "SHIFT");
        rate("GAS", "SCM", month.atDay(1), "50");
        rate("GAS", "SCM", month.atDay(3), "60");
        rate("POWER", "KWH", month.atDay(1), "8");
        round(month.atDay(1), null, reading(gasMain, "1000"));
        round(month.atDay(2), null, reading(gasMain, "1100"));     // 100 SCM x 50
        round(month.atDay(3), null, reading(gasMain, "1200"));     // 100 SCM x 60
        round(month.atDay(1), shiftA, reading(kilnGas, "10"), reading(pressPower, "5000"));
        round(month.atDay(2), shiftA, reading(kilnGas, "90"), reading(pressPower, "5500"), Map.of("meterId", coalUsed, "used", new BigDecimal("500")));

        // labour: 1 day x 500 + 2 h x 70 through a contractor; one worker on the rolls with no rate
        long contractor = read(postAs(admin, "/api/parties", Map.of("partyType", "CONTRACTOR", "name", "Shree Labour",
                "gstRegistration", "UNREGISTERED")).andExpect(status().isCreated())).get("id").asLong();
        long ramesh = worker("Ramesh", contractor, "500", "70");
        long peon = worker("Peon", null, null, null);
        postAs(admin, "/api/labour/attendance", Map.of("plantId", plantId, "date", month.atDay(2).toString(), "lines", List.of(
                Map.of("workerId", ramesh, "status", "PRESENT", "otHours", 2), Map.of("workerId", peon, "status", "PRESENT"))))
                .andExpect(status().isOk());

        // monthly costs
        putAs(admin, MONTHLY, Map.of("plantId", plantId, "month", month.toString(),
                "lines", List.of(Map.of("head", "STAFF_SALARY", "amount", 100000)))).andExpect(status().isOk());

        // 790 boxes x 1.44 m²
        produce(month.atDay(5));

        JsonNode s = sheet();
        assertThat(s.get("running").asBoolean()).isFalse();
        assertDecimal(s.get("sqmPacked"), "1137.60");
        assertThat(s.get("boxesPacked").asInt()).isEqualTo(790);

        assertDecimal(line(s, "BODY").get("amount"), "10000");
        assertThat(line(s, "BODY").get("name").asText()).startsWith("Body raw materials");
        assertDecimal(line(s, "PACKING").get("amount"), "2000");
        // the coal issued is shown, not added: the meter counts the coal used
        assertDecimal(s.get("fuelInStore"), "9000");
        assertThat(codes(s)).doesNotContain("FUEL");

        JsonNode gas = line(s, "GAS");
        assertDecimal(gas.get("quantity"), "200");
        assertDecimal(gas.get("amount"), "11000");
        assertDecimal(gas.get("rate"), "55");
        assertThat(gas.get("unit").asText()).isEqualTo("SCM");
        assertThat(gas.has("fromSections")).isFalse();
        // the main meter was read on 3 days of the month
        assertThat(gas.get("missingReadings").asInt()).isEqualTo(month.lengthOfMonth() - 3);

        // no main power meter: the section meter stands in, and says so
        JsonNode power = line(s, "POWER");
        assertDecimal(power.get("amount"), "4000");
        assertThat(power.get("fromSections").asBoolean()).isTrue();

        // coal without a rate: used, not priced, and not ₹ 0 silently
        JsonNode coalLine = line(s, "COAL");
        assertDecimal(coalLine.get("unpricedQuantity"), "500");
        assertDecimal(coalLine.get("amount"), "0");

        assertDecimal(line(s, "CONTRACT").get("amount"), "640");
        assertDecimal(line(s, "CONTRACT").get("quantity"), "1");
        assertThat(line(s, "OWN_ROLLS").get("unpricedWorkers").asInt()).isEqualTo(1);
        assertDecimal(line(s, "STAFF_SALARY").get("amount"), "100000");

        // 10,000 + 2,000 + 11,000 + 4,000 + 640 + 1,00,000
        assertDecimal(s.get("total"), "127640");
        assertDecimal(s.get("perSqm"), "112.20");
        assertDecimal(s.get("perBox"), "161.57");
        assertDecimal(line(s, "STAFF_SALARY").get("sharePct"), "78.3");
        assertDecimal(line(s, "BODY").get("perSqm"), "8.79");
    }

    @Test
    void aNewRateDoesntRewriteTheDaysBeforeIt() throws Exception {
        long power = meter("GEB", "POWER", "KWH", true, null, "DAY");
        round(month.atDay(1), null, reading(power, "1000"));
        round(month.atDay(2), null, reading(power, "1100"));
        round(month.atDay(3), null, reading(power, "1200"));

        // the first use is before any rate
        rate("POWER", "KWH", month.atDay(3), "10");
        JsonNode line = line(sheet(), "POWER");
        assertDecimal(line.get("amount"), "1000");
        assertDecimal(line.get("unpricedQuantity"), "100");

        long first = rate("POWER", "KWH", month.atDay(1), "8");
        line = line(sheet(), "POWER");
        assertDecimal(line.get("amount"), "1800");
        assertThat(line.has("unpricedQuantity")).isFalse();

        // corrected, then taken away
        putAs(admin, RATES + "/" + first, rateBody("POWER", "KWH", month.atDay(1), "9")).andExpect(status().isOk());
        assertDecimal(line(sheet(), "POWER").get("amount"), "1900");
        deleteAs(admin, RATES + "/" + first).andExpect(status().isNoContent());
        assertDecimal(line(sheet(), "POWER").get("amount"), "1000");

        // listed gas, power, then the fuels, not A to Z; within a fuel the newest first
        rate("COAL", "KG", month.atDay(1), "9");
        rate("POWER", "KWH", month.atDay(2), "9");
        JsonNode list = read(getAs(admin, RATES + "?plantId=" + plantId));
        assertThat(List.of(list.get(0).get("kind").asText(), list.get(1).get("kind").asText(), list.get(2).get("kind").asText()))
                .containsExactly("POWER", "POWER", "COAL");
        assertThat(list.get(0).get("validFrom").asText()).isEqualTo(month.atDay(3).toString());

        // mistakes
        postAs(admin, RATES, rateBody("POWER", "KWH", month.atDay(3), "11")).andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.startsWith("There is already a power rate from ")));
        postAs(admin, RATES, rateBody("RUN_HOURS", "HOUR", month.atDay(1), "5")).andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Running hours have no rate of their own: give the diesel a rate"));
        postAs(admin, RATES, rateBody("COAL", "KWH", month.atDay(1), "5")).andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("A coal rate is per KG"));
    }

    @Test
    void monthlyCostsChangeOnlyTheHeadsSent() throws Exception {
        YearMonth before = month.minusMonths(1);
        putAs(admin, MONTHLY, Map.of("plantId", plantId, "month", before.toString(),
                "lines", List.of(Map.of("head", "INTEREST", "amount", 25000)))).andExpect(status().isOk());
        putAs(admin, MONTHLY, Map.of("plantId", plantId, "month", month.toString(), "lines", List.of(
                Map.of("head", "STAFF_SALARY", "amount", 90000), Map.of("head", "ADMIN", "amount", 5000, "note", "phones"))))
                .andExpect(status().isOk());
        JsonNode rows = read(putAs(admin, MONTHLY, Map.of("plantId", plantId, "month", month.toString(),
                "lines", List.of(Map.of("head", "STAFF_SALARY", "amount", 95000)))).andExpect(status().isOk()));
        assertDecimal(head(rows, "STAFF_SALARY").get("amount"), "95000");
        assertDecimal(head(rows, "ADMIN").get("amount"), "5000");
        assertThat(head(rows, "ADMIN").get("note").asText()).isEqualTo("phones");
        assertDecimal(head(rows, "INTEREST").get("lastMonth"), "25000");     // to copy when it stays the same
        assertThat(head(rows, "INTEREST").has("amount")).isFalse();

        // a head sent with no amount has no cost any more
        Map<String, Object> clear = new HashMap<>();
        clear.put("head", "ADMIN");
        clear.put("amount", null);
        rows = read(putAs(admin, MONTHLY, Map.of("plantId", plantId, "month", month.toString(), "lines", List.of(clear))).andExpect(status().isOk()));
        assertThat(head(rows, "ADMIN").has("amount")).isFalse();
        assertDecimal(sheet().get("total"), "95000");
        // nothing packed: a total, but nothing to divide it by
        assertThat(sheet().has("perSqm")).isFalse();

        putAs(admin, MONTHLY, Map.of("plantId", plantId, "month", YearMonth.now().plusMonths(2).toString(),
                "lines", List.of(Map.of("head", "ADMIN", "amount", 1)))).andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("That month hasn't started"));
        putAs(admin, MONTHLY, Map.of("plantId", plantId, "month", month.toString(), "lines", List.of(
                Map.of("head", "ADMIN", "amount", 1), Map.of("head", "ADMIN", "amount", 2)))).andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("A cost head is in the list twice"));
    }

    @Test
    void theTrendShowsTheLatestMonthsOldestFirst() throws Exception {
        putAs(admin, MONTHLY, Map.of("plantId", plantId, "month", month.toString(),
                "lines", List.of(Map.of("head", "STAFF_SALARY", "amount", 113760)))).andExpect(status().isOk());
        produce(month.atDay(5));
        JsonNode trend = read(getAs(admin, "/api/costing/trend?plantId=" + plantId + "&months=3").andExpect(status().isOk()));
        assertThat(trend.size()).isEqualTo(3);
        assertThat(trend.get(2).get("month").asText()).isEqualTo(month.plusMonths(1).toString());
        assertThat(trend.get(1).get("month").asText()).isEqualTo(month.toString());
        assertDecimal(trend.get(1).get("perSqm"), "100");
        assertThat(trend.get(0).has("perSqm")).isFalse();

        getAs(admin, SHEET + "?plantId=" + plantId + "&month=" + YearMonth.now().plusMonths(2)).andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("That month hasn't started"));
    }

    @Test
    void moneyStaysWithAccounts() throws Exception {
        createUser(admin, "acc.test", "Accounts@123", roleId("Accounts"), plantId);
        String accounts = login("acc.test", "Accounts@123");
        postAs(accounts, RATES, rateBody("GAS", "SCM", month.atDay(1), "50")).andExpect(status().isCreated());
        getAs(accounts, SHEET + "?plantId=" + plantId).andExpect(status().isOk());

        createUser(admin, "pm.test", "Manager@123", roleId("Production manager"), plantId);
        String manager = login("pm.test", "Manager@123");
        getAs(manager, SHEET + "?plantId=" + plantId).andExpect(status().isForbidden());
        getAs(manager, RATES + "?plantId=" + plantId).andExpect(status().isForbidden());

        long viewRole = createRole(admin, "Cost viewer test", "COST_VIEW");
        createUser(admin, "cost.view", "Viewer@123", viewRole, plantId);
        String viewer = login("cost.view", "Viewer@123");
        getAs(viewer, SHEET + "?plantId=" + plantId).andExpect(status().isOk());
        postAs(viewer, RATES, rateBody("GAS", "SCM", month.atDay(2), "50")).andExpect(status().isForbidden());

        long otherPlant = createPlant(admin, createCompany(admin, "CSCO2"), "CS-2");
        createUser(admin, "other.acc", "Accounts@123", roleId("Accounts"), otherPlant);
        String other = login("other.acc", "Accounts@123");
        getAs(other, SHEET + "?plantId=" + plantId).andExpect(status().isForbidden());
    }

    // ------------------------------------------------------------------ helpers

    private JsonNode sheet() throws Exception {
        return read(getAs(admin, SHEET + "?plantId=" + plantId + "&month=" + month).andExpect(status().isOk()));
    }

    private static JsonNode line(JsonNode sheet, String code) {
        for (JsonNode l : sheet.get("lines")) {
            if (l.get("code").asText().equals(code)) return l;
        }
        throw new AssertionError("No line " + code + " in " + sheet.get("lines"));
    }

    private static List<String> codes(JsonNode sheet) {
        List<String> out = new ArrayList<>();
        sheet.get("lines").forEach(l -> out.add(l.get("code").asText()));
        return out;
    }

    private static JsonNode head(JsonNode rows, String head) {
        for (JsonNode r : rows) {
            if (r.get("head").asText().equals(head)) return r;
        }
        throw new AssertionError("No head " + head);
    }

    private Map<String, Object> rateBody(String kind, String unit, LocalDate from, String rate) {
        return Map.of("plantId", plantId, "kind", kind, "unit", unit, "validFrom", from.toString(), "rate", new BigDecimal(rate));
    }

    private long rate(String kind, String unit, LocalDate from, String rate) throws Exception {
        return read(postAs(admin, RATES, rateBody(kind, unit, from, rate)).andExpect(status().isCreated())).get("id").asLong();
    }

    private long meter(String code, String kind, String unit, boolean main, Long machineId, String every) throws Exception {
        Map<String, Object> body = new HashMap<>(Map.of("plantId", plantId, "code", code, "name", "Meter " + code, "kind", kind,
                "unit", unit, "main", main, "readEvery", every));
        if (machineId != null) body.put("machineId", machineId);
        return read(postAs(admin, "/api/energy/meters", body).andExpect(status().isCreated())).get("id").asLong();
    }

    private static Map<String, Object> reading(long meterId, String value) {
        return Map.of("meterId", meterId, "reading", new BigDecimal(value));
    }

    @SafeVarargs
    private void round(LocalDate date, Long shiftId, Map<String, Object>... lines) throws Exception {
        Map<String, Object> body = new HashMap<>(Map.of("plantId", plantId, "date", date.toString(), "lines", List.of(lines)));
        if (shiftId != null) body.put("shiftId", shiftId);
        postAs(admin, "/api/energy/round", body).andExpect(status().isOk());
    }

    private long worker(String name, Long contractorId, String dayRate, String otRate) throws Exception {
        Map<String, Object> body = new HashMap<>(Map.of("plantId", plantId, "name", name, "department", "PRESS", "punch", "CARD"));
        if (contractorId != null) body.put("contractorId", contractorId);
        if (dayRate != null) body.put("dayRate", new BigDecimal(dayRate));
        if (otRate != null) body.put("otRate", new BigDecimal(otRate));
        return read(postAs(admin, "/api/labour/workers", body).andExpect(status().isCreated())).get("id").asLong();
    }

    private long material(String code, String name, String group, String unit) throws Exception {
        return read(postAs(admin, "/api/masters/materials", Map.of("code", code, "name", name,
                "groupId", idOf("material-groups", group), "unitId", idOf("units", unit), "hsnCode", "2508", "gstRate", 5))
                .andExpect(status().isCreated())).get("id").asLong();
    }

    private Map<String, Object> stockLine(long materialId, String qty, String rate) {
        return Map.of("materialId", materialId, "locationId", store, "quantity", new BigDecimal(qty), "rate", new BigDecimal(rate));
    }

    private Map<String, Object> issueLine(long materialId, String qty) {
        return Map.of("materialId", materialId, "locationId", store, "quantity", new BigDecimal(qty));
    }

    @SafeVarargs
    private Map<String, Object> document(String type, LocalDate date, Map<String, Object> extra, Map<String, Object>... lines) {
        Map<String, Object> body = new HashMap<>(Map.of("plantId", plantId, "docType", type, "docDate", date.toString(), "lines", List.of(lines)));
        body.putAll(extra);
        return body;
    }

    /** Creates and posts a store document. */
    private long post(Map<String, Object> body) throws Exception {
        long id = read(postAs(admin, DOCS, body).andExpect(status().isCreated())).get("id").asLong();
        postAs(admin, DOCS + "/" + id + "/post", Map.of()).andExpect(status().isOk());
        return id;
    }

    /** One lot run on the day: 790 boxes of 1.44 m² packed. */
    private void produce(LocalDate day) throws Exception {
        long press = machine("PRESS-1", "PRESS");
        long printer = machine("DP-1", "DIGITAL_PRINTER");
        long silo = machine("SILO-1", "SILO");
        long glazeLine = machine("GL-1", "GLAZE_LINE");
        long polisher = machine("POL-1", "POLISHING_LINE");
        long sorter = machine("SORT-1", "SORTING_LINE");
        long design = read(postAs(admin, "/api/masters/designs", Map.of("code", "CS-D", "name", "Costing test",
                "categoryId", idOf("categories", "GVT"), "sizeId", idOf("sizes", "600X1200"),
                "finishId", idOf("finishes", "GLOSSY"), "faces", 4)).andExpect(status().isCreated())).get("id").asLong();
        long lot = read(postAs(admin, "/api/production/lots", Map.of("plantId", plantId, "designId", design, "printerId", printer,
                "openedDate", day.toString(), "shiftId", shiftA, "closeCurrent", false))
                .andExpect(status().isCreated())).get("id").asLong();
        Map<String, Object> pressEntry = new HashMap<>(Map.of("plantId", plantId, "pressId", press, "prodDate", day.toString(),
                "shiftId", shiftA, "siloId", silo, "pressedCount", 2000, "pressRejects", 30, "dryerRejects", 20, "powderTon", 42));
        long id = read(postAs(admin, "/api/production/press-entries", pressEntry).andExpect(status().isCreated())).get("id").asLong();
        postAs(admin, "/api/production/press-entries/" + id + "/post", Map.of()).andExpect(status().isOk());
        postLine(lot, day, "GLAZE", glazeLine, 1900, 40);
        postLine(lot, day, "KILN", kiln, 1800, 36);
        postLine(lot, day, "POLISH", polisher, 1700, 20);
        long godown = read(postAs(admin, "/api/fg/godowns", Map.of("plantId", plantId, "code", "FG-1", "name", "FG"))
                .andExpect(status().isCreated())).get("id").asLong();
        long pillar = read(postAs(admin, "/api/fg/godowns/" + godown + "/pillars",
                Map.of("prefix", "A", "from", 1, "to", 1, "capacityPallets", 20))).at("/pillars/0/id").asLong();
        Map<String, Object> pack = new HashMap<>(Map.of("plantId", plantId, "lotId", lot, "prodDate", day.toString(),
                "shiftId", shiftA, "machineId", sorter, "broken", 0, "lines", List.of(Map.of("gradeId", idOf("grades", "PREMIUM"),
                        "shade", "01", "caliber", "C1", "boxes", 790, "pillarId", pillar))));
        id = read(postAs(admin, "/api/production/pack-entries", pack).andExpect(status().isCreated())).get("id").asLong();
        postAs(admin, "/api/production/pack-entries/" + id + "/post", Map.of()).andExpect(status().isOk());
    }

    private void postLine(long lot, LocalDate day, String stage, long machineId, int tilesIn, int rejects) throws Exception {
        Map<String, Object> body = Map.of("plantId", plantId, "stage", stage, "lotId", lot, "prodDate", day.toString(),
                "shiftId", shiftA, "machineId", machineId, "tilesIn", tilesIn, "rejects", rejects);
        long id = read(postAs(admin, "/api/production/line-entries", body).andExpect(status().isCreated())).get("id").asLong();
        postAs(admin, "/api/production/line-entries/" + id + "/post", Map.of()).andExpect(status().isOk());
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
