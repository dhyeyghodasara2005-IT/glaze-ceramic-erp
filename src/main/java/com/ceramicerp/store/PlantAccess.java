package com.ceramicerp.store;

import com.ceramicerp.security.CurrentUser;
import org.springframework.security.access.AccessDeniedException;

/** Every store record belongs to a plant; this is the one check used everywhere in the store module. */
final class PlantAccess {

    private PlantAccess() {
    }

    /** Throws 403 unless the logged-in user may work in this plant. */
    static void require(Long plantId) {
        if (!CurrentUser.get().canAccessPlant(plantId)) {
            throw new AccessDeniedException("No access to plant " + plantId);
        }
    }
}
