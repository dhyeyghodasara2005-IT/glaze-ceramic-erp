package com.ceramicerp.lab;

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
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Step 8d: raw material tested when it arrives (the plant's answer, 25 Oct 2026). Red clay gets three tests with
 * ranges (moisture at most 8%, residue at most 2%, water absorption with no range); grease gets none, so it never
 * waits for the lab.
 */
class LabIntegrationTest extends IntegrationTestBase {

    private static final String DOCS = "/api/store/documents";
    private final LocalDate today = LocalDate.now(ZoneId.of("Asia/Kolkata"));
    private final String fy = DocumentNumberService.financialYear(today);

    private String admin;
    private long plantId, yard, clay, grease, supplier;
    private long moist, residue, absorption;

    @BeforeEach
    void setUp() throws Exception {
        admin = adminToken();
        plantId = createPlant(admin, createCompany(admin, "LABCO"), "LAB-1");
        yard = read(postAs(admin, "/api/store/locations", Map.of("plantId", plantId, "code", "YARD", "name", "Clay yard"))
                .andExpect(status().isCreated())).get("id").asLong();
        clay = material("RED-CLAY", "Red clay", "BODY", "TON");
        grease = material("GREASE-EP2", "EP2 grease", "SPARE", "KG");
        supplier = read(postAs(admin, "/api/parties", Map.of("partyType", "SUPPLIER", "name", "Thangadh Minerals",
                "gstRegistration", "UNREGISTERED")).andExpect(status().isCreated())).get("id").asLong();
        JsonNode tests = read(putAs(admin, params(clay), Map.of("parameters", List.of(
                param(null, "moist", "Moisture", "%", null, "8"),
                param(null, "RES63", "Residue on 63 µm", "%", null, "2"),
                param(null, "WA", "Water absorption", "%", null, null)))).andExpect(status().isOk()));
        moist = tests.get(0).get("id").asLong();
        residue = tests.get(1).get("id").asLong();
        absorption = tests.get(2).get("id").asLong();
        assertThat(tests.get(0).get("code").asText()).isEqualTo("MOIST");
    }

    @Test
    void testsAreSetUpPerMaterial() throws Exception {
        JsonNode materials = read(getAs(admin, "/api/lab/materials"));
        assertThat(row(materials, "RED-CLAY").get("tests").asInt()).isEqualTo(3);
        assertThat(row(materials, "GREASE-EP2").get("tests").asInt()).isZero();

        // reordered, one dropped: it is switched off, not deleted
        read(putAs(admin, params(clay), Map.of("parameters", List.of(
                param(residue, "RES63", "Residue on 63 µm", "%", null, "2.5"),
                param(moist, "MOIST", "Moisture", "%", null, "8")))).andExpect(status().isOk()));
        JsonNode all = read(getAs(admin, params(clay)));
        assertThat(all.get(0).get("code").asText()).isEqualTo("RES63");
        assertThat(all.get(0).get("maxValue").decimalValue()).isEqualByComparingTo("2.5");
        assertThat(find(all, "WA").get("active").asBoolean()).isFalse();
        // added again by its code: the same test comes back on
        JsonNode back = read(putAs(admin, params(clay), Map.of("parameters", List.of(
                param(residue, "RES63", "Residue on 63 µm", "%", null, "2.5"),
                param(moist, "MOIST", "Moisture", "%", null, "8"),
                param(null, "WA", "Water absorption", "%", null, "0.5")))).andExpect(status().isOk()));
        assertThat(find(back, "WA").get("id").asLong()).isEqualTo(absorption);
        assertThat(find(back, "WA").get("active").asBoolean()).isTrue();

        putAs(admin, params(clay), Map.of("parameters", List.of(param(null, "X", "Strength", null, "5", "1"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Strength: the lowest acceptable value is above the highest"));
        putAs(admin, params(clay), Map.of("parameters", List.of(param(null, "X", "A", null, null, null),
                param(null, "x", "B", null, null, null))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Test code X is used twice"));
    }

    @Test
    void aGrnWaitsForTheLabThenTakesTheVerdict() throws Exception {
        long grn = grn(line(clay, "30", "1200"), line(grease, "10", "180"));
        JsonNode waiting = read(getAs(admin, "/api/lab/waiting?plantId=" + plantId));
        assertThat(waiting).hasSize(1);                                       // the clay, not the grease
        assertThat(waiting.get(0).get("materialCode").asText()).isEqualTo("RED-CLAY");
        assertThat(waiting.get(0).get("supplierName").asText()).isEqualTo("Thangadh Minerals");
        assertThat(waiting.get(0).get("receivedQty").decimalValue()).isEqualByComparingTo("30");

        postAs(admin, DOCS + "/" + grn + "/post", Map.of())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Line 1: the lab hasn't tested Red clay yet. The GRN waits for its result"));

        JsonNode test = read(postAs(admin, "/api/lab/tests", result(grn, clay, "ACCEPTED",
                Map.of(moist, "6.5", residue, "1.2"))).andExpect(status().isOk()));
        assertThat(test.get("testNo").asText()).isEqualTo("LAB-1/LAB/" + fy + "/0001");
        assertThat(test.get("outOfRange").asInt()).isZero();
        assertThat(test.get("editable").asBoolean()).isTrue();
        assertThat(test.at("/results/0/withinRange").asBoolean()).isTrue();
        // the moisture the lab found goes on the GRN line
        JsonNode doc = read(getAs(admin, DOCS + "/" + grn));
        assertThat(doc.at("/lines/0/moisturePct").decimalValue()).isEqualByComparingTo("6.5");
        assertThat(read(getAs(admin, "/api/lab/waiting?plantId=" + plantId))).isEmpty();
        assertThat(read(getAs(admin, "/api/lab/grn/" + grn)).at("/0/verdict").asText()).isEqualTo("ACCEPTED");

        postAs(admin, DOCS + "/" + grn + "/post", Map.of()).andExpect(status().isOk());
        assertThat(stock("RED-CLAY")).isEqualByComparingTo("30");
        postAs(admin, "/api/lab/tests", result(grn, clay, "REJECTED", Map.of(moist, "6.5")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(startsWith("GRN LAB-1/GRN/")));
        assertThat(read(getAs(admin, "/api/lab/tests/" + test.get("id").asLong())).get("editable").asBoolean()).isFalse();
        getAs(admin, "/api/search?q=LAB-1/LAB").andExpect(jsonPath("$[0].type").value("lab-test"));
    }

    @Test
    void aRejectedMaterialNeverEntersStock() throws Exception {
        long grn = grn(line(clay, "25", "1200"));
        JsonNode test = read(postAs(admin, "/api/lab/tests", result(grn, clay, "REJECTED",
                Map.of(moist, "7", residue, "3.5", absorption, "0.4"))).andExpect(status().isOk()));
        assertThat(test.get("outOfRange").asInt()).isEqualTo(1);                // residue 3.5 > 2
        JsonNode doc = read(getAs(admin, DOCS + "/" + grn));
        assertThat(doc.at("/lines/0/rejectedQty").decimalValue()).isEqualByComparingTo("25");
        assertThat(doc.at("/lines/0/quantity").decimalValue()).isEqualByComparingTo("0");

        // accepted on a second look: received again; rejected again
        postAs(admin, "/api/lab/tests", result(grn, clay, "ACCEPTED", Map.of(moist, "7"))).andExpect(status().isOk());
        assertThat(read(getAs(admin, DOCS + "/" + grn)).at("/lines/0/quantity").decimalValue()).isEqualByComparingTo("25");
        postAs(admin, "/api/lab/tests", result(grn, clay, "REJECTED", Map.of(residue, "3.5"))).andExpect(status().isOk());

        // the store keeper edits the GRN as if nothing was rejected: posting still rejects it in full
        Map<String, Object> edited = grnBody(line(clay, "25", "1200"));
        putAs(admin, DOCS + "/" + grn, edited).andExpect(status().isOk());
        JsonNode posted = read(postAs(admin, DOCS + "/" + grn + "/post", Map.of()).andExpect(status().isOk()));
        assertThat(posted.at("/lines/0/rejectedQty").decimalValue()).isEqualByComparingTo("25");
        assertThat(posted.get("totalValue").decimalValue()).isEqualByComparingTo("0");
        assertThat(stock("RED-CLAY")).isEqualByComparingTo("0");

        // a result taken back while the GRN is a draft: the material waits for the lab again
        long second = grn(line(clay, "20", "1200"));
        long id = read(postAs(admin, "/api/lab/tests", result(second, clay, "REJECTED", Map.of(residue, "4"))))
                .get("id").asLong();
        deleteAs(admin, "/api/lab/tests/" + id).andExpect(status().isNoContent());
        assertThat(read(getAs(admin, DOCS + "/" + second)).at("/lines/0/rejectedQty").decimalValue()).isEqualByComparingTo("0");
        assertThat(read(getAs(admin, "/api/lab/waiting?plantId=" + plantId))).hasSize(1);
    }

    @Test
    void mistakesTheLabCanMake() throws Exception {
        long grn = grn(line(clay, "10", "1200"), line(grease, "5", "180"));
        postAs(admin, "/api/lab/tests", result(grn, grease, "ACCEPTED", Map.of(moist, "1")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("EP2 grease has no lab tests set up"));
        Map<String, Object> future = result(grn, clay, "ACCEPTED", Map.of(moist, "5"));
        future.put("testedOn", today.plusDays(1).toString());
        postAs(admin, "/api/lab/tests", future).andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("It can't be tested in the future"));
        postAs(admin, "/api/lab/tests", result(grn, clay, "ACCEPTED", Map.of()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Enter at least one result"));
    }

    @Test
    void supplierQualityAndTheDashboard() throws Exception {
        long first = grn(line(clay, "30", "1200"));
        postAs(admin, "/api/lab/tests", result(first, clay, "ACCEPTED", Map.of(moist, "6"))).andExpect(status().isOk());
        long second = grn(line(clay, "28", "1200"));
        postAs(admin, "/api/lab/tests", result(second, clay, "REJECTED", Map.of(residue, "3"))).andExpect(status().isOk());
        grn(line(clay, "26", "1200"));                                          // a third truck, still waiting

        JsonNode o = read(getAs(admin, "/api/lab/overview?plantId=" + plantId));
        assertThat(o.get("waiting").asInt()).isEqualTo(1);
        assertThat(o.get("tested").asInt()).isEqualTo(2);
        assertThat(o.get("rejected").asInt()).isEqualTo(1);
        assertThat(o.get("rejectedPct").decimalValue()).isEqualByComparingTo("50.0");
        assertThat(o.at("/suppliers/0/name").asText()).isEqualTo("Thangadh Minerals");

        JsonNode page = read(getAs(admin, "/api/lab/tests?plantId=" + plantId + "&verdict=REJECTED"));
        assertThat(page.get("totalElements").asInt()).isEqualTo(1);
        assertThat(read(getAs(admin, "/api/lab/tests?plantId=" + plantId + "&search=thangadh")).get("totalElements").asInt())
                .isEqualTo(2);

        JsonNode dash = read(getAs(admin, "/api/dashboard?plantId=" + plantId + "&from=" + today + "&to=" + today));
        JsonNode lab = null;
        for (JsonNode a : dash.get("attention")) if (a.get("kind").asText().equals("LAB_WAITING")) lab = a;
        assertThat(lab).isNotNull();
        assertThat(lab.get("count").asInt()).isEqualTo(1);
        assertThat(lab.get("what").asText()).isEqualTo("Red clay");
    }

    @Test
    void whoMayDoWhat() throws Exception {
        long grn = grn(line(clay, "30", "1200"));
        createUser(admin, "lab.one", "LabOne@123", roleId("Lab chemist"), plantId);
        String chemist = login("lab.one", "LabOne@123");
        createUser(admin, "store.one", "StoreOne@123", roleId("Store keeper"), plantId);
        String keeper = login("store.one", "StoreOne@123");

        // the lab tests, but doesn't post GRNs or set tests up
        postAs(chemist, "/api/lab/tests", result(grn, clay, "ACCEPTED", Map.of(moist, "6"))).andExpect(status().isOk());
        postAs(chemist, DOCS + "/" + grn + "/post", Map.of()).andExpect(status().isForbidden());
        putAs(chemist, params(clay), Map.of("parameters", List.of())).andExpect(status().isForbidden());
        // the store keeper sees the verdict and posts, but doesn't test
        assertThat(read(getAs(keeper, "/api/lab/grn/" + grn)).at("/0/verdict").asText()).isEqualTo("ACCEPTED");
        postAs(keeper, "/api/lab/tests", result(grn, clay, "REJECTED", Map.of(moist, "6"))).andExpect(status().isForbidden());
        postAs(keeper, DOCS + "/" + grn + "/post", Map.of()).andExpect(status().isOk());

        long otherPlant = createPlant(admin, createCompany(admin, "LABCO2"), "LAB-2");
        getAs(chemist, "/api/lab/waiting?plantId=" + otherPlant).andExpect(status().isForbidden());
    }

    // ------------------------------------------------------------------ helpers

    private long material(String code, String name, String group, String unit) throws Exception {
        return read(postAs(admin, "/api/masters/materials", Map.of("code", code, "name", name,
                "groupId", idOf("material-groups", group), "unitId", idOf("units", unit), "hsnCode", "2508", "gstRate", 5))
                .andExpect(status().isCreated())).get("id").asLong();
    }

    private static String params(long materialId) {
        return "/api/lab/materials/" + materialId + "/parameters";
    }

    private static Map<String, Object> param(Long id, String code, String name, String unit, String min, String max) {
        Map<String, Object> p = new HashMap<>(Map.of("code", code, "name", name));
        if (id != null) p.put("id", id);
        if (unit != null) p.put("unit", unit);
        if (min != null) p.put("minValue", new BigDecimal(min));
        if (max != null) p.put("maxValue", new BigDecimal(max));
        return p;
    }

    private Map<String, Object> line(long materialId, String received, String rate) {
        return new HashMap<>(Map.of("materialId", materialId, "locationId", yard, "receivedQty", new BigDecimal(received),
                "rate", new BigDecimal(rate)));
    }

    @SafeVarargs
    private Map<String, Object> grnBody(Map<String, Object>... lines) {
        return new HashMap<>(Map.of("plantId", plantId, "docType", "GRN", "docDate", today.toString(), "supplierId", supplier,
                "vehicleNo", "GJ 36 T 4455", "lines", List.of(lines)));
    }

    @SafeVarargs
    private long grn(Map<String, Object>... lines) throws Exception {
        return read(postAs(admin, DOCS, grnBody(lines)).andExpect(status().isCreated())).get("id").asLong();
    }

    private Map<String, Object> result(long grn, long materialId, String verdict, Map<Long, String> values) {
        List<Map<String, Object>> results = new ArrayList<>();
        values.forEach((param, value) -> results.add(Map.of("parameterId", param, "value", new BigDecimal(value))));
        return new HashMap<>(Map.of("documentId", grn, "materialId", materialId, "testedOn", today.toString(),
                "testedBy", "Mehul", "verdict", verdict, "results", results));
    }

    private BigDecimal stock(String code) throws Exception {
        for (JsonNode row : read(getAs(admin, "/api/store/stock?plantId=" + plantId))) {
            if (row.get("materialCode").asText().equals(code)) return row.get("quantity").decimalValue();
        }
        return BigDecimal.ZERO;
    }

    private static JsonNode row(JsonNode list, String code) {
        for (JsonNode n : list) if (n.get("code").asText().equals(code)) return n;
        throw new AssertionError(code + " not found");
    }

    private static JsonNode find(JsonNode list, String code) {
        return row(list, code);
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
