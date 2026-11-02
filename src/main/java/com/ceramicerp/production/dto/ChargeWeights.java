package com.ceramicerp.production.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.List;

/** "To put 12 T of dry clay in the mill, load 13.75 T." */
public final class ChargeWeights {

    private ChargeWeights() {
    }

    public record Request(@NotNull(message = "Select a plant") Long plantId, @NotEmpty List<@Valid Line> lines) {
    }

    public record Line(@NotNull Long materialId, @NotNull Long locationId,
                       @NotNull @DecimalMin(value = "0", inclusive = false) BigDecimal dryQuantity) {
    }

    /** @param waterLitres the water that the moisture brings into the mill */
    public record Result(List<LineResult> lines, BigDecimal waterLitres) {
    }

    /**
     * @param wetQuantity     the weight to load
     * @param moisturePct     average of the lots used; empty when none of them has a moisture
     * @param moistureUnknown some of it comes from a lot with no moisture, loaded as weighed
     * @param shortDry        dry quantity the godown can't supply; empty when it can
     * @param lots            the lots it will come from, oldest first
     */
    public record LineResult(Long materialId, Long locationId, BigDecimal dryQuantity, BigDecimal wetQuantity,
                             BigDecimal moisturePct, boolean moistureUnknown, BigDecimal shortDry, List<LotShare> lots) {
    }

    public record LotShare(String lotNo, BigDecimal moisturePct, BigDecimal wetQuantity) {
    }
}
