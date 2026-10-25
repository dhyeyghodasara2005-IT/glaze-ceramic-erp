package com.ceramicerp.maintenance;

import com.ceramicerp.common.PageResponse;
import com.ceramicerp.common.Text;
import com.ceramicerp.common.exception.BusinessException;
import com.ceramicerp.common.exception.NotFoundException;
import com.ceramicerp.maintenance.dto.BreakdownCloseRequest;
import com.ceramicerp.maintenance.dto.BreakdownReportRequest;
import com.ceramicerp.maintenance.dto.BreakdownResponse;
import com.ceramicerp.maintenance.dto.BreakdownSummary;
import com.ceramicerp.maintenance.dto.MachineRef;
import com.ceramicerp.maintenance.dto.SpareUsed;
import com.ceramicerp.master.Machine;
import com.ceramicerp.master.MachineRepository;
import com.ceramicerp.numbering.DocumentNumberService;
import com.ceramicerp.organization.Plant;
import com.ceramicerp.organization.PlantService;
import com.ceramicerp.security.CurrentUser;
import com.ceramicerp.security.PlantAccess;
import com.ceramicerp.store.StoreDocument;
import com.ceramicerp.user.Permission;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * The breakdown log. A supervisor reports a machine as down (OPEN); maintenance closes it when it runs again, and
 * the spares it used leave the store through a store issue made here (origin BREAKDOWN, so the store screens can't
 * edit or cancel it on their own). Cancelling a closed breakdown cancels that issue too: the spares go back.
 */
@Service
@RequiredArgsConstructor
public class BreakdownService {

    /** The plants are in India: "now" is Indian time, whatever zone the server runs in. */
    static final ZoneId PLANT_ZONE = ZoneId.of("Asia/Kolkata");
    static final String ORIGIN = "BREAKDOWN";
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("d MMM, HH:mm", Locale.ENGLISH);

    private final BreakdownRepository breakdownRepository;
    private final MachineRepository machineRepository;
    private final SpareIssues spareIssues;
    private final DocumentNumberService numberService;
    private final PlantService plantService;

