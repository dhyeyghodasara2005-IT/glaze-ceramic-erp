package com.ceramicerp.production;

import com.ceramicerp.production.dto.PressOverview;
import com.ceramicerp.production.dto.SiloStatus;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Silos (shared by body preparation, which fills them, and the press, which empties them). */
@Tag(name = "Production - Silos & press")
@RestController
@RequestMapping("/api/production")
@RequiredArgsConstructor
public class SiloController {

    private final SiloService siloService;
    private final PressService pressService;

    @GetMapping("/silos")
    @PreAuthorize("hasAuthority('PRODUCTION_VIEW')")
    public List<SiloStatus> silos(@RequestParam Long plantId) {
        return siloService.status(plantId);
    }

    /** Either supervisor can say "this silo is empty": the one who fills it or the one who empties it. */
    @PostMapping("/silos/{id}/empty")
    @PreAuthorize("hasAnyAuthority('BODY_PREP_ENTRY', 'PRESS_ENTRY')")
    public List<SiloStatus> markEmpty(@PathVariable Long id) {
        return siloService.markEmpty(id);
    }

    @GetMapping("/press/overview")
    @PreAuthorize("hasAuthority('PRODUCTION_VIEW')")
    public PressOverview pressOverview(@RequestParam Long plantId) {
        return pressService.overview(plantId);
    }
}
