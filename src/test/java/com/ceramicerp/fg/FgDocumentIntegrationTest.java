package com.ceramicerp.fg;

import com.ceramicerp.IntegrationTestBase;
import com.ceramicerp.numbering.DocumentNumberService;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Step 5b: the warehouse's documents - opening stock, breakage and regrading. */
class FgDocumentIntegrationTest extends IntegrationTestBase {

    private static final String DOCS = "/api/fg/documents";
    private final LocalDate today = LocalDate.now(ZoneId.of("Asia/Kolkata"));
    private final String fy = DocumentNumberService.financialYear(today);

    private String admin;
    private long plantId, design, premium, standard;
    private long packEntryId;
    /** 700 Premium 01 C1 boxes at A1, from a posted pack entry */
    private long a1Stock;
    private final Map<String, Long> pillars = new HashMap<>();

    @BeforeEach
    void setUp() throws Exception {
        admin = adminToken();
        long companyId = createCompany(admin, "FGDCO");
        plantId = createPlant(admin, companyId, "FG-D");
        long press = createMachine("PRESS-1", "PRESS");
        long sorter = createMachine("SORT-1", "SORTING_LINE");
        long shiftA = idOf("shifts", "A");
        premium = idOf("grades", "PREMIUM");
        standard = idOf("grades", "STANDARD");
        design = read(postAs(admin, "/api/masters/designs", Map.of("code", "ARMANI-FG", "name", "Armani Grey",
                "categoryId", idOf("categories", "GVT"), "sizeId", idOf("sizes", "600X1200"),
                "finishId", idOf("finishes", "MATT"), "faces", 2)).andExpect(status().isCreated())).get("id").asLong();
        long lot = read(postAs(admin, "/api/production/lots", Map.of("plantId", plantId, "designId", design, "pressId", press,
                "openedDate", today.toString(), "shiftId", shiftA, "closeCurrent", false))
                .andExpect(status().isCreated())).get("id").asLong();
        long godown = read(postAs(admin, "/api/fg/godowns", Map.of("plantId", plantId, "code", "FG-1", "name", "FG"))
                .andExpect(status().isCreated())).get("id").asLong();
        read(postAs(admin, "/api/fg/godowns/" + godown + "/pillars", Map.of("prefix", "A", "from", 1, "to", 4)))
                .get("pillars").forEach(p -> pillars.put(p.get("code").asText(), p.get("id").asLong()));

        Map<String, Object> pack = new HashMap<>();
        pack.put("plantId", plantId);
        pack.put("lotId", lot);
        pack.put("prodDate", today.toString());
        pack.put("shiftId", shiftA);
        pack.put("machineId", sorter);
        pack.put("broken", 0);
        pack.put("lines", List.of(Map.of("gradeId", premium, "shade", "01", "caliber", "C1", "boxes", 700,
                "pillarId", pillars.get("A1"))));
        packEntryId = read(postAs(admin, "/api/production/pack-entries", pack).andExpect(status().isCreated())).get("id").asLong();
        postAs(admin, "/api/production/pack-entries/" + packEntryId + "/post", Map.of()).andExpect(status().isOk());
        a1Stock = read(getAs(admin, "/api/fg/stock?plantId=" + plantId)).at("/0/id").asLong();
    }

