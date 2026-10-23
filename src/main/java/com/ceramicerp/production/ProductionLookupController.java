package com.ceramicerp.production;

import com.ceramicerp.fg.FgGodownService;
import com.ceramicerp.fg.dto.PillarOption;
import com.ceramicerp.master.BodyRecipeService;
import com.ceramicerp.master.DesignRepository;
import com.ceramicerp.master.GradeRepository;
import com.ceramicerp.master.MachineRepository;
import com.ceramicerp.master.MaterialRepository;
import com.ceramicerp.master.ShiftService;
import com.ceramicerp.master.dto.BodyRecipeResponse;
import com.ceramicerp.master.dto.ShiftResponse;
import com.ceramicerp.production.dto.AvailableStock;
import com.ceramicerp.production.dto.BodyPrepOverview;
import com.ceramicerp.production.dto.CurrentShiftResponse;
import com.ceramicerp.production.dto.DesignOption;
import com.ceramicerp.production.dto.GradeOption;
import com.ceramicerp.production.dto.MachineOption;
import com.ceramicerp.security.PlantAccess;
import com.ceramicerp.store.StockBalanceRepository;
import com.ceramicerp.store.StoreLocation;
import com.ceramicerp.store.StoreLocationRepository;
import com.ceramicerp.store.dto.StoreOption;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Everything the production screens need for their dropdowns, with only PRODUCTION_VIEW.
 * A supervisor gets shifts, recipes, machines, materials, godowns and stock QUANTITIES - but no
 * master editing rights and no rupee values.
 */
@Tag(name = "Production - Lookups")
@RestController
@RequestMapping("/api/production")
@RequiredArgsConstructor
public class ProductionLookupController {

    private final ShiftService shiftService;
    private final BodyRecipeService recipeService;
    private final BodyPrepService bodyPrepService;
    private final MachineRepository machineRepository;
    private final MaterialRepository materialRepository;
    private final StoreLocationRepository locationRepository;
    private final StockBalanceRepository balanceRepository;
    private final DesignRepository designRepository;
    private final GradeRepository gradeRepository;
    private final FgGodownService fgGodownService;

    @GetMapping("/body/overview")
    @PreAuthorize("hasAuthority('PRODUCTION_VIEW')")
    public BodyPrepOverview overview(@RequestParam Long plantId) {
        return bodyPrepService.overview(plantId);
    }

    @GetMapping("/lookups/current-shift")
    @PreAuthorize("hasAuthority('PRODUCTION_VIEW')")
    public CurrentShiftResponse currentShift() {
        return bodyPrepService.currentShift();
    }

    @GetMapping("/lookups/shifts")
    @PreAuthorize("hasAuthority('PRODUCTION_VIEW')")
    public List<ShiftResponse> shifts() {
        return shiftService.listActive();
    }

    @GetMapping("/lookups/recipes")
    @PreAuthorize("hasAuthority('PRODUCTION_VIEW')")
    public List<BodyRecipeResponse> recipes() {
        return recipeService.listActive();
    }

    @GetMapping("/lookups/machines")
    @PreAuthorize("hasAuthority('PRODUCTION_VIEW')")
    @Transactional(readOnly = true)
    public List<MachineOption> machines(@RequestParam Long plantId) {
        PlantAccess.require(plantId);
        return machineRepository.findByPlantIdOrderByTypeAscCodeAsc(plantId).stream()
                .filter(m -> m.isActive())
                .map(m -> new MachineOption(m.getId(), m.getCode(), m.getName(), m.getType()))
                .toList();
    }

    @GetMapping("/lookups/materials")
    @PreAuthorize("hasAuthority('PRODUCTION_VIEW')")
    @Transactional(readOnly = true)
    public List<StoreOption> materials() {
        return materialRepository.findByActiveTrueOrderByCodeAsc().stream()
                .map(m -> new StoreOption(m.getId(), m.getCode(), m.getName(), m.getUnit().getCode(),
                        m.getUnit().getDecimals()))
                .toList();
    }

    @GetMapping("/lookups/godowns")
    @PreAuthorize("hasAuthority('PRODUCTION_VIEW')")
    @Transactional(readOnly = true)
    public List<StoreOption> godowns(@RequestParam Long plantId) {
        PlantAccess.require(plantId);
        return locationRepository.findByPlantIdOrderByCodeAsc(plantId).stream()
                .filter(StoreLocation::isActive)
                .map(l -> new StoreOption(l.getId(), l.getCode(), l.getName(), null, 0))
                .toList();
    }

    /** Quantity of every material in every godown of the plant (only where there is some). */
    @GetMapping("/lookups/stock")
    @PreAuthorize("hasAuthority('PRODUCTION_VIEW')")
    @Transactional(readOnly = true)
    public List<AvailableStock> stock(@RequestParam Long plantId) {
        PlantAccess.require(plantId);
        return balanceRepository.findByPlantId(plantId).stream()
                .filter(b -> b.getQuantity().signum() > 0)
                .map(b -> new AvailableStock(b.getMaterial().getId(), b.getLocation().getId(), b.getQuantity()))
                .toList();
    }

    /** Active designs for "start a design on a line", with the area of one tile. */
    @GetMapping("/lookups/designs")
    @PreAuthorize("hasAuthority('PRODUCTION_VIEW')")
    @Transactional(readOnly = true)
    public List<DesignOption> designs() {
        return designRepository.findByActiveTrueOrderByCodeAsc().stream()
                .map(d -> new DesignOption(d.getId(), d.getCode(), d.getName(), d.getTileSize().getCode(),
                        d.getFinish().getName(), ProductionRules.sqmPerTile(d.getTileSize())))
                .toList();
    }

    /** Grades boxes can be packed in (sellable, active), best first. Broken tiles are counted, not graded. */
    @GetMapping("/lookups/grades")
    @PreAuthorize("hasAuthority('PRODUCTION_VIEW')")
    @Transactional(readOnly = true)
    public List<GradeOption> grades() {
        return gradeRepository.findAllByOrderBySortOrderAscNameAsc().stream()
                .filter(g -> g.isActive() && g.isSellable())
                .map(g -> new GradeOption(g.getId(), g.getCode(), g.getName(), g.getSortOrder()))
                .toList();
    }

    /** Open pillars of the plant's finished-goods godowns, with how full each is: for the pack entry. */
    @GetMapping("/lookups/pillars")
    @PreAuthorize("hasAuthority('PRODUCTION_VIEW')")
    public List<PillarOption> pillars(@RequestParam Long plantId) {
        return fgGodownService.pillarOptions(plantId);
    }
}
