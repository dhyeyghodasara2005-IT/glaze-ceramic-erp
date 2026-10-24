package com.ceramicerp.sales.dto;

import com.ceramicerp.fg.PillarZone;

/**
 * A stock row the loader may take a line's boxes from instead.
 *
 * @param free boxes not kept for other orders (what this order already takes from it counts as free)
 */
public record PickOption(Long stockId, String godownCode, String pillarCode, PillarZone zone, Integer walkOrder,
                         String batchCode, String shade, String caliber, int boxes, int free) {
}
