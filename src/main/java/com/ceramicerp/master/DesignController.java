package com.ceramicerp.master;

import com.ceramicerp.common.PageResponse;
import com.ceramicerp.master.dto.DesignRequest;
import com.ceramicerp.master.dto.DesignResponse;
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

/**
 * HTTP endpoints for designs: /api/masters/designs
 * The list is paged and filterable, e.g. GET /api/masters/designs?search=marble&sizeId=1&page=0&size=20
 */
@Tag(name = "Masters - Designs")
@RestController
@RequestMapping("/api/masters/designs")
@RequiredArgsConstructor
public class DesignController {

    private final DesignService designService;

    @GetMapping
    @PreAuthorize("hasAuthority('MASTER_VIEW')")
    public PageResponse<DesignResponse> search(@RequestParam(required = false) String search,
                                               @RequestParam(required = false) Long categoryId,
                                               @RequestParam(required = false) Long sizeId,
                                               @RequestParam(required = false) Long finishId,
                                               @RequestParam(required = false) Boolean active,
                                               @RequestParam(defaultValue = "0") int page,
                                               @RequestParam(defaultValue = "20") int size) {
        return designService.search(search, categoryId, sizeId, finishId, active, page, size);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('MASTER_VIEW')")
    public DesignResponse get(@PathVariable Long id) {
        return designService.get(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('MASTER_MANAGE')")
    public DesignResponse create(@Valid @RequestBody DesignRequest req) {
        return designService.create(req);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('MASTER_MANAGE')")
    public DesignResponse update(@PathVariable Long id, @Valid @RequestBody DesignRequest req) {
        return designService.update(id, req);
    }
}
