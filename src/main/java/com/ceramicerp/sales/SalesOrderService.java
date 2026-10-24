package com.ceramicerp.sales;

import com.ceramicerp.common.PageResponse;
import com.ceramicerp.common.Text;
import com.ceramicerp.common.exception.BusinessException;
import com.ceramicerp.common.exception.NotFoundException;
import com.ceramicerp.fg.FgStockRepository;
import com.ceramicerp.fg.dto.DesignGradeStock;
import com.ceramicerp.fg.dto.FgStockRow;
import com.ceramicerp.master.Design;
import com.ceramicerp.master.DesignRepository;
import com.ceramicerp.master.Grade;
import com.ceramicerp.master.GradeRepository;
import com.ceramicerp.master.TileSize;
import com.ceramicerp.organization.Plant;
import com.ceramicerp.organization.PlantService;
import com.ceramicerp.party.Party;
import com.ceramicerp.party.PartyRepository;
import com.ceramicerp.party.PartyType;
import com.ceramicerp.sales.dto.SalesLineRequest;
import com.ceramicerp.sales.dto.SalesOption;
import com.ceramicerp.sales.dto.SalesOrderRequest;
import com.ceramicerp.sales.dto.SalesOrderResponse;
import com.ceramicerp.sales.dto.SalesOrderSummary;
import com.ceramicerp.sales.dto.SalesParty;
import com.ceramicerp.security.CurrentUser;
import com.ceramicerp.security.PlantAccess;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Sales orders from loading slips. A draft can be changed freely; confirming checks that every line is matched to a
 * design and grade and remembers those matches for the next slip. Until loading starts (step 6c) a confirmed order
 * can be reopened; cancelling needs SALES_CANCEL.
 * <p>
 * Nothing here moves stock: the boxes leave the godown when the truck is loaded (6c).
 */
@Service
@RequiredArgsConstructor
public class SalesOrderService {

    /** The plants are in India: "today" is Indian time, whatever zone the server runs in. */
    private static final ZoneId PLANT_ZONE = ZoneId.of("Asia/Kolkata");

    /** Glaze's weight may differ this much from the slip's before it is flagged (2%): box weights differ a little. */
    private static final BigDecimal WEIGHT_TOLERANCE = new BigDecimal("0.02");

    private final SalesOrderRepository orderRepository;
    private final ProductAliasRepository aliasRepository;
    private final PartyRepository partyRepository;
    private final DesignRepository designRepository;
    private final GradeRepository gradeRepository;
    private final FgStockRepository stockRepository;
    private final PlantService plantService;

