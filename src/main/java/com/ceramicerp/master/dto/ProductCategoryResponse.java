package com.ceramicerp.master.dto;

import com.ceramicerp.master.ProductCategory;

import java.math.BigDecimal;

/** What the API sends back for a product category. */
public record ProductCategoryResponse(
        Long id,
        String code,
        String name,
        String hsnCode,
        BigDecimal gstRate,
        boolean active,
        Long version
) {
    public static ProductCategoryResponse from(ProductCategory c) {
        return new ProductCategoryResponse(c.getId(), c.getCode(), c.getName(), c.getHsnCode(), c.getGstRate(),
                c.isActive(), c.getVersion());
    }
}
