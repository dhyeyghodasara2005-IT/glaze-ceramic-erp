package com.ceramicerp.master;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Walks a plant's layout (the "feeds into" arrows of the Machines screen) - the server-side twin of the
 * frontend's machines/layout.ts. Production asks it which presses feed a printer, to keep a lot's trace on its own
 * line in a plant with two lines.
 */
@Component
@RequiredArgsConstructor
public class PlantLayout {

    private final MachineLinkRepository linkRepository;
    private final MachineRepository machineRepository;

    /**
     * The machines of one type you reach from a machine going AGAINST the arrows (where its tiles came from),
     * through anything in between (dryer, glaze line, compensator), stopping at the first one of that type on each
     * path. Empty when the machine isn't in the layout, or nothing of that type is up the line.
     * <p>
     * Machines that were replaced by one of those count too (PRESS-1-OLD for PRESS-1): the arrows moved to the new
     * press, but the old one's entries are still behind the lots of its time. And a replaced starting machine
     * (an old printer) is walked from its replacement, which now holds its place in the layout.
     */
    public Set<Long> upstream(Long plantId, Long fromId, MachineType type) {
        List<MachineLink> links = linkRepository.findByPlantId(plantId);
        if (links.isEmpty()) {
            return Set.of();
        }
        Map<Long, Set<Long>> back = new HashMap<>();
        for (MachineLink l : links) {
            back.computeIfAbsent(l.getTo().getId(), k -> new HashSet<>()).add(l.getFrom().getId());
        }
        Map<Long, MachineType> types = new HashMap<>();
        Map<Long, Long> replacedBy = new HashMap<>();
        Map<Long, Set<Long>> predecessors = new HashMap<>();   // new machine -> the ones it replaced
        for (Machine m : machineRepository.findByPlantId(plantId)) {
            types.put(m.getId(), m.getType());
            if (m.getReplacedBy() != null) {
                replacedBy.put(m.getId(), m.getReplacedBy().getId());
                predecessors.computeIfAbsent(m.getReplacedBy().getId(), k -> new HashSet<>()).add(m.getId());
            }
        }
        Long start = fromId;
        for (int hops = 0; replacedBy.containsKey(start) && hops < 50; hops++) {
            start = replacedBy.get(start);                     // the machine standing in its place today
        }

        Set<Long> found = new HashSet<>();
        Set<Long> seen = new HashSet<>(Set.of(start));
        Deque<Long> todo = new ArrayDeque<>(back.getOrDefault(start, Set.of()));
        while (!todo.isEmpty()) {
            Long at = todo.pop();
            if (!seen.add(at)) {
                continue;
            }
            if (types.get(at) == type) {
                found.add(at);                                   // stop on this path
            } else {
                todo.addAll(back.getOrDefault(at, Set.of()));
            }
        }
        // ...and every machine those replaced, however many times over.
        Deque<Long> older = new ArrayDeque<>(found);
        while (!older.isEmpty()) {
            for (Long old : predecessors.getOrDefault(older.pop(), Set.of())) {
                if (found.add(old)) {
                    older.push(old);
                }
            }
        }
        return found;
    }
}
