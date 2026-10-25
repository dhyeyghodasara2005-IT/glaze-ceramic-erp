package com.ceramicerp.store;

import com.ceramicerp.master.Machine;
import com.ceramicerp.master.MachineRepository;
import com.ceramicerp.master.MaterialRepository;
import com.ceramicerp.party.PartyRepository;
import com.ceramicerp.party.PartyType;
import com.ceramicerp.security.PlantAccess;
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
 * Dropdown lists for the store screens. They only need STORE_VIEW, so a store keeper can make
 * a GRN without also being given the right to view (or edit) all masters and parties.
 */
@Tag(name = "Store - Lookups")
@RestController
@RequestMapping("/api/store/lookups")
@RequiredArgsConstructor
public class StoreLookupController {

    private final MaterialRepository materialRepository;
    private final PartyRepository partyRepository;
    private final MachineRepository machineRepository;

    @GetMapping("/materials")
    @PreAuthorize("hasAuthority('STORE_VIEW')")
    @Transactional(readOnly = true)
    public List<StoreOption> materials() {
        return materialRepository.findByActiveTrueOrderByCodeAsc().stream()
                .map(m -> new StoreOption(m.getId(), m.getCode(), m.getName(), m.getUnit().getCode(),
                        m.getUnit().getDecimals()))
                .toList();
    }

    @GetMapping("/suppliers")
    @PreAuthorize("hasAuthority('STORE_VIEW')")
    public List<StoreOption> suppliers() {
        return partyRepository.findByPartyTypeAndActiveTrueOrderByNameAsc(PartyType.SUPPLIER).stream()
                .map(p -> new StoreOption(p.getId(), p.getCode(), p.getName(), p.getCity(), 0))
                .toList();
    }

    @GetMapping("/machines")
    @PreAuthorize("hasAuthority('STORE_VIEW')")
    public List<StoreOption> machines(@RequestParam Long plantId) {
        PlantAccess.require(plantId);
        return machineRepository.findByPlantIdInProcessOrder(plantId).stream()
                .filter(Machine::isActive)
                .map(m -> new StoreOption(m.getId(), m.getCode(), m.getName(), m.getType().name(), 0))
                .toList();
    }
}
