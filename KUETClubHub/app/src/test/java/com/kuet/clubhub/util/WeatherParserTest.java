package com.kuet.clubhub.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.kuet.clubhub.model.WeatherForecast;

import org.junit.Test;

public class WeatherParserTest {

    private static final String SAMPLE_JSON = "{"
            + "\"latitude\":22.9,\"longitude\":89.5,"
            + "\"daily\":{"
            + "\"time\":[\"2026-10-28\",\"2026-10-29\"],"
            + "\"weather_code\":[2,61],"
            + "\"temperature_2m_max\":[31.4,28.6],"
            + "\"temperature_2m_min\":[23.8,22.1],"
            + "\"precipitation_probability_max\":[20,80],"
            + "\"wind_speed_10m_max\":[14.2,24.7]"
            + "}}";

    @Test
    public void parseDailyJsonMatchesExactEventDate() {
        WeatherForecast f = WeatherParser.parseDailyJson(SAMPLE_JSON, "2026-10-29");
        assertNotNull(f);
        assertEquals("2026-10-29", f.getDate());
        assertEquals(61, f.getWeatherCode());
        assertEquals("Rain", f.getCondition());
        assertEquals(29, f.getTempMaxC());
        assertEquals(22, f.getTempMinC());
        assertEquals(80, f.getRainChancePercent());
        assertEquals(25, f.getWindMaxKmh());
        assertTrue(f.isExactEventDate());
    }

    @Test
    public void parseDailyJsonFallsBackToFirstDayWhenDateIsBeyondHorizon() {
        WeatherForecast f = WeatherParser.parseDailyJson(SAMPLE_JSON, "2026-12-01");
        assertNotNull(f);
        assertEquals("2026-10-28", f.getDate());
        assertEquals("Partly cloudy", f.getCondition());
        assertEquals(31, f.getTempMaxC());
        assertEquals(24, f.getTempMinC());
        assertFalse(f.isExactEventDate());
    }

    @Test
    public void parseDailyJsonReturnsNullOnMalformedInput() {
        assertNull(WeatherParser.parseDailyJson("", "2026-10-28"));
        assertNull(WeatherParser.parseDailyJson("{\"error\":true}", "2026-10-28"));
    }

    @Test
    public void describeWmoCodeCoversCommonConditions() {
        assertEquals("Clear sky", WeatherParser.describeWmoCode(0));
        assertEquals("Partly cloudy", WeatherParser.describeWmoCode(2));
        assertEquals("Thunderstorm", WeatherParser.describeWmoCode(95));
    }
}
