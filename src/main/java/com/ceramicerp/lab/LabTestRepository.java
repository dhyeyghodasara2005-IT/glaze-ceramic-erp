package com.ceramicerp.lab;

import com.ceramicerp.store.StoreDocumentLine;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface LabTestRepository extends JpaRepository<LabTest, Long> {

    @EntityGraph(attributePaths = {"plant", "document", "document.supplier", "material", "material.unit"})
    Optional<LabTest> findWithDetailsById(Long id);

    @EntityGraph(attributePaths = {"plant", "document", "document.supplier", "material", "material.unit"})
    Optional<LabTest> findByDocumentIdAndMaterialId(Long documentId, Long materialId);

    @EntityGraph(attributePaths = {"material"})
    List<LabTest> findByDocumentId(Long documentId);

    /**
     * GRN lines still waiting for the lab: the GRN is a draft, its material has tests set up, and that material of
     * that GRN has no test yet. Oldest truck first.
     */
    @Query("""
            select l from StoreDocumentLine l join fetch l.document d join fetch l.material m join fetch m.unit
                 left join fetch d.supplier
            where d.plant.id = :plantId and d.docType = com.ceramicerp.store.StoreDocType.GRN
              and d.status = com.ceramicerp.store.DocStatus.DRAFT
              and exists (select p.id from LabParameter p where p.material = m and p.active = true)
              and not exists (select t.id from LabTest t where t.document = d and t.material = m)
            order by d.createdAt, d.id, l.lineNo
            """)
    List<StoreDocumentLine> waitingLines(@Param("plantId") Long plantId);

    @Query(value = """
            select t from LabTest t join fetch t.document d left join fetch d.supplier s join fetch t.material m
            where t.plant.id = :plantId
              and (:verdict is null or t.verdict = :verdict)
              and (:materialId is null or m.id = :materialId)
              and (:from is null or t.testedOn >= :from) and (:to is null or t.testedOn <= :to)
              and (:search is null or lower(t.testNo) like :search or lower(m.name) like :search
                   or lower(m.code) like :search or lower(s.name) like :search or lower(d.vehicleNo) like :search)
            """,
            countQuery = """
            select count(t) from LabTest t join t.document d left join d.supplier s join t.material m
            where t.plant.id = :plantId
              and (:verdict is null or t.verdict = :verdict)
              and (:materialId is null or m.id = :materialId)
              and (:from is null or t.testedOn >= :from) and (:to is null or t.testedOn <= :to)
              and (:search is null or lower(t.testNo) like :search or lower(m.name) like :search
                   or lower(m.code) like :search or lower(s.name) like :search or lower(d.vehicleNo) like :search)
            """)
    Page<LabTest> search(@Param("plantId") Long plantId, @Param("verdict") LabVerdict verdict,
                         @Param("materialId") Long materialId, @Param("from") LocalDate from, @Param("to") LocalDate to,
                         @Param("search") String search, Pageable page);

    /** Tested and rejected per supplier in a period (supplier id, name, tested, rejected), most tested first. */
    @Query("""
            select s.id, s.name, count(t), sum(case when t.verdict = com.ceramicerp.lab.LabVerdict.REJECTED then 1 else 0 end)
            from LabTest t join t.document d left join d.supplier s
            where t.plant.id = :plantId and t.testedOn between :from and :to
            group by s.id, s.name
            order by count(t) desc
            """)
    List<Object[]> perSupplier(@Param("plantId") Long plantId, @Param("from") LocalDate from, @Param("to") LocalDate to);
}
