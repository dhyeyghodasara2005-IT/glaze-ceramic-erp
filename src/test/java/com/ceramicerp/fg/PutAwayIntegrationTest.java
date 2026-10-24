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

/**
 * Step 6a: smart put-away. The godown in these tests is the left wing of a godown loaded in the middle:
 *
 *   A1  A2 | A3  A4 | A5  A6 ┃ loading   (R1 remainder, S1 staging next to it)
 *   SLOW   | NORMAL | FAST   ┃
 *   walk 6, 5, 4, 3, 2, 1 - A6 is next to the loading point. Each pillar holds 4 pallets of 50 boxes.
 */
class PutAwayIntegrationTest extends IntegrationTestBase {

    private final LocalDate today = LocalDate.now(ZoneId.of("Asia/Kolkata"));

    private String admin;
    private long plantId, godown, lotId, otherDesign, otherLotId, premium, sorter, shiftA;
    private final Map<String, Long> pillars = new HashMap<>();

    @BeforeEach
    void setUp() throws Exception {
        admin = adminToken();
        plantId = createPlant(admin, createCompany(admin, "PACO"), "PA-1");
        long press = machine("PRESS-1", "PRESS");
        long press2 = machine("PRESS-2", "PRESS");
        sorter = machine("SORT-1", "SORTING_LINE");
        shiftA = idOf("shifts", "A");
        premium = idOf("grades", "PREMIUM");
        lotId = lot(design("CAL-PA", "Calacatta PA"), press);
        otherDesign = design("ONYX-PA", "Onyx PA");
        otherLotId = lot(otherDesign, press2);

        godown = read(postAs(admin, "/api/fg/godowns", Map.of("plantId", plantId, "code", "FG-1", "name", "FG"))
                .andExpect(status().isCreated())).get("id").asLong();
        addPillars("A", 1, 6, 4);
        addPillars("R", 1, 1, 4);
        addPillars("S", 1, 1, 10);
        layout(Map.of("prefix", "A", "from", 1, "to", 6, "firstWalkOrder", 6, "step", -1));
        layout(Map.of("prefix", "A", "from", 1, "to", 2, "zone", "SLOW"));
        layout(Map.of("prefix", "A", "from", 5, "to", 6, "zone", "FAST"));
        layout(Map.of("prefix", "R", "from", 1, "to", 1, "kind", "REMAINDER", "firstWalkOrder", 1));
        JsonNode map = layout(Map.of("prefix", "S", "from", 1, "to", 1, "kind", "STAGING", "firstWalkOrder", 1));
        map.get("pillars").forEach(p -> pillars.put(p.get("code").asText(), p.get("id").asLong()));
    }

