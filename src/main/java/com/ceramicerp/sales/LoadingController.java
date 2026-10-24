package com.ceramicerp.sales;

import com.ceramicerp.sales.dto.AddPickRequest;
import com.ceramicerp.sales.dto.PickOption;
import com.ceramicerp.sales.dto.SalesOrderResponse;
import com.ceramicerp.sales.dto.TickRequest;
import com.ceramicerp.sales.dto.TruckRequest;
import com.ceramicerp.store.dto.CancelRequest;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** /api/sales/orders/{id}/... - loading a confirmed order onto its truck (LOADING_ENTRY), and undoing it (SALES_CANCEL). */
@Tag(name = "Sales orders - loading")
@RestController
@RequestMapping("/api/sales/orders/{id}")
@RequiredArgsConstructor
public class LoadingController {

    private final LoadingService loadingService;

    @PutMapping("/truck")
    @PreAuthorize("hasAuthority('LOADING_ENTRY')")
    public SalesOrderResponse truck(@PathVariable Long id, @Valid @RequestBody TruckRequest req) {
        return loadingService.setTruck(id, req);
    }

    @PutMapping("/picks/{pickId}")
    @PreAuthorize("hasAuthority('LOADING_ENTRY')")
    public SalesOrderResponse tick(@PathVariable Long id, @PathVariable Long pickId, @Valid @RequestBody TickRequest req) {
        return loadingService.tick(id, pickId, req.loadedBoxes());
    }

    @GetMapping("/lines/{lineId}/stock")
    @PreAuthorize("hasAuthority('LOADING_ENTRY')")
    public List<PickOption> options(@PathVariable Long id, @PathVariable Long lineId) {
        return loadingService.options(id, lineId);
    }

    @PostMapping("/lines/{lineId}/picks")
    @PreAuthorize("hasAuthority('LOADING_ENTRY')")
    public SalesOrderResponse addPick(@PathVariable Long id, @PathVariable Long lineId, @Valid @RequestBody AddPickRequest req) {
        return loadingService.addPick(id, lineId, req);
    }

    @PostMapping("/dispatch")
    @PreAuthorize("hasAuthority('LOADING_ENTRY')")
    public SalesOrderResponse dispatch(@PathVariable Long id) {
        return loadingService.dispatch(id);
    }

    @PostMapping("/undo-dispatch")
    @PreAuthorize("hasAuthority('SALES_CANCEL')")
    public SalesOrderResponse undoDispatch(@PathVariable Long id, @Valid @RequestBody CancelRequest req) {
        return loadingService.undoDispatch(id, req.reason());
    }
}
