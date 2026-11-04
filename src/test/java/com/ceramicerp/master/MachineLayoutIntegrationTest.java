package com.ceramicerp.master;

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
 * The plant's layout (which machine feeds which) and replacing a machine. Plants differ - two presses taking turns
 * in front of one kiln, one line feeding two kilns, compensators anywhere - so the layout is data, not code.
 */
class MachineLayoutIntegrationTest extends IntegrationTestBase {

    private static final String MACHINES = "/api/masters/machines";

    private final LocalDate today = LocalDate.now(ZoneId.of("Asia/Kolkata"));
    private String admin;
    private long plantId;
    private long press1, press2, dryer, glaze, printer, comp, kiln1, kiln2;

    @BeforeEach
    void setUp() throws Exception {
        admin = adminToken();
        plantId = createPlant(admin, createCompany(admin, "LAYCO"), "LAY-1");
        press1 = machine("PRESS-1", "PRESS");
        press2 = machine("PRESS-2", "PRESS");
        dryer = machine("DRY-1", "DRYER");
        glaze = machine("GL-1", "GLAZE_LINE");
        printer = machine("DP-1", "DIGITAL_PRINTER");
        comp = machine("COMP-1", "COMPENSATOR");
        kiln1 = machine("KILN-1", "KILN");
        kiln2 = machine("KILN-2", "KILN");
    }

    // ------------------------------------------------------------------ layout

    @Test
    void twoPressesOneLineTwoKilns() throws Exception {
        feeds(press1, dryer);
        feeds(press2, dryer);
        feeds(dryer, glaze);
        feeds(glaze, printer);
        feeds(printer, comp);
        JsonNode links = feeds(comp, kiln1, kiln2);

        assertThat(links.size()).isEqualTo(7);
        assertThat(arrows(links)).contains(press1 + ">" + dryer, press2 + ">" + dryer, comp + ">" + kiln1, comp + ">" + kiln2);

        // The whole list is set each time: leaving KILN-2 out unlinks it.
        assertThat(arrows(feeds(comp, kiln1))).contains(comp + ">" + kiln1).doesNotContain(comp + ">" + kiln2);
        // ...and an empty list unlinks everything from that machine.
        assertThat(arrows(feeds(comp))).doesNotContain(comp + ">" + kiln1);
    }

