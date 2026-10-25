package com.ceramicerp.energy;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * A meter's readings stand in order: by date, then by shift order (a once-a-day reading has no shift).
 * "coalesce(s.sortOrder, -1)" is that order; the left join keeps the readings without a shift.
 */
public interface MeterReadingRepository extends JpaRepository<MeterReading, Long> {

    @Query("""
            select r from MeterReading r left join r.shift s
            where r.meter.id = :meterId and r.readOn = :readOn
              and ((:shiftId is null and r.shift is null) or s.id = :shiftId)
            """)
    Optional<MeterReading> findSlot(@Param("meterId") Long meterId, @Param("readOn") LocalDate readOn,
                                    @Param("shiftId") Long shiftId);

    /** The readings just before a place in the order, nearest first (ask for one). */
    @Query("""
            select r from MeterReading r left join fetch r.shift s
            where r.meter.id = :meterId
              and (r.readOn < :readOn or (r.readOn = :readOn and coalesce(s.sortOrder, -1) < :order))
            order by r.readOn desc, coalesce(s.sortOrder, -1) desc
            """)
    List<MeterReading> before(@Param("meterId") Long meterId, @Param("readOn") LocalDate readOn,
                              @Param("order") int order, Pageable page);

    /** The readings just after a place in the order, nearest first (ask for one). */
    @Query("""
            select r from MeterReading r left join fetch r.shift s
            where r.meter.id = :meterId
              and (r.readOn > :readOn or (r.readOn = :readOn and coalesce(s.sortOrder, -1) > :order))
            order by r.readOn, coalesce(s.sortOrder, -1)
            """)
    List<MeterReading> after(@Param("meterId") Long meterId, @Param("readOn") LocalDate readOn,
                             @Param("order") int order, Pageable page);

    /** The latest uses of a meter, newest first, to know what is usual. */
    @Query("""
            select r.used from MeterReading r left join r.shift s
            where r.meter.id = :meterId and r.restarted = false
            order by r.readOn desc, coalesce(s.sortOrder, -1) desc
            """)
    List<BigDecimal> recentUses(@Param("meterId") Long meterId, Pageable page);

    /** A round's readings: the shift's, and the day's once-a-day readings. */
    @Query("""
            select r from MeterReading r join fetch r.meter m left join fetch r.shift s
            where r.plant.id = :plantId and r.readOn = :readOn and (r.shift is null or s.id = :shiftId)
            """)
    List<MeterReading> inRound(@Param("plantId") Long plantId, @Param("readOn") LocalDate readOn,
                               @Param("shiftId") Long shiftId);

    /** Everything used on the production days of a period. */
    @Query("""
            select r from MeterReading r join fetch r.meter m left join fetch r.shift s
            where r.plant.id = :plantId and r.readOn between :from and :to
            """)
    List<MeterReading> usedBetween(@Param("plantId") Long plantId, @Param("from") LocalDate from,
                                   @Param("to") LocalDate to);

    /** A meter's readings, newest first (ask for one: the latest). */
    @Query("""
            select r from MeterReading r left join fetch r.shift s
            where r.meter.id = :meterId
            order by r.readOn desc, coalesce(s.sortOrder, -1) desc
            """)
    List<MeterReading> latest(@Param("meterId") Long meterId, Pageable page);

    boolean existsByMeterId(Long meterId);

    Optional<MeterReading> findFirstByMeterIdOrderByReadOnAsc(Long meterId);
}
