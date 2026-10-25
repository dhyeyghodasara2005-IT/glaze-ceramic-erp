package com.ceramicerp.maintenance;

import com.ceramicerp.common.Text;
import com.ceramicerp.common.exception.BusinessException;
import com.ceramicerp.common.exception.NotFoundException;
import com.ceramicerp.maintenance.dto.MachineRef;
import com.ceramicerp.maintenance.dto.PmDoneRequest;
import com.ceramicerp.maintenance.dto.PmJobResponse;
import com.ceramicerp.maintenance.dto.PmOverview;
import com.ceramicerp.maintenance.dto.PmTaskRequest;
import com.ceramicerp.maintenance.dto.PmTaskResponse;
import com.ceramicerp.maintenance.dto.PmTaskSummary;
import com.ceramicerp.maintenance.dto.SpareUsed;
import com.ceramicerp.master.Machine;
import com.ceramicerp.master.MachineRepository;
import com.ceramicerp.numbering.DocumentNumberService;
import com.ceramicerp.organization.Plant;
import com.ceramicerp.organization.PlantService;
import com.ceramicerp.security.CurrentUser;
import com.ceramicerp.security.PlantAccess;
import com.ceramicerp.store.StoreDocument;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Preventive maintenance: routine jobs per machine, each due every so many days. Recording one as done moves its
 * next due date to (done date + interval) and takes its spares out of the store; cancelling the newest record puts
 * the job back exactly as it was.
 */
@Service
@RequiredArgsConstructor
public class PmTaskService {

