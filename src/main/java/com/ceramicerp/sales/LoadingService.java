package com.ceramicerp.sales;

import com.ceramicerp.common.Text;
import com.ceramicerp.common.exception.BusinessException;
import com.ceramicerp.common.exception.NotFoundException;
import com.ceramicerp.fg.FgPillar;
import com.ceramicerp.fg.FgSource;
import com.ceramicerp.fg.FgStock;
import com.ceramicerp.fg.FgStockRepository;
import com.ceramicerp.fg.FgStockService;
import com.ceramicerp.numbering.DocumentNumberService;
import com.ceramicerp.sales.dto.AddPickRequest;
import com.ceramicerp.sales.dto.PickOption;
import com.ceramicerp.sales.dto.SalesOrderResponse;
import com.ceramicerp.sales.dto.StockKept;
import com.ceramicerp.sales.dto.TruckRequest;
import com.ceramicerp.security.CurrentUser;
import com.ceramicerp.security.PlantAccess;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * The loading of a confirmed order: the truck, the loader's ticks ("loaded" or what was really taken), boxes taken
 * from another pillar, and the confirmation that moves the boxes out of the stock. Needs LOADING_ENTRY; undoing a
 * dispatch needs SALES_CANCEL (both checked by the controller).
 */
@Service
@RequiredArgsConstructor
public class LoadingService {

    /** The plants are in India: "today" is Indian time, whatever zone the server runs in. */
    private static final ZoneId PLANT_ZONE = ZoneId.of("Asia/Kolkata");

    private final SalesOrderRepository orderRepository;
    private final SalesOrderPickRepository pickRepository;
    private final FgStockRepository stockRepository;
    private final FgStockService stockService;
    private final DocumentNumberService numberService;
    private final PlanLock planLock;
    private final SalesOrderView view;

    @Transactional
    public SalesOrderResponse setTruck(Long id, TruckRequest req) {
        SalesOrder order = find(id);
        requireConfirmed(order, "loaded");
        // GJ-36-T-4521 and "gj 36 t 4521" are the same truck: kept as GJ36T4521, the way the e-way bill wants it
        String truck = Text.upperOrNull(req.truckNo());
        order.setTruckNo(truck == null ? null : Text.trimToNull(truck.replaceAll("[^A-Z0-9]", "")));
        order.setDriverName(Text.trimToNull(req.driverName()));
        order.setDriverMobile(Text.trimToNull(req.driverMobile()));
        order.setLrNo(Text.upperOrNull(req.lrNo()));
        return view.of(order);
    }

    /** What was really loaded from one plan row ("Loaded ✓" sends the planned boxes); null takes the tick back. */
    @Transactional
    public SalesOrderResponse tick(Long id, Long pickId, Integer loaded) {
        SalesOrder order = find(id);
        requireConfirmed(order, "loaded");
        SalesOrderPick pick = order.getPicks().stream().filter(p -> p.getId().equals(pickId)).findFirst()
                .orElseThrow(() -> new NotFoundException("Loading plan row", pickId));
        if (loaded == null) {
            pick.setLoadedBoxes(null);
            pick.setLoadedAt(null);
            pick.setLoadedBy(null);
            return view.of(order);
        }
        if (loaded > pick.keptBoxes()) {
            // more than the plan kept: the extra boxes must be free, so other orders are held off meanwhile
            planLock.lock(order.getPlant().getId());
            int free = free(pick.getStock(), order, pick);
            if (loaded > free) {
                throw new BusinessException("Only " + free + " boxes at " + pick.getStock().getPillar().getCode()
                        + " can go on this truck (the rest are kept for other orders)");
            }
        }
        requireWithinLine(order, pick.getLine(), pick, loaded);
        pick.setLoadedBoxes(loaded);
        pick.setLoadedAt(Instant.now());
        pick.setLoadedBy(CurrentUser.get().getUsername());
        return view.of(order);
    }

