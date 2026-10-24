package com.ceramicerp.fg;

import com.ceramicerp.fg.dto.FgGodownRequest;
import com.ceramicerp.fg.dto.FgGodownResponse;
import com.ceramicerp.fg.dto.FgLedger;
import com.ceramicerp.fg.dto.FgStockRow;
import com.ceramicerp.fg.dto.GodownMap;
import com.ceramicerp.fg.dto.PillarBulkRequest;
import com.ceramicerp.fg.dto.PillarLayoutRequest;
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
    private final FgDesignSettingService designSettingService;

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

    /** Zone, walk order and kind for a run of pillars at once. */
    @PutMapping("/godowns/{id}/layout")
    @PreAuthorize("hasAuthority('FG_ENTRY')")
    public GodownMap setLayout(@PathVariable Long id, @Valid @RequestBody PillarLayoutRequest req) {
        return godownService.setLayout(id, req);
    }

    /** Ids of the designs marked as best sellers. */
    @GetMapping("/designs/fast-movers")
    @PreAuthorize("hasAuthority('FG_VIEW')")
    public List<Long> fastMovers() {
        return designSettingService.fastMovers();
    }

    @PutMapping("/designs/{designId}/fast-mover")
    @PreAuthorize("hasAuthority('FG_ENTRY')")
    public List<Long> setFastMover(@PathVariable Long designId, @RequestBody FastMoverRequest req) {
        designSettingService.setFastMover(designId, req.fastMover());
        return designSettingService.fastMovers();
    }

    public record FastMoverRequest(boolean fastMover) {
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
