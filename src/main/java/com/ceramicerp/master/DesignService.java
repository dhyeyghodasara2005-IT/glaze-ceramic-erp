package com.ceramicerp.master;

import com.ceramicerp.common.PageResponse;
import com.ceramicerp.common.Text;
import com.ceramicerp.common.exception.BusinessException;
import com.ceramicerp.common.exception.NotFoundException;
import com.ceramicerp.master.dto.DesignRequest;
import com.ceramicerp.master.dto.DesignResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

/**
 * Business rules for designs:
 * - codes are unique and stored upper-case
 * - a NEW category/size/finish choice must be active (you can't pick a retired size),
 *   but an existing design keeps working if its size is retired later
 */
@Service
@RequiredArgsConstructor
public class DesignService {

    private final DesignRepository designRepository;
    private final ProductCategoryService categoryService;
    private final TileSizeService tileSizeService;
    private final FinishService finishService;

    @Transactional(readOnly = true)
    public PageResponse<DesignResponse> search(String search, Long categoryId, Long sizeId, Long finishId,
                                               Boolean active, int page, int size) {
        String pattern = Text.trimToNull(search) == null
                ? null
                : "%" + search.trim().toLowerCase(Locale.ROOT) + "%";
        // page >= 0, page size between 1 and 100, sorted by code
        var pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100), Sort.by("code"));
        var result = designRepository.search(pattern, categoryId, sizeId, finishId, active, pageable);
        return PageResponse.of(result, DesignResponse::from);
    }

    @Transactional(readOnly = true)
    public DesignResponse get(Long id) {
        return DesignResponse.from(designRepository.findWithDetailsById(id)
                .orElseThrow(() -> new NotFoundException("Design", id)));
    }

    @Transactional
    public DesignResponse create(DesignRequest req) {
        String code = Text.upperOrNull(req.code());
        if (designRepository.existsByCodeIgnoreCase(code)) {
            throw new BusinessException("Design code already exists: " + code);
        }
        Design design = new Design();
        apply(design, req, code);
        return DesignResponse.from(designRepository.save(design));
    }

    @Transactional
    public DesignResponse update(Long id, DesignRequest req) {
        Design design = designRepository.findById(id).orElseThrow(() -> new NotFoundException("Design", id));
        String code = Text.upperOrNull(req.code());
        if (designRepository.existsByCodeIgnoreCaseAndIdNot(code, id)) {
            throw new BusinessException("Design code already exists: " + code);
        }
        apply(design, req, code);
        return DesignResponse.from(design);
    }

    private void apply(Design d, DesignRequest req, String code) {
        ProductCategory category = categoryService.find(req.categoryId());
        TileSize tileSize = tileSizeService.find(req.sizeId());
        Finish finish = finishService.find(req.finishId());

        MasterRules.requireActiveIfChanged(category.isActive(), d.getCategory(), category, "Category", category.getCode());
        MasterRules.requireActiveIfChanged(tileSize.isActive(), d.getTileSize(), tileSize, "Size", tileSize.getCode());
        MasterRules.requireActiveIfChanged(finish.isActive(), d.getFinish(), finish, "Finish", finish.getCode());

        d.setCode(code);
        d.setName(req.name().trim());
        d.setCategory(category);
        d.setTileSize(tileSize);
        d.setFinish(finish);
        d.setFaces(req.faces());
        d.setLaunchDate(req.launchDate());
        d.setDescription(Text.trimToNull(req.description()));
        if (req.active() != null) {
            d.setActive(req.active());
        }
    }
}
