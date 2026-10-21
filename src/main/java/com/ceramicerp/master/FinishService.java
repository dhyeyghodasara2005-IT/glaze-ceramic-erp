package com.ceramicerp.master;

import com.ceramicerp.common.Text;
import com.ceramicerp.common.exception.BusinessException;
import com.ceramicerp.common.exception.NotFoundException;
import com.ceramicerp.master.dto.FinishRequest;
import com.ceramicerp.master.dto.FinishResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** Business rules for finishes: codes are unique and stored upper-case. */
@Service
@RequiredArgsConstructor
public class FinishService {

    private final FinishRepository finishRepository;

    @Transactional(readOnly = true)
    public List<FinishResponse> list() {
        return finishRepository.findAllByOrderByNameAsc().stream()
                .map(FinishResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public FinishResponse get(Long id) {
        return FinishResponse.from(find(id));
    }

    @Transactional
    public FinishResponse create(FinishRequest req) {
        String code = Text.upperOrNull(req.code());
        if (finishRepository.existsByCodeIgnoreCase(code)) {
            throw new BusinessException("Finish code already exists: " + code);
        }
        Finish finish = new Finish();
        apply(finish, req, code);
        return FinishResponse.from(finishRepository.save(finish));
    }

    @Transactional
    public FinishResponse update(Long id, FinishRequest req) {
        Finish finish = find(id);
        String code = Text.upperOrNull(req.code());
        if (finishRepository.existsByCodeIgnoreCaseAndIdNot(code, id)) {
            throw new BusinessException("Finish code already exists: " + code);
        }
        apply(finish, req, code);
        return FinishResponse.from(finish);
    }

    /** Also used by DesignService (same package) to check a finish exists. */
    Finish find(Long id) {
        return finishRepository.findById(id).orElseThrow(() -> new NotFoundException("Finish", id));
    }

    private void apply(Finish f, FinishRequest req, String code) {
        f.setCode(code);
        f.setName(req.name().trim());
        if (req.active() != null) {
            f.setActive(req.active());
        }
    }
}
