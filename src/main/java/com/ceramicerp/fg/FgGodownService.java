package com.ceramicerp.fg;

import com.ceramicerp.common.Text;
import com.ceramicerp.common.exception.BusinessException;
import com.ceramicerp.common.exception.NotFoundException;
import com.ceramicerp.fg.dto.FgGodownRequest;
import com.ceramicerp.fg.dto.FgGodownResponse;
import com.ceramicerp.fg.dto.FgStockRow;
import com.ceramicerp.fg.dto.GodownMap;
import com.ceramicerp.fg.dto.PillarBulkRequest;
import com.ceramicerp.fg.dto.PillarOption;
import com.ceramicerp.fg.dto.PillarUpdateRequest;
import com.ceramicerp.organization.Plant;
import com.ceramicerp.organization.PlantService;
import com.ceramicerp.security.PlantAccess;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/** Finished-goods godowns, their pillars, and the godown map (what stands under each pillar). */
@Service
@RequiredArgsConstructor
public class FgGodownService {

    /** Most pillars one "Add pillars" can make: enough for a whole row, small enough to catch a typo like 1 to 1000. */
    private static final int MAX_PILLARS_AT_ONCE = 200;

    private final FgGodownRepository godownRepository;
    private final FgPillarRepository pillarRepository;
    private final FgStockRepository stockRepository;
    private final PlantService plantService;

    @Transactional(readOnly = true)
    public List<FgGodownResponse> list(Long plantId) {
        PlantAccess.require(plantId);
        Map<Long, Long> pillars = pillarRepository.findByGodownPlantId(plantId).stream()
                .collect(Collectors.groupingBy(p -> p.getGodown().getId(), Collectors.counting()));
        Map<Long, Long> boxes = stockRepository.findByPlantIdAndBoxesGreaterThan(plantId, 0).stream()
                .collect(Collectors.groupingBy(s -> s.getPillar().getGodown().getId(), Collectors.summingLong(FgStock::getBoxes)));
        return godownRepository.findByPlantIdOrderByCodeAsc(plantId).stream()
                .map(g -> FgGodownResponse.from(g, pillars.getOrDefault(g.getId(), 0L), boxes.getOrDefault(g.getId(), 0L)))
                .toList();
    }

    @Transactional
    public FgGodownResponse create(FgGodownRequest req) {
        Plant plant = plantService.find(req.plantId());
        PlantAccess.require(plant.getId());
        String code = Text.upperOrNull(req.code());
        if (godownRepository.existsByPlantIdAndCodeIgnoreCase(plant.getId(), code)) {
            throw new BusinessException("Godown code " + code + " already exists in plant " + plant.getCode());
        }
        FgGodown godown = new FgGodown();
        godown.setPlant(plant);
        apply(godown, req, code);
        return FgGodownResponse.from(godownRepository.save(godown), 0, 0);
    }

    @Transactional
    public FgGodownResponse update(Long id, FgGodownRequest req) {
        FgGodown godown = find(id);
        if (!godown.getPlant().getId().equals(req.plantId())) {
            throw new BusinessException("A godown can't be moved to another plant");
        }
        String code = Text.upperOrNull(req.code());
        if (godownRepository.existsByPlantIdAndCodeIgnoreCaseAndIdNot(godown.getPlant().getId(), code, id)) {
            throw new BusinessException("Godown code " + code + " already exists in this plant");
        }
        apply(godown, req, code);
        return map(id).godown();
    }

    /** "Add pillars A1 to A20". Codes that already exist are skipped, so it is safe to run twice. */
    @Transactional
    public GodownMap addPillars(Long godownId, PillarBulkRequest req) {
        FgGodown godown = find(godownId);
        if (req.to() < req.from()) {
            throw new BusinessException("The last number can't be smaller than the first");
        }
        if (req.to() - req.from() + 1 > MAX_PILLARS_AT_ONCE) {
            throw new BusinessException("At most " + MAX_PILLARS_AT_ONCE + " pillars at a time");
        }
        String prefix = req.prefix().trim().toUpperCase(Locale.ROOT);
        Set<String> existing = new HashSet<>();
        pillarRepository.findByGodownId(godownId).forEach(p -> existing.add(p.getCode()));
        for (int n = req.from(); n <= req.to(); n++) {
            String code = prefix + n;
            if (existing.contains(code)) {
                continue;
            }
            FgPillar pillar = new FgPillar();
            pillar.setGodown(godown);
            pillar.setCode(code);
            pillar.setCapacityPallets(req.capacityPallets());
            pillarRepository.save(pillar);
        }
        return map(godownId);
    }

