package com.ceramicerp.production;

import com.ceramicerp.common.PageResponse;
import com.ceramicerp.production.dto.LotOpenRequest;
import com.ceramicerp.production.dto.LotResponse;
import com.ceramicerp.production.dto.LotSummary;
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
import java.util.List;

/** /api/production/lots - design lots (press & dryer supervisor). */
@Tag(name = "Production - Design lots")
@RestController
@RequestMapping("/api/production/lots")
@RequiredArgsConstructor
public class LotController {

    private final LotService lotService;

    @GetMapping
    @PreAuthorize("hasAuthority('PRODUCTION_VIEW')")
    public PageResponse<LotSummary> search(@RequestParam Long plantId,
                                           @RequestParam(required = false) LotStatus status,
                                           @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                           @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                           @RequestParam(required = false) String search,
                                           @RequestParam(defaultValue = "0") int page,
                                           @RequestParam(defaultValue = "20") int size) {
        return lotService.search(plantId, status, from, to, search, page, size);
    }

    /** Lots a press entry can be made for (open, or closed in the last few days). */
    @GetMapping("/selectable")
    @PreAuthorize("hasAuthority('PRODUCTION_VIEW')")
    public List<LotSummary> selectable(@RequestParam Long plantId) {
        return lotService.selectable(plantId);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('PRODUCTION_VIEW')")
    public LotResponse get(@PathVariable Long id) {
        return lotService.get(id);
    }

    /** Start a design on a press line. */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('PRESS_ENTRY')")
    public LotResponse open(@Valid @RequestBody LotOpenRequest req) {
        return lotService.open(req);
    }

    @PostMapping("/{id}/close")
    @PreAuthorize("hasAuthority('PRESS_ENTRY')")
    public LotResponse close(@PathVariable Long id) {
        return lotService.close(id);
    }

    /** Only for a lot with nothing posted against it, so the press supervisor may do it. */
    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasAuthority('PRESS_ENTRY')")
    public LotResponse cancel(@PathVariable Long id, @Valid @RequestBody CancelRequest req) {
        return lotService.cancel(id, req.reason());
    }
}
