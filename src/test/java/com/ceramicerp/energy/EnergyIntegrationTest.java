package com.ceramicerp.energy;

import com.ceramicerp.IntegrationTestBase;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
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
 * Step 8c: the plant's meters, as the plant described them on 25 Oct 2026. Gas: the Gujarat Gas main meter (daily,
 * SCM) and the kiln's own meter (per shift, m³). Power: the GEB main meter (daily) and a section meter. Coal for the
 * spray dryer by weight (per shift). The DG set's hour meter and its diesel (daily).
 * <p>
 * Shifts are the seeded A (Day) and B (Night). Everything happens two days ago, so no round is "still to come"
 * whatever the time the test runs.
 */
class EnergyIntegrationTest extends IntegrationTestBase {

    private static final String METERS = "/api/energy/meters";
    private static final String ROUND = "/api/energy/round";

    private final LocalDate day = LocalDate.now(ZoneId.of("Asia/Kolkata")).minusDays(2);

    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private EntityManager em;

    private String admin;
    private long plantId, kiln, sprayDryer, dg, shiftA, shiftB;
    private long gasMain, kilnGas, powerMain, pressPower, coal, dgHours, diesel;

    @BeforeEach
    void setUp() throws Exception {
        admin = adminToken();
        plantId = createPlant(admin, createCompany(admin, "ENCO"), "EN-1");
        kiln = machine("KILN-1", "KILN");
        sprayDryer = machine("SD-1", "SPRAY_DRYER");
        dg = machine("DG-1", "UTILITY");
        shiftA = idOf("shifts", "A");
        shiftB = idOf("shifts", "B");
        gasMain = meter("GG-MAIN", "Gujarat Gas main", "GAS", null, true, null, "DAY");
        kilnGas = meter("KILN1-GAS", "Kiln 1 gas", "GAS", "M3", false, kiln, "SHIFT");
        powerMain = meter("GEB-MAIN", "GEB main", "POWER", null, true, null, "DAY");
        pressPower = meter("PRESS-PWR", "Press section power", "POWER", null, false, null, "SHIFT");
        coal = meter("SD-COAL", "Spray dryer coal", "COAL", null, false, sprayDryer, "SHIFT");
        dgHours = meter("DG-HRS", "DG hours", "RUN_HOURS", null, false, dg, "DAY");
        diesel = meter("DG-DIESEL", "DG diesel", "DIESEL", null, false, dg, "DAY");
    }

