package com.ceramicerp.sales.dto;

import java.util.List;

/**
 * What Glaze understood from one product description on a slip.
 *
 * @param how        REMEMBERED (a person confirmed this description before), GUESSED (read from the design's name
 *                   and the grade ending), SEVERAL (more than one design fits: a person must choose) or NONE
 * @param candidates SEVERAL: the designs that fit equally well
 */
public record ProductMatch(String description, How how, SalesOption design, SalesOption grade,
                           List<SalesOption> candidates) {

    public enum How { REMEMBERED, GUESSED, SEVERAL, NONE }
}
