package com.ceramicerp.sales;

/** How the order came in. */
public enum OrderSource {
    /** Typed from the slip. */
    TYPED,
    /** Read from the slip's PDF (the reader comes once real slip files are shared). */
    PDF
}
