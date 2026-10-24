package com.ceramicerp.sales;

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
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Step 6b: sales orders from loading slips, billed to a dealer or a jobwork principal, with products matched. */
class SalesOrderIntegrationTest extends IntegrationTestBase {

    private static final String ORDERS = "/api/sales/orders";
    /** The product line of the sample slip of 24 Oct 2026. */
    private static final String FLAVOUR = "O7VM 600X1200-2 FLAVOUR IVORY PDR-P";

    private final LocalDate today = LocalDate.now(ZoneId.of("Asia/Kolkata"));

    private String admin;
    private long plantId, premium, standard;
    private long flavour, flavourBig, ivory, statA, statB;
    private long dealer, principal, transporter;

    @BeforeEach
    void setUp() throws Exception {
        admin = adminToken();
        long companyId = createCompany(admin, "SOCO");
        plantId = createPlant(admin, companyId, "SO-1");
        premium = idOf("grades", "PREMIUM");
        standard = idOf("grades", "STANDARD");

        // 600X1200 boxes weigh 26.6 kg (the sample slip: 195 boxes, 5192.5 kg)
        long size = idOf("sizes", "600X1200");
        Map<String, Object> sizeBody = new HashMap<>(Map.of("code", "600X1200", "name", "600 x 1200 mm", "lengthMm", 600,
                "widthMm", 1200, "thicknessMm", 9.0, "pcsPerBox", 2, "boxesPerPallet", 50, "active", true));
        sizeBody.put("boxWeightKg", 26.6);
        putAs(admin, "/api/masters/sizes/" + size, sizeBody).andExpect(status().isOk());
        long bigSize = read(postAs(admin, "/api/masters/sizes", Map.of("code", "800X1600", "name", "800 x 1600 mm",
                "lengthMm", 800, "widthMm", 1600, "thicknessMm", 9.0, "pcsPerBox", 2))
                .andExpect(status().isCreated())).get("id").asLong();

        flavour = design("FLV-IVORY", "Flavour Ivory", size);
        flavourBig = design("FLV-IVORY-16", "Flavour Ivory", bigSize);   // same name, bigger size
        ivory = design("IVORY-PLAIN", "Ivory", size);                    // a shorter name inside the longer one
        statA = design("STAT-A", "Statuario", size);                     // two designs, one name, one size
        statB = design("STAT-B", "Statuario", size);

        Map<String, Object> shree = party("DEALER", "Shree Tiles");
        shree.put("city", "Jaipur");
        shree.put("creditLimit", 500000);
        shree.put("creditDays", 30);
        dealer = read(postAs(admin, "/api/parties", shree).andExpect(status().isCreated())).get("id").asLong();
        JsonNode orient = read(postAs(admin, "/api/parties", party("JOBWORK", "Orient Vitrified")).andExpect(status().isCreated()));
        assertThat(orient.get("code").asText()).isEqualTo("JWP-0001");
        principal = orient.get("id").asLong();
        transporter = read(postAs(admin, "/api/parties", party("TRANSPORTER", "Morbi Roadways"))
                .andExpect(status().isCreated())).get("id").asLong();

        // 140 Flavour Ivory Premium boxes in two batches and pillars: any batch may be loaded
        long godown = read(postAs(admin, "/api/fg/godowns", Map.of("plantId", plantId, "code", "FG-1", "name", "FG"))
                .andExpect(status().isCreated())).get("id").asLong();
        Map<String, Long> pillars = new HashMap<>();
        read(postAs(admin, "/api/fg/godowns/" + godown + "/pillars", Map.of("prefix", "A", "from", 1, "to", 2)))
                .get("pillars").forEach(p -> pillars.put(p.get("code").asText(), p.get("id").asLong()));
        List<Map<String, Object>> rows = List.of(
                Map.of("pillarId", pillars.get("A1"), "designId", flavour, "batchCode", "A-07", "gradeId", premium,
                        "shade", "01", "caliber", "C1", "boxes", 60),
                Map.of("pillarId", pillars.get("A2"), "designId", flavour, "batchCode", "A-03", "gradeId", premium,
                        "shade", "02", "caliber", "C1", "boxes", 80));
        long opening = read(postAs(admin, "/api/fg/documents", Map.of("plantId", plantId, "docType", "OPENING",
                "docDate", today.toString(), "lines", rows)).andExpect(status().isCreated())).get("id").asLong();
        postAs(admin, "/api/fg/documents/" + opening + "/post", Map.of()).andExpect(status().isOk());
    }

