package com.ceramicerp.store;

import com.ceramicerp.common.Text;
import com.ceramicerp.common.exception.BusinessException;
import com.ceramicerp.common.exception.NotFoundException;
import com.ceramicerp.organization.Plant;
import com.ceramicerp.organization.PlantService;
import com.ceramicerp.security.PlantAccess;
import com.ceramicerp.store.dto.StoreLocationRequest;
import com.ceramicerp.store.dto.StoreLocationResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** Godowns of a plant. Codes are unique within the plant; only users with plant access see them. */
@Service
@RequiredArgsConstructor
public class StoreLocationService {

    private final StoreLocationRepository locationRepository;
    private final PlantService plantService;

    @Transactional(readOnly = true)
    public List<StoreLocationResponse> list(Long plantId) {
        PlantAccess.require(plantId);
        return locationRepository.findByPlantIdOrderByCodeAsc(plantId).stream().map(StoreLocationResponse::from).toList();
    }

    @Transactional
    public StoreLocationResponse create(StoreLocationRequest req) {
        Plant plant = plantService.find(req.plantId());
        PlantAccess.require(plant.getId());
        String code = Text.upperOrNull(req.code());
        if (locationRepository.existsByPlantIdAndCodeIgnoreCase(plant.getId(), code)) {
            throw new BusinessException("Godown code " + code + " already exists in plant " + plant.getCode());
        }
        StoreLocation location = new StoreLocation();
        location.setPlant(plant);
        apply(location, req, code);
        return StoreLocationResponse.from(locationRepository.save(location));
    }

    @Transactional
    public StoreLocationResponse update(Long id, StoreLocationRequest req) {
        StoreLocation location = find(id);
        PlantAccess.require(location.getPlant().getId());
        if (!location.getPlant().getId().equals(req.plantId())) {
            throw new BusinessException("A godown can't be moved to another plant");
        }
        String code = Text.upperOrNull(req.code());
        if (locationRepository.existsByPlantIdAndCodeIgnoreCaseAndIdNot(location.getPlant().getId(), code, id)) {
            throw new BusinessException("Godown code " + code + " already exists in this plant");
        }
        apply(location, req, code);
        return StoreLocationResponse.from(location);
    }

    StoreLocation find(Long id) {
        return locationRepository.findWithPlantById(id).orElseThrow(() -> new NotFoundException("Godown", id));
    }

    private void apply(StoreLocation l, StoreLocationRequest req, String code) {
        l.setCode(code);
        l.setName(req.name().trim());
        if (req.active() != null) {
            l.setActive(req.active());
        }
    }
}
