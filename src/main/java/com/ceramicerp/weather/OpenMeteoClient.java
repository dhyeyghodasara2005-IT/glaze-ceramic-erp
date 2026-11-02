package com.ceramicerp.weather;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Reads the hourly weather from open-meteo.com. Only the plant's coordinates are sent.
 * Its free service is for non-commercial use, so a plant running on this needs a paid plan: app.weather.url and
 * app.weather.api-key point this at it.
 */
@Component
public class OpenMeteoClient implements WeatherClient {

    private final RestClient client;
    private final String url;
    private final String apiKey;

    public OpenMeteoClient(RestClient.Builder builder,
                           @Value("${app.weather.url}") String url,
                           @Value("${app.weather.api-key:}") String apiKey) {
        // never let a slow weather service hold anything up
        SimpleClientHttpRequestFactory timeouts = new SimpleClientHttpRequestFactory();
        timeouts.setConnectTimeout(Duration.ofSeconds(10));
        timeouts.setReadTimeout(Duration.ofSeconds(20));
        this.client = builder.requestFactory(timeouts).build();
        this.url = url;
        this.apiKey = apiKey;
    }

    private record Response(Hourly hourly) {
    }

    private record Hourly(List<LocalDateTime> time,
                          @JsonProperty("temperature_2m") List<BigDecimal> temperature,
                          @JsonProperty("relative_humidity_2m") List<BigDecimal> humidity,
                          List<BigDecimal> precipitation) {
    }

    @Override
    public List<Hour> fetch(BigDecimal latitude, BigDecimal longitude, int pastDays) {
        UriComponentsBuilder uri = UriComponentsBuilder.fromUriString(url)
                .queryParam("latitude", latitude)
                .queryParam("longitude", longitude)
                .queryParam("hourly", "temperature_2m,relative_humidity_2m,precipitation")
                .queryParam("past_days", pastDays)
                .queryParam("forecast_days", 1)
                .queryParam("timezone", WeatherService.PLANT_ZONE.getId());
        if (StringUtils.hasText(apiKey)) {
            uri.queryParam("apikey", apiKey);
        }
        Response response = client.get().uri(uri.build().toUri()).retrieve().body(Response.class);
        Hourly h = response.hourly();

        List<Hour> hours = new ArrayList<>();
        for (int i = 0; i < h.time().size(); i++) {
            // a gap in the data is left out, not saved as zero
            if (h.temperature().get(i) != null && h.humidity().get(i) != null) {
                BigDecimal rain = h.precipitation().get(i);
                hours.add(new Hour(h.time().get(i), h.temperature().get(i), h.humidity().get(i), rain == null ? BigDecimal.ZERO : rain));
            }
        }
        return hours;
    }
}
