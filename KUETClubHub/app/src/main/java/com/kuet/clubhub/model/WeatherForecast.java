package com.kuet.clubhub.model;

/**
 * One day's campus weather forecast for KUET (Khulna), parsed from the free Open-Meteo API
 * (Proposal feature 21, Week 11 HTTP + JSON).
 */
public class WeatherForecast {

    private final String date;
    private final int weatherCode;
    private final String condition;
    private final int tempMaxC;
    private final int tempMinC;
    private final int rainChancePercent;
    private final int windMaxKmh;
    private final boolean exactEventDate;

    public WeatherForecast(String date, int weatherCode, String condition,
                           int tempMaxC, int tempMinC,
                           int rainChancePercent, int windMaxKmh,
                           boolean exactEventDate) {
        this.date = date;
        this.weatherCode = weatherCode;
        this.condition = condition;
        this.tempMaxC = tempMaxC;
        this.tempMinC = tempMinC;
        this.rainChancePercent = rainChancePercent;
        this.windMaxKmh = windMaxKmh;
        this.exactEventDate = exactEventDate;
    }

    public String getDate() { return date; }
    public int getWeatherCode() { return weatherCode; }
    public String getCondition() { return condition; }
    public int getTempMaxC() { return tempMaxC; }
    public int getTempMinC() { return tempMinC; }
    public int getRainChancePercent() { return rainChancePercent; }
    public int getWindMaxKmh() { return windMaxKmh; }

    /**
     * True when the forecast row matches the exact calendar day of the event; false when the event
     * is beyond the 16-day forecast horizon and today's campus weather is shown instead.
     */
    public boolean isExactEventDate() { return exactEventDate; }
}