    @Test
    void metersAreSetUpWithTheRightUnits() throws Exception {
        JsonNode list = read(getAs(admin, METERS + "?plantId=" + plantId));
        assertThat(list.size()).isEqualTo(7);
        JsonNode first = list.get(0);
        assertThat(first.get("code").asText()).isEqualTo("DG-DIESEL");        // same sort order: by code
        assertThat(first.get("unit").asText()).isEqualTo("LITRE");
        assertThat(first.get("counter").asBoolean()).isFalse();
        assertThat(find(list, "KILN1-GAS").get("unit").asText()).isEqualTo("M3");
        assertThat(find(list, "GG-MAIN").get("unit").asText()).isEqualTo("SCM");   // the kind's usual unit

        postAs(admin, METERS, meterBody("X-1", "x", "COAL", "KWH", false, null, "SHIFT"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("A coal meter counts in KG"));
        postAs(admin, METERS, meterBody("gg-main", "x", "GAS", null, true, null, "DAY"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Meter code GG-MAIN already exists in plant EN-1"));
        long otherPlant = createPlant(admin, createCompany(admin, "ENCO2"), "EN-2");
        Map<String, Object> elsewhere = meterBody("K-2", "x", "GAS", null, false, kiln, "SHIFT");
        elsewhere.put("plantId", otherPlant);
        postAs(admin, METERS, elsewhere).andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Machine KILN-1 is not in plant EN-2"));

        // once it has readings, its kind and unit stay
        saveRound(day, shiftA, line(kilnGas, "100"));
        putAs(admin, METERS + "/" + kilnGas, meterBody("KILN1-GAS", "Kiln 1 gas", "GAS", "SCM", false, kiln, "SHIFT"))
                .andExpect(status().isConflict());
        putAs(admin, METERS + "/" + kilnGas, meterBody("KILN1-GAS", "Kiln 1 gas meter", "GAS", "M3", false, kiln, "SHIFT"))
                .andExpect(status().isOk());
    }

    @Test
    void readingsGiveTheUseAndCatchMistakes() throws Exception {
        LocalDate before = day.minusDays(1);
        assertThat(used(saveRound(before, shiftA, line(kilnGas, "1000")), kilnGas)).isEqualByComparingTo("0"); // where it starts
        assertThat(used(saveRound(before, shiftB, line(kilnGas, "1600")), kilnGas)).isEqualByComparingTo("600");

        postAs(admin, ROUND, round(day, shiftA, line(kilnGas, "1500")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.startsWith(
                        "Kiln 1 gas: 1500 is lower than the reading before (1600 on ")));
        assertThat(used(saveRound(day, shiftA, line(kilnGas, "2000")), kilnGas)).isEqualByComparingTo("400");

        // a corrected reading also changes the use of the one after it
        JsonNode corrected = saveRound(before, shiftB, line(kilnGas, "1700"));
        assertThat(used(corrected, kilnGas)).isEqualByComparingTo("700");
        assertThat(used(round(day, shiftA), kilnGas)).isEqualByComparingTo("300");
        postAs(admin, ROUND, round(before, shiftB, line(kilnGas, "2100")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString(
                        "is higher than the reading after it (2000 on ")));

        // taken back: the one after now counts from the one before
        long id = lineOf(corrected, kilnGas).at("/saved/id").asLong();
        deleteAs(admin, "/api/energy/readings/" + id).andExpect(status().isNoContent());
        assertThat(used(round(day, shiftA), kilnGas)).isEqualByComparingTo("1000");

        // a new meter: counted from 0
        Map<String, Object> restart = line(kilnGas, "50");
        restart.put("restarted", true);
        assertThat(used(saveRound(day, shiftB, restart), kilnGas)).isEqualByComparingTo("50");

        // what goes wrong when typing
        postAs(admin, ROUND, round(day, shiftA, Map.of("meterId", coal, "reading", 5)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Spray dryer coal: type the amount used"));
        postAs(admin, ROUND, round(day, null, line(kilnGas, "3000")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Kiln 1 gas is read after each shift: choose the shift"));
        postAs(admin, ROUND, round(day.plusDays(5), shiftA, line(kilnGas, "3000")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Readings can't be entered for a day that hasn't come"));
    }

    @Test
    void theDailyMetersComeWithTheLastShift() throws Exception {
        assertThat(codes(round(day, shiftA))).containsExactlyInAnyOrder("KILN1-GAS", "PRESS-PWR", "SD-COAL");
        assertThat(codes(round(day, shiftB))).hasSize(7);
        assertThat(codes(read(getAs(admin, ROUND + "?plantId=" + plantId + "&date=" + day))))
                .containsExactlyInAnyOrder("DG-DIESEL", "DG-HRS", "GEB-MAIN", "GG-MAIN");

        // saved from the night round, a daily reading has no shift: the day-only round finds it too
        saveRound(day, shiftB, line(powerMain, "50000"));
        JsonNode dayOnly = read(getAs(admin, ROUND + "?plantId=" + plantId + "&date=" + day));
        assertThat(lineOf(dayOnly, powerMain).at("/saved/reading").decimalValue()).isEqualByComparingTo("50000");
    }

    @Test
    void theUsePerSqmAndTheDashboard() throws Exception {
        produce();
        LocalDate before = day.minusDays(1);
        saveRound(before, shiftB, line(kilnGas, "9000"), line(pressPower, "100"), line(powerMain, "50000"),
                line(gasMain, "20000"), line(dgHours, "120.5"));
        saveRound(day, shiftA, line(kilnGas, "10000"), line(pressPower, "400"), amount(coal, "2500"));
        JsonNode night = saveRound(day, shiftB, line(kilnGas, "10500"), line(pressPower, "600"), amount(coal, "2000"),
                line(powerMain, "50900"), line(gasMain, "21800"), line(dgHours, "123.0"), amount(diesel, "40"));
        assertThat(lineOf(night, kilnGas).at("/previous/reading").decimalValue()).isEqualByComparingTo("10000");

        JsonNode use = read(getAs(admin, "/api/energy/use?plantId=" + plantId + "&from=" + day + "&to=" + day));
        JsonNode t = use.get("totals");
        assertDecimal(t.get("gasMain"), "1800");
        assertThat(t.get("gasMainUnit").asText()).isEqualTo("SCM");
        assertDecimal(t.get("kilnGas"), "1500");
        assertThat(t.get("kilnGasUnit").asText()).isEqualTo("M3");
        assertDecimal(t.get("sqmFired"), "1270.08");                 // 1,764 tiles x 0.72 m²
        assertDecimal(t.get("kilnGasPerSqm"), "1.181");
        assertDecimal(t.get("powerMain"), "900");
        assertDecimal(t.get("sqmPacked"), "1137.60");                // 790 boxes x 1.44 m²
        assertDecimal(t.get("powerPerSqm"), "0.791");
        assertDecimal(t.get("gasPerSqm"), "1.582");
        assertDecimal(t.get("coalKg"), "4500");
        assertDecimal(t.get("dgHours"), "2.5");
        assertDecimal(t.get("dieselLitres"), "40");
        assertDecimal(t.get("dieselPerHour"), "16.000");
        assertThat(t.hasNonNull("coalPerTon")).isFalse();            // no spray runs: nothing to divide by

        // power by section, and what the main meter counted beyond them
        assertThat(use.at("/powerParts/0/name").asText()).isEqualTo("Press section power");
        assertDecimal(use.at("/powerParts/0/kwh"), "500");
        assertThat(use.at("/powerParts/1").hasNonNull("meterId")).isFalse();
        assertDecimal(use.at("/powerParts/1/kwh"), "400");
        assertDecimal(use.at("/days/0/kilnGas"), "1500");

        // the dashboard takes the meters over the typed gas (1,400 SCM in the kiln entry)
        JsonNode p = read(getAs(admin, "/api/dashboard?plantId=" + plantId + "&from=" + day + "&to=" + day)).get("production");
        assertDecimal(p.get("gasScm"), "1500");
        assertThat(p.get("gasUnit").asText()).isEqualTo("M3");
        assertDecimal(p.get("gasPerSqm"), "1.181");
        assertDecimal(p.get("powerKwh"), "900");
        assertDecimal(p.get("powerPerSqm"), "0.791");
    }

    @Test
    void readingsNotTakenNeedALook() throws Exception {
        // meters set up ten days ago, nothing read for the last round or for yesterday
        em.flush();
        jdbc.update("update meters set created_at = ? where plant_id = ?",
                java.sql.Timestamp.valueOf(day.minusDays(8).atStartOfDay()), plantId);
        em.clear();

        JsonNode last = read(getAs(admin, "/api/energy/use?plantId=" + plantId + "&from=" + day + "&to=" + day)).get("lastRound");
        assertThat(last.get("missing")).hasSize(3);
        assertThat(last.get("dayMissing")).hasSize(3);                // not the diesel: it's typed only when the DG ran
        JsonNode needs = attention(read(getAs(admin, "/api/dashboard?plantId=" + plantId + "&from=" + day + "&to=" + day)), "METER_MISSING");
        assertThat(needs.get("count").asInt()).isEqualTo(6);

        // the round to read now is the shift that ended last; fill it, and yesterday's daily meters
        JsonNode now = read(getAs(admin, ROUND + "?plantId=" + plantId));
        assertThat(now.get("date").asText()).isEqualTo(last.get("date").asText());
        assertThat(now.at("/shift/code").asText()).isEqualTo(last.get("shiftCode").asText());
        saveRound(LocalDate.parse(now.get("date").asText()), now.at("/shift/id").asLong(),
                line(kilnGas, "10"), line(pressPower, "10"), amount(coal, "10"));
        saveRound(LocalDate.parse(last.get("day").asText()), null,
                line(gasMain, "10"), line(powerMain, "10"), line(dgHours, "10"), amount(diesel, "10"));

        JsonNode after = read(getAs(admin, "/api/energy/use?plantId=" + plantId + "&from=" + day + "&to=" + day)).get("lastRound");
        assertThat(after.get("missing")).isEmpty();
        assertThat(after.get("dayMissing")).isEmpty();
        assertThat(attention(read(getAs(admin, "/api/dashboard?plantId=" + plantId + "&from=" + day + "&to=" + day)),
                "METER_MISSING")).isNull();

        // the period's count: the kiln has none of its 2 readings for the day, the main meter none of 1
        JsonNode meters = read(getAs(admin, "/api/energy/use?plantId=" + plantId + "&from=" + day + "&to=" + day)).get("meters");
        assertThat(find(meters, "KILN1-GAS", "/meter/code").get("expected").asInt()).isEqualTo(2);
        assertThat(find(meters, "KILN1-GAS", "/meter/code").get("readings").asInt()).isEqualTo(0);
        assertThat(find(meters, "GEB-MAIN", "/meter/code").get("expected").asInt()).isEqualTo(1);
    }

    @Test
    void readingsTypedFromARegisterCountFromTheFirstOne() throws Exception {
        // set up today, but the register goes back: the meter is in use from its first reading
        saveRound(day.minusDays(1), shiftB, line(kilnGas, "100"));
        saveRound(day, shiftA, line(kilnGas, "200"));
        JsonNode meters = read(getAs(admin, "/api/energy/use?plantId=" + plantId + "&from=" + day + "&to=" + day)).get("meters");
        JsonNode kilnRow = find(meters, "KILN1-GAS", "/meter/code");
        assertThat(kilnRow.get("readings").asInt()).isEqualTo(1);
        assertThat(kilnRow.get("expected").asInt()).isEqualTo(2);
        assertThat(find(meters, "GEB-MAIN", "/meter/code").get("expected").asInt()).isEqualTo(0);   // no readings, set up today
    }

    @Test
    void whoMayDoWhat() throws Exception {
        createUser(admin, "kiln.sup", "KilnSup@123", roleId("Kiln supervisor"), plantId);
        String supervisor = login("kiln.sup", "KilnSup@123");
        long viewRole = createRole(admin, "Energy viewer", "ENERGY_VIEW");
        createUser(admin, "en.view", "EnView@123", viewRole, plantId);
        String viewer = login("en.view", "EnView@123");

        // the starter supervisor role may enter readings, not see the report or set meters up
        getAs(supervisor, ROUND + "?plantId=" + plantId).andExpect(status().isOk());
        postAs(supervisor, ROUND, round(day, shiftA, line(kilnGas, "100"))).andExpect(status().isOk());
        getAs(supervisor, "/api/energy/use?plantId=" + plantId + "&from=" + day + "&to=" + day).andExpect(status().isForbidden());
        postAs(supervisor, METERS, meterBody("X-2", "x", "GAS", null, false, null, "DAY")).andExpect(status().isForbidden());

        getAs(viewer, "/api/energy/use?plantId=" + plantId + "&from=" + day + "&to=" + day).andExpect(status().isOk());
        postAs(viewer, ROUND, round(day, shiftA, line(kilnGas, "200"))).andExpect(status().isForbidden());

        long otherPlant = createPlant(admin, createCompany(admin, "ENCO3"), "EN-3");
        getAs(supervisor, ROUND + "?plantId=" + otherPlant).andExpect(status().isForbidden());
    }

    // ------------------------------------------------------------------ helpers

    /**
     * One lot run on the day, as in the dashboard test: 2,000 pressed, 1,900 glazed, 1,800 into the kiln with 36
     * rejected (1,400 SCM typed in the kiln entry), 1,700 polished, 790 boxes packed.
     */
    private void produce() throws Exception {
        long press = machine("PRESS-1", "PRESS");
        long silo = machine("SILO-1", "SILO");
        long glazeLine = machine("GL-1", "GLAZE_LINE");
        long polisher = machine("POL-1", "POLISHING_LINE");
        long sorter = machine("SORT-1", "SORTING_LINE");
        long design = read(postAs(admin, "/api/masters/designs", Map.of("code", "EN-D", "name", "Energy test",
                "categoryId", idOf("categories", "GVT"), "sizeId", idOf("sizes", "600X1200"),
                "finishId", idOf("finishes", "GLOSSY"), "faces", 4)).andExpect(status().isCreated())).get("id").asLong();
        long lot = read(postAs(admin, "/api/production/lots", Map.of("plantId", plantId, "designId", design, "pressId", press,
                "openedDate", day.toString(), "shiftId", shiftA, "closeCurrent", false))
                .andExpect(status().isCreated())).get("id").asLong();
        Map<String, Object> pressEntry = new HashMap<>(Map.of("plantId", plantId, "lotId", lot, "prodDate", day.toString(),
                "shiftId", shiftA, "siloId", silo, "pressedCount", 2000, "pressRejects", 30, "dryerRejects", 20, "powderTon", 42));
        long id = read(postAs(admin, "/api/production/press-entries", pressEntry).andExpect(status().isCreated())).get("id").asLong();
        postAs(admin, "/api/production/press-entries/" + id + "/post", Map.of()).andExpect(status().isOk());
        postLine(lot, "GLAZE", glazeLine, 1900, 40, null);
        postLine(lot, "KILN", kiln, 1800, 36, 1400);
        postLine(lot, "POLISH", polisher, 1700, 20, null);

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

    private void postLine(long lot, String stage, long machineId, int tilesIn, int rejects, Integer gasScm) throws Exception {
        Map<String, Object> body = new HashMap<>(Map.of("plantId", plantId, "stage", stage, "lotId", lot,
                "prodDate", day.toString(), "shiftId", shiftA, "machineId", machineId, "tilesIn", tilesIn, "rejects", rejects));
        if (gasScm != null) body.put("gasScm", gasScm);
        long id = read(postAs(admin, "/api/production/line-entries", body).andExpect(status().isCreated())).get("id").asLong();
        postAs(admin, "/api/production/line-entries/" + id + "/post", Map.of()).andExpect(status().isOk());
    }

    private long meter(String code, String name, String kind, String unit, boolean main, Long machineId, String every)
            throws Exception {
        return read(postAs(admin, METERS, meterBody(code, name, kind, unit, main, machineId, every))
                .andExpect(status().isCreated())).get("id").asLong();
    }

    private Map<String, Object> meterBody(String code, String name, String kind, String unit, boolean main, Long machineId,
                                          String every) {
        Map<String, Object> body = new HashMap<>(Map.of("plantId", plantId, "code", code, "name", name, "kind", kind,
                "main", main, "readEvery", every));
        if (unit != null) body.put("unit", unit);
        if (machineId != null) body.put("machineId", machineId);
        return body;
    }

    private static Map<String, Object> line(long meterId, String reading) {
        return new HashMap<>(Map.of("meterId", meterId, "reading", new BigDecimal(reading)));
    }

    private static Map<String, Object> amount(long meterId, String used) {
        return new HashMap<>(Map.of("meterId", meterId, "used", new BigDecimal(used)));
    }

    @SafeVarargs
    private Map<String, Object> round(LocalDate date, Long shiftId, Map<String, Object>... lines) {
        Map<String, Object> body = new HashMap<>(Map.of("plantId", plantId, "date", date.toString(),
                "lines", new ArrayList<>(List.of(lines))));
        if (shiftId != null) body.put("shiftId", shiftId);
        return body;
    }

    @SafeVarargs
    private JsonNode saveRound(LocalDate date, Long shiftId, Map<String, Object>... lines) throws Exception {
        return read(postAs(admin, ROUND, round(date, shiftId, lines)).andExpect(status().isOk()));
    }

    private JsonNode round(LocalDate date, long shiftId) throws Exception {
        return read(getAs(admin, ROUND + "?plantId=" + plantId + "&date=" + date + "&shiftId=" + shiftId));
    }

    private static JsonNode lineOf(JsonNode round, long meterId) {
        for (JsonNode l : round.get("lines")) {
            if (l.at("/meter/id").asLong() == meterId) return l;
        }
        throw new AssertionError("No line for meter " + meterId);
    }

    private static BigDecimal used(JsonNode round, long meterId) {
        return lineOf(round, meterId).at("/saved/used").decimalValue();
    }

    private static List<String> codes(JsonNode round) {
        return round.get("lines").findValues("meter").stream().map(m -> m.get("code").asText()).toList();
    }

    private static JsonNode find(JsonNode list, String code) {
        return find(list, code, "/code");
    }

    private static JsonNode find(JsonNode list, String code, String path) {
        for (JsonNode n : list) {
            if (n.at(path).asText().equals(code)) return n;
        }
        throw new AssertionError(code + " not found");
    }

    private static JsonNode attention(JsonNode dashboard, String kind) {
        for (JsonNode a : dashboard.get("attention")) {
            if (a.get("kind").asText().equals(kind)) return a;
        }
        return null;
    }

    private static void assertDecimal(JsonNode node, String expected) {
        assertThat(node.decimalValue()).isEqualByComparingTo(expected);
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
}