    @Transactional
    public GodownMap updatePillar(Long pillarId, PillarUpdateRequest req) {
        FgPillar pillar = pillarRepository.findWithGodownById(pillarId).orElseThrow(() -> new NotFoundException("Pillar", pillarId));
        PlantAccess.require(pillar.getGodown().getPlant().getId());
        if (!req.active() && pillar.isActive() && stockRepository.existsByPillarIdAndBoxesGreaterThan(pillarId, 0)) {
            throw new BusinessException("Pillar " + pillar.getCode() + " still has boxes. It can be closed once it is empty.");
        }
        pillar.setCapacityPallets(req.capacityPallets());
        pillar.setActive(req.active());
        return map(pillar.getGodown().getId());
    }

    @Transactional(readOnly = true)
    public GodownMap map(Long godownId) {
        FgGodown godown = find(godownId);
        Map<Long, List<FgStockRow>> byPillar = new HashMap<>();
        for (FgStock s : stockRepository.findByPillarGodownIdAndBoxesGreaterThan(godownId, 0)) {
            byPillar.computeIfAbsent(s.getPillar().getId(), k -> new ArrayList<>()).add(FgStockRow.from(s));
        }
        List<FgPillar> pillars = new ArrayList<>(pillarRepository.findByGodownId(godownId));
        pillars.sort(Comparator.comparing(FgPillar::getCode, FgPillar.CODE_ORDER));

        List<GodownMap.Pillar> cells = pillars.stream().map(p -> {
            List<FgStockRow> rows = new ArrayList<>(byPillar.getOrDefault(p.getId(), List.of()));
            rows.sort(Comparator.comparingInt(FgStockRow::boxes).reversed());
            return new GodownMap.Pillar(p.getId(), p.getCode(), p.getCapacityPallets(), p.isActive(),
                    rows.stream().mapToInt(FgStockRow::boxes).sum(), pallets(rows), palletsUnknown(rows), rows);
        }).toList();
        long boxes = cells.stream().mapToLong(GodownMap.Pillar::boxes).sum();
        return new GodownMap(FgGodownResponse.from(godown, pillars.size(), boxes), cells);
    }

    /** Active pillars of active godowns, with how full each is: the pack entry's dropdown. */
    @Transactional(readOnly = true)
    public List<PillarOption> pillarOptions(Long plantId) {
        PlantAccess.require(plantId);
        Map<Long, List<FgStockRow>> byPillar = new HashMap<>();
        for (FgStock s : stockRepository.findByPlantIdAndBoxesGreaterThan(plantId, 0)) {
            byPillar.computeIfAbsent(s.getPillar().getId(), k -> new ArrayList<>()).add(FgStockRow.from(s));
        }
        return pillarRepository.findByGodownPlantId(plantId).stream()
                .filter(p -> p.isActive() && p.getGodown().isActive())
                .sorted(Comparator.comparing((FgPillar p) -> p.getGodown().getCode())
                        .thenComparing(FgPillar::getCode, FgPillar.CODE_ORDER))
                .map(p -> {
                    List<FgStockRow> rows = byPillar.getOrDefault(p.getId(), List.of());
                    FgStockRow main = rows.stream().max(Comparator.comparingInt(FgStockRow::boxes)).orElse(null);
                    return new PillarOption(p.getId(), p.getCode(), p.getGodown().getId(), p.getGodown().getCode(),
                            p.getCapacityPallets(), rows.stream().mapToInt(FgStockRow::boxes).sum(), pallets(rows),
                            palletsUnknown(rows), main == null ? null : main.designName(), main == null ? null : main.shade());
                })
                .toList();
    }

    private FgGodown find(Long id) {
        FgGodown godown = godownRepository.findWithPlantById(id).orElseThrow(() -> new NotFoundException("Godown", id));
        PlantAccess.require(godown.getPlant().getId());
        return godown;
    }

    private static void apply(FgGodown g, FgGodownRequest req, String code) {
        g.setCode(code);
        g.setName(req.name().trim());
        if (req.active() != null) {
            g.setActive(req.active());
        }
    }

    private static int pallets(List<FgStockRow> rows) {
        return rows.stream().mapToInt(r -> r.pallets() == null ? 0 : r.pallets()).sum();
    }

    private static boolean palletsUnknown(List<FgStockRow> rows) {
        return rows.stream().anyMatch(r -> r.pallets() == null);
    }
}
