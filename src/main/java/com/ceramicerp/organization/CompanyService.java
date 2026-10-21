package com.ceramicerp.organization;

import com.ceramicerp.common.Text;
import com.ceramicerp.common.exception.BusinessException;
import com.ceramicerp.common.exception.NotFoundException;
import com.ceramicerp.organization.dto.CompanyRequest;
import com.ceramicerp.organization.dto.CompanyResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** Business rules for companies. Controllers stay thin and call this. */
@Service
@RequiredArgsConstructor
public class CompanyService {

    private final CompanyRepository companyRepository;

    @Transactional(readOnly = true)
    public List<CompanyResponse> list() {
        return companyRepository.findAllByOrderByNameAsc().stream().map(CompanyResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public CompanyResponse get(Long id) {
        return CompanyResponse.from(find(id));
    }

    @Transactional
    public CompanyResponse create(CompanyRequest req) {
        String code = Text.upperOrNull(req.code());
        String gstin = Text.upperOrNull(req.gstin());
        if (companyRepository.existsByCodeIgnoreCase(code)) {
            throw new BusinessException("Company code already exists: " + code);
        }
        if (gstin != null && companyRepository.existsByGstinIgnoreCase(gstin)) {
            throw new BusinessException("Another company already has GSTIN " + gstin);
        }
        Company company = new Company();
        apply(company, req, code, gstin);
        return CompanyResponse.from(companyRepository.save(company));
    }

    @Transactional
    public CompanyResponse update(Long id, CompanyRequest req) {
        Company company = find(id);
        String code = Text.upperOrNull(req.code());
        String gstin = Text.upperOrNull(req.gstin());
        if (companyRepository.existsByCodeIgnoreCaseAndIdNot(code, id)) {
            throw new BusinessException("Company code already exists: " + code);
        }
        if (gstin != null && companyRepository.existsByGstinIgnoreCaseAndIdNot(gstin, id)) {
            throw new BusinessException("Another company already has GSTIN " + gstin);
        }
        apply(company, req, code, gstin);
        return CompanyResponse.from(company);
    }

    Company find(Long id) {
        return companyRepository.findById(id).orElseThrow(() -> new NotFoundException("Company", id));
    }

    private void apply(Company c, CompanyRequest req, String code, String gstin) {
        c.setCode(code);
        c.setName(req.name().trim());
        c.setLegalName(Text.trimToNull(req.legalName()));
        c.setGstin(gstin);
        c.setPan(Text.upperOrNull(req.pan()));
        c.setAddressLine(Text.trimToNull(req.addressLine()));
        c.setCity(Text.trimToNull(req.city()));
        c.setState(Text.trimToNull(req.state()));
        c.setPincode(Text.trimToNull(req.pincode()));
        c.setPhone(Text.trimToNull(req.phone()));
        c.setEmail(Text.trimToNull(req.email()));
        if (req.active() != null) {
            c.setActive(req.active());
        }
    }
}
