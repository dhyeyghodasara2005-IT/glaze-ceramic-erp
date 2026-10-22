package com.ceramicerp.production;

import com.ceramicerp.common.BaseEntity;
import com.ceramicerp.common.PageResponse;
import com.ceramicerp.common.Text;
import com.ceramicerp.common.exception.BusinessException;
import com.ceramicerp.common.exception.NotFoundException;
import com.ceramicerp.master.MachineRepository;
import com.ceramicerp.master.MachineType;
import com.ceramicerp.master.Shift;
import com.ceramicerp.master.ShiftService;
import com.ceramicerp.numbering.DocumentNumberService;
import com.ceramicerp.organization.Plant;
import com.ceramicerp.organization.PlantService;
import com.ceramicerp.production.dto.BodyBatchSummary;
import com.ceramicerp.production.dto.SprayRunRequest;
import com.ceramicerp.production.dto.SprayRunResponse;
import com.ceramicerp.production.dto.SprayRunSummary;
import com.ceramicerp.security.CurrentUser;
import com.ceramicerp.security.PlantAccess;
import com.ceramicerp.store.DocStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import static com.ceramicerp.production.ProductionRules.requireActiveIfChanged;
import static com.ceramicerp.production.ProductionRules.requireMachine;
import static com.ceramicerp.production.ProductionRules.requireNotFuture;

/**
 * Spray dryer runs: slip from DISCHARGED body batches becomes powder in a silo.
 * Posting marks the batches whose slip ran out as CONSUMED; cancelling gives them back.
 */
@Service
@RequiredArgsConstructor
public class SprayRunService {

    private final SprayRunRepository runRepository;
    private final BodyBatchRepository batchRepository;
    private final MachineRepository machineRepository;
    private final PlantService plantService;
    private final ShiftService shiftService;
    private final DocumentNumberService numberService;
    private final PressEntryPowderRepository powderRepository;

    @Transactional(readOnly = true)
    public PageResponse<SprayRunSummary> search(Long plantId, DocStatus status, LocalDate from, LocalDate to,
                                                String search, int page, int size) {
        PlantAccess.require(plantId);
        return PageResponse.of(runRepository.search(plantId, status, from, to, ProductionRules.pattern(search),
                ProductionRules.page(page, size)), SprayRunSummary::from);
    }

    @Transactional(readOnly = true)
    public SprayRunResponse get(Long id) {
        return SprayRunResponse.from(find(id));
    }

    /** Batches whose slip is waiting in a tank - what the spray dryer supervisor can pick from. */
    @Transactional(readOnly = true)
    public List<BodyBatchSummary> availableBatches(Long plantId) {
        PlantAccess.require(plantId);
        return batchRepository.findByPlantIdAndStatusOrderByMillingStartAsc(plantId, BodyBatchStatus.DISCHARGED)
                .stream().map(BodyBatchSummary::from).toList();
    }

    @Transactional
    public SprayRunResponse create(SprayRunRequest req) {
        Plant plant = plantService.find(req.plantId());
        PlantAccess.require(plant.getId());
        SprayRun run = new SprayRun();
        run.setPlant(plant);
        apply(run, req);
        return SprayRunResponse.from(runRepository.save(run));
    }

    @Transactional
    public SprayRunResponse update(Long id, SprayRunRequest req) {
        SprayRun run = find(id);
        requireDraft(run, "edited");
        if (!run.getPlant().getId().equals(req.plantId())) {
            throw new BusinessException("A run can't be moved to another plant");
        }
        apply(run, req);
        return SprayRunResponse.from(run);
    }

    @Transactional
    public void deleteDraft(Long id) {
        SprayRun run = find(id);
        requireDraft(run, "deleted");
        runRepository.delete(run);
    }

    @Transactional
    public SprayRunResponse post(Long id) {
        SprayRun run = find(id);
        requireDraft(run, "posted");
        requireNotFuture(run.getProdDate());
        // Saved as a draft hours ago? Another run may have used a batch up since then.
        for (SprayRunBatch rb : run.getBatches()) {
            requireWaiting(rb.getBodyBatch());
        }

        run.setRunNo(numberService.next(run.getPlant(), ProductionDocType.SPRAY_RUN, run.getProdDate()));
        for (SprayRunBatch rb : run.getBatches()) {
            if (rb.isFinished()) {
                // Two runs finishing the same batch at once? Both change the batch row, and its @Version
                // makes the second one fail - so a batch can never be consumed twice.
                rb.getBodyBatch().setStatus(BodyBatchStatus.CONSUMED);
            }
        }
        run.setPowderLeft(run.getPowderTon());   // the silo now holds this run's powder
        run.setStatus(DocStatus.POSTED);
        run.setPostedAt(Instant.now());
        run.setPostedBy(CurrentUser.get().getUsername());
        return SprayRunResponse.from(run);
    }

