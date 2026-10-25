package com.ceramicerp.maintenance;

import com.ceramicerp.maintenance.dto.PmDoneRequest;
import com.ceramicerp.maintenance.dto.PmOverview;
import com.ceramicerp.maintenance.dto.PmTaskRequest;
import com.ceramicerp.maintenance.dto.PmTaskResponse;
import com.ceramicerp.maintenance.dto.PmTaskSummary;
import com.ceramicerp.store.dto.CancelRequest;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
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

import java.util.List;

/**
 * /api/maintenance/pm-... - preventive jobs: everyone in maintenance sees the due list (MAINTENANCE_VIEW), the
 * maintenance team sets jobs up and records them as done (MAINTENANCE_ENTRY), MAINTENANCE_CANCEL undoes a record.
 */
@Tag(name = "Maintenance - preventive jobs")
@RestController
@RequestMapping("/api/maintenance")
@RequiredArgsConstructor
public class PmTaskController {

    private static final String SEES = "hasAnyAuthority('MAINTENANCE_VIEW', 'MAINTENANCE_ENTRY')";

    private final PmTaskService taskService;

    @GetMapping("/pm-tasks")
    @PreAuthorize(SEES)
    public List<PmTaskSummary> search(@RequestParam Long plantId,
                                      @RequestParam(required = false) DueStatus due,
                                      @RequestParam(required = false) Long machineId,
                                      @RequestParam(required = false) String search,
                                      @RequestParam(defaultValue = "false") boolean includeInactive) {
        return taskService.search(plantId, due, machineId, search, includeInactive);
    }

    @GetMapping("/pm-tasks/{id}")
    @PreAuthorize(SEES)
    public PmTaskResponse get(@PathVariable Long id) {
        return taskService.get(id);
    }

    @PostMapping("/pm-tasks")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('MAINTENANCE_ENTRY')")
    public List<PmTaskSummary> create(@Valid @RequestBody PmTaskRequest req) {
        return taskService.create(req);
    }

    @PutMapping("/pm-tasks/{id}")
    @PreAuthorize("hasAuthority('MAINTENANCE_ENTRY')")
    public PmTaskResponse update(@PathVariable Long id, @Valid @RequestBody PmTaskRequest req) {
        return taskService.update(id, req);
    }

    @PostMapping("/pm-tasks/{id}/done")
    @PreAuthorize("hasAuthority('MAINTENANCE_ENTRY')")
    public PmTaskResponse done(@PathVariable Long id, @Valid @RequestBody PmDoneRequest req) {
        return taskService.done(id, req);
    }

    @PostMapping("/pm-jobs/{id}/cancel")
    @PreAuthorize("hasAuthority('MAINTENANCE_CANCEL')")
    public PmTaskResponse cancelJob(@PathVariable Long id, @Valid @RequestBody CancelRequest req) {
        return taskService.cancelJob(id, req.reason());
    }

    @GetMapping("/pm-overview")
    @PreAuthorize(SEES)
    public PmOverview overview(@RequestParam Long plantId) {
        return taskService.overview(plantId);
    }
}
