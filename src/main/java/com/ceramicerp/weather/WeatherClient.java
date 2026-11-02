package com.ceramicerp.weather;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/** Where the weather comes from. An interface, so the provider can change and the tests need no internet. */
public interface WeatherClient {

    /** One hour's weather, in plant wall-clock time. */
    record Hour(LocalDateTime hour, BigDecimal temperatureC, BigDecimal humidityPct, BigDecimal rainMm) {
    }

    /** Today's hours and those of the last `pastDays` days. May include hours still to come. */
    List<Hour> fetch(BigDecimal latitude, BigDecimal longitude, int pastDays);
}