    /** The batches this run used up are waiting again; the run stays for the record. */
    @Transactional
    public SprayRunResponse cancel(Long id, String reason) {
        SprayRun run = find(id);
        if (run.getStatus() != DocStatus.POSTED) {
            throw new BusinessException("Only a posted run can be cancelled");
        }
        // Its powder already went into tiles? Then undo those press entries first, or the lot's trace breaks.
        List<PressEntryPowder> used = powderRepository.findForRun(id, DocStatus.POSTED);
        if (!used.isEmpty()) {
            throw new BusinessException("Powder from " + run.getRunNo() + " was already used by press entry "
                    + used.get(0).getEntry().getEntryNo() + ". Cancel that entry first.");
        }
        run.setPowderLeft(BigDecimal.ZERO);
        for (SprayRunBatch rb : run.getBatches()) {
            if (rb.isFinished() && rb.getBodyBatch().getStatus() == BodyBatchStatus.CONSUMED) {
                rb.getBodyBatch().setStatus(BodyBatchStatus.DISCHARGED);
            }
        }
        run.setStatus(DocStatus.CANCELLED);
        run.setCancelledAt(Instant.now());
        run.setCancelledBy(CurrentUser.get().getUsername());
        run.setCancelReason(reason.trim());
        return SprayRunResponse.from(run);
    }

    private SprayRun find(Long id) {
        SprayRun run = runRepository.findWithDetailsById(id).orElseThrow(() -> new NotFoundException("Spray dryer run", id));
        PlantAccess.require(run.getPlant().getId());
        return run;
    }

    private static void requireDraft(SprayRun run, String action) {
        if (run.getStatus() != DocStatus.DRAFT) {
            throw new BusinessException("Only a draft can be " + action + ". " + run.getRunNo() + " is "
                    + run.getStatus().name().toLowerCase(Locale.ROOT));
        }
    }

    private static void requireWaiting(BodyBatch batch) {
        if (batch.getStatus() != BodyBatchStatus.DISCHARGED) {
            String name = batch.getBatchNo() != null ? batch.getBatchNo() : "A draft batch";
            throw new BusinessException(name + " is not waiting in a slip tank (it is "
                    + batch.getStatus().name().toLowerCase(Locale.ROOT) + ")");
        }
    }

    private void apply(SprayRun run, SprayRunRequest req) {
        requireNotFuture(req.prodDate());
        ProductionRules.requireNotFuture(req.runStart(), "The run start");
        if (req.runEnd() != null) {
            ProductionRules.requireNotFuture(req.runEnd(), "The run end");
        }
        ProductionRules.requireOrder(req.runStart(), req.runEnd(), "The run end");
        run.setProdDate(req.prodDate());

        Shift shift = shiftService.find(req.shiftId());
        requireActiveIfChanged(shift.isActive(), run.getShift(), shift, "Shift " + shift.getCode());
        run.setShift(shift);
        run.setSprayDryer(requireMachine(machineRepository, req.sprayDryerId(), run.getPlant(), MachineType.SPRAY_DRYER,
                run.getSprayDryer()));
        run.setSilo(requireMachine(machineRepository, req.siloId(), run.getPlant(), MachineType.SILO, run.getSilo()));

        run.setRunStart(req.runStart());
        run.setRunEnd(req.runEnd());
        run.setPowderTon(req.powderTon());
        run.setPowderMoisture(req.powderMoisture());
        run.setGasScm(req.gasScm());
        run.setInletTemp(req.inletTemp());
        run.setOutletTemp(req.outletTemp());
        run.setRemarks(Text.trimToNull(req.remarks()));

        Map<Long, BodyBatch> batches = batchRepository.findAllById(
                        req.batches().stream().map(SprayRunRequest.BatchUse::bodyBatchId).collect(Collectors.toSet()))
                .stream().collect(Collectors.toMap(BaseEntity::getId, Function.identity()));

        run.getBatches().clear();
        Set<Long> seen = new HashSet<>();
        for (SprayRunRequest.BatchUse use : req.batches()) {
            BodyBatch batch = batches.get(use.bodyBatchId());
            if (batch == null || !batch.getPlant().getId().equals(run.getPlant().getId())) {
                throw new NotFoundException("Body batch", use.bodyBatchId());
            }
            if (!seen.add(batch.getId())) {
                throw new BusinessException(batch.getBatchNo() + " is selected twice");
            }
            requireWaiting(batch);

            SprayRunBatch link = new SprayRunBatch();
            link.setRun(run);
            link.setBodyBatch(batch);
            link.setFinished(use.finished());
            run.getBatches().add(link);
        }
    }
}
