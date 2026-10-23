package com.ceramicerp.fg.dto;

/**
 * A pillar for the pack entry's dropdown, with how full it is, so the packing supervisor can choose
 * "by colour and stock".
 *
 * @param mainDesign the design with most boxes there (empty pillar = null)
 */
public record PillarOption(Long id, String code, Long godownId, String godownCode, Integer capacityPallets, int boxes,
                           int pallets, boolean palletsUnknown, String mainDesign, String mainShade) {
}
