package com.ceramicerp.lab;

import com.ceramicerp.common.PageResponse;
import com.ceramicerp.common.Text;
import com.ceramicerp.common.exception.BusinessException;
import com.ceramicerp.common.exception.NotFoundException;
import com.ceramicerp.lab.dto.GrnLabStatus;
import com.ceramicerp.lab.dto.LabForm;
import com.ceramicerp.lab.dto.LabMaterialRow;
import com.ceramicerp.lab.dto.LabOverview;
import com.ceramicerp.lab.dto.LabParameterResponse;
import com.ceramicerp.lab.dto.LabParametersRequest;
import com.ceramicerp.lab.dto.LabSample;
import com.ceramicerp.lab.dto.LabTestRequest;
import com.ceramicerp.lab.dto.LabTestResponse;
import com.ceramicerp.lab.dto.LabTestSummary;
import com.ceramicerp.master.Material;
import com.ceramicerp.master.MaterialRepository;
import com.ceramicerp.numbering.DocumentNumberService;
import com.ceramicerp.security.PlantAccess;
import com.ceramicerp.store.DocStatus;
import com.ceramicerp.store.GrnCheck;
import com.ceramicerp.store.StoreDocType;
import com.ceramicerp.store.StoreDocument;
import com.ceramicerp.store.StoreDocumentLine;
import com.ceramicerp.store.StoreDocumentRepository;
import com.ceramicerp.store.StoreDocumentService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Raw-material lab tests (step 8d). A material with tests set up waits for the lab at every GRN: the GRN stays a
 * draft until the lab has accepted or rejected that material, and a rejected material's lines are rejected in full,
 * so none of it enters stock.
 */
@Service
@RequiredArgsConstructor
public class LabService implements GrnCheck {

    private static final ZoneId PLANT_ZONE = ZoneId.of("Asia/Kolkata");
    /** The result found for this test is written on the GRN lines as their moisture %. */
    static final String MOISTURE = "MOIST";
    private static final int SUPPLIER_DAYS = 30;

    private final LabParameterRepository parameterRepository;
    private final LabTestRepository testRepository;
    private final MaterialRepository materialRepository;
    private final StoreDocumentRepository documentRepository;
    private final StoreDocumentService documentService;
    private final DocumentNumberService numberService;

    // ------------------------------------------------------------------ the tests of a material

