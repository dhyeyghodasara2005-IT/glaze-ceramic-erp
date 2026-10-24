package com.ceramicerp.dashboard;

import com.ceramicerp.dashboard.dto.DashboardResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

/**
 * /api/dashboard?plantId=&from=&to= - the owner's dashboard. Anyone who may see production, finished goods, sales
 * or the store gets the sections of what they may see.
 */
@Tag(name = "Dashboard")
@RestController
@RequestMapping("/api/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final DashboardService dashboardService;

    @GetMapping
    @PreAuthorize("hasAnyAuthority('PRODUCTION_VIEW', 'FG_VIEW', 'SALES_VIEW', 'STORE_VIEW')")
    public DashboardResponse get(@RequestParam Long plantId,
                                 @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                 @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return dashboardService.get(plantId, from, to);
    }
}
