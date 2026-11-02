package com.ceramicerp.weather.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/** The weather of one production shift. @param hours how many hourly readings it is made from */
public record WeatherShift(LocalDate prodDate, Long shiftId, String shiftCode, String shiftName, int hours,
                           BigDecimal temperatureAvg, BigDecimal temperatureMin, BigDecimal temperatureMax,
                           BigDecimal humidityAvg, BigDecimal rainMm) {
}
