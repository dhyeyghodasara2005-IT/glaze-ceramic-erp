package com.ceramicerp.sales;

import com.ceramicerp.common.PageResponse;
import com.ceramicerp.common.exception.BusinessException;
import com.ceramicerp.master.DesignRepository;
import com.ceramicerp.master.GradeRepository;
import com.ceramicerp.party.PartyRepository;
import com.ceramicerp.party.PartyType;
import com.ceramicerp.sales.dto.MatchRequest;
import com.ceramicerp.sales.dto.ProductMatch;
import com.ceramicerp.sales.dto.SalesDesign;
import com.ceramicerp.sales.dto.SalesOption;
import com.ceramicerp.sales.dto.SalesOrderRequest;
import com.ceramicerp.sales.dto.SalesOrderResponse;
import com.ceramicerp.sales.dto.SalesOrderSummary;
import com.ceramicerp.sales.dto.SalesParty;
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
 * /api/sales/orders - sales orders from loading slips, /api/sales/match - reading slip descriptions as designs, and
 * /api/sales/lookups/... - the dropdowns, with SALES_VIEW only (no master or party rights needed).
 */
@Tag(name = "Sales orders")
@RestController
@RequestMapping("/api/sales")
@RequiredArgsConstructor
public class SalesOrderController {

    private final SalesOrderService orderService;
    private final ProductMatcher matcher;
    private final PartyRepository partyRepository;
    private final DesignRepository designRepository;
    private final GradeRepository gradeRepository;

    @GetMapping("/orders")
    @PreAuthorize("hasAuthority('SALES_VIEW')")
    public PageResponse<SalesOrderSummary> search(@RequestParam Long plantId,
                                                  @RequestParam(required = false) SalesOrderStatus status,
                                                  @RequestParam(required = false) SalesOrderType type,
                                                  @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                                  @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                                  @RequestParam(required = false) String search,
                                                  @RequestParam(defaultValue = "0") int page,
                                                  @RequestParam(defaultValue = "20") int size) {
        return orderService.search(plantId, status, type, from, to, search, page, size);
    }

    @GetMapping("/orders/{id}")
    @PreAuthorize("hasAuthority('SALES_VIEW')")
    public SalesOrderResponse get(@PathVariable Long id) {
        return orderService.get(id);
    }

    @PostMapping("/orders")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('SALES_ENTRY')")
    public SalesOrderResponse create(@Valid @RequestBody SalesOrderRequest req) {
        return orderService.create(req);
    }

    @PutMapping("/orders/{id}")
    @PreAuthorize("hasAuthority('SALES_ENTRY')")
    public SalesOrderResponse update(@PathVariable Long id, @Valid @RequestBody SalesOrderRequest req) {
        return orderService.update(id, req);
    }

    @DeleteMapping("/orders/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('SALES_ENTRY')")
    public void delete(@PathVariable Long id) {
        orderService.deleteDraft(id);
    }

    @PostMapping("/orders/{id}/confirm")
    @PreAuthorize("hasAuthority('SALES_ENTRY')")
    public SalesOrderResponse confirm(@PathVariable Long id) {
        return orderService.confirm(id);
    }

    @PostMapping("/orders/{id}/reopen")
    @PreAuthorize("hasAuthority('SALES_ENTRY')")
    public SalesOrderResponse reopen(@PathVariable Long id) {
        return orderService.reopen(id);
    }

    /** Picks the boxes again (more were packed, or some broke). Only until loading starts. */
    @PostMapping("/orders/{id}/plan")
    @PreAuthorize("hasAuthority('SALES_ENTRY')")
    public SalesOrderResponse replan(@PathVariable Long id) {
        return orderService.replan(id);
    }

    @PostMapping("/orders/{id}/cancel")
    @PreAuthorize("hasAuthority('SALES_CANCEL')")
    public SalesOrderResponse cancel(@PathVariable Long id, @Valid @RequestBody CancelRequest req) {
        return orderService.cancel(id, req.reason());
    }

    /** What Glaze understands from each product description (remembered, or guessed from the names). */
    @PostMapping("/match")
    @PreAuthorize("hasAuthority('SALES_ENTRY')")
    public List<ProductMatch> match(@Valid @RequestBody MatchRequest req) {
        return matcher.match(req.descriptions());
    }

    /** Active dealers, jobwork principals or transporters, with what the office checks. */
    @GetMapping("/lookups/parties")
    @PreAuthorize("hasAuthority('SALES_VIEW')")
    @Transactional(readOnly = true)
    public List<SalesParty> parties(@RequestParam PartyType type) {
        if (type == PartyType.SUPPLIER) {
            throw new BusinessException("Sales orders use dealers, jobwork principals and transporters");
        }
        return partyRepository.findByPartyTypeAndActiveTrueOrderByNameAsc(type).stream().map(SalesParty::from).toList();
    }

    /** Active designs, with their size, m² and weight per box. */
    @GetMapping("/lookups/designs")
    @PreAuthorize("hasAuthority('SALES_VIEW')")
    @Transactional(readOnly = true)
    public List<SalesDesign> designs() {
        return designRepository.findByActiveTrueOrderByCodeAsc().stream().map(SalesDesign::from).toList();
    }

    /** Grades that can be sold, best first. */
    @GetMapping("/lookups/grades")
    @PreAuthorize("hasAuthority('SALES_VIEW')")
    @Transactional(readOnly = true)
    public List<SalesOption> grades() {
        return gradeRepository.findAllByOrderBySortOrderAscNameAsc().stream()
                .filter(g -> g.isActive() && g.isSellable())
                .map(ProductMatcher::option)
                .toList();
    }
}
