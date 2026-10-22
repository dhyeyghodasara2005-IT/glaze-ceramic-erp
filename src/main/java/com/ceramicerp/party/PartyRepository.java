package com.ceramicerp.party;

import org.springframework.data.domain.Limit;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface PartyRepository extends JpaRepository<Party, Long> {

    boolean existsByCodeIgnoreCase(String code);

    boolean existsByCodeIgnoreCaseAndIdNot(String code, Long id);

    boolean existsByPartyTypeAndGstinIgnoreCase(PartyType type, String gstin);

    boolean existsByPartyTypeAndGstinIgnoreCaseAndIdNot(PartyType type, String gstin, Long id);

    /**
     * Highest existing codes for a prefix, longest first so "DLR-10000" beats "DLR-9999".
     * Used to pick the next automatic code.
     */
    @Query("select p.code from Party p where p.code like :prefix order by length(p.code) desc, p.code desc")
    List<String> findTopCodes(@Param("prefix") String prefixPattern, Limit limit);

    /** One page of parties; every filter is optional (null = no filter). */
    @Query("""
            select p from Party p
            where (:type is null or p.partyType = :type)
              and (:search is null
                   or lower(p.code) like :search or lower(p.name) like :search
                   or lower(p.gstin) like :search or p.mobile like :search or lower(p.city) like :search)
              and (:stateCode is null or p.stateCode = :stateCode)
              and (:active is null or p.active = :active)
            """)
    Page<Party> search(@Param("type") PartyType type,
                       @Param("search") String search,
                       @Param("stateCode") String stateCode,
                       @Param("active") Boolean active,
                       Pageable pageable);

    /** Active parties of one type, for dropdowns (e.g. suppliers on a GRN). */
    List<Party> findByPartyTypeAndActiveTrueOrderByNameAsc(PartyType type);
}
