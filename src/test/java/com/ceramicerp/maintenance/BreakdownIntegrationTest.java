package com.ceramicerp.maintenance;

import com.ceramicerp.IntegrationTestBase;
import com.ceramicerp.numbering.DocumentNumberService;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

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

/**
 * Step 8a: the breakdown log. Plant MNT-1 with a press, a kiln and a dryer; the store holds 10 bearings (₹ 450)
 * and 20 kg grease (₹ 180).
 */
class BreakdownIntegrationTest extends IntegrationTestBase {

    private static final String BD = "/api/maintenance/breakdowns";

    private final LocalDateTime now = LocalDateTime.now(ZoneId.of("Asia/Kolkata"));
    private final LocalDate today = now.toLocalDate();
    private final LocalDate yesterday = today.minusDays(1);
    private final String fy = DocumentNumberService.financialYear(today);

    private String admin;
    private long plantId, press, kiln, dryer, main, bearing, grease;
    private String supervisor, fitter;

    @BeforeEach
    void setUp() throws Exception {
        admin = adminToken();
        plantId = createPlant(admin, createCompany(admin, "MNTCO"), "MNT-1");
        press = machine("PRESS-1", "PRESS");
        kiln = machine("KILN-1", "KILN");
        dryer = machine("DRYER-1", "DRYER");
        main = read(postAs(admin, "/api/store/locations", Map.of("plantId", plantId, "code", "MAIN", "name", "Main store"))
                .andExpect(status().isCreated())).get("id").asLong();
        bearing = material("BRG-6205", "Bearing 6205", "SPARE", "NOS");
        grease = material("GREASE-EP2", "EP2 grease", "CHEMICAL", "KG");
        Map<String, Object> opening = new HashMap<>(Map.of("plantId", plantId, "docType", "ADJUSTMENT", "reason", "OPENING",
                "docDate", today.toString(), "lines", List.of(stockLine(bearing, "10", "450"), stockLine(grease, "20", "180"))));
        long doc = read(postAs(admin, "/api/store/documents", opening).andExpect(status().isCreated())).get("id").asLong();
        postAs(admin, "/api/store/documents/" + doc + "/post", Map.of()).andExpect(status().isOk());

        // the starter roles: a line supervisor reports, the maintenance in-charge closes
        createUser(admin, "press.sup", "PressSup@123", roleId("Press supervisor"), plantId);
        supervisor = login("press.sup", "PressSup@123");
        createUser(admin, "fitter", "Fitter@123", roleId("Maintenance in-charge"), plantId);
        fitter = login("fitter", "Fitter@123");
    }

    @Test
    void aSupervisorReportsAndMaintenanceClosesWithTheSpares() throws Exception {
        LocalDateTime stopped = now.minusHours(2).withSecond(0).withNano(0);
        JsonNode open = read(postAs(supervisor, BD, report(press, stopped, " Press not building pressure "))
                .andExpect(status().isCreated()));
        assertThat(open.get("breakdownNo").asText()).isEqualTo("MNT-1/BD/" + fy + "/0001");
        assertThat(open.get("status").asText()).isEqualTo("OPEN");
        assertThat(open.get("symptom").asText()).isEqualTo("Press not building pressure");
        assertThat(open.get("downtimeMinutes").asInt()).isBetween(119, 121);   // so far
        assertThat(open.get("reportedBy").asText()).isEqualTo("press.sup");
        long id = open.get("id").asLong();

        // the same machine can't be down twice
        String again = read(postAs(supervisor, BD, report(press, now.minusMinutes(5), "again"))
                .andExpect(status().isConflict())).get("message").asText();
        assertThat(again).startsWith("Machine PRESS-1 is already reported down since").endsWith("(MNT-1/BD/" + fy + "/0001)");
        postAs(supervisor, BD, report(press, now.plusHours(1), "later")).andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("It can't stop in the future"));

