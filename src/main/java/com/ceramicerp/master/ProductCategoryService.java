package com.ceramicerp.master;

import com.ceramicerp.common.Text;
import com.ceramicerp.common.exception.BusinessException;
import com.ceramicerp.common.exception.NotFoundException;
import com.ceramicerp.master.dto.ProductCategoryRequest;
import com.ceramicerp.master.dto.ProductCategoryResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** Business rules for product categories: unique upper-case codes. */
@Service
@RequiredArgsConstructor
public class ProductCategoryService {

    private final ProductCategoryRepository categoryRepository;

    @Transactional(readOnly = true)
    public List<ProductCategoryResponse> list() {
        return categoryRepository.findAllByOrderByNameAsc().stream()
                .map(ProductCategoryResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public ProductCategoryResponse get(Long id) {
        return ProductCategoryResponse.from(find(id));
    }

    @Transactional
    public ProductCategoryResponse create(ProductCategoryRequest req) {
        String code = Text.upperOrNull(req.code());
        if (categoryRepository.existsByCodeIgnoreCase(code)) {
            throw new BusinessException("Category code already exists: " + code);
        }
        ProductCategory category = new ProductCategory();
        apply(category, req, code);
        return ProductCategoryResponse.from(categoryRepository.save(category));
    }

    @Transactional
    public ProductCategoryResponse update(Long id, ProductCategoryRequest req) {
        ProductCategory category = find(id);
        String code = Text.upperOrNull(req.code());
        if (categoryRepository.existsByCodeIgnoreCaseAndIdNot(code, id)) {
            throw new BusinessException("Category code already exists: " + code);
        }
        apply(category, req, code);
        return ProductCategoryResponse.from(category);
    }

    /** Also used by DesignService. */
    ProductCategory find(Long id) {
        return categoryRepository.findById(id).orElseThrow(() -> new NotFoundException("Category", id));
    }

    private void apply(ProductCategory c, ProductCategoryRequest req, String code) {
        c.setCode(code);
        c.setName(req.name().trim());
        c.setHsnCode(Text.trimToNull(req.hsnCode()));
        c.setGstRate(req.gstRate());
        if (req.active() != null) {
            c.setActive(req.active());
        }
    }
}
