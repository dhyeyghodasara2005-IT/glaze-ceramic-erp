package com.ceramicerp.store;

import com.ceramicerp.store.dto.StoreLocationRequest;
import com.ceramicerp.store.dto.StoreLocationResponse;
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

/** /api/store/locations?plantId=1 - godowns of one plant. */
@Tag(name = "Store - Godowns")
@RestController
@RequestMapping("/api/store/locations")
@RequiredArgsConstructor
public class StoreLocationController {

    private final StoreLocationService locationService;

    @GetMapping
    @PreAuthorize("hasAuthority('STORE_VIEW')")
    public List<StoreLocationResponse> list(@RequestParam Long plantId) {
        return locationService.list(plantId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('STORE_ENTRY')")
    public StoreLocationResponse create(@Valid @RequestBody StoreLocationRequest req) {
        return locationService.create(req);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('STORE_ENTRY')")
    public StoreLocationResponse update(@PathVariable Long id, @Valid @RequestBody StoreLocationRequest req) {
        return locationService.update(id, req);
    }
}
