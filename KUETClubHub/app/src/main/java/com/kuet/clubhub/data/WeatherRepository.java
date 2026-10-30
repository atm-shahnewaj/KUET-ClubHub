package com.kuet.clubhub.data;

import android.os.Handler;
import android.os.Looper;

import com.kuet.clubhub.model.WeatherForecast;
import com.kuet.clubhub.util.Io;
import com.kuet.clubhub.util.WeatherParser;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Date;

/**
 * Fetches the 16-day weather forecast for KUET campus (Khulna, 22.9006°N, 89.5024°E) from the
 * free Open-Meteo HTTP JSON API (no API key needed; Proposal feature 21, Week 11 Networking).
 */
public final class WeatherRepository {

    private static final String ENDPOINT =
            "https://api.open-meteo.com/v1/forecast"
                    + "?latitude=22.9006&longitude=89.5024"
                    + "&daily=weather_code,temperature_2m_max,temperature_2m_min,"
                    + "precipitation_probability_max,wind_speed_10m_max"
                    + "&timezone=Asia%2FDhaka&forecast_days=16";

    private static final int TIMEOUT_MS = 8_000;
    private static final long CACHE_TTL_MS = 15 * 60 * 1_000L;

    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static String cachedJson;
    private static long cachedAtMillis;

    private WeatherRepository() {
    }

    /**
     * Loads the forecast for {@code eventDate} on a background thread and posts the result back on
     * the main thread. When {@code forceRefresh} is false, a recent response (under 15 minutes old)
     * is reused so rotating the screen does not repeat the network call.
     */
    public static void fetchForDate(final Date eventDate, final boolean forceRefresh,
                                    final Callback<WeatherForecast> callback) {
        final String targetIso = WeatherParser.isoDate(eventDate);
        final long now = System.currentTimeMillis();

        synchronized (WeatherRepository.class) {
            if (!forceRefresh && cachedJson != null && (now - cachedAtMillis) < CACHE_TTL_MS) {
                WeatherForecast cached = WeatherParser.parseDailyJson(cachedJson, targetIso);
                if (cached != null) {
                    MAIN.post(() -> callback.onSuccess(cached));
                    return;
                }
            }
        }

        Io.NETWORK.execute(() -> {
            HttpURLConnection conn = null;
            try {
                URL url = new URL(ENDPOINT);
                conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setConnectTimeout(TIMEOUT_MS);
                conn.setReadTimeout(TIMEOUT_MS);
                conn.setRequestProperty("Accept", "application/json");
                conn.setRequestProperty("User-Agent", "KUETClubHub/1.0");

                int status = conn.getResponseCode();
                if (status < 200 || status >= 300) {
                    postError(callback, "Weather service returned HTTP " + status + ".");
                    return;
                }
                String body = readStream(conn.getInputStream());
                WeatherForecast forecast = WeatherParser.parseDailyJson(body, targetIso);
                if (forecast == null) {
                    postError(callback, "Could not read the weather data.");
                    return;
                }
                synchronized (WeatherRepository.class) {
                    cachedJson = body;
                    cachedAtMillis = System.currentTimeMillis();
                }
                MAIN.post(() -> callback.onSuccess(forecast));
            } catch (IOException e) {
                postError(callback, "Could not load the weather forecast. Check your internet connection.");
            } finally {
                if (conn != null) {
                    conn.disconnect();
                }
            }
        });
    }

    private static void postError(Callback<WeatherForecast> cb, String message) {
        MAIN.post(() -> cb.onError(message));
    }

    private static String readStream(InputStream in) throws IOException {
        StringBuilder sb = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(in, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
        }
        return sb.toString();
    }
}
