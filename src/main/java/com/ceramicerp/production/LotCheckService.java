package com.ceramicerp.production;

import com.ceramicerp.common.PageResponse;
import com.ceramicerp.common.Text;
import com.ceramicerp.common.exception.BusinessException;
import com.ceramicerp.common.exception.NotFoundException;
import com.ceramicerp.master.Shift;
import com.ceramicerp.master.ShiftService;
import com.ceramicerp.numbering.DocumentNumberService;
import com.ceramicerp.organization.Plant;
import com.ceramicerp.organization.PlantService;
import com.ceramicerp.production.dto.LotCheckRequest;
import com.ceramicerp.production.dto.LotCheckResponse;
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
import java.time.format.DateTimeFormatter;
import java.util.Locale;

import static com.ceramicerp.production.ProductionRules.requireActiveIfChanged;
import static com.ceramicerp.production.ProductionRules.requireNotFuture;

/**
 * Quality checks in the cooling area. A check moves no tiles, so there is no draft: it is numbered
 * and final when saved. A wrong one is cancelled (with a reason) and entered again.
 */
@Service
@RequiredArgsConstructor
public class LotCheckService {

    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.ENGLISH);

    private final LotCheckRepository checkRepository;
    private final ProductionLotRepository lotRepository;
    private final PlantService plantService;
    private final ShiftService shiftService;
    private final DocumentNumberService numberService;

    @Transactional(readOnly = true)
    public PageResponse<LotCheckResponse> search(Long plantId, DocStatus status, LocalDate from, LocalDate to,
                                                 String search, int page, int size) {
        PlantAccess.require(plantId);
        var pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100),
                Sort.by(Sort.Order.desc("checkDate"), Sort.Order.desc("id")));
        return PageResponse.of(checkRepository.search(plantId, status, from, to, ProductionRules.pattern(search), pageable),
                LotCheckResponse::from);
    }

    @Transactional(readOnly = true)
    public LotCheckResponse get(Long id) {
        return LotCheckResponse.from(find(id));
    }

    @Transactional
    public LotCheckResponse create(LotCheckRequest req) {
        Plant plant = plantService.find(req.plantId());
        PlantAccess.require(plant.getId());
        ProductionLot lot = lotRepository.findWithDetailsById(req.lotId())
                .filter(l -> l.getPlant().getId().equals(plant.getId()))
                .orElseThrow(() -> new NotFoundException("Design lot", req.lotId()));
        if (lot.getStatus() == LotStatus.CANCELLED) {
            throw new BusinessException("Lot " + lot.getLotNo() + " is cancelled");
        }
        requireNotFuture(req.checkDate());
        if (req.checkDate().isBefore(lot.getOpenedDate())) {
            throw new BusinessException("The date can't be before the lot started (" + lot.getOpenedDate().format(DAY) + ")");
        }
        Shift shift = shiftService.find(req.shiftId());
        requireActiveIfChanged(shift.isActive(), null, shift, "Shift " + shift.getCode());

        LotCheck check = new LotCheck();
        check.setPlant(plant);
        check.setLot(lot);
        check.setCheckDate(req.checkDate());
        check.setShift(shift);
        check.setSamples(req.samples());
        check.setCentreBendMm(req.centreBendMm());
        check.setEdgeBendMm(req.edgeBendMm());
        check.setWarpageMm(req.warpageMm());
        check.setLengthMm(req.lengthMm());
        check.setWidthMm(req.widthMm());
        check.setThicknessMm(req.thicknessMm());
        check.setVerdict(req.verdict());
        check.setExpectedTopPct(req.expectedTopPct());
        check.setRemarks(Text.trimToNull(req.remarks()));
        check.setCheckNo(numberService.next(plant, ProductionDocType.LOT_CHECK, req.checkDate()));
        return LotCheckResponse.from(checkRepository.save(check));
    }

    /** The checker (QUALITY_CHECK) or a manager (PRODUCTION_CANCEL) - checked by the controller. */
    @Transactional
    public LotCheckResponse cancel(Long id, String reason) {
        LotCheck check = find(id);
        if (check.getStatus() != DocStatus.POSTED) {
            throw new BusinessException(check.getCheckNo() + " is already cancelled");
        }
        check.setStatus(DocStatus.CANCELLED);
        check.setCancelledAt(Instant.now());
        check.setCancelledBy(CurrentUser.get().getUsername());
        check.setCancelReason(reason.trim());
        return LotCheckResponse.from(check);
    }

    private LotCheck find(Long id) {
        LotCheck check = checkRepository.findWithDetailsById(id).orElseThrow(() -> new NotFoundException("Quality check", id));
        PlantAccess.require(check.getPlant().getId());
        return check;
    }
}
