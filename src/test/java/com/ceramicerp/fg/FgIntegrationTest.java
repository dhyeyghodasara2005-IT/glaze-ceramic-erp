package com.ceramicerp.fg;

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

/** Step 5a: finished-goods godowns and pillars, and stock that comes in from posted pack entries. */
class FgIntegrationTest extends IntegrationTestBase {

    private static final String PACK = "/api/production/pack-entries";
    private final LocalDate today = LocalDate.now(ZoneId.of("Asia/Kolkata"));

    private String admin;
    private long companyId;
    private long plantId;
    private long sorter, shiftA, lotId;
    private long premium, standard;
    private long godown;
    /** pillar ids by code: A1 ... A12 */
    private final Map<String, Long> pillars = new HashMap<>();

    @BeforeEach
    void setUp() throws Exception {
        admin = adminToken();
        companyId = createCompany(admin, "FGCO");
        plantId = createPlant(admin, companyId, "FG-T");
        long press = createMachine(plantId, "PRESS-1", "PRESS");
        sorter = createMachine(plantId, "SORT-1", "SORTING_LINE");
        shiftA = idOf("shifts", "A");
        premium = idOf("grades", "PREMIUM");
        standard = idOf("grades", "STANDARD");
        long design = read(postAs(admin, "/api/masters/designs", Map.of("code", "CALACATTA-FG", "name", "Calacatta Gold",
                "categoryId", idOf("categories", "GVT"), "sizeId", idOf("sizes", "600X1200"),
                "finishId", idOf("finishes", "GLOSSY"), "faces", 4)).andExpect(status().isCreated())).get("id").asLong();
        lotId = read(postAs(admin, "/api/production/lots", Map.of("plantId", plantId, "designId", design, "pressId", press,
                "openedDate", today.toString(), "shiftId", shiftA, "closeCurrent", false))
                .andExpect(status().isCreated())).get("id").asLong();

        godown = read(postAs(admin, "/api/fg/godowns", Map.of("plantId", plantId, "code", "fg-1", "name", "Main FG godown"))
                .andExpect(status().isCreated())).get("id").asLong();
        JsonNode map = read(postAs(admin, "/api/fg/godowns/" + godown + "/pillars",
                Map.of("prefix", "a", "from", 1, "to", 12, "capacityPallets", 24)).andExpect(status().isOk()));
        map.get("pillars").forEach(p -> pillars.put(p.get("code").asText(), p.get("id").asLong()));
    }

