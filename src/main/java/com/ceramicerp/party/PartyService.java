package com.ceramicerp.party;

import com.ceramicerp.common.IndianStates;
import com.ceramicerp.common.PageResponse;
import com.ceramicerp.common.Text;
import com.ceramicerp.common.exception.BusinessException;
import com.ceramicerp.common.exception.NotFoundException;
import com.ceramicerp.party.dto.PartyRequest;
import com.ceramicerp.party.dto.PartyResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Limit;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

/**
 * Business rules for dealers, suppliers and transporters:
 * - code: typed, or the next automatic number (DLR-0001 ...)
 * - GSTIN required for REGULAR / COMPOSITION parties
 * - the GSTIN decides the state (first 2 digits) and the PAN (characters 3-12)
 * - the same GSTIN can't be used twice for the same party type
 * - only OVERSEAS parties are outside India (no GSTIN, no Indian state)
 */
@Service
@RequiredArgsConstructor
public class PartyService {

    private static final String INDIA = "India";

    private final PartyRepository partyRepository;

    @Transactional(readOnly = true)
    public PageResponse<PartyResponse> search(PartyType type, String search, String stateCode, Boolean active,
                                              int page, int size) {
        String pattern = Text.trimToNull(search) == null ? null : "%" + search.trim().toLowerCase(Locale.ROOT) + "%";
        var pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100), Sort.by("name"));
        return PageResponse.of(partyRepository.search(type, pattern, Text.trimToNull(stateCode), active, pageable),
                PartyResponse::from);
    }

    @Transactional(readOnly = true)
    public PartyResponse get(Long id) {
        return PartyResponse.from(find(id));
    }

    @Transactional
    public PartyResponse create(PartyRequest req) {
        String code = Text.upperOrNull(req.code());
        if (code == null) {
            code = nextCode(req.partyType());
        } else if (partyRepository.existsByCodeIgnoreCase(code)) {
            throw new BusinessException("Party code already exists: " + code);
        }
        Party party = new Party();
        party.setPartyType(req.partyType());
        party.setCode(code);
        apply(party, req);
        return PartyResponse.from(partyRepository.save(party));
    }

    @Transactional
    public PartyResponse update(Long id, PartyRequest req) {
        Party party = find(id);
        if (req.partyType() != party.getPartyType()) {
            throw new BusinessException("A " + party.getPartyType().label() + " can't be changed into a "
                    + req.partyType().label());
        }
        String code = Text.upperOrNull(req.code());
        if (code != null) {
            if (partyRepository.existsByCodeIgnoreCaseAndIdNot(code, id)) {
                throw new BusinessException("Party code already exists: " + code);
            }
            party.setCode(code);
        }
        apply(party, req);
        return PartyResponse.from(party);
    }

    private Party find(Long id) {
        return partyRepository.findById(id).orElseThrow(() -> new NotFoundException("Party", id));
    }

    /**
     * Next free number for the type's prefix: DLR-0001, DLR-0002 ...
     * Looks at the highest existing numeric codes and adds one.
     */
    String nextCode(PartyType type) {
        String prefix = type.getCodePrefix();
        int next = 1;
        for (String existing : partyRepository.findTopCodes(prefix + "%", Limit.of(20))) {
            String digits = existing.substring(prefix.length());
            if (digits.matches("\\d{1,9}")) {
                next = Integer.parseInt(digits) + 1;
                break;
            }
        }
        String code = prefix + String.format("%04d", next);
        while (partyRepository.existsByCodeIgnoreCase(code)) { // someone typed this code by hand
            code = prefix + String.format("%04d", ++next);
        }
        return code;
    }

    private void apply(Party p, PartyRequest req) {
        GstRegistration reg = req.gstRegistration();
        String gstin = Text.upperOrNull(req.gstin());
        String pan = Text.upperOrNull(req.pan());
        String stateCode = Text.trimToNull(req.stateCode());
        String country = Text.trimToNull(req.country()) == null ? INDIA : req.country().trim();
        String pincode = Text.trimToNull(req.pincode());

        if (reg == GstRegistration.OVERSEAS) {
            // Export customer: no Indian GST details at all.
            if (gstin != null) throw new BusinessException("An overseas party can't have a GSTIN");
            if (INDIA.equalsIgnoreCase(country)) throw new BusinessException("Enter the country of the overseas party");
            stateCode = null;
        } else {
            if (!INDIA.equalsIgnoreCase(country)) {
                throw new BusinessException("Only OVERSEAS parties can be outside India");
            }
            country = INDIA;
            if (reg.requiresGstin() && gstin == null) {
                throw new BusinessException("GSTIN is required for a " + reg.name().toLowerCase(Locale.ROOT) + " party");
            }
            if (gstin != null) {
                // GSTIN = 2-digit state code + 10-character PAN + 3 more characters
                String gstinState = gstin.substring(0, 2);
                if (stateCode != null && !stateCode.equals(gstinState)) {
                    throw new BusinessException("State doesn't match the GSTIN (the GSTIN is from "
                            + IndianStates.name(gstinState) + ")");
                }
                stateCode = gstinState;
                String gstinPan = gstin.substring(2, 12);
                if (pan != null && !pan.equals(gstinPan)) {
                    throw new BusinessException("PAN doesn't match the GSTIN (expected " + gstinPan + ")");
                }
                pan = gstinPan;
                boolean duplicate = p.getId() == null
                        ? partyRepository.existsByPartyTypeAndGstinIgnoreCase(p.getPartyType(), gstin)
                        : partyRepository.existsByPartyTypeAndGstinIgnoreCaseAndIdNot(p.getPartyType(), gstin, p.getId());
                if (duplicate) {
                    throw new BusinessException("Another " + p.getPartyType().label() + " already has GSTIN " + gstin);
                }
            }
            if (stateCode != null && !IndianStates.isValid(stateCode)) {
                throw new BusinessException("Unknown GST state code: " + stateCode);
            }
            if (pincode != null && !pincode.matches("[1-9][0-9]{5}")) {
                throw new BusinessException("Indian PIN code must be 6 digits");
            }
        }

        p.setName(req.name().trim());
        p.setContactPerson(Text.trimToNull(req.contactPerson()));
        p.setGstRegistration(reg);
        p.setGstin(gstin);
        p.setPan(pan);
        p.setAddressLine(Text.trimToNull(req.addressLine()));
        p.setCity(Text.trimToNull(req.city()));
        p.setStateCode(stateCode);
        p.setState(IndianStates.name(stateCode));   // name always comes from the code, never typed
        p.setPincode(pincode);
        p.setCountry(country);
        p.setPhone(Text.trimToNull(req.phone()));
        p.setMobile(Text.trimToNull(req.mobile()));
        p.setEmail(Text.trimToNull(req.email()));
        p.setRegion(Text.trimToNull(req.region()));
        // Credit limit only means something for parties you bill (how much they may owe you).
        p.setCreditLimit(p.getPartyType().isCustomer() ? req.creditLimit() : null);
        p.setCreditDays(req.creditDays());
        p.setBankName(Text.trimToNull(req.bankName()));
        p.setBankAccountNo(Text.trimToNull(req.bankAccountNo()));
        p.setBankIfsc(Text.upperOrNull(req.bankIfsc()));
        p.setNotes(Text.trimToNull(req.notes()));
        if (req.active() != null) {
            p.setActive(req.active());
        }
    }
}
