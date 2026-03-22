package com.example;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class WynncraftApiClient {
    private final long callIntervalMs;
    private final HttpClient httpClient;
    private final ExecutorService ioExecutor;
    private long lastApiCallTimeMs;
    private volatile long lastResponseTimeMs;

    public interface ApiResultHandler {
        void onSuccess(long totalKills);

        void onError(String status);
    }

    public interface ServerStatusHandler {
        void onSuccess(Object result);

        void onError(String status);
    }

    public WynncraftApiClient(long callIntervalMs) {
        this.callIntervalMs = callIntervalMs;
        this.httpClient = HttpClient.newHttpClient();
        this.ioExecutor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "wynncraft-api-client");
            t.setDaemon(true);
            return t;
        });
        this.lastApiCallTimeMs = 0;
        this.lastResponseTimeMs = -1;
    }

    public void resetThrottle() {
        lastApiCallTimeMs = 0;
    }

    public long getLastResponseTimeMs() {
        return lastResponseTimeMs;
    }

    public void fetchMobsKilledIfDue(String playerName, ApiResultHandler handler) {
        long now = System.currentTimeMillis();
        synchronized (this) {
            if (now - lastApiCallTimeMs < callIntervalMs) {
                return;
            }
            lastApiCallTimeMs = now;
        }

        ioExecutor.execute(() -> fetchMobsKilled(playerName, handler));
    }

    private void fetchMobsKilled(String playerName, ApiResultHandler handler) {
        try {
            long requestStartMs = System.currentTimeMillis();
            String encodedPlayerName = URLEncoder.encode(playerName, StandardCharsets.UTF_8);
            String apiUrl = "https://api.wynncraft.com/v3/player/" + encodedPlayerName + "?fullResult";

            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(apiUrl))
                .GET()
                .header("User-Agent", "MobFarmSessions/1.0")
                .timeout(java.time.Duration.ofSeconds(5))
                .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            lastResponseTimeMs = Math.max(0, System.currentTimeMillis() - requestStartMs);
            if (response.statusCode() != 200) {
                handler.onError("API Error");
                return;
            }

            JsonObject root = JsonParser.parseString(response.body()).getAsJsonObject();
            if (!root.has("globalData")) {
                handler.onError("Response Error");
                return;
            }

            JsonObject globalData = root.getAsJsonObject("globalData");
            if (globalData.has("killedMobs")) {
                handler.onSuccess(globalData.get("killedMobs").getAsLong());
                return;
            }

            if (globalData.has("mobsKilled")) {
                handler.onSuccess(globalData.get("mobsKilled").getAsLong());
                return;
            }

            handler.onError("Data Error");
        } catch (Exception exception) {
            handler.onError("Offline");
        }
    }

    public void fetchServerStatus(ServerStatusHandler handler) {
        ioExecutor.execute(() -> checkServerStatus(handler));
    }

    private void checkServerStatus(ServerStatusHandler handler) {
        try {
            String apiUrl = "https://api.wynncraft.com/v3/server/info";

            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(apiUrl))
                .GET()
                .header("User-Agent", "MobFarmSessions/1.0")
                .timeout(java.time.Duration.ofSeconds(5))
                .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                handler.onError("API Error");
                return;
            }

            JsonObject root = JsonParser.parseString(response.body()).getAsJsonObject();
            handler.onSuccess(root);
        } catch (Exception exception) {
            handler.onError("Offline");
        }
    }

    public void shutdown() {
        if (!ioExecutor.isShutdown()) {
            ioExecutor.shutdown();
            try {
                if (!ioExecutor.awaitTermination(5, java.util.concurrent.TimeUnit.SECONDS)) {
                    ioExecutor.shutdownNow();
                }
            } catch (InterruptedException e) {
                ioExecutor.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
    }
}
