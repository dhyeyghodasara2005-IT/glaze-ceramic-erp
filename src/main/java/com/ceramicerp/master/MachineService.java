package com.ceramicerp.master;

import com.ceramicerp.common.Text;
import com.ceramicerp.common.exception.BusinessException;
import com.ceramicerp.common.exception.NotFoundException;
import com.ceramicerp.master.dto.MachineRequest;
import com.ceramicerp.master.dto.MachineResponse;
import com.ceramicerp.organization.Plant;
import com.ceramicerp.organization.PlantService;
import com.ceramicerp.security.AppUserDetails;
import com.ceramicerp.security.CurrentUser;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Business rules for machines:
 * - you only see / edit machines in plants you have access to
 * - codes are unique within a plant
 * - a new plant choice must be active
 */
@Service
@RequiredArgsConstructor
public class MachineService {

    private final MachineRepository machineRepository;
    private final PlantService plantService;

    @Transactional(readOnly = true)
    public List<MachineResponse> list(Long plantId) {
        AppUserDetails user = CurrentUser.get();
        List<Machine> machines = plantId == null
                ? machineRepository.findAllByOrderByPlantNameAscTypeAscCodeAsc()
                : machineRepository.findByPlantIdOrderByTypeAscCodeAsc(plantId);
        return machines.stream()
                .filter(m -> user.canAccessPlant(m.getPlant().getId()))
                .map(MachineResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public MachineResponse get(Long id) {
        Machine machine = find(id);
        requirePlantAccess(machine.getPlant().getId());
        return MachineResponse.from(machine);
    }

    @Transactional
    public MachineResponse create(MachineRequest req) {
        Plant plant = choosePlant(req.plantId(), null);
        String code = Text.upperOrNull(req.code());
        if (machineRepository.existsByPlantIdAndCodeIgnoreCase(plant.getId(), code)) {
            throw new BusinessException("Machine code " + code + " already exists in plant " + plant.getCode());
        }
        Machine machine = new Machine();
        machine.setPlant(plant);
        apply(machine, req, code);
        return MachineResponse.from(machineRepository.save(machine));
    }

    @Transactional
    public MachineResponse update(Long id, MachineRequest req) {
        Machine machine = find(id);
        requirePlantAccess(machine.getPlant().getId());          // may you touch it where it is now?
        Plant plant = choosePlant(req.plantId(), machine.getPlant()); // ...and where it's moving to?
        String code = Text.upperOrNull(req.code());
        if (machineRepository.existsByPlantIdAndCodeIgnoreCaseAndIdNot(plant.getId(), code, id)) {
            throw new BusinessException("Machine code " + code + " already exists in plant " + plant.getCode());
        }
        machine.setPlant(plant);
        apply(machine, req, code);
        return MachineResponse.from(machine);
    }

    private Machine find(Long id) {
        return machineRepository.findWithPlantById(id).orElseThrow(() -> new NotFoundException("Machine", id));
    }

    /** Loads the plant, checks the user may use it, and (if it changed) that it's active. */
    private Plant choosePlant(Long plantId, Plant current) {
        Plant plant = plantService.find(plantId);
        requirePlantAccess(plant.getId());
        MasterRules.requireActiveIfChanged(plant.isActive(), current, plant, "Plant", plant.getCode());
        return plant;
    }

    private static void requirePlantAccess(Long plantId) {
        if (!CurrentUser.get().canAccessPlant(plantId)) {
            throw new AccessDeniedException("No access to plant " + plantId);
        }
    }

    private void apply(Machine m, MachineRequest req, String code) {
        m.setCode(code);
        m.setName(req.name().trim());
        m.setType(req.type());
        m.setCapacity(req.capacity());
        m.setCapacityUnit(Text.trimToNull(req.capacityUnit()));
        m.setMake(Text.trimToNull(req.make()));
        m.setModel(Text.trimToNull(req.model()));
        m.setInstalledOn(req.installedOn());
        if (req.active() != null) {
            m.setActive(req.active());
        }
    }
}
