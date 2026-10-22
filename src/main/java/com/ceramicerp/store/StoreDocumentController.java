package com.ceramicerp.store;

import com.ceramicerp.common.PageResponse;
import com.ceramicerp.store.dto.CancelRequest;
import com.ceramicerp.store.dto.StoreDocumentRequest;
import com.ceramicerp.store.dto.StoreDocumentResponse;
import com.ceramicerp.store.dto.StoreDocumentSummary;
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
 * /api/store/documents?plantId=1&type=GRN&status=POSTED&from=2026-10-01&to=2026-10-31&search=clay
 * One API for GRNs, issues and adjustments; the screen passes the type.
 */
@Tag(name = "Store - Documents")
@RestController
@RequestMapping("/api/store/documents")
@RequiredArgsConstructor
public class StoreDocumentController {

    private final StoreDocumentService documentService;

    @GetMapping
    @PreAuthorize("hasAuthority('STORE_VIEW')")
    public PageResponse<StoreDocumentSummary> search(@RequestParam Long plantId,
                                                     @RequestParam StoreDocType type,
                                                     @RequestParam(required = false) DocStatus status,
                                                     @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                                     @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                                     @RequestParam(required = false) String search,
                                                     @RequestParam(defaultValue = "0") int page,
                                                     @RequestParam(defaultValue = "20") int size) {
        return documentService.search(plantId, type, status, from, to, search, page, size);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('STORE_VIEW')")
    public StoreDocumentResponse get(@PathVariable Long id) {
        return documentService.get(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('STORE_ENTRY')")
    public StoreDocumentResponse create(@Valid @RequestBody StoreDocumentRequest req) {
        return documentService.create(req);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('STORE_ENTRY')")
    public StoreDocumentResponse update(@PathVariable Long id, @Valid @RequestBody StoreDocumentRequest req) {
        return documentService.update(id, req);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('STORE_ENTRY')")
    public void delete(@PathVariable Long id) {
        documentService.deleteDraft(id);
    }

    @PostMapping("/{id}/post")
    @PreAuthorize("hasAuthority('STORE_ENTRY')")
    public StoreDocumentResponse post(@PathVariable Long id) {
        return documentService.post(id);
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasAuthority('STORE_CANCEL')")
    public StoreDocumentResponse cancel(@PathVariable Long id, @Valid @RequestBody CancelRequest req) {
        return documentService.cancel(id, req.reason());
    }
}
