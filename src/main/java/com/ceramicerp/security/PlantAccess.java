package com.ceramicerp.security;

import org.springframework.security.access.AccessDeniedException;

/**
 * Store and production records belong to a plant. This is the one check they all use:
 * "may the logged-in user work in this plant?"
 */
public final class PlantAccess {

    private PlantAccess() {
    }

    /** Throws 403 unless the logged-in user may work in this plant. */
    public static void require(Long plantId) {
        if (!CurrentUser.get().canAccessPlant(plantId)) {
            throw new AccessDeniedException("No access to plant " + plantId);
        }
    }
}
