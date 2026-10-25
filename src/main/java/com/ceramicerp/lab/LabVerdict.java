package com.ceramicerp.lab;

/** What the lab decided about a sample. */
public enum LabVerdict {
    ACCEPTED,
    /** The material goes back: its lines on the GRN are rejected in full and none of it enters stock. */
    REJECTED
}