        // only maintenance closes it
        Map<String, Object> close = close(stopped.plusMinutes(95), "HYDRAULIC", "Replaced the pump seal",
                spare(bearing, "2"), spare(grease, "1.5"));
        close.put("failedPart", "hydraulic pump seal");
        close.put("cause", "seal worn out");
        close.put("fixedBy", "Ramesh, Suresh");
        postAs(supervisor, BD + "/" + id + "/close", close).andExpect(status().isForbidden());
        postAs(fitter, BD + "/" + id + "/close", close(stopped.minusMinutes(1), "HYDRAULIC", "x"))
                .andExpect(status().isConflict());

        JsonNode closed = read(postAs(fitter, BD + "/" + id + "/close", close).andExpect(status().isOk()));
        assertThat(closed.get("status").asText()).isEqualTo("CLOSED");
        assertThat(closed.get("downtimeMinutes").asInt()).isEqualTo(95);
        assertThat(closed.get("category").asText()).isEqualTo("HYDRAULIC");
        assertThat(closed.get("fixedBy").asText()).isEqualTo("Ramesh, Suresh");
        assertThat(closed.get("closedBy").asText()).isEqualTo("fitter");
        assertThat(closed.at("/spares/0/materialCode").asText()).isEqualTo("BRG-6205");
        assertDecimal(closed.at("/spares/0/value"), "900");
        assertDecimal(closed.at("/spares/1/value"), "270");
        assertDecimal(closed.get("spareCost"), "1170");
        assertThat(closed.hasNonNull("issueDocumentNo")).isTrue();
        // the spares left the store
        assertDecimal(stock("BRG-6205"), "8");
        assertDecimal(stock("GREASE-EP2"), "18.5");

