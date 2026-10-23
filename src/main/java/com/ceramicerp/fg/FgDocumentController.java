package com.ceramicerp.fg;

import com.ceramicerp.common.PageResponse;
import com.ceramicerp.fg.dto.FgDocumentRequest;
import com.ceramicerp.fg.dto.FgDocumentResponse;
import com.ceramicerp.fg.dto.FgDocumentSummary;
import com.ceramicerp.fg.dto.FgOption;
import com.ceramicerp.fg.dto.PillarOption;
import com.ceramicerp.master.DesignRepository;
import com.ceramicerp.master.GradeRepository;
import com.ceramicerp.store.DocStatus;
import com.ceramicerp.store.dto.CancelRequest;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
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
 * /api/fg/documents?type=OPENING|BREAKAGE|REGRADE - the warehouse's documents, and
 * /api/fg/lookups/... - the dropdowns they need, with FG_VIEW only (no master rights).
 */
@Tag(name = "Finished goods - documents")
@RestController
@RequestMapping("/api/fg")
@RequiredArgsConstructor
public class FgDocumentController {

    private final FgDocumentService documentService;
    private final FgGodownService godownService;
    private final DesignRepository designRepository;
    private final GradeRepository gradeRepository;

    @GetMapping("/documents")
    @PreAuthorize("hasAuthority('FG_VIEW')")
    public PageResponse<FgDocumentSummary> search(@RequestParam Long plantId,
                                                  @RequestParam FgDocType type,
                                                  @RequestParam(required = false) DocStatus status,
                                                  @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                                  @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                                  @RequestParam(required = false) String search,
                                                  @RequestParam(defaultValue = "0") int page,
                                                  @RequestParam(defaultValue = "20") int size) {
        return documentService.search(plantId, type, status, from, to, search, page, size);
    }

    @GetMapping("/documents/{id}")
    @PreAuthorize("hasAuthority('FG_VIEW')")
    public FgDocumentResponse get(@PathVariable Long id) {
        return documentService.get(id);
    }

    @PostMapping("/documents")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('FG_ENTRY')")
    public FgDocumentResponse create(@Valid @RequestBody FgDocumentRequest req) {
        return documentService.create(req);
    }

    @PutMapping("/documents/{id}")
    @PreAuthorize("hasAuthority('FG_ENTRY')")
    public FgDocumentResponse update(@PathVariable Long id, @Valid @RequestBody FgDocumentRequest req) {
        return documentService.update(id, req);
    }

    @DeleteMapping("/documents/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('FG_ENTRY')")
    public void delete(@PathVariable Long id) {
        documentService.deleteDraft(id);
    }

    @PostMapping("/documents/{id}/post")
    @PreAuthorize("hasAuthority('FG_ENTRY')")
    public FgDocumentResponse post(@PathVariable Long id) {
        return documentService.post(id);
    }

    @PostMapping("/documents/{id}/cancel")
    @PreAuthorize("hasAuthority('FG_CANCEL')")
    public FgDocumentResponse cancel(@PathVariable Long id, @Valid @RequestBody CancelRequest req) {
        return documentService.cancel(id, req.reason());
    }

    /** Active designs, with their size (opening stock). */
    @GetMapping("/lookups/designs")
    @PreAuthorize("hasAuthority('FG_VIEW')")
    @Transactional(readOnly = true)
    public List<FgOption> designs() {
        return designRepository.findByActiveTrueOrderByCodeAsc().stream()
                .map(d -> new FgOption(d.getId(), d.getCode(), d.getName(), d.getTileSize().getCode()))
                .toList();
    }

    /** Grades boxes can be in stock as (sellable, active), best first. */
    @GetMapping("/lookups/grades")
    @PreAuthorize("hasAuthority('FG_VIEW')")
    @Transactional(readOnly = true)
    public List<FgOption> grades() {
        return gradeRepository.findAllByOrderBySortOrderAscNameAsc().stream()
                .filter(g -> g.isActive() && g.isSellable())
                .map(g -> new FgOption(g.getId(), g.getCode(), g.getName(), null))
                .toList();
    }

    /** Open pillars with how full each is. */
    @GetMapping("/lookups/pillars")
    @PreAuthorize("hasAuthority('FG_VIEW')")
    public List<PillarOption> pillars(@RequestParam Long plantId) {
        return godownService.pillarOptions(plantId);
    }
}
