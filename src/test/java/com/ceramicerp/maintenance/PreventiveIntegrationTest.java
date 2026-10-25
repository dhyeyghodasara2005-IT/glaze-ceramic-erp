package com.ceramicerp.maintenance;

import com.ceramicerp.IntegrationTestBase;
import com.ceramicerp.numbering.DocumentNumberService;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

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

/** Step 8b: preventive jobs. Plant PM-1 with two presses and a kiln; the store holds 10 oil filters (₹ 1,200). */
class PreventiveIntegrationTest extends IntegrationTestBase {

    private static final String TASKS = "/api/maintenance/pm-tasks";

    private final LocalDate today = LocalDate.now(ZoneId.of("Asia/Kolkata"));
    private final String fy = DocumentNumberService.financialYear(today);

    private String admin;
    private long plantId, press1, press2, kiln, main, filter;

    @BeforeEach
    void setUp() throws Exception {
        admin = adminToken();
        plantId = createPlant(admin, createCompany(admin, "PMCO"), "PM-1");
        press1 = machine("PRESS-1", "PRESS");
        press2 = machine("PRESS-2", "PRESS");
        kiln = machine("KILN-1", "KILN");
        main = read(postAs(admin, "/api/store/locations", Map.of("plantId", plantId, "code", "MAIN", "name", "Main store"))
                .andExpect(status().isCreated())).get("id").asLong();
        filter = read(postAs(admin, "/api/masters/materials", Map.of("code", "FLT-HYD", "name", "Hydraulic oil filter",
                "groupId", idOf("material-groups", "SPARE"), "unitId", idOf("units", "NOS"), "hsnCode", "8421", "gstRate", 18))
                .andExpect(status().isCreated())).get("id").asLong();
        Map<String, Object> opening = new HashMap<>(Map.of("plantId", plantId, "docType", "ADJUSTMENT", "reason", "OPENING",
                "docDate", today.toString(), "lines", List.of(Map.of("materialId", filter, "locationId", main,
                        "quantity", new BigDecimal("10"), "rate", new BigDecimal("1200")))));
        long doc = read(postAs(admin, "/api/store/documents", opening).andExpect(status().isCreated())).get("id").asLong();
        postAs(admin, "/api/store/documents/" + doc + "/post", Map.of()).andExpect(status().isOk());
    }