    @Transactional(readOnly = true)
    public PageResponse<BreakdownSummary> search(Long plantId, BreakdownStatus status, Long machineId, LocalDate from,
                                                 LocalDate to, String search, int page, int size) {
        PlantAccess.require(plantId);
        String pattern = Text.trimToNull(search) == null ? null : "%" + search.trim().toLowerCase(Locale.ROOT) + "%";
        var pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100),
                Sort.by(Sort.Order.desc("stoppedAt"), Sort.Order.desc("id")));
        LocalDateTime now = now();
        return PageResponse.of(breakdownRepository.search(plantId, status, machineId,
                        from == null ? null : from.atStartOfDay(), to == null ? null : to.plusDays(1).atStartOfDay(),
                        pattern, pageable),
                b -> new BreakdownSummary(b.getId(), b.getBreakdownNo(), MachineRef.of(b.getMachine()), b.getStoppedAt(),
                        b.getSymptom(), b.getStatus(), b.getRestartedAt(), minutes(b, now), b.getCategory(),
                        b.getFailedPart(), b.getCreatedBy()));
    }

    @Transactional(readOnly = true)
    public BreakdownResponse get(Long id) {
        return toResponse(find(id));
    }

    /** A machine has stopped. Refused if it is already reported down. */
    @Transactional
    public BreakdownResponse report(BreakdownReportRequest req) {
        Plant plant = plantService.find(req.plantId());
        PlantAccess.require(plant.getId());
        Breakdown b = new Breakdown();
        b.setPlant(plant);
        apply(b, req);
        b.setBreakdownNo(numberService.next(plant, MaintenanceDocKind.BREAKDOWN, req.stoppedAt().toLocalDate()));
        return toResponse(breakdownRepository.saveAndFlush(b));
    }

    /** Corrects an open report (wrong machine, time or words). */
    @Transactional
    public BreakdownResponse update(Long id, BreakdownReportRequest req) {
        Breakdown b = find(id);
        requireStatus(b, BreakdownStatus.OPEN, "changed");
        if (!b.getPlant().getId().equals(req.plantId())) {
            throw new BusinessException("A breakdown can't be moved to another plant");
        }
        apply(b, req);
        return toResponse(breakdownRepository.saveAndFlush(b));
    }

    /** The machine runs again: the cause and the fix are written down, and the spares leave the store. */
    @Transactional
    public BreakdownResponse close(Long id, BreakdownCloseRequest req) {
        Breakdown b = find(id);
        requireStatus(b, BreakdownStatus.OPEN, "closed");
        LocalDateTime restarted = req.restartedAt();
        if (restarted.isAfter(now())) {
            throw new BusinessException("It can't run again in the future");
        }
        if (restarted.isBefore(b.getStoppedAt())) {
            throw new BusinessException("It can't run again before it stopped (" + b.getStoppedAt().format(TIME) + ")");
        }
        b.setRestartedAt(restarted);
        b.setDowntimeMinutes((int) Duration.between(b.getStoppedAt(), restarted).toMinutes());
        b.setCategory(req.category());
        b.setFailedPart(Text.trimToNull(req.failedPart()));
        b.setCause(Text.trimToNull(req.cause()));
        b.setActionTaken(req.actionTaken().trim());
        b.setFixedBy(Text.trimToNull(req.fixedBy()));

        // the store checks the materials, the godowns and that there is enough of each
        StoreDocument issue = spareIssues.issue(ORIGIN, b.getPlant().getId(), b.getMachine().getId(),
                restarted.toLocalDate(), "Breakdown " + b.getBreakdownNo(), req.spares());
        b.setIssueDocument(issue);
        int lineNo = 0;
        for (SpareIssues.Taken taken : SpareIssues.taken(issue)) {
            BreakdownSpare s = new BreakdownSpare();
            s.setBreakdown(b);
            s.setLineNo(++lineNo);
            s.setMaterial(taken.material());
            s.setLocation(taken.location());
            s.setQuantity(taken.quantity());
            b.getSpares().add(s);
        }
        b.setStatus(BreakdownStatus.CLOSED);
        b.setClosedAt(Instant.now());
        b.setClosedBy(CurrentUser.get().getUsername());
        return toResponse(breakdownRepository.saveAndFlush(b));
    }

    /**
     * A report made by mistake (open: reporters and maintenance), or a closed breakdown entered wrongly
     * (MAINTENANCE_CANCEL only: its spares go back to the store).
     */
    @Transactional
    public BreakdownResponse cancel(Long id, String reason) {
        Breakdown b = find(id);
        Set<Permission> may = CurrentUser.get().getPermissions();
        switch (b.getStatus()) {
            case OPEN -> {
                if (!may.contains(Permission.BREAKDOWN_REPORT) && !may.contains(Permission.MAINTENANCE_ENTRY)) {
                    throw new AccessDeniedException("Reporting breakdowns is not allowed");
                }
            }
            case CLOSED -> {
                if (!may.contains(Permission.MAINTENANCE_CANCEL)) {
                    throw new AccessDeniedException("Cancelling closed breakdowns is not allowed");
                }
                spareIssues.undo(b.getIssueDocument(), "Breakdown " + b.getBreakdownNo() + " cancelled: " + reason.trim());
            }
            case CANCELLED -> throw new BusinessException(b.getBreakdownNo() + " is already cancelled");
        }
        b.setStatus(BreakdownStatus.CANCELLED);
        b.setCancelledAt(Instant.now());
        b.setCancelledBy(CurrentUser.get().getUsername());
        b.setCancelReason(reason.trim());
        return toResponse(b);
    }

    // ------------------------------------------------------------------ helpers

    private void apply(Breakdown b, BreakdownReportRequest req) {
        if (req.stoppedAt().isAfter(now())) {
            throw new BusinessException("It can't stop in the future");
        }
        Machine machine = machineRepository.findWithPlantById(req.machineId())
                .orElseThrow(() -> new NotFoundException("Machine", req.machineId()));
        if (!machine.getPlant().getId().equals(b.getPlant().getId())) {
            throw new BusinessException("Machine " + machine.getCode() + " is not in plant " + b.getPlant().getCode());
        }
        boolean unchanged = b.getMachine() != null && b.getMachine().getId().equals(machine.getId());
        if (!machine.isActive() && !unchanged) {
            throw new BusinessException("Machine " + machine.getCode() + " is inactive");
        }
        // one open breakdown per machine (the database refuses a second one too, even at the same second)
        breakdownRepository.findByMachineIdAndStatus(machine.getId(), BreakdownStatus.OPEN)
                .filter(open -> !open.getId().equals(b.getId()))
                .ifPresent(open -> {
                    throw new BusinessException(machine.getName() + " is already reported down since "
                            + open.getStoppedAt().format(TIME) + " (" + open.getBreakdownNo() + ")");
                });
        b.setMachine(machine);
        b.setStoppedAt(req.stoppedAt().withNano(0));
        b.setSymptom(req.symptom().trim());
    }

    private BreakdownResponse toResponse(Breakdown b) {
        StoreDocument issue = b.getIssueDocument();
        List<SpareUsed> spares = SpareIssues.view(b.getSpares().stream()
                .map(s -> new SpareIssues.Taken(s.getMaterial(), s.getLocation(), s.getQuantity())).toList(), issue);
        return new BreakdownResponse(b.getId(), b.getPlant().getId(), b.getPlant().getCode(), b.getBreakdownNo(),
                MachineRef.of(b.getMachine()), b.getStoppedAt(), b.getSymptom(), b.getStatus(), b.getRestartedAt(),
                minutes(b, now()), b.getCategory(), b.getFailedPart(), b.getCause(), b.getActionTaken(), b.getFixedBy(),
                spares, issue == null ? null : issue.getId(), issue == null ? null : issue.getDocNo(),
                SpareIssues.cost(spares), b.getCreatedBy(), b.getCreatedAt(), b.getClosedAt(), b.getClosedBy(),
                b.getCancelledAt(), b.getCancelledBy(), b.getCancelReason(), b.getVersion());
    }

    /** Minutes down: as recorded once closed, so far while open. */
    static int minutes(Breakdown b, LocalDateTime now) {
        if (b.getDowntimeMinutes() != null) {
            return b.getDowntimeMinutes();
        }
        return b.getStatus() == BreakdownStatus.OPEN
                ? (int) Math.max(0, Duration.between(b.getStoppedAt(), now).toMinutes()) : 0;
    }

    static LocalDateTime now() {
        return LocalDateTime.now(PLANT_ZONE);
    }

    private Breakdown find(Long id) {
        Breakdown b = breakdownRepository.findWithDetailsById(id).orElseThrow(() -> new NotFoundException("Breakdown", id));
        PlantAccess.require(b.getPlant().getId());
        return b;
    }

    private static void requireStatus(Breakdown b, BreakdownStatus needed, String action) {
        if (b.getStatus() != needed) {
            throw new BusinessException("Only an " + needed.name().toLowerCase(Locale.ROOT) + " breakdown can be " + action
                    + ". " + b.getBreakdownNo() + " is " + b.getStatus().name().toLowerCase(Locale.ROOT));
        }
    }
}
