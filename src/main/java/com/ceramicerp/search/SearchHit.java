package com.ceramicerp.search;

/**
 * One result in the global search, e.g. type="design", code="MARBLE-01", title="Statuario Marble",
 * subtitle="GVT · 600X1200 · Glossy". The frontend uses {@code type} to pick the icon and the page to open.
 */
public record SearchHit(
        String type,
        Long id,
        String code,
        String title,
        String subtitle,
        boolean active
) {
}
