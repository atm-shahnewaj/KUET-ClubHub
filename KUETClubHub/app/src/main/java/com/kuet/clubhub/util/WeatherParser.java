package com.kuet.clubhub.util;

import com.kuet.clubhub.model.WeatherForecast;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Pure-Java JSON parser and WMO weather-code mapper for the Open-Meteo daily forecast endpoint.
 * Implemented without Android framework dependencies so unit tests run directly under JUnit.
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
        String dailyBlock = extractObject(json, "\"daily\"");
        if (dailyBlock == null) {
            return null;
        }

        List<String> dates = extractStringArray(dailyBlock, "\"time\"");
        if (dates.isEmpty()) {
            return null;
        }
        List<Double> codes = extractNumberArray(dailyBlock, "\"weather_code\"");
        if (codes.isEmpty()) {
            codes = extractNumberArray(dailyBlock, "\"weathercode\"");
        }
        List<Double> maxTemps = extractNumberArray(dailyBlock, "\"temperature_2m_max\"");
        List<Double> minTemps = extractNumberArray(dailyBlock, "\"temperature_2m_min\"");
        List<Double> rainProbs = extractNumberArray(dailyBlock, "\"precipitation_probability_max\"");
        List<Double> winds = extractNumberArray(dailyBlock, "\"wind_speed_10m_max\"");
        if (winds.isEmpty()) {
            winds = extractNumberArray(dailyBlock, "\"windspeed_10m_max\"");
        }

        if (maxTemps.isEmpty() || minTemps.isEmpty()) {
            return null;
        }

        int index = 0;
        boolean exact = false;
        if (targetIsoDate != null && !targetIsoDate.isEmpty()) {
            for (int i = 0; i < dates.size(); i++) {
                if (targetIsoDate.equals(dates.get(i))) {
                    index = i;
                    exact = true;
                    break;
                }
            }
        }

        String date = dates.get(index);
        int code = index < codes.size() ? (int) Math.round(codes.get(index)) : 0;
        int maxC = (int) Math.round(maxTemps.get(Math.min(index, maxTemps.size() - 1)));
        int minC = (int) Math.round(minTemps.get(Math.min(index, minTemps.size() - 1)));
        int rain = index < rainProbs.size() ? clampPercent((int) Math.round(rainProbs.get(index))) : 0;
        int wind = index < winds.size() ? Math.max(0, (int) Math.round(winds.get(index))) : 0;

        return new WeatherForecast(date, code, describeWmoCode(code),
                maxC, minC, rain, wind, exact);
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

    private static String extractObject(String json, String key) {
        int keyPos = json.indexOf(key);
        if (keyPos < 0) {
            return null;
        }
        int braceStart = json.indexOf('{', keyPos + key.length());
        if (braceStart < 0) {
            return null;
        }
        int depth = 0;
        boolean inString = false;
        for (int i = braceStart; i < json.length(); i++) {
            char c = json.charAt(i);
            if (c == '"' && (i == 0 || json.charAt(i - 1) != '\\')) {
                inString = !inString;
            } else if (!inString) {
                if (c == '{') {
                    depth++;
                } else if (c == '}') {
                    depth--;
                    if (depth == 0) {
                        return json.substring(braceStart, i + 1);
                    }
                }
            }
        }
        return null;
    }

    private static String extractArrayContent(String block, String key) {
        int keyPos = block.indexOf(key);
        if (keyPos < 0) {
            return null;
        }
        int colon = block.indexOf(':', keyPos + key.length());
        if (colon < 0) {
            return null;
        }
        int start = block.indexOf('[', colon + 1);
        if (start < 0) {
            return null;
        }
        int end = block.indexOf(']', start + 1);
        if (end < 0) {
            return null;
        }
        return block.substring(start + 1, end);
    }

    private static List<String> extractStringArray(String block, String key) {
        String content = extractArrayContent(block, key);
        if (content == null || content.trim().isEmpty()) {
            return new ArrayList<>();
        }
        List<String> out = new ArrayList<>();
        String[] parts = content.split(",");
        for (String p : parts) {
            String cleaned = p.trim();
            if (cleaned.startsWith("\"") && cleaned.endsWith("\"") && cleaned.length() >= 2) {
                out.add(cleaned.substring(1, cleaned.length() - 1));
            }
        }
        return out;
    }

    private static List<Double> extractNumberArray(String block, String key) {
        String content = extractArrayContent(block, key);
        if (content == null || content.trim().isEmpty()) {
            return new ArrayList<>();
        }
        List<Double> out = new ArrayList<>();
        String[] parts = content.split(",");
        for (String p : parts) {
            String cleaned = p.trim();
            if (cleaned.isEmpty() || "null".equalsIgnoreCase(cleaned)) {
                out.add(0.0);
                continue;
            }
            try {
                out.add(Double.parseDouble(cleaned));
            } catch (NumberFormatException ignored) {
                return new ArrayList<>();
            }
        }
        return out;
    }
}
