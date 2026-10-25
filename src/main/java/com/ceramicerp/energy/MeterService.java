package com.ceramicerp.energy;

import com.ceramicerp.common.Text;
import com.ceramicerp.common.exception.BusinessException;
import com.ceramicerp.common.exception.NotFoundException;
import com.ceramicerp.energy.dto.MeterRequest;
import com.ceramicerp.energy.dto.MeterResponse;
import com.ceramicerp.master.Machine;
import com.ceramicerp.master.MachineRepository;
import com.ceramicerp.organization.Plant;
import com.ceramicerp.organization.PlantService;
import com.ceramicerp.security.PlantAccess;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** A plant's meters: what each one measures, in what, and how often it is read. */
@Service
@RequiredArgsConstructor
public class MeterService {

    private final MeterRepository meterRepository;
    private final MeterReadingRepository readingRepository;
    private final MachineRepository machineRepository;
    private final PlantService plantService;

    @Transactional(readOnly = true)
    public List<MeterResponse> list(Long plantId, boolean includeInactive) {
        PlantAccess.require(plantId);
        return meterRepository.findByPlantIdOrderBySortOrderAscCodeAsc(plantId).stream()
                .filter(m -> includeInactive || m.isActive())
                .map(MeterResponse::from)
                .toList();
    }

    @Transactional
    public MeterResponse create(MeterRequest req) {
        Plant plant = plantService.find(req.plantId());
        PlantAccess.require(plant.getId());
        String code = Text.upperOrNull(req.code());
        if (meterRepository.existsByPlantIdAndCodeIgnoreCase(plant.getId(), code)) {
            throw new BusinessException("Meter code " + code + " already exists in plant " + plant.getCode());
        }
        Meter m = new Meter();
        m.setPlant(plant);
        apply(m, req, code);
        return MeterResponse.from(meterRepository.save(m));
    }

    @Transactional
    public MeterResponse update(Long id, MeterRequest req) {
        Meter m = meterRepository.findWithDetailsById(id).orElseThrow(() -> new NotFoundException("Meter", id));
        PlantAccess.require(m.getPlant().getId());
        if (!m.getPlant().getId().equals(req.plantId())) {
            throw new BusinessException("A meter can't be moved to another plant");
        }
        String code = Text.upperOrNull(req.code());
        if (meterRepository.existsByPlantIdAndCodeIgnoreCaseAndIdNot(m.getPlant().getId(), code, id)) {
            throw new BusinessException("Meter code " + code + " already exists in plant " + m.getPlant().getCode());
        }
        // its readings were written as this kind, in this unit: changing either would mix two things in one total
        MeterUnit unit = req.unit() == null ? req.kind().getUnits().get(0) : req.unit();
        if ((req.kind() != m.getKind() || unit != m.getUnit()) && readingRepository.existsByMeterId(id)) {
            throw new BusinessException("Meter " + m.getCode() + " has readings, so its kind and unit stay: set up a new meter instead");
        }
        apply(m, req, code);
        return MeterResponse.from(m);
    }

    private void apply(Meter m, MeterRequest req, String code) {
        MeterUnit unit = req.unit() == null ? req.kind().getUnits().get(0) : req.unit();
        if (!req.kind().getUnits().contains(unit)) {
            throw new BusinessException("A " + req.kind().name().toLowerCase().replace('_', ' ') + " meter counts in "
                    + String.join(" or ", req.kind().getUnits().stream().map(Enum::name).toList()));
        }
        m.setCode(code);
        m.setName(req.name().trim());
        m.setKind(req.kind());
        m.setUnit(unit);
        m.setMain(Boolean.TRUE.equals(req.main()));
        m.setMachine(req.machineId() == null ? null : machine(req.machineId(), m.getPlant()));
        m.setReadEvery(req.readEvery());
        m.setSortOrder(req.sortOrder() == null ? 0 : req.sortOrder());
        if (req.active() != null) {
            m.setActive(req.active());
        }
    }

    private Machine machine(Long id, Plant plant) {
        Machine machine = machineRepository.findWithPlantById(id).orElseThrow(() -> new NotFoundException("Machine", id));
        if (!machine.getPlant().getId().equals(plant.getId())) {
            throw new BusinessException("Machine " + machine.getCode() + " is not in plant " + plant.getCode());
        }
        return machine;
    }
}
