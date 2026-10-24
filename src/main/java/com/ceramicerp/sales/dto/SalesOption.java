package com.ceramicerp.sales.dto;

/** A design, grade or transporter for the sales screens' dropdowns (the dispatch office may not see the masters). */
public record SalesOption(Long id, String code, String name, String extra) {
}
