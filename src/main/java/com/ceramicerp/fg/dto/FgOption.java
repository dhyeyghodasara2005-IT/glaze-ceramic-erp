package com.ceramicerp.fg.dto;

/** A design or a grade for the warehouse's dropdowns (the warehouse keeper may not see the masters). */
public record FgOption(Long id, String code, String name, String extra) {
}
