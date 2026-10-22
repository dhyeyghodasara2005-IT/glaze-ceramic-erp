package com.ceramicerp.store;

import com.ceramicerp.IntegrationTestBase;
import com.ceramicerp.numbering.DocumentNumberService;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class StoreIntegrationTest extends IntegrationTestBase {

    private static final String DOCS = "/api/store/documents";
    private static final String FY = DocumentNumberService.financialYear(LocalDate.now());

    private String admin;
    private long companyId;
    private long plantId;
    private long main;      // godown "Main godown"
    private long yard;      // godown "Clay yard"
    private long clay;      // TON (3 decimals), reorder level 50
    private long bags;      // BAG (0 decimals), no reorder level
    private long supplier;

    @BeforeEach
    void setUp() throws Exception {
        admin = adminToken();
        companyId = createCompany(admin, "STRCO");
        plantId = createPlant(admin, companyId, "STR-1");
        main = createGodown(plantId, "MAIN", "Main godown");
        yard = createGodown(plantId, "YARD", "Clay yard");
        clay = createMaterial("CLAY-T", "TON", 50);
        bags = createMaterial("BAG-T", "BAG", null);
        supplier = createParty("SUPPLIER", "Shree Clay Mines");
    }

    // ------------------------------------------------------------------ godowns

    @Test
    void godownCodeIsUniqueWithinAPlantOnly() throws Exception {
        postAs(admin, "/api/store/locations", Map.of("plantId", plantId, "code", "main", "name", "Again"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Godown code MAIN already exists in plant STR-1"));

        long otherPlant = createPlant(admin, companyId, "STR-2");
        postAs(admin, "/api/store/locations", Map.of("plantId", otherPlant, "code", "main", "name", "Main"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value("MAIN"));
    }

    // ------------------------------------------------------------------ GRN

    @Test
    void postedGrnAddsTheAcceptedQuantityAtItsRate() throws Exception {
        long id = create(grn(grnLine(clay, main, "12", "2", "1000")));

        JsonNode draft = read(getAs(admin, DOCS + "/" + id));
        assertThat(draft.get("status").asText()).isEqualTo("DRAFT");
        assertThat(draft.hasNonNull("docNo")).isFalse();     // no number until posted
        assertDecimal(draft.at("/lines/0/quantity"), "10");     // 12 received - 2 rejected
        assertDecimal(draft.get("totalValue"), "10000");
        assertDecimal(stockOf("CLAY-T").get("quantity"), "0");  // a draft doesn't touch stock

        JsonNode posted = read(postAs(admin, DOCS + "/" + id + "/post", Map.of()).andExpect(status().isOk()));
        assertThat(posted.get("docNo").asText()).isEqualTo("STR-1/GRN/" + FY + "/0001");
        assertThat(posted.get("status").asText()).isEqualTo("POSTED");
        assertThat(posted.get("postedBy").asText()).isEqualTo(adminUsername);

        JsonNode stock = stockOf("CLAY-T");
        assertDecimal(stock.get("quantity"), "10");
        assertDecimal(stock.get("value"), "10000");
        assertDecimal(stock.get("averageRate"), "1000");
        assertThat(stock.get("low").asBoolean()).isTrue();      // 10 <= reorder level 50
        assertThat(stock.at("/locations/0/locationCode").asText()).isEqualTo("MAIN");
    }

    @Test
    void numbersRunPerTypeAndAreGivenOnlyWhenPosted() throws Exception {
        long draft = create(grn(grnLine(clay, main, "1", null, "10")));
        JsonNode first = postNew(grn(grnLine(clay, main, "1", null, "10")));
        JsonNode second = postNew(grn(grnLine(clay, main, "1", null, "10")));
        JsonNode issue = postNew(issue(issueLine(clay, main, "1")));

        assertThat(first.get("docNo").asText()).isEqualTo("STR-1/GRN/" + FY + "/0001");
        assertThat(second.get("docNo").asText()).isEqualTo("STR-1/GRN/" + FY + "/0002");
        assertThat(issue.get("docNo").asText()).isEqualTo("STR-1/ISS/" + FY + "/0001");
        // The draft made first got no number - so deleting drafts never leaves gaps.
        JsonNode stillDraft = read(postAs(admin, DOCS + "/" + draft + "/post", Map.of()));
        assertThat(stillDraft.get("docNo").asText()).isEqualTo("STR-1/GRN/" + FY + "/0003");
    }

    @Test
    void financialYearStartsInApril() {
        assertThat(DocumentNumberService.financialYear(LocalDate.of(2026, 10, 15))).isEqualTo("26-27");
        assertThat(DocumentNumberService.financialYear(LocalDate.of(2027, 3, 31))).isEqualTo("26-27");
        assertThat(DocumentNumberService.financialYear(LocalDate.of(2027, 4, 1))).isEqualTo("27-28");
        assertThat(DocumentNumberService.financialYear(LocalDate.of(2099, 4, 1))).isEqualTo("99-00");
    }

    // ------------------------------------------------------------------ issue

    @Test
    void issuesGoOutAtTheWeightedAverageRate() throws Exception {
        postNew(grn(grnLine(clay, main, "10", null, "1000")));
        postNew(grn(grnLine(clay, main, "10", null, "2000")));
        // now 20 TON worth 30,000 -> average 1,500 per TON

        JsonNode issue = postNew(issue(issueLine(clay, main, "5")));
        assertDecimal(issue.at("/lines/0/rate"), "1500");
        assertDecimal(issue.at("/lines/0/value"), "7500");
        assertDecimal(issue.get("totalValue"), "7500");

        JsonNode stock = stockOf("CLAY-T");
        assertDecimal(stock.get("quantity"), "15");
        assertDecimal(stock.get("value"), "22500");
    }

    @Test
    void takingOutEverythingLeavesNoValueBehind() throws Exception {
        postNew(grn(grnLine(clay, main, "3", null, "333.3333")));   // 999.9999 -> 1,000.00
        postNew(issue(issueLine(clay, main, "1")));
        postNew(issue(issueLine(clay, main, "2")));

        JsonNode stock = stockOf("CLAY-T");
        assertDecimal(stock.get("quantity"), "0");
        assertDecimal(stock.get("value"), "0");
    }

    @Test
    void issuingMoreThanInStockIsRefused() throws Exception {
        postNew(grn(grnLine(clay, main, "10", null, "1000")));
        long id = create(issue(issueLine(clay, main, "10.5")));

        postAs(admin, DOCS + "/" + id + "/post", Map.of())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message")
                        .value("Not enough CLAY-T in Main godown: available 10 TON, needed 10.5 TON"));
    }

    @Test
    void stockInOneGodownCantBeIssuedFromAnother() throws Exception {
        postNew(grn(grnLine(clay, yard, "10", null, "1000")));
        long id = create(issue(issueLine(clay, main, "1")));

        postAs(admin, DOCS + "/" + id + "/post", Map.of())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Not enough CLAY-T in Main godown: available 0 TON, needed 1 TON"));
    }

    // ------------------------------------------------------------------ adjustment

    @Test
    void adjustmentsAddAndReduceStock() throws Exception {
        JsonNode opening = postNew(adjustment("OPENING", adjLine(clay, main, "100", "900")));
        assertThat(opening.get("docNo").asText()).isEqualTo("STR-1/ADJ/" + FY + "/0001");
        assertDecimal(opening.get("totalValue"), "90000");

        // Physical count found 2 TON less. A rate typed on a reduction is ignored: it goes out at the average.
        JsonNode count = postNew(adjustment("PHYSICAL_COUNT", adjLine(clay, main, "-2", "5000")));
        assertDecimal(count.at("/lines/0/rate"), "900");
        assertDecimal(count.at("/lines/0/value"), "-1800");

        JsonNode stock = stockOf("CLAY-T");
        assertDecimal(stock.get("quantity"), "98");
        assertDecimal(stock.get("value"), "88200");
    }

    // ------------------------------------------------------------------ cancel

    @Test
    void cancellingAnIssuePutsBackTheExactValue() throws Exception {
        postNew(grn(grnLine(clay, main, "10", null, "1000")));
        postNew(grn(grnLine(clay, main, "10", null, "2000")));
        JsonNode issue = postNew(issue(issueLine(clay, main, "5")));

        JsonNode cancelled = read(postAs(admin, DOCS + "/" + issue.get("id").asLong() + "/cancel",
                Map.of("reason", "Wrong machine")).andExpect(status().isOk()));
        assertThat(cancelled.get("status").asText()).isEqualTo("CANCELLED");
        assertThat(cancelled.get("cancelReason").asText()).isEqualTo("Wrong machine");

        JsonNode stock = stockOf("CLAY-T");
        assertDecimal(stock.get("quantity"), "20");
        assertDecimal(stock.get("value"), "30000");

        // Ledger, newest first: the reversal, the issue, GRN 2, GRN 1.
        JsonNode ledger = read(getAs(admin, "/api/store/stock/ledger?plantId=" + plantId + "&materialId=" + clay));
        assertThat(ledger.get("totalElements").asInt()).isEqualTo(4);
        JsonNode reversal = ledger.at("/content/0");
        assertThat(reversal.get("reversal").asBoolean()).isTrue();
        assertDecimal(reversal.get("quantity"), "5");
        assertDecimal(reversal.get("value"), "7500");
        assertDecimal(reversal.get("balanceQty"), "20");
        assertDecimal(ledger.at("/content/1/quantity"), "-5");
    }

    @Test
    void grnCantBeCancelledOnceItsStockIsUsed() throws Exception {
        JsonNode grn = postNew(grn(grnLine(clay, main, "10", null, "1000")));
        postNew(issue(issueLine(clay, main, "4")));

        postAs(admin, DOCS + "/" + grn.get("id").asLong() + "/cancel", Map.of("reason", "Duplicate entry"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Can't cancel STR-1/GRN/" + FY + "/0001. "
                        + "Not enough CLAY-T in Main godown: available 6 TON, needed 10 TON"));
    }

    @Test
    void onlyPostedDocumentsCanBeCancelledAndAReasonIsRequired() throws Exception {
        long draft = create(grn(grnLine(clay, main, "1", null, "10")));
        postAs(admin, DOCS + "/" + draft + "/cancel", Map.of("reason", "Typo"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Only a posted document can be cancelled"));
        postAs(admin, DOCS + "/" + draft + "/cancel", Map.of("reason", " "))
                .andExpect(status().isBadRequest());
    }

    // ------------------------------------------------------------------ drafts

    @Test
    void draftsCanBeEditedAndDeletedButPostedDocumentsCant() throws Exception {
        long id = create(grn(grnLine(clay, main, "10", null, "1000")));

        Map<String, Object> changed = grn(grnLine(bags, yard, "40", null, "25"), grnLine(clay, main, "5", null, "900"));
        putAs(admin, DOCS + "/" + id, changed)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lines.length()").value(2))
                .andExpect(jsonPath("$.lines[0].lineNo").value(1))
                .andExpect(jsonPath("$.lines[0].materialCode").value("BAG-T"))
                .andExpect(jsonPath("$.lines[1].locationCode").value("MAIN"));
        assertDecimal(read(getAs(admin, DOCS + "/" + id)).get("totalValue"), "5500");   // 40 x 25 + 5 x 900

        deleteAs(admin, DOCS + "/" + id).andExpect(status().isNoContent());
        getAs(admin, DOCS + "/" + id).andExpect(status().isNotFound());

        long posted = postNew(grn(grnLine(clay, main, "10", null, "1000"))).get("id").asLong();
        putAs(admin, DOCS + "/" + posted, changed)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Only a draft can be edited. STR-1/GRN/" + FY + "/0001 is posted"));
        deleteAs(admin, DOCS + "/" + posted).andExpect(status().isConflict());
    }

    @Test
    void documentListFiltersByTypeStatusAndText() throws Exception {
        Map<String, Object> withBill = grn(grnLine(clay, main, "1", null, "10"));
        withBill.put("supplierInvoiceNo", "inv-778");
        postNew(withBill);
        create(grn(grnLine(clay, main, "1", null, "10")));
        postNew(issue(issueLine(clay, main, "1")));

        String list = DOCS + "?plantId=" + plantId;
        getAs(admin, list + "&type=GRN").andExpect(jsonPath("$.totalElements").value(2));
        getAs(admin, list + "&type=GRN&status=DRAFT").andExpect(jsonPath("$.totalElements").value(1));
        getAs(admin, list + "&type=GRN&search=INV-778")
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].supplierName").value("Shree Clay Mines"));
        getAs(admin, list + "&type=ISSUE").andExpect(jsonPath("$.totalElements").value(1));

        getAs(admin, "/api/search?q=INV-778").andExpect(jsonPath("$[*].type", hasItem("grn")));
        getAs(admin, "/api/search?q=STR-1/ISS")
                .andExpect(jsonPath("$[*].code", hasItem("STR-1/ISS/" + FY + "/0001")));
    }

    // ------------------------------------------------------------------ rules

    @Test
    void entriesAreChecked() throws Exception {
        long dealer = createParty("DEALER", "Some Dealer");
        Map<String, Object> toDealer = grn(grnLine(clay, main, "1", null, "10"));
        toDealer.put("supplierId", dealer);
        expectConflict(toDealer, "Some Dealer is not a supplier");

        expectConflict(grn(grnLine(clay, main, "5", "6", "100")), "Line 1: rejected can't be more than received");
        expectConflict(grn(grnLine(bags, main, "1.5", null, "20")),
                "Line 1: quantity in BAG can have at most 0 decimal places");

        long otherPlant = createPlant(admin, companyId, "STR-2");
        long otherGodown = createGodown(otherPlant, "MAIN", "Main");
        expectConflict(grn(grnLine(clay, otherGodown, "1", null, "10")), "Line 1: choose a godown of plant STR-1");

        Map<String, Object> future = grn(grnLine(clay, main, "1", null, "10"));
        future.put("docDate", LocalDate.now().plusDays(1).toString());
        expectConflict(future, "The date can't be in the future");

        expectConflict(adjustment(null, adjLine(clay, main, "5", "100")), "Select the reason for the adjustment");
        expectConflict(adjustment("OPENING", adjLine(clay, main, "5", null)),
                "Line 1: opening stock needs a positive quantity and a rate");
        expectConflict(issue(issueLine(clay, main, "0")), "Line 1: enter the quantity to issue");

        Map<String, Object> noLines = grn();
        postAs(admin, DOCS, noLines).andExpect(status().isBadRequest());
    }

    @Test
    void storeKeepersWorkOnlyInTheirOwnPlant() throws Exception {
        long otherPlant = createPlant(admin, companyId, "STR-2");
        long roleId = createRole(admin, "Store keeper T", "STORE_VIEW", "STORE_ENTRY");
        createUser(admin, "store.t", "password-1", roleId, plantId);
        String keeper = login("store.t", "password-1");

        getAs(keeper, DOCS + "?plantId=" + plantId + "&type=GRN").andExpect(status().isOk());
        getAs(keeper, DOCS + "?plantId=" + otherPlant + "&type=GRN").andExpect(status().isForbidden());
        getAs(keeper, "/api/store/stock?plantId=" + otherPlant).andExpect(status().isForbidden());
        getAs(keeper, "/api/store/locations?plantId=" + otherPlant).andExpect(status().isForbidden());

        // The dropdowns work without any master / party permission.
        getAs(keeper, "/api/store/lookups/materials").andExpect(jsonPath("$[*].code", hasItem("CLAY-T")));
        getAs(keeper, "/api/store/lookups/suppliers").andExpect(jsonPath("$[*].name", hasItem("Shree Clay Mines")));

        long id = read(postAs(keeper, DOCS, grn(grnLine(clay, main, "1", null, "10")))
                .andExpect(status().isCreated())).get("id").asLong();
        postAs(keeper, DOCS + "/" + id + "/post", Map.of()).andExpect(status().isOk());
        // ...but cancelling needs STORE_CANCEL
        postAs(keeper, DOCS + "/" + id + "/cancel", Map.of("reason", "Oops")).andExpect(status().isForbidden());
    }

    // ------------------------------------------------------------------ helpers

    private void expectConflict(Map<String, Object> body, String message) throws Exception {
        postAs(admin, DOCS, body).andExpect(status().isConflict()).andExpect(jsonPath("$.message").value(message));
    }

    /** Saves a draft and returns its id. */
    private long create(Map<String, Object> body) throws Exception {
        return read(postAs(admin, DOCS, body).andExpect(status().isCreated())).get("id").asLong();
    }

    /** Saves a draft and posts it; returns the posted document. */
    private JsonNode postNew(Map<String, Object> body) throws Exception {
        return read(postAs(admin, DOCS + "/" + create(body) + "/post", Map.of()).andExpect(status().isOk()));
    }

    private JsonNode stockOf(String materialCode) throws Exception {
        for (JsonNode row : read(getAs(admin, "/api/store/stock?plantId=" + plantId).andExpect(status().isOk()))) {
            if (row.get("materialCode").asText().equals(materialCode)) return row;
        }
        throw new AssertionError(materialCode + " not in stock list");
    }

    private static void assertDecimal(JsonNode node, String expected) {
        assertThat(node.decimalValue()).isEqualByComparingTo(expected);
    }

    @SafeVarargs
    private Map<String, Object> grn(Map<String, Object>... lines) {
        Map<String, Object> body = header("GRN", lines);
        body.put("supplierId", supplier);
        body.put("vehicleNo", "gj 36 t 1234");
        return body;
    }

    @SafeVarargs
    private Map<String, Object> issue(Map<String, Object>... lines) {
        Map<String, Object> body = header("ISSUE", lines);
        body.put("issuedTo", "Ball mill section");
        return body;
    }

    @SafeVarargs
    private Map<String, Object> adjustment(String reason, Map<String, Object>... lines) {
        Map<String, Object> body = header("ADJUSTMENT", lines);
        body.put("reason", reason);
        return body;
    }

    private Map<String, Object> header(String type, Map<String, Object>[] lines) {
        Map<String, Object> body = new HashMap<>();
        body.put("plantId", plantId);
        body.put("docType", type);
        body.put("docDate", LocalDate.now().toString());
        body.put("lines", List.of(lines));
        return body;
    }

    private static Map<String, Object> grnLine(long materialId, long locationId, String received, String rejected,
                                               String rate) {
        Map<String, Object> line = line(materialId, locationId);
        line.put("receivedQty", new BigDecimal(received));
        line.put("rejectedQty", rejected == null ? null : new BigDecimal(rejected));
        line.put("rate", new BigDecimal(rate));
        return line;
    }

    private static Map<String, Object> issueLine(long materialId, long locationId, String qty) {
        Map<String, Object> line = line(materialId, locationId);
        line.put("quantity", new BigDecimal(qty));
        return line;
    }

    private static Map<String, Object> adjLine(long materialId, long locationId, String qty, String rate) {
        Map<String, Object> line = issueLine(materialId, locationId, qty);
        line.put("rate", rate == null ? null : new BigDecimal(rate));
        return line;
    }

    private static Map<String, Object> line(long materialId, long locationId) {
        Map<String, Object> line = new HashMap<>();
        line.put("materialId", materialId);
        line.put("locationId", locationId);
        return line;
    }

    private long createGodown(long plant, String code, String name) throws Exception {
        return read(postAs(admin, "/api/store/locations", Map.of("plantId", plant, "code", code, "name", name))
                .andExpect(status().isCreated())).get("id").asLong();
    }

    private long createMaterial(String code, String unit, Integer reorderLevel) throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("code", code);
        body.put("name", "Material " + code);
        body.put("groupId", idOf("material-groups", "BODY"));
        body.put("unitId", idOf("units", unit));
        body.put("hsnCode", "2508");
        body.put("gstRate", 5);
        body.put("reorderLevel", reorderLevel);
        return read(postAs(admin, "/api/masters/materials", body).andExpect(status().isCreated())).get("id").asLong();
    }

    private long createParty(String type, String name) throws Exception {
        return read(postAs(admin, "/api/parties", Map.of("partyType", type, "name", name,
                "gstRegistration", "UNREGISTERED")).andExpect(status().isCreated())).get("id").asLong();
    }

    private long idOf(String master, String code) throws Exception {
        for (JsonNode n : read(getAs(admin, "/api/masters/" + master))) {
            if (n.get("code").asText().equals(code)) return n.get("id").asLong();
        }
        throw new AssertionError(code + " not found in " + master);
    }
}
