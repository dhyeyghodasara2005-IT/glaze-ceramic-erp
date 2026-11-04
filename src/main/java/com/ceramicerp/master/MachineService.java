package com.ceramicerp.master;

import com.ceramicerp.common.Text;
import com.ceramicerp.common.exception.BusinessException;
import com.ceramicerp.common.exception.NotFoundException;
import com.ceramicerp.master.dto.MachineLinkResponse;
import com.ceramicerp.master.dto.MachineReplaceRequest;
import com.ceramicerp.master.dto.MachineRequest;
import com.ceramicerp.master.dto.MachineResponse;
import com.ceramicerp.organization.Plant;
import com.ceramicerp.organization.PlantService;
import com.ceramicerp.security.AppUserDetails;
import com.ceramicerp.security.CurrentUser;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Business rules for machines:
 * - you only see / edit machines in plants you have access to
 * - codes are unique within a plant
 * - a new plant choice must be active
 * - the layout (which machine feeds which) stays inside one plant and never loops back on itself
 * - a replaced machine hands its place in the layout, and whatever other modules keep for that place, to the new one
 */
@Service
@RequiredArgsConstructor
public class MachineService {

    /** Added to an old machine's code when its replacement takes the code over. */
    private static final String OLD_SUFFIX = "-OLD";
    private static final int CODE_MAX = 20;

    private final MachineRepository machineRepository;
    private final MachineLinkRepository linkRepository;
    private final PlantService plantService;
    private final ApplicationEventPublisher events;

