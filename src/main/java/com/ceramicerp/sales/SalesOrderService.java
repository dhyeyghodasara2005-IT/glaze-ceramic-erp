package com.ceramicerp.sales;

import com.ceramicerp.common.PageResponse;
import com.ceramicerp.common.Text;
import com.ceramicerp.common.exception.BusinessException;
import com.ceramicerp.common.exception.NotFoundException;
import com.ceramicerp.master.Design;
import com.ceramicerp.master.DesignRepository;
import com.ceramicerp.master.Grade;
import com.ceramicerp.master.GradeRepository;
import com.ceramicerp.organization.Plant;
import com.ceramicerp.organization.PlantService;
import com.ceramicerp.party.Party;
import com.ceramicerp.party.PartyRepository;
import com.ceramicerp.party.PartyType;
import com.ceramicerp.sales.dto.SalesLineRequest;
import com.ceramicerp.sales.dto.SalesOrderRequest;
import com.ceramicerp.sales.dto.SalesOrderResponse;
import com.ceramicerp.sales.dto.SalesOrderSummary;
import com.ceramicerp.security.CurrentUser;
import com.ceramicerp.security.PlantAccess;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
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
 * design and grade, remembers those matches for the next slip, and picks the boxes to load (LoadingPlanner). Until
 * loading starts a confirmed order can be reopened or planned again; cancelling needs SALES_CANCEL.
 * <p>
 * Nothing here moves stock: the boxes leave the godown when the loading is confirmed (LoadingService).
 */
@Service
@RequiredArgsConstructor
public class SalesOrderService {

    /** The plants are in India: "today" is Indian time, whatever zone the server runs in. */
    private static final ZoneId PLANT_ZONE = ZoneId.of("Asia/Kolkata");

    private final SalesOrderRepository orderRepository;
    private final ProductAliasRepository aliasRepository;
    private final PartyRepository partyRepository;
    private final DesignRepository designRepository;
    private final GradeRepository gradeRepository;
    private final PlantService plantService;
    private final LoadingPlanner planner;
    private final SalesOrderView view;

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
        return view.of(find(id));
    }

    @Transactional
    public SalesOrderResponse create(SalesOrderRequest req) {
        Plant plant = plantService.find(req.plantId());
        PlantAccess.require(plant.getId());
        SalesOrder order = new SalesOrder();
        order.setPlant(plant);
        order.setSource(OrderSource.TYPED);
        apply(order, req);
        return view.of(orderRepository.saveAndFlush(order));
    }

    @Transactional
    public SalesOrderResponse update(Long id, SalesOrderRequest req) {
        SalesOrder order = find(id);
        requireStatus(order, SalesOrderStatus.DRAFT, "edited");
        if (!order.getPlant().getId().equals(req.plantId())) {
            throw new BusinessException("An order can't be moved to another plant");
        }
        apply(order, req);
        return view.of(orderRepository.saveAndFlush(order));
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
        planner.plan(order);
        return view.of(order);
    }

    /** Back to draft, to correct a confirmed order: its boxes are no longer kept. Only until loading starts. */
    @Transactional
    public SalesOrderResponse reopen(Long id) {
        SalesOrder order = find(id);
        requireStatus(order, SalesOrderStatus.CONFIRMED, "reopened");
        requireLoadingNotStarted(order);
        order.setStatus(SalesOrderStatus.DRAFT);
        order.setConfirmedAt(null);
        order.setConfirmedBy(null);
        order.setPlannedAt(null);
        order.getPicks().clear();
        return view.of(order);
    }

    /**
     * Picks the boxes again, e.g. after more boxes were packed for a short line, or after boxes kept for it broke.
     * Only until loading starts.
     */
    @Transactional
    public SalesOrderResponse replan(Long id) {
        SalesOrder order = find(id);
        requireStatus(order, SalesOrderStatus.CONFIRMED, "planned again");
        requireLoadingNotStarted(order);
        planner.plan(order);
        return view.of(order);
    }

    /** Needs SALES_CANCEL (checked by the controller). A draft is deleted instead. The kept boxes are free again. */
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
        return view.of(order);
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
        // the same slip entered (or uploaded) twice would be loaded twice. Per party: each principal's own
        // system numbers its slips, so two principals may print the same number.
        Party party = requireParty(req.partyId(), req.orderType().partyType(), order.getParty());
        boolean taken = order.getId() == null
                ? orderRepository.existsByPlantIdAndPartyIdAndSlipNo(plantId, party.getId(), slipNo)
                : orderRepository.existsByPlantIdAndPartyIdAndSlipNoAndIdNot(plantId, party.getId(), slipNo, order.getId());
        if (taken) {
            throw new BusinessException("Loading slip " + slipNo + " of " + party.getName() + " is already entered in plant "
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
        order.setParty(party);
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

    /** Once the loader has ticked something, the plan is what is on the truck: it can't be thrown away. */
    static void requireLoadingNotStarted(SalesOrder order) {
        if (order.getPicks().stream().anyMatch(p -> p.getLoadedBoxes() != null)) {
            throw new BusinessException("Loading has started for slip " + order.getSlipNo()
                    + ". Untick the loaded boxes first.");
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
