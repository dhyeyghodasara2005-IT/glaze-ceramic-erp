package com.ceramicerp.sales;

import com.ceramicerp.master.Design;
import com.ceramicerp.master.DesignRepository;
import com.ceramicerp.master.Grade;
import com.ceramicerp.master.GradeRepository;
import com.ceramicerp.sales.dto.ProductMatch;
import com.ceramicerp.sales.dto.SalesOption;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Reads a slip's product description, e.g. {@code O7VM 600X1200-2 FLAVOUR IVORY PDR-P}, as a design and a grade.
 * <ol>
 *   <li>REMEMBERED: a person confirmed this exact description before (product_aliases).</li>
 *   <li>GUESSED: the design whose name (or code) appears in it as whole words - the longest wins, then the one whose
 *       size is in it too; the grade from the ending: {@code -P} = the grade whose code starts with P (Premium).</li>
 * </ol>
 * Rules, not AI: anyone can see why a line was matched, and a wrong guess is corrected once and then remembered.
 */
@Service
@RequiredArgsConstructor
public class ProductMatcher {

    /** The grade ending: "...PDR-P" -> "P". Letters only, so "600X1200-2" is not read as a grade. */
    private static final Pattern GRADE_ENDING = Pattern.compile("-([A-Z]{1,3})$");

    private final ProductAliasRepository aliasRepository;
    private final DesignRepository designRepository;
    private final GradeRepository gradeRepository;

    /** The description tidied, so small differences in typing still find the remembered match. */
    public static String key(String description) {
        return description.trim().toUpperCase(Locale.ROOT)
                .replaceAll("\\s+", " ")
                .replaceAll(" ?- ?", "-");
    }

    /** One result per description, in the same order. */
    @Transactional(readOnly = true)
    public List<ProductMatch> match(List<String> descriptions) {
        List<String> keys = descriptions.stream().map(d -> d == null ? "" : key(d)).toList();
        Map<String, ProductAlias> remembered = aliasRepository.findByNameKeyIn(keys.stream().distinct().toList()).stream()
                .collect(Collectors.toMap(ProductAlias::getNameKey, Function.identity()));
        Known known = new Known(designRepository.findByActiveTrueOrderByCodeAsc(),
                gradeRepository.findAllByOrderBySortOrderAscNameAsc().stream()
                        .filter(g -> g.isActive() && g.isSellable()).toList());

        List<ProductMatch> results = new ArrayList<>();
        for (int i = 0; i < keys.size(); i++) {
            String description = descriptions.get(i);
            String key = keys.get(i);
            ProductAlias alias = remembered.get(key);
            // a remembered design or grade that has been closed since is not offered: guess again instead
            if (alias != null && alias.getDesign().isActive() && alias.getGrade().isActive()) {
                results.add(new ProductMatch(description, ProductMatch.How.REMEMBERED, option(alias.getDesign()),
                        option(alias.getGrade()), List.of()));
            } else {
                results.add(guess(description, key, known));
            }
        }
        return results;
    }

    /** Active designs and sellable grades, loaded once for all the lines of a slip. */
    private record Known(List<Design> designs, List<Grade> grades) {
    }

    private static ProductMatch guess(String description, String key, Known known) {
        List<String> words = words(key);
        SalesOption grade = gradeOf(key, words, known.grades());
        if (words.isEmpty()) {
            return new ProductMatch(description, ProductMatch.How.NONE, null, grade, List.of());
        }

        // The design's name in the description, as whole words; if no name fits, its code.
        List<Design> fits = longest(known.designs(), d -> words(key(d.getName())), words);
        if (fits.isEmpty()) {
            fits = longest(known.designs(), d -> words(key(d.getCode())), words);
        }
        if (fits.size() > 1) {
            // the same name in two sizes: the size printed in the description decides
            List<Design> sized = fits.stream()
                    .filter(d -> contains(words, words(key(d.getTileSize().getCode()))))
                    .toList();
            if (!sized.isEmpty()) {
                fits = sized;
            }
        }
        if (fits.isEmpty()) {
            return new ProductMatch(description, ProductMatch.How.NONE, null, grade, List.of());
        }
        if (fits.size() > 1) {
            return new ProductMatch(description, ProductMatch.How.SEVERAL, null, grade,
                    fits.stream().map(ProductMatcher::option).toList());
        }
        return new ProductMatch(description, ProductMatch.How.GUESSED, option(fits.get(0)), grade, List.of());
    }

    /** The designs whose words appear in the description, keeping only those with the most words (the most specific). */
    private static List<Design> longest(Collection<Design> designs, Function<Design, List<String>> wordsOf,
                                        List<String> words) {
        int best = 0;
        List<Design> found = new ArrayList<>();
        for (Design d : designs) {
            List<String> name = wordsOf.apply(d);
            if (name.isEmpty() || !contains(words, name)) {
                continue;
            }
            if (name.size() > best) {
                best = name.size();
                found.clear();
            }
            if (name.size() == best) {
                found.add(d);
            }
        }
        return found;
    }

    /**
     * The grade from the ending ("-P" -> PREMIUM), else a grade's code or name written out ("... PREMIUM").
     * Only when exactly one sellable grade fits.
     */
    private static SalesOption gradeOf(String key, List<String> words, List<Grade> grades) {
        Matcher ending = GRADE_ENDING.matcher(key);
        if (ending.find()) {
            String letters = ending.group(1);
            List<Grade> fit = grades.stream()
                    .filter(g -> g.getCode().toUpperCase(Locale.ROOT).startsWith(letters))
                    .toList();
            if (fit.size() == 1) {
                return option(fit.get(0));
            }
        }
        List<Grade> named = grades.stream()
                .filter(g -> words.contains(g.getCode().toUpperCase(Locale.ROOT))
                        || contains(words, words(key(g.getName()))))
                .toList();
        return named.size() == 1 ? option(named.get(0)) : null;
    }

    /** "O7VM 600X1200-2 FLAVOUR IVORY PDR-P" -> [O7VM, 600X1200, 2, FLAVOUR, IVORY, PDR, P] */
    private static List<String> words(String key) {
        return Arrays.stream(key.split("[^A-Z0-9]+")).filter(w -> !w.isEmpty()).toList();
    }

    /** Is {@code part} found in {@code words} as a run of whole words? */
    private static boolean contains(List<String> words, List<String> part) {
        return !part.isEmpty() && Collections.indexOfSubList(words, part) >= 0;
    }

    static SalesOption option(Design d) {
        return new SalesOption(d.getId(), d.getCode(), d.getName(), d.getTileSize().getCode());
    }

    static SalesOption option(Grade g) {
        return new SalesOption(g.getId(), g.getCode(), g.getName(), null);
    }
}
