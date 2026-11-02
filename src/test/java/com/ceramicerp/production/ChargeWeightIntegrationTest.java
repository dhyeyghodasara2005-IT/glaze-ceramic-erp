package com.ceramicerp.production;

import com.ceramicerp.IntegrationTestBase;
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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Step 10b: the weight to load so the dry recipe stays exact. */
class ChargeWeightIntegrationTest extends IntegrationTestBase {

    private static final String WEIGHTS = "/api/production/body-batches/wet-weights";
    private static final ZoneId INDIA = ZoneId.of("Asia/Kolkata");

    private final LocalDate today = LocalDate.now(INDIA);

    private String admin;
    private long companyId;
    private long plantId;
    private long yard, shed;
    private long clay, additive, bags;
    private long supplier;

    @BeforeEach
    void setUp() throws Exception {
        admin = adminToken();
        companyId = createCompany(admin, "WETCO");
        plantId = createPlant(admin, companyId, "WET-1");
        yard = godown("YARD", "Clay yard");
        shed = godown("SHED", "Shed");
        clay = material("CLAY-W", "TON");
        additive = material("STPP-W", "KG");
        bags = material("BAG-W", "BAG");
        supplier = read(postAs(admin, "/api/parties", Map.of("partyType", "SUPPLIER", "name", "Wet Mines",
                "gstRegistration", "UNREGISTERED")).andExpect(status().isCreated())).get("id").asLong();
    }

    @Test
    void theWetWeightPutsTheDryWeightInTheMill() throws Exception {
        receive(clay, yard, "100", "8");
        JsonNode result = weights(line(clay, yard, "46"));
        JsonNode line = result.at("/lines/0");
        // 46 / 0.92
        assertDecimal(line.get("wetQuantity"), "50");
        assertDecimal(line.get("moisturePct"), "8");
        assertThat(line.get("moistureUnknown").asBoolean()).isFalse();
        assertThat(line.has("shortDry")).isFalse();
        // 50 T at 8 % is 4 T of water
        assertDecimal(result.get("waterLitres"), "4000");
    }

    @Test
    void itFollowsTheLotsOldestFirstEachWithItsOwnMoisture() throws Exception {
        receive(clay, yard, "10", "10");
        receive(clay, yard, "20", "20");
        JsonNode line = weights(line(clay, yard, "12")).at("/lines/0");
        // the whole first truck gives 9 dry; the last 3 dry need 3.75 of the wetter one
        assertDecimal(line.get("wetQuantity"), "13.75");
        assertThat(line.get("lots")).hasSize(2);
        assertDecimal(line.at("/lots/0/wetQuantity"), "10");
        assertDecimal(line.at("/lots/0/moisturePct"), "10");
        assertDecimal(line.at("/lots/1/wetQuantity"), "3.75");
        // (1 + 0.75) of water in 13.75
        assertDecimal(line.get("moisturePct"), "12.73");
    }

    @Test
    void twoLinesOfOneMaterialDontCountTheSameLotTwice() throws Exception {
        receive(clay, yard, "10", "10");
        receive(clay, yard, "20", "20");
        JsonNode lines = weights(line(clay, yard, "4.5"), line(clay, yard, "6.5")).get("lines");
        assertDecimal(lines.at("/0/wetQuantity"), "5");
        // 5 T left of the first truck gives 4.5 dry; the other 2 dry come from the second
        assertDecimal(lines.at("/1/wetQuantity"), "7.5");
    }

    @Test
    void aLotWithNoMoistureIsLoadedAsWeighedAndSaysSo() throws Exception {
        receive(clay, yard, "100", null);
        JsonNode result = weights(line(clay, yard, "12"));
        JsonNode line = result.at("/lines/0");
        assertDecimal(line.get("wetQuantity"), "12");
        assertThat(line.get("moistureUnknown").asBoolean()).isTrue();
        assertThat(line.has("moisturePct")).isFalse();
        assertDecimal(result.get("waterLitres"), "0");
    }

    @Test
    void whatTheGodownCantSupplyIsShownAsShort() throws Exception {
        receive(clay, yard, "10", "10");
        JsonNode line = weights(line(clay, yard, "12")).at("/lines/0");
        assertDecimal(line.get("shortDry"), "3");
        // 10 from the lot and the missing 3 as it stands: charging then refuses and says how much is missing
        assertDecimal(line.get("wetQuantity"), "13");
    }

    @Test
    void onlyThatGodownsLotsCount() throws Exception {
        receive(clay, yard, "100", "8");
        JsonNode line = weights(line(clay, shed, "10")).at("/lines/0");
        assertDecimal(line.get("shortDry"), "10");
        assertThat(line.get("lots")).isEmpty();
    }

    @Test
    void kilosWorkLikeTonsAndBagsAreLeftAlone() throws Exception {
        receive(additive, yard, "1000", "2");
        receive(bags, yard, "50", "10");
        JsonNode result = weights(line(additive, yard, "98"), line(bags, yard, "5"));
        assertDecimal(result.at("/lines/0/wetQuantity"), "100");
        assertDecimal(result.at("/lines/1/wetQuantity"), "5");
        assertThat(result.at("/lines/1/lots")).isEmpty();
        // 2 kg of water from the additive; nothing from the bags
        assertDecimal(result.get("waterLitres"), "2");
    }

    @Test
    void workingItOutSavesNothing() throws Exception {
        receive(clay, yard, "100", "8");
        weights(line(clay, yard, "46"));
        JsonNode lots = read(getAs(admin, "/api/store/lots?plantId=" + plantId));
        assertDecimal(lots.at("/content/0/quantityLeft"), "100");
    }

