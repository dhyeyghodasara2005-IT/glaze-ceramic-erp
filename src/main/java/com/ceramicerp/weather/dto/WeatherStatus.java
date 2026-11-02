package com.ceramicerp.weather.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * @param enabled     the server's setting app.weather.enabled
 * @param hasLocation the plant has its coordinates
 * @param stale       readings exist, but have stopped coming
 * @param latestHour  empty until the first reading
 */
public record WeatherStatus(boolean enabled, boolean hasLocation, boolean stale, LocalDateTime latestHour,
                            BigDecimal temperatureC, BigDecimal humidityPct, BigDecimal rainMm) {
}
