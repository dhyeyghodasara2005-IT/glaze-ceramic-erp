package com.ceramicerp.production;

import com.ceramicerp.common.PageResponse;
import com.ceramicerp.production.dto.PressEntryRequest;
import com.ceramicerp.production.dto.PressEntryResponse;
import com.ceramicerp.production.dto.PressEntrySummary;
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

/** /api/production/press-entries - press & dryer shift entries. */
@Tag(name = "Production - Press entries")
@RestController
@RequestMapping("/api/production/press-entries")
@RequiredArgsConstructor
public class PressEntryController {

    private final PressEntryService entryService;

    @GetMapping
    @PreAuthorize("hasAuthority('PRODUCTION_VIEW')")
    public PageResponse<PressEntrySummary> search(@RequestParam Long plantId,
                                                  @RequestParam(required = false) DocStatus status,
                                                  @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                                  @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                                  @RequestParam(required = false) String search,
                                                  @RequestParam(defaultValue = "0") int page,
                                                  @RequestParam(defaultValue = "20") int size) {
        return entryService.search(plantId, status, from, to, search, page, size);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('PRODUCTION_VIEW')")
    public PressEntryResponse get(@PathVariable Long id) {
        return entryService.get(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('PRESS_ENTRY')")
    public PressEntryResponse create(@Valid @RequestBody PressEntryRequest req) {
        return entryService.create(req);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('PRESS_ENTRY')")
    public PressEntryResponse update(@PathVariable Long id, @Valid @RequestBody PressEntryRequest req) {
        return entryService.update(id, req);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('PRESS_ENTRY')")
    public void delete(@PathVariable Long id) {
        entryService.deleteDraft(id);
    }

    @PostMapping("/{id}/post")
    @PreAuthorize("hasAuthority('PRESS_ENTRY')")
    public PressEntryResponse post(@PathVariable Long id) {
        return entryService.post(id);
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasAuthority('PRODUCTION_CANCEL')")
    public PressEntryResponse cancel(@PathVariable Long id, @Valid @RequestBody CancelRequest req) {
        return entryService.cancel(id, req.reason());
    }
}
