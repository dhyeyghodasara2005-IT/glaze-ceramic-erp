package com.ceramicerp.costing;

import com.ceramicerp.costing.dto.CostRateRequest;
import com.ceramicerp.costing.dto.CostRateResponse;
import com.ceramicerp.costing.dto.CostSheet;
import com.ceramicerp.costing.dto.CostTrend;
import com.ceramicerp.costing.dto.MonthlyCostRequest;
import com.ceramicerp.costing.dto.MonthlyCostRow;
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

import java.time.YearMonth;
import java.util.List;

/** /api/costing - accounts keep the rates and monthly costs (COST_MANAGE); COST_VIEW sees what a m² costs. */
@Tag(name = "Costing")
@RestController
@RequestMapping("/api/costing")
@RequiredArgsConstructor
public class CostingController {

    private final CostSheetService sheetService;
    private final CostRateService rateService;

    /** month as 2026-10 (default: this month) */
    @GetMapping("/sheet")
    @PreAuthorize("hasAuthority('COST_VIEW')")
    public CostSheet sheet(@RequestParam Long plantId,
                           @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM") YearMonth month) {
        return sheetService.sheet(plantId, month);
    }

    @GetMapping("/trend")
    @PreAuthorize("hasAuthority('COST_VIEW')")
    public List<CostTrend> trend(@RequestParam Long plantId, @RequestParam(defaultValue = "6") int months) {
        return sheetService.trend(plantId, months);
    }

    @GetMapping("/rates")
    @PreAuthorize("hasAnyAuthority('COST_VIEW', 'COST_MANAGE')")
    public List<CostRateResponse> rates(@RequestParam Long plantId) {
        return rateService.rates(plantId);
    }

    @PostMapping("/rates")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('COST_MANAGE')")
    public CostRateResponse create(@Valid @RequestBody CostRateRequest req) {
        return rateService.create(req);
    }

    @PutMapping("/rates/{id}")
    @PreAuthorize("hasAuthority('COST_MANAGE')")
    public CostRateResponse update(@PathVariable Long id, @Valid @RequestBody CostRateRequest req) {
        return rateService.update(id, req);
    }

    @DeleteMapping("/rates/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('COST_MANAGE')")
    public void delete(@PathVariable Long id) {
        rateService.delete(id);
    }

    @GetMapping("/monthly")
    @PreAuthorize("hasAnyAuthority('COST_VIEW', 'COST_MANAGE')")
    public List<MonthlyCostRow> monthly(@RequestParam Long plantId,
                                        @RequestParam @DateTimeFormat(pattern = "yyyy-MM") YearMonth month) {
        return rateService.monthly(plantId, month);
    }

    @PutMapping("/monthly")
    @PreAuthorize("hasAuthority('COST_MANAGE')")
    public List<MonthlyCostRow> saveMonthly(@Valid @RequestBody MonthlyCostRequest req) {
        return rateService.saveMonthly(req);
    }
}
