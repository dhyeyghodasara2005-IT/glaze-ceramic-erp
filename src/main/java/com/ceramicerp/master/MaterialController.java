package com.ceramicerp.master;

import com.ceramicerp.common.PageResponse;
import com.ceramicerp.master.dto.MaterialRequest;
import com.ceramicerp.master.dto.MaterialResponse;
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

/** /api/masters/materials - paged + filterable, like designs. */
@Tag(name = "Masters - Materials")
@RestController
@RequestMapping("/api/masters/materials")
@RequiredArgsConstructor
public class MaterialController {

    private final MaterialService materialService;

    @GetMapping
    @PreAuthorize("hasAuthority('MASTER_VIEW')")
    public PageResponse<MaterialResponse> search(@RequestParam(required = false) String search,
                                                 @RequestParam(required = false) Long groupId,
                                                 @RequestParam(required = false) Boolean active,
                                                 @RequestParam(defaultValue = "0") int page,
                                                 @RequestParam(defaultValue = "20") int size) {
        return materialService.search(search, groupId, active, page, size);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('MASTER_VIEW')")
    public MaterialResponse get(@PathVariable Long id) {
        return materialService.get(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('MASTER_MANAGE')")
    public MaterialResponse create(@Valid @RequestBody MaterialRequest req) {
        return materialService.create(req);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('MASTER_MANAGE')")
    public MaterialResponse update(@PathVariable Long id, @Valid @RequestBody MaterialRequest req) {
        return materialService.update(id, req);
    }
}
