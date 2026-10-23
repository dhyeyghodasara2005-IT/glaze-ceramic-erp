package com.ceramicerp.fg;

import com.ceramicerp.common.PageResponse;
import com.ceramicerp.common.Text;
import com.ceramicerp.common.exception.BusinessException;
import com.ceramicerp.common.exception.NotFoundException;
import com.ceramicerp.fg.dto.FgDocumentRequest;
import com.ceramicerp.fg.dto.FgDocumentResponse;
import com.ceramicerp.fg.dto.FgDocumentSummary;
import com.ceramicerp.fg.dto.FgLineRequest;
import com.ceramicerp.master.Design;
import com.ceramicerp.master.DesignRepository;
import com.ceramicerp.master.Grade;
import com.ceramicerp.master.GradeRepository;
import com.ceramicerp.numbering.DocumentNumberService;
import com.ceramicerp.organization.Plant;
import com.ceramicerp.organization.PlantService;
import com.ceramicerp.security.CurrentUser;
import com.ceramicerp.security.PlantAccess;
import com.ceramicerp.store.DocStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * The warehouse's documents: opening stock, breakage and regrading. Drafts can be changed freely; posting
 * gives the number and moves the stock through FgStockService; cancelling moves it back.
 */
@Service
@RequiredArgsConstructor
public class FgDocumentService {

    /** The plants are in India: "today" is Indian time, whatever zone the server runs in. */
    private static final ZoneId PLANT_ZONE = ZoneId.of("Asia/Kolkata");

    private final FgDocumentRepository documentRepository;
    private final FgStockRepository stockRepository;
    private final FgPillarRepository pillarRepository;
    private final DesignRepository designRepository;
    private final GradeRepository gradeRepository;
    private final FgStockService stockService;
    private final PlantService plantService;
    private final DocumentNumberService numberService;

