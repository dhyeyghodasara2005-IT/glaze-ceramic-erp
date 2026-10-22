package com.ceramicerp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Shared setup for API tests. Each test runs in a transaction that is rolled back at the end,
 * so tests don't affect each other.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
public abstract class IntegrationTestBase {

    @Autowired
    protected MockMvc mvc;

    @Autowired
    protected ObjectMapper objectMapper;

    @Value("${app.bootstrap.admin-username}")
    protected String adminUsername;

    @Value("${app.bootstrap.admin-password}")
    protected String adminPassword;

    protected String login(String username, String password) throws Exception {
        String body = mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("username", username, "password", password))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).get("token").asText();
    }

    protected String adminToken() throws Exception {
        return login(adminUsername, adminPassword);
    }

    protected ResultActions getAs(String token, String url) throws Exception {
        return mvc.perform(get(url).header(HttpHeaders.AUTHORIZATION, "Bearer " + token));
    }

    protected ResultActions postAs(String token, String url, Object body) throws Exception {
        return mvc.perform(post(url).header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content(json(body)));
    }

    protected ResultActions putAs(String token, String url, Object body) throws Exception {
        return mvc.perform(put(url).header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content(json(body)));
    }

    protected ResultActions deleteAs(String token, String url) throws Exception {
        return mvc.perform(delete(url).header(HttpHeaders.AUTHORIZATION, "Bearer " + token));
    }

    protected JsonNode read(ResultActions result) throws Exception {
        return objectMapper.readTree(result.andReturn().getResponse().getContentAsString());
    }

    protected String json(Object value) throws Exception {
        return objectMapper.writeValueAsString(value);
    }

    // ---- helpers that create test data through the API ----

    protected long createCompany(String token, String code) throws Exception {
        return read(postAs(token, "/api/companies", Map.of("code", code, "name", code + " Ceramics"))
                .andExpect(status().isCreated())).get("id").asLong();
    }

    protected long createPlant(String token, long companyId, String code) throws Exception {
        return read(postAs(token, "/api/plants", Map.of("companyId", companyId, "code", code, "name", code + " Plant"))
                .andExpect(status().isCreated())).get("id").asLong();
    }

    protected long createRole(String token, String name, String... permissions) throws Exception {
        return read(postAs(token, "/api/roles", Map.of("name", name, "permissions", permissions))
                .andExpect(status().isCreated())).get("id").asLong();
    }

    protected long createUser(String token, String username, String password, long roleId, Long... plantIds)
            throws Exception {
        return read(postAs(token, "/api/users", Map.of(
                "username", username,
                "fullName", username + " Test",
                "password", password,
                "roleIds", new Long[]{roleId},
                "plantIds", plantIds,
                "allPlantsAccess", false))
                .andExpect(status().isCreated())).get("id").asLong();
    }
}
