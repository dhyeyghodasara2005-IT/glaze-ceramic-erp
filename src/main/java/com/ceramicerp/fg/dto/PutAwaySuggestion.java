package com.ceramicerp.fg.dto;

import com.ceramicerp.fg.PillarZone;

/**
 * One suggested pillar for new pallets, best first.
 *
 * @param reason      why this pillar (the screen turns it into a sentence)
 * @param freePallets pallets that still fit there; null when its capacity or the size's pallet is not known
 */
public record PutAwaySuggestion(Long pillarId, String code, String godownCode, PillarZone zone, Integer walkOrder,
                                Reason reason, Integer freePallets) {

    public enum Reason {
        /** The same batch, grade, shade and caliber already stands there, and there is room. */
        SAME_BOX,
        /** Less than a pallet: a remainder pillar near the loading point. */
        REMAINDER,
        /** An empty pillar right next to this design's other pillars. */
        NEXT_TO_DESIGN,
        /** An empty pillar in the zone this design belongs in. */
        EMPTY_IN_ZONE,
        /** An empty pillar elsewhere (the right zone is full). */
        EMPTY_ELSEWHERE
    }
}
