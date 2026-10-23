package com.ceramicerp.fg;

import com.ceramicerp.fg.dto.FgGodownRequest;
import com.ceramicerp.fg.dto.FgGodownResponse;
import com.ceramicerp.fg.dto.FgLedger;
import com.ceramicerp.fg.dto.FgStockRow;
import com.ceramicerp.fg.dto.GodownMap;
import com.ceramicerp.fg.dto.PillarBulkRequest;
import com.ceramicerp.fg.dto.PillarUpdateRequest;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
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

import java.util.List;

/** /api/fg - finished-goods godowns, pillars and stock. */
@Tag(name = "Finished goods")
@RestController
@RequestMapping("/api/fg")
@RequiredArgsConstructor
public class FgController {

    private final FgGodownService godownService;
    private final FgStockQueryService stockService;

    @GetMapping("/godowns")
    @PreAuthorize("hasAuthority('FG_VIEW')")
    public List<FgGodownResponse> godowns(@RequestParam Long plantId) {
        return godownService.list(plantId);
    }

    @PostMapping("/godowns")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('FG_ENTRY')")
    public FgGodownResponse createGodown(@Valid @RequestBody FgGodownRequest req) {
        return godownService.create(req);
    }

    @PutMapping("/godowns/{id}")
    @PreAuthorize("hasAuthority('FG_ENTRY')")
    public FgGodownResponse updateGodown(@PathVariable Long id, @Valid @RequestBody FgGodownRequest req) {
        return godownService.update(id, req);
    }

    @GetMapping("/godowns/{id}/map")
    @PreAuthorize("hasAuthority('FG_VIEW')")
    public GodownMap map(@PathVariable Long id) {
        return godownService.map(id);
    }

    @PostMapping("/godowns/{id}/pillars")
    @PreAuthorize("hasAuthority('FG_ENTRY')")
    public GodownMap addPillars(@PathVariable Long id, @Valid @RequestBody PillarBulkRequest req) {
        return godownService.addPillars(id, req);
    }

    @PutMapping("/pillars/{id}")
    @PreAuthorize("hasAuthority('FG_ENTRY')")
    public GodownMap updatePillar(@PathVariable Long id, @Valid @RequestBody PillarUpdateRequest req) {
        return godownService.updatePillar(id, req);
    }

    @GetMapping("/stock")
    @PreAuthorize("hasAuthority('FG_VIEW')")
    public List<FgStockRow> stock(@RequestParam Long plantId,
                                  @RequestParam(required = false) Long godownId,
                                  @RequestParam(required = false) Long gradeId,
                                  @RequestParam(required = false) String search) {
        return stockService.search(plantId, godownId, gradeId, search);
    }

    @GetMapping("/stock/{id}")
    @PreAuthorize("hasAuthority('FG_VIEW')")
    public FgLedger ledger(@PathVariable Long id) {
        return stockService.ledger(id);
    }
}
