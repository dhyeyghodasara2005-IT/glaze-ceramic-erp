package com.ceramicerp.weather;

import com.ceramicerp.common.BaseEntity;
import com.ceramicerp.organization.Plant;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** The weather at a plant for one hour, in plant wall-clock time. */
@Getter
@Setter
@Entity
@Table(name = "weather_readings")
public class WeatherReading extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "plant_id", nullable = false)
    private Plant plant;

    @Column(nullable = false)
    private LocalDateTime readingHour;

    @Column(name = "temperature_c", nullable = false, precision = 4, scale = 1)
    private BigDecimal temperatureC;

    @Column(nullable = false, precision = 4, scale = 1)
    private BigDecimal humidityPct;

    @Column(nullable = false, precision = 6, scale = 2)
    private BigDecimal rainMm;
}
