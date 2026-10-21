package com.ceramicerp.organization;

import com.ceramicerp.organization.dto.PlantRequest;
import com.ceramicerp.organization.dto.PlantResponse;
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

@Tag(name = "Plants")
@RestController
@RequestMapping("/api/plants")
@RequiredArgsConstructor
public class PlantController {

    private final PlantService plantService;

    @GetMapping
    @PreAuthorize("hasAuthority('PLANT_VIEW')")
    public List<PlantResponse> list(@RequestParam(required = false) Long companyId) {
        return plantService.list(companyId);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('PLANT_VIEW')")
    public PlantResponse get(@PathVariable Long id) {
        return plantService.get(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('PLANT_MANAGE')")
    public PlantResponse create(@Valid @RequestBody PlantRequest req) {
        return plantService.create(req);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('PLANT_MANAGE')")
    public PlantResponse update(@PathVariable Long id, @Valid @RequestBody PlantRequest req) {
        return plantService.update(id, req);
    }
}
