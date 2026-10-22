package com.ceramicerp.party;

/** GST status of a party - decides what the invoice looks like and which return it goes into. */
public enum GstRegistration {
    /** Normal GST-registered business (B2B invoice, GSTIN required). */
    REGULAR,
    /** Composition scheme dealer (GSTIN required, can't claim input credit). */
    COMPOSITION,
    /** Business without GST registration. */
    UNREGISTERED,
    /** End customer / individual (B2C). */
    CONSUMER,
    /** Outside India (export). No GSTIN, no Indian state. */
    OVERSEAS;

    public boolean requiresGstin() {
        return this == REGULAR || this == COMPOSITION;
    }
}
