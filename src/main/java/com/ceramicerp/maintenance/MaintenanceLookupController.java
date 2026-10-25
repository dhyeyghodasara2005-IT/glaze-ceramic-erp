package com.ceramicerp.maintenance;

import com.ceramicerp.maintenance.dto.MachineRef;
import com.ceramicerp.maintenance.dto.SpareOption;
import com.ceramicerp.maintenance.dto.SpareStock;
import com.ceramicerp.master.MachineRepository;
import com.ceramicerp.master.MaterialRepository;
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

import java.util.Comparator;
import java.util.List;

/**
 * /api/maintenance/lookups/... - the dropdowns the breakdown screens need, without master or store rights:
 * machines for reporters, spares, godowns and their stock for the maintenance team.
 */
@Tag(name = "Maintenance - lookups")
@RestController
@RequestMapping("/api/maintenance/lookups")
@RequiredArgsConstructor
public class MaintenanceLookupController {

    /** The store's "Spares & consumables" group: listed first. */
    private static final String SPARE_GROUP = "SPARE";

    private final MachineRepository machineRepository;
    private final MaterialRepository materialRepository;
    private final StoreLocationRepository locationRepository;
    private final StockBalanceRepository balanceRepository;

    /** Active machines of the plant, in process order. */
    @GetMapping("/machines")
    @PreAuthorize("hasAnyAuthority('MAINTENANCE_VIEW', 'BREAKDOWN_REPORT', 'MAINTENANCE_ENTRY')")
    @Transactional(readOnly = true)
    public List<MachineRef> machines(@RequestParam Long plantId) {
        PlantAccess.require(plantId);
        return machineRepository.findByPlantIdInProcessOrder(plantId).stream()
                .filter(m -> m.isActive())
                .map(MachineRef::of)
                .toList();
    }

    /** Active materials, spares first. */
    @GetMapping("/spares")
    @PreAuthorize("hasAuthority('MAINTENANCE_ENTRY')")
    @Transactional(readOnly = true)
    public List<SpareOption> spares() {
        return materialRepository.findByActiveTrueOrderByCodeAsc().stream()
                .map(m -> new SpareOption(m.getId(), m.getCode(), m.getName(), m.getUnit().getCode(),
                        m.getUnit().getDecimals(), SPARE_GROUP.equals(m.getGroup().getCode())))
                .sorted(Comparator.comparing((SpareOption o) -> !o.spare()).thenComparing(SpareOption::code))
                .toList();
    }

    @GetMapping("/godowns")
    @PreAuthorize("hasAuthority('MAINTENANCE_ENTRY')")
    @Transactional(readOnly = true)
    public List<StoreOption> godowns(@RequestParam Long plantId) {
        PlantAccess.require(plantId);
        return locationRepository.findByPlantIdOrderByCodeAsc(plantId).stream()
                .filter(StoreLocation::isActive)
                .map(l -> new StoreOption(l.getId(), l.getCode(), l.getName(), null, 0))
                .toList();
    }

    /** What each godown holds (only where there is some), so the screen can say "3 in stock". */
    @GetMapping("/stock")
    @PreAuthorize("hasAuthority('MAINTENANCE_ENTRY')")
    @Transactional(readOnly = true)
    public List<SpareStock> stock(@RequestParam Long plantId) {
        PlantAccess.require(plantId);
        return balanceRepository.findByPlantId(plantId).stream()
                .filter(b -> b.getQuantity().signum() > 0)
                .map(b -> new SpareStock(b.getMaterial().getId(), b.getLocation().getId(), b.getQuantity()))
                .toList();
    }
}