        postAs(fitter, BD + "/" + id + "/close", close).andExpect(status().isConflict());
        // now the press can be reported down again
        postAs(supervisor, BD, report(press, now.minusMinutes(5), "jammed")).andExpect(status().isCreated());
    }

    @Test
    void cancellingAClosedBreakdownPutsTheSparesBack() throws Exception {
        long id = read(postAs(supervisor, BD, report(kiln, now.minusHours(3), "roller stuck"))).get("id").asLong();
        postAs(fitter, BD + "/" + id + "/close", close(now.minusHours(1), "MECHANICAL", "Changed roller bearing",
                spare(bearing, "3"))).andExpect(status().isOk());
        assertDecimal(stock("BRG-6205"), "7");

        postAs(fitter, BD + "/" + id + "/cancel", Map.of("reason", "wrong kiln")).andExpect(status().isForbidden());
        JsonNode cancelled = read(postAs(admin, BD + "/" + id + "/cancel", Map.of("reason", "wrong kiln")).andExpect(status().isOk()));
        assertThat(cancelled.get("status").asText()).isEqualTo("CANCELLED");
        assertDecimal(stock("BRG-6205"), "10");
        postAs(admin, BD + "/" + id + "/cancel", Map.of("reason", "again")).andExpect(status().isConflict());

        // a report made by mistake is simply cancelled by the supervisor
        long mistake = read(postAs(supervisor, BD, report(dryer, now.minusMinutes(10), "oops"))).get("id").asLong();
        postAs(supervisor, BD + "/" + mistake + "/cancel", Map.of("reason", "it was running")).andExpect(status().isOk());
    }

    @Test
    void downtimeFiguresAndTheDashboard() throws Exception {
        // yesterday: the press stopped twice (60 + 30 minutes); the kiln's night stop ran 60 minutes into yesterday
        closed(press, yesterday.atTime(8, 0), yesterday.atTime(9, 0), "HYDRAULIC");
        closed(press, yesterday.atTime(14, 0), yesterday.atTime(14, 30), "ELECTRICAL");
        closed(kiln, yesterday.minusDays(1).atTime(23, 0), yesterday.atTime(1, 0), "MECHANICAL");
        long cancelled = closed(kiln, yesterday.atTime(16, 0), yesterday.atTime(18, 0), "OTHER");
        postAs(admin, BD + "/" + cancelled + "/cancel", Map.of("reason", "test")).andExpect(status().isOk());
        // the dryer has been down since midnight (outside yesterday)
        postAs(supervisor, BD, report(dryer, today.atStartOfDay(), "fan motor tripped")).andExpect(status().isCreated());

        JsonNode d = read(getAs(admin, "/api/maintenance/downtime?plantId=" + plantId + "&from=" + yesterday + "&to=" + yesterday)
                .andExpect(status().isOk()));
        assertThat(d.get("periodMinutes").asInt()).isEqualTo(1440);
        assertThat(d.get("breakdowns").asInt()).isEqualTo(2);          // the kiln's started the day before
        assertThat(d.get("downtimeMinutes").asInt()).isEqualTo(150);
        JsonNode p = machineRow(d, "PRESS-1");
        assertThat(p.get("downtimeMinutes").asInt()).isEqualTo(90);
        assertThat(p.get("mttrMinutes").asInt()).isEqualTo(45);
        assertDecimal(p.get("mtbfHours"), "11.3");                       // (1,440 - 90) / 60 / 2 = 11.25
        assertDecimal(p.get("availabilityPct"), "93.8");
        JsonNode k = machineRow(d, "KILN-1");
        assertThat(k.get("downtimeMinutes").asInt()).isEqualTo(60);
        assertThat(k.hasNonNull("mttrMinutes")).isFalse();
        assertDecimal(k.get("availabilityPct"), "95.8");
        assertThat(d.at("/machines/0/machine/code").asText()).isEqualTo("PRESS-1");   // most time lost first
        assertThat(machineRow(d, "DRYER-1").get("downNow").asBoolean()).isTrue();
        assertThat(d.at("/downNow/0/machine/code").asText()).isEqualTo("DRYER-1");
        assertThat(d.get("categories").findValuesAsText("category")).contains("HYDRAULIC", "MECHANICAL", "ELECTRICAL")
                .doesNotContain("OTHER");

        JsonNode dash = read(getAs(admin, "/api/dashboard?plantId=" + plantId + "&from=" + yesterday + "&to=" + yesterday));
        assertThat(dash.at("/maintenance/breakdowns").asInt()).isEqualTo(2);
        assertThat(dash.at("/maintenance/downtimeMinutes").asInt()).isEqualTo(150);
        assertThat(dash.at("/maintenance/machinesDown").asInt()).isEqualTo(1);
        assertDecimal(dash.at("/maintenance/availabilityPct"), "96.5");   // (3 x 1,440 - 150) / (3 x 1,440)
        assertThat(dash.at("/attention/0/kind").asText()).isEqualTo("MACHINES_DOWN");
        assertThat(dash.at("/attention/0/what").asText()).isEqualTo("Machine DRYER-1");

        // the list and the global search
        getAs(admin, BD + "?plantId=" + plantId + "&status=OPEN").andExpect(jsonPath("$.totalElements").value(1));
        getAs(admin, BD + "?plantId=" + plantId + "&search=pump").andExpect(jsonPath("$.totalElements").value(0));
        getAs(admin, BD + "?plantId=" + plantId + "&machineId=" + press).andExpect(jsonPath("$.totalElements").value(2));
        getAs(admin, "/api/search?q=MNT-1/BD").andExpect(jsonPath("$[0].type").value("breakdown"));
    }

    @Test
    void whoMayDoWhat() throws Exception {
        long viewRole = createRole(admin, "Maint viewer", "MAINTENANCE_VIEW");
        createUser(admin, "maint.view", "MaintView@123", viewRole, plantId);
        String viewer = login("maint.view", "MaintView@123");
        long reportRole = createRole(admin, "Reporter only", "BREAKDOWN_REPORT");
        createUser(admin, "reporter", "Reporter@123", reportRole, plantId);
        String reporter = login("reporter", "Reporter@123");

        postAs(viewer, BD, report(press, now.minusMinutes(3), "x")).andExpect(status().isForbidden());
        long id = read(postAs(reporter, BD, report(press, now.minusMinutes(3), "x")).andExpect(status().isCreated())).get("id").asLong();
        getAs(reporter, BD + "/" + id).andExpect(status().isOk());
        getAs(reporter, "/api/maintenance/lookups/machines?plantId=" + plantId)
                .andExpect(jsonPath("$.length()").value(3));
        getAs(reporter, "/api/maintenance/downtime?plantId=" + plantId + "&from=" + today + "&to=" + today)
                .andExpect(status().isForbidden());
        getAs(reporter, "/api/maintenance/lookups/spares").andExpect(status().isForbidden());
        // spares come first in the fitter's list
        assertThat(read(getAs(fitter, "/api/maintenance/lookups/spares")).at("/0/spare").asBoolean()).isTrue();

        long other = createPlant(admin, createCompany(admin, "MNTCO2"), "MNT-2");
        getAs(reporter, BD + "?plantId=" + other).andExpect(status().isForbidden());
        Map<String, Object> elsewhere = report(press, now.minusMinutes(1), "x");
        elsewhere.put("plantId", other);
        postAs(admin, BD, elsewhere)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Machine PRESS-1 is not in plant MNT-2"));
    }

    // ------------------------------------------------------------------ helpers

    private Map<String, Object> report(long machineId, LocalDateTime stoppedAt, String symptom) {
        return new HashMap<>(Map.of("plantId", plantId, "machineId", machineId, "stoppedAt", stoppedAt.withNano(0).toString(),
                "symptom", symptom));
    }

    @SafeVarargs
    private Map<String, Object> close(LocalDateTime restartedAt, String category, String action, Map<String, Object>... spares) {
        return new HashMap<>(Map.of("restartedAt", restartedAt.withNano(0).toString(), "category", category,
                "actionTaken", action, "spares", new ArrayList<>(List.of(spares))));
    }

    private Map<String, Object> spare(long materialId, String qty) {
        return Map.of("materialId", materialId, "locationId", main, "quantity", new BigDecimal(qty));
    }

    /** A breakdown reported and closed (by the admin) at fixed times. */
    private long closed(long machineId, LocalDateTime stopped, LocalDateTime restarted, String category) throws Exception {
        long id = read(postAs(admin, BD, report(machineId, stopped, "stopped")).andExpect(status().isCreated())).get("id").asLong();
        postAs(admin, BD + "/" + id + "/close", close(restarted, category, "fixed")).andExpect(status().isOk());
        return id;
    }

    private static JsonNode machineRow(JsonNode d, String code) {
        for (JsonNode m : d.get("machines")) {
            if (m.at("/machine/code").asText().equals(code)) return m;
        }
        throw new AssertionError("No " + code);
    }

    private JsonNode stock(String materialCode) throws Exception {
        for (JsonNode row : read(getAs(admin, "/api/store/stock?plantId=" + plantId))) {
            if (row.get("materialCode").asText().equals(materialCode)) return row.get("quantity");
        }
        throw new AssertionError("No stock of " + materialCode);
    }

    private Map<String, Object> stockLine(long materialId, String qty, String rate) {
        return Map.of("materialId", materialId, "locationId", main, "quantity", new BigDecimal(qty), "rate", new BigDecimal(rate));
    }

    private long machine(String code, String type) throws Exception {
        return read(postAs(admin, "/api/masters/machines", Map.of("plantId", plantId, "code", code,
                "name", "Machine " + code, "type", type)).andExpect(status().isCreated())).get("id").asLong();
    }

    private long material(String code, String name, String group, String unit) throws Exception {
        return read(postAs(admin, "/api/masters/materials", Map.of("code", code, "name", name,
                "groupId", idOf("material-groups", group), "unitId", idOf("units", unit), "hsnCode", "8482", "gstRate", 18))
                .andExpect(status().isCreated())).get("id").asLong();
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
