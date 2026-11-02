package com.ceramicerp.weather;

import com.ceramicerp.common.exception.BusinessException;
import com.ceramicerp.master.Shift;
import com.ceramicerp.master.ShiftRepository;
import com.ceramicerp.master.ShiftService;
import com.ceramicerp.organization.Plant;
import com.ceramicerp.organization.PlantRepository;
import com.ceramicerp.organization.PlantService;
import com.ceramicerp.security.PlantAccess;
import com.ceramicerp.weather.dto.WeatherShift;
import com.ceramicerp.weather.dto.WeatherStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Saves the weather per plant, hour by hour, and adds it up per production shift. The weather is a helper: when the
 * service can't be reached nothing else is affected, and the next run catches up on the hours it missed.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WeatherService {

    public static final ZoneId PLANT_ZONE = ZoneId.of("Asia/Kolkata");

    /** The most the service accepts. On a plant's first run, ask for all of it (it answers with what it has). */
    private static final int MAX_PAST_DAYS = 92;
    /** Readings older than this mean the collector has stopped. */
    private static final int STALE_AFTER_HOURS = 3;
    private static final int MAX_RANGE_DAYS = 366;

    private final WeatherClient client;
    private final WeatherReadingRepository readingRepository;
    private final PlantRepository plantRepository;
    private final PlantService plantService;
    private final ShiftRepository shiftRepository;
    private final TransactionTemplate transaction;

    @Value("${app.weather.enabled}")
    private boolean enabled;

    // ------------------------------------------------------------------ collecting

    /** The hourly run: every active plant that has a location. One plant failing doesn't stop the others. */
    public void collectAll() {
        for (Plant plant : plantRepository.findByActiveTrueAndLatitudeNotNullAndLongitudeNotNull()) {
            try {
                collect(plant);
            } catch (RuntimeException e) {
                log.warn("Weather for plant {} not collected: {}", plant.getCode(), e.getMessage());
            }
        }
    }

    /** "Fetch now" on the screen. */
    public WeatherStatus collectNow(Long plantId) {
        PlantAccess.require(plantId);
        if (!enabled) {
            throw new BusinessException("Weather collection is turned off in the server's settings");
        }
        Plant plant = plantService.find(plantId);
        if (plant.getLatitude() == null || plant.getLongitude() == null) {
            throw new BusinessException("Enter the location of plant " + plant.getCode() + " first");
        }
        try {
            collect(plant);
        } catch (BusinessException e) {
            throw e;
        } catch (RuntimeException e) {
            log.warn("Weather for plant {} not collected: {}", plant.getCode(), e.getMessage());
            throw new BusinessException("The weather service could not be reached. The next hourly run will catch up.");
        }
        return status(plantId);
    }

    /** The call to the service stays outside the transaction: a database connection never waits on the internet. */
    private void collect(Plant plant) {
        LocalDateTime now = LocalDateTime.now(PLANT_ZONE);
        int pastDays = readingRepository.findFirstByPlantIdOrderByReadingHourDesc(plant.getId())
                .map(r -> (int) Math.min(MAX_PAST_DAYS, ChronoUnit.DAYS.between(r.getReadingHour().toLocalDate(), now.toLocalDate()) + 1))
                .orElse(MAX_PAST_DAYS);
        List<WeatherClient.Hour> hours = client.fetch(plant.getLatitude(), plant.getLongitude(), pastDays).stream()
                .filter(h -> !h.hour().isAfter(now))   // the response runs to the end of today: those hours are forecasts
                .toList();
        if (!hours.isEmpty()) {
            transaction.executeWithoutResult(status -> save(plant, hours));
        }
    }

    /** A past hour the service has corrected is updated; a new hour is added. */
    private void save(Plant plant, List<WeatherClient.Hour> hours) {
        LocalDateTime first = hours.stream().map(WeatherClient.Hour::hour).min(Comparator.naturalOrder()).orElseThrow();
        LocalDateTime last = hours.stream().map(WeatherClient.Hour::hour).max(Comparator.naturalOrder()).orElseThrow();
        Map<LocalDateTime, WeatherReading> saved = readingRepository.findBetween(plant.getId(), first, last).stream()
                .collect(Collectors.toMap(WeatherReading::getReadingHour, Function.identity()));
        List<WeatherReading> toSave = new ArrayList<>();
        for (WeatherClient.Hour h : hours) {
            WeatherReading reading = saved.get(h.hour());
            if (reading == null) {
                reading = new WeatherReading();
                reading.setPlant(plant);
                reading.setReadingHour(h.hour());
                toSave.add(reading);
            }
            reading.setTemperatureC(h.temperatureC().setScale(1, RoundingMode.HALF_UP));
            reading.setHumidityPct(h.humidityPct().setScale(1, RoundingMode.HALF_UP));
            reading.setRainMm(h.rainMm().setScale(2, RoundingMode.HALF_UP));
        }
        readingRepository.saveAll(toSave);
    }

    // ------------------------------------------------------------------ the screen

    @Transactional(readOnly = true)
    public WeatherStatus status(Long plantId) {
        PlantAccess.require(plantId);
        Plant plant = plantService.find(plantId);
        WeatherReading latest = readingRepository.findFirstByPlantIdOrderByReadingHourDesc(plantId).orElse(null);
        boolean stale = latest != null
                && latest.getReadingHour().isBefore(LocalDateTime.now(PLANT_ZONE).minusHours(STALE_AFTER_HOURS));
        return new WeatherStatus(enabled, plant.getLatitude() != null && plant.getLongitude() != null, stale,
                latest == null ? null : latest.getReadingHour(), latest == null ? null : latest.getTemperatureC(),
                latest == null ? null : latest.getHumidityPct(), latest == null ? null : latest.getRainMm());
    }

    /** The weather of each production shift, newest first. A night shift belongs to the date it started. */
    @Transactional(readOnly = true)
    public List<WeatherShift> shifts(Long plantId, LocalDate from, LocalDate to) {
        PlantAccess.require(plantId);
        if (from.isAfter(to)) {
            throw new BusinessException("The start date can't be after the end date");
        }
        if (ChronoUnit.DAYS.between(from, to) > MAX_RANGE_DAYS) {
            throw new BusinessException("Choose a range of up to a year");
        }
        List<Shift> shifts = shiftRepository.findByActiveTrueOrderBySortOrderAscCodeAsc();
        Map<ShiftKey, List<WeatherReading>> byShift = new HashMap<>();
        // the morning after `to` holds the end of that date's night shift
        for (WeatherReading r : readingRepository.findBetween(plantId, from.atStartOfDay(), to.plusDays(1).atTime(LocalTime.MAX))) {
            ShiftService.findCurrent(shifts, r.getReadingHour()).ifPresent(current -> {
                LocalDate date = current.prodDate();
                if (!date.isBefore(from) && !date.isAfter(to)) {
                    byShift.computeIfAbsent(new ShiftKey(date, current.shift()), k -> new ArrayList<>()).add(r);
                }
            });
        }
        Map<ShiftKey, List<WeatherReading>> ordered = new LinkedHashMap<>();
        byShift.entrySet().stream()
                .sorted(Comparator.comparing((Map.Entry<ShiftKey, List<WeatherReading>> e) -> e.getKey().date()).reversed()
                        .thenComparing(e -> e.getKey().shift().getSortOrder()))
                .forEach(e -> ordered.put(e.getKey(), e.getValue()));
        return ordered.entrySet().stream().map(e -> toRow(e.getKey(), e.getValue())).toList();
    }

    private record ShiftKey(LocalDate date, Shift shift) {
    }

    private static WeatherShift toRow(ShiftKey key, List<WeatherReading> readings) {
        BigDecimal count = BigDecimal.valueOf(readings.size());
        BigDecimal temperature = sum(readings, WeatherReading::getTemperatureC);
        BigDecimal humidity = sum(readings, WeatherReading::getHumidityPct);
        return new WeatherShift(key.date(), key.shift().getId(), key.shift().getCode(), key.shift().getName(), readings.size(),
                temperature.divide(count, 1, RoundingMode.HALF_UP),
                readings.stream().map(WeatherReading::getTemperatureC).min(Comparator.naturalOrder()).orElseThrow(),
                readings.stream().map(WeatherReading::getTemperatureC).max(Comparator.naturalOrder()).orElseThrow(),
                humidity.divide(count, 1, RoundingMode.HALF_UP),
                sum(readings, WeatherReading::getRainMm));
    }

    private static BigDecimal sum(List<WeatherReading> readings, Function<WeatherReading, BigDecimal> field) {
        return readings.stream().map(field).reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
