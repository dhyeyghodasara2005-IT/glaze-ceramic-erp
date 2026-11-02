package com.ceramicerp.labour;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface AttendanceRepository extends JpaRepository<Attendance, Long> {

    /** A day's attendance of a plant, with the worker (and the shift). */
    @Query("""
            select a from Attendance a join fetch a.worker w left join fetch a.shift
            where a.plant.id = :plantId and a.workDate = :date
            """)
    List<Attendance> onDay(@Param("plantId") Long plantId, @Param("date") LocalDate date);

    Optional<Attendance> findByWorkerIdAndWorkDate(Long workerId, LocalDate workDate);

    /** Everything marked in a period, with the worker and their contractor (the month per contractor). */
    @Query("""
            select a from Attendance a join fetch a.worker w left join fetch w.contractor
            where a.plant.id = :plantId and a.workDate between :from and :to
            """)
    List<Attendance> between(@Param("plantId") Long plantId, @Param("from") LocalDate from, @Param("to") LocalDate to);
}