    @Test
    void aWholeRunOfPillarsIsLaidOutAtOnce() throws Exception {
        JsonNode map = read(getAs(admin, "/api/fg/godowns/" + godown + "/map"));
        JsonNode a1 = pillar(map, "A1");
        JsonNode a6 = pillar(map, "A6");
        assertThat(a6.get("walkOrder").asInt()).isEqualTo(1);           // next to the loading point
        assertThat(a6.get("zone").asText()).isEqualTo("FAST");
        assertThat(a1.get("walkOrder").asInt()).isEqualTo(6);
        assertThat(a1.get("zone").asText()).isEqualTo("SLOW");
        assertThat(pillar(map, "A3").get("zone").asText()).isEqualTo("NORMAL");   // the default
        assertThat(pillar(map, "R1").get("kind").asText()).isEqualTo("REMAINDER");

        // Counting down from 2 over six pillars would reach -3
        putAs(admin, "/api/fg/godowns/" + godown + "/layout", Map.of("prefix", "A", "from", 1, "to", 6, "firstWalkOrder", 2, "step", -1))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("The walk order would go below 1. Start higher, or count up instead of down."));
    }

    @Test
    void newBoxesGoOnTopOfTheSameBoxThenNextToTheDesign() throws Exception {
        pack(lotId, "01", "C1", 100, "A4");                              // 2 of A4's 4 pallets

        List<JsonNode> same = suggest(lotId, "01", "c1", 50);           // "c1" is the same caliber as C1
        assertThat(same.get(0).get("code").asText()).isEqualTo("A4");
        assertThat(same.get(0).get("reason").asText()).isEqualTo("SAME_BOX");
        assertThat(same.get(0).get("freePallets").asInt()).isEqualTo(2);
        assertThat(same.get(1).get("code").asText()).isEqualTo("A5");    // empty, next to A4, nearer the loading point than A3
        assertThat(same.get(1).get("reason").asText()).isEqualTo("NEXT_TO_DESIGN");

        // Another shade is another box: it doesn't go on top of shade 01, but next to it
        List<JsonNode> otherShade = suggest(lotId, "02", "C1", 50);
        assertThat(codes(otherShade)).doesNotContain("A4");
        assertThat(otherShade.get(0).get("reason").asText()).isEqualTo("NEXT_TO_DESIGN");
    }

    @Test
    void aFullPillarIsNotSuggested() throws Exception {
        pack(lotId, "01", "C1", 200, "A4");                              // 4 pallets: full
        assertThat(codes(suggest(lotId, "01", "C1", 50))).doesNotContain("A4");
    }

    @Test
    void bestSellersGoNextToTheLoadingPointAndOthersLeaveItFree() throws Exception {
        List<JsonNode> normal = suggest(otherLotId, "01", "C1", 100);
        assertThat(normal.get(0).get("code").asText()).isEqualTo("A4");  // NORMAL zone, nearest first
        assertThat(normal.get(0).get("reason").asText()).isEqualTo("EMPTY_IN_ZONE");
        // the fast zone comes after the slow one for a design that isn't a best seller
        assertThat(codes(normal)).containsExactly("A4", "A3", "A2", "A1", "A6");

        putAs(admin, "/api/fg/designs/" + otherDesign + "/fast-mover", Map.of("fastMover", true)).andExpect(status().isOk());
        List<JsonNode> fast = suggest(otherLotId, "01", "C1", 100);
        assertThat(fast.get(0).get("code").asText()).isEqualTo("A6");
        assertThat(fast.get(0).get("zone").asText()).isEqualTo("FAST");
    }

    @Test
    void lessThanAPalletGoesToARemainderPillarAndStagingIsNeverSuggested() throws Exception {
        List<JsonNode> small = suggest(otherLotId, "01", "C1", 30);      // 30 of 50 boxes
        assertThat(small.get(0).get("code").asText()).isEqualTo("R1");
        assertThat(small.get(0).get("reason").asText()).isEqualTo("REMAINDER");
        assertThat(codes(small)).doesNotContain("S1");
        assertThat(codes(suggest(otherLotId, "01", "C1", 300))).doesNotContain("S1");
    }

    @Test
    void thePillarListSaysWhatStandsWhere() throws Exception {
        pack(lotId, "01", "C1", 100, "A4");
        for (JsonNode p : read(getAs(admin, "/api/production/lookups/pillars?plantId=" + plantId))) {
            if (p.get("code").asText().equals("A4")) {
                assertThat(p.at("/stock/0/shade").asText()).isEqualTo("01");   // the screen warns before mixing
                assertThat(p.get("zone").asText()).isEqualTo("NORMAL");
                assertThat(p.get("walkOrder").asInt()).isEqualTo(3);
                return;
            }
        }
        throw new AssertionError("A4 missing");
    }

    @Test
    void theWarehouseDecidesTheBestSellers() throws Exception {
        createUser(admin, "fg.look", "password-1", createRole(admin, "FG look", "FG_VIEW"), plantId);
        createUser(admin, "pack.pa", "password-1", createRole(admin, "Pack PA", "PRODUCTION_VIEW", "PACKING_ENTRY"), plantId);
        String viewer = login("fg.look", "password-1");
        String packer = login("pack.pa", "password-1");

        putAs(viewer, "/api/fg/designs/" + otherDesign + "/fast-mover", Map.of("fastMover", true)).andExpect(status().isForbidden());
        putAs(admin, "/api/fg/designs/" + otherDesign + "/fast-mover", Map.of("fastMover", true))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0]").value(otherDesign));
        getAs(viewer, "/api/fg/designs/fast-movers").andExpect(status().isOk()).andExpect(jsonPath("$[0]").value(otherDesign));
        // the packing supervisor gets suggestions without any warehouse rights
        getAs(packer, "/api/production/lookups/put-away?plantId=" + plantId + "&lotId=" + lotId + "&gradeId=" + premium)
                .andExpect(status().isOk());
        putAs(viewer, "/api/fg/godowns/" + godown + "/layout", Map.of("prefix", "A", "from", 1, "to", 1, "zone", "FAST"))
                .andExpect(status().isForbidden());
    }

    // ------------------------------------------------------------------ helpers

    private List<JsonNode> suggest(long lot, String shade, String caliber, int boxes) throws Exception {
        List<JsonNode> list = new ArrayList<>();
        read(getAs(admin, "/api/production/lookups/put-away?plantId=" + plantId + "&lotId=" + lot + "&gradeId=" + premium
                + "&shade=" + shade + "&caliber=" + caliber + "&boxes=" + boxes)).forEach(list::add);
        return list;
    }

    private static List<String> codes(List<JsonNode> suggestions) {
        return suggestions.stream().map(s -> s.get("code").asText()).toList();
    }

    private static JsonNode pillar(JsonNode map, String code) {
        for (JsonNode p : map.get("pillars")) {
            if (p.get("code").asText().equals(code)) return p;
        }
        throw new AssertionError(code + " missing");
    }

    private void pack(long lot, String shade, String caliber, int boxes, String pillar) throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("plantId", plantId);
        body.put("lotId", lot);
        body.put("prodDate", today.toString());
        body.put("shiftId", shiftA);
        body.put("machineId", sorter);
        body.put("broken", 0);
        body.put("lines", List.of(Map.of("gradeId", premium, "shade", shade, "caliber", caliber, "boxes", boxes,
                "pillarId", pillars.get(pillar))));
        long id = read(postAs(admin, "/api/production/pack-entries", body).andExpect(status().isCreated())).get("id").asLong();
        postAs(admin, "/api/production/pack-entries/" + id + "/post", Map.of()).andExpect(status().isOk());
    }

    private void addPillars(String prefix, int from, int to, int capacity) throws Exception {
        postAs(admin, "/api/fg/godowns/" + godown + "/pillars", Map.of("prefix", prefix, "from", from, "to", to,
                "capacityPallets", capacity)).andExpect(status().isOk());
    }

    private JsonNode layout(Map<String, Object> body) throws Exception {
        return read(putAs(admin, "/api/fg/godowns/" + godown + "/layout", body).andExpect(status().isOk()));
    }

    private long design(String code, String name) throws Exception {
        return read(postAs(admin, "/api/masters/designs", Map.of("code", code, "name", name,
                "categoryId", idOf("categories", "GVT"), "sizeId", idOf("sizes", "600X1200"),
                "finishId", idOf("finishes", "GLOSSY"), "faces", 2)).andExpect(status().isCreated())).get("id").asLong();
    }

    private long lot(long design, long press) throws Exception {
        return read(postAs(admin, "/api/production/lots", Map.of("plantId", plantId, "designId", design, "pressId", press,
                "openedDate", today.toString(), "shiftId", shiftA, "closeCurrent", false))
                .andExpect(status().isCreated())).get("id").asLong();
    }

    private long machine(String code, String type) throws Exception {
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
