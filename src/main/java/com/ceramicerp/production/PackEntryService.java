package com.ceramicerp.production;

import com.ceramicerp.common.PageResponse;
import com.ceramicerp.common.Text;
import com.ceramicerp.common.exception.BusinessException;
import com.ceramicerp.common.exception.NotFoundException;
import com.ceramicerp.fg.FgCodes;
import com.ceramicerp.fg.FgPillar;
import com.ceramicerp.fg.FgPillarRepository;
import com.ceramicerp.fg.FgSource;
import com.ceramicerp.fg.FgStockService;
import com.ceramicerp.master.Grade;
import com.ceramicerp.master.GradeRepository;
import com.ceramicerp.master.MachineRepository;
import com.ceramicerp.master.MachineType;
import com.ceramicerp.master.Shift;
import com.ceramicerp.master.ShiftService;
import com.ceramicerp.numbering.DocumentNumberService;
import com.ceramicerp.organization.Plant;
import com.ceramicerp.organization.PlantService;
import com.ceramicerp.production.dto.PackCodes;
import com.ceramicerp.production.dto.PackEntryRequest;
import com.ceramicerp.production.dto.PackEntryResponse;
import com.ceramicerp.production.dto.PackEntrySummary;
import com.ceramicerp.production.dto.PackLineRequest;
import com.ceramicerp.security.CurrentUser;
import com.ceramicerp.security.PlantAccess;
import com.ceramicerp.store.DocStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import static com.ceramicerp.production.ProductionRules.pct;
import static com.ceramicerp.production.ProductionRules.requireActiveIfChanged;
import static com.ceramicerp.production.ProductionRules.requireMachine;
import static com.ceramicerp.production.ProductionRules.requireNotFuture;
import static com.ceramicerp.production.ProductionRules.sqm;
import static com.ceramicerp.production.ProductionRules.sqmPerTile;

/**
 * Sorting & packing shift entries. The packing supervisor enters the boxes per grade, shade and
 * caliber plus the broken pieces; the server works out the pieces (boxes x pieces per box) and the
 * totals. Posting gives the number, makes the boxes count in the lot, and puts them into finished-goods
 * stock at the pillars the supervisor chose. Cancelling takes them out again.
 */
