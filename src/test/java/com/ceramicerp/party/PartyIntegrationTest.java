package com.ceramicerp.party;

import com.ceramicerp.IntegrationTestBase;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class PartyIntegrationTest extends IntegrationTestBase {

    private static final String URL = "/api/parties";
    /** 24 = Gujarat, PAN = AAACS1234A */
    private static final String GSTIN_GJ = "24AAACS1234A1Z5";

    private Map<String, Object> party(String type, String name) {
        Map<String, Object> body = new HashMap<>();
        body.put("partyType", type);
        body.put("name", name);
        body.put("gstRegistration", "UNREGISTERED");
        return body;
    }

    @Test
    void codesAreNumberedAutomaticallyPerType() throws Exception {
        String admin = adminToken();
        postAs(admin, URL, party("DEALER", "Shree Tiles")).andExpect(jsonPath("$.code").value("DLR-0001"));
        postAs(admin, URL, party("DEALER", "Om Ceramics")).andExpect(jsonPath("$.code").value("DLR-0002"));
        postAs(admin, URL, party("SUPPLIER", "Clay Mines")).andExpect(jsonPath("$.code").value("SUP-0001"));
    }

    @Test
    void gstinFillsStateAndPan() throws Exception {
        Map<String, Object> body = party("DEALER", "Gujarat Dealer");
        body.put("gstRegistration", "REGULAR");
        body.put("gstin", GSTIN_GJ.toLowerCase());
        postAs(adminToken(), URL, body)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.gstin").value(GSTIN_GJ))
                .andExpect(jsonPath("$.stateCode").value("24"))
                .andExpect(jsonPath("$.state").value("Gujarat"))
                .andExpect(jsonPath("$.pan").value("AAACS1234A"));
    }

    @Test
    void gstinRequiredForRegularParty() throws Exception {
        Map<String, Object> body = party("DEALER", "No GSTIN");
        body.put("gstRegistration", "REGULAR");
        postAs(adminToken(), URL, body)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("GSTIN is required for a regular party"));
    }

    @Test
    void stateMustMatchGstin() throws Exception {
        Map<String, Object> body = party("DEALER", "Wrong State");
        body.put("gstRegistration", "REGULAR");
        body.put("gstin", GSTIN_GJ);
        body.put("stateCode", "08"); // Rajasthan
        postAs(adminToken(), URL, body)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("State doesn't match the GSTIN (the GSTIN is from Gujarat)"));
    }

    @Test
    void sameGstinOncePerTypeButAllowedAcrossTypes() throws Exception {
        String admin = adminToken();
        Map<String, Object> dealer = party("DEALER", "Shah Traders");
        dealer.put("gstRegistration", "REGULAR");
        dealer.put("gstin", GSTIN_GJ);
        postAs(admin, URL, dealer).andExpect(status().isCreated());

        dealer.put("name", "Shah Traders again");
        postAs(admin, URL, dealer)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Another dealer already has GSTIN " + GSTIN_GJ));

        Map<String, Object> supplier = new HashMap<>(dealer);
        supplier.put("partyType", "SUPPLIER");
        postAs(admin, URL, supplier).andExpect(status().isCreated()); // same firm can also be a supplier
    }

    @Test
    void overseasPartyNeedsCountryAndNoGstin() throws Exception {
        String admin = adminToken();
        Map<String, Object> body = party("DEALER", "Dubai Importer");
        body.put("gstRegistration", "OVERSEAS");
        postAs(admin, URL, body)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Enter the country of the overseas party"));

        body.put("country", "United Arab Emirates");
        postAs(admin, URL, body)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.country").value("United Arab Emirates"))
                .andExpect(jsonPath("$.stateCode").doesNotExist());
    }

    @Test
    void creditLimitOnlyKeptForDealers() throws Exception {
        String admin = adminToken();
        Map<String, Object> supplier = party("SUPPLIER", "Frit Co");
        supplier.put("creditLimit", 500000);
        supplier.put("creditDays", 30);
        postAs(admin, URL, supplier)
                .andExpect(jsonPath("$.creditLimit").doesNotExist())
                .andExpect(jsonPath("$.creditDays").value(30));
    }

    @Test
    void jobworkPrincipalsAreBilledLikeDealers() throws Exception {
        String admin = adminToken();
        Map<String, Object> principal = party("JOBWORK", "Orient Vitrified");
        principal.put("creditLimit", 900000);
        postAs(admin, URL, principal)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value("JWP-0001"))
                .andExpect(jsonPath("$.creditLimit").value(900000));

        principal.put("name", "Orient again");
        principal.put("gstRegistration", "REGULAR");
        principal.put("gstin", GSTIN_GJ);
        postAs(admin, URL, principal).andExpect(status().isCreated());
        postAs(admin, URL, principal)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Another jobwork principal already has GSTIN " + GSTIN_GJ));
        getAs(admin, URL + "?type=JOBWORK").andExpect(jsonPath("$.totalElements").value(2));
    }

    @Test
    void formatChecks() throws Exception {
        Map<String, Object> body = party("SUPPLIER", "Bad Data");
        body.put("bankIfsc", "SBIN1234567");   // 5th character must be 0
        body.put("creditDays", 400);
        postAs(adminToken(), URL, body)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.bankIfsc").value("Invalid IFSC"))
                .andExpect(jsonPath("$.fieldErrors.creditDays").exists());
    }

    @Test
    void typeFilterSearchAndGlobalSearch() throws Exception {
        String admin = adminToken();
        Map<String, Object> dealer = party("DEALER", "Jaipur Tile House");
        dealer.put("city", "Jaipur");
        dealer.put("stateCode", "08");
        postAs(admin, URL, dealer).andExpect(status().isCreated());
        postAs(admin, URL, party("TRANSPORTER", "Jaipur Roadways")).andExpect(status().isCreated());

        getAs(admin, URL + "?type=DEALER&search=jaipur")
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].state").value("Rajasthan"));
        getAs(admin, "/api/search?q=jaipur")
                .andExpect(jsonPath("$[*].type", hasItem("dealer")))
                .andExpect(jsonPath("$[*].type", hasItem("transporter")));
    }

    @Test
    void partyTypeCannotChange() throws Exception {
        String admin = adminToken();
        Map<String, Object> body = party("DEALER", "Fixed Type");
        long id = read(postAs(admin, URL, body)).get("id").asLong();
        body.put("partyType", "SUPPLIER");
        putAs(admin, URL + "/" + id, body).andExpect(status().isConflict());
    }

    @Test
    void needsPartyPermission() throws Exception {
        String admin = adminToken();
        long roleId = createRole(admin, "Masters only", "MASTER_VIEW");
        createUser(admin, "nopar", "password-1", roleId);
        getAs(login("nopar", "password-1"), URL).andExpect(status().isForbidden());
    }
}