    @Test
    void chargingTheWorkedOutWeightDeliversTheDryWeight() throws Exception {
        receive(clay, yard, "10", "10");
        receive(clay, yard, "20", "20");
        BigDecimal wet = weights(line(clay, yard, "12")).at("/lines/0/wetQuantity").decimalValue();

        long id = charge(wet);

        JsonNode trucks = read(getAs(admin, "/api/production/trace/body-batches/" + id).andExpect(status().isOk()));
        assertThat(trucks).hasSize(2);
        assertDecimal(trucks.at("/0/quantity"), "10");
        assertDecimal(trucks.at("/0/dryQuantity"), "9");
        assertDecimal(trucks.at("/1/quantity"), "3.75");
        assertDecimal(trucks.at("/1/dryQuantity"), "3");
    }

    @Test
    void aTruckWithNoMoistureHasNoDryFigureInTheTrace() throws Exception {
        receive(clay, yard, "10", null);
        JsonNode truck = read(getAs(admin, "/api/production/trace/body-batches/" + charge(new BigDecimal("4")))
                .andExpect(status().isOk())).at("/0");
        assertDecimal(truck.get("quantity"), "4");
        assertThat(truck.has("dryQuantity")).isFalse();
    }

    @Test
    void itNeedsBodyPrepEntryAndStaysInsideThePlant() throws Exception {
        Map<String, Object> body = Map.of("plantId", plantId, "lines", List.of(line(clay, yard, "1")));

        long viewer = createRole(admin, "Viewer W", "PRODUCTION_VIEW");
        createUser(admin, "viewer.w", "password-1", viewer, plantId);
        postAs(login("viewer.w", "password-1"), WEIGHTS, body).andExpect(status().isForbidden());

        long otherPlant = createPlant(admin, companyId, "WET-2");
        long supervisor = createRole(admin, "Body W", "BODY_PREP_ENTRY");
        createUser(admin, "body.w", "password-1", supervisor, otherPlant);
        postAs(login("body.w", "password-1"), WEIGHTS, body).andExpect(status().isForbidden());
    }

    @Test
    void anEmptyListOrAZeroQuantityIsRefused() throws Exception {
        postAs(admin, WEIGHTS, Map.of("plantId", plantId, "lines", List.of())).andExpect(status().isBadRequest());
        postAs(admin, WEIGHTS, Map.of("plantId", plantId, "lines", List.of(line(clay, yard, "0")))).andExpect(status().isBadRequest());
    }

    // ------------------------------------------------------------------ helpers

    /** Charges a mill with this weight of clay from the yard. */
    private long charge(BigDecimal quantity) throws Exception {
        long mill = read(postAs(admin, "/api/masters/machines", Map.of("plantId", plantId, "code", "BM-W",
                "name", "Mill", "type", "BALL_MILL")).andExpect(status().isCreated())).get("id").asLong();
        Map<String, Object> batch = new HashMap<>();
        batch.put("plantId", plantId);
        batch.put("prodDate", today.toString());
        batch.put("shiftId", idOf("shifts", "A"));
        batch.put("ballMillId", mill);
        batch.put("chargeTon", 12);
        batch.put("millingStart", LocalDateTime.now(INDIA).withNano(0).minusHours(1).toString());
        batch.put("lines", List.of(Map.of("materialId", clay, "locationId", yard, "quantity", quantity)));
        long id = read(postAs(admin, "/api/production/body-batches", batch).andExpect(status().isCreated())).get("id").asLong();
        postAs(admin, "/api/production/body-batches/" + id + "/charge", Map.of()).andExpect(status().isOk());
        return id;
    }

    @SafeVarargs
    private JsonNode weights(Map<String, Object>... lines) throws Exception {
        return read(postAs(admin, WEIGHTS, Map.of("plantId", plantId, "lines", new ArrayList<>(List.of(lines))))
                .andExpect(status().isOk()));
    }

    private static Map<String, Object> line(long materialId, long locationId, String dry) {
        return Map.of("materialId", materialId, "locationId", locationId, "dryQuantity", new BigDecimal(dry));
    }

    private void receive(long materialId, long locationId, String quantity, String moisture) throws Exception {
        Map<String, Object> line = new HashMap<>();
        line.put("materialId", materialId);
        line.put("locationId", locationId);
        line.put("receivedQty", new BigDecimal(quantity));
        line.put("rate", new BigDecimal("1000"));
        line.put("moisturePct", moisture == null ? null : new BigDecimal(moisture));
        Map<String, Object> grn = new HashMap<>();
        grn.put("plantId", plantId);
        grn.put("docType", "GRN");
        grn.put("docDate", LocalDate.now().toString());
        grn.put("supplierId", supplier);
        grn.put("lines", List.of(line));
        long id = read(postAs(admin, "/api/store/documents", grn).andExpect(status().isCreated())).get("id").asLong();
        postAs(admin, "/api/store/documents/" + id + "/post", Map.of()).andExpect(status().isOk());
    }

    private long godown(String code, String name) throws Exception {
        return read(postAs(admin, "/api/store/locations", Map.of("plantId", plantId, "code", code, "name", name))
                .andExpect(status().isCreated())).get("id").asLong();
    }

    private long material(String code, String unit) throws Exception {
        return read(postAs(admin, "/api/masters/materials", Map.of("code", code, "name", "Material " + code,
                "groupId", idOf("material-groups", "BODY"), "unitId", idOf("units", unit)))
                .andExpect(status().isCreated())).get("id").asLong();
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
