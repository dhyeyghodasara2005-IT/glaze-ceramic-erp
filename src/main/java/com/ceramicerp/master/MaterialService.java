package com.ceramicerp.master;

import com.ceramicerp.common.PageResponse;
import com.ceramicerp.common.Text;
import com.ceramicerp.common.exception.BusinessException;
import com.ceramicerp.common.exception.NotFoundException;
import com.ceramicerp.master.dto.MaterialRequest;
import com.ceramicerp.master.dto.MaterialResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Locale;

/**
 * Business rules for materials:
 * - unique upper-case codes
 * - a new group/unit choice must be active
 * - the reorder level may not have more decimals than the unit allows (no "10.5 BOX")
 */
@Service
@RequiredArgsConstructor
public class MaterialService {

    private final MaterialRepository materialRepository;
    private final MaterialGroupService groupService;
    private final UnitOfMeasureService unitService;

    @Transactional(readOnly = true)
    public PageResponse<MaterialResponse> search(String search, Long groupId, Boolean active, int page, int size) {
        String pattern = Text.trimToNull(search) == null ? null : "%" + search.trim().toLowerCase(Locale.ROOT) + "%";
        var pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100), Sort.by("code"));
        return PageResponse.of(materialRepository.search(pattern, groupId, active, pageable), MaterialResponse::from);
    }

    @Transactional(readOnly = true)
    public MaterialResponse get(Long id) {
        return MaterialResponse.from(materialRepository.findWithDetailsById(id)
                .orElseThrow(() -> new NotFoundException("Material", id)));
    }

    @Transactional
    public MaterialResponse create(MaterialRequest req) {
        String code = Text.upperOrNull(req.code());
        if (materialRepository.existsByCodeIgnoreCase(code)) {
            throw new BusinessException("Material code already exists: " + code);
        }
        Material material = new Material();
        apply(material, req, code);
        return MaterialResponse.from(materialRepository.save(material));
    }

    @Transactional
    public MaterialResponse update(Long id, MaterialRequest req) {
        Material material = materialRepository.findById(id).orElseThrow(() -> new NotFoundException("Material", id));
        String code = Text.upperOrNull(req.code());
        if (materialRepository.existsByCodeIgnoreCaseAndIdNot(code, id)) {
            throw new BusinessException("Material code already exists: " + code);
        }
        apply(material, req, code);
        return MaterialResponse.from(material);
    }

    private void apply(Material m, MaterialRequest req, String code) {
        MaterialGroup group = groupService.find(req.groupId());
        UnitOfMeasure unit = unitService.find(req.unitId());
        MasterRules.requireActiveIfChanged(group.isActive(), m.getGroup(), group, "Material group", group.getCode());
        MasterRules.requireActiveIfChanged(unit.isActive(), m.getUnit(), unit, "Unit", unit.getCode());
        requireDecimalsFit(req.reorderLevel(), unit);

        m.setCode(code);
        m.setName(req.name().trim());
        m.setGroup(group);
        m.setUnit(unit);
        m.setHsnCode(Text.trimToNull(req.hsnCode()));
        m.setGstRate(req.gstRate());
        m.setReorderLevel(req.reorderLevel());
        m.setDescription(Text.trimToNull(req.description()));
        if (req.active() != null) {
            m.setActive(req.active());
        }
    }

    /** 10.500 KG is fine (3 decimals allowed), 10.5 BOX is not (0 decimals). */
    private static void requireDecimalsFit(BigDecimal quantity, UnitOfMeasure unit) {
        if (quantity != null && quantity.stripTrailingZeros().scale() > unit.getDecimals()) {
            throw new BusinessException("Reorder level in " + unit.getCode() + " can have at most "
                    + unit.getDecimals() + " decimal places");
        }
    }
}
