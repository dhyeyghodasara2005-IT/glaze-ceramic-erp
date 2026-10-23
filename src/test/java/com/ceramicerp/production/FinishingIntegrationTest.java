package com.ceramicerp.production;

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
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Step 4d: quality checks in the cooling area, polishing, and sorting & packing. */
class FinishingIntegrationTest extends IntegrationTestBase {

    private static final String LINE = "/api/production/line-entries";
    private static final String PACK = "/api/production/pack-entries";
    private static final String CHECKS = "/api/production/lot-checks";
    private static final ZoneId INDIA = ZoneId.of("Asia/Kolkata");

    private final LocalDate today = LocalDate.now(INDIA);
    private final String fy = DocumentNumberService.financialYear(today);

    private String admin;
    private long plantId;
    private long kiln, polisher, sorter;
    private long shiftA;
    private long lotId;
    private long premium, standard, commercial;

    /**
     * A lot that has been through the kiln:
     * 2,000 pressed -> 1,950 good -> 1,900 glazed, 1,860 out -> 1,800 fired, 1,764 out (in cooling).
     */
    @BeforeEach
    void setUp() throws Exception {
        admin = adminToken();
        long companyId = createCompany(admin, "FINCO");
        plantId = createPlant(admin, companyId, "FIN-1");
        long press = createMachine("PRESS-1", "PRESS");
        long glazeLine = createMachine("GL-1", "GLAZE_LINE");
        kiln = createMachine("KILN-1", "KILN");
        polisher = createMachine("POL-1", "POLISHING_LINE");
        sorter = createMachine("SORT-1", "SORTING_LINE");
        long silo = createMachine("SILO-1", "SILO");
        shiftA = idOf("shifts", "A");
        premium = idOf("grades", "PREMIUM");
        standard = idOf("grades", "STANDARD");
        commercial = idOf("grades", "COMMERCIAL");
        long design = read(postAs(admin, "/api/masters/designs", Map.of("code", "STATUARIO-F", "name", "Statuario Gold",
                "categoryId", idOf("categories", "GVT"), "sizeId", idOf("sizes", "600X1200"),
                "finishId", idOf("finishes", "GLOSSY"), "faces", 4)).andExpect(status().isCreated())).get("id").asLong();

        lotId = read(postAs(admin, "/api/production/lots", Map.of("plantId", plantId, "designId", design, "pressId", press,
                "openedDate", today.toString(), "shiftId", shiftA, "closeCurrent", false))
                .andExpect(status().isCreated())).get("id").asLong();
        Map<String, Object> pressEntry = new HashMap<>();
        pressEntry.put("plantId", plantId);
        pressEntry.put("lotId", lotId);
        pressEntry.put("prodDate", today.toString());
        pressEntry.put("shiftId", shiftA);
        pressEntry.put("siloId", silo);
        pressEntry.put("pressedCount", 2000);
        pressEntry.put("pressRejects", 30);
        pressEntry.put("dryerRejects", 20);
        pressEntry.put("powderTon", 42);
        long id = read(postAs(admin, "/api/production/press-entries", pressEntry).andExpect(status().isCreated())).get("id").asLong();
        postAs(admin, "/api/production/press-entries/" + id + "/post", Map.of()).andExpect(status().isOk());
        postLine(lineEntry("GLAZE", glazeLine, 1900, 40));
        postLine(lineEntry("KILN", kiln, 1800, 36));
    }

