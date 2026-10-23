package com.ceramicerp.fg.dto;

import com.ceramicerp.fg.FgStock;
import com.ceramicerp.master.TileSize;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * One stock row as the screens show it: which boxes, where, how many.
 *
 * @param pallets boxes / boxes per pallet, rounded up (a part pallet still takes a place); null when the
 *                size has no boxes per pallet set
 */
public record FgStockRow(
        Long id,
        Long godownId,
        String godownCode,
        Long pillarId,
        String pillarCode,
        Long designId,
        String designCode,
        String designName,
        String sizeCode,
        Long lotId,
        String batchCode,
        Long gradeId,
        String gradeCode,
        String gradeName,
        int gradeSortOrder,
        String shade,
        String caliber,
        int boxes,
        BigDecimal sqm,
        Integer pallets
) {
    /** Needs pillar, pillar.godown, design, design.tileSize and grade loaded. */
    public static FgStockRow from(FgStock s) {
        var size = s.getDesign().getTileSize();
        var pillar = s.getPillar();
        return new FgStockRow(s.getId(), pillar.getGodown().getId(), pillar.getGodown().getCode(), pillar.getId(),
                pillar.getCode(), s.getDesign().getId(), s.getDesign().getCode(), s.getDesign().getName(), size.getCode(),
                s.getLotId(), s.getBatchCode(), s.getGrade().getId(), s.getGrade().getCode(), s.getGrade().getName(),
                s.getGrade().getSortOrder(), s.getShade(), s.getCaliber(), s.getBoxes(), sqm(s.getBoxes(), size),
                pallets(s.getBoxes(), size));
    }

    /** boxes x sq m per box. */
    public static BigDecimal sqm(long boxes, TileSize size) {
        return size.getSqmPerBox() == null ? null
                : size.getSqmPerBox().multiply(BigDecimal.valueOf(boxes)).setScale(2, RoundingMode.HALF_UP);
    }

    /** Whole pallets needed, or null when the size doesn't say how many boxes make a pallet. */
    public static Integer pallets(int boxes, TileSize size) {
        Integer perPallet = size.getBoxesPerPallet();
        return perPallet == null || perPallet < 1 ? null : (boxes + perPallet - 1) / perPallet;
    }
}
