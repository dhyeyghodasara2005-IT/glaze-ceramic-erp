package com.ceramicerp.master;

import com.ceramicerp.master.dto.FinishRequest;
import com.ceramicerp.master.dto.FinishResponse;
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

/** HTTP endpoints for finishes: /api/masters/finishes */
@Tag(name = "Masters - Finishes")
@RestController
@RequestMapping("/api/masters/finishes")
@RequiredArgsConstructor
public class FinishController {

    private final FinishService finishService;

    @GetMapping
    @PreAuthorize("hasAuthority('MASTER_VIEW')")
    public List<FinishResponse> list() {
        return finishService.list();
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('MASTER_VIEW')")
    public FinishResponse get(@PathVariable Long id) {
        return finishService.get(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('MASTER_MANAGE')")
    public FinishResponse create(@Valid @RequestBody FinishRequest req) {
        return finishService.create(req);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('MASTER_MANAGE')")
    public FinishResponse update(@PathVariable Long id, @Valid @RequestBody FinishRequest req) {
        return finishService.update(id, req);
    }
}