    @Test
    void descriptionsAreGuessedFromTheNamesThenRemembered() throws Exception {
        JsonNode match = read(postAs(admin, "/api/sales/match", Map.of("descriptions", List.of(FLAVOUR,
                "o7vm 800x1600-2 flavour ivory pdr-p", "IVORY 600X1200-S", "STATUARIO 600X1200-P", "SOMETHING NEW")))
                .andExpect(status().isOk()));
        // the longest name wins ("Flavour Ivory" over "Ivory"), then the size printed in it; "-P" = Premium
        assertThat(match.at("/0/how").asText()).isEqualTo("GUESSED");
        assertThat(match.at("/0/design/id").asLong()).isEqualTo(flavour);
        assertThat(match.at("/0/grade/code").asText()).isEqualTo("PREMIUM");
        assertThat(match.at("/1/design/id").asLong()).isEqualTo(flavourBig);
        assertThat(match.at("/2/design/id").asLong()).isEqualTo(ivory);
        assertThat(match.at("/2/grade/code").asText()).isEqualTo("STANDARD");
        // two designs fit equally: a person chooses
        assertThat(match.at("/3/how").asText()).isEqualTo("SEVERAL");
        assertThat(match.at("/3/candidates").size()).isEqualTo(2);
        assertThat(absent(match, "/3/design")).isTrue();
        assertThat(match.at("/4/how").asText()).isEqualTo("NONE");
        assertThat(absent(match, "/4/grade")).isTrue();

        // The office chooses Statuario B once and confirms ...
        long id = create(order("460143", "SALE", dealer, line(FLAVOUR, flavour, premium, 100),
                line("STATUARIO 600X1200-P", statB, premium, 20)));
        assertThat(read(getAs(admin, ORDERS + "/" + id)).at("/lines/0/remembered").asBoolean()).isFalse();
        JsonNode confirmed = read(postAs(admin, ORDERS + "/" + id + "/confirm", Map.of()).andExpect(status().isOk()));
        assertThat(confirmed.at("/lines/0/remembered").asBoolean()).isTrue();

        // ... and the next slip needs nobody, even typed a little differently.
        match = read(postAs(admin, "/api/sales/match", Map.of("descriptions", List.of("statuario  600x1200 - p", FLAVOUR))));
        assertThat(match.at("/0/how").asText()).isEqualTo("REMEMBERED");
        assertThat(match.at("/0/design/id").asLong()).isEqualTo(statB);
        assertThat(match.at("/0/grade/id").asLong()).isEqualTo(premium);
        assertThat(match.at("/1/how").asText()).isEqualTo("REMEMBERED");
    }

    @Test
    void aSlipIsEnteredOnlyOnce() throws Exception {
        JsonNode first = read(postAs(admin, ORDERS, order(" 460143 ", "SALE", dealer, line(FLAVOUR, flavour, premium, 100)))
                .andExpect(status().isCreated()));
        assertThat(first.get("slipNo").asText()).isEqualTo("460143");
        assertThat(first.get("status").asText()).isEqualTo("DRAFT");
        assertThat(first.get("source").asText()).isEqualTo("TYPED");
        postAs(admin, ORDERS, order("460143", "SALE", dealer, line(FLAVOUR, flavour, premium, 5)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Loading slip 460143 is already entered in plant SO-1"));

        // the same number in another plant is another slip
        long other = createPlant(admin, createCompany(admin, "SOCO2"), "SO-2");
        Map<String, Object> elsewhere = order("460143", "SALE", dealer, line(FLAVOUR, flavour, premium, 5));
        elsewhere.put("plantId", other);
        postAs(admin, ORDERS, elsewhere).andExpect(status().isCreated());

        Map<String, Object> future = order("460144", "SALE", dealer, line(FLAVOUR, flavour, premium, 5));
        future.put("slipDate", today.plusDays(1).toString());
        postAs(admin, ORDERS, future)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("The slip date can't be in the future"));
        Map<String, Object> late = order("460144", "SALE", dealer, line(FLAVOUR, flavour, premium, 5));
        late.put("slipDate", today.minusDays(1).toString());
        late.put("orderDate", today.toString());
        postAs(admin, ORDERS, late)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("The sales order date can't be after the slip date"));
    }

