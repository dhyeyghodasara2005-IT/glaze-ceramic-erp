package com.ceramicerp.production;

import com.ceramicerp.common.PageResponse;
import com.ceramicerp.production.dto.LineEntryRequest;
import com.ceramicerp.production.dto.LineEntryResponse;
import com.ceramicerp.production.dto.LineEntrySummary;
import com.ceramicerp.production.dto.LineOverview;
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

/**
 * /api/production/line-entries?stage=GLAZE|KILN|POLISH - glaze & print, kiln and polishing shift entries.
 * Writing needs one of the stage permissions here; the service then checks the one the stage needs.
 */
@Tag(name = "Production - Glaze, kiln & polishing")
@RestController
@RequestMapping("/api/production")
@RequiredArgsConstructor
public class LineEntryController {

    private final LineEntryService entryService;

    @GetMapping("/line-entries")
    @PreAuthorize("hasAuthority('PRODUCTION_VIEW')")
    public PageResponse<LineEntrySummary> search(@RequestParam Long plantId,
                                                 @RequestParam LineStage stage,
                                                 @RequestParam(required = false) DocStatus status,
                                                 @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                                 @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                                 @RequestParam(required = false) String search,
                                                 @RequestParam(defaultValue = "0") int page,
                                                 @RequestParam(defaultValue = "20") int size) {
        return entryService.search(plantId, stage, status, from, to, search, page, size);
    }

    @GetMapping("/line-entries/{id}")
    @PreAuthorize("hasAuthority('PRODUCTION_VIEW')")
    public LineEntryResponse get(@PathVariable Long id) {
        return entryService.get(id);
    }

    @PostMapping("/line-entries")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyAuthority('GLAZE_ENTRY', 'KILN_ENTRY', 'POLISH_ENTRY')")
    public LineEntryResponse create(@Valid @RequestBody LineEntryRequest req) {
        return entryService.create(req);
    }

    @PutMapping("/line-entries/{id}")
    @PreAuthorize("hasAnyAuthority('GLAZE_ENTRY', 'KILN_ENTRY', 'POLISH_ENTRY')")
    public LineEntryResponse update(@PathVariable Long id, @Valid @RequestBody LineEntryRequest req) {
        return entryService.update(id, req);
    }

    @DeleteMapping("/line-entries/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAnyAuthority('GLAZE_ENTRY', 'KILN_ENTRY', 'POLISH_ENTRY')")
    public void delete(@PathVariable Long id) {
        entryService.deleteDraft(id);
    }

    @PostMapping("/line-entries/{id}/post")
    @PreAuthorize("hasAnyAuthority('GLAZE_ENTRY', 'KILN_ENTRY', 'POLISH_ENTRY')")
    public LineEntryResponse post(@PathVariable Long id) {
        return entryService.post(id);
    }

    @PostMapping("/line-entries/{id}/cancel")
    @PreAuthorize("hasAuthority('PRODUCTION_CANCEL')")
    public LineEntryResponse cancel(@PathVariable Long id, @Valid @RequestBody CancelRequest req) {
        return entryService.cancel(id, req.reason());
    }

    @GetMapping("/line/overview")
    @PreAuthorize("hasAuthority('PRODUCTION_VIEW')")
    public LineOverview overview(@RequestParam Long plantId) {
        return entryService.overview(plantId);
    }
}
