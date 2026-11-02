package com.ceramicerp.labour;

import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

public interface WorkerRepository extends JpaRepository<Worker, Long> {

    @Query("""
            select w from Worker w left join fetch w.contractor c
            where w.plant.id = :plantId
              and (:active is null or w.active = :active)
              and (:contractorId is null or c.id = :contractorId)
              and (:department is null or w.department = :department)
              and (:search is null or lower(w.name) like :search or lower(w.code) like :search
                   or lower(w.deviceId) like :search or lower(c.name) like :search)
            order by w.code
            """)
    List<Worker> findMatching(@Param("plantId") Long plantId, @Param("active") Boolean active,
                              @Param("contractorId") Long contractorId, @Param("department") Department department,
                              @Param("search") String search);

    /**
     * A plant's workers in process order of their departments (Press before Kiln before Packing), then by code.
     * The department is stored as text, so the database would sort it A to Z: the order is made here instead.
     */
    default List<Worker> search(Long plantId, Boolean active, Long contractorId, Department department, String search) {
        return findMatching(plantId, active, contractorId, department, search).stream()
                .sorted(Comparator.comparing(Worker::getDepartment))   // stable: the codes stay in order
                .toList();
    }

    @EntityGraph(attributePaths = {"plant", "contractor"})
    Optional<Worker> findWithDetailsById(Long id);

    boolean existsByPlantIdAndCodeIgnoreCase(Long plantId, String code);

    boolean existsByPlantIdAndCodeIgnoreCaseAndIdNot(Long plantId, String code, Long id);

    boolean existsByPlantIdAndDeviceIdIgnoreCase(Long plantId, String deviceId);

    boolean existsByPlantIdAndDeviceIdIgnoreCaseAndIdNot(Long plantId, String deviceId, Long id);

    /** The highest codes with a prefix, to give the next one (W-0001, W-0002 ...). */
    @Query("select w.code from Worker w where w.plant.id = :plantId and w.code like :prefix order by length(w.code) desc, w.code desc")
    List<String> findTopCodes(@Param("plantId") Long plantId, @Param("prefix") String prefixPattern, Limit limit);
}
