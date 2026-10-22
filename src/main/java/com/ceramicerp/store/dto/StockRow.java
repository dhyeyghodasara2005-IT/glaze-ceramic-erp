package com.ceramicerp.store.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * Stock of one material in a plant: the total, and how much lies in each godown.
 *
 * @param averageRate value / quantity (null when there is no stock)
 * @param low         true when a reorder level is set and the stock is at or below it
 */
public record StockRow(
        Long materialId,
        String materialCode,
        String materialName,
        String groupName,
        String unitCode,
        int unitDecimals,
        BigDecimal quantity,
        BigDecimal value,
        BigDecimal averageRate,
        BigDecimal reorderLevel,
        boolean low,
        boolean active,
        List<LocationStock> locations
) {
    public record LocationStock(Long locationId, String locationCode, String locationName,
                                BigDecimal quantity, BigDecimal value) {
    }
}
