package com.ceramicerp.weather;

import com.ceramicerp.weather.dto.WeatherShift;
import com.ceramicerp.weather.dto.WeatherStatus;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

/**
 * /api/weather/status?plantId=1                          - is it being collected, and the latest hour
 * /api/weather/shifts?plantId=1&from=...&to=...          - per production shift
 * /api/weather/collect?plantId=1                         - fetch now instead of waiting for the hourly run
 */
@Tag(name = "Weather")
@RestController
@RequestMapping("/api/weather")
@RequiredArgsConstructor
public class WeatherController {

    private final WeatherService weatherService;

    @GetMapping("/status")
    @PreAuthorize("hasAuthority('PRODUCTION_VIEW')")
    public WeatherStatus status(@RequestParam Long plantId) {
        return weatherService.status(plantId);
    }

    @GetMapping("/shifts")
    @PreAuthorize("hasAuthority('PRODUCTION_VIEW')")
    public List<WeatherShift> shifts(@RequestParam Long plantId,
                                     @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                     @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return weatherService.shifts(plantId, from, to);
    }

    @PostMapping("/collect")
    @PreAuthorize("hasAuthority('PLANT_MANAGE')")
    public WeatherStatus collect(@RequestParam Long plantId) {
        return weatherService.collectNow(plantId);
    }
}
