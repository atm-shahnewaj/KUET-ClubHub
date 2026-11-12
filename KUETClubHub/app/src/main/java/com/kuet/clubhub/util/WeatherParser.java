package com.kuet.clubhub.util;

import com.kuet.clubhub.model.WeatherForecast;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Parses the Open-Meteo daily forecast JSON with {@code org.json} ({@link JSONObject} and
 * {@link JSONArray}) and maps WMO weather codes to readable descriptions. The unit tests run
 * under plain JUnit using the {@code org.json:json} test dependency.
 */
public final class WeatherParser {

    private WeatherParser() {
    }

    /** Formats a Date as {@code yyyy-MM-dd} in the device's local time zone. */
    public static String isoDate(Date d) {
        if (d == null) {
            return "";
        }
        return new SimpleDateFormat("yyyy-MM-dd", Locale.ENGLISH).format(d);
    }

    /**
     * Parses the Open-Meteo daily JSON payload and picks the forecast for {@code targetIsoDate}
     * ({@code yyyy-MM-dd}). If {@code targetIsoDate} is outside the 16-day forecast window,
     * returns the first day's forecast with {@link WeatherForecast#isExactEventDate()} set to
     * {@code false}. Returns {@code null} if the JSON is malformed or contains no daily entries.
     */
    public static WeatherForecast parseDailyJson(String json, String targetIsoDate) {
        if (json == null || json.isEmpty()) {
            return null;
        }
        final JSONObject daily;
        try {
            daily = new JSONObject(json).optJSONObject("daily");
        } catch (JSONException e) {
            return null;
        }
        if (daily == null) {
            return null;
        }

        JSONArray dates = daily.optJSONArray("time");
        if (dates == null || dates.length() == 0) {
            return null;
        }
        List<Double> codes = numbers(daily, "weather_code", "weathercode");
        List<Double> maxTemps = numbers(daily, "temperature_2m_max");
        List<Double> minTemps = numbers(daily, "temperature_2m_min");
        List<Double> rainProbs = numbers(daily, "precipitation_probability_max");
        List<Double> winds = numbers(daily, "wind_speed_10m_max", "windspeed_10m_max");

        if (maxTemps.isEmpty() || minTemps.isEmpty()) {
            return null;
        }

        int index = 0;
        boolean exact = false;
        if (targetIsoDate != null && !targetIsoDate.isEmpty()) {
            for (int i = 0; i < dates.length(); i++) {
                if (targetIsoDate.equals(dates.optString(i))) {
                    index = i;
                    exact = true;
                    break;
                }
            }
        }

        String date = dates.optString(index);
        int code = index < codes.size() ? (int) Math.round(codes.get(index)) : 0;
        int maxC = (int) Math.round(maxTemps.get(Math.min(index, maxTemps.size() - 1)));
        int minC = (int) Math.round(minTemps.get(Math.min(index, minTemps.size() - 1)));
        int rain = index < rainProbs.size() ? clampPercent((int) Math.round(rainProbs.get(index))) : 0;
        int wind = index < winds.size() ? Math.max(0, (int) Math.round(winds.get(index))) : 0;

        return new WeatherForecast(date, code, describeWmoCode(code),
                maxC, minC, rain, wind, exact);
    }

    /**
     * Reads the first of {@code keys} that exists in {@code daily} as a list of numbers (the API
     * sends {@code null} for missing values, which become 0). Older API versions used different
     * key names, so a second name can be given as a fallback.
     */
    private static List<Double> numbers(JSONObject daily, String... keys) {
        for (String key : keys) {
            JSONArray array = daily.optJSONArray(key);
            if (array == null || array.length() == 0) {
                continue;
            }
            List<Double> out = new ArrayList<>(array.length());
            for (int i = 0; i < array.length(); i++) {
                out.add(array.optDouble(i, 0.0));
            }
            return out;
        }
        return new ArrayList<>();
    }

    /** Maps standard WMO weather interpretation codes (WW) to clear English descriptions. */
    public static String describeWmoCode(int code) {
        switch (code) {
            case 0:
                return "Clear sky";
            case 1:
                return "Mainly clear";
            case 2:
                return "Partly cloudy";
            case 3:
                return "Overcast";
            case 45:
            case 48:
                return "Foggy";
            case 51:
            case 53:
            case 55:
            case 56:
            case 57:
                return "Light drizzle";
            case 61:
            case 63:
            case 66:
                return "Rain";
            case 65:
            case 67:
                return "Heavy rain";
            case 71:
            case 73:
            case 75:
            case 77:
                return "Snow";
            case 80:
            case 81:
                return "Rain showers";
            case 82:
                return "Heavy showers";
            case 95:
            case 96:
            case 99:
                return "Thunderstorm";
            default:
                return "Fair weather";
        }
    }

    private static int clampPercent(int value) {
        return Math.max(0, Math.min(100, value));
    }
}
