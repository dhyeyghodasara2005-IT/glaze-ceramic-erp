package com.ceramicerp.production;

import com.ceramicerp.production.dto.MaterialTrace;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * /api/production/trace/body-batches/5    - the trucks behind a mill charge
 * /api/production/trace/lots/5            - the trucks behind a design lot
 * /api/production/trace/material-lots/5   - the mill charges and design lots a truck went into
 */
@Tag(name = "Production - Raw material trace")
@RestController
@RequestMapping("/api/production/trace")
@RequiredArgsConstructor
public class MaterialTraceController {

    private final MaterialTraceService traceService;

    @GetMapping("/body-batches/{id}")
    @PreAuthorize("hasAuthority('PRODUCTION_VIEW')")
    public List<MaterialTrace.Truck> forBatch(@PathVariable Long id) {
        return traceService.forBatch(id);
    }

    @GetMapping("/lots/{id}")
    @PreAuthorize("hasAuthority('PRODUCTION_VIEW')")
    public List<MaterialTrace.Truck> forDesignLot(@PathVariable Long id) {
        return traceService.forDesignLot(id);
    }

    @GetMapping("/material-lots/{id}")
    @PreAuthorize("hasAuthority('PRODUCTION_VIEW')")
    public MaterialTrace.Reach forMaterialLot(@PathVariable Long id) {
        return traceService.forMaterialLot(id);
    }
}
