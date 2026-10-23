package com.ceramicerp.production;

import com.ceramicerp.common.BaseEntity;
import com.ceramicerp.common.Text;
import com.ceramicerp.common.exception.BusinessException;
import com.ceramicerp.common.exception.NotFoundException;
import com.ceramicerp.master.Grade;
import com.ceramicerp.master.GradeRepository;
import com.ceramicerp.master.Machine;
import com.ceramicerp.master.MachineRepository;
import com.ceramicerp.master.MachineType;
import com.ceramicerp.master.TileSize;
import com.ceramicerp.organization.Plant;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Locale;
import java.util.Optional;

/** Checks shared by every production entry. */
final class ProductionRules {

    /** The plants are in India: "now" on the shop floor is Indian time, whatever zone the server runs in. */
    static final ZoneId PLANT_ZONE = ZoneId.of("Asia/Kolkata");

    private ProductionRules() {
    }

    static LocalDateTime now() {
        return LocalDateTime.now(PLANT_ZONE);
    }

    static void requireNotFuture(LocalDate prodDate) {
        if (prodDate.isAfter(now().toLocalDate())) {
            throw new BusinessException("The production date can't be in the future");
        }
    }

    /** A few minutes of slack, because phone clocks are never exactly right. */
    static void requireNotFuture(LocalDateTime time, String what) {
        if (time.isAfter(now().plusMinutes(10))) {
            throw new BusinessException(what + " can't be in the future");
        }
    }

    static void requireOrder(LocalDateTime start, LocalDateTime end, String what) {
        if (end != null && end.isBefore(start)) {
            throw new BusinessException(what + " can't be before the start");
        }
    }

    /** The chosen machine must be in this plant, of the right kind, and active (unless already chosen). */
    static Machine requireMachine(MachineRepository repo, Long id, Plant plant, MachineType type, Machine current) {
        Machine m = repo.findWithPlantById(id).orElseThrow(() -> new NotFoundException("Machine", id));
        if (!m.getPlant().getId().equals(plant.getId())) {
            throw new BusinessException("Machine " + m.getCode() + " is not in plant " + plant.getCode());
        }
        if (m.getType() != type) {
            throw new BusinessException(m.getCode() + " is a " + label(m.getType()) + ", not a " + label(type));
        }
        requireActiveIfChanged(m.isActive(), current, m, "Machine " + m.getCode());
        return m;
    }

    /** Same rule as the masters: an inactive record may stay, but can't be newly picked. */
    static void requireActiveIfChanged(boolean active, BaseEntity current, BaseEntity chosen, String what) {
        boolean unchanged = current != null && current.getId().equals(chosen.getId());
        if (!active && !unchanged) {
            throw new BusinessException(what + " is inactive. Choose an active one.");
        }
    }

    /** BALL_MILL -> "ball mill" */
    static String label(MachineType type) {
        return type.name().toLowerCase(Locale.ROOT).replace('_', ' ');
    }

    static String pattern(String search) {
        return Text.trimToNull(search) == null ? null : "%" + search.trim().toLowerCase(Locale.ROOT) + "%";
    }

    /** Newest first. */
    static Pageable page(int page, int size) {
        return PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100),
                Sort.by(Sort.Order.desc("prodDate"), Sort.Order.desc("id")));
    }

    /**
     * Square metres of one tile: 600 x 1200 mm -> 0.72. Falls back to sq m per box / pieces per box
     * for a size entered without length and width. null if neither is known.
     */
    static BigDecimal sqmPerTile(TileSize size) {
        if (size.getLengthMm() != null && size.getWidthMm() != null) {
            return BigDecimal.valueOf((long) size.getLengthMm() * size.getWidthMm())
                    .divide(BigDecimal.valueOf(1_000_000), 4, RoundingMode.HALF_UP);
        }
        if (size.getSqmPerBox() != null && size.getPcsPerBox() != null && size.getPcsPerBox() > 0) {
            return size.getSqmPerBox().divide(BigDecimal.valueOf(size.getPcsPerBox()), 4, RoundingMode.HALF_UP);
        }
        return null;
    }

    /** tiles x sq m per tile, or null when the size is unknown. */
    static BigDecimal sqm(long tiles, BigDecimal sqmPerTile) {
        return sqmPerTile == null ? null : sqmPerTile.multiply(BigDecimal.valueOf(tiles)).setScale(2, RoundingMode.HALF_UP);
    }

    /** part / whole x 100 with 2 decimals, or null when the whole is 0. */
    static BigDecimal pct(long part, long whole) {
        return whole == 0 ? null
                : BigDecimal.valueOf(part * 100).divide(BigDecimal.valueOf(whole), 2, RoundingMode.HALF_UP);
    }

    /** The best grade a tile can be sold as: the active, sellable grade with the lowest sort order (Premium). */
    static Optional<Grade> topGrade(GradeRepository grades) {
        return grades.findAllByOrderBySortOrderAscNameAsc().stream()
                .filter(g -> g.isActive() && g.isSellable())
                .findFirst();
    }
}
