package com.ceramicerp.maintenance;

import com.ceramicerp.common.PageResponse;
import com.ceramicerp.maintenance.dto.BreakdownCloseRequest;
import com.ceramicerp.maintenance.dto.BreakdownReportRequest;
import com.ceramicerp.maintenance.dto.BreakdownResponse;
import com.ceramicerp.maintenance.dto.BreakdownSummary;
import com.ceramicerp.maintenance.dto.DowntimeResponse;
import com.ceramicerp.store.dto.CancelRequest;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
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

/**
 * /api/maintenance/breakdowns - supervisors report a machine as down (BREAKDOWN_REPORT), maintenance closes it
 * (MAINTENANCE_ENTRY); /api/maintenance/downtime - downtime per machine (MAINTENANCE_VIEW).
 */
@Tag(name = "Maintenance - breakdowns")
@RestController
@RequestMapping("/api/maintenance")
@RequiredArgsConstructor
public class BreakdownController {

    /** Whoever reports or fixes breakdowns may also look at them. */
    private static final String SEES = "hasAnyAuthority('MAINTENANCE_VIEW', 'BREAKDOWN_REPORT', 'MAINTENANCE_ENTRY')";

    private final BreakdownService breakdownService;
    private final DowntimeService downtimeService;

    @GetMapping("/breakdowns")
    @PreAuthorize(SEES)
    public PageResponse<BreakdownSummary> search(@RequestParam Long plantId,
                                                 @RequestParam(required = false) BreakdownStatus status,
                                                 @RequestParam(required = false) Long machineId,
                                                 @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                                 @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                                 @RequestParam(required = false) String search,
                                                 @RequestParam(defaultValue = "0") int page,
                                                 @RequestParam(defaultValue = "20") int size) {
        return breakdownService.search(plantId, status, machineId, from, to, search, page, size);
    }

    @GetMapping("/breakdowns/{id}")
    @PreAuthorize(SEES)
    public BreakdownResponse get(@PathVariable Long id) {
        return breakdownService.get(id);
    }

    @PostMapping("/breakdowns")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyAuthority('BREAKDOWN_REPORT', 'MAINTENANCE_ENTRY')")
    public BreakdownResponse report(@Valid @RequestBody BreakdownReportRequest req) {
        return breakdownService.report(req);
    }

    @PutMapping("/breakdowns/{id}")
    @PreAuthorize("hasAnyAuthority('BREAKDOWN_REPORT', 'MAINTENANCE_ENTRY')")
    public BreakdownResponse update(@PathVariable Long id, @Valid @RequestBody BreakdownReportRequest req) {
        return breakdownService.update(id, req);
    }

    @PostMapping("/breakdowns/{id}/close")
    @PreAuthorize("hasAuthority('MAINTENANCE_ENTRY')")
    public BreakdownResponse close(@PathVariable Long id, @Valid @RequestBody BreakdownCloseRequest req) {
        return breakdownService.close(id, req);
    }

    /** Open ones: reporters and maintenance. Closed ones: MAINTENANCE_CANCEL (checked in the service). */
    @PostMapping("/breakdowns/{id}/cancel")
    @PreAuthorize("hasAnyAuthority('BREAKDOWN_REPORT', 'MAINTENANCE_ENTRY', 'MAINTENANCE_CANCEL')")
    public BreakdownResponse cancel(@PathVariable Long id, @Valid @RequestBody CancelRequest req) {
        return breakdownService.cancel(id, req.reason());
    }

    @GetMapping("/downtime")
    @PreAuthorize("hasAuthority('MAINTENANCE_VIEW')")
    public DowntimeResponse downtime(@RequestParam Long plantId,
                                     @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                     @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return downtimeService.downtime(plantId, from, to);
    }
}
