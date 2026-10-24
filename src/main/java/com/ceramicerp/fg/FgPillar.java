package com.ceramicerp.fg;

import com.ceramicerp.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.Comparator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A pillar in a finished-goods godown, as painted on it: A1, A2, B1 ... Pallets stand in rows under it,
 * stacked up to 3 or 4 high. capacityPallets is how many fit (optional).
 */
@Getter
@Setter
@Entity
@Table(name = "fg_pillars")
public class FgPillar extends BaseEntity {

    /** A2 before A10, as people read them (plain text sorting would put A10 first). */
    public static final Comparator<String> CODE_ORDER = Comparator
            .comparing((String c) -> part(c, 1))
            .thenComparingInt(c -> part(c, 2).isEmpty() ? 0 : Integer.parseInt(part(c, 2)))
            .thenComparing(c -> part(c, 3));

    private static final Pattern PARTS = Pattern.compile("([A-Z]*)(\\d{0,6})(.*)");

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "godown_id", nullable = false)
    private FgGodown godown;

    @Column(nullable = false, length = 10)
    private String code;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 10)
    private PillarZone zone = PillarZone.NORMAL;

    /** Distance from the loading point in pillars: 1 = right next to it. Empty = not set yet. */
    private Integer walkOrder;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 20)
    private PillarKind kind = PillarKind.STORAGE;

    private Integer capacityPallets;

    @Column(nullable = false)
    private boolean active = true;

    /** The row letters of a code: "A12" -> "A". */
    public static String rowOf(String code) {
        return part(code, 1);
    }

    /** The number of a code: "A12" -> 12, or -1 when there is none. */
    public static int numberOf(String code) {
        String n = part(code, 2);
        return n.isEmpty() ? -1 : Integer.parseInt(n);
    }

    /** "A12" -> group 1 "A", group 2 "12", group 3 "" */
    private static String part(String code, int group) {
        Matcher m = PARTS.matcher(code);
        return m.matches() ? m.group(group) : code;
    }
}