    @Test
    void oneJobForBothPressesAndTheDueList() throws Exception {
        JsonNode created = read(postAs(admin, TASKS, task(List.of(press1, press2), "Clean die and punches", "CLEANING", 7,
                today.minusDays(3))).andExpect(status().isCreated()));
        assertThat(created.size()).isEqualTo(2);
        assertThat(created.at("/0/due").asText()).isEqualTo("OVERDUE");
        assertThat(created.at("/0/daysOverdue").asInt()).isEqualTo(3);
        create(kiln, "Check rollers", today);
        create(kiln, "Check burners", today.plusDays(5));
        create(kiln, "Calibrate thermocouples", today.plusDays(40));

        assertThat(list("")).hasSize(5);
        assertThat(list("&due=OVERDUE")).hasSize(2);
        assertThat(list("&due=TODAY")).hasSize(3);           // what has to be done by today, overdue included
        assertThat(list("&due=WEEK")).hasSize(4);
        assertThat(list("&machineId=" + kiln)).hasSize(3);
        JsonNode soonest = list("").get(0);
        assertThat(soonest.get("title").asText()).isEqualTo("Clean die and punches");

        JsonNode overview = read(getAs(admin, "/api/maintenance/pm-overview?plantId=" + plantId));
        assertThat(overview.get("activeJobs").asInt()).isEqualTo(5);
        assertThat(overview.get("overdue").asInt()).isEqualTo(2);
        assertThat(overview.get("dueToday").asInt()).isEqualTo(1);
        assertThat(overview.get("dueWeek").asInt()).isEqualTo(1);
        assertThat(overview.at("/oldest/daysOverdue").asInt()).isEqualTo(3);

        // switched off: not due any more
        long burners = list("&search=burners").get(0).get("id").asLong();
        Map<String, Object> off = task(List.of(kiln), "Check burners", "INSPECTION", 30, today.plusDays(5));
        off.put("active", false);
        putAs(admin, TASKS + "/" + burners, off).andExpect(status().isOk());
        assertThat(list("")).hasSize(4);
        assertThat(list("&includeInactive=true")).hasSize(5);
        putAs(admin, TASKS + "/" + burners, task(List.of(kiln, press1), "x", "OTHER", 1, today))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("A job belongs to one machine: set it up again for the others"));
    }

    @Test
    void doneMovesTheNextDueAndTakesTheSparesThenCancelPutsItBack() throws Exception {
        Map<String, Object> filterJob = task(List.of(press1), "Change hydraulic oil filter", "REPLACEMENT", 90, today.minusDays(2));
        filterJob.put("needsStop", true);
        long id = read(postAs(admin, TASKS, filterJob).andExpect(status().isCreated())).get(0).get("id").asLong();
        Map<String, Object> done = new HashMap<>(Map.of("doneOn", today.toString(), "doneBy", "Ramesh", "minutes", 40,
                "stopMinutes", 30, "findings", "oil dark: plan an oil change",
                "spares", new ArrayList<>(List.of(Map.of("materialId", filter, "locationId", main, "quantity", new BigDecimal("1"))))));
        JsonNode t = read(postAs(admin, TASKS + "/" + id + "/done", done).andExpect(status().isOk()));
        assertThat(t.get("lastDone").asText()).isEqualTo(today.toString());
        assertThat(t.get("nextDue").asText()).isEqualTo(today.plusDays(90).toString());
        assertThat(t.get("due").asText()).isEqualTo("LATER");
        JsonNode job = t.at("/history/0");
        assertThat(job.get("jobNo").asText()).isEqualTo("PM-1/PM/" + fy + "/0001");
        assertThat(job.get("wasDue").asText()).isEqualTo(today.minusDays(2).toString());
        assertThat(job.get("daysLate").asInt()).isEqualTo(2);
        assertThat(job.get("stopMinutes").asInt()).isEqualTo(30);
        assertThat(job.get("spareCost").decimalValue()).isEqualByComparingTo("1200");
        assertThat(job.get("latest").asBoolean()).isTrue();
        assertThat(stock()).isEqualByComparingTo("9");

        // not before the last time, not in the future
        Map<String, Object> earlier = new HashMap<>(Map.of("doneOn", today.minusDays(1).toString()));
        postAs(admin, TASKS + "/" + id + "/done", earlier).andExpect(status().isConflict());
        postAs(admin, TASKS + "/" + id + "/done", Map.of("doneOn", today.plusDays(1).toString()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("It can't be done in the future"));

        // done again the same day: only that newest record can be cancelled
        JsonNode twice = read(postAs(admin, TASKS + "/" + id + "/done", Map.of("doneOn", today.toString())));
        long first = job.get("id").asLong();
        long second = twice.at("/history/0/id").asLong();
        postAs(admin, "/api/maintenance/pm-jobs/" + first + "/cancel", Map.of("reason", "x"))
                .andExpect(status().isConflict());
        postAs(admin, "/api/maintenance/pm-jobs/" + second + "/cancel", Map.of("reason", "entered twice")).andExpect(status().isOk());
        JsonNode back = read(postAs(admin, "/api/maintenance/pm-jobs/" + first + "/cancel", Map.of("reason", "test"))
                .andExpect(status().isOk()));
        assertThat(back.get("nextDue").asText()).isEqualTo(today.minusDays(2).toString());
        assertThat(back.hasNonNull("lastDone")).isFalse();
        assertThat(back.get("due").asText()).isEqualTo("OVERDUE");
        assertThat(stock()).isEqualByComparingTo("10");
        getAs(admin, "/api/search?q=PM-1/PM").andExpect(jsonPath("$[0].type").value("pm-job"))
                .andExpect(jsonPath("$[0].id").value(id));
    }

    @Test
    void onTimeShareAndTheDashboard() throws Exception {
        long late = create(press1, "Grease guides", today.minusDays(4));
        long early = create(press2, "Grease guides", today.plusDays(1));
        create(kiln, "Check rollers", today.minusDays(9));
        postAs(admin, TASKS + "/" + late + "/done", Map.of("doneOn", today.toString())).andExpect(status().isOk());
        postAs(admin, TASKS + "/" + early + "/done", Map.of("doneOn", today.toString())).andExpect(status().isOk());

        JsonNode overview = read(getAs(admin, "/api/maintenance/pm-overview?plantId=" + plantId));
        assertThat(overview.get("doneLast30").asInt()).isEqualTo(2);
        assertThat(overview.get("onTimeLast30").asInt()).isEqualTo(1);
        assertThat(overview.get("onTimePct").decimalValue()).isEqualByComparingTo("50.0");

        JsonNode dash = read(getAs(admin, "/api/dashboard?plantId=" + plantId + "&from=" + today + "&to=" + today));
        assertThat(dash.at("/maintenance/pmOverdue").asInt()).isEqualTo(1);
        assertThat(dash.at("/maintenance/pmOnTimePct").decimalValue()).isEqualByComparingTo("50.0");
        JsonNode pm = null;
        for (JsonNode a : dash.get("attention")) if (a.get("kind").asText().equals("PM_OVERDUE")) pm = a;
        assertThat(pm).isNotNull();
        assertThat(pm.get("what").asText()).isEqualTo("Machine KILN-1: Check rollers");
        assertThat(pm.get("boxes").asInt()).isEqualTo(9);
    }

    @Test
    void whoMayDoWhat() throws Exception {
        long id = create(press1, "Clean die", today);
        long viewRole = createRole(admin, "PM viewer", "MAINTENANCE_VIEW");
        createUser(admin, "pm.view", "PmView@123", viewRole, plantId);
        String viewer = login("pm.view", "PmView@123");
        createUser(admin, "pm.fitter", "PmFitter@123", roleId("Maintenance in-charge"), plantId);
        String fitter = login("pm.fitter", "PmFitter@123");

        getAs(viewer, TASKS + "?plantId=" + plantId).andExpect(status().isOk());
        postAs(viewer, TASKS, task(List.of(press1), "x", "OTHER", 7, today)).andExpect(status().isForbidden());
        postAs(viewer, TASKS + "/" + id + "/done", Map.of("doneOn", today.toString())).andExpect(status().isForbidden());
        long job = read(postAs(fitter, TASKS + "/" + id + "/done", Map.of("doneOn", today.toString())).andExpect(status().isOk()))
                .at("/history/0/id").asLong();
        postAs(fitter, "/api/maintenance/pm-jobs/" + job + "/cancel", Map.of("reason", "x")).andExpect(status().isForbidden());

        long other = createPlant(admin, createCompany(admin, "PMCO2"), "PM-2");
        Map<String, Object> elsewhere = task(List.of(press1), "x", "OTHER", 7, today);
        elsewhere.put("plantId", other);
        postAs(admin, TASKS, elsewhere).andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Machine PRESS-1 is not in plant PM-2"));
        getAs(viewer, TASKS + "?plantId=" + other).andExpect(status().isForbidden());
    }

    // ------------------------------------------------------------------ helpers

    private Map<String, Object> task(List<Long> machines, String title, String kind, int everyDays, LocalDate nextDue) {
        return new HashMap<>(Map.of("plantId", plantId, "machineIds", machines, "title", title, "kind", kind,
                "everyDays", everyDays, "nextDue", nextDue.toString()));
    }

    private long create(long machineId, String title, LocalDate nextDue) throws Exception {
        return read(postAs(admin, TASKS, task(List.of(machineId), title, "INSPECTION", 7, nextDue))
                .andExpect(status().isCreated())).get(0).get("id").asLong();
    }

    private JsonNode list(String query) throws Exception {
        return read(getAs(admin, TASKS + "?plantId=" + plantId + query).andExpect(status().isOk()));
    }

    private BigDecimal stock() throws Exception {
        for (JsonNode row : read(getAs(admin, "/api/store/stock?plantId=" + plantId))) {
            if (row.get("materialCode").asText().equals("FLT-HYD")) return row.get("quantity").decimalValue();
        }
        return BigDecimal.ZERO;
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
