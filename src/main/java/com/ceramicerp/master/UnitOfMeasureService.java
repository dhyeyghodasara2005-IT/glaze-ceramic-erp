package com.ceramicerp.master;

import com.ceramicerp.common.Text;
import com.ceramicerp.common.exception.BusinessException;
import com.ceramicerp.common.exception.NotFoundException;
import com.ceramicerp.master.dto.UnitOfMeasureRequest;
import com.ceramicerp.master.dto.UnitOfMeasureResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class UnitOfMeasureService {

    private final UnitOfMeasureRepository unitRepository;

    @Transactional(readOnly = true)
    public List<UnitOfMeasureResponse> list() {
        return unitRepository.findAllByOrderByCodeAsc().stream().map(UnitOfMeasureResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public UnitOfMeasureResponse get(Long id) {
        return UnitOfMeasureResponse.from(find(id));
    }

    @Transactional
    public UnitOfMeasureResponse create(UnitOfMeasureRequest req) {
        String code = Text.upperOrNull(req.code());
        if (unitRepository.existsByCodeIgnoreCase(code)) {
            throw new BusinessException("Unit code already exists: " + code);
        }
        UnitOfMeasure unit = new UnitOfMeasure();
        apply(unit, req, code);
        return UnitOfMeasureResponse.from(unitRepository.save(unit));
    }

    @Transactional
    public UnitOfMeasureResponse update(Long id, UnitOfMeasureRequest req) {
        UnitOfMeasure unit = find(id);
        String code = Text.upperOrNull(req.code());
        if (unitRepository.existsByCodeIgnoreCaseAndIdNot(code, id)) {
            throw new BusinessException("Unit code already exists: " + code);
        }
        apply(unit, req, code);
        return UnitOfMeasureResponse.from(unit);
    }

    UnitOfMeasure find(Long id) {
        return unitRepository.findById(id).orElseThrow(() -> new NotFoundException("Unit", id));
    }

    private void apply(UnitOfMeasure u, UnitOfMeasureRequest req, String code) {
        u.setCode(code);
        u.setName(req.name().trim());
        u.setDecimals(req.decimals());
        if (req.active() != null) {
            u.setActive(req.active());
        }
    }
}