    @Transactional(readOnly = true)
    public PageResponse<FgDocumentSummary> search(Long plantId, FgDocType type, DocStatus status, LocalDate from,
                                                  LocalDate to, String search, int page, int size) {
        PlantAccess.require(plantId);
        String pattern = Text.trimToNull(search) == null ? null : "%" + search.trim().toLowerCase(Locale.ROOT) + "%";
        var pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100),
                Sort.by(Sort.Order.desc("docDate"), Sort.Order.desc("id")));
        return PageResponse.of(documentRepository.search(plantId, type, status, from, to, pattern, pageable),
                FgDocumentSummary::from);
    }

    @Transactional(readOnly = true)
    public FgDocumentResponse get(Long id) {
        return FgDocumentResponse.from(find(id));
    }

    @Transactional
    public FgDocumentResponse create(FgDocumentRequest req) {
        Plant plant = plantService.find(req.plantId());
        PlantAccess.require(plant.getId());
        FgDocument doc = new FgDocument();
        doc.setPlant(plant);
        doc.setDocType(req.docType());
        apply(doc, req);
        return FgDocumentResponse.from(documentRepository.saveAndFlush(doc));
    }

    @Transactional
    public FgDocumentResponse update(Long id, FgDocumentRequest req) {
        FgDocument doc = find(id);
        requireDraft(doc, "edited");
        if (!doc.getPlant().getId().equals(req.plantId()) || doc.getDocType() != req.docType()) {
            throw new BusinessException("A document can't be moved to another plant or type");
        }
        apply(doc, req);
        return FgDocumentResponse.from(documentRepository.saveAndFlush(doc));
    }

    @Transactional
    public void deleteDraft(Long id) {
        FgDocument doc = find(id);
        requireDraft(doc, "deleted");
        documentRepository.delete(doc);
    }

    @Transactional
    public FgDocumentResponse post(Long id) {
        FgDocument doc = find(id);
        requireDraft(doc, "posted");
        requireNotFuture(doc.getDocDate());
        doc.setDocNo(numberService.next(doc.getPlant(), doc.getDocType(), doc.getDocDate()));
        doc.setStatus(DocStatus.POSTED);
        doc.setPostedAt(Instant.now());
        doc.setPostedBy(CurrentUser.get().getUsername());
        // The engine checks there are enough boxes NOW (a draft may be days old) and refuses otherwise.
        stockService.applyAll(movements(doc, 1),
                new FgStockService.Source(doc.getDocType().source(), doc.getId(), doc.getDocNo(), doc.getDocDate(), false));
        return FgDocumentResponse.from(doc);
    }

    /** Needs FG_CANCEL (checked by the controller). Refused if the boxes have moved on since. */
    @Transactional
    public FgDocumentResponse cancel(Long id, String reason) {
        FgDocument doc = find(id);
        if (doc.getStatus() != DocStatus.POSTED) {
            throw new BusinessException("Only a posted document can be cancelled");
        }
        stockService.applyAll(movements(doc, -1),
                new FgStockService.Source(doc.getDocType().source(), doc.getId(), doc.getDocNo(), LocalDate.now(PLANT_ZONE), true));
        doc.setStatus(DocStatus.CANCELLED);
        doc.setCancelledAt(Instant.now());
        doc.setCancelledBy(CurrentUser.get().getUsername());
        doc.setCancelReason(reason.trim());
        return FgDocumentResponse.from(doc);
    }

    // ------------------------------------------------------------------ stock movements

    /**
     * What a document does to the stock. sign 1 = posting, -1 = cancelling (everything the other way round).
     * <ul>
     *   <li>OPENING: the boxes come in at their pillar, with the batch printed on them (no lot).</li>
     *   <li>BREAKAGE: the boxes go out of the stock row they were taken from.</li>
     *   <li>REGRADE: out of the old row, into a row of the same batch with the new grade, shade, caliber and pillar.</li>
     * </ul>
     */
    private static List<FgStockService.Movement> movements(FgDocument doc, int sign) {
        List<FgStockService.Movement> moves = new ArrayList<>();
        for (FgDocumentLine l : doc.getLines()) {
            int boxes = sign * l.getBoxes();
            switch (doc.getDocType()) {
                case OPENING -> moves.add(new FgStockService.Movement(new FgStockService.Batch(l.getPillar(), l.getDesign(),
                        null, l.getBatchCode(), l.getGrade(), l.getShade(), l.getCaliber()), boxes));
                case BREAKAGE -> moves.add(new FgStockService.Movement(batchOf(l.getFromStock()), -boxes));
                case REGRADE -> {
                    FgStock from = l.getFromStock();
                    moves.add(new FgStockService.Movement(batchOf(from), -boxes));
                    moves.add(new FgStockService.Movement(new FgStockService.Batch(l.getPillar(), from.getDesign(),
                            from.getLotId(), from.getBatchCode(), l.getGrade(), l.getShade(), l.getCaliber()), boxes));
                }
            }
        }
        return moves;
    }

    private static FgStockService.Batch batchOf(FgStock s) {
        return new FgStockService.Batch(s.getPillar(), s.getDesign(), s.getLotId(), s.getBatchCode(), s.getGrade(),
                s.getShade(), s.getCaliber());
    }

    // ------------------------------------------------------------------ checking a draft

    private void apply(FgDocument doc, FgDocumentRequest req) {
        requireNotFuture(req.docDate());
        doc.setDocDate(req.docDate());
        doc.setRemarks(Text.trimToNull(req.remarks()));

        // Things already on this draft may stay even if they have been closed / made inactive since.
        Set<Long> pillarsBefore = ids(doc.getLines().stream().map(FgDocumentLine::getPillar).filter(Objects::nonNull).map(FgPillar::getId).toList());
        Set<Long> designsBefore = ids(doc.getLines().stream().map(FgDocumentLine::getDesign).filter(Objects::nonNull).map(Design::getId).toList());
        Set<Long> gradesBefore = ids(doc.getLines().stream().map(FgDocumentLine::getGrade).filter(Objects::nonNull).map(Grade::getId).toList());

        // Everything the rows point at, each kind in one query.
        Map<Long, FgStock> stocks = byId(stockRepository.findAllById(nonNull(req.lines(), FgLineRequest::fromStockId)), FgStock::getId);
        Map<Long, FgPillar> pillars = byId(pillarRepository.findByIdIn(nonNull(req.lines(), FgLineRequest::pillarId)), FgPillar::getId);
        Map<Long, Design> designs = byId(designRepository.findAllById(nonNull(req.lines(), FgLineRequest::designId)), Design::getId);
        Map<Long, Grade> grades = byId(gradeRepository.findAllById(nonNull(req.lines(), FgLineRequest::gradeId)), Grade::getId);

        doc.getLines().clear();
        if (doc.getId() != null) {
            // delete the old rows before the new ones go in (see PackEntryService)
            documentRepository.flush();
        }
        int lineNo = 0;
        for (FgLineRequest row : req.lines()) {
            lineNo++;
            String at = " (row " + lineNo + ")";
            FgDocumentLine line = new FgDocumentLine();
            line.setDocument(doc);
            line.setLineNo(lineNo);
            line.setBoxes(row.boxes());
            line.setRemarks(Text.trimToNull(row.remarks()));

            switch (doc.getDocType()) {
                case OPENING -> {
                    line.setPillar(requirePillar(pillars, row.pillarId(), doc, pillarsBefore, at));
                    Design design = require(designs, row.designId(), "Choose the design" + at);
                    if (!design.isActive() && !designsBefore.contains(design.getId())) {
                        throw new BusinessException("Design " + design.getCode() + " is inactive" + at);
                    }
                    line.setDesign(design);
                    line.setBatchCode(FgCodes.batch(require(row.batchCode(), "Enter the batch printed on the boxes" + at)));
                    setTarget(line, row, grades, gradesBefore, at);
                }
                case BREAKAGE -> {
                    FgStock from = requireStock(stocks, row.fromStockId(), doc, row.boxes(), at);
                    line.setFromStock(from);
                    line.setReason(require(row.reason(), "Choose why the boxes broke" + at));
                }
                case REGRADE -> {
                    FgStock from = requireStock(stocks, row.fromStockId(), doc, row.boxes(), at);
                    line.setFromStock(from);
                    line.setPillar(requirePillar(pillars, row.pillarId(), doc, pillarsBefore, at));
                    setTarget(line, row, grades, gradesBefore, at);
                    boolean same = from.getGrade().getId().equals(line.getGrade().getId())
                            && from.getShade().equals(line.getShade()) && from.getCaliber().equals(line.getCaliber())
                            && from.getPillar().getId().equals(line.getPillar().getId());
                    if (same) {
                        throw new BusinessException("Nothing changes" + at + ": choose a new grade, shade, caliber or pillar");
                    }
                }
            }
            doc.getLines().add(line);
        }
    }

    /** The (new) grade, shade and caliber of an opening or regrade row. */
    private static void setTarget(FgDocumentLine line, FgLineRequest row, Map<Long, Grade> grades, Set<Long> gradesBefore,
                                  String at) {
        Grade grade = require(grades, row.gradeId(), "Choose the grade" + at);
        if (!grade.isSellable()) {
            throw new BusinessException(grade.getName() + " can't be in stock" + at + ". Write broken boxes off as breakage.");
        }
        if (!grade.isActive() && !gradesBefore.contains(grade.getId())) {
            throw new BusinessException("Grade " + grade.getName() + " is inactive" + at);
        }
        line.setGrade(grade);
        line.setShade(FgCodes.shadeOrCaliber(require(row.shade(), "Enter the shade" + at), "Shade"));
        line.setCaliber(FgCodes.shadeOrCaliber(require(row.caliber(), "Enter the caliber" + at), "Caliber"));
    }

    /** A stock row of this plant with at least this many boxes in it right now. */
    private static FgStock requireStock(Map<Long, FgStock> stocks, Long id, FgDocument doc, int boxes, String at) {
        FgStock stock = require(stocks, id, "Choose the boxes to take" + at);
        if (!stock.getPlant().getId().equals(doc.getPlant().getId())) {
            throw new BusinessException("Those boxes are not in plant " + doc.getPlant().getCode() + at);
        }
        if (boxes > stock.getBoxes()) {
            throw new BusinessException("Only " + stock.getBoxes() + " boxes of " + stock.getDesign().getName() + " "
                    + stock.getGrade().getName() + " " + stock.getShade() + "/" + stock.getCaliber() + " at "
                    + stock.getPillar().getCode() + at);
        }
        return stock;
    }

    /** A pillar of this plant, open (unless this draft already had it). */
    private static FgPillar requirePillar(Map<Long, FgPillar> pillars, Long id, FgDocument doc, Set<Long> before, String at) {
        FgPillar pillar = require(pillars, id, "Choose the pillar" + at);
        if (!pillar.getGodown().getPlant().getId().equals(doc.getPlant().getId())) {
            throw new BusinessException("Pillar " + pillar.getCode() + " is not in plant " + doc.getPlant().getCode());
        }
        if (!(pillar.isActive() && pillar.getGodown().isActive()) && !before.contains(pillar.getId())) {
            throw new BusinessException("Pillar " + pillar.getGodown().getCode() + " " + pillar.getCode() + " is closed" + at);
        }
        return pillar;
    }

    private FgDocument find(Long id) {
        FgDocument doc = documentRepository.findWithPlantById(id).orElseThrow(() -> new NotFoundException("Document", id));
        PlantAccess.require(doc.getPlant().getId());
        return doc;
    }

    private static void requireDraft(FgDocument doc, String action) {
        if (doc.getStatus() != DocStatus.DRAFT) {
            throw new BusinessException("Only a draft can be " + action + ". " + doc.getDocNo() + " is "
                    + doc.getStatus().name().toLowerCase(Locale.ROOT));
        }
    }

    private static void requireNotFuture(LocalDate date) {
        if (date.isAfter(LocalDate.now(PLANT_ZONE))) {
            throw new BusinessException("The date can't be in the future");
        }
    }

    // ------------------------------------------------------------------ small helpers

    private static <T> T require(T value, String message) {
        if (value == null || (value instanceof String s && s.isBlank())) {
            throw new BusinessException(message);
        }
        return value;
    }

    private static <T> T require(Map<Long, T> map, Long id, String message) {
        if (id == null) {
            throw new BusinessException(message);
        }
        T value = map.get(id);
        if (value == null) {
            throw new NotFoundException("Record", id);
        }
        return value;
    }

    private static List<Long> nonNull(List<FgLineRequest> rows, Function<FgLineRequest, Long> field) {
        return rows.stream().map(field).filter(Objects::nonNull).distinct().toList();
    }

    private static <T> Map<Long, T> byId(Collection<T> items, Function<T, Long> id) {
        return items.stream().collect(Collectors.toMap(id, Function.identity()));
    }

    private static Set<Long> ids(List<Long> ids) {
        return Set.copyOf(ids);
    }
}
