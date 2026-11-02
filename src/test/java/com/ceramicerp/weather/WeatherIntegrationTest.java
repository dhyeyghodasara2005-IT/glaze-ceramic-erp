package com.ceramicerp.weather;

import com.ceramicerp.IntegrationTestBase;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.ResourceAccessException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Step 10c: the hourly weather. The weather service is replaced by a stand-in, so no test needs the internet. */
@TestPropertySource(properties = "app.weather.enabled=true")
class WeatherIntegrationTest extends IntegrationTestBase {

    private static final ZoneId INDIA = ZoneId.of("Asia/Kolkata");

    @MockitoBean
    private WeatherClient client;

    @Autowired
    private WeatherService service;

    private String admin;
    private long companyId;
    private long plantId;
    private final LocalDate today = LocalDate.now(INDIA);
    private final LocalDate yesterday = today.minusDays(1);

    @BeforeEach
    void setUp() throws Exception {
        admin = adminToken();
        companyId = createCompany(admin, "WTHCO");
        plantId = plant("WTH-1", "22.81730", "70.83780");
    }

    @Test
    void thePlantKeepsItsLocation() throws Exception {
        JsonNode plant = read(getAs(admin, "/api/plants/" + plantId).andExpect(status().isOk()));
        assertDecimal(plant.get("latitude"), "22.8173");
        assertDecimal(plant.get("longitude"), "70.8378");
    }

    @Test
    void aLocationNeedsBothFiguresAndSensibleOnes() throws Exception {
        Map<String, Object> half = plantBody("WTH-2");
        half.put("latitude", new BigDecimal("22.8"));
        postAs(admin, "/api/plants", half).andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Enter both the latitude and the longitude, or neither"));
        Map<String, Object> far = plantBody("WTH-3");
        far.put("latitude", new BigDecimal("91"));
        far.put("longitude", new BigDecimal("70"));
        postAs(admin, "/api/plants", far).andExpect(status().isBadRequest());
    }

    @Test
    void fetchingSavesTheHoursAndShowsTheLatest() throws Exception {
        LocalDateTime hour = yesterday.atTime(10, 0);
        when(client.fetch(any(), any(), anyInt())).thenReturn(List.of(
                hour(hour, "26.2", "66", "0"), hour(hour.plusHours(1), "28.4", "60", "1.25")));

        JsonNode status = read(postAs(admin, "/api/weather/collect?plantId=" + plantId, Map.of()).andExpect(status().isOk()));
        assertThat(status.get("enabled").asBoolean()).isTrue();
        assertThat(status.get("hasLocation").asBoolean()).isTrue();
        assertDecimal(status.get("temperatureC"), "28.4");
        assertDecimal(status.get("humidityPct"), "60");
        assertDecimal(status.get("rainMm"), "1.25");
        assertThat(status.get("latestHour").asText()).startsWith(yesterday + "T11:00");
    }

    @Test
    void hoursStillToComeAreNotSaved() throws Exception {
        LocalDateTime now = LocalDateTime.now(INDIA).withMinute(0).withSecond(0).withNano(0);
        when(client.fetch(any(), any(), anyInt())).thenReturn(List.of(hour(now.minusHours(1), "25", "50", "0"), hour(now.plusHours(2), "40", "10", "0")));
        JsonNode status = read(postAs(admin, "/api/weather/collect?plantId=" + plantId, Map.of()).andExpect(status().isOk()));
        assertDecimal(status.get("temperatureC"), "25");
    }

    @Test
    void theSameHourIsUpdatedNotSavedTwice() throws Exception {
        LocalDateTime hour = yesterday.atTime(10, 0);
        when(client.fetch(any(), any(), anyInt())).thenReturn(List.of(hour(hour, "26", "66", "0")));
        postAs(admin, "/api/weather/collect?plantId=" + plantId, Map.of()).andExpect(status().isOk());
        when(client.fetch(any(), any(), anyInt())).thenReturn(List.of(hour(hour, "27", "70", "2")));
        postAs(admin, "/api/weather/collect?plantId=" + plantId, Map.of()).andExpect(status().isOk());

        JsonNode rows = shifts(yesterday, yesterday);
        assertThat(rows).hasSize(1);
        assertThat(rows.at("/0/hours").asInt()).isEqualTo(1);
        assertDecimal(rows.at("/0/temperatureAvg"), "27");
    }

