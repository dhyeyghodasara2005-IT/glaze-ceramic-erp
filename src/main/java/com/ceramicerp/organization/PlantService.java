package com.ceramicerp.organization;

import com.ceramicerp.common.Text;
import com.ceramicerp.common.exception.BusinessException;
import com.ceramicerp.common.exception.NotFoundException;
import com.ceramicerp.organization.dto.PlantRequest;
import com.ceramicerp.organization.dto.PlantResponse;
import com.ceramicerp.security.AppUserDetails;
import com.ceramicerp.security.CurrentUser;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class PlantService {

    private final PlantRepository plantRepository;
    private final CompanyService companyService;

    /** Lists only the plants the logged-in user is allowed to see. */
    @Transactional(readOnly = true)
    public List<PlantResponse> list(Long companyId) {
        AppUserDetails user = CurrentUser.get();
        List<Plant> plants = companyId == null
                ? plantRepository.findAllByOrderByNameAsc()
                : plantRepository.findByCompanyIdOrderByNameAsc(companyId);
        return plants.stream()
                .filter(p -> user.canAccessPlant(p.getId()))
                .map(PlantResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public PlantResponse get(Long id) {
        Plant plant = find(id);
        if (!CurrentUser.get().canAccessPlant(id)) {
            throw new AccessDeniedException("No access to plant " + id);
        }
        return PlantResponse.from(plant);
    }

    @Transactional
    public PlantResponse create(PlantRequest req) {
        String code = Text.upperOrNull(req.code());
        if (plantRepository.existsByCodeIgnoreCase(code)) {
            throw new BusinessException("Plant code already exists: " + code);
        }
        Plant plant = new Plant();
        apply(plant, req, code);
        return PlantResponse.from(plantRepository.save(plant));
    }

    @Transactional
    public PlantResponse update(Long id, PlantRequest req) {
        Plant plant = find(id);
        String code = Text.upperOrNull(req.code());
        if (plantRepository.existsByCodeIgnoreCaseAndIdNot(code, id)) {
            throw new BusinessException("Plant code already exists: " + code);
        }
        apply(plant, req, code);
        return PlantResponse.from(plant);
    }

    public Plant find(Long id) {
        return plantRepository.findById(id).orElseThrow(() -> new NotFoundException("Plant", id));
    }

    private void apply(Plant p, PlantRequest req, String code) {
        p.setCompany(companyService.find(req.companyId()));
        p.setCode(code);
        p.setName(req.name().trim());
        p.setGstin(Text.upperOrNull(req.gstin()));
        p.setAddressLine(Text.trimToNull(req.addressLine()));
        p.setCity(Text.trimToNull(req.city()));
        p.setState(Text.trimToNull(req.state()));
        p.setPincode(Text.trimToNull(req.pincode()));
        p.setCapacitySqmPerDay(req.capacitySqmPerDay());
        if (req.active() != null) {
            p.setActive(req.active());
        }
    }
}
