package com.ceramicerp.fg.dto;

import com.ceramicerp.fg.PillarKind;
import com.ceramicerp.fg.PillarZone;

import java.util.List;

/**
 * A pillar for the pack entry's dropdown, with how full it is, so the packing supervisor can choose
 * "by colour and stock".
 *
 * @param mainDesign the design with most boxes there (empty pillar = null)
 * @param stock      everything standing there: the screen warns when a row would mix with it
 */
public record PillarOption(Long id, String code, Long godownId, String godownCode, PillarZone zone, Integer walkOrder,
                           PillarKind kind, Integer capacityPallets, int boxes, int pallets, boolean palletsUnknown,
                           String mainDesign, String mainShade, List<FgStockRow> stock) {
}