    @Test
    void pillarsAreAddedInARowAndReadAsPeopleDo() throws Exception {
        // A2 comes before A10 (plain text order would put A10 right after A1)
        JsonNode map = read(getAs(admin, "/api/fg/godowns/" + godown + "/map"));
        assertThat(codes(map)).startsWith("A1", "A2", "A3").endsWith("A10", "A11", "A12");
        assertThat(map.at("/pillars/0/capacityPallets").asInt()).isEqualTo(24);
        assertThat(map.at("/godown/code").asText()).isEqualTo("FG-1");

        // Running it again over A10 to A14 only adds the two new ones
        JsonNode more = read(postAs(admin, "/api/fg/godowns/" + godown + "/pillars", Map.of("prefix", "A", "from", 10, "to", 14))
                .andExpect(status().isOk()));
        assertThat(more.get("pillars").size()).isEqualTo(14);

        postAs(admin, "/api/fg/godowns/" + godown + "/pillars", Map.of("prefix", "B", "from", 5, "to", 1))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("The last number can't be smaller than the first"));
        postAs(admin, "/api/fg/godowns/" + godown + "/pillars", Map.of("prefix", "B", "from", 1, "to", 300))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("At most 200 pillars at a time"));

        JsonNode godowns = read(getAs(admin, "/api/fg/godowns?plantId=" + plantId));
        assertThat(godowns.at("/0/pillars").asLong()).isEqualTo(14);
    }

    @Test
    void postingAPackEntryPutsTheBoxesAtTheirPillars() throws Exception {
        setBoxesPerPallet(40);
        // One shade split over two pillars (A1 got full), and a Standard row next to it
        JsonNode entry = postPack(row(premium, "01", "C1", 700, "A1"), row(premium, "01", "C1", 50, "A2"),
                row(standard, "01", "C1", 80, "A1"));
        String entryNo = entry.get("entryNo").asText();
        assertThat(entry.at("/lines/1/pillarCode").asText()).isEqualTo("A2");
        assertThat(entry.at("/lines/1/godownCode").asText()).isEqualTo("FG-1");

        List<JsonNode> stock = rows(read(getAs(admin, "/api/fg/stock?plantId=" + plantId)));
        assertThat(stock).hasSize(3);
        JsonNode a1 = stock.get(0);                              // Premium before Standard, A1 before A2
        assertThat(a1.get("pillarCode").asText()).isEqualTo("A1");
        assertThat(a1.get("gradeCode").asText()).isEqualTo("PREMIUM");
        assertThat(a1.get("batchCode").asText()).endsWith("/LOT/" + entryNo.split("/")[2] + "/0001");
        assertThat(a1.get("boxes").asInt()).isEqualTo(700);
        assertThat(a1.get("sqm").decimalValue()).isEqualByComparingTo("1008.00");   // 700 x 1.44
        assertThat(a1.get("pallets").asInt()).isEqualTo(18);                        // 700 / 40 = 17.5 -> 18 places

        JsonNode ledger = read(getAs(admin, "/api/fg/stock/" + a1.get("id").asLong()));
        assertThat(ledger.get("moves").size()).isEqualTo(1);
        assertThat(ledger.at("/moves/0/source").asText()).isEqualTo("PACK");
        assertThat(ledger.at("/moves/0/sourceNo").asText()).isEqualTo(entryNo);
        assertThat(ledger.at("/moves/0/boxes").asInt()).isEqualTo(700);
        assertThat(ledger.at("/moves/0/balanceBoxes").asInt()).isEqualTo(700);

        JsonNode map = read(getAs(admin, "/api/fg/godowns/" + godown + "/map"));
        assertThat(map.at("/pillars/0/boxes").asInt()).isEqualTo(780);               // A1: 700 + 80
        assertThat(map.at("/pillars/0/pallets").asInt()).isEqualTo(20);              // 18 + 2
        assertThat(map.at("/pillars/0/stock/0/boxes").asInt()).isEqualTo(700);       // most boxes first

        // Filters: by grade, and by text (design, batch, shade or pillar)
        assertThat(rows(read(getAs(admin, "/api/fg/stock?plantId=" + plantId + "&gradeId=" + standard)))).hasSize(1);
        assertThat(rows(read(getAs(admin, "/api/fg/stock?plantId=" + plantId + "&search=a2")))).hasSize(1);
    }

    @Test
    void cancellingThePackEntryTakesTheBoxesOutAgain() throws Exception {
        long id = postPack(row(premium, "01", "C1", 700, "A1")).get("id").asLong();
        long stockId = rows(read(getAs(admin, "/api/fg/stock?plantId=" + plantId))).get(0).get("id").asLong();

        postAs(admin, PACK + "/" + id + "/cancel", Map.of("reason", "Wrong lot")).andExpect(status().isOk());

        assertThat(rows(read(getAs(admin, "/api/fg/stock?plantId=" + plantId)))).isEmpty();   // empty rows are hidden
        JsonNode ledger = read(getAs(admin, "/api/fg/stock/" + stockId));
        assertThat(ledger.get("moves").size()).isEqualTo(2);
        assertThat(ledger.at("/moves/1/boxes").asInt()).isEqualTo(-700);
        assertThat(ledger.at("/moves/1/balanceBoxes").asInt()).isZero();
        assertThat(ledger.at("/moves/1/reversal").asBoolean()).isTrue();
    }

    @Test
    void aPillarMustBeInThePlantAndOpen() throws Exception {
        long otherPlant = createPlant(admin, companyId, "FG-OTHER");
        long otherGodown = read(postAs(admin, "/api/fg/godowns", Map.of("plantId", otherPlant, "code", "X", "name", "Other"))
                .andExpect(status().isCreated())).get("id").asLong();
        long elsewhere = read(postAs(admin, "/api/fg/godowns/" + otherGodown + "/pillars", Map.of("prefix", "Z", "from", 1, "to", 1)))
                .at("/pillars/0/id").asLong();
        Map<String, Object> wrongPlant = new HashMap<>(row(premium, "01", "C1", 10, "A1"));
        wrongPlant.put("pillarId", elsewhere);
        postAs(admin, PACK, packEntry(wrongPlant))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Pillar Z1 is not in plant FG-T"));

        // A pillar can be closed while empty, and then can't be chosen
        putAs(admin, "/api/fg/pillars/" + pillars.get("A3"), Map.of("active", false)).andExpect(status().isOk());
        postAs(admin, PACK, packEntry(row(premium, "01", "C1", 10, "A3")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Pillar FG-1 A3 is closed. Choose another one."));

        // ...but not while boxes stand there
        postPack(row(premium, "01", "C1", 10, "A1"));
        putAs(admin, "/api/fg/pillars/" + pillars.get("A1"), Map.of("active", false))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Pillar A1 still has boxes. It can be closed once it is empty."));
    }

    @Test
    void finishedGoodsHaveTheirOwnPermissions() throws Exception {
        createUser(admin, "pack.fg", "password-1", createRole(admin, "Pack FG", "PRODUCTION_VIEW", "PACKING_ENTRY"), plantId);
        createUser(admin, "fg.view", "password-1", createRole(admin, "FG view", "FG_VIEW"), plantId);
        createUser(admin, "fg.keeper", "password-1", createRole(admin, "FG keeper", "FG_VIEW", "FG_ENTRY"), plantId);
        String packer = login("pack.fg", "password-1");
        String viewer = login("fg.view", "password-1");
        String keeper = login("fg.keeper", "password-1");

        // The packing supervisor picks pillars through the production lookups, without stock rights
        getAs(packer, "/api/production/lookups/pillars?plantId=" + plantId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].code").value("A1"));
        getAs(packer, "/api/fg/stock?plantId=" + plantId).andExpect(status().isForbidden());

        getAs(viewer, "/api/fg/stock?plantId=" + plantId).andExpect(status().isOk());
        postAs(viewer, "/api/fg/godowns", Map.of("plantId", plantId, "code", "FG-2", "name", "Second")).andExpect(status().isForbidden());
        postAs(keeper, "/api/fg/godowns", Map.of("plantId", plantId, "code", "FG-2", "name", "Second")).andExpect(status().isCreated());
    }

    // ------------------------------------------------------------------ helpers

    private Map<String, Object> row(long gradeId, String shade, String caliber, int boxes, String pillar) {
        return Map.of("gradeId", gradeId, "shade", shade, "caliber", caliber, "boxes", boxes, "pillarId", pillars.get(pillar));
    }

    @SafeVarargs
    private Map<String, Object> packEntry(Map<String, Object>... rows) {
        Map<String, Object> body = new HashMap<>();
        body.put("plantId", plantId);
        body.put("lotId", lotId);
        body.put("prodDate", today.toString());
        body.put("shiftId", shiftA);
        body.put("machineId", sorter);
        body.put("broken", 0);
        body.put("lines", List.of(rows));
        return body;
    }

    @SafeVarargs
    private JsonNode postPack(Map<String, Object>... rows) throws Exception {
        long id = read(postAs(admin, PACK, packEntry(rows)).andExpect(status().isCreated())).get("id").asLong();
        return read(postAs(admin, PACK + "/" + id + "/post", Map.of()).andExpect(status().isOk()));
    }

    private static List<JsonNode> rows(JsonNode array) {
        List<JsonNode> list = new ArrayList<>();
        array.forEach(list::add);
        return list;
    }

    private static List<String> codes(JsonNode map) {
        List<String> codes = new ArrayList<>();
        map.get("pillars").forEach(p -> codes.add(p.get("code").asText()));
        return codes;
    }

    /** Edit the seeded 600 x 1200 size through the API, as a user would (rolled back after the test). */
    private void setBoxesPerPallet(int boxes) throws Exception {
        for (JsonNode s : read(getAs(admin, "/api/masters/sizes"))) {
            if (s.get("code").asText().equals("600X1200")) {
                Map<String, Object> body = new HashMap<>();
                for (String f : List.of("code", "name", "lengthMm", "widthMm", "thicknessMm", "pcsPerBox")) {
                    body.put(f, s.get(f));
                }
                body.put("boxesPerPallet", boxes);
                putAs(admin, "/api/masters/sizes/" + s.get("id").asLong(), body).andExpect(status().isOk());
            }
        }
    }

    private long createMachine(long plant, String code, String type) throws Exception {
        return read(postAs(admin, "/api/masters/machines", Map.of("plantId", plant, "code", code,
                "name", "Machine " + code, "type", type)).andExpect(status().isCreated())).get("id").asLong();
    }

    private long idOf(String master, String code) throws Exception {
        for (JsonNode n : read(getAs(admin, "/api/masters/" + master))) {
            if (n.get("code").asText().equals(code)) return n.get("id").asLong();
        }
        throw new AssertionError(code + " not found in " + master);
    }
}
