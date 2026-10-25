package com.ceramicerp.store;

/**
 * Something another module checks on a GRN just before it is posted. The lab (step 8d) makes sure every material
 * that needs a test has one, and rejects in full the lines of a material it rejected.
 * <p>
 * The store doesn't know who checks: Spring hands it every bean that implements this. So the store needs nothing
 * from the lab, and the lab can be built on top of the store without the two depending on each other.
 */
public interface GrnCheck {

    /** Throw a BusinessException to stop the posting; may change the lines' rejected and accepted quantities. */
    void beforePost(StoreDocument grn);
}
