package com.ceramicerp.master;

import com.ceramicerp.common.Text;
import com.ceramicerp.common.exception.BusinessException;
import com.ceramicerp.common.exception.NotFoundException;
import com.ceramicerp.master.dto.GradeRequest;
import com.ceramicerp.master.dto.GradeResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** Business rules for grades: unique upper-case codes, listed best-first. */
@Service
@RequiredArgsConstructor
public class GradeService {

    private final GradeRepository gradeRepository;

    @Transactional(readOnly = true)
    public List<GradeResponse> list() {
        return gradeRepository.findAllByOrderBySortOrderAscNameAsc().stream()
                .map(GradeResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public GradeResponse get(Long id) {
        return GradeResponse.from(find(id));
    }

    @Transactional
    public GradeResponse create(GradeRequest req) {
        String code = Text.upperOrNull(req.code());
        if (gradeRepository.existsByCodeIgnoreCase(code)) {
            throw new BusinessException("Grade code already exists: " + code);
        }
        Grade grade = new Grade();
        apply(grade, req, code);
        return GradeResponse.from(gradeRepository.save(grade));
    }

    @Transactional
    public GradeResponse update(Long id, GradeRequest req) {
        Grade grade = find(id);
        String code = Text.upperOrNull(req.code());
        if (gradeRepository.existsByCodeIgnoreCaseAndIdNot(code, id)) {
            throw new BusinessException("Grade code already exists: " + code);
        }
        apply(grade, req, code);
        return GradeResponse.from(grade);
    }

    Grade find(Long id) {
        return gradeRepository.findById(id).orElseThrow(() -> new NotFoundException("Grade", id));
    }

    private void apply(Grade g, GradeRequest req, String code) {
        g.setCode(code);
        g.setName(req.name().trim());
        g.setSortOrder(req.sortOrder());
        g.setSellable(req.sellable());
        if (req.active() != null) {
            g.setActive(req.active());
        }
    }
}
