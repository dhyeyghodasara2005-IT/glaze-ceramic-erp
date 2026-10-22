package com.ceramicerp.numbering;

import com.ceramicerp.organization.Plant;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

/**
 * Hands out document numbers: {PLANT}/{TYPE}/{FY}/{NNNN}, e.g. UNIT-1/GRN/26-27/0001.
 * Numbering restarts every financial year (1 April), like Tally.
 * Used by every module whose documents need a number (store, production ...).
 */
@Service
@RequiredArgsConstructor
public class DocumentNumberService {

    private final DocumentSequenceRepository sequenceRepository;

    /** Must run inside the caller's transaction (MANDATORY) so the lock lasts until the document is saved. */
    @Transactional(propagation = Propagation.MANDATORY)
    public String next(Plant plant, DocumentKind type, LocalDate date) {
        String finYear = financialYear(date);
        DocumentSequence seq = sequenceRepository
                .findByPlantIdAndDocTypeAndFinYear(plant.getId(), type.name(), finYear)
                .orElseGet(() -> {
                    DocumentSequence s = new DocumentSequence();
                    s.setPlantId(plant.getId());
                    s.setDocType(type.name());
                    s.setFinYear(finYear);
                    return sequenceRepository.save(s);
                });
        seq.setLastNumber(seq.getLastNumber() + 1);
        return plant.getCode() + "/" + type.getPrefix() + "/" + finYear + "/" + String.format("%04d", seq.getLastNumber());
    }

    /** 15 Oct 2026 -> "26-27"; 10 Mar 2027 -> "26-27"; 1 Apr 2027 -> "27-28". */
    public static String financialYear(LocalDate date) {
        int start = date.getMonthValue() >= 4 ? date.getYear() : date.getYear() - 1;
        return String.format("%02d-%02d", start % 100, (start + 1) % 100);
    }
}