    /** Stock rows a line could take its boxes from instead, nearest first. */
    @Transactional(readOnly = true)
    public List<PickOption> options(Long id, Long lineId) {
        SalesOrder order = find(id);
        SalesOrderLine line = lineOf(order, lineId);
        List<FgStock> rows = stockRepository.findByPlantIdAndDesignIdInAndBoxesGreaterThan(order.getPlant().getId(),
                        List.of(line.getDesign().getId()), 0).stream()
                .filter(s -> s.getGrade().getId().equals(line.getGrade().getId()))
                .filter(s -> order.getPicks().stream().noneMatch(p -> p.getLine().getId().equals(line.getId()) && p.getStock().getId().equals(s.getId())))
                .toList();
        if (rows.isEmpty()) {
            return List.of();
        }
        Map<Long, Long> keptByOthers = keptByOthers(rows.stream().map(FgStock::getId).toList(), order);
        return rows.stream()
                .map(s -> new PickOption(s.getId(), s.getPillar().getGodown().getCode(), s.getPillar().getCode(),
                        s.getPillar().getZone(), s.getPillar().getWalkOrder(), s.getBatchCode(), s.getShade(),
                        s.getCaliber(), s.getBoxes(),
                        (int) (s.getBoxes() - keptByOthers.getOrDefault(s.getId(), 0L) - keptHereBy(order, s, null))))
                .filter(o -> o.free() > 0)
                .sorted(Comparator.comparing(PickOption::godownCode)
                        .thenComparing(PickOption::walkOrder, Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(PickOption::pillarCode, FgPillar.CODE_ORDER))
                .toList();
    }

    /** The loader took boxes from a pillar the plan didn't name: a new, already ticked row. */
    @Transactional
    public SalesOrderResponse addPick(Long id, Long lineId, AddPickRequest req) {
        SalesOrder order = find(id);
        requireConfirmed(order, "loaded");
        SalesOrderLine line = lineOf(order, lineId);
        planLock.lock(order.getPlant().getId());
        FgStock stock = stockRepository.findWithDetailsById(req.stockId())
                .orElseThrow(() -> new NotFoundException("Stock row", req.stockId()));
        if (!stock.getPlant().getId().equals(order.getPlant().getId())
                || !stock.getDesign().getId().equals(line.getDesign().getId())
                || !stock.getGrade().getId().equals(line.getGrade().getId())) {
            throw new BusinessException("Those boxes are not " + line.getDesign().getName() + " "
                    + line.getGrade().getName() + " of plant " + order.getPlant().getCode());
        }
        if (order.getPicks().stream().anyMatch(p -> p.getLine().getId().equals(line.getId()) && p.getStock().getId().equals(stock.getId()))) {
            throw new BusinessException(stock.getPillar().getCode() + " is already in the plan for row " + line.getLineNo()
                    + ": change its boxes instead");
        }
        int free = free(stock, order, null);
        if (req.boxes() > free) {
            throw new BusinessException("Only " + free + " free boxes at " + stock.getPillar().getCode());
        }
        requireWithinLine(order, line, null, req.boxes());

        SalesOrderPick pick = new SalesOrderPick();
        pick.setOrder(order);
        pick.setLine(line);
        pick.setStock(stock);
        pick.setPlannedBoxes(0);                 // not in Glaze's plan: added at loading
        pick.setLoadedBoxes(req.boxes());
        pick.setReason(PickReason.ADDED);
        pick.setLoadedAt(Instant.now());
        pick.setLoadedBy(CurrentUser.get().getUsername());
        order.getPicks().add(pick);
        pickRepository.flush();
        return view.of(order);
    }

    /** What is on the truck leaves the stock; the order is dispatched and gets its dispatch / gate pass no. */
    @Transactional
    public SalesOrderResponse dispatch(Long id) {
        SalesOrder order = find(id);
        requireConfirmed(order, "dispatched");
        if (order.getTruckNo() == null) {
            throw new BusinessException("Enter the truck no. first");
        }
        long open = order.getPicks().stream().filter(p -> p.getLoadedBoxes() == null).count();
        if (open > 0) {
            throw new BusinessException("Tick every row of the loading plan first: " + open + " not ticked yet");
        }
        if (order.getPicks().stream().mapToInt(SalesOrderPick::keptBoxes).sum() == 0) {
            throw new BusinessException("Nothing is loaded yet");
        }
        LocalDate today = LocalDate.now(PLANT_ZONE);
        if (order.getDispatchNo() == null) {
            // an undone dispatch keeps its number: the same truck trip, confirmed again
            order.setDispatchNo(numberService.next(order.getPlant(), SalesDocKind.DISPATCH, today));
        }
        // the engine checks the boxes are really there now (some may have broken since they were planned)
        stockService.applyAll(movements(order, -1),
                new FgStockService.Source(FgSource.DISPATCH, order.getId(), order.getDispatchNo(), today, false));
        order.setStatus(SalesOrderStatus.DISPATCHED);
        order.setDispatchDate(today);
        order.setDispatchedAt(Instant.now());
        order.setDispatchedBy(CurrentUser.get().getUsername());
        return view.of(order);
    }

    /** The truck was unloaded again: the boxes go back to their pillars and the order is confirmed (loading) again. */
    @Transactional
    public SalesOrderResponse undoDispatch(Long id, String reason) {
        SalesOrder order = find(id);
        if (order.getStatus() != SalesOrderStatus.DISPATCHED) {
            throw new BusinessException("Only a dispatched order can be undone. Slip " + order.getSlipNo() + " is "
                    + order.getStatus().name().toLowerCase(Locale.ROOT));
        }
        stockService.applyAll(movements(order, 1),
                new FgStockService.Source(FgSource.DISPATCH, order.getId(), order.getDispatchNo(), LocalDate.now(PLANT_ZONE), true));
        order.setStatus(SalesOrderStatus.CONFIRMED);
        order.setDispatchDate(null);
        order.setDispatchedAt(null);
        order.setDispatchedBy(null);
        order.setUndoneAt(Instant.now());
        order.setUndoneBy(CurrentUser.get().getUsername());
        order.setUndoReason(reason.trim());
        return view.of(order);
    }

    // ------------------------------------------------------------------ helpers

    /** The loaded boxes of every plan row, out of (-1) or back into (+1) the stock. */
    private static List<FgStockService.Movement> movements(SalesOrder order, int sign) {
        return order.getPicks().stream()
                .filter(p -> p.getLoadedBoxes() != null && p.getLoadedBoxes() > 0)
                .map(p -> new FgStockService.Movement(FgStockService.Batch.of(p.getStock()), sign * p.getLoadedBoxes()))
                .toList();
    }

    /** Boxes of a stock row this order may still take: not kept for other orders, nor by this order's other rows. */
    private int free(FgStock stock, SalesOrder order, SalesOrderPick except) {
        long others = keptByOthers(List.of(stock.getId()), order).getOrDefault(stock.getId(), 0L);
        return (int) (stock.getBoxes() - others - keptHereBy(order, stock, except));
    }

    /** What this order's plan rows (other than {@code except}) keep of one stock row. */
    private static int keptHereBy(SalesOrder order, FgStock stock, SalesOrderPick except) {
        return order.getPicks().stream()
                .filter(p -> p != except && p.getStock().getId().equals(stock.getId()))
                .mapToInt(SalesOrderPick::keptBoxes)
                .sum();
    }

    private Map<Long, Long> keptByOthers(List<Long> stockIds, SalesOrder order) {
        return pickRepository.keptByStock(stockIds, order.getId()).stream()
                .collect(Collectors.toMap(StockKept::stockId, StockKept::boxes));
    }

    /** A line never gets more boxes than the slip ordered. */
    private static void requireWithinLine(SalesOrder order, SalesOrderLine line, SalesOrderPick changed, int boxes) {
        int total = boxes + order.getPicks().stream()
                .filter(p -> p.getLine().getId().equals(line.getId()) && p != changed)
                .mapToInt(SalesOrderPick::keptBoxes)
                .sum();
        if (total > line.getBoxes()) {
            throw new BusinessException("Row " + line.getLineNo() + " ordered " + line.getBoxes() + " boxes: this would make "
                    + total);
        }
    }

    private static SalesOrderLine lineOf(SalesOrder order, Long lineId) {
        return order.getLines().stream()
                .filter(l -> l.getId().equals(lineId) && l.getDesign() != null && l.getGrade() != null)
                .findFirst()
                .orElseThrow(() -> new NotFoundException("Order line", lineId));
    }

    private SalesOrder find(Long id) {
        SalesOrder order = orderRepository.findWithDetailsById(id).orElseThrow(() -> new NotFoundException("Sales order", id));
        PlantAccess.require(order.getPlant().getId());
        return order;
    }

    private static void requireConfirmed(SalesOrder order, String action) {
        if (order.getStatus() != SalesOrderStatus.CONFIRMED) {
            throw new BusinessException("Only a confirmed order can be " + action + ". Slip " + order.getSlipNo() + " is "
                    + order.getStatus().name().toLowerCase(Locale.ROOT));
        }
    }
}
