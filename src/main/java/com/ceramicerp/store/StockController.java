package com.ceramicerp.store;

import com.ceramicerp.common.PageResponse;
import com.ceramicerp.store.dto.LedgerRow;
import com.ceramicerp.store.dto.StockRow;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

/**
 * /api/store/stock?plantId=1                         - stock of every material
 * /api/store/stock/ledger?plantId=1&materialId=5     - its movements
 */
@Tag(name = "Store - Stock")
@RestController
@RequestMapping("/api/store/stock")
@RequiredArgsConstructor
public class StockController {

    private final StockQueryService stockQueryService;

    @GetMapping
    @PreAuthorize("hasAuthority('STORE_VIEW')")
    public List<StockRow> summary(@RequestParam Long plantId) {
        return stockQueryService.summary(plantId);
    }

    @GetMapping("/ledger")
    @PreAuthorize("hasAuthority('STORE_VIEW')")
    public PageResponse<LedgerRow> ledger(@RequestParam Long plantId,
                                          @RequestParam Long materialId,
                                          @RequestParam(required = false) Long locationId,
                                          @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                          @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                          @RequestParam(defaultValue = "0") int page,
                                          @RequestParam(defaultValue = "50") int size) {
        return stockQueryService.ledger(plantId, materialId, locationId, from, to, page, size);
    }
}