    @Test
    void theFunnelRunsFromTheKilnToTheBox() throws Exception {
        Map<String, Object> polish = lineEntry("POLISH", polisher, 1700, 20);
        polish.put("powerKwh", 900);
        polish.put("gasScm", 50);       // not a polishing reading: dropped
        JsonNode polished = postLine(polish);
        assertThat(polished.get("entryNo").asText()).isEqualTo("FIN-1/PL/" + fy + "/0001");
        assertDecimal(polished.get("powerKwh"), "900");
        assertThat(polished.hasNonNull("gasScm")).isFalse();

        // 700 + 80 + 10 boxes of 2 pieces = 1,580 packed, plus 16 broken = 1,596 sorted
        JsonNode packed = postPack(packEntry(16, row(premium, "01", "C1", 700), row(standard, "01", "C1", 80),
                row(commercial, "02", "C2", 10)));
        assertThat(packed.get("entryNo").asText()).isEqualTo("FIN-1/PK/" + fy + "/0001");
        assertThat(packed.get("piecesPacked").asInt()).isEqualTo(1580);
        assertThat(packed.get("tilesSorted").asInt()).isEqualTo(1596);
        assertThat(packed.get("boxes").asInt()).isEqualTo(790);
        assertThat(packed.at("/lines/0/pieces").asInt()).isEqualTo(1400);
        assertDecimal(packed.get("packedSqm"), "1137.60");         // 1,580 x 0.72
        assertDecimal(packed.get("brokenPct"), "1.00");            // 16 of 1,596

        JsonNode lot = read(getAs(admin, "/api/production/lots/" + lotId));
        JsonNode flow = lot.get("flow");
        assertThat(flow.get("waitingPolish").asLong()).isEqualTo(64);    // 1,764 fired - 1,700 polished
        assertThat(flow.get("polishOut").asLong()).isEqualTo(1680);
        assertThat(flow.get("waitingSort").asLong()).isEqualTo(84);      // 1,680 - 1,596 sorted
        assertThat(flow.get("packed").asLong()).isEqualTo(1580);
        assertThat(flow.get("boxes").asLong()).isEqualTo(790);
        assertDecimal(flow.get("packedSqm"), "1137.60");
        assertDecimal(flow.get("packedYieldPct"), "79.00");              // 1,580 of 2,000 pressed
        assertDecimal(flow.get("yieldPct"), "88.20");                    // fired / pressed, as before

        assertThat(lot.get("packed").size()).isEqualTo(3);
        assertThat(lot.at("/packed/0/gradeCode").asText()).isEqualTo("PREMIUM");
        assertThat(lot.at("/packed/0/boxes").asLong()).isEqualTo(700);
        assertThat(lot.at("/quality/topGradeName").asText()).isEqualTo("Premium");
        assertDecimal(lot.at("/quality/actualTopPct"), "87.72");         // 1,400 of 1,596 sorted
        assertThat(lot.get("polishEntries").size()).isEqualTo(1);
        assertThat(lot.get("packEntries").size()).isEqualTo(1);
    }

