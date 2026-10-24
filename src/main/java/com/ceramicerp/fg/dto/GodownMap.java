package com.ceramicerp.fg.dto;

import com.ceramicerp.fg.PillarKind;
import com.ceramicerp.fg.PillarZone;

import java.util.List;

/** A godown's floor as a grid of pillars, each with what stands under it. */
public record GodownMap(FgGodownResponse godown, List<Pillar> pillars) {

    /**
     * @param pallets        pallets standing there, where the size says how many boxes make one
     * @param palletsUnknown true when some boxes there are of a size without "boxes per pallet"
     * @param stock          what stands there, most boxes first
     */
    public record Pillar(Long id, String code, PillarZone zone, Integer walkOrder, PillarKind kind,
                         Integer capacityPallets, boolean active, int boxes, int pallets, boolean palletsUnknown,
                         List<FgStockRow> stock) {
    }
}
