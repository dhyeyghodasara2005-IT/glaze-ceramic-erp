package com.ceramicerp.labour;

import com.ceramicerp.IntegrationTestBase;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Step 9a: workers come through labour contractors and are paid monthly (the plant, 25 Oct 2026). Supervisors mark
 * each day from the signed cards; the labour office adds up the month per contractor.
 * <p>
 * Days are marked two days ago (or in the month before), so none is "a day that hasn't started" whatever the time the
 * test runs.
 */
class LabourIntegrationTest extends IntegrationTestBase {

    private static final String WORKERS = "/api/labour/workers";
    private static final String ATTENDANCE = "/api/labour/attendance";

    private final LocalDate day = LocalDate.now(ZoneId.of("Asia/Kolkata")).minusDays(2);

    @Autowired
    private AttendanceService attendanceService;

    private String admin;
    private long plantId, contractor, shiftA;

    @BeforeEach
    void setUp() throws Exception {
        admin = adminToken();
        plantId = createPlant(admin, createCompany(admin, "LBCO"), "LB-1");
        contractor = party("CONTRACTOR", "Shree Labour Services");
        for (JsonNode s : read(getAs(admin, "/api/masters/shifts"))) {
            if (s.get("code").asText().equals("A")) shiftA = s.get("id").asLong();
        }
    }

    @Test
    void workersGetCodesAndComeThroughAContractor() throws Exception {
        JsonNode first = read(postAs(admin, WORKERS, worker(null, "Ramesh Parmar", "PRESS", contractor)).andExpect(status().isCreated()));
        assertThat(first.get("code").asText()).isEqualTo("W-0001");
        assertThat(first.at("/contractor/name").asText()).isEqualTo("Shree Labour Services");
        assertThat(read(postAs(admin, WORKERS, worker(null, "Suresh Koli", "KILN", null)).andExpect(status().isCreated()))
                .get("code").asText()).isEqualTo("W-0002");
        assertThat(read(postAs(admin, WORKERS, worker("c-17", "Mahesh Vala", "PACKING", contractor)).andExpect(status().isCreated()))
                .get("code").asText()).isEqualTo("C-17");

        postAs(admin, WORKERS, worker("C-17", "x", "PRESS", null)).andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Worker code C-17 already exists in plant LB-1"));
        long supplier = party("SUPPLIER", "Morbi Clay Co");
        postAs(admin, WORKERS, worker(null, "x", "PRESS", supplier)).andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Morbi Clay Co is a supplier, not a labour contractor"));
        Map<String, Object> early = worker(null, "x", "PRESS", null);
        early.put("joinedOn", LocalDate.now().plusDays(3).toString());
        postAs(admin, WORKERS, early).andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("The joining date can't be in the future"));

        // one fingerprint-machine number, one worker
        Map<String, Object> punches = worker(null, "Kishor Dabhi", "GLAZE", contractor);
        punches.put("punch", "FINGERPRINT");
        punches.put("deviceId", "117");
        postAs(admin, WORKERS, punches).andExpect(status().isCreated());
        punches.put("name", "Someone else");
        postAs(admin, WORKERS, punches).andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Fingerprint number 117 is already given to another worker"));

        // filters
        // in the order the tile moves through the plant, not A to Z: Press, Glaze, Kiln, Packing
        List<String> names = new ArrayList<>();
        read(getAs(admin, WORKERS + "?plantId=" + plantId)).forEach(w -> names.add(w.get("name").asText()));
        assertThat(names).containsExactly("Ramesh Parmar", "Kishor Dabhi", "Suresh Koli", "Mahesh Vala");
        assertThat(read(getAs(admin, WORKERS + "?plantId=" + plantId + "&contractorId=" + contractor)).size()).isEqualTo(3);
        assertThat(read(getAs(admin, WORKERS + "?plantId=" + plantId + "&department=KILN")).get(0).get("name").asText())
                .isEqualTo("Suresh Koli");
        assertThat(read(getAs(admin, WORKERS + "?plantId=" + plantId + "&search=117")).get(0).get("name").asText())
                .isEqualTo("Kishor Dabhi");
        // the contractor dropdown has labour contractors only
        JsonNode contractors = read(getAs(admin, "/api/labour/contractors"));
        assertThat(contractors.findValuesAsText("name")).contains("Shree Labour Services").doesNotContain("Morbi Clay Co");