    @Test
    void theFirstRunTakesAllItCanAndLaterRunsOnlyTheGap() throws Exception {
        when(client.fetch(any(), any(), anyInt())).thenReturn(List.of(hour(today.minusDays(5).atTime(10, 0), "26", "66", "0")));
        postAs(admin, "/api/weather/collect?plantId=" + plantId, Map.of()).andExpect(status().isOk());
        postAs(admin, "/api/weather/collect?plantId=" + plantId, Map.of()).andExpect(status().isOk());

        ArgumentCaptor<Integer> days = ArgumentCaptor.forClass(Integer.class);
        verify(client, org.mockito.Mockito.times(2)).fetch(any(), any(), days.capture());
        assertThat(days.getAllValues().get(0)).isEqualTo(92);
        assertThat(days.getAllValues().get(1)).isEqualTo(6);
    }

    @Test
    void aShiftAddsUpItsOwnHoursAndANightBelongsToTheDateItStarted() throws Exception {
        List<WeatherClient.Hour> hours = new ArrayList<>();
        // the day shift of yesterday, 08:00 to 19:00, then the night 20:00 to 07:00 this morning
        for (int h = 8; h < 20; h++) {
            hours.add(hour(yesterday.atTime(h, 0), String.valueOf(20 + h), "50", h == 10 ? "2.5" : "0"));
        }
        LocalDateTime night = yesterday.atTime(20, 0);
        for (int h = 0; h < 12; h++) {
            hours.add(hour(night.plusHours(h), "20", "80", "0"));
        }
        // keep only what has already happened, whenever this test runs
        LocalDateTime now = LocalDateTime.now(INDIA);
        when(client.fetch(any(), any(), anyInt())).thenReturn(hours);
        postAs(admin, "/api/weather/collect?plantId=" + plantId, Map.of()).andExpect(status().isOk());

        JsonNode rows = shifts(yesterday, yesterday);
        assertThat(rows).hasSize(2);
        JsonNode day = rows.get(0);
        assertThat(day.get("shiftCode").asText()).isEqualTo("A");
        assertThat(day.get("hours").asInt()).isEqualTo(12);
        // 28 to 39
        assertDecimal(day.get("temperatureMin"), "28");
        assertDecimal(day.get("temperatureMax"), "39");
        assertDecimal(day.get("temperatureAvg"), "33.5");
        assertDecimal(day.get("humidityAvg"), "50");
        assertDecimal(day.get("rainMm"), "2.5");

        JsonNode nightRow = rows.get(1);
        assertThat(nightRow.get("shiftCode").asText()).isEqualTo("B");
        assertThat(nightRow.get("prodDate").asText()).isEqualTo(yesterday.toString());
        // before 08:00 only part of last night has happened yet
        int expected = now.toLocalTime().isBefore(LocalTime.of(8, 0)) ? 4 + now.getHour() + 1 : 12;
        assertThat(nightRow.get("hours").asInt()).isEqualTo(expected);
    }

    @Test
    void newestDatesComeFirstAndTheRangeIsChecked() throws Exception {
        when(client.fetch(any(), any(), anyInt())).thenReturn(List.of(
                hour(today.minusDays(3).atTime(10, 0), "26", "66", "0"), hour(today.minusDays(2).atTime(10, 0), "27", "66", "0")));
        postAs(admin, "/api/weather/collect?plantId=" + plantId, Map.of()).andExpect(status().isOk());
        JsonNode rows = shifts(today.minusDays(3), today.minusDays(2));
        assertThat(rows.at("/0/prodDate").asText()).isEqualTo(today.minusDays(2).toString());
        assertThat(shifts(today.minusDays(3), today.minusDays(3))).hasSize(1);

        getAs(admin, "/api/weather/shifts?plantId=" + plantId + "&from=" + today + "&to=" + yesterday).andExpect(status().isConflict());
        getAs(admin, "/api/weather/shifts?plantId=" + plantId + "&from=" + today.minusDays(400) + "&to=" + today).andExpect(status().isConflict());
    }