    @Test
    void openingStockComesInWithTheBatchPrintedOnTheBoxes() throws Exception {
        JsonNode doc = post(document("OPENING", opening("A2", " b-2309 ", premium, "05", "c2", 120)));
        assertThat(doc.get("docNo").asText()).isEqualTo("FG-D/OS/" + fy + "/0001");

        JsonNode row = stockAt("A2");
        assertThat(row.get("batchCode").asText()).isEqualTo("B-2309");
        assertThat(row.hasNonNull("lotId")).isFalse();                 // packed before the ERP: no lot
        assertThat(row.get("caliber").asText()).isEqualTo("C2");
        assertThat(row.get("boxes").asInt()).isEqualTo(120);
        assertThat(read(getAs(admin, "/api/fg/stock/" + row.get("id").asLong())).at("/moves/0/source").asText())
                .isEqualTo("OPENING");

        postAs(admin, DOCS, document("OPENING", opening("A2", "B-1", idOf("grades", "REJECT"), "05", "C2", 1)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Reject can't be in stock (row 1). Write broken boxes off as breakage."));
        Map<String, Object> noBatch = opening("A2", " ", premium, "05", "C2", 1);
        postAs(admin, DOCS, document("OPENING", noBatch))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Enter the batch printed on the boxes (row 1)"));
    }

    @Test
    void breakageWritesBoxesOff() throws Exception {
        JsonNode doc = post(document("BREAKAGE", breakage(a1Stock, 30, "HANDLING")));
        assertThat(doc.get("docNo").asText()).isEqualTo("FG-D/BK/" + fy + "/0001");
        assertThat(stockAt("A1").get("boxes").asInt()).isEqualTo(670);
        JsonNode ledger = read(getAs(admin, "/api/fg/stock/" + a1Stock));
        assertThat(ledger.at("/moves/1/source").asText()).isEqualTo("BREAKAGE");
        assertThat(ledger.at("/moves/1/boxes").asInt()).isEqualTo(-30);

        postAs(admin, DOCS, document("BREAKAGE", breakage(a1Stock, 671, "LOADING")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Only 670 boxes of Armani Grey Premium 01/C1 at A1 (row 1)"));
        postAs(admin, DOCS, document("BREAKAGE", breakage(a1Stock, 5, null)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Choose why the boxes broke (row 1)"));
    }

    @Test
    void aDraftIsCheckedAgainWhenPosted() throws Exception {
        // Two rows of 400 from the same 700 boxes: each row alone is fine, together they are not.
        long id = read(postAs(admin, DOCS, document("BREAKAGE", breakage(a1Stock, 400, "STORAGE"), breakage(a1Stock, 400, "STORAGE")))
                .andExpect(status().isCreated())).get("id").asLong();
        postAs(admin, DOCS + "/" + id + "/post", Map.of())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Not enough Armani Grey Premium shade 01 caliber C1 ("
                        + read(getAs(admin, "/api/fg/stock/" + a1Stock)).at("/stock/batchCode").asText()
                        + ") at pillar A1: 700 boxes there, 800 needed"));
        assertThat(stockAt("A1").get("boxes").asInt()).isEqualTo(700);     // nothing moved
    }

    @Test
    void regradingMovesBoxesToTheNewGradeShadeCaliberOrPillar() throws Exception {
        JsonNode doc = post(document("REGRADE", regrade(a1Stock, 100, standard, "01", "C1", "A3")));
        assertThat(doc.get("docNo").asText()).isEqualTo("FG-D/RG/" + fy + "/0001");
        assertThat(doc.at("/lines/0/from/pillarCode").asText()).isEqualTo("A1");

        assertThat(stockAt("A1").get("boxes").asInt()).isEqualTo(600);
        JsonNode moved = stockAt("A3");
        assertThat(moved.get("gradeCode").asText()).isEqualTo("STANDARD");
        assertThat(moved.get("boxes").asInt()).isEqualTo(100);
        assertThat(moved.get("batchCode").asText()).isEqualTo(stockAt("A1").get("batchCode").asText());   // same batch
        assertThat(moved.get("lotId").asLong()).isEqualTo(stockAt("A1").get("lotId").asLong());            // same lot

        postAs(admin, DOCS, document("REGRADE", regrade(a1Stock, 10, premium, "01", "c1", "A1")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Nothing changes (row 1): choose a new grade, shade, caliber or pillar"));
    }

    @Test
    void cancellingPutsTheBoxesBackInTheRightOrder() throws Exception {
        long regradeId = post(document("REGRADE", regrade(a1Stock, 100, standard, "01", "C1", "A3"))).get("id").asLong();

        // The pack entry can't be cancelled now: 100 of its 700 boxes have moved on.
        postAs(admin, "/api/production/pack-entries/" + packEntryId + "/cancel", Map.of("reason", "Wrong lot"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("600 boxes there, 700 needed")));

        // Undo the regrade first, then the pack entry.
        postAs(admin, DOCS + "/" + regradeId + "/cancel", Map.of("reason", "Re-checked again"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));
        assertThat(stockAt("A1").get("boxes").asInt()).isEqualTo(700);
        postAs(admin, "/api/production/pack-entries/" + packEntryId + "/cancel", Map.of("reason", "Wrong lot"))
                .andExpect(status().isOk());
        assertThat(read(getAs(admin, "/api/fg/stock?plantId=" + plantId)).size()).isZero();
    }

    @Test
    void theWarehouseNeedsItsOwnPermissions() throws Exception {
        createUser(admin, "fg.keep", "password-1", createRole(admin, "FG keep", "FG_VIEW", "FG_ENTRY"), plantId);
        createUser(admin, "fg.boss", "password-1", createRole(admin, "FG boss", "FG_VIEW", "FG_CANCEL"), plantId);
        String keeper = login("fg.keep", "password-1");
        String boss = login("fg.boss", "password-1");

        // The keeper has no master rights, but gets the dropdowns through the FG lookups
        getAs(keeper, "/api/fg/lookups/designs").andExpect(status().isOk()).andExpect(jsonPath("$[*].code", hasItem("ARMANI-FG")));
        getAs(keeper, "/api/fg/lookups/grades").andExpect(status().isOk()).andExpect(jsonPath("$[0].code").value("PREMIUM"));
        getAs(keeper, "/api/fg/lookups/pillars?plantId=" + plantId).andExpect(status().isOk());

        long id = read(postAs(keeper, DOCS, document("BREAKAGE", breakage(a1Stock, 5, "OTHER"))).andExpect(status().isCreated()))
                .get("id").asLong();
        postAs(keeper, DOCS + "/" + id + "/post", Map.of()).andExpect(status().isOk());
        postAs(keeper, DOCS + "/" + id + "/cancel", Map.of("reason", "x")).andExpect(status().isForbidden());
        postAs(boss, DOCS, document("BREAKAGE", breakage(a1Stock, 5, "OTHER"))).andExpect(status().isForbidden());
        postAs(boss, DOCS + "/" + id + "/cancel", Map.of("reason", "Counted wrong")).andExpect(status().isOk());
    }

    @Test
    void warehouseDocumentsAreFoundByNumber() throws Exception {
        post(document("BREAKAGE", breakage(a1Stock, 5, "LOADING")));
        getAs(admin, "/api/search?q=FG-D/BK").andExpect(jsonPath("$[*].type", hasItem("fg-breakage")));
    }

    // ------------------------------------------------------------------ helpers

    @SafeVarargs
    private Map<String, Object> document(String type, Map<String, Object>... lines) {
        return Map.of("plantId", plantId, "docType", type, "docDate", today.toString(), "lines", List.of(lines));
    }

    private Map<String, Object> opening(String pillar, String batch, long gradeId, String shade, String caliber, int boxes) {
        return Map.of("pillarId", pillars.get(pillar), "designId", design, "batchCode", batch, "gradeId", gradeId,
                "shade", shade, "caliber", caliber, "boxes", boxes);
    }

    private static Map<String, Object> breakage(long stockId, int boxes, String reason) {
        Map<String, Object> line = new HashMap<>();
        line.put("fromStockId", stockId);
        line.put("boxes", boxes);
        line.put("reason", reason);
        return line;
    }

    private Map<String, Object> regrade(long stockId, int boxes, long gradeId, String shade, String caliber, String pillar) {
        return Map.of("fromStockId", stockId, "boxes", boxes, "gradeId", gradeId, "shade", shade, "caliber", caliber,
                "pillarId", pillars.get(pillar));
    }

    private JsonNode post(Map<String, Object> body) throws Exception {
        long id = read(postAs(admin, DOCS, body).andExpect(status().isCreated())).get("id").asLong();
        return read(postAs(admin, DOCS + "/" + id + "/post", Map.of()).andExpect(status().isOk()));
    }

    /** The first stock row at a pillar. */
    private JsonNode stockAt(String pillar) throws Exception {
        for (JsonNode r : read(getAs(admin, "/api/fg/stock?plantId=" + plantId))) {
            if (r.get("pillarCode").asText().equals(pillar)) return r;
        }
        throw new AssertionError("No stock at " + pillar);
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
