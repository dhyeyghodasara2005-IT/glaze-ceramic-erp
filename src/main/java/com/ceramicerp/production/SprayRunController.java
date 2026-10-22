package com.ceramicerp.production;

import com.ceramicerp.common.PageResponse;
import com.ceramicerp.production.dto.BodyBatchSummary;
import com.ceramicerp.production.dto.SprayRunRequest;
import com.ceramicerp.production.dto.SprayRunResponse;
import com.ceramicerp.production.dto.SprayRunSummary;
import com.ceramicerp.store.DocStatus;
import com.ceramicerp.store.dto.CancelRequest;
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

/** /api/production/spray-runs - spray dryer runs (body preparation supervisor). */
@Tag(name = "Production - Spray dryer runs")
@RestController
@RequestMapping("/api/production/spray-runs")
@RequiredArgsConstructor
public class SprayRunController {

    private final SprayRunService runService;

    @GetMapping
    @PreAuthorize("hasAuthority('PRODUCTION_VIEW')")
    public PageResponse<SprayRunSummary> search(@RequestParam Long plantId,
                                                @RequestParam(required = false) DocStatus status,
                                                @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                                @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                                @RequestParam(required = false) String search,
                                                @RequestParam(defaultValue = "0") int page,
                                                @RequestParam(defaultValue = "20") int size) {
        return runService.search(plantId, status, from, to, search, page, size);
    }

    /** Body batches whose slip is waiting in a tank. */
    @GetMapping("/available-batches")
    @PreAuthorize("hasAuthority('PRODUCTION_VIEW')")
    public List<BodyBatchSummary> availableBatches(@RequestParam Long plantId) {
        return runService.availableBatches(plantId);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('PRODUCTION_VIEW')")
    public SprayRunResponse get(@PathVariable Long id) {
        return runService.get(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('BODY_PREP_ENTRY')")
    public SprayRunResponse create(@Valid @RequestBody SprayRunRequest req) {
        return runService.create(req);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('BODY_PREP_ENTRY')")
    public SprayRunResponse update(@PathVariable Long id, @Valid @RequestBody SprayRunRequest req) {
        return runService.update(id, req);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('BODY_PREP_ENTRY')")
    public void delete(@PathVariable Long id) {
        runService.deleteDraft(id);
    }

    @PostMapping("/{id}/post")
    @PreAuthorize("hasAuthority('BODY_PREP_ENTRY')")
    public SprayRunResponse post(@PathVariable Long id) {
        return runService.post(id);
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasAuthority('PRODUCTION_CANCEL')")
    public SprayRunResponse cancel(@PathVariable Long id, @Valid @RequestBody CancelRequest req) {
        return runService.cancel(id, req.reason());
    }
}