    @Test
    void aFailedFetchSaysSoAndChangesNothing() throws Exception {
        when(client.fetch(any(), any(), anyInt())).thenThrow(new ResourceAccessException("timed out"));
        postAs(admin, "/api/weather/collect?plantId=" + plantId, Map.of()).andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("The weather service could not be reached. The next hourly run will catch up."));
        assertThat(read(getAs(admin, "/api/weather/status?plantId=" + plantId)).has("latestHour")).isFalse();
    }

    @Test
    void oneFailingPlantDoesntStopTheHourlyRunForTheOthers() throws Exception {
        long second = plant("WTH-4", "23.00000", "71.00000");
        BigDecimal firstLatitude = new BigDecimal("22.81730");
        when(client.fetch(any(), any(), anyInt())).thenAnswer(call -> {
            if (((BigDecimal) call.getArgument(0)).compareTo(firstLatitude) == 0) {
                throw new ResourceAccessException("timed out");
            }
            return List.of(hour(yesterday.atTime(10, 0), "26", "66", "0"));
        });
        service.collectAll();
        assertThat(read(getAs(admin, "/api/weather/status?plantId=" + plantId)).has("latestHour")).isFalse();
        assertThat(read(getAs(admin, "/api/weather/status?plantId=" + second)).has("latestHour")).isTrue();
    }

    @Test
    void aPlantWithoutALocationIsSkippedAndCantFetch() throws Exception {
        long noLocation = plant("WTH-5", null, null);
        assertThat(read(getAs(admin, "/api/weather/status?plantId=" + noLocation)).get("hasLocation").asBoolean()).isFalse();
        postAs(admin, "/api/weather/collect?plantId=" + noLocation, Map.of()).andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Enter the location of plant WTH-5 first"));
    }

    @Test
    void whenTurnedOffNothingIsSentAnywhere() throws Exception {
        ReflectionTestUtils.setField(service, "enabled", false);
        try {
            postAs(admin, "/api/weather/collect?plantId=" + plantId, Map.of()).andExpect(status().isConflict())
                    .andExpect(jsonPath("$.message").value("Weather collection is turned off in the server's settings"));
            assertThat(read(getAs(admin, "/api/weather/status?plantId=" + plantId)).get("enabled").asBoolean()).isFalse();
            verify(client, never()).fetch(any(), any(), anyInt());
        } finally {
            ReflectionTestUtils.setField(service, "enabled", true);
        }
    }

    @Test
    void oldReadingsAreMarkedStale() throws Exception {
        when(client.fetch(any(), any(), anyInt())).thenReturn(List.of(hour(today.minusDays(2).atTime(10, 0), "26", "66", "0")));
        JsonNode status = read(postAs(admin, "/api/weather/collect?plantId=" + plantId, Map.of()).andExpect(status().isOk()));
        assertThat(status.get("stale").asBoolean()).isTrue();
    }

    @Test
    void viewingNeedsProductionViewFetchingNeedsPlantManageAndPlantsStayApart() throws Exception {
        long viewerRole = createRole(admin, "Viewer WT", "PRODUCTION_VIEW");
        createUser(admin, "viewer.wt", "password-1", viewerRole, plantId);
        String viewer = login("viewer.wt", "password-1");
        getAs(viewer, "/api/weather/status?plantId=" + plantId).andExpect(status().isOk());
        postAs(viewer, "/api/weather/collect?plantId=" + plantId, Map.of()).andExpect(status().isForbidden());

        long other = plant("WTH-6", "23.00000", "71.00000");
        getAs(viewer, "/api/weather/status?plantId=" + other).andExpect(status().isForbidden());
        getAs(viewer, "/api/weather/shifts?plantId=" + other + "&from=" + today + "&to=" + today).andExpect(status().isForbidden());

        long storeRole = createRole(admin, "Store WT", "STORE_VIEW");
        createUser(admin, "store.wt", "password-1", storeRole, plantId);
        getAs(login("store.wt", "password-1"), "/api/weather/status?plantId=" + plantId).andExpect(status().isForbidden());
    }

    // ------------------------------------------------------------------ helpers

    private JsonNode shifts(LocalDate from, LocalDate to) throws Exception {
        return read(getAs(admin, "/api/weather/shifts?plantId=" + plantId + "&from=" + from + "&to=" + to).andExpect(status().isOk()));
    }

    private static WeatherClient.Hour hour(LocalDateTime at, String temperature, String humidity, String rain) {
        return new WeatherClient.Hour(at, new BigDecimal(temperature), new BigDecimal(humidity), new BigDecimal(rain));
    }

    private Map<String, Object> plantBody(String code) {
        Map<String, Object> body = new HashMap<>();
        body.put("companyId", companyId);
        body.put("code", code);
        body.put("name", code + " Plant");
        return body;
    }

    private long plant(String code, String latitude, String longitude) throws Exception {
        Map<String, Object> body = plantBody(code);
        if (latitude != null) {
            body.put("latitude", new BigDecimal(latitude));
            body.put("longitude", new BigDecimal(longitude));
        }
        return read(postAs(admin, "/api/plants", body).andExpect(status().isCreated())).get("id").asLong();
    }

    private static void assertDecimal(JsonNode node, String expected) {
        assertThat(node.decimalValue()).isEqualByComparingTo(expected);
    }
}