    /** Every active material, with how many tests it gets (the setup page). */
    @Transactional(readOnly = true)
    public List<LabMaterialRow> materials() {
        Map<Long, Long> counts = parameterRepository.countPerMaterial().stream()
                .collect(Collectors.toMap(r -> (Long) r[0], r -> ((Number) r[1]).longValue()));
        return materialRepository.findByActiveTrueOrderByCodeAsc().stream()
                .map(m -> new LabMaterialRow(m.getId(), m.getCode(), m.getName(), m.getGroup().getCode(),
                        m.getGroup().getName(), m.getUnit().getCode(), counts.getOrDefault(m.getId(), 0L)))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<LabParameterResponse> parameters(Long materialId) {
        return parameterRepository.findByMaterialIdOrderBySortOrderAscIdAsc(materialId).stream()
                .map(LabParameterResponse::from).toList();
    }

    /**
     * Sets a material's tests, in this order. Tests already set up are matched by id (or, for a new line, by code: a
     * test switched off earlier comes back on) and changed; a test left out is switched off, not deleted, because old
     * results point to it.
     */
    @Transactional
    public List<LabParameterResponse> saveParameters(Long materialId, LabParametersRequest req) {
        Material material = materialRepository.findById(materialId).orElseThrow(() -> new NotFoundException("Material", materialId));
        Map<Long, LabParameter> existing = parameterRepository.findByMaterialIdOrderBySortOrderAscIdAsc(materialId).stream()
                .collect(Collectors.toMap(LabParameter::getId, Function.identity()));
        Map<String, LabParameter> byCode = existing.values().stream()
                .collect(Collectors.toMap(LabParameter::getCode, Function.identity()));
        Set<String> codes = new HashSet<>();
        Set<Long> kept = new HashSet<>();
        int order = 0;
        for (LabParametersRequest.Line line : req.parameters()) {
            String code = Text.upperOrNull(line.code());
            if (!codes.add(code)) {
                throw new BusinessException("Test code " + code + " is used twice");
            }
            if (line.minValue() != null && line.maxValue() != null && line.minValue().compareTo(line.maxValue()) > 0) {
                throw new BusinessException(line.name().trim() + ": the lowest acceptable value is above the highest");
            }
            LabParameter p = line.id() != null ? existing.get(line.id()) : byCode.get(code);
            if (line.id() != null && p == null) {
                throw new NotFoundException("Lab test", line.id());
            }
            LabParameter sameCode = byCode.get(code);
            if (p != null && sameCode != null && sameCode != p) {
                throw new BusinessException("Test code " + code + " already belongs to another test of this material");
            }
            if (p == null) {
                p = new LabParameter();
                p.setMaterial(material);
            } else {
                kept.add(p.getId());
            }
            p.setCode(code);
            p.setName(line.name().trim());
            p.setUnit(Text.trimToNull(line.unit()));
            p.setMinValue(line.minValue());
            p.setMaxValue(line.maxValue());
            p.setSortOrder(++order);
            p.setActive(true);
            parameterRepository.save(p);
        }
        existing.values().stream().filter(p -> !kept.contains(p.getId())).forEach(p -> p.setActive(false));
        parameterRepository.flush();
        return parameters(materialId);
    }

    // ------------------------------------------------------------------ samples and results

    /** The samples waiting for the lab: one per GRN draft and material that needs a test, oldest truck first. */
    @Transactional(readOnly = true)
    public List<LabSample> waiting(Long plantId) {
        PlantAccess.require(plantId);
        Map<String, LabSample> samples = new LinkedHashMap<>();
        for (StoreDocumentLine l : testRepository.waitingLines(plantId)) {
            samples.merge(l.getDocument().getId() + "/" + l.getMaterial().getId(), sample(l.getDocument(), l.getMaterial()),
                    (a, b) -> a);
        }
        return new ArrayList<>(samples.values());
    }

    /** The form for one sample: the truck, the tests, and the result saved so far. */
    @Transactional(readOnly = true)
    public LabForm form(Long documentId, Long materialId) {
        StoreDocument grn = grn(documentId);
        Material material = onGrn(grn, materialId);
        List<LabParameterResponse> params = parameterRepository.findByMaterialIdAndActiveTrueOrderBySortOrderAscIdAsc(materialId)
                .stream().map(LabParameterResponse::from).toList();
        LabTest test = testRepository.findByDocumentIdAndMaterialId(documentId, materialId).orElse(null);
        return new LabForm(sample(grn, material), params, test == null ? null : toResponse(test));
    }

    /** Saves (or corrects, while the GRN is a draft) the lab's result, and passes the verdict to the GRN. */
    @Transactional
    public LabTestResponse save(LabTestRequest req) {
        StoreDocument grn = grn(req.documentId());
        if (grn.getStatus() != DocStatus.DRAFT) {
            throw new BusinessException("GRN " + grn.getDocNo() + " is posted: its lab result can't change any more");
        }
        Material material = onGrn(grn, req.materialId());
        Map<Long, LabParameter> params = parameterRepository.findByMaterialIdAndActiveTrueOrderBySortOrderAscIdAsc(material.getId())
                .stream().collect(Collectors.toMap(LabParameter::getId, Function.identity()));
        if (params.isEmpty()) {
            throw new BusinessException(material.getName() + " has no lab tests set up");
        }
        LocalDate today = LocalDate.now(PLANT_ZONE);
        if (req.testedOn().isAfter(today)) {
            throw new BusinessException("It can't be tested in the future");
        }
        if (req.testedOn().isBefore(grn.getDocDate())) {
            throw new BusinessException("It can't be tested before it arrived (GRN date " + grn.getDocDate() + ")");
        }
        if (req.results().isEmpty()) {
            throw new BusinessException("Enter at least one result");
        }

        LabTest test = testRepository.findByDocumentIdAndMaterialId(grn.getId(), material.getId()).orElseGet(() -> {
            LabTest t = new LabTest();
            t.setPlant(grn.getPlant());
            t.setDocument(grn);
            t.setMaterial(material);
            t.setTestNo(numberService.next(grn.getPlant(), LabDocKind.LAB_TEST, req.testedOn()));
            return t;
        });
        test.setTestedOn(req.testedOn());
        test.setTestedBy(Text.trimToNull(req.testedBy()));
        test.setVerdict(req.verdict());
        test.setRemarks(Text.trimToNull(req.remarks()));

        // the results, with each test as it is set up today
        test.getResults().clear();
        Set<Long> seen = new HashSet<>();
        int outOfRange = 0;
        BigDecimal moisture = null;
        for (LabTestRequest.Result r : req.results()) {
            LabParameter p = params.get(r.parameterId());
            if (p == null) {
                throw new BusinessException("That test isn't set up for " + material.getName());
            }
            if (!seen.add(p.getId())) {
                throw new BusinessException(p.getName() + " is entered twice");
            }
            LabResult result = new LabResult();
            result.setTest(test);
            result.setParameter(p);
            result.setSortOrder(p.getSortOrder());
            result.setCode(p.getCode());
            result.setName(p.getName());
            result.setUnit(p.getUnit());
            result.setMinValue(p.getMinValue());
            result.setMaxValue(p.getMaxValue());
            result.setValue(r.value());
            result.setWithinRange(p.accepts(r.value()));
            if (!result.isWithinRange()) {
                outOfRange++;
            }
            if (MOISTURE.equals(p.getCode()) && r.value().signum() >= 0 && r.value().compareTo(BigDecimal.valueOf(100)) <= 0) {
                moisture = r.value().setScale(2, RoundingMode.HALF_UP);
            }
            test.getResults().add(result);
        }
        test.setOutOfRange(outOfRange);
        testRepository.save(test);
        documentService.labVerdict(grn, material, req.verdict() == LabVerdict.REJECTED, moisture);
        testRepository.flush();
        return toResponse(test);
    }

    /** Takes a result back while the GRN is a draft: the material waits for the lab again. */
    @Transactional
    public void delete(Long id) {
        LabTest test = find(id);
        StoreDocument grn = grn(test.getDocument().getId());
        if (grn.getStatus() != DocStatus.DRAFT) {
            throw new BusinessException("GRN " + grn.getDocNo() + " is posted: its lab result stays");
        }
        if (test.getVerdict() == LabVerdict.REJECTED) {
            documentService.labVerdict(grn, test.getMaterial(), false, null);
        }
        testRepository.delete(test);
    }

    @Transactional(readOnly = true)
    public LabTestResponse get(Long id) {
        return toResponse(find(id));
    }

    @Transactional(readOnly = true)
    public PageResponse<LabTestSummary> search(Long plantId, LabVerdict verdict, Long materialId, LocalDate from,
                                               LocalDate to, String search, int page, int size) {
        PlantAccess.require(plantId);
        String pattern = Text.trimToNull(search) == null ? null : "%" + search.trim().toLowerCase(Locale.ROOT) + "%";
        var result = testRepository.search(plantId, verdict, materialId, from, to, pattern,
                PageRequest.of(page, Math.min(size, 100), Sort.by(Sort.Order.desc("testedOn"), Sort.Order.desc("id"))));
        return PageResponse.of(result, LabService::summary);
    }

    /** Samples waiting now, and the last 30 days per supplier. */
    @Transactional(readOnly = true)
    public LabOverview overview(Long plantId) {
        PlantAccess.require(plantId);
        LocalDate today = LocalDate.now(PLANT_ZONE);
        List<LabOverview.SupplierQuality> suppliers = testRepository.perSupplier(plantId, today.minusDays(SUPPLIER_DAYS - 1), today)
                .stream()
                .map(r -> {
                    int tested = ((Number) r[2]).intValue();
                    int rejected = r[3] == null ? 0 : ((Number) r[3]).intValue();
                    return new LabOverview.SupplierQuality((Long) r[0], (String) r[1], tested, rejected, pct(rejected, tested));
                })
                .toList();
        int tested = suppliers.stream().mapToInt(LabOverview.SupplierQuality::tested).sum();
        int rejected = suppliers.stream().mapToInt(LabOverview.SupplierQuality::rejected).sum();
        return new LabOverview(waiting(plantId).size(), tested, rejected, pct(rejected, tested), suppliers);
    }

    /** Where each material of a GRN stands with the lab (only the materials that need a test). */
    @Transactional(readOnly = true)
    public List<GrnLabStatus> grnStatus(Long documentId) {
        StoreDocument grn = grn(documentId);
        Map<Long, LabTest> tests = testRepository.findByDocumentId(grn.getId()).stream()
                .collect(Collectors.toMap(t -> t.getMaterial().getId(), Function.identity()));
        Set<Long> needing = needingTests(grn);
        return grn.getLines().stream()
                .map(StoreDocumentLine::getMaterial)
                .filter(m -> needing.contains(m.getId()))
                .distinct()
                .map(m -> {
                    LabTest t = tests.get(m.getId());
                    return new GrnLabStatus(m.getId(), m.getName(), t == null ? null : t.getId(),
                            t == null ? null : t.getTestNo(), t == null ? null : t.getVerdict());
                })
                .toList();
    }

    /**
     * Before a GRN is posted (called by the store): every material that needs a test has one, and a rejected
     * material's lines are rejected in full, even if the GRN was edited after the lab's result.
     */
    @Override
    public void beforePost(StoreDocument grn) {
        Set<Long> needing = needingTests(grn);
        if (needing.isEmpty()) {
            return;
        }
        Map<Long, LabTest> tests = testRepository.findByDocumentId(grn.getId()).stream()
                .collect(Collectors.toMap(t -> t.getMaterial().getId(), Function.identity()));
        for (StoreDocumentLine line : grn.getLines()) {
            Material m = line.getMaterial();
            if (!needing.contains(m.getId())) {
                continue;
            }
            LabTest test = tests.get(m.getId());
            if (test == null) {
                throw new BusinessException("Line " + line.getLineNo() + ": the lab hasn't tested " + m.getName()
                        + " yet. The GRN waits for its result");
            }
            if (test.getVerdict() == LabVerdict.REJECTED) {
                documentService.labVerdict(grn, m, true, null);
            }
        }
    }

    // ------------------------------------------------------------------ helpers

    private Set<Long> needingTests(StoreDocument grn) {
        Set<Long> materialIds = grn.getLines().stream().map(l -> l.getMaterial().getId()).collect(Collectors.toSet());
        return materialIds.isEmpty() ? Set.of() : new HashSet<>(parameterRepository.testedOf(materialIds));
    }

    private StoreDocument grn(Long documentId) {
        StoreDocument doc = documentRepository.findWithDetailsById(documentId)
                .orElseThrow(() -> new NotFoundException("GRN", documentId));
        PlantAccess.require(doc.getPlant().getId());
        if (doc.getDocType() != StoreDocType.GRN) {
            throw new BusinessException("Only material that arrives on a GRN goes to the lab");
        }
        return doc;
    }

    private static Material onGrn(StoreDocument grn, Long materialId) {
        return grn.getLines().stream().map(StoreDocumentLine::getMaterial)
                .filter(m -> m.getId().equals(materialId)).findFirst()
                .orElseThrow(() -> new BusinessException("That material isn't on this GRN"));
    }

    private LabTest find(Long id) {
        LabTest t = testRepository.findWithDetailsById(id).orElseThrow(() -> new NotFoundException("Lab test", id));
        PlantAccess.require(t.getPlant().getId());
        return t;
    }

    private static LabSample sample(StoreDocument grn, Material m) {
        List<StoreDocumentLine> lines = grn.getLines().stream().filter(l -> l.getMaterial().getId().equals(m.getId())).toList();
        BigDecimal received = lines.stream().map(StoreDocumentLine::getReceivedQty).filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal rejected = lines.stream().map(StoreDocumentLine::getRejectedQty).filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return new LabSample(grn.getId(), grn.getDocNo(), grn.getDocDate(), grn.getStatus(),
                grn.getSupplier() == null ? null : grn.getSupplier().getName(), grn.getVehicleNo(),
                grn.getWeighbridgeSlipNo(), grn.getSupplierInvoiceNo(), m.getId(), m.getCode(), m.getName(),
                m.getUnit().getCode(), received, rejected, grn.getCreatedAt());
    }

    private LabTestResponse toResponse(LabTest t) {
        StoreDocument grn = t.getDocument();
        List<LabTestResponse.Result> results = t.getResults().stream()
                .map(r -> new LabTestResponse.Result(r.getParameter().getId(), r.getCode(), r.getName(), r.getUnit(),
                        r.getMinValue(), r.getMaxValue(), r.getValue(), r.isWithinRange()))
                .toList();
        return new LabTestResponse(t.getId(), t.getTestNo(), t.getPlant().getId(), sample(grn, t.getMaterial()),
                t.getTestedOn(), t.getTestedBy(), t.getVerdict(), t.getOutOfRange(), t.getRemarks(), results,
                grn.getStatus() == DocStatus.DRAFT, t.getCreatedBy(), t.getCreatedAt(), t.getUpdatedBy(), t.getUpdatedAt());
    }

    private static LabTestSummary summary(LabTest t) {
        StoreDocument d = t.getDocument();
        return new LabTestSummary(t.getId(), t.getTestNo(), t.getTestedOn(), d.getId(), d.getDocNo(),
                t.getMaterial().getId(), t.getMaterial().getCode(), t.getMaterial().getName(),
                d.getSupplier() == null ? null : d.getSupplier().getName(), d.getVehicleNo(), t.getVerdict(),
                t.getOutOfRange());
    }

    private static BigDecimal pct(int part, int whole) {
        return whole == 0 ? null : BigDecimal.valueOf(part * 100L).divide(BigDecimal.valueOf(whole), 1, RoundingMode.HALF_UP);
    }
}