@Service
@RequiredArgsConstructor
public class PackEntryService {

    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.ENGLISH);

    private final PackEntryRepository entryRepository;
    private final ProductionLotRepository lotRepository;
    private final MachineRepository machineRepository;
    private final GradeRepository gradeRepository;
    private final FgPillarRepository pillarRepository;
    private final FgStockService fgStockService;
    private final PlantService plantService;
    private final ShiftService shiftService;
    private final DocumentNumberService numberService;

    @Transactional(readOnly = true)
    public PageResponse<PackEntrySummary> search(Long plantId, DocStatus status, LocalDate from, LocalDate to,
                                                 String search, int page, int size) {
        PlantAccess.require(plantId);
        return PageResponse.of(entryRepository.search(plantId, status, from, to, ProductionRules.pattern(search),
                        ProductionRules.page(page, size)),
                e -> PackEntrySummary.from(e, sqm(e.getPiecesPacked(), sqmPerTile(e.getLot().getDesign().getTileSize()))));
    }

    @Transactional(readOnly = true)
    public PackEntryResponse get(Long id) {
        return toResponse(find(id));
    }

    @Transactional
    public PackEntryResponse create(PackEntryRequest req) {
        Plant plant = plantService.find(req.plantId());
        PlantAccess.require(plant.getId());
        PackEntry entry = new PackEntry();
        entry.setPlant(plant);
        apply(entry, req);
        // saveAndFlush: the rows get their ids now, so the response can show them
        return toResponse(entryRepository.saveAndFlush(entry));
    }

    @Transactional
    public PackEntryResponse update(Long id, PackEntryRequest req) {
        PackEntry entry = find(id);
        requireDraft(entry, "edited");
        if (!entry.getPlant().getId().equals(req.plantId())) {
            throw new BusinessException("An entry can't be moved to another plant");
        }
        apply(entry, req);
        return toResponse(entryRepository.saveAndFlush(entry));
    }

    @Transactional
    public void deleteDraft(Long id) {
        PackEntry entry = find(id);
        requireDraft(entry, "deleted");
        entryRepository.delete(entry);
    }

    @Transactional
    public PackEntryResponse post(Long id) {
        PackEntry entry = find(id);
        requireDraft(entry, "posted");
        requireLotUsable(entry.getLot());
        requireNotFuture(entry.getProdDate());
        for (PackEntryLine line : entry.getLines()) {
            if (line.getPillar() == null) {      // a draft saved before pillars existed
                throw new BusinessException("Choose the pillar for row " + line.getLineNo() + ", then save the draft again");
            }
        }
        entry.setEntryNo(numberService.next(entry.getPlant(), ProductionDocType.PACK_ENTRY, entry.getProdDate()));
        entry.setStatus(DocStatus.POSTED);
        entry.setPostedAt(Instant.now());
        entry.setPostedBy(CurrentUser.get().getUsername());
        // the boxes go into finished-goods stock, each row at its pillar
        fgStockService.applyAll(movements(entry, 1),
                new FgStockService.Source(FgSource.PACK, entry.getId(), entry.getEntryNo(), entry.getProdDate(), false));
        return toResponse(entry);
    }

    /** Needs PRODUCTION_CANCEL (checked by the controller). */
    @Transactional
    public PackEntryResponse cancel(Long id, String reason) {
        PackEntry entry = find(id);
        if (entry.getStatus() != DocStatus.POSTED) {
            throw new BusinessException("Only a posted entry can be cancelled");
        }
        // Take the boxes back out of stock. If some have moved on since (broken, regraded, dispatched),
        // the engine refuses: those movements have to be undone first.
        fgStockService.applyAll(movements(entry, -1),
                new FgStockService.Source(FgSource.PACK, entry.getId(), entry.getEntryNo(), LocalDate.now(ProductionRules.PLANT_ZONE), true));
        entry.setStatus(DocStatus.CANCELLED);
        entry.setCancelledAt(Instant.now());
        entry.setCancelledBy(CurrentUser.get().getUsername());
        entry.setCancelReason(reason.trim());
        return toResponse(entry);
    }

    /** Shade and caliber codes this lot has used, for the entry's suggestions. */
    @Transactional(readOnly = true)
    public PackCodes codes(Long lotId) {
        ProductionLot lot = lotRepository.findById(lotId).orElseThrow(() -> new NotFoundException("Design lot", lotId));
        PlantAccess.require(lot.getPlant().getId());
        return new PackCodes(entryRepository.shadesOfLot(lotId), entryRepository.calibersOfLot(lotId));
    }

    private PackEntry find(Long id) {
        PackEntry entry = entryRepository.findWithDetailsById(id).orElseThrow(() -> new NotFoundException("Pack entry", id));
        PlantAccess.require(entry.getPlant().getId());
        return entry;
    }

    private static void requireDraft(PackEntry entry, String action) {
        if (entry.getStatus() != DocStatus.DRAFT) {
            throw new BusinessException("Only a draft can be " + action + ". " + entry.getEntryNo() + " is "
                    + entry.getStatus().name().toLowerCase(Locale.ROOT));
        }
    }

    private static void requireLotUsable(ProductionLot lot) {
        if (lot.getStatus() == LotStatus.CANCELLED) {
            throw new BusinessException("Lot " + lot.getLotNo() + " is cancelled");
        }
    }

    /** The pillar must be in this plant, and open (unless this draft already had it). */
    private static FgPillar requirePillar(FgPillar pillar, Long id, PackEntry entry, Set<Long> pillarsBefore) {
        if (pillar == null) {
            throw new NotFoundException("Pillar", id);
        }
        if (!pillar.getGodown().getPlant().getId().equals(entry.getPlant().getId())) {
            throw new BusinessException("Pillar " + pillar.getCode() + " is not in plant " + entry.getPlant().getCode());
        }
        boolean open = pillar.isActive() && pillar.getGodown().isActive();
        if (!open && !pillarsBefore.contains(pillar.getId())) {
            throw new BusinessException("Pillar " + pillar.getGodown().getCode() + " " + pillar.getCode()
                    + " is closed. Choose another one.");
        }
        return pillar;
    }

    /** One stock movement per row: +boxes when posting (sign 1), -boxes when cancelling (sign -1). */
    private static List<FgStockService.Movement> movements(PackEntry entry, int sign) {
        ProductionLot lot = entry.getLot();
        return entry.getLines().stream()
                .filter(l -> l.getPillar() != null)
                .map(l -> new FgStockService.Movement(new FgStockService.Batch(l.getPillar(), lot.getDesign(), lot.getId(),
                        lot.getLotNo(), l.getGrade(), l.getShade(), l.getCaliber()), sign * l.getBoxes()))
                .toList();
    }

    private PackEntryResponse toResponse(PackEntry e) {
        BigDecimal perTile = sqmPerTile(e.getLot().getDesign().getTileSize());
        Function<Integer, BigDecimal> area = pieces -> sqm(pieces, perTile);
        return PackEntryResponse.from(e, perTile, area, pct(e.getBroken(), e.getTilesSorted()));
    }

    private void apply(PackEntry entry, PackEntryRequest req) {
        ProductionLot lot = lotRepository.findWithDetailsById(req.lotId())
                .filter(l -> l.getPlant().getId().equals(entry.getPlant().getId()))
                .orElseThrow(() -> new NotFoundException("Design lot", req.lotId()));
        requireLotUsable(lot);
        requireNotFuture(req.prodDate());
        if (req.prodDate().isBefore(lot.getOpenedDate())) {
            throw new BusinessException("The date can't be before the lot started (" + lot.getOpenedDate().format(DAY) + ")");
        }
        entry.setLot(lot);
        entry.setProdDate(req.prodDate());

        Shift shift = shiftService.find(req.shiftId());
        requireActiveIfChanged(shift.isActive(), entry.getShift(), shift, "Shift " + shift.getCode());
        entry.setShift(shift);
        entry.setMachine(requireMachine(machineRepository, req.machineId(), entry.getPlant(), MachineType.SORTING_LINE,
                entry.getMachine()));

        var size = lot.getDesign().getTileSize();
        Integer perBox = size.getPcsPerBox();
        if (perBox == null || perBox < 1) {
            throw new BusinessException("Size " + size.getCode() + " has no pieces per box. Set it under Setup -> Sizes.");
        }

        // A grade or pillar that was already on this draft may stay even if it has been made inactive since.
        Set<Long> gradesBefore = entry.getLines().stream().map(l -> l.getGrade().getId()).collect(Collectors.toSet());
        Set<Long> pillarsBefore = entry.getLines().stream().filter(l -> l.getPillar() != null)
                .map(l -> l.getPillar().getId()).collect(Collectors.toSet());
        Map<Long, Grade> grades = gradeRepository.findAllById(req.lines().stream().map(PackLineRequest::gradeId).toList())
                .stream().collect(Collectors.toMap(Grade::getId, Function.identity()));
        Map<Long, FgPillar> pillars = pillarRepository.findByIdIn(req.lines().stream().map(PackLineRequest::pillarId).toList())
                .stream().collect(Collectors.toMap(FgPillar::getId, Function.identity()));

        entry.getLines().clear();
        if (entry.getId() != null) {
            // Delete the old rows now. Otherwise Hibernate inserts the new rows first, and a row kept
            // as it was (same grade, shade, caliber) breaks the database's one-row-per-combination rule.
            entryRepository.flush();
        }
        Set<String> seen = new HashSet<>();
        int lineNo = 0;
        int boxes = 0;
        int pieces = 0;
        for (PackLineRequest row : req.lines()) {
            Grade grade = grades.get(row.gradeId());
            if (grade == null) {
                throw new NotFoundException("Grade", row.gradeId());
            }
            if (!grade.isSellable()) {
                throw new BusinessException(grade.getName() + " tiles aren't boxed. Count them under Broken.");
            }
            if (!grade.isActive() && !gradesBefore.contains(grade.getId())) {
                throw new BusinessException("Grade " + grade.getName() + " is inactive. Choose an active one.");
            }
            String shade = FgCodes.shadeOrCaliber(row.shade(), "Shade");
            String caliber = FgCodes.shadeOrCaliber(row.caliber(), "Caliber");
            FgPillar pillar = requirePillar(pillars.get(row.pillarId()), row.pillarId(), entry, pillarsBefore);
            if (!seen.add(grade.getId() + "|" + shade + "|" + caliber + "|" + pillar.getId())) {
                throw new BusinessException(grade.getName() + ", shade " + shade + ", caliber " + caliber + " at pillar "
                        + pillar.getCode() + " is entered twice. Put those boxes in one row.");
            }
            PackEntryLine line = new PackEntryLine();
            line.setEntry(entry);
            line.setLineNo(++lineNo);
            line.setGrade(grade);
            line.setShade(shade);
            line.setCaliber(caliber);
            line.setPillar(pillar);
            line.setBoxes(row.boxes());
            line.setPieces(row.boxes() * perBox);
            entry.getLines().add(line);
            boxes += line.getBoxes();
            pieces += line.getPieces();
        }

        int broken = req.broken() == null ? 0 : req.broken();
        entry.setBoxes(boxes);
        entry.setPiecesPacked(pieces);
        entry.setBroken(broken);
        entry.setTilesSorted(pieces + broken);
        entry.setRemarks(Text.trimToNull(req.remarks()));
    }
}
