package com.ceramicerp.fg;

/** What made a finished-goods stock movement. Step 5b adds opening stock, breakage and regrading; step 6 dispatch. */
public enum FgSource {
    /** A posted pack entry (in), or its cancellation (out). */
    PACK
}
