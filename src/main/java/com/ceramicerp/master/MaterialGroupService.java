package com.ceramicerp.master;

import com.ceramicerp.common.Text;
import com.ceramicerp.common.exception.BusinessException;
import com.ceramicerp.common.exception.NotFoundException;
import com.ceramicerp.master.dto.MaterialGroupRequest;
import com.ceramicerp.master.dto.MaterialGroupResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class MaterialGroupService {

    private final MaterialGroupRepository groupRepository;

    @Transactional(readOnly = true)
    public List<MaterialGroupResponse> list() {
        return groupRepository.findAllByOrderByNameAsc().stream().map(MaterialGroupResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public MaterialGroupResponse get(Long id) {
        return MaterialGroupResponse.from(find(id));
    }

    @Transactional
    public MaterialGroupResponse create(MaterialGroupRequest req) {
        String code = Text.upperOrNull(req.code());
        if (groupRepository.existsByCodeIgnoreCase(code)) {
            throw new BusinessException("Material group code already exists: " + code);
        }
        MaterialGroup group = new MaterialGroup();
        apply(group, req, code);
        return MaterialGroupResponse.from(groupRepository.save(group));
    }

    @Transactional
    public MaterialGroupResponse update(Long id, MaterialGroupRequest req) {
        MaterialGroup group = find(id);
        String code = Text.upperOrNull(req.code());
        if (groupRepository.existsByCodeIgnoreCaseAndIdNot(code, id)) {
            throw new BusinessException("Material group code already exists: " + code);
        }
        apply(group, req, code);
        return MaterialGroupResponse.from(group);
    }

    MaterialGroup find(Long id) {
        return groupRepository.findById(id).orElseThrow(() -> new NotFoundException("Material group", id));
    }

    private void apply(MaterialGroup g, MaterialGroupRequest req, String code) {
        g.setCode(code);
        g.setName(req.name().trim());
        if (req.active() != null) {
            g.setActive(req.active());
        }
    }
}
