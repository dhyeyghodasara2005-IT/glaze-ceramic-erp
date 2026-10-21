package com.ceramicerp.master;

import com.ceramicerp.master.dto.UnitOfMeasureRequest;
import com.ceramicerp.master.dto.UnitOfMeasureResponse;
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

/** /api/masters/units */
@Tag(name = "Masters - Units")
@RestController
@RequestMapping("/api/masters/units")
@RequiredArgsConstructor
public class UnitOfMeasureController {

    private final UnitOfMeasureService unitService;

    @GetMapping
    @PreAuthorize("hasAuthority('MASTER_VIEW')")
    public List<UnitOfMeasureResponse> list() {
        return unitService.list();
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('MASTER_VIEW')")
    public UnitOfMeasureResponse get(@PathVariable Long id) {
        return unitService.get(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('MASTER_MANAGE')")
    public UnitOfMeasureResponse create(@Valid @RequestBody UnitOfMeasureRequest req) {
        return unitService.create(req);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('MASTER_MANAGE')")
    public UnitOfMeasureResponse update(@PathVariable Long id, @Valid @RequestBody UnitOfMeasureRequest req) {
        return unitService.update(id, req);
    }
}
