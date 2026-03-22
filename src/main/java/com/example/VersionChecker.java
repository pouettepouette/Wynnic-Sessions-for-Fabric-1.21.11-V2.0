package com.example;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

public class VersionChecker {
    private static final String GITHUB_API_URL = "https://api.github.com/repos/samuel/mobfarmsessions/releases";
    private static final String GITHUB_PROFILE_URL = "https://github.com/pouettepouette?tab=repositories";
    private static final String CURRENT_VERSION = "2.0";
    
    private static String latestVersion = null;
    private static String latestDownloadUrl = null;
    private static boolean isUpdateAvailable = false;
    private static boolean hasChecked = false;
    private static long lastCheckTimeMs = 0;
    private static final long CHECK_COOLDOWN_MS = 60000; // 1 minute cooldown
    
    public static void checkLatestVersion() {
        long now = System.currentTimeMillis();
        if (hasChecked && (now - lastCheckTimeMs) < CHECK_COOLDOWN_MS) {
            return;
        }
        new Thread(() -> {
            try {
                HttpClient client = HttpClient.newHttpClient();
                HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(GITHUB_API_URL))
                    .timeout(java.time.Duration.ofSeconds(5))
                    .GET()
                    .build();
                
                HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
                
                if (response.statusCode() == 200) {
                    JsonArray releases = JsonParser.parseString(response.body()).getAsJsonArray();
                    if (releases.size() > 0) {
                        JsonObject latestRelease = releases.get(0).getAsJsonObject();
                        latestVersion = latestRelease.get("tag_name").getAsString();
                        JsonArray assets = latestRelease.getAsJsonArray("assets");
                        if (assets != null && assets.size() > 0) {
                            for (JsonElement asset : assets) {
                                JsonObject assetObj = asset.getAsJsonObject();
                                String name = assetObj.get("name").getAsString();
                                if (name.endsWith(".jar")) {
                                    latestDownloadUrl = assetObj.get("browser_download_url").getAsString();
                                    break;
                                }
                            }
                        }
                        
                        isUpdateAvailable = compareVersions(CURRENT_VERSION, latestVersion) < 0;
                    }
                }
                
                hasChecked = true;
                lastCheckTimeMs = now;
            } catch (Exception e) {
                hasChecked = true;
                lastCheckTimeMs = now;
            }
        }).start();
    }
    
    private static int compareVersions(String v1, String v2) {
        String clean1 = v1.replaceAll("^v", "");
        String clean2 = v2.replaceAll("^v", "");
        
        String[] parts1 = clean1.split("\\.");
        String[] parts2 = clean2.split("\\.");
        
        int len = Math.max(parts1.length, parts2.length);
        for (int i = 0; i < len; i++) {
            int p1 = i < parts1.length ? parseInt(parts1[i]) : 0;
            int p2 = i < parts2.length ? parseInt(parts2[i]) : 0;
            if (p1 != p2) {
                return Integer.compare(p1, p2);
            }
        }
        return 0;
    }
    
    private static int parseInt(String s) {
        try {
            return Integer.parseInt(s);
        } catch (Exception e) {
            return 0;
        }
    }
    
    public static String getCurrentVersion() {
        return CURRENT_VERSION;
    }
    
    public static String getLatestVersion() {
        return latestVersion != null ? latestVersion : "Unknown";
    }
    
    public static String getDownloadUrl() {
        return GITHUB_PROFILE_URL;
    }
    
    public static boolean hasChecked() {
        return hasChecked;
    }
    
    public static boolean isUpdateAvailable() {
        return isUpdateAvailable;
    }
    
    public static String getStatus() {
        if (!hasChecked) {
            return "Checking...";
        }
        if (isUpdateAvailable) {
            return "Update available: " + getLatestVersion();
        }
        return "Up to date";
    }
    
    public static void resetCheck() {
        hasChecked = false;
        latestVersion = null;
        latestDownloadUrl = null;
        isUpdateAvailable = false;
    }
}