    @Transactional(readOnly = true)
    public PageResponse<SalesOrderSummary> search(Long plantId, SalesOrderStatus status, SalesOrderType type,
                                                  LocalDate from, LocalDate to, String search, int page, int size) {
        PlantAccess.require(plantId);
        String pattern = Text.trimToNull(search) == null ? null : "%" + search.trim().toLowerCase(Locale.ROOT) + "%";
        var pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100),
                Sort.by(Sort.Order.desc("slipDate"), Sort.Order.desc("id")));
        return PageResponse.of(orderRepository.search(plantId, status, type, from, to, pattern, pageable),
                SalesOrderSummary::from);
    }

    @Transactional(readOnly = true)
    public SalesOrderResponse get(Long id) {
        return toResponse(find(id));
    }

    @Transactional
    public SalesOrderResponse create(SalesOrderRequest req) {
        Plant plant = plantService.find(req.plantId());
        PlantAccess.require(plant.getId());
        SalesOrder order = new SalesOrder();
        order.setPlant(plant);
        order.setSource(OrderSource.TYPED);
        apply(order, req);
        return toResponse(orderRepository.saveAndFlush(order));
    }

    @Transactional
    public SalesOrderResponse update(Long id, SalesOrderRequest req) {
        SalesOrder order = find(id);
        requireStatus(order, SalesOrderStatus.DRAFT, "edited");
        if (!order.getPlant().getId().equals(req.plantId())) {
            throw new BusinessException("An order can't be moved to another plant");
        }
        apply(order, req);
        return toResponse(orderRepository.saveAndFlush(order));
    }

    @Transactional
    public void deleteDraft(Long id) {
        SalesOrder order = find(id);
        requireStatus(order, SalesOrderStatus.DRAFT, "deleted");
        orderRepository.delete(order);
    }

    /** Every line needs its design and grade; the matches are then remembered for the next slip. */
    @Transactional
    public SalesOrderResponse confirm(Long id) {
        SalesOrder order = find(id);
        requireStatus(order, SalesOrderStatus.DRAFT, "confirmed");
        if (!order.getParty().isActive()) {
            throw new BusinessException(order.getParty().getName() + " is inactive. Choose another party, or make it "
                    + "active again in Parties.");
        }
        List<String> unmatched = order.getLines().stream()
                .filter(l -> l.getDesign() == null || l.getGrade() == null)
                .map(l -> String.valueOf(l.getLineNo()))
                .toList();
        if (!unmatched.isEmpty()) {
            throw new BusinessException("Choose the design and grade for row " + String.join(", ", unmatched));
        }
        order.setStatus(SalesOrderStatus.CONFIRMED);
        order.setConfirmedAt(Instant.now());
        order.setConfirmedBy(CurrentUser.get().getUsername());
        remember(order.getLines());
        return toResponse(order);
    }

    /** Back to draft, to correct a confirmed order. From step 6c only until loading starts. */
    @Transactional
    public SalesOrderResponse reopen(Long id) {
        SalesOrder order = find(id);
        requireStatus(order, SalesOrderStatus.CONFIRMED, "reopened");
        order.setStatus(SalesOrderStatus.DRAFT);
        order.setConfirmedAt(null);
        order.setConfirmedBy(null);
        return toResponse(order);
    }

    /** Needs SALES_CANCEL (checked by the controller). A draft is deleted instead. */
    @Transactional
    public SalesOrderResponse cancel(Long id, String reason) {
        SalesOrder order = find(id);
        if (order.getStatus() != SalesOrderStatus.CONFIRMED) {
            throw new BusinessException("Only a confirmed order can be cancelled. Delete a draft instead.");
        }
        order.setStatus(SalesOrderStatus.CANCELLED);
        order.setCancelledAt(Instant.now());
        order.setCancelledBy(CurrentUser.get().getUsername());
        order.setCancelReason(reason.trim());
        return toResponse(order);
    }

    // ------------------------------------------------------------------ remembering matches

    /**
     * Saves "this description = this design + grade" for every line. A description seen before is updated, so a
     * correction made today is what tomorrow's slips get.
     */
    private void remember(List<SalesOrderLine> lines) {
        Map<String, SalesOrderLine> byKey = new LinkedHashMap<>();
        lines.forEach(l -> byKey.put(ProductMatcher.key(l.getDescription()), l));
        Map<String, ProductAlias> known = aliasRepository.findByNameKeyIn(byKey.keySet()).stream()
                .collect(Collectors.toMap(ProductAlias::getNameKey, Function.identity()));
        byKey.forEach((key, line) -> {
            ProductAlias alias = known.get(key);
            if (alias == null) {
                alias = new ProductAlias();
                alias.setNameKey(key);
            }
            alias.setDesign(line.getDesign());
            alias.setGrade(line.getGrade());
            aliasRepository.save(alias);
        });
    }

    // ------------------------------------------------------------------ checking a draft

    private void apply(SalesOrder order, SalesOrderRequest req) {
        String slipNo = Text.upperOrNull(req.slipNo());
        Long plantId = order.getPlant().getId();
        boolean taken = order.getId() == null
                ? orderRepository.existsByPlantIdAndSlipNo(plantId, slipNo)
                : orderRepository.existsByPlantIdAndSlipNoAndIdNot(plantId, slipNo, order.getId());
        if (taken) {
            // the same slip entered (or uploaded) twice would be loaded twice
            throw new BusinessException("Loading slip " + slipNo + " is already entered in plant "
                    + order.getPlant().getCode());
        }
        requireNotFuture(req.slipDate(), "slip date");
        if (req.orderDate() != null) {
            requireNotFuture(req.orderDate(), "sales order date");
            if (req.orderDate().isAfter(req.slipDate())) {
                throw new BusinessException("The sales order date can't be after the slip date");
            }
        }
        order.setSlipNo(slipNo);
        order.setSlipDate(req.slipDate());
        order.setOrderRef(Text.upperOrNull(req.orderRef()));
        order.setOrderDate(req.orderDate());
        order.setOrderType(req.orderType());
        order.setParty(requireParty(req.partyId(), req.orderType().partyType(), order.getParty()));
        order.setTransporter(req.transporterId() == null ? null
                : requireParty(req.transporterId(), PartyType.TRANSPORTER, order.getTransporter()));
        order.setSlipBoxes(req.slipBoxes());
        order.setSlipWeightKg(req.slipWeightKg());
        order.setRemarks(Text.trimToNull(req.remarks()));

        // Designs and grades already on this draft may stay even if they have been made inactive since.
        Set<Long> designsBefore = order.getLines().stream().map(SalesOrderLine::getDesign).filter(Objects::nonNull)
                .map(Design::getId).collect(Collectors.toSet());
        Set<Long> gradesBefore = order.getLines().stream().map(SalesOrderLine::getGrade).filter(Objects::nonNull)
                .map(Grade::getId).collect(Collectors.toSet());
        Map<Long, Design> designs = byId(designRepository.findAllById(nonNull(req.lines(), SalesLineRequest::designId)),
                Design::getId);
        Map<Long, Grade> grades = byId(gradeRepository.findAllById(nonNull(req.lines(), SalesLineRequest::gradeId)),
                Grade::getId);

        // No unique key on the lines, so the old rows can simply be replaced (no flush needed, unlike PackEntryService).
        order.getLines().clear();
        int lineNo = 0;
        for (SalesLineRequest row : req.lines()) {
            lineNo++;
            String at = " (row " + lineNo + ")";
            SalesOrderLine line = new SalesOrderLine();
            line.setOrder(order);
            line.setLineNo(lineNo);
            line.setDescription(row.description().trim().replaceAll("\\s+", " "));
            line.setBoxes(row.boxes());
            line.setMrpBox(row.mrpBox());
            line.setMrpPiece(row.mrpPiece());
            line.setBatchRef(Text.upperOrNull(row.batchRef()));
            if (row.designId() != null) {
                Design design = found(designs, row.designId());
                if (!design.isActive() && !designsBefore.contains(design.getId())) {
                    throw new BusinessException("Design " + design.getCode() + " is inactive" + at);
                }
                line.setDesign(design);
            }
            if (row.gradeId() != null) {
                Grade grade = found(grades, row.gradeId());
                if (!grade.isSellable()) {
                    throw new BusinessException(grade.getName() + " tiles can't be sold" + at);
                }
                if (!grade.isActive() && !gradesBefore.contains(grade.getId())) {
                    throw new BusinessException("Grade " + grade.getName() + " is inactive" + at);
                }
                line.setGrade(grade);
            }
            order.getLines().add(line);
        }
    }

    /** A party of the right kind, active (unless this order already had it). */
    private Party requireParty(Long id, PartyType type, Party before) {
        Party party = partyRepository.findById(id).orElseThrow(() -> new NotFoundException("Party", id));
        if (party.getPartyType() != type) {
            throw new BusinessException(party.getName() + " is not a " + type.label() + ". " + switch (type) {
                case DEALER -> "A sale is billed to a dealer.";
                case JOBWORK -> "A jobwork order is billed to the company whose jobwork it is.";
                default -> "Choose a transporter from Parties.";
            });
        }
        if (!party.isActive() && (before == null || !before.getId().equals(id))) {
            throw new BusinessException(party.getName() + " is inactive");
        }
        return party;
    }

    // ------------------------------------------------------------------ the response, with its checks

    private record DesignGrade(Long designId, Long gradeId) {
    }

    /** Needs the plant, party and transporter loaded; lines, designs (with sizes) and grades are loaded as needed. */
    private SalesOrderResponse toResponse(SalesOrder o) {
        List<SalesOrderLine> lines = o.getLines();
        boolean cancelled = o.getStatus() == SalesOrderStatus.CANCELLED;

        // Boxes in stock per design + grade, for all lines in one query. Nothing to check once cancelled.
        Map<DesignGrade, Long> stock = new HashMap<>();
        List<Long> designIds = lines.stream().map(SalesOrderLine::getDesign).filter(Objects::nonNull)
                .map(Design::getId).distinct().toList();
        if (!cancelled && !designIds.isEmpty()) {
            for (DesignGradeStock s : stockRepository.totalsByDesignAndGrade(o.getPlant().getId(), designIds)) {
                stock.put(new DesignGrade(s.designId(), s.gradeId()), s.boxes());
            }
        }
        Map<String, ProductAlias> aliases = aliasRepository.findByNameKeyIn(lines.stream()
                        .map(l -> ProductMatcher.key(l.getDescription())).distinct().toList()).stream()
                .collect(Collectors.toMap(ProductAlias::getNameKey, Function.identity()));

        // Two lines of the same design and grade share its stock: the first line is served first.
        Map<DesignGrade, Long> left = new HashMap<>(stock);
        List<SalesOrderResponse.Line> out = new ArrayList<>();
        for (SalesOrderLine l : lines) {
            Design design = l.getDesign();
            Grade grade = l.getGrade();
            TileSize size = design == null ? null : design.getTileSize();
            Long inStock = null;
            Integer shortBy = null;
            if (design != null && grade != null && !cancelled) {
                DesignGrade dg = new DesignGrade(design.getId(), grade.getId());
                inStock = stock.getOrDefault(dg, 0L);
                long available = left.getOrDefault(dg, 0L);
                shortBy = (int) Math.max(0, l.getBoxes() - available);
                left.put(dg, Math.max(0, available - l.getBoxes()));
            }
            ProductAlias alias = aliases.get(ProductMatcher.key(l.getDescription()));
            boolean remembered = alias != null && design != null && grade != null
                    && alias.getDesign().getId().equals(design.getId()) && alias.getGrade().getId().equals(grade.getId());
            out.add(new SalesOrderResponse.Line(l.getId(), l.getLineNo(), l.getDescription(),
                    design == null ? null : ProductMatcher.option(design),
                    grade == null ? null : ProductMatcher.option(grade), l.getBoxes(), l.getMrpBox(), l.getMrpPiece(),
                    l.getBatchRef(), size == null ? null : FgStockRow.sqm(l.getBoxes(), size), weight(l.getBoxes(), size),
                    inStock, shortBy, remembered));
        }

        Party t = o.getTransporter();
        return new SalesOrderResponse(o.getId(), o.getPlant().getId(), o.getPlant().getCode(), o.getSlipNo(),
                o.getSlipDate(), o.getOrderRef(), o.getOrderDate(), o.getOrderType(), o.getStatus(), o.getSource(),
                SalesParty.from(o.getParty()),
                t == null ? null : new SalesOption(t.getId(), t.getCode(), t.getName(), t.getCity()),
                o.getSlipBoxes(), o.getSlipWeightKg(), o.getRemarks(), out, totals(o, out), o.getConfirmedAt(),
                o.getConfirmedBy(), o.getCancelledAt(), o.getCancelledBy(), o.getCancelReason(), o.getCreatedAt(),
                o.getCreatedBy(), o.getVersion());
    }

    /** Glaze's own totals, and how far they are from what the slip prints. */
    private static SalesOrderResponse.Totals totals(SalesOrder o, List<SalesOrderResponse.Line> lines) {
        int boxes = lines.stream().mapToInt(SalesOrderResponse.Line::boxes).sum();
        // a total is only shown when every line has its part (a partial total would look like a wrong slip)
        BigDecimal sqm = sumOrNull(lines.stream().map(SalesOrderResponse.Line::sqm).toList());
        BigDecimal weight = sumOrNull(lines.stream().map(SalesOrderResponse.Line::weightKg).toList());
        Integer boxesDiff = o.getSlipBoxes() == null ? null : boxes - o.getSlipBoxes();
        BigDecimal weightDiff = weight == null || o.getSlipWeightKg() == null ? null
                : weight.subtract(o.getSlipWeightKg());
        Boolean weightOk = weightDiff == null ? null
                : weightDiff.abs().compareTo(o.getSlipWeightKg().multiply(WEIGHT_TOLERANCE)) <= 0;
        int unmatched = (int) lines.stream().filter(l -> l.design() == null || l.grade() == null).count();
        int shortLines = (int) lines.stream().filter(l -> l.shortBy() != null && l.shortBy() > 0).count();
        return new SalesOrderResponse.Totals(boxes, sqm, weight, boxesDiff, weightDiff, weightOk, unmatched, shortLines);
    }

    /** boxes x box weight of the size, or null when the size has no box weight. */
    private static BigDecimal weight(int boxes, TileSize size) {
        return size == null || size.getBoxWeightKg() == null ? null
                : size.getBoxWeightKg().multiply(BigDecimal.valueOf(boxes)).setScale(1, RoundingMode.HALF_UP);
    }

    private static BigDecimal sumOrNull(List<BigDecimal> values) {
        if (values.isEmpty() || values.stream().anyMatch(Objects::isNull)) {
            return null;
        }
        return values.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    // ------------------------------------------------------------------ small helpers

    private SalesOrder find(Long id) {
        SalesOrder order = orderRepository.findWithDetailsById(id).orElseThrow(() -> new NotFoundException("Sales order", id));
        PlantAccess.require(order.getPlant().getId());
        return order;
    }

    private static void requireStatus(SalesOrder order, SalesOrderStatus needed, String action) {
        if (order.getStatus() != needed) {
            throw new BusinessException("Only a " + needed.name().toLowerCase(Locale.ROOT) + " order can be " + action
                    + ". Slip " + order.getSlipNo() + " is " + order.getStatus().name().toLowerCase(Locale.ROOT));
        }
    }

    private static void requireNotFuture(LocalDate date, String what) {
        if (date.isAfter(LocalDate.now(PLANT_ZONE))) {
            throw new BusinessException("The " + what + " can't be in the future");
        }
    }

    private static <T> T found(Map<Long, T> map, Long id) {
        T value = map.get(id);
        if (value == null) {
            throw new NotFoundException("Record", id);
        }
        return value;
    }

    private static List<Long> nonNull(List<SalesLineRequest> rows, Function<SalesLineRequest, Long> field) {
        return rows.stream().map(field).filter(Objects::nonNull).distinct().toList();
    }

    private static <T> Map<Long, T> byId(Collection<T> items, Function<T, Long> id) {
        return items.stream().collect(Collectors.toMap(id, Function.identity()));
    }
}
