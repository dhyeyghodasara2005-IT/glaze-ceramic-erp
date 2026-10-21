package com.ceramicerp.master;

import com.ceramicerp.master.dto.TileSizeRequest;
import com.ceramicerp.master.dto.TileSizeResponse;
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

/** HTTP endpoints for tile sizes. Each method checks a permission, then calls the service. */
@Tag(name = "Masters - Sizes")
@RestController
@RequestMapping("/api/masters/sizes")
@RequiredArgsConstructor
public class TileSizeController {

    private final TileSizeService tileSizeService;

    @GetMapping
    @PreAuthorize("hasAuthority('MASTER_VIEW')")
    public List<TileSizeResponse> list() {
        return tileSizeService.list();
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('MASTER_VIEW')")
    public TileSizeResponse get(@PathVariable Long id) {
        return tileSizeService.get(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('MASTER_MANAGE')")
    public TileSizeResponse create(@Valid @RequestBody TileSizeRequest req) {
        return tileSizeService.create(req);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('MASTER_MANAGE')")
    public TileSizeResponse update(@PathVariable Long id, @Valid @RequestBody TileSizeRequest req) {
        return tileSizeService.update(id, req);
    }
}