    @Transactional(readOnly = true)
    public List<MachineResponse> list(Long plantId) {
        AppUserDetails user = CurrentUser.get();
        List<Machine> machines = plantId == null
                ? machineRepository.findAllInProcessOrder()
                : machineRepository.findByPlantIdInProcessOrder(plantId);
        return machines.stream()
                .filter(m -> user.canAccessPlant(m.getPlant().getId()))
                .map(MachineResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public MachineResponse get(Long id) {
        Machine machine = find(id);
        requirePlantAccess(machine.getPlant().getId());
        return MachineResponse.from(machine);
    }

    @Transactional
    public MachineResponse create(MachineRequest req) {
        Plant plant = choosePlant(req.plantId(), null);
        String code = Text.upperOrNull(req.code());
        requireFreeCode(plant, code, null);
        Machine machine = new Machine();
        machine.setPlant(plant);
        apply(machine, req, code);
        return MachineResponse.from(machineRepository.save(machine));
    }

    @Transactional
    public MachineResponse update(Long id, MachineRequest req) {
        Machine machine = find(id);
        requirePlantAccess(machine.getPlant().getId());          // may you touch it where it is now?
        Plant plant = choosePlant(req.plantId(), machine.getPlant()); // ...and where it's moving to?
        String code = Text.upperOrNull(req.code());
        requireFreeCode(plant, code, id);
        // Its arrows point at machines of the old plant; a layout never crosses plants.
        if (!plant.getId().equals(machine.getPlant().getId()) && linkRepository.existsByFromIdOrToId(id, id)) {
            throw new BusinessException(machine.getCode() + " is part of the layout of " + machine.getPlant().getCode()
                    + ". Remove its links before moving it to another plant.");
        }
        machine.setPlant(plant);
        apply(machine, req, code);
        return MachineResponse.from(machine);
    }

    // ------------------------------------------------------------------ layout

    /** Every arrow of one plant's layout. */
    @Transactional(readOnly = true)
    public List<MachineLinkResponse> links(Long plantId) {
        requirePlantAccess(plantId);
        return linkRepository.findByPlantId(plantId).stream().map(MachineLinkResponse::from).toList();
    }

    /**
     * Sets everything this machine feeds into, as a whole list (the ones left out are unlinked).
     * New arrows need both machines active and in the same plant, and may not make a loop.
     */
    @Transactional
    public List<MachineLinkResponse> setFeeds(Long machineId, List<Long> toIds) {
        Machine machine = find(machineId);
        Long plantId = machine.getPlant().getId();
        requirePlantAccess(plantId);

        Set<Long> wanted = new LinkedHashSet<>(toIds);
        List<MachineLink> current = linkRepository.findByFromId(machineId);
        Set<Long> kept = new HashSet<>();
        for (MachineLink link : current) {
            if (wanted.contains(link.getTo().getId())) {
                kept.add(link.getTo().getId());
            } else {
                linkRepository.delete(link);
            }
        }

        // The rest of the plant's arrows, without this machine's old ones: what a new arrow could loop through.
        Map<Long, Set<Long>> next = new HashMap<>();
        for (MachineLink l : linkRepository.findByPlantId(plantId)) {
            if (!l.getFrom().getId().equals(machineId)) {
                next.computeIfAbsent(l.getFrom().getId(), k -> new HashSet<>()).add(l.getTo().getId());
            }
        }
        next.put(machineId, new HashSet<>(wanted));

        for (Long toId : wanted) {
            if (kept.contains(toId)) {
                continue;                                         // already there: no checks, it may be inactive now
            }
            Machine to = find(toId);
            if (to.getId().equals(machineId)) {
                throw new BusinessException("A machine can't feed into itself");
            }
            if (!to.getPlant().getId().equals(plantId)) {
                throw new BusinessException(to.getCode() + " is in another plant");
            }
            if (!machine.isActive() || !to.isActive()) {
                throw new BusinessException((machine.isActive() ? to : machine).getCode() + " is not in use");
            }
            if (reaches(next, toId, machineId)) {
                throw new BusinessException("That would make a loop: " + to.getCode() + " already leads back to "
                        + machine.getCode());
            }
            MachineLink link = new MachineLink();
            link.setPlant(machine.getPlant());
            link.setFrom(machine);
            link.setTo(to);
            linkRepository.save(link);
        }
        return links(plantId);
    }

    /** Can you get from "start" to "goal" by following the arrows? A walk with a to-do list (no recursion). */
    static boolean reaches(Map<Long, Set<Long>> next, Long start, Long goal) {
        Deque<Long> todo = new ArrayDeque<>(List.of(start));
        Set<Long> seen = new HashSet<>();
        while (!todo.isEmpty()) {
            Long at = todo.pop();
            if (at.equals(goal)) {
                return true;
            }
            if (seen.add(at)) {
                todo.addAll(next.getOrDefault(at, Set.of()));
            }
        }
        return false;
    }

    // ------------------------------------------------------------------ replace

    /**
     * Takes a machine out and puts a new one of the same type in its place, in one go:
     * - the new machine gets the old one's arrows in the layout;
     * - other modules move what belongs to the place (meters, preventive tasks) - see MachineReplacedEvent;
     * - the old machine stops being used and points at its replacement. Its past entries, breakdowns and finished
     *   jobs stay with it, because that is the machine that really did the work.
 * Other modules may refuse first (MachineReplacingEvent): production does while a design lot is open on it.
     * The new machine may keep the old code: the old one is renamed PRESS-1-OLD (PRESS-1-OLD2 ... if taken).
     */
    @Transactional
    public MachineResponse replace(Long oldId, MachineReplaceRequest req) {
        Machine old = find(oldId);
        Plant plant = old.getPlant();
        requirePlantAccess(plant.getId());
        if (!old.isActive()) {
            throw new BusinessException(old.getCode() + " is not in use, so there is nothing to replace");
        }
        String oldCode = old.getCode();
        // Ask first: a module that can't let the machine go throws here, before anything has changed.
        events.publishEvent(new MachineReplacingEvent(plant.getId(), oldId, oldCode));
        String code = Text.upperOrNull(req.code());
        if (code.equalsIgnoreCase(oldCode)) {
            old.setCode(freeOldCode(plant, oldCode));
            machineRepository.flush();                            // free the code in the database before reusing it
        } else {
            requireFreeCode(plant, code, null);
        }

        Machine fresh = new Machine();
        fresh.setPlant(plant);
        fresh.setType(old.getType());
        fresh.setCode(code);
        fresh.setName(req.name().trim());
        fresh.setCapacity(req.capacity());
        fresh.setCapacityUnit(Text.trimToNull(req.capacityUnit()));
        fresh.setMake(Text.trimToNull(req.make()));
        fresh.setModel(Text.trimToNull(req.model()));
        fresh.setInstalledOn(req.installedOn());
        machineRepository.save(fresh);

        for (MachineLink link : linkRepository.findTouching(oldId)) {
            if (link.getFrom().getId().equals(oldId)) {
                link.setFrom(fresh);
            }
            if (link.getTo().getId().equals(oldId)) {
                link.setTo(fresh);
            }
        }
        old.setActive(false);
        old.setReplacedBy(fresh);
        machineRepository.flush();
        // ...then tell: the other modules move what belongs to this place in the plant (same transaction).
        events.publishEvent(new MachineReplacedEvent(plant.getId(), oldId, fresh.getId(), oldCode));
        return MachineResponse.from(fresh);
    }

    /** PRESS-1 -> PRESS-1-OLD, or PRESS-1-OLD2, -OLD3 ... when taken. Cut short to fit 20 characters. */
    private String freeOldCode(Plant plant, String code) {
        for (int n = 1; ; n++) {
            String suffix = OLD_SUFFIX + (n == 1 ? "" : n);
            String base = code.length() + suffix.length() > CODE_MAX ? code.substring(0, CODE_MAX - suffix.length()) : code;
            String candidate = base + suffix;
            if (!machineRepository.existsByPlantIdAndCodeIgnoreCase(plant.getId(), candidate)) {
                return candidate;
            }
        }
    }

    // ------------------------------------------------------------------ helpers

    private Machine find(Long id) {
        return machineRepository.findWithPlantById(id).orElseThrow(() -> new NotFoundException("Machine", id));
    }

    private void requireFreeCode(Plant plant, String code, Long exceptId) {
        boolean taken = exceptId == null
                ? machineRepository.existsByPlantIdAndCodeIgnoreCase(plant.getId(), code)
                : machineRepository.existsByPlantIdAndCodeIgnoreCaseAndIdNot(plant.getId(), code, exceptId);
        if (taken) {
            throw new BusinessException("Machine code " + code + " already exists in plant " + plant.getCode());
        }
    }

    /** Loads the plant, checks the user may use it, and (if it changed) that it's active. */
    private Plant choosePlant(Long plantId, Plant current) {
        Plant plant = plantService.find(plantId);
        requirePlantAccess(plant.getId());
        MasterRules.requireActiveIfChanged(plant.isActive(), current, plant, "Plant", plant.getCode());
        return plant;
    }

    private static void requirePlantAccess(Long plantId) {
        if (!CurrentUser.get().canAccessPlant(plantId)) {
            throw new AccessDeniedException("No access to plant " + plantId);
        }
    }

    private void apply(Machine m, MachineRequest req, String code) {
        m.setCode(code);
        m.setName(req.name().trim());
        m.setType(req.type());
        m.setCapacity(req.capacity());
        m.setCapacityUnit(Text.trimToNull(req.capacityUnit()));
        m.setMake(Text.trimToNull(req.make()));
        m.setModel(Text.trimToNull(req.model()));
        m.setInstalledOn(req.installedOn());
        if (req.active() != null) {
            m.setActive(req.active());
        }
    }
}
