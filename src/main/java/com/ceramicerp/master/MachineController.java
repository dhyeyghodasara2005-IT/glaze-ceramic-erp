package com.ceramicerp.master;

import com.ceramicerp.master.dto.MachineRequest;
import com.ceramicerp.master.dto.MachineResponse;
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

/** /api/masters/machines?plantId=1 - machines of the plants you can access. */
@Tag(name = "Masters - Machines")
@RestController
@RequestMapping("/api/masters/machines")
@RequiredArgsConstructor
public class MachineController {

    private final MachineService machineService;

    @GetMapping
    @PreAuthorize("hasAuthority('MASTER_VIEW')")
    public List<MachineResponse> list(@RequestParam(required = false) Long plantId) {
        return machineService.list(plantId);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('MASTER_VIEW')")
    public MachineResponse get(@PathVariable Long id) {
        return machineService.get(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('MASTER_MANAGE')")
    public MachineResponse create(@Valid @RequestBody MachineRequest req) {
        return machineService.create(req);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('MASTER_MANAGE')")
    public MachineResponse update(@PathVariable Long id, @Valid @RequestBody MachineRequest req) {
        return machineService.update(id, req);
    }
}