    static final String ORIGIN = "PREVENTIVE";
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH);

    private final PmTaskRepository taskRepository;
    private final PmJobRepository jobRepository;
    private final MachineRepository machineRepository;
    private final SpareIssues spareIssues;
    private final DocumentNumberService numberService;
    private final PlantService plantService;

    /**
     * A plant's jobs, soonest due first.
     *
     * @param due OVERDUE, TODAY or WEEK = everything due by then (so WEEK includes the overdue ones); null = all
     */
    @Transactional(readOnly = true)
    public List<PmTaskSummary> search(Long plantId, DueStatus due, Long machineId, String search, boolean includeInactive) {
        PlantAccess.require(plantId);
        LocalDate today = today();
        LocalDate dueBy = due == null || due == DueStatus.LATER ? null
                : due == DueStatus.OVERDUE ? today.minusDays(1)
                : due == DueStatus.TODAY ? today : today.plusDays(DueStatus.WEEK_DAYS);
        String pattern = Text.trimToNull(search) == null ? null : "%" + search.trim().toLowerCase(Locale.ROOT) + "%";
        return taskRepository.search(plantId, includeInactive ? null : Boolean.TRUE, machineId, dueBy, pattern).stream()
                .map(t -> summary(t, today))
                .toList();
    }

    @Transactional(readOnly = true)
    public PmTaskResponse get(Long id) {
        return toResponse(find(id));
    }

    /** The same job for one or more machines of the plant (e.g. both presses). */
    @Transactional
    public List<PmTaskSummary> create(PmTaskRequest req) {
        Plant plant = plantService.find(req.plantId());
        PlantAccess.require(plant.getId());
        List<PmTaskSummary> created = new ArrayList<>();
        for (Long machineId : req.machineIds().stream().distinct().toList()) {
            PmTask t = new PmTask();
            t.setPlant(plant);
            t.setMachine(machine(machineId, plant, null));
            apply(t, req);
            created.add(summary(taskRepository.save(t), today()));
        }
        return created;
    }

    /** Changes one job (its machine, what it is, how often, when due next; or switches it off). */
    @Transactional
    public PmTaskResponse update(Long id, PmTaskRequest req) {
        PmTask t = find(id);
        if (!t.getPlant().getId().equals(req.plantId())) {
            throw new BusinessException("A job can't be moved to another plant");
        }
        if (req.machineIds().size() != 1) {
            throw new BusinessException("A job belongs to one machine: set it up again for the others");
        }
        t.setMachine(machine(req.machineIds().get(0), t.getPlant(), t.getMachine()));
        apply(t, req);
        if (req.active() != null) {
            t.setActive(req.active());
        }
        return toResponse(taskRepository.saveAndFlush(t));
    }

    /** The job was done: its spares leave the store and it is due again (done date + interval). */
    @Transactional
    public PmTaskResponse done(Long id, PmDoneRequest req) {
        PmTask t = find(id);
        if (!t.isActive()) {
            throw new BusinessException("This job is switched off");
        }
        if (req.doneOn().isAfter(today())) {
            throw new BusinessException("It can't be done in the future");
        }
        if (t.getLastDone() != null && req.doneOn().isBefore(t.getLastDone())) {
            throw new BusinessException("It was last done on " + t.getLastDone().format(DATE) + ": enter a later date");
        }
        PmJob job = new PmJob();
        job.setPlant(t.getPlant());
        job.setTask(t);
        job.setJobNo(numberService.next(t.getPlant(), MaintenanceDocKind.PREVENTIVE, req.doneOn()));
        job.setDoneOn(req.doneOn());
        job.setWasDue(t.getNextDue());
        job.setPreviousLastDone(t.getLastDone());
        job.setDoneBy(Text.trimToNull(req.doneBy()));
        job.setMinutes(req.minutes());
        job.setStopMinutes(t.isNeedsStop() ? req.stopMinutes() : null);
        job.setFindings(Text.trimToNull(req.findings()));

        StoreDocument issue = spareIssues.issue(ORIGIN, t.getPlant().getId(), t.getMachine().getId(), req.doneOn(),
                "Preventive job " + job.getJobNo(), req.spares());
        job.setIssueDocument(issue);
        int lineNo = 0;
        for (SpareIssues.Taken taken : SpareIssues.taken(issue)) {
            PmJobSpare s = new PmJobSpare();
            s.setJob(job);
            s.setLineNo(++lineNo);
            s.setMaterial(taken.material());
            s.setLocation(taken.location());
            s.setQuantity(taken.quantity());
            job.getSpares().add(s);
        }
        jobRepository.save(job);

        t.setLastDone(req.doneOn());
        t.setNextDue(req.doneOn().plusDays(t.getEveryDays()));
        return toResponse(taskRepository.saveAndFlush(t));
    }

    /**
     * A record entered by mistake (MAINTENANCE_CANCEL, checked by the controller). Only the newest one of its job:
     * the job's dates go back to what they were before it, and its spares go back to the store.
     */
    @Transactional
    public PmTaskResponse cancelJob(Long jobId, String reason) {
        PmJob job = jobRepository.findWithDetailsById(jobId).orElseThrow(() -> new NotFoundException("Preventive job", jobId));
        PlantAccess.require(job.getPlant().getId());
        if (job.getStatus() != PmJobStatus.DONE) {
            throw new BusinessException(job.getJobNo() + " is already cancelled");
        }
        PmTask t = job.getTask();
        PmJob newest = jobRepository.findByTaskIdOrderByDoneOnDescIdDesc(t.getId()).stream()
                .filter(j -> j.getStatus() == PmJobStatus.DONE)
                .findFirst().orElseThrow();
        if (!newest.getId().equals(job.getId())) {
            throw new BusinessException("Only the newest record of a job can be cancelled (" + newest.getJobNo() + ")");
        }
        spareIssues.undo(job.getIssueDocument(), "Preventive job " + job.getJobNo() + " cancelled: " + reason.trim());
        job.setStatus(PmJobStatus.CANCELLED);
        job.setCancelledAt(Instant.now());
        job.setCancelledBy(CurrentUser.get().getUsername());
        job.setCancelReason(reason.trim());
        t.setNextDue(job.getWasDue());
        t.setLastDone(job.getPreviousLastDone());
        return toResponse(t);
    }

    /** Overdue, due today and this week, and how many jobs of the last 30 days were done on time. */
    @Transactional(readOnly = true)
    public PmOverview overview(Long plantId) {
        PlantAccess.require(plantId);
        LocalDate today = today();
        List<PmTaskSummary> active = search(plantId, null, null, null, false);
        List<PmTaskSummary> overdue = active.stream().filter(t -> t.due() == DueStatus.OVERDUE).toList();
        List<PmJob> done = jobRepository.doneBetween(plantId, today.minusDays(29), today);
        int onTime = (int) done.stream().filter(j -> j.daysLate() == 0).count();
        BigDecimal pct = done.isEmpty() ? null
                : BigDecimal.valueOf(onTime * 100L).divide(BigDecimal.valueOf(done.size()), 1, RoundingMode.HALF_UP);
        return new PmOverview(active.size(), overdue.size(),
                (int) active.stream().filter(t -> t.due() == DueStatus.TODAY).count(),
                (int) active.stream().filter(t -> t.due() == DueStatus.WEEK).count(),
                done.size(), onTime, pct, overdue.isEmpty() ? null : overdue.get(0));
    }

    // ------------------------------------------------------------------ helpers

    private void apply(PmTask t, PmTaskRequest req) {
        t.setTitle(req.title().trim());
        t.setKind(req.kind());
        t.setEveryDays(req.everyDays());
        t.setInstructions(Text.trimToNull(req.instructions()));
        t.setNeedsStop(Boolean.TRUE.equals(req.needsStop()));
        t.setNextDue(req.nextDue());
    }

    /** A machine of this plant, active unless the job already had it. */
    private Machine machine(Long id, Plant plant, Machine current) {
        Machine m = machineRepository.findWithPlantById(id).orElseThrow(() -> new NotFoundException("Machine", id));
        if (!m.getPlant().getId().equals(plant.getId())) {
            throw new BusinessException("Machine " + m.getCode() + " is not in plant " + plant.getCode());
        }
        if (!m.isActive() && (current == null || !current.getId().equals(m.getId()))) {
            throw new BusinessException("Machine " + m.getCode() + " is inactive");
        }
        return m;
    }

    static PmTaskSummary summary(PmTask t, LocalDate today) {
        DueStatus due = DueStatus.of(t.getNextDue(), today);
        return new PmTaskSummary(t.getId(), MachineRef.of(t.getMachine()), t.getTitle(), t.getKind(), t.getEveryDays(),
                t.isNeedsStop(), t.getNextDue(), t.getLastDone(), t.isActive(), due, overdueDays(t, today));
    }

    private static int overdueDays(PmTask t, LocalDate today) {
        return (int) Math.max(0, ChronoUnit.DAYS.between(t.getNextDue(), today));
    }

    private PmTaskResponse toResponse(PmTask t) {
        LocalDate today = today();
        List<PmJob> jobs = jobRepository.findByTaskIdOrderByDoneOnDescIdDesc(t.getId());
        Long newestDone = jobs.stream().filter(j -> j.getStatus() == PmJobStatus.DONE).map(PmJob::getId).findFirst().orElse(null);
        List<PmJobResponse> history = jobs.stream().limit(30).map(j -> {
            StoreDocument issue = j.getIssueDocument();
            List<SpareUsed> spares = SpareIssues.view(j.getSpares().stream()
                    .map(s -> new SpareIssues.Taken(s.getMaterial(), s.getLocation(), s.getQuantity())).toList(), issue);
            return new PmJobResponse(j.getId(), j.getJobNo(), j.getDoneOn(), j.getWasDue(), j.daysLate(), j.getDoneBy(),
                    j.getMinutes(), j.getStopMinutes(), j.getFindings(), j.getStatus(), spares, SpareIssues.cost(spares),
                    issue == null ? null : issue.getId(), issue == null ? null : issue.getDocNo(), j.getCreatedBy(),
                    j.getCreatedAt(), j.getCancelledAt(), j.getCancelledBy(), j.getCancelReason(),
                    j.getId().equals(newestDone));
        }).toList();
        return new PmTaskResponse(t.getId(), t.getPlant().getId(), t.getPlant().getCode(), MachineRef.of(t.getMachine()),
                t.getTitle(), t.getKind(), t.getEveryDays(), t.getInstructions(), t.isNeedsStop(), t.getNextDue(),
                t.getLastDone(), t.isActive(), DueStatus.of(t.getNextDue(), today), overdueDays(t, today), history,
                t.getCreatedBy(), t.getVersion());
    }

    private PmTask find(Long id) {
        PmTask t = taskRepository.findWithDetailsById(id).orElseThrow(() -> new NotFoundException("Preventive job", id));
        PlantAccess.require(t.getPlant().getId());
        return t;
    }

    private static LocalDate today() {
        return LocalDate.now(BreakdownService.PLANT_ZONE);
    }
}
