package com.ceramicerp.production;

import com.ceramicerp.common.BaseEntity;
import com.ceramicerp.common.Text;
import com.ceramicerp.common.exception.BusinessException;
import com.ceramicerp.common.exception.NotFoundException;
import com.ceramicerp.master.Machine;
import com.ceramicerp.master.MachineRepository;
import com.ceramicerp.master.MachineType;
import com.ceramicerp.organization.Plant;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Locale;

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
}
