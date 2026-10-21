package com.ceramicerp.master;

import com.ceramicerp.master.dto.MaterialGroupRequest;
import com.ceramicerp.master.dto.MaterialGroupResponse;
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
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** /api/masters/material-groups */
@Tag(name = "Masters - Material groups")
@RestController
@RequestMapping("/api/masters/material-groups")
@RequiredArgsConstructor
public class MaterialGroupController {

    private final MaterialGroupService groupService;

    @GetMapping
    @PreAuthorize("hasAuthority('MASTER_VIEW')")
    public List<MaterialGroupResponse> list() {
        return groupService.list();
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('MASTER_VIEW')")
    public MaterialGroupResponse get(@PathVariable Long id) {
        return groupService.get(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('MASTER_MANAGE')")
    public MaterialGroupResponse create(@Valid @RequestBody MaterialGroupRequest req) {
        return groupService.create(req);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('MASTER_MANAGE')")
    public MaterialGroupResponse update(@PathVariable Long id, @Valid @RequestBody MaterialGroupRequest req) {
        return groupService.update(id, req);
    }
}
