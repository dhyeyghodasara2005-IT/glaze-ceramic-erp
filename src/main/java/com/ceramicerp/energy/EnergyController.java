package com.ceramicerp.energy;

import com.ceramicerp.energy.dto.EnergyUse;
import com.ceramicerp.energy.dto.MeterRequest;
import com.ceramicerp.energy.dto.MeterResponse;
import com.ceramicerp.energy.dto.RoundRequest;
import com.ceramicerp.energy.dto.RoundResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

/**
 * /api/energy - meters are plant masters (MASTER_MANAGE sets them up); supervisors type the readings
 * (METER_ENTRY); the owner and managers see what was used (ENERGY_VIEW).
 */
@Tag(name = "Energy")
@RestController
@RequestMapping("/api/energy")
@RequiredArgsConstructor
public class EnergyController {

    private final MeterService meterService;
    private final ReadingService readingService;
    private final EnergyService energyService;

    @GetMapping("/meters")
    @PreAuthorize("hasAnyAuthority('MASTER_VIEW', 'ENERGY_VIEW', 'METER_ENTRY')")
    public List<MeterResponse> meters(@RequestParam Long plantId,
                                      @RequestParam(defaultValue = "false") boolean includeInactive) {
        return meterService.list(plantId, includeInactive);
    }

    @PostMapping("/meters")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('MASTER_MANAGE')")
    public MeterResponse create(@Valid @RequestBody MeterRequest req) {
        return meterService.create(req);
    }

    @PutMapping("/meters/{id}")
    @PreAuthorize("hasAuthority('MASTER_MANAGE')")
    public MeterResponse update(@PathVariable Long id, @Valid @RequestBody MeterRequest req) {
        return meterService.update(id, req);
    }

    /** A reading round; without a date, the one to read now (the shift that ended last). */
    @GetMapping("/round")
    @PreAuthorize("hasAnyAuthority('METER_ENTRY', 'ENERGY_VIEW')")
    public RoundResponse round(@RequestParam Long plantId,
                               @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
                               @RequestParam(required = false) Long shiftId) {
        return readingService.round(plantId, date, shiftId);
    }

    @PostMapping("/round")
    @PreAuthorize("hasAuthority('METER_ENTRY')")
    public RoundResponse saveRound(@Valid @RequestBody RoundRequest req) {
        return readingService.save(req);
    }

    @DeleteMapping("/readings/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('METER_ENTRY')")
    public void deleteReading(@PathVariable Long id) {
        readingService.delete(id);
    }

    @GetMapping("/use")
    @PreAuthorize("hasAuthority('ENERGY_VIEW')")
    public EnergyUse use(@RequestParam Long plantId,
                         @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                         @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return energyService.use(plantId, from, to);
    }
}
