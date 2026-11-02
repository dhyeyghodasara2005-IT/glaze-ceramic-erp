package com.ceramicerp.weather;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface WeatherReadingRepository extends JpaRepository<WeatherReading, Long> {

    Optional<WeatherReading> findFirstByPlantIdOrderByReadingHourDesc(Long plantId);

    @Query("""
            select r from WeatherReading r
            where r.plant.id = :plantId and r.readingHour >= :from and r.readingHour <= :to
            order by r.readingHour
            """)
    List<WeatherReading> findBetween(@Param("plantId") Long plantId, @Param("from") LocalDateTime from,
                                     @Param("to") LocalDateTime to);
}
