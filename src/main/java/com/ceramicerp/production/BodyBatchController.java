package com.ceramicerp.production;

import com.ceramicerp.common.PageResponse;
import com.ceramicerp.production.dto.BodyBatchRequest;
import com.ceramicerp.production.dto.BodyBatchResponse;
import com.ceramicerp.production.dto.BodyBatchSummary;
import com.ceramicerp.production.dto.DischargeRequest;
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
 * /api/production/body-batches - ball mill charges.
 * Entries need BODY_PREP_ENTRY: the permission given to the body preparation supervisor's role.
 */
@Tag(name = "Production - Body batches")
@RestController
@RequestMapping("/api/production/body-batches")
@RequiredArgsConstructor
public class BodyBatchController {

    private final BodyBatchService batchService;

    @GetMapping
    @PreAuthorize("hasAuthority('PRODUCTION_VIEW')")
    public PageResponse<BodyBatchSummary> search(@RequestParam Long plantId,
                                                 @RequestParam(required = false) BodyBatchStatus status,
                                                 @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                                 @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                                 @RequestParam(required = false) String search,
                                                 @RequestParam(defaultValue = "0") int page,
                                                 @RequestParam(defaultValue = "20") int size) {
        return batchService.search(plantId, status, from, to, search, page, size);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('PRODUCTION_VIEW')")
    public BodyBatchResponse get(@PathVariable Long id) {
        return batchService.get(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('BODY_PREP_ENTRY')")
    public BodyBatchResponse create(@Valid @RequestBody BodyBatchRequest req) {
        return batchService.create(req);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('BODY_PREP_ENTRY')")
    public BodyBatchResponse update(@PathVariable Long id, @Valid @RequestBody BodyBatchRequest req) {
        return batchService.update(id, req);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('BODY_PREP_ENTRY')")
    public void delete(@PathVariable Long id) {
        batchService.deleteDraft(id);
    }

    @PostMapping("/{id}/charge")
    @PreAuthorize("hasAuthority('BODY_PREP_ENTRY')")
    public BodyBatchResponse charge(@PathVariable Long id) {
        return batchService.charge(id);
    }

    @PostMapping("/{id}/discharge")
    @PreAuthorize("hasAuthority('BODY_PREP_ENTRY')")
    public BodyBatchResponse discharge(@PathVariable Long id, @Valid @RequestBody DischargeRequest req) {
        return batchService.discharge(id, req);
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasAuthority('PRODUCTION_CANCEL')")
    public BodyBatchResponse cancel(@PathVariable Long id, @Valid @RequestBody CancelRequest req) {
        return batchService.cancel(id, req.reason());
    }
}
