package com.ceramicerp.labour;

import com.ceramicerp.labour.dto.AttendanceRequest;
import com.ceramicerp.labour.dto.AttendanceSheet;
import com.ceramicerp.labour.dto.LabourMonth;
import com.ceramicerp.labour.dto.WorkerRequest;
import com.ceramicerp.labour.dto.WorkerResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
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
import java.time.YearMonth;
import java.util.List;

/**
 * /api/labour - the labour office keeps the workers and their rates (LABOUR_MANAGE) and sees the month
 * (LABOUR_VIEW); supervisors mark their workers' days (ATTENDANCE_ENTRY), without seeing any rates.
 */
@Tag(name = "Labour")
@RestController
@RequestMapping("/api/labour")
@RequiredArgsConstructor
public class LabourController {

    private final WorkerService workerService;
    private final AttendanceService attendanceService;

    @GetMapping("/workers")
    @PreAuthorize("hasAnyAuthority('LABOUR_VIEW', 'LABOUR_MANAGE')")
    public List<WorkerResponse> workers(@RequestParam Long plantId,
                                        @RequestParam(required = false) Boolean active,
                                        @RequestParam(required = false) Long contractorId,
                                        @RequestParam(required = false) Department department,
                                        @RequestParam(required = false) String search) {
        return workerService.list(plantId, active, contractorId, department, search);
    }

    @GetMapping("/workers/{id}")
    @PreAuthorize("hasAnyAuthority('LABOUR_VIEW', 'LABOUR_MANAGE')")
    public WorkerResponse worker(@PathVariable Long id) {
        return workerService.get(id);
    }

    @PostMapping("/workers")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('LABOUR_MANAGE')")
    public WorkerResponse create(@Valid @RequestBody WorkerRequest req) {
        return workerService.create(req);
    }

    @PutMapping("/workers/{id}")
    @PreAuthorize("hasAuthority('LABOUR_MANAGE')")
    public WorkerResponse update(@PathVariable Long id, @Valid @RequestBody WorkerRequest req) {
        return workerService.update(id, req);
    }

    /** Labour contractors for the worker form and the filters. */
    @GetMapping("/contractors")
    @PreAuthorize("hasAnyAuthority('LABOUR_VIEW', 'LABOUR_MANAGE')")
    public List<WorkerResponse.ContractorRef> contractors() {
        return workerService.contractors();
    }

    /** A day's sheet; without a date, today's production date. */
    @GetMapping("/attendance")
    @PreAuthorize("hasAnyAuthority('ATTENDANCE_ENTRY', 'LABOUR_VIEW')")
    public AttendanceSheet sheet(@RequestParam Long plantId,
                                 @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
                                 @RequestParam(required = false) Department department) {
        return attendanceService.sheet(plantId, date, department);
    }

    @PostMapping("/attendance")
    @PreAuthorize("hasAuthority('ATTENDANCE_ENTRY')")
    public AttendanceSheet save(@Valid @RequestBody AttendanceRequest req) {
        return attendanceService.save(req);
    }

    /** The month per contractor; month as 2026-10 (default: this month). */
    @GetMapping("/month")
    @PreAuthorize("hasAuthority('LABOUR_VIEW')")
    public LabourMonth month(@RequestParam Long plantId,
                             @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM") YearMonth month) {
        return attendanceService.month(plantId, month);
    }
}