    @Test
    void packRowsAreCheckedAndTheServerWorksOutThePieces() throws Exception {
        postAs(admin, PACK, packEntry(0, row(premium, "01", "C1", 10), row(premium, "01", "c1", 5)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Premium, shade 01, caliber C1 is entered twice. Put those boxes in one row."));
        postAs(admin, PACK, packEntry(0, row(idOf("grades", "REJECT"), "01", "C1", 10)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Reject tiles aren't boxed. Count them under Broken."));
        postAs(admin, PACK, packEntry(0, row(premium, "A 1", "C1", 10)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Shade \"A 1\" can only use letters, digits and . / - (up to 10)"));
        Map<String, Object> wrongMachine = packEntry(0, row(premium, "01", "C1", 10));
        wrongMachine.put("machineId", polisher);
        postAs(admin, PACK, wrongMachine)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("POL-1 is a polishing line, not a sorting line"));

        // Codes are tidied: " a3 " is saved as "A3". Pieces = boxes x 2 for 600 x 1200.
        JsonNode draft = read(postAs(admin, PACK, packEntry(4, row(premium, " a3 ", "c-2", 50)))
                .andExpect(status().isCreated()));
        assertThat(draft.at("/lines/0/shade").asText()).isEqualTo("A3");
        assertThat(draft.at("/lines/0/caliber").asText()).isEqualTo("C-2");
        assertThat(draft.at("/lines/0/pieces").asInt()).isEqualTo(100);
        assertThat(draft.get("tilesSorted").asInt()).isEqualTo(104);
        assertThat(draft.hasNonNull("entryNo")).isFalse();

        // Saving the draft again with the same row (and one more) works: the old rows go first.
        long id = draft.get("id").asLong();
        JsonNode edited = read(putAs(admin, PACK + "/" + id, packEntry(4, row(premium, "A3", "C-2", 60),
                row(standard, "A3", "C-2", 5))).andExpect(status().isOk()));
        assertThat(edited.get("lines").size()).isEqualTo(2);
        assertThat(edited.get("boxes").asInt()).isEqualTo(65);

        JsonNode codes = read(getAs(admin, PACK + "/codes?lotId=" + lotId));
        assertThat(codes.get("shades").get(0).asText()).isEqualTo("A3");
        assertThat(codes.get("calibers").get(0).asText()).isEqualTo("C-2");

        // The packing dropdown offers sellable grades only, best first - including the new Eco.
        List<String> grades = new ArrayList<>();
        read(getAs(admin, "/api/production/lookups/grades")).forEach(g -> grades.add(g.get("code").asText()));
        assertThat(grades).containsExactly("PREMIUM", "STANDARD", "COMMERCIAL", "ECO");
    }

    @Test
    void aQualityCheckIsFinalWhenSavedAndCanBeCancelled() throws Exception {
        Map<String, Object> body = check("WATCH");
        body.put("centreBendMm", -0.4);
        body.put("expectedTopPct", 85);
        JsonNode check = read(postAs(admin, CHECKS, body).andExpect(status().isCreated()));
        assertThat(check.get("checkNo").asText()).isEqualTo("FIN-1/QC/" + fy + "/0001");
        assertThat(check.get("status").asText()).isEqualTo("POSTED");
        assertDecimal(check.get("centreBendMm"), "-0.40");

        assertDecimal(read(getAs(admin, "/api/production/lots/" + lotId)).at("/quality/expectedTopPct"), "85");

        long id = check.get("id").asLong();
        postAs(admin, CHECKS + "/" + id + "/cancel", Map.of("reason", "Wrong lot"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));
        postAs(admin, CHECKS + "/" + id + "/cancel", Map.of("reason", "Again"))
                .andExpect(status().isConflict());
        assertThat(read(getAs(admin, "/api/production/lots/" + lotId)).at("/quality").hasNonNull("expectedTopPct")).isFalse();

        postAs(admin, CHECKS, check(null))
                .andExpect(status().isBadRequest());
    }

    @Test
    void eachFinishingJobNeedsItsOwnPermission() throws Exception {
        createUser(admin, "polish.t", "password-1", createRole(admin, "Polish T", "PRODUCTION_VIEW", "POLISH_ENTRY"), plantId);
        createUser(admin, "pack.t", "password-1", createRole(admin, "Pack T", "PRODUCTION_VIEW", "PACKING_ENTRY"), plantId);
        createUser(admin, "qc.t", "password-1", createRole(admin, "QC T", "PRODUCTION_VIEW", "QUALITY_CHECK"), plantId);
        createUser(admin, "kiln.t2", "password-1", createRole(admin, "Kiln T2", "PRODUCTION_VIEW", "KILN_ENTRY"), plantId);
        String polishSup = login("polish.t", "password-1");
        String packSup = login("pack.t", "password-1");
        String checker = login("qc.t", "password-1");
        String kilnSup = login("kiln.t2", "password-1");

        postAs(polishSup, LINE, lineEntry("POLISH", polisher, 100, 0)).andExpect(status().isCreated());
        postAs(polishSup, LINE, lineEntry("KILN", kiln, 100, 0)).andExpect(status().isForbidden());
        postAs(kilnSup, LINE, lineEntry("POLISH", polisher, 100, 0)).andExpect(status().isForbidden());

        postAs(packSup, PACK, packEntry(0, row(premium, "01", "C1", 10))).andExpect(status().isCreated());
        postAs(polishSup, PACK, packEntry(0, row(premium, "01", "C1", 10))).andExpect(status().isForbidden());

        long checkId = read(postAs(checker, CHECKS, check("GOOD")).andExpect(status().isCreated())).get("id").asLong();
        postAs(packSup, CHECKS, check("GOOD")).andExpect(status().isForbidden());
        // no drafts for checks, so the checker can cancel their own mistake
        postAs(checker, CHECKS + "/" + checkId + "/cancel", Map.of("reason", "Typo")).andExpect(status().isOk());
    }

    @Test
    void theFinishingHomeShowsLotsOnTheirWayToTheBox() throws Exception {
        Map<String, Object> polish = lineEntry("POLISH", polisher, 1700, 20);
        polish.put("powerKwh", 900);
        postLine(polish);
        Map<String, Object> body = check("GOOD");
        body.put("expectedTopPct", 90);
        postAs(admin, CHECKS, body).andExpect(status().isCreated());
        // 700 + 82 boxes = 1,564 pieces, plus 16 broken = 1,580 sorted
        postPack(packEntry(16, row(premium, "01", "C1", 700), row(standard, "01", "C1", 82)));

        JsonNode overview = read(getAs(admin, "/api/production/finishing/overview?plantId=" + plantId));
        assertThat(overview.get("topGradeName").asText()).isEqualTo("Premium");
        assertThat(overview.get("lots").size()).isEqualTo(1);
        assertThat(overview.at("/lots/0/flow/waitingPolish").asLong()).isEqualTo(64);
        assertThat(overview.at("/lots/0/flow/waitingSort").asLong()).isEqualTo(100);    // 1,680 - 1,580
        assertThat(overview.at("/lots/0/lastCheck/verdict").asText()).isEqualTo("GOOD");
        assertDecimal(overview.at("/lots/0/lastCheck/expectedTopPct"), "90");
        assertDecimal(overview.at("/lots/0/topPct"), "88.61");    // 1,400 of 1,580
        if (overview.get("prodDate").asText().equals(today.toString())) {
            assertThat(overview.at("/today/polishIn").asLong()).isEqualTo(1700);
            assertDecimal(overview.at("/today/powerKwh"), "900");
            assertThat(overview.at("/today/boxes").asLong()).isEqualTo(782);
            assertThat(overview.at("/today/grades/0/code").asText()).isEqualTo("PREMIUM");
            assertThat(overview.at("/today/checks").asLong()).isEqualTo(1);
        }
    }

    @Test
    void finishingRecordsAreFoundByNumber() throws Exception {
        postLine(lineEntry("POLISH", polisher, 500, 5));
        postPack(packEntry(0, row(premium, "01", "C1", 10)));
        postAs(admin, CHECKS, check("GOOD")).andExpect(status().isCreated());
        getAs(admin, "/api/search?q=FIN-1/PL").andExpect(jsonPath("$[*].type", hasItem("polish-entry")));
        getAs(admin, "/api/search?q=FIN-1/PK").andExpect(jsonPath("$[*].type", hasItem("pack-entry")));
        getAs(admin, "/api/search?q=FIN-1/QC").andExpect(jsonPath("$[*].type", hasItem("quality-check")));
    }

    // ------------------------------------------------------------------ helpers

    private Map<String, Object> lineEntry(String stage, long machineId, int tilesIn, int rejects) {
        Map<String, Object> body = new HashMap<>();
        body.put("plantId", plantId);
        body.put("stage", stage);
        body.put("lotId", lotId);
        body.put("prodDate", today.toString());
        body.put("shiftId", shiftA);
        body.put("machineId", machineId);
        body.put("tilesIn", tilesIn);
        body.put("rejects", rejects);
        return body;
    }

    private JsonNode postLine(Map<String, Object> body) throws Exception {
        long id = read(postAs(admin, LINE, body).andExpect(status().isCreated())).get("id").asLong();
        return read(postAs(admin, LINE + "/" + id + "/post", Map.of()).andExpect(status().isOk()));
    }

    private static Map<String, Object> row(long gradeId, String shade, String caliber, int boxes) {
        return Map.of("gradeId", gradeId, "shade", shade, "caliber", caliber, "boxes", boxes);
    }

    @SafeVarargs
    private Map<String, Object> packEntry(int broken, Map<String, Object>... rows) {
        Map<String, Object> body = new HashMap<>();
        body.put("plantId", plantId);
        body.put("lotId", lotId);
        body.put("prodDate", today.toString());
        body.put("shiftId", shiftA);
        body.put("machineId", sorter);
        body.put("broken", broken);
        body.put("lines", List.of(rows));
        return body;
    }

    private JsonNode postPack(Map<String, Object> body) throws Exception {
        long id = read(postAs(admin, PACK, body).andExpect(status().isCreated())).get("id").asLong();
        return read(postAs(admin, PACK + "/" + id + "/post", Map.of()).andExpect(status().isOk()));
    }

    private Map<String, Object> check(String verdict) {
        Map<String, Object> body = new HashMap<>();
        body.put("plantId", plantId);
        body.put("lotId", lotId);
        body.put("checkDate", today.toString());
        body.put("shiftId", shiftA);
        body.put("samples", 5);
        body.put("verdict", verdict);
        return body;
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
