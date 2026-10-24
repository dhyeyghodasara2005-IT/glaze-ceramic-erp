package com.ceramicerp.fg;

import com.ceramicerp.fg.dto.DesignGradeStock;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface FgStockRepository extends JpaRepository<FgStock, Long> {

    /**
     * Loads the stock row AND locks it (SELECT ... FOR UPDATE) until our transaction ends, so two
     * movements of the same boxes at the same moment are handled one after the other.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select s from FgStock s
            where s.pillar.id = :pillarId and s.design.id = :designId and s.batchCode = :batchCode
              and s.grade.id = :gradeId and s.shade = :shade and s.caliber = :caliber
            """)
    Optional<FgStock> findForUpdate(@Param("pillarId") Long pillarId,
                                    @Param("designId") Long designId,
                                    @Param("batchCode") String batchCode,
                                    @Param("gradeId") Long gradeId,
                                    @Param("shade") String shade,
                                    @Param("caliber") String caliber);

    /** A plant's stock (only rows with boxes), with everything a list shows, in one query. */
    @EntityGraph(attributePaths = {"pillar", "pillar.godown", "design", "design.tileSize", "grade"})
    @Query("""
            select s from FgStock s
            where s.plant.id = :plantId and s.boxes > 0
              and (:godownId is null or s.pillar.godown.id = :godownId)
              and (:gradeId is null or s.grade.id = :gradeId)
              and (:search is null or lower(s.design.name) like :search or lower(s.design.code) like :search
                   or lower(s.batchCode) like :search or lower(s.shade) like :search or lower(s.pillar.code) like :search)
            """)
    List<FgStock> search(@Param("plantId") Long plantId,
                         @Param("godownId") Long godownId,
                         @Param("gradeId") Long gradeId,
                         @Param("search") String search);

    /** What stands at the pillars of one godown. */
    @EntityGraph(attributePaths = {"pillar", "pillar.godown", "design", "design.tileSize", "grade"})
    List<FgStock> findByPillarGodownIdAndBoxesGreaterThan(Long godownId, int boxes);

    /** What stands at every pillar of a plant (for the pack entry's pillar list). */
    @EntityGraph(attributePaths = {"pillar", "pillar.godown", "design", "design.tileSize", "grade"})
    List<FgStock> findByPlantIdAndBoxesGreaterThan(Long plantId, int boxes);

    @EntityGraph(attributePaths = {"plant", "pillar", "pillar.godown", "design", "design.tileSize", "grade"})
    Optional<FgStock> findWithDetailsById(Long id);

    boolean existsByPillarIdAndBoxesGreaterThan(Long pillarId, int boxes);

    /**
     * Boxes in stock per design and grade (a sales order can be loaded from any batch, shade or caliber).
     * "select new" builds the small record straight from the grouped query.
     */
    @Query("""
            select new com.ceramicerp.fg.dto.DesignGradeStock(s.design.id, s.grade.id, sum(s.boxes))
            from FgStock s
            where s.plant.id = :plantId and s.boxes > 0 and s.design.id in :designIds
            group by s.design.id, s.grade.id
            """)
    List<DesignGradeStock> totalsByDesignAndGrade(@Param("plantId") Long plantId,
                                                  @Param("designIds") Collection<Long> designIds);
}
