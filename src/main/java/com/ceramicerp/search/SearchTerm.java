package com.ceramicerp.search;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.function.Function;

/**
 * What the user typed, prepared for searching.
 *
 * @param text     trimmed text as typed, e.g. "600"
 * @param lower    lower-case, e.g. "600"
 * @param contains SQL LIKE pattern, e.g. "%600%" (with % and _ in the input escaped,
 *                 so typing "_" searches for an underscore instead of "any character")
 */
public record SearchTerm(String text, String lower, String contains) {

    public static SearchTerm of(String q) {
        String text = q.trim();
        String lower = text.toLowerCase(Locale.ROOT);
        String escaped = lower.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
        return new SearchTerm(text, lower, "%" + escaped + "%");
    }

    /**
     * How good a match is - lower is better:
     * 0 exact code, 1 code starts with it, 2 name starts with it, 3 found somewhere inside.
     */
    public int rank(String code, String name) {
        String c = code == null ? "" : code.toLowerCase(Locale.ROOT);
        String n = name == null ? "" : name.toLowerCase(Locale.ROOT);
        if (c.equals(lower)) return 0;
        if (c.startsWith(lower)) return 1;
        if (n.startsWith(lower)) return 2;
        return 3;
    }

    /** Sorts {@code items} best-match first (keeping their order on ties) and keeps the top {@code limit}. */
    public <T> List<T> best(List<T> items, int limit, Function<T, String> code, Function<T, String> name) {
        return items.stream()
                .sorted(Comparator.comparingInt(item -> rank(code.apply(item), name.apply(item))))
                .limit(limit)
                .toList();
    }
}
