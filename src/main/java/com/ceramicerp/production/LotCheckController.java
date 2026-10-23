package com.ceramicerp.production;

import com.ceramicerp.common.PageResponse;
import com.ceramicerp.production.dto.FinishingOverview;
import com.ceramicerp.production.dto.LotCheckRequest;
import com.ceramicerp.production.dto.LotCheckResponse;
import com.ceramicerp.store.DocStatus;
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
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

/**
 * /api/production/lot-checks - quality checks in the cooling area, and
 * /api/production/finishing/overview - the finishing home screen.
 */
@Tag(name = "Production - Quality checks & finishing")
@RestController
@RequestMapping("/api/production")
@RequiredArgsConstructor
public class LotCheckController {

    private final LotCheckService checkService;
    private final FinishingService finishingService;

    @GetMapping("/lot-checks")
    @PreAuthorize("hasAuthority('PRODUCTION_VIEW')")
    public PageResponse<LotCheckResponse> search(@RequestParam Long plantId,
                                                 @RequestParam(required = false) DocStatus status,
                                                 @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                                 @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                                 @RequestParam(required = false) String search,
                                                 @RequestParam(defaultValue = "0") int page,
                                                 @RequestParam(defaultValue = "20") int size) {
        return checkService.search(plantId, status, from, to, search, page, size);
    }

    @GetMapping("/lot-checks/{id}")
    @PreAuthorize("hasAuthority('PRODUCTION_VIEW')")
    public LotCheckResponse get(@PathVariable Long id) {
        return checkService.get(id);
    }

    @PostMapping("/lot-checks")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('QUALITY_CHECK')")
    public LotCheckResponse create(@Valid @RequestBody LotCheckRequest req) {
        return checkService.create(req);
    }

    /** A check has no draft, so the checker may cancel their own mistakes too. */
    @PostMapping("/lot-checks/{id}/cancel")
    @PreAuthorize("hasAnyAuthority('QUALITY_CHECK', 'PRODUCTION_CANCEL')")
    public LotCheckResponse cancel(@PathVariable Long id, @Valid @RequestBody CancelRequest req) {
        return checkService.cancel(id, req.reason());
    }

    @GetMapping("/finishing/overview")
    @PreAuthorize("hasAuthority('PRODUCTION_VIEW')")
    public FinishingOverview overview(@RequestParam Long plantId) {
        return finishingService.overview(plantId);
    }
}
