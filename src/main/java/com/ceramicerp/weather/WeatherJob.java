package com.ceramicerp.weather;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

/** Every hour, five minutes past, once the service has that hour. Exists only when app.weather.enabled is true. */
@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "app.weather.enabled", havingValue = "true")
@RequiredArgsConstructor
public class WeatherJob {

    private final WeatherService weatherService;

    @Scheduled(cron = "0 5 * * * *", zone = "Asia/Kolkata")
    public void collect() {
        weatherService.collectAll();
    }
}