    @Test
    void theBillGoesToTheRightKindOfParty() throws Exception {
        postAs(admin, ORDERS, order("1", "SALE", principal, line(FLAVOUR, flavour, premium, 5)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Orient Vitrified is not a dealer. A sale is billed to a dealer."));
        postAs(admin, ORDERS, order("1", "JOBWORK", dealer, line(FLAVOUR, flavour, premium, 5)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Shree Tiles is not a jobwork principal. "
                        + "A jobwork order is billed to the company whose jobwork it is."));
        Map<String, Object> wrongTruck = order("1", "SALE", dealer, line(FLAVOUR, flavour, premium, 5));
        wrongTruck.put("transporterId", dealer);
        postAs(admin, ORDERS, wrongTruck)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Shree Tiles is not a transporter. Choose a transporter from Parties."));
        postAs(admin, ORDERS, order("1", "SALE", dealer, line(FLAVOUR, flavour, idOf("grades", "REJECT"), 5)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Reject tiles can't be sold (row 1)"));

        Map<String, Object> jobwork = order("1", "JOBWORK", principal, line(FLAVOUR, flavour, premium, 5));
        jobwork.put("transporterId", transporter);
        JsonNode created = read(postAs(admin, ORDERS, jobwork).andExpect(status().isCreated()));
        assertThat(created.at("/party/code").asText()).isEqualTo("JWP-0001");
        assertThat(created.at("/transporter/name").asText()).isEqualTo("Morbi Roadways");

        // the dealer's credit terms are on the order, to check before confirming
        JsonNode sale = read(postAs(admin, ORDERS, order("2", "SALE", dealer, line(FLAVOUR, flavour, premium, 5))));
        assertThat(sale.at("/party/creditLimit").asInt()).isEqualTo(500000);
        assertThat(sale.at("/party/creditDays").asInt()).isEqualTo(30);
        assertThat(sale.at("/party/city").asText()).isEqualTo("Jaipur");
    }

    @Test
    void confirmingNeedsEveryLineMatchedAndTheChecksCompareStockAndTheSlip() throws Exception {
        // Two lines of the same product share its 140 boxes; the third isn't matched yet.
        Map<String, Object> body = order("460143", "SALE", dealer, line(FLAVOUR, flavour, premium, 100),
                line(FLAVOUR, flavour, premium, 95), line("SOMETHING NEW", null, null, 10));
        body.put("slipBoxes", 200);
        body.put("slipWeightKg", 6000);
        JsonNode draft = read(postAs(admin, ORDERS, body).andExpect(status().isCreated()));
        long id = draft.get("id").asLong();
        assertThat(draft.at("/totals/boxes").asInt()).isEqualTo(205);
        assertThat(draft.at("/totals/boxesDiff").asInt()).isEqualTo(5);
        assertThat(absent(draft, "/totals/weightKg")).isTrue();      // a line without a design: no total weight
        assertThat(draft.at("/totals/unmatchedLines").asInt()).isEqualTo(1);
        assertThat(absent(draft, "/lines/2/inStock")).isTrue();
        postAs(admin, ORDERS + "/" + id + "/confirm", Map.of())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Choose the design and grade for row 3"));

        // Without the third line: 195 boxes, 195 x 26.6 = 5187.0 kg against the slip's 6000.
        body.put("lines", List.of(line(FLAVOUR, flavour, premium, 100), line(FLAVOUR, flavour, premium, 95)));
        JsonNode updated = read(putAs(admin, ORDERS + "/" + id, body).andExpect(status().isOk()));
        assertThat(updated.at("/totals/boxesDiff").asInt()).isEqualTo(-5);
        assertThat(updated.at("/totals/weightKg").decimalValue()).isEqualByComparingTo("5187.0");
        assertThat(updated.at("/totals/weightDiffKg").decimalValue()).isEqualByComparingTo("-813.0");
        assertThat(updated.at("/totals/weightOk").asBoolean()).isFalse();
        assertThat(updated.at("/totals/sqm").decimalValue()).isEqualByComparingTo("280.80");
        // stock: the first line is served first, both batches count
        assertThat(updated.at("/lines/0/inStock").asInt()).isEqualTo(140);
        assertThat(updated.at("/lines/0/shortBy").asInt()).isZero();
        assertThat(updated.at("/lines/1/shortBy").asInt()).isEqualTo(55);
        assertThat(updated.at("/totals/shortLines").asInt()).isEqualTo(1);

        // The printed totals of the sample slip: within 2% (box weights differ a little between systems).
        body.put("slipBoxes", 195);
        body.put("slipWeightKg", 5192.5);
        JsonNode slip = read(putAs(admin, ORDERS + "/" + id, body).andExpect(status().isOk()));
        assertThat(slip.at("/totals/boxesDiff").asInt()).isZero();
        assertThat(slip.at("/totals/weightDiffKg").decimalValue()).isEqualByComparingTo("-5.5");
        assertThat(slip.at("/totals/weightOk").asBoolean()).isTrue();

        // Short stock warns but doesn't block: production may pack the rest before the truck comes.
        JsonNode confirmed = read(postAs(admin, ORDERS + "/" + id + "/confirm", Map.of()).andExpect(status().isOk()));
        assertThat(confirmed.get("status").asText()).isEqualTo("CONFIRMED");
        assertThat(confirmed.get("confirmedBy").asText()).isEqualTo(adminUsername);
        putAs(admin, ORDERS + "/" + id, body)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Only a draft order can be edited. Slip 460143 is confirmed"));
        // nothing moved: the boxes leave at loading (step 6c)
        assertThat(read(getAs(admin, "/api/fg/stock?plantId=" + plantId)).findValues("boxes").stream()
                .mapToInt(JsonNode::asInt).sum()).isEqualTo(140);
    }

    @Test
    void reopenCancelAndWhoMayDoWhat() throws Exception {
        long role = createRole(admin, "Dispatch test", "SALES_VIEW", "SALES_ENTRY");
        createUser(admin, "dispatch.test", "Dispatch@123", role, plantId);
        String office = login("dispatch.test", "Dispatch@123");
        long viewRole = createRole(admin, "Sales viewer test", "SALES_VIEW");
        createUser(admin, "sales.viewer", "Viewer@123", viewRole, plantId);
        String viewer = login("sales.viewer", "Viewer@123");

        // the office needs no master or party rights for its dropdowns
        JsonNode dealers = read(getAs(office, "/api/sales/lookups/parties?type=DEALER").andExpect(status().isOk()));
        assertThat(dealers.findValuesAsText("name")).contains("Shree Tiles");
        getAs(office, "/api/sales/lookups/parties?type=SUPPLIER")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Sales orders use dealers, jobwork principals and transporters"));
        getAs(office, "/api/sales/lookups/designs").andExpect(status().isOk()).andExpect(jsonPath("$[*].code", hasItem("FLV-IVORY")));
        getAs(office, "/api/sales/lookups/grades").andExpect(status().isOk())
                .andExpect(jsonPath("$[*].code", hasItem("PREMIUM")));

        long id = read(postAs(office, ORDERS, order("460143", "SALE", dealer, line(FLAVOUR, flavour, premium, 50)))
                .andExpect(status().isCreated())).get("id").asLong();
        postAs(viewer, ORDERS, order("460144", "SALE", dealer, line(FLAVOUR, flavour, premium, 5)))
                .andExpect(status().isForbidden());
        postAs(viewer, "/api/sales/match", Map.of("descriptions", List.of(FLAVOUR))).andExpect(status().isForbidden());
        getAs(viewer, ORDERS + "/" + id).andExpect(status().isOk());

        postAs(office, ORDERS + "/" + id + "/confirm", Map.of()).andExpect(status().isOk());
        JsonNode reopened = read(postAs(office, ORDERS + "/" + id + "/reopen", Map.of()).andExpect(status().isOk()));
        assertThat(reopened.get("status").asText()).isEqualTo("DRAFT");
        assertThat(absent(reopened, "/confirmedAt")).isTrue();
        postAs(office, ORDERS + "/" + id + "/confirm", Map.of()).andExpect(status().isOk());

        // cancelling needs SALES_CANCEL and a reason
        postAs(office, ORDERS + "/" + id + "/cancel", Map.of("reason", "Dealer cancelled")).andExpect(status().isForbidden());
        postAs(admin, ORDERS + "/" + id + "/cancel", Map.of("reason", " ")).andExpect(status().isBadRequest());
        JsonNode cancelled = read(postAs(admin, ORDERS + "/" + id + "/cancel", Map.of("reason", "Dealer cancelled"))
                .andExpect(status().isOk()));
        assertThat(cancelled.get("cancelReason").asText()).isEqualTo("Dealer cancelled");
        assertThat(absent(cancelled, "/lines/0/inStock")).isTrue();     // nothing to check any more
        postAs(admin, ORDERS + "/" + id + "/cancel", Map.of("reason", "again"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Only a confirmed order can be cancelled. Delete a draft instead."));
        postAs(admin, ORDERS + "/" + id + "/reopen", Map.of()).andExpect(status().isConflict());

        // a draft is simply deleted
        long draft = read(postAs(office, ORDERS, order("460145", "SALE", dealer, line(FLAVOUR, flavour, premium, 5))))
                .get("id").asLong();
        deleteAs(office, ORDERS + "/" + draft).andExpect(status().isNoContent());
        getAs(office, ORDERS + "/" + draft).andExpect(status().isNotFound());

        // another plant's orders stay out of reach
        long other = createPlant(admin, createCompany(admin, "SOCO3"), "SO-3");
        Map<String, Object> elsewhere = order("9", "SALE", dealer, line(FLAVOUR, flavour, premium, 5));
        elsewhere.put("plantId", other);
        long otherId = read(postAs(admin, ORDERS, elsewhere).andExpect(status().isCreated())).get("id").asLong();
        getAs(office, ORDERS + "/" + otherId).andExpect(status().isForbidden());
        getAs(office, ORDERS + "?plantId=" + other).andExpect(status().isForbidden());
    }

    @Test
    void ordersAreFoundBySlipNoSalesOrderNoOrParty() throws Exception {
        Map<String, Object> sale = order("460143", "SALE", dealer, line(FLAVOUR, flavour, premium, 100));
        sale.put("orderRef", "3436547");
        create(sale);
        create(order("460150", "JOBWORK", principal, line(FLAVOUR, flavour, standard, 40)));

        getAs(admin, ORDERS + "?plantId=" + plantId)
                .andExpect(jsonPath("$.totalElements").value(2));
        getAs(admin, ORDERS + "?plantId=" + plantId + "&type=JOBWORK")
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].partyName").value("Orient Vitrified"))
                .andExpect(jsonPath("$.content[0].boxes").value(40))
                .andExpect(jsonPath("$.content[0].matched").value(true));
        getAs(admin, ORDERS + "?plantId=" + plantId + "&search=3436547")
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].slipNo").value("460143"));
        getAs(admin, ORDERS + "?plantId=" + plantId + "&search=shree")
                .andExpect(jsonPath("$.totalElements").value(1));

        getAs(admin, "/api/search?q=460143")
                .andExpect(jsonPath("$[*].type", hasItem("sales-order")))
                .andExpect(jsonPath("$[?(@.type == 'sales-order')].title", hasItem("Shree Tiles")));
    }

    // ------------------------------------------------------------------ helpers

    @SafeVarargs
    private Map<String, Object> order(String slipNo, String type, long partyId, Map<String, Object>... lines) {
        Map<String, Object> body = new HashMap<>();
        body.put("plantId", plantId);
        body.put("slipNo", slipNo);
        body.put("slipDate", today.toString());
        body.put("orderType", type);
        body.put("partyId", partyId);
        body.put("lines", new ArrayList<>(List.of(lines)));
        return body;
    }

    private static Map<String, Object> line(String description, Long designId, Long gradeId, int boxes) {
        Map<String, Object> line = new HashMap<>();
        line.put("description", description);
        line.put("designId", designId);
        line.put("gradeId", gradeId);
        line.put("boxes", boxes);
        line.put("mrpBox", 1616.00);
        line.put("mrpPiece", 808.00);
        line.put("batchRef", "A-07");
        return line;
    }

    /** Empty fields are left out of the JSON. */
    private static boolean absent(JsonNode node, String pointer) {
        return node.at(pointer).isMissingNode() || node.at(pointer).isNull();
    }

    private long create(Map<String, Object> body) throws Exception {
        return read(postAs(admin, ORDERS, body).andExpect(status().isCreated())).get("id").asLong();
    }

    private static Map<String, Object> party(String type, String name) {
        Map<String, Object> body = new HashMap<>();
        body.put("partyType", type);
        body.put("name", name);
        body.put("gstRegistration", "UNREGISTERED");
        return body;
    }

    private long design(String code, String name, long sizeId) throws Exception {
        return read(postAs(admin, "/api/masters/designs", Map.of("code", code, "name", name,
                "categoryId", idOf("categories", "GVT"), "sizeId", sizeId, "finishId", idOf("finishes", "GLOSSY"),
                "faces", 1)).andExpect(status().isCreated())).get("id").asLong();
    }

    private long idOf(String master, String code) throws Exception {
        for (JsonNode n : read(getAs(admin, "/api/masters/" + master))) {
            if (n.get("code").asText().equals(code)) return n.get("id").asLong();
        }
        throw new AssertionError(code + " not found in " + master);
    }
}
