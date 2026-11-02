package com.ceramicerp.common;

import com.ceramicerp.IntegrationTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Step 11a: the React app served from inside the jar. src/test/resources/static stands in for a release build. */
class SinglePageAppIntegrationTest extends IntegrationTestBase {

    @Test
    void thePagesOpenWithoutLoggingInSoTheLoginPageCanLoad() throws Exception {
        mvc.perform(get("/index.html")).andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Glaze test page")));
    }

    @Test
    void reloadingAPageGetsTheAppNotAnError() throws Exception {
        for (String page : new String[]{"/store/lots", "/production/body-batches/12", "/login"}) {
            mvc.perform(get(page)).andExpect(status().isOk())
                    .andExpect(content().string(org.hamcrest.Matchers.containsString("Glaze test page")));
        }
    }

    @Test
    void aMissingFileIsAnErrorNotTheApp() throws Exception {
        mvc.perform(get("/assets/gone-123.js")).andExpect(status().isNotFound());
        mvc.perform(get("/gone.png")).andExpect(status().isNotFound());
    }

    @Test
    void theApiStillNeedsALogin() throws Exception {
        mvc.perform(get("/api/plants")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/no-such-thing")).andExpect(status().isUnauthorized());
        mvc.perform(get("/actuator/metrics")).andExpect(status().isUnauthorized());
    }

    @Test
    void aWrongApiAddressStaysAnErrorEvenWhenLoggedIn() throws Exception {
        String body = getAs(adminToken(), "/api/no-such-thing").andExpect(status().isNotFound())
                .andReturn().getResponse().getContentAsString();
        assertThat(body).doesNotContain("Glaze test page");
    }

    @Test
    void onlyReadingIsOpen() throws Exception {
        mvc.perform(post("/store/lots").contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isUnauthorized());
    }

    @Test
    void theAppsFilesAreKeptButIndexIsAskedForEveryTime() throws Exception {
        mvc.perform(get("/assets/app-abc123.js")).andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", org.hamcrest.Matchers.containsString("immutable")));
        mvc.perform(get("/store/lots")).andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", org.hamcrest.Matchers.containsString("no-cache")));
    }

    @Test
    void theRuleForWhatCountsAsAPage() {
        assertThat(SinglePageAppConfig.AppPageResolver.isPage("store/lots")).isTrue();
        assertThat(SinglePageAppConfig.AppPageResolver.isPage("")).isTrue();
        assertThat(SinglePageAppConfig.AppPageResolver.isPage("api/plants")).isFalse();
        assertThat(SinglePageAppConfig.AppPageResolver.isPage("actuator/env")).isFalse();
        assertThat(SinglePageAppConfig.AppPageResolver.isPage("logo.svg")).isFalse();
    }
}