        // a worker who leaves: marked inactive, kept
        Map<String, Object> left = worker("W-0002", "Suresh Koli", "KILN", null);
        left.put("active", false);
        putAs(admin, WORKERS + "/" + read(getAs(admin, WORKERS + "?plantId=" + plantId + "&department=KILN")).get(0).get("id").asLong(), left)
                .andExpect(status().isOk()).andExpect(jsonPath("$.active").value(false));
        assertThat(read(getAs(admin, WORKERS + "?plantId=" + plantId + "&active=true")).size()).isEqualTo(3);

        getAs(admin, "/api/search?q=kishor").andExpect(jsonPath("$[*].type", hasItem("worker")));
        getAs(admin, "/api/search?q=shree").andExpect(jsonPath("$[*].type", hasItem("contractor")));
    }

    @Test
    void onlyTheMarksSentChange() throws Exception {
        long ramesh = workerId(null, "Ramesh Parmar", "PRESS", contractor);
        long suresh = workerId(null, "Suresh Koli", "PRESS", contractor);
        JsonNode sheet = read(getAs(admin, ATTENDANCE + "?plantId=" + plantId + "&date=" + day));
        assertThat(sheet.get("rows").size()).isEqualTo(2);
        assertThat(sheet.at("/counts/notMarked").asInt()).isEqualTo(2);
        assertThat(sheet.get("shifts").findValuesAsText("code")).contains("A", "B");
        assertThat(sheet.toString()).doesNotContain("dayRate");   // a supervisor signs for days, not money

        sheet = save(day, shiftA, line(ramesh, "PRESENT", "2"));
        assertThat(sheet.at("/counts/present").asInt()).isEqualTo(1);
        JsonNode mark = row(sheet, ramesh).get("mark");
        assertThat(mark.get("otHours").decimalValue()).isEqualByComparingTo("2");
        assertThat(mark.get("shiftCode").asText()).isEqualTo("A");
        assertThat(mark.get("by").asText()).isEqualTo(adminUsername);
        assertThat(mark.get("source").asText()).isEqualTo("CARD");

        // another supervisor marks their own worker: Ramesh's mark stays as it was
        long role = createRole(admin, "Labour clerk test", "ATTENDANCE_ENTRY");
        createUser(admin, "press.sup", "Press@1234", role, plantId);
        String supervisor = login("press.sup", "Press@1234");
        sheet = read(postAs(supervisor, ATTENDANCE, request(day, null, line(suresh, "HALF", null))).andExpect(status().isOk()));
        assertThat(row(sheet, ramesh).at("/mark/by").asText()).isEqualTo(adminUsername);
        assertThat(row(sheet, suresh).at("/mark/by").asText()).isEqualTo("press.sup");
        assertThat(sheet.at("/counts/half").asInt()).isEqualTo(1);

        // changed, then taken back
        sheet = save(day, shiftA, line(ramesh, "ABSENT", null));
        assertThat(row(sheet, ramesh).at("/mark/status").asText()).isEqualTo("ABSENT");
        sheet = save(day, shiftA, line(ramesh, null, null));
        assertThat(row(sheet, ramesh).has("mark")).isFalse();
        assertThat(sheet.at("/counts/notMarked").asInt()).isEqualTo(1);

        // what goes wrong when marking
        postAs(admin, ATTENDANCE, request(day, null, line(ramesh, "ABSENT", "3"))).andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("No overtime on a day Ramesh Parmar didn't work"));
        postAs(admin, ATTENDANCE, request(day.plusDays(5), null, line(ramesh, "PRESENT", null))).andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Attendance can't be marked for a day that hasn't started"));
        postAs(admin, ATTENDANCE, request(day, null, line(ramesh, "PRESENT", null), line(ramesh, "HALF", null)))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.message").value("A worker is in the list twice"));
        Map<String, Object> late = worker(null, "Naresh Late", "PRESS", contractor);
        late.put("joinedOn", day.plusDays(1).toString());
        long naresh = read(postAs(admin, WORKERS, late).andExpect(status().isCreated())).get("id").asLong();
        postAs(admin, ATTENDANCE, request(day, null, line(naresh, "PRESENT", null))).andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(startsWith("Naresh Late (W-0003) joined on ")));
        // and he isn't on that day's sheet
        assertThat(read(getAs(admin, ATTENDANCE + "?plantId=" + plantId + "&date=" + day)).get("rows").size()).isEqualTo(2);
    }

    @Test
    void theMonthPerContractor() throws Exception {
        YearMonth last = YearMonth.now(ZoneId.of("Asia/Kolkata")).minusMonths(1);
        long ramesh = workerId(rated(worker(null, "Ramesh Parmar", "PRESS", contractor), "500", "70"));
        long suresh = workerId(null, "Suresh Koli", "KILN", contractor);                      // no rate yet
        long office = workerId(rated(worker(null, "Office Peon", "OFFICE", null), "600", null)); // own rolls
        save(last.atDay(1), null, line(ramesh, "PRESENT", "4"), line(suresh, "PRESENT", null), line(office, "PRESENT", null));
        save(last.atDay(2), null, line(ramesh, "PRESENT", null), line(suresh, "PRESENT", null), line(office, "LEAVE", null));
        save(last.atDay(3), null, line(ramesh, "PRESENT", null));
        save(last.atDay(4), null, line(ramesh, "HALF", null));
        save(last.atDay(5), null, line(ramesh, "ABSENT", null));

        JsonNode m = read(getAs(admin, "/api/labour/month?plantId=" + plantId + "&month=" + last).andExpect(status().isOk()));
        assertThat(m.get("to").asText()).isEqualTo(last.atEndOfMonth().toString());
        JsonNode shree = m.get("contractors").get(0);
        assertThat(shree.get("name").asText()).isEqualTo("Shree Labour Services");
        assertThat(shree.get("workers").asInt()).isEqualTo(2);
        assertThat(shree.get("manDays").decimalValue()).isEqualByComparingTo("5.5");     // 3.5 + 2
        assertThat(shree.get("amount").decimalValue()).isEqualByComparingTo("2030");     // 3.5 x 500 + 4 h x 70
        assertThat(shree.get("unpriced").asInt()).isEqualTo(1);                          // Suresh: no rate
        JsonNode r = shree.get("rows").get(0);
        assertThat(r.get("name").asText()).isEqualTo("Ramesh Parmar");
        assertThat(List.of(r.get("present").asInt(), r.get("half").asInt(), r.get("absent").asInt())).containsExactly(3, 1, 1);

        JsonNode own = m.get("contractors").get(1);                                      // the plant's own rolls come last
        assertThat(own.has("name")).isFalse();
        assertThat(own.get("manDays").decimalValue()).isEqualByComparingTo("1");        // leave isn't a working day
        assertThat(own.get("amount").decimalValue()).isEqualByComparingTo("600");
        assertThat(m.get("amount").decimalValue()).isEqualByComparingTo("2630");
        assertThat(m.get("otHours").decimalValue()).isEqualByComparingTo("4");
        assertThat(m.get("departments").findValuesAsText("department")).containsExactly("PRESS", "KILN", "OFFICE");

        getAs(admin, "/api/labour/month?plantId=" + plantId + "&month=" + YearMonth.now().plusMonths(2))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.message").value("That month hasn't started"));
    }

    @Test
    void supervisorsMarkDaysButDontSeeRates() throws Exception {
        long ramesh = workerId(rated(worker(null, "Ramesh Parmar", "PRESS", contractor), "500", "70"));
        long role = createRole(admin, "Labour clerk test 2", "ATTENDANCE_ENTRY");
        createUser(admin, "kiln.sup", "Kiln@1234", role, plantId);
        String supervisor = login("kiln.sup", "Kiln@1234");
        getAs(supervisor, ATTENDANCE + "?plantId=" + plantId).andExpect(status().isOk());
        getAs(supervisor, WORKERS + "?plantId=" + plantId).andExpect(status().isForbidden());
        getAs(supervisor, "/api/labour/month?plantId=" + plantId).andExpect(status().isForbidden());
        postAs(supervisor, WORKERS, worker(null, "x", "KILN", null)).andExpect(status().isForbidden());

        long viewRole = createRole(admin, "Labour viewer test", "LABOUR_VIEW");
        createUser(admin, "labour.view", "View@1234", viewRole, plantId);
        String viewer = login("labour.view", "View@1234");
        getAs(viewer, "/api/labour/month?plantId=" + plantId).andExpect(status().isOk());
        postAs(viewer, ATTENDANCE, request(day, null, line(ramesh, "PRESENT", null))).andExpect(status().isForbidden());

        // a supervisor of another plant can't mark this plant's workers
        long otherPlant = createPlant(admin, createCompany(admin, "LBCO2"), "LB-2");
        createUser(admin, "other.sup", "Other@1234", role, otherPlant);
        String other = login("other.sup", "Other@1234");
        postAs(other, ATTENDANCE, request(day, null, line(ramesh, "PRESENT", null))).andExpect(status().isForbidden());
        Map<String, Object> wrongPlant = request(day, null, line(ramesh, "PRESENT", null));
        wrongPlant.put("plantId", otherPlant);
        postAs(admin, ATTENDANCE, wrongPlant).andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Worker W-0001 is not in plant LB-2"));
    }

    @Test
    void theDashboardAsksForYesterdaysAttendance() throws Exception {
        LocalDate yesterday = attendanceService.yesterday();
        long ramesh = workerId(null, "Ramesh Parmar", "PRESS", contractor);
        workerId(null, "Suresh Koli", "KILN", contractor);
        String url = "/api/dashboard?plantId=" + plantId + "&from=" + yesterday + "&to=" + yesterday;
        JsonNode missing = attention(read(getAs(admin, url).andExpect(status().isOk())), "ATTENDANCE_MISSING");
        assertThat(missing.get("count").asInt()).isEqualTo(2);
        assertThat(missing.get("what").asText()).isEqualTo("PRESS,KILN");

        save(yesterday, null, line(ramesh, "PRESENT", null));
        missing = attention(read(getAs(admin, url)), "ATTENDANCE_MISSING");
        assertThat(missing.get("count").asInt()).isEqualTo(1);
        assertThat(missing.get("what").asText()).isEqualTo("KILN");
    }

    // ------------------------------------------------------------------ helpers

    private long party(String type, String name) throws Exception {
        return read(postAs(admin, "/api/parties", Map.of("partyType", type, "name", name, "gstRegistration", "UNREGISTERED"))
                .andExpect(status().isCreated())).get("id").asLong();
    }

    private Map<String, Object> worker(String code, String name, String department, Long contractorId) {
        Map<String, Object> body = new HashMap<>();
        body.put("plantId", plantId);
        body.put("code", code);
        body.put("name", name);
        body.put("department", department);
        body.put("contractorId", contractorId);
        body.put("punch", "CARD");
        return body;
    }

    private static Map<String, Object> rated(Map<String, Object> body, String dayRate, String otRate) {
        body.put("dayRate", dayRate);
        body.put("otRate", otRate);
        return body;
    }

    private long workerId(String code, String name, String department, Long contractorId) throws Exception {
        return workerId(worker(code, name, department, contractorId));
    }

    private long workerId(Map<String, Object> body) throws Exception {
        return read(postAs(admin, WORKERS, body).andExpect(status().isCreated())).get("id").asLong();
    }

    private static Map<String, Object> line(long workerId, String status, String otHours) {
        Map<String, Object> line = new HashMap<>();
        line.put("workerId", workerId);
        line.put("status", status);
        line.put("otHours", otHours);
        return line;
    }

    @SafeVarargs
    private Map<String, Object> request(LocalDate date, Long shiftId, Map<String, Object>... lines) {
        Map<String, Object> body = new HashMap<>();
        body.put("plantId", plantId);
        body.put("date", date.toString());
        body.put("shiftId", shiftId);
        body.put("lines", new ArrayList<>(List.of(lines)));
        return body;
    }

    @SafeVarargs
    private JsonNode save(LocalDate date, Long shiftId, Map<String, Object>... lines) throws Exception {
        return read(postAs(admin, ATTENDANCE, request(date, shiftId, lines)).andExpect(status().isOk()));
    }

    private static JsonNode row(JsonNode sheet, long workerId) {
        for (JsonNode r : sheet.get("rows")) {
            if (r.get("workerId").asLong() == workerId) return r;
        }
        throw new AssertionError("Worker " + workerId + " not on the sheet");
    }

    private static JsonNode attention(JsonNode d, String kind) {
        for (JsonNode a : d.get("attention")) {
            if (a.get("kind").asText().equals(kind)) return a;
        }
        throw new AssertionError("No " + kind + " in " + d.get("attention"));
    }
}