    @Test
    void theLayoutNeverLoopsOrLeavesThePlant() throws Exception {
        feeds(press1, dryer);
        feeds(dryer, glaze);
        expectRefused(glaze, List.of(press1), "That would make a loop: PRESS-1 already leads back to GL-1");
        expectRefused(dryer, List.of(dryer), "A machine can't feed into itself");

        long other = createPlant(admin, createCompany(admin, "LAYCO2"), "LAY-2");
        long elsewhere = read(postAs(admin, MACHINES, Map.of("plantId", other, "code", "KILN-9", "name", "Kiln 9",
                "type", "KILN")).andExpect(status().isCreated())).get("id").asLong();
        expectRefused(glaze, List.of(elsewhere), "KILN-9 is in another plant");

        // A machine in the layout can't be moved to another plant with its arrows.
        putAs(admin, MACHINES + "/" + dryer, Map.of("plantId", other, "code", "DRY-1", "name", "Dryer", "type", "DRYER"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("DRY-1 is part of the layout of LAY-1. Remove its links before moving it to another plant."));
    }

    @Test
    void aSupervisorReadsTheLayoutWithoutMasterRights() throws Exception {
        feeds(printer, kiln1);
        long role = createRole(admin, "Kiln supervisor L", "PRODUCTION_VIEW", "KILN_ENTRY");
        createUser(admin, "kiln.lay", "password-1", role, plantId);
        String kilnSup = login("kiln.lay", "password-1");

        getAs(kilnSup, MACHINES + "/links?plantId=" + plantId).andExpect(status().isForbidden());
        JsonNode links = read(getAs(kilnSup, "/api/production/lookups/machine-links?plantId=" + plantId).andExpect(status().isOk()));
        assertThat(arrows(links)).containsExactly(printer + ">" + kiln1);
    }

    // ------------------------------------------------------------------ replace

    @Test
    void aNewKilnTakesTheOldOnesPlace() throws Exception {
        feeds(comp, kiln1);
        feeds(kiln1, machine("POL-1", "POLISHING_LINE"));
        long gasMeter = read(postAs(admin, "/api/energy/meters", Map.of("plantId", plantId, "code", "KILN1-GAS",
                "name", "Kiln 1 gas", "kind", "GAS", "unit", "M3", "main", false, "machineId", kiln1, "readEvery", "SHIFT"))
                .andExpect(status().isCreated())).get("id").asLong();
        long task = read(postAs(admin, "/api/maintenance/pm-tasks", Map.of("plantId", plantId, "machineIds", List.of(kiln1),
                "title", "Check the rollers", "kind", "INSPECTION", "everyDays", 7, "nextDue", today.plusDays(3).toString()))
                .andExpect(status().isCreated())).get(0).get("id").asLong();

        Map<String, Object> body = new HashMap<>(Map.of("code", "KILN-1", "name", "Roller kiln 1 (new)", "make", "Sacmi"));
        body.put("installedOn", today.toString());
        JsonNode fresh = read(postAs(admin, MACHINES + "/" + kiln1 + "/replace", body).andExpect(status().isCreated()));
        long newKiln = fresh.get("id").asLong();
        assertThat(fresh.get("code").asText()).isEqualTo("KILN-1");
        assertThat(fresh.get("type").asText()).isEqualTo("KILN");
        assertThat(fresh.get("active").asBoolean()).isTrue();

        // The old kiln is out of use, renamed, and points at the new one.
        JsonNode old = read(getAs(admin, MACHINES + "/" + kiln1));
        assertThat(old.get("code").asText()).isEqualTo("KILN-1-OLD");
        assertThat(old.get("active").asBoolean()).isFalse();
        assertThat(old.get("replacedByCode").asText()).isEqualTo("KILN-1");

        // Its place in the layout moved, both the arrow in and the arrow out.
        List<String> arrows = arrows(read(getAs(admin, MACHINES + "/links?plantId=" + plantId)));
        assertThat(arrows).contains(comp + ">" + newKiln).noneMatch(a -> a.contains(String.valueOf(kiln1)));

        // The gas meter carries on with the new kiln.
        JsonNode meters = read(getAs(admin, "/api/energy/meters?plantId=" + plantId));
        assertThat(meters.get(0).get("id").asLong()).isEqualTo(gasMeter);
        assertThat(meters.get(0).get("machine").get("id").asLong()).isEqualTo(newKiln);

        // The routine job is copied to the new kiln on the same date; the old one is switched off.
        JsonNode tasks = read(getAs(admin, "/api/maintenance/pm-tasks?plantId=" + plantId + "&includeInactive=true"));
        assertThat(tasks.size()).isEqualTo(2);
        for (JsonNode t : tasks) {
            boolean isOld = t.get("id").asLong() == task;
            assertThat(t.get("machine").get("id").asLong()).isEqualTo(isOld ? kiln1 : newKiln);
            assertThat(t.get("active").asBoolean()).isEqualTo(!isOld);
            assertThat(t.get("nextDue").asText()).isEqualTo(today.plusDays(3).toString());
        }

        // A second replacement of the same code finds the next free name; an old machine can't be replaced again.
        postAs(admin, MACHINES + "/" + newKiln + "/replace", Map.of("code", "KILN-1", "name", "Kiln 1 (third)"))
                .andExpect(status().isCreated());
        assertThat(read(getAs(admin, MACHINES + "/" + newKiln)).get("code").asText()).isEqualTo("KILN-1-OLD2");
        postAs(admin, MACHINES + "/" + kiln1 + "/replace", Map.of("code", "KILN-5", "name", "Kiln 5"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("KILN-1-OLD is not in use, so there is nothing to replace"));
    }

    @Test
    void aNewCodeLeavesTheOldOneAsItIs() throws Exception {
        postAs(admin, MACHINES + "/" + kiln2 + "/replace", Map.of("code", "KILN-3", "name", "Kiln 3"))
                .andExpect(status().isCreated());
        assertThat(read(getAs(admin, MACHINES + "/" + kiln2)).get("code").asText()).isEqualTo("KILN-2");
        postAs(admin, MACHINES + "/" + kiln1 + "/replace", Map.of("code", "KILN-3", "name", "Kiln 3 again"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Machine code KILN-3 already exists in plant LAY-1"));
    }

    @Test
    void aMachineRunningADesignCantBeReplaced() throws Exception {
        long design = read(postAs(admin, "/api/masters/designs", Map.of("code", "LAY-D", "name", "Layout design",
                "categoryId", idOf("categories", "GVT"), "sizeId", idOf("sizes", "600X1200"),
                "finishId", idOf("finishes", "GLOSSY"), "faces", 4)).andExpect(status().isCreated())).get("id").asLong();
        long lot = read(postAs(admin, "/api/production/lots", lotBody(design, printer)).andExpect(status().isCreated()))
                .get("id").asLong();
        String lotNo = read(getAs(admin, "/api/production/lots/" + lot)).get("lotNo").asText();

        postAs(admin, MACHINES + "/" + printer + "/replace", Map.of("code", "DP-1", "name", "Printer 1 (new)"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("DP-1 is running design lot " + lotNo
                        + ". Close the lot before replacing the machine."));
        // Refused means nothing happened: the old printer is still DP-1 and in use.
        JsonNode still = read(getAs(admin, MACHINES + "/" + printer));
        assertThat(still.get("code").asText()).isEqualTo("DP-1");
        assertThat(still.get("active").asBoolean()).isTrue();
        // A press runs no design, so it can be replaced any time.
        postAs(admin, MACHINES + "/" + press1 + "/replace", Map.of("code", "PRESS-1", "name", "Press 1 (new)"))
                .andExpect(status().isCreated());

        // Once the lot is closed the printer can go. The lot keeps the printer it really ran on.
        postAs(admin, "/api/production/lots/" + lot + "/close", Map.of()).andExpect(status().isOk());
        postAs(admin, MACHINES + "/" + printer + "/replace", Map.of("code", "DP-1", "name", "Printer 1 (new)"))
                .andExpect(status().isCreated());
        assertThat(read(getAs(admin, "/api/production/lots/" + lot)).get("printerCode").asText()).isEqualTo("DP-1-OLD");
    }

    // ------------------------------------------------------------------ helpers

    private long machine(String code, String type) throws Exception {
        return read(postAs(admin, MACHINES, Map.of("plantId", plantId, "code", code, "name", "Machine " + code,
                "type", type)).andExpect(status().isCreated())).get("id").asLong();
    }

    /** Sets what one machine feeds into; returns the plant's layout. */
    private JsonNode feeds(long from, long... to) throws Exception {
        List<Long> ids = new ArrayList<>();
        for (long id : to) ids.add(id);
        return read(putAs(admin, MACHINES + "/" + from + "/feeds", Map.of("toMachineIds", ids)).andExpect(status().isOk()));
    }

    private void expectRefused(long from, List<Long> to, String message) throws Exception {
        putAs(admin, MACHINES + "/" + from + "/feeds", Map.of("toMachineIds", to))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(message));
    }

    private static List<String> arrows(JsonNode links) {
        List<String> out = new ArrayList<>();
        links.forEach(l -> out.add(l.get("fromMachineId").asLong() + ">" + l.get("toMachineId").asLong()));
        return out;
    }

    private Map<String, Object> lotBody(long designId, long printerId) throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("plantId", plantId);
        body.put("designId", designId);
        body.put("printerId", printerId);
        body.put("openedDate", today.toString());
        body.put("shiftId", idOf("shifts", "A"));
        body.put("closeCurrent", false);
        return body;
    }

    private long idOf(String master, String code) throws Exception {
        for (JsonNode n : read(getAs(admin, "/api/masters/" + master))) {
            if (n.get("code").asText().equals(code)) return n.get("id").asLong();
        }
        throw new AssertionError(code + " not found in " + master);
    }
}
