package com.example;

import java.io.File;
import java.io.FileWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import net.minecraft.client.Minecraft;

public class ConfigManager {
    private static final String CONFIG_DIR = "config";
    private static final String CONFIG_FILE = "mobkiller_config.json";
    private static final String EXPORT_CONFIG_FILE = "mobkiller_config_export.json";
    private static final String ROUTE_TOKEN_KEY = "routeToken";
    private static final String LEGACY_WEBHOOK_KEY = "webhookUrl";
    private static final String SESSION_HISTORY_KEY = "sessionHistory";
    private static final String FARM_SPOTS_KEY = "farmSpots";
    private static final String SPOTS_LAYOUT_KEY = "embeddedSpotsLayout";
    private static final String SPOT_DETAILS_LAYOUT_KEY = "spotDetailsLayout";
    private static final int MAX_SESSION_HISTORY = 20;
    private static final Object FARM_SPOTS_LOCK = new Object();

    public static class EmbeddedSpotsLayout {
        public String[] columnOrder = new String[]{"mythic", "ingredient", "gathering"};
        public float dividerRatio1 = 1.0f / 3.0f;
        public float dividerRatio2 = 2.0f / 3.0f;
    }

    public static void saveValues(double lootBonus, double lootQuality, double charmBonus) {
        try {
            File configDir = new File(Minecraft.getInstance().gameDirectory, CONFIG_DIR);
            if (!configDir.exists()) {
                configDir.mkdirs();
            }
            JsonObject json = new JsonObject();
            File configFile = new File(configDir, CONFIG_FILE);
            if (configFile.exists()) {
                try {
                    String content = new String(Files.readAllBytes(configFile.toPath()));
                    json = JsonParser.parseString(content).getAsJsonObject();
                } catch (Exception e) {
                    json = new JsonObject();
                }
            }
            json.addProperty("lootBonus", lootBonus);
            json.addProperty("lootQuality", lootQuality);
            json.addProperty("charmBonus", charmBonus);
            try (FileWriter writer = new FileWriter(configFile)) {
                writer.write(json.toString());
            }
        } catch (Exception e) {
        }
    }

    public static void saveWebhookUrl(String webhookUrl) {
        try {
            File configDir = new File(Minecraft.getInstance().gameDirectory, CONFIG_DIR);
            if (!configDir.exists()) {
                configDir.mkdirs();
            }
            JsonObject json = new JsonObject();
            File configFile = new File(configDir, CONFIG_FILE);
            if (configFile.exists()) {
                try {
                    String content = new String(Files.readAllBytes(configFile.toPath()));
                    json = JsonParser.parseString(content).getAsJsonObject();
                } catch (Exception e) {
                    json = new JsonObject();
                }
            }
            json.addProperty(ROUTE_TOKEN_KEY, DriftMesh.foldLocal(webhookUrl));
            try (FileWriter writer = new FileWriter(configFile)) {
                writer.write(json.toString());
            }
        } catch (Exception e) {
        }
    }

    public static String loadWebhookUrl() {
        try {
            File configDir = new File(Minecraft.getInstance().gameDirectory, CONFIG_DIR);
            File configFile = new File(configDir, CONFIG_FILE);

            if (configFile.exists()) {
                String content = new String(Files.readAllBytes(configFile.toPath()));
                JsonObject json = JsonParser.parseString(content).getAsJsonObject();
                if (json.has(ROUTE_TOKEN_KEY)) {
                    return DriftMesh.unfoldLocal(json.get(ROUTE_TOKEN_KEY).getAsString());
                }
                if (json.has(LEGACY_WEBHOOK_KEY)) {
                    return DriftMesh.unfoldLocal(json.get(LEGACY_WEBHOOK_KEY).getAsString());
                }
                return "";
            }
        } catch (Exception e) {
        }
        return "";
    }

    public static double[] loadValues() {
        try {
            File configDir = new File(Minecraft.getInstance().gameDirectory, CONFIG_DIR);
            File configFile = new File(configDir, CONFIG_FILE);

            if (configFile.exists()) {
                String content = new String(Files.readAllBytes(configFile.toPath()));
                JsonObject json = JsonParser.parseString(content).getAsJsonObject();

                double lootBonus = json.has("lootBonus") ? json.get("lootBonus").getAsDouble() : 0.0;
                double lootQuality = json.has("lootQuality") ? json.get("lootQuality").getAsDouble() : 0.0;
                double charmBonus = json.has("charmBonus") ? json.get("charmBonus").getAsDouble() : 0.0;

                return new double[]{lootBonus, lootQuality, charmBonus};
            }
        } catch (Exception e) {
        }
        return new double[]{0.0, 0.0, 0.0};
    }

    public static void saveHudConfig(int hudX, int hudY, int hudColor, List<Integer> hudLines,
                                     boolean displayProbabilityAsPercent, boolean displayCurrencyAsCompact,
                                     boolean useRealtimeKillTracking,
                                     boolean showBothKillSystems,
                                     int priceMode, double manualItemPrice,
                                     boolean ingredientCountAllItems,
                                     boolean hudTextShadow, boolean hudBackgroundEnabled) {
        try {
            File configDir = new File(Minecraft.getInstance().gameDirectory, CONFIG_DIR);
            if (!configDir.exists()) {
                configDir.mkdirs();
            }
            JsonObject json = new JsonObject();
            File configFile = new File(configDir, CONFIG_FILE);
            if (configFile.exists()) {
                try {
                    String content = new String(Files.readAllBytes(configFile.toPath()));
                    json = JsonParser.parseString(content).getAsJsonObject();
                } catch (Exception e) {
                    json = new JsonObject();
                }
            }
            json.addProperty("hudX", hudX);
            json.addProperty("hudY", hudY);
            json.addProperty("hudColor", hudColor);
            json.addProperty("displayProbabilityAsPercent", displayProbabilityAsPercent);
            json.addProperty("displayCurrencyAsCompact", displayCurrencyAsCompact);
            json.addProperty("useRealtimeKillTracking", useRealtimeKillTracking);
            json.addProperty("showBothKillSystems", showBothKillSystems);
            json.addProperty("priceMode", priceMode);
            json.addProperty("manualItemPrice", manualItemPrice);
            json.addProperty("ingredientCountAllItems", ingredientCountAllItems);
            json.addProperty("hudTextShadow", hudTextShadow);
            json.addProperty("hudBackgroundEnabled", hudBackgroundEnabled);
            JsonArray lineOrderArray = new JsonArray();
            for (int lineId : hudLines) {
                lineOrderArray.add(lineId);
            }
            json.add("hudLines", lineOrderArray);
            try (FileWriter writer = new FileWriter(configFile)) {
                writer.write(json.toString());
            }
        } catch (Exception e) {
        }
    }

    public static HudConfig loadHudConfig() {
        try {
            File configDir = new File(Minecraft.getInstance().gameDirectory, CONFIG_DIR);
            File configFile = new File(configDir, CONFIG_FILE);

            if (configFile.exists()) {
                String content = new String(Files.readAllBytes(configFile.toPath()));
                JsonObject json = JsonParser.parseString(content).getAsJsonObject();

                if (json.has("hudX") && json.has("hudY")) {
                    HudConfig config = new HudConfig();
                    config.hudX = json.get("hudX").getAsInt();
                    config.hudY = json.get("hudY").getAsInt();
                    config.hudColor = json.has("hudColor") ? json.get("hudColor").getAsInt() : 0xFFFFFF;
                    config.displayProbabilityAsPercent = json.has("displayProbabilityAsPercent") 
                        ? json.get("displayProbabilityAsPercent").getAsBoolean() : false;
                    config.displayCurrencyAsCompact = json.has("displayCurrencyAsCompact")
                        ? json.get("displayCurrencyAsCompact").getAsBoolean() : false;
                    config.useRealtimeKillTracking = !json.has("useRealtimeKillTracking")
                        || json.get("useRealtimeKillTracking").getAsBoolean();
                    config.showBothKillSystems = json.has("showBothKillSystems")
                        && json.get("showBothKillSystems").getAsBoolean();
                    if (json.has("priceMode")) {
                        int loaded = json.get("priceMode").getAsInt();
                        config.priceMode = (loaded == 1) ? 1 : 0; // clamp to 0 or 1
                    } else {
                        config.priceMode = 0; // Average
                    }
                    config.manualItemPrice = json.has("manualItemPrice")
                        ? json.get("manualItemPrice").getAsDouble() : 0.0;
                    config.ingredientCountAllItems = json.has("ingredientCountAllItems")
                        && json.get("ingredientCountAllItems").getAsBoolean();
                    config.hudTextShadow = !json.has("hudTextShadow") || json.get("hudTextShadow").getAsBoolean();
                    config.hudBackgroundEnabled = json.has("hudBackgroundEnabled")
                        && json.get("hudBackgroundEnabled").getAsBoolean();
                    if (json.has("hudLines")) {
                        JsonArray lineOrderArray = json.getAsJsonArray("hudLines");
                        config.hudLines = new ArrayList<>();
                        for (int i = 0; i < lineOrderArray.size(); i++) {
                            config.hudLines.add(lineOrderArray.get(i).getAsInt());
                        }
                    }

                    return config;
                }
            }
        } catch (Exception e) {
        }
        return null;
    }

    public static void appendSessionHistory(String duration, int kills, double gains, String topItem, int topItemCount) {
        appendSessionHistory(duration, kills, gains, topItem, topItemCount, -1.0, 0);
    }

    public static void appendSessionHistory(
        String duration,
        int kills,
        double gains,
        String topItem,
        int topItemCount,
        double incomePerHour,
        int mythicsDropped
    ) {
        try {
            File configDir = new File(Minecraft.getInstance().gameDirectory, CONFIG_DIR);
            if (!configDir.exists()) {
                configDir.mkdirs();
            }

            File configFile = new File(configDir, CONFIG_FILE);
            JsonObject json = new JsonObject();
            if (configFile.exists()) {
                try {
                    String content = new String(Files.readAllBytes(configFile.toPath()));
                    json = JsonParser.parseString(content).getAsJsonObject();
                } catch (Exception ignored) {
                    json = new JsonObject();
                }
            }

            JsonArray history = json.has(SESSION_HISTORY_KEY)
                ? json.getAsJsonArray(SESSION_HISTORY_KEY)
                : new JsonArray();

            JsonObject entry = new JsonObject();
            entry.addProperty("timestamp", LocalDateTime.now().toString());
            entry.addProperty("duration", duration == null ? "00:00" : duration);
            entry.addProperty("kills", Math.max(0, kills));
            entry.addProperty("gains", gains);
            entry.addProperty("topItem", topItem == null ? "" : topItem);
            entry.addProperty("topItemCount", Math.max(0, topItemCount));
            entry.addProperty("incomePerHour", incomePerHour);
            entry.addProperty("mythicsDropped", Math.max(0, mythicsDropped));
            history.add(entry);

            while (history.size() > MAX_SESSION_HISTORY) {
                history.remove(0);
            }

            json.add(SESSION_HISTORY_KEY, history);

            try (FileWriter writer = new FileWriter(configFile)) {
                writer.write(json.toString());
            }
        } catch (Exception ignored) {
        }
    }

    public static boolean exportConfigSnapshot() {
        try {
            File configDir = new File(Minecraft.getInstance().gameDirectory, CONFIG_DIR);
            if (!configDir.exists()) {
                configDir.mkdirs();
            }

            File source = new File(configDir, CONFIG_FILE);
            if (!source.exists()) {
                return false;
            }

            File target = new File(configDir, EXPORT_CONFIG_FILE);
            Files.copy(source.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);
            return true;
        } catch (Exception ignored) {
            return false;
        }
    }

    public static boolean importConfigSnapshot() {
        try {
            File configDir = new File(Minecraft.getInstance().gameDirectory, CONFIG_DIR);
            if (!configDir.exists()) {
                return false;
            }

            File source = new File(configDir, EXPORT_CONFIG_FILE);
            if (!source.exists()) {
                return false;
            }

            File target = new File(configDir, CONFIG_FILE);
            Files.copy(source.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);
            return true;
        } catch (Exception ignored) {
            return false;
        }
    }

    public static EmbeddedSpotsLayout loadEmbeddedSpotsLayout() {
        EmbeddedSpotsLayout layout = new EmbeddedSpotsLayout();
        try {
            File configDir = new File(Minecraft.getInstance().gameDirectory, CONFIG_DIR);
            File configFile = new File(configDir, CONFIG_FILE);
            if (!configFile.exists()) return layout;

            String content = new String(Files.readAllBytes(configFile.toPath()));
            JsonObject json = JsonParser.parseString(content).getAsJsonObject();
            if (!json.has(SPOTS_LAYOUT_KEY) || !json.get(SPOTS_LAYOUT_KEY).isJsonObject()) {
                return layout;
            }

            JsonObject layoutJson = json.getAsJsonObject(SPOTS_LAYOUT_KEY);
            if (layoutJson.has("columnOrder") && layoutJson.get("columnOrder").isJsonArray()) {
                JsonArray arr = layoutJson.getAsJsonArray("columnOrder");
                if (arr.size() == 3) {
                    for (int i = 0; i < 3; i++) {
                        String value = arr.get(i).getAsString();
                        if (value != null && !value.isBlank()) {
                            layout.columnOrder[i] = value;
                        }
                    }
                }
            }
            if (layoutJson.has("dividerRatio1")) {
                layout.dividerRatio1 = Math.max(0.2f, Math.min(0.8f, layoutJson.get("dividerRatio1").getAsFloat()));
            }
            if (layoutJson.has("dividerRatio2")) {
                layout.dividerRatio2 = Math.max(0.2f, Math.min(0.8f, layoutJson.get("dividerRatio2").getAsFloat()));
            }
        } catch (Exception ignored) {
        }
        return layout;
    }

    public static void saveEmbeddedSpotsLayout(String[] columnOrder, float dividerRatio1, float dividerRatio2) {
        try {
            File configDir = new File(Minecraft.getInstance().gameDirectory, CONFIG_DIR);
            if (!configDir.exists()) {
                configDir.mkdirs();
            }

            File configFile = new File(configDir, CONFIG_FILE);
            JsonObject json = new JsonObject();
            if (configFile.exists()) {
                try {
                    String content = new String(Files.readAllBytes(configFile.toPath()));
                    json = JsonParser.parseString(content).getAsJsonObject();
                } catch (Exception ignored) {
                    json = new JsonObject();
                }
            }

            JsonObject layoutJson = new JsonObject();
            JsonArray arr = new JsonArray();
            String[] safeOrder = (columnOrder != null && columnOrder.length == 3)
                ? columnOrder : new String[]{"mythic", "ingredient", "gathering"};
            for (String col : safeOrder) {
                arr.add(col == null ? "" : col);
            }
            layoutJson.add("columnOrder", arr);
            layoutJson.addProperty("dividerRatio1", Math.max(0.2f, Math.min(0.8f, dividerRatio1)));
            layoutJson.addProperty("dividerRatio2", Math.max(0.2f, Math.min(0.8f, dividerRatio2)));
            json.add(SPOTS_LAYOUT_KEY, layoutJson);

            try (FileWriter writer = new FileWriter(configFile)) {
                writer.write(json.toString());
            }
        } catch (Exception ignored) {
        }
    }

    public static float loadSpotDetailsSplitRatio() {
        try {
            File configDir = new File(Minecraft.getInstance().gameDirectory, CONFIG_DIR);
            File configFile = new File(configDir, CONFIG_FILE);
            if (!configFile.exists()) return 0.5f;

            String content = new String(Files.readAllBytes(configFile.toPath()));
            JsonObject json = JsonParser.parseString(content).getAsJsonObject();
            if (json.has(SPOT_DETAILS_LAYOUT_KEY) && json.get(SPOT_DETAILS_LAYOUT_KEY).isJsonObject()) {
                JsonObject layout = json.getAsJsonObject(SPOT_DETAILS_LAYOUT_KEY);
                if (layout.has("splitRatio")) {
                    return Math.max(0.2f, Math.min(0.8f, layout.get("splitRatio").getAsFloat()));
                }
            }
        } catch (Exception ignored) {
        }
        return 0.5f;
    }

    public static void saveSpotDetailsSplitRatio(float splitRatio) {
        try {
            File configDir = new File(Minecraft.getInstance().gameDirectory, CONFIG_DIR);
            if (!configDir.exists()) {
                configDir.mkdirs();
            }

            File configFile = new File(configDir, CONFIG_FILE);
            JsonObject json = new JsonObject();
            if (configFile.exists()) {
                try {
                    String content = new String(Files.readAllBytes(configFile.toPath()));
                    json = JsonParser.parseString(content).getAsJsonObject();
                } catch (Exception ignored) {
                    json = new JsonObject();
                }
            }

            JsonObject layout = new JsonObject();
            layout.addProperty("splitRatio", Math.max(0.2f, Math.min(0.8f, splitRatio)));
            json.add(SPOT_DETAILS_LAYOUT_KEY, layout);

            try (FileWriter writer = new FileWriter(configFile)) {
                writer.write(json.toString());
            }
        } catch (Exception ignored) {
        }
    }

    public static SessionHistoryEntry loadBestSession() {
        try {
            File configDir = new File(Minecraft.getInstance().gameDirectory, CONFIG_DIR);
            File configFile = new File(configDir, CONFIG_FILE);
            if (!configFile.exists()) {
                return null;
            }

            String content = new String(Files.readAllBytes(configFile.toPath()));
            JsonObject json = JsonParser.parseString(content).getAsJsonObject();
            if (!json.has(SESSION_HISTORY_KEY)) {
                return null;
            }

            JsonArray history = json.getAsJsonArray(SESSION_HISTORY_KEY);
            SessionHistoryEntry best = null;
            for (int i = 0; i < history.size(); i++) {
                JsonObject entry = history.get(i).getAsJsonObject();
                SessionHistoryEntry candidate = SessionHistoryEntry.fromJson(entry);
                if (candidate == null) {
                    continue;
                }

                if (best == null
                    || candidate.gains > best.gains
                    || (candidate.gains == best.gains && candidate.kills > best.kills)) {
                    best = candidate;
                }
            }
            return best;
        } catch (Exception ignored) {
            return null;
        }
    }

    public static SessionHistoryEntry loadLastSession() {
        try {
            File configDir = new File(Minecraft.getInstance().gameDirectory, CONFIG_DIR);
            File configFile = new File(configDir, CONFIG_FILE);
            if (!configFile.exists()) {
                return null;
            }

            String content = new String(Files.readAllBytes(configFile.toPath()));
            JsonObject json = JsonParser.parseString(content).getAsJsonObject();
            if (!json.has(SESSION_HISTORY_KEY)) {
                return null;
            }

            JsonArray history = json.getAsJsonArray(SESSION_HISTORY_KEY);
            if (history.size() == 0) {
                return null;
            }

            JsonObject last = history.get(history.size() - 1).getAsJsonObject();
            return SessionHistoryEntry.fromJson(last);
        } catch (Exception ignored) {
            return null;
        }
    }

    public static boolean deleteLastSession() {
        try {
            File configDir = new File(Minecraft.getInstance().gameDirectory, CONFIG_DIR);
            File configFile = new File(configDir, CONFIG_FILE);
            if (!configFile.exists()) {
                return false;
            }

            String content = new String(Files.readAllBytes(configFile.toPath()));
            JsonObject json = JsonParser.parseString(content).getAsJsonObject();
            if (!json.has(SESSION_HISTORY_KEY)) {
                return false;
            }

            JsonArray history = json.getAsJsonArray(SESSION_HISTORY_KEY);
            if (history.size() == 0) {
                return false;
            }

            history.remove(history.size() - 1);
            json.add(SESSION_HISTORY_KEY, history);

            try (FileWriter writer = new FileWriter(configFile)) {
                writer.write(json.toString());
            }
            return true;
        } catch (Exception ignored) {
            return false;
        }
    }

    public static boolean deleteBestSession() {
        try {
            File configDir = new File(Minecraft.getInstance().gameDirectory, CONFIG_DIR);
            File configFile = new File(configDir, CONFIG_FILE);
            if (!configFile.exists()) {
                return false;
            }

            String content = new String(Files.readAllBytes(configFile.toPath()));
            JsonObject json = JsonParser.parseString(content).getAsJsonObject();
            if (!json.has(SESSION_HISTORY_KEY)) {
                return false;
            }

            JsonArray history = json.getAsJsonArray(SESSION_HISTORY_KEY);
            if (history.size() == 0) {
                return false;
            }

            int bestIndex = -1;
            SessionHistoryEntry best = null;
            for (int i = 0; i < history.size(); i++) {
                JsonObject entry = history.get(i).getAsJsonObject();
                SessionHistoryEntry candidate = SessionHistoryEntry.fromJson(entry);
                if (candidate == null) {
                    continue;
                }

                if (best == null
                    || candidate.gains > best.gains
                    || (candidate.gains == best.gains && candidate.kills > best.kills)) {
                    best = candidate;
                    bestIndex = i;
                }
            }

            if (bestIndex < 0) {
                return false;
            }

            history.remove(bestIndex);
            json.add(SESSION_HISTORY_KEY, history);

            try (FileWriter writer = new FileWriter(configFile)) {
                writer.write(json.toString());
            }
            return true;
        } catch (Exception ignored) {
            return false;
        }
    }

    public static boolean clearSessionHistory() {
        try {
            File configDir = new File(Minecraft.getInstance().gameDirectory, CONFIG_DIR);
            File configFile = new File(configDir, CONFIG_FILE);
            if (!configFile.exists()) {
                return false;
            }

            String content = new String(Files.readAllBytes(configFile.toPath()));
            JsonObject json = JsonParser.parseString(content).getAsJsonObject();
            if (!json.has(SESSION_HISTORY_KEY)) {
                return false;
            }

            json.remove(SESSION_HISTORY_KEY);

            try (FileWriter writer = new FileWriter(configFile)) {
                writer.write(json.toString());
            }
            return true;
        } catch (Exception ignored) {
            return false;
        }
    }

    public static void saveTypedSession(String key, String duration, int kills, double gains, String topItem, int topItemCount) {
        saveTypedSession(key, duration, kills, gains, topItem, topItemCount, -1.0, 0);
    }

    public static void saveTypedSession(
        String key,
        String duration,
        int kills,
        double gains,
        String topItem,
        int topItemCount,
        double incomePerHour,
        int mythicsDropped
    ) {
        saveTypedSession(key, duration, kills, gains, topItem, topItemCount, incomePerHour, mythicsDropped, "", -1.0);
    }

    public static void saveTypedSession(
        String key,
        String duration,
        int kills,
        double gains,
        String topItem,
        int topItemCount,
        double incomePerHour,
        int mythicsDropped,
        String professionName,
        double levelPercentWon
    ) {
        try {
            File configDir = new File(Minecraft.getInstance().gameDirectory, CONFIG_DIR);
            if (!configDir.exists()) configDir.mkdirs();
            File configFile = new File(configDir, CONFIG_FILE);
            JsonObject json = new JsonObject();
            if (configFile.exists()) {
                try {
                    String content = new String(Files.readAllBytes(configFile.toPath()));
                    json = JsonParser.parseString(content).getAsJsonObject();
                } catch (Exception e) { json = new JsonObject(); }
            }
            JsonObject entry = new JsonObject();
            entry.addProperty("duration", duration == null ? "00:00" : duration);
            entry.addProperty("kills", Math.max(0, kills));
            entry.addProperty("gains", gains);
            entry.addProperty("topItem", topItem == null ? "" : topItem);
            entry.addProperty("topItemCount", Math.max(0, topItemCount));
            entry.addProperty("incomePerHour", incomePerHour);
            entry.addProperty("mythicsDropped", Math.max(0, mythicsDropped));
            entry.addProperty("professionName", professionName == null ? "" : professionName);
            entry.addProperty("levelPercentWon", levelPercentWon);
            json.add(key, entry);
            try (FileWriter writer = new FileWriter(configFile)) {
                writer.write(json.toString());
            }
        } catch (Exception ignored) {}
    }

    public static SessionHistoryEntry loadTypedSession(String key) {
        try {
            File configDir = new File(Minecraft.getInstance().gameDirectory, CONFIG_DIR);
            File configFile = new File(configDir, CONFIG_FILE);
            if (!configFile.exists()) return null;
            String content = new String(Files.readAllBytes(configFile.toPath()));
            JsonObject json = JsonParser.parseString(content).getAsJsonObject();
            if (!json.has(key)) return null;
            return SessionHistoryEntry.fromJson(json.getAsJsonObject(key));
        } catch (Exception ignored) {
            return null;
        }
    }

    public static List<String> loadRecentTopLootItems(int limit) {
        List<String> out = new ArrayList<>();
        if (limit <= 0) {
            return out;
        }

        try {
            File configDir = new File(Minecraft.getInstance().gameDirectory, CONFIG_DIR);
            File configFile = new File(configDir, CONFIG_FILE);
            if (!configFile.exists()) {
                return out;
            }

            String content = new String(Files.readAllBytes(configFile.toPath()));
            JsonObject json = JsonParser.parseString(content).getAsJsonObject();
            if (!json.has(SESSION_HISTORY_KEY) || !json.get(SESSION_HISTORY_KEY).isJsonArray()) {
                return out;
            }

            JsonArray history = json.getAsJsonArray(SESSION_HISTORY_KEY);
            for (int i = history.size() - 1; i >= 0 && out.size() < limit; i--) {
                JsonElement element = history.get(i);
                if (element == null || !element.isJsonObject()) {
                    continue;
                }

                JsonObject entry = element.getAsJsonObject();
                if (!entry.has("topItem") || entry.get("topItem").isJsonNull()) {
                    continue;
                }

                String topItem = entry.get("topItem").getAsString();
                if (topItem == null) {
                    continue;
                }

                String cleaned = topItem.trim();
                if (cleaned.isEmpty() || "-".equals(cleaned) || out.contains(cleaned)) {
                    continue;
                }

                out.add(cleaned);
            }
        } catch (Exception ignored) {
        }

        return out;
    }

    public static List<FarmSpot> loadFarmSpots() {
        synchronized (FARM_SPOTS_LOCK) {
            List<FarmSpot> spots = new ArrayList<>();
            try {
                File configDir = new File(Minecraft.getInstance().gameDirectory, CONFIG_DIR);
                File configFile = new File(configDir, CONFIG_FILE);
                if (!configFile.exists()) return spots;

                String content = new String(Files.readAllBytes(configFile.toPath()));
                JsonObject json = JsonParser.parseString(content).getAsJsonObject();
                boolean repaired = sanitizeFarmSpotsJson(json);
                if (repaired) {
                    writeJsonAtomically(configDir, configFile, json);
                }
                if (!json.has(FARM_SPOTS_KEY) || !json.get(FARM_SPOTS_KEY).isJsonArray()) return spots;

                JsonArray arr = json.getAsJsonArray(FARM_SPOTS_KEY);
                for (int i = 0; i < arr.size(); i++) {
                    if (!arr.get(i).isJsonObject()) continue;
                    FarmSpot spot = FarmSpot.fromJson(arr.get(i).getAsJsonObject());
                    if (spot != null) spots.add(spot);
                }
            } catch (Exception ignored) {
            }
            return spots;
        }
    }

    public static void saveFarmSpots(List<FarmSpot> spots) {
        synchronized (FARM_SPOTS_LOCK) {
            try {
                File configDir = new File(Minecraft.getInstance().gameDirectory, CONFIG_DIR);
                if (!configDir.exists()) configDir.mkdirs();
                File configFile = new File(configDir, CONFIG_FILE);

                JsonObject json = new JsonObject();
                if (configFile.exists()) {
                    try {
                        String content = new String(Files.readAllBytes(configFile.toPath()));
                        json = JsonParser.parseString(content).getAsJsonObject();
                    } catch (Exception e) {
                        json = new JsonObject();
                    }
                }

                JsonArray arr = new JsonArray();
                if (spots != null) {
                    List<FarmSpot> snapshot = new ArrayList<>(spots);
                    for (FarmSpot spot : snapshot) {
                        if (spot != null) arr.add(spot.toJson());
                    }
                }
                json.add(FARM_SPOTS_KEY, arr);

                writeJsonAtomically(configDir, configFile, json);
            } catch (Exception ignored) {
            }
        }
    }

    public static boolean upsertFarmSpot(FarmSpot spot) {
        if (spot == null || spot.name == null || spot.name.trim().isEmpty()) return false;
        synchronized (FARM_SPOTS_LOCK) {
            List<FarmSpot> spots = loadFarmSpots();
            for (int i = 0; i < spots.size(); i++) {
                FarmSpot cur = spots.get(i);
                if (cur.name.equalsIgnoreCase(spot.name)) {
                    spot.totalFarmedSeconds = cur.totalFarmedSeconds;
                    spot.totalKills = cur.totalKills;
                    spot.totalSessions = cur.totalSessions;
                    spot.autoPresetEnabled = cur.autoPresetEnabled;
                    if ((spot.lastTopItem == null || spot.lastTopItem.isEmpty()) && cur.lastTopItem != null) {
                        spot.lastTopItem = cur.lastTopItem;
                    }
                    if (spot.lastPlayerLevelHint <= 0) {
                        spot.lastPlayerLevelHint = cur.lastPlayerLevelHint;
                    }
                    if (spot.ingredientsFound == null || spot.ingredientsFound.isEmpty()) {
                        spot.ingredientsFound = new LinkedHashMap<>(cur.ingredientsFound);
                    }
                    if (spot.mythicsFound == null || spot.mythicsFound.isEmpty()) {
                        spot.mythicsFound = new LinkedHashMap<>(cur.mythicsFound);
                    }
                    if ((spot.mobNamesSummary == null || spot.mobNamesSummary.isEmpty()) && cur.mobNamesSummary != null) {
                        spot.mobNamesSummary = cur.mobNamesSummary;
                    }
                    if ((spot.mobLevelRange == null || spot.mobLevelRange.isEmpty()) && cur.mobLevelRange != null) {
                        spot.mobLevelRange = cur.mobLevelRange;
                    }
                    spots.set(i, spot);
                    saveFarmSpots(spots);
                    return true;
                }
            }
            spots.add(spot);
            saveFarmSpots(spots);
            return true;
        }
    }

    public static boolean updateFarmSpot(String originalName, FarmSpot updatedSpot) {
        if (originalName == null || originalName.trim().isEmpty()) return false;
        if (updatedSpot == null || updatedSpot.name == null || updatedSpot.name.trim().isEmpty()) return false;

        synchronized (FARM_SPOTS_LOCK) {
            List<FarmSpot> spots = loadFarmSpots();
            int originalIndex = -1;
            FarmSpot original = null;
            for (int i = 0; i < spots.size(); i++) {
                FarmSpot cur = spots.get(i);
                if (cur.name.equalsIgnoreCase(originalName)) {
                    originalIndex = i;
                    original = cur;
                    break;
                }
            }
            if (originalIndex < 0 || original == null) return false;

            for (int i = 0; i < spots.size(); i++) {
                if (i == originalIndex) continue;
                FarmSpot cur = spots.get(i);
                if (cur.name.equalsIgnoreCase(updatedSpot.name)) {
                    return false;
                }
            }

            updatedSpot.totalFarmedSeconds = original.totalFarmedSeconds;
            updatedSpot.totalKills = original.totalKills;
            updatedSpot.totalSessions = original.totalSessions;
            updatedSpot.autoPresetEnabled = original.autoPresetEnabled;
            if ((updatedSpot.lastTopItem == null || updatedSpot.lastTopItem.isEmpty()) && original.lastTopItem != null) {
                updatedSpot.lastTopItem = original.lastTopItem;
            }
            if (updatedSpot.lastPlayerLevelHint <= 0) {
                updatedSpot.lastPlayerLevelHint = original.lastPlayerLevelHint;
            }
            if (updatedSpot.ingredientsFound == null || updatedSpot.ingredientsFound.isEmpty()) {
                updatedSpot.ingredientsFound = new LinkedHashMap<>(original.ingredientsFound);
            }
            if (updatedSpot.mythicsFound == null || updatedSpot.mythicsFound.isEmpty()) {
                updatedSpot.mythicsFound = new LinkedHashMap<>(original.mythicsFound);
            }
            if ((updatedSpot.mobNamesSummary == null || updatedSpot.mobNamesSummary.isEmpty()) && original.mobNamesSummary != null) {
                updatedSpot.mobNamesSummary = original.mobNamesSummary;
            }
            if ((updatedSpot.mobLevelRange == null || updatedSpot.mobLevelRange.isEmpty()) && original.mobLevelRange != null) {
                updatedSpot.mobLevelRange = original.mobLevelRange;
            }
            updatedSpot.lastUpdatedIso = LocalDateTime.now().toString();

            spots.set(originalIndex, updatedSpot);
            saveFarmSpots(spots);
            return true;
        }
    }

    public static boolean deleteFarmSpot(String spotName) {
        if (spotName == null || spotName.trim().isEmpty()) return false;
        synchronized (FARM_SPOTS_LOCK) {
            List<FarmSpot> spots = loadFarmSpots();
            boolean removed = spots.removeIf(s -> s != null && s.name != null && s.name.equalsIgnoreCase(spotName));
            if (!removed) return false;
            saveFarmSpots(spots);
            return true;
        }
    }

    public static boolean toggleFarmSpotFavorite(String spotName) {
        if (spotName == null || spotName.trim().isEmpty()) return false;
        synchronized (FARM_SPOTS_LOCK) {
            List<FarmSpot> spots = loadFarmSpots();
            for (FarmSpot spot : spots) {
                if (spot == null || spot.name == null) continue;
                if (!spot.name.equalsIgnoreCase(spotName)) continue;
                spot.favorite = !spot.favorite;
                spot.lastUpdatedIso = LocalDateTime.now().toString();
                saveFarmSpots(spots);
                return true;
            }
            return false;
        }
    }

    public static boolean toggleFarmSpotAutoPreset(String spotName) {
        if (spotName == null || spotName.trim().isEmpty()) return false;
        synchronized (FARM_SPOTS_LOCK) {
            List<FarmSpot> spots = loadFarmSpots();
            for (FarmSpot spot : spots) {
                if (spot == null || spot.name == null) continue;
                if (!spot.name.equalsIgnoreCase(spotName)) continue;
                spot.autoPresetEnabled = !spot.autoPresetEnabled;
                spot.lastUpdatedIso = LocalDateTime.now().toString();
                saveFarmSpots(spots);
                return true;
            }
            return false;
        }
    }

    public static boolean appendFarmSpotSessionStats(String spotName, long farmSeconds, int kills, String topItem, int playerLevel) {
        return appendFarmSpotSessionStats(spotName, farmSeconds, kills, topItem, playerLevel, null);
    }

    public static boolean appendFarmSpotSessionStats(String spotName, long farmSeconds, int kills, String topItem, int playerLevel, Map<String, Integer> foundIngredients) {
        return appendFarmSpotSessionStats(spotName, farmSeconds, kills, topItem, playerLevel, foundIngredients, null, "", "");
    }

    public static boolean appendFarmSpotSessionStats(
        String spotName,
        long farmSeconds,
        int kills,
        String topItem,
        int playerLevel,
        Map<String, Integer> foundIngredients,
        Map<String, Integer> foundMythics,
        String mobNamesSummary,
        String mobLevelRange
    ) {
        return appendFarmSpotSessionStats(spotName, farmSeconds, kills, topItem, playerLevel, foundIngredients, foundMythics, mobNamesSummary, mobLevelRange, 0);
    }

    public static boolean appendFarmSpotSessionStats(
        String spotName,
        long farmSeconds,
        int kills,
        String topItem,
        int playerLevel,
        Map<String, Integer> foundIngredients,
        Map<String, Integer> foundMythics,
        String mobNamesSummary,
        String mobLevelRange,
        long moneyMade
    ) {
        if (spotName == null || spotName.trim().isEmpty()) return false;
        synchronized (FARM_SPOTS_LOCK) {
            List<FarmSpot> spots = loadFarmSpots();
            Map<String, Integer> foundIngredientsSnapshot = copyCountMap(foundIngredients);
            Map<String, Integer> foundMythicsSnapshot = copyCountMap(foundMythics);
            for (FarmSpot spot : spots) {
                if (!spot.name.equalsIgnoreCase(spotName)) continue;
                spot.totalFarmedSeconds += Math.max(0, farmSeconds);
                spot.totalKills += Math.max(0, kills);
                spot.totalSessions += 1;
                if (topItem != null && !topItem.isEmpty()) spot.lastTopItem = topItem;
                if (playerLevel > 0) spot.lastPlayerLevelHint = playerLevel;
                if (!foundIngredientsSnapshot.isEmpty()) {
                    if (spot.ingredientsFound == null) {
                        spot.ingredientsFound = new LinkedHashMap<>();
                    }
                    for (Map.Entry<String, Integer> entry : foundIngredientsSnapshot.entrySet()) {
                        if (entry == null || entry.getKey() == null) continue;
                        int delta = Math.max(0, entry.getValue() == null ? 0 : entry.getValue());
                        if (delta <= 0) continue;
                        String item = entry.getKey().trim();
                        if (item.isEmpty()) continue;
                        int previous = Math.max(0, spot.ingredientsFound.getOrDefault(item, 0));
                        spot.ingredientsFound.put(item, previous + delta);
                    }
                }
                if (!foundMythicsSnapshot.isEmpty()) {
                    if (spot.mythicsFound == null) {
                        spot.mythicsFound = new LinkedHashMap<>();
                    }
                    for (Map.Entry<String, Integer> entry : foundMythicsSnapshot.entrySet()) {
                        if (entry == null || entry.getKey() == null) continue;
                        int delta = Math.max(0, entry.getValue() == null ? 0 : entry.getValue());
                        if (delta <= 0) continue;
                        String item = entry.getKey().trim();
                        if (item.isEmpty()) continue;
                        int previous = Math.max(0, spot.mythicsFound.getOrDefault(item, 0));
                        spot.mythicsFound.put(item, previous + delta);
                    }
                }
                if (mobNamesSummary != null && !mobNamesSummary.trim().isEmpty()) {
                    spot.mobNamesSummary = mobNamesSummary.trim();
                }
                if (mobLevelRange != null && !mobLevelRange.trim().isEmpty()) {
                    spot.mobLevelRange = mobLevelRange.trim();
                }
                spot.totalMoneyMade += Math.max(0, moneyMade);
                spot.lastUpdatedIso = LocalDateTime.now().toString();
                SpotSessionRecord record = new SpotSessionRecord();
                record.durationSeconds = Math.max(0, farmSeconds);
                record.kills = Math.max(0, kills);
                record.moneyMade = Math.max(0, moneyMade);
                record.loot = topItem == null || topItem.trim().isEmpty() ? "-" : topItem.trim();
                record.dateIso = LocalDateTime.now().toString();
                if (spot.sessionRecords == null) spot.sessionRecords = new ArrayList<>();
                spot.sessionRecords.add(0, record);
                while (spot.sessionRecords.size() > 500) spot.sessionRecords.remove(spot.sessionRecords.size() - 1);
                saveFarmSpots(spots);
                return true;
            }
            return false;
        }
    }

    private static Map<String, Integer> copyCountMap(Map<String, Integer> source) {
        Map<String, Integer> snapshot = new LinkedHashMap<>();
        if (source == null || source.isEmpty()) {
            return snapshot;
        }
        for (Map.Entry<String, Integer> entry : new ArrayList<>(source.entrySet())) {
            if (entry == null || entry.getKey() == null) continue;
            String key = entry.getKey().trim();
            if (key.isEmpty()) continue;
            snapshot.put(key, Math.max(0, entry.getValue() == null ? 0 : entry.getValue()));
        }
        return snapshot;
    }

    private static boolean sanitizeFarmSpotsJson(JsonObject json) {
        if (json == null || !json.has(FARM_SPOTS_KEY)) {
            return false;
        }
        JsonElement farmSpotsElement = json.get(FARM_SPOTS_KEY);
        if (farmSpotsElement == null || farmSpotsElement.isJsonNull()) {
            json.remove(FARM_SPOTS_KEY);
            return true;
        }
        if (!farmSpotsElement.isJsonArray()) {
            json.remove(FARM_SPOTS_KEY);
            return true;
        }

        boolean changed = false;
        JsonArray current = farmSpotsElement.getAsJsonArray();
        JsonArray sanitized = new JsonArray();
        for (int i = 0; i < current.size(); i++) {
            JsonElement element = current.get(i);
            if (element == null || !element.isJsonObject()) {
                changed = true;
                continue;
            }
            JsonObject spot = element.getAsJsonObject();
            if (sanitizeFarmSpotJson(spot)) {
                changed = true;
            }
            sanitized.add(spot);
        }

        if (changed || sanitized.size() != current.size()) {
            json.add(FARM_SPOTS_KEY, sanitized);
            return true;
        }
        return false;
    }

    private static boolean sanitizeFarmSpotJson(JsonObject spot) {
        if (spot == null) {
            return false;
        }

        boolean changed = false;

        if (spot.has("lastSession")) {
            JsonObject migratedRecord = createRecordFromLegacyLastSession(spot.get("lastSession"));
            if (migratedRecord != null) {
                if (!spot.has("sessionRecords") || !spot.get("sessionRecords").isJsonArray() || spot.getAsJsonArray("sessionRecords").size() == 0) {
                    JsonArray migratedRecords = new JsonArray();
                    migratedRecords.add(migratedRecord);
                    spot.add("sessionRecords", migratedRecords);
                }
            }
            spot.remove("lastSession");
            changed = true;
        }

        changed = sanitizeObjectCountMap(spot, "ingredientsFound") || changed;
        changed = sanitizeObjectCountMap(spot, "mythicsFound") || changed;
        changed = sanitizeSessionRecords(spot) || changed;
        return changed;
    }

    private static boolean sanitizeObjectCountMap(JsonObject parent, String key) {
        if (parent == null || key == null || !parent.has(key)) {
            return false;
        }
        JsonElement value = parent.get(key);
        if (value == null || value.isJsonNull()) {
            parent.add(key, new JsonObject());
            return true;
        }
        if (!value.isJsonObject()) {
            parent.add(key, new JsonObject());
            return true;
        }

        boolean changed = false;
        JsonObject source = value.getAsJsonObject();
        JsonObject sanitized = new JsonObject();
        for (Map.Entry<String, JsonElement> entry : source.entrySet()) {
            if (entry == null || entry.getKey() == null || entry.getValue() == null || entry.getValue().isJsonNull()) {
                changed = true;
                continue;
            }
            String name = entry.getKey().trim();
            if (name.isEmpty()) {
                changed = true;
                continue;
            }
            try {
                sanitized.addProperty(name, Math.max(0, entry.getValue().getAsInt()));
            } catch (Exception ignored) {
                changed = true;
            }
        }

        if (changed || sanitized.size() != source.size()) {
            parent.add(key, sanitized);
            return true;
        }
        return false;
    }

    private static boolean sanitizeSessionRecords(JsonObject spot) {
        if (spot == null || !spot.has("sessionRecords")) {
            return false;
        }
        JsonElement recordsElement = spot.get("sessionRecords");
        if (recordsElement == null || recordsElement.isJsonNull() || !recordsElement.isJsonArray()) {
            spot.add("sessionRecords", new JsonArray());
            return true;
        }

        boolean changed = false;
        JsonArray source = recordsElement.getAsJsonArray();
        JsonArray sanitized = new JsonArray();
        for (int i = 0; i < source.size(); i++) {
            JsonElement item = source.get(i);
            if (item == null || !item.isJsonObject()) {
                changed = true;
                continue;
            }
            JsonObject record = sanitizeSessionRecordJson(item.getAsJsonObject());
            if (record == null) {
                changed = true;
                continue;
            }
            sanitized.add(record);
        }

        if (changed || sanitized.size() != source.size()) {
            spot.add("sessionRecords", sanitized);
            return true;
        }
        return false;
    }

    private static JsonObject sanitizeSessionRecordJson(JsonObject rawRecord) {
        if (rawRecord == null) {
            return null;
        }
        JsonObject sanitized = new JsonObject();
        sanitized.addProperty("dur", readLong(rawRecord, "dur", readLong(rawRecord, "durationSeconds", 0L)));
        sanitized.addProperty("kills", readLong(rawRecord, "kills", 0L));
        sanitized.addProperty("money", readLong(rawRecord, "money", readLong(rawRecord, "moneyMade", 0L)));
        sanitized.addProperty("loot", readString(rawRecord, "loot", "-"));
        sanitized.addProperty("date", readString(rawRecord, "date", readString(rawRecord, "dateIso", "")));
        return sanitized;
    }

    private static JsonObject createRecordFromLegacyLastSession(JsonElement legacyElement) {
        if (legacyElement == null || legacyElement.isJsonNull() || !legacyElement.isJsonObject()) {
            return null;
        }

        JsonObject legacy = legacyElement.getAsJsonObject();
        if (legacy.has("ledger") && legacy.get("ledger").isJsonArray()) {
            JsonArray ledger = legacy.getAsJsonArray("ledger");
            for (int i = 0; i < ledger.size(); i++) {
                JsonElement entry = ledger.get(i);
                if (entry != null && entry.isJsonObject()) {
                    return sanitizeSessionRecordJson(entry.getAsJsonObject());
                }
            }
        }

        JsonObject migrated = sanitizeSessionRecordJson(legacy);
        String loot = readString(migrated, "loot", "-");
        long duration = readLong(migrated, "dur", 0L);
        long kills = readLong(migrated, "kills", 0L);
        long money = readLong(migrated, "money", 0L);
        String date = readString(migrated, "date", "");
        if (duration <= 0L && kills <= 0L && money <= 0L && (loot == null || loot.isBlank()) && (date == null || date.isBlank())) {
            return null;
        }
        return migrated;
    }

    private static long readLong(JsonObject source, String key, long fallback) {
        if (source == null || key == null || !source.has(key)) {
            return fallback;
        }
        try {
            return Math.max(0L, source.get(key).getAsLong());
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private static String readString(JsonObject source, String key, String fallback) {
        if (source == null || key == null || !source.has(key)) {
            return fallback;
        }
        try {
            String value = source.get(key).getAsString();
            return value == null ? fallback : value;
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private static void writeJsonAtomically(File configDir, File configFile, JsonObject json) throws Exception {
        File tempFile = File.createTempFile("mobkiller_config", ".tmp", configDir);
        Files.writeString(tempFile.toPath(), json.toString(), StandardCharsets.UTF_8);
        Files.move(tempFile.toPath(), configFile.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
    }

    public static class HudConfig {
        public int hudX;
        public int hudY;
        public int hudColor;
        public List<Integer> hudLines;
        public boolean displayProbabilityAsPercent;
        public boolean displayCurrencyAsCompact;
        public boolean useRealtimeKillTracking;
        public boolean showBothKillSystems;
        public int priceMode;
        public double manualItemPrice;
        public boolean ingredientCountAllItems;
        public boolean hudTextShadow;
        public boolean hudBackgroundEnabled;
    }

    public static class SpotSessionRecord {
        public long durationSeconds;
        public long kills;
        public long moneyMade;
        public String loot;
        public String dateIso;

        JsonObject toJson() {
            JsonObject o = new JsonObject();
            o.addProperty("dur", Math.max(0, durationSeconds));
            o.addProperty("kills", Math.max(0, kills));
            o.addProperty("money", Math.max(0, moneyMade));
            o.addProperty("loot", loot == null ? "" : loot);
            o.addProperty("date", dateIso == null ? "" : dateIso);
            return o;
        }

        static SpotSessionRecord fromJson(JsonObject o) {
            if (o == null) return null;
            SpotSessionRecord r = new SpotSessionRecord();
            r.durationSeconds = o.has("dur") ? Math.max(0L, o.get("dur").getAsLong()) : 0L;
            r.kills = o.has("kills") ? Math.max(0L, o.get("kills").getAsLong()) : 0L;
            r.moneyMade = o.has("money") ? Math.max(0L, o.get("money").getAsLong()) : 0L;
            r.loot = o.has("loot") ? o.get("loot").getAsString() : "-";
            r.dateIso = o.has("date") ? o.get("date").getAsString() : "";
            return r;
        }
    }

    public static class FarmSpot {
        public String name;
        public String category; // mythic, ingredient, gathering
        public String zone;
        public boolean favorite;
        public boolean autoPresetEnabled = true;
        public int x;
        public int y;
        public int z;
        public int detectionRadius = 200; // radius in blocks for spot detection
        public long totalFarmedSeconds;
        public long totalKills;
        public int totalSessions;
        public String lastTopItem;
        public int lastPlayerLevelHint;
        public String lastUpdatedIso;
        public Map<String, Integer> ingredientsFound = new LinkedHashMap<>();
        public Map<String, Integer> mythicsFound = new LinkedHashMap<>();
        public String mobNamesSummary;
        public String mobLevelRange;
        public long totalMoneyMade = 0;
        public List<SpotSessionRecord> sessionRecords = new ArrayList<>();

        JsonObject toJson() {
            JsonObject o = new JsonObject();
            o.addProperty("name", name == null ? "" : name);
            o.addProperty("category", category == null ? "" : category);
            o.addProperty("zone", zone == null ? "" : zone);
            o.addProperty("favorite", favorite);
            o.addProperty("autoPresetEnabled", autoPresetEnabled);
            o.addProperty("x", x);
            o.addProperty("y", y);
            o.addProperty("z", z);
            o.addProperty("detectionRadius", Math.max(1, detectionRadius));
            o.addProperty("totalFarmedSeconds", Math.max(0, totalFarmedSeconds));
            o.addProperty("totalKills", Math.max(0, totalKills));
            o.addProperty("totalSessions", Math.max(0, totalSessions));
            o.addProperty("lastTopItem", lastTopItem == null ? "" : lastTopItem);
            o.addProperty("lastPlayerLevelHint", Math.max(0, lastPlayerLevelHint));
            o.addProperty("lastUpdatedIso", lastUpdatedIso == null ? "" : lastUpdatedIso);
            JsonObject ingredients = new JsonObject();
            Map<String, Integer> ingredientSnapshot = copyCountMap(ingredientsFound);
            if (!ingredientSnapshot.isEmpty()) {
                for (Map.Entry<String, Integer> e : ingredientSnapshot.entrySet()) {
                    if (e == null || e.getKey() == null) continue;
                    String key = e.getKey().trim();
                    if (key.isEmpty()) continue;
                    ingredients.addProperty(key, Math.max(0, e.getValue() == null ? 0 : e.getValue()));
                }
            }
            o.add("ingredientsFound", ingredients);
            JsonObject mythics = new JsonObject();
            Map<String, Integer> mythicSnapshot = copyCountMap(mythicsFound);
            if (!mythicSnapshot.isEmpty()) {
                for (Map.Entry<String, Integer> e : mythicSnapshot.entrySet()) {
                    if (e == null || e.getKey() == null) continue;
                    String key = e.getKey().trim();
                    if (key.isEmpty()) continue;
                    mythics.addProperty(key, Math.max(0, e.getValue() == null ? 0 : e.getValue()));
                }
            }
            o.add("mythicsFound", mythics);
            o.addProperty("mobNamesSummary", mobNamesSummary == null ? "" : mobNamesSummary);
            o.addProperty("mobLevelRange", mobLevelRange == null ? "" : mobLevelRange);
            o.addProperty("totalMoneyMade", Math.max(0, totalMoneyMade));
            JsonArray sessions = new JsonArray();
            List<SpotSessionRecord> sessionSnapshot = sessionRecords == null ? new ArrayList<>() : new ArrayList<>(sessionRecords);
            for (SpotSessionRecord r : sessionSnapshot) {
                if (r != null) sessions.add(r.toJson());
            }
            o.add("sessionRecords", sessions);
            return o;
        }

        static FarmSpot fromJson(JsonObject o) {
            if (o == null) return null;
            FarmSpot s = new FarmSpot();
            s.name = o.has("name") ? o.get("name").getAsString() : "";
            if (s.name.isEmpty()) return null;
            s.category = o.has("category") ? o.get("category").getAsString() : "";
            s.zone = o.has("zone") ? o.get("zone").getAsString() : "";
            s.favorite = o.has("favorite") && o.get("favorite").getAsBoolean();
            s.autoPresetEnabled = !o.has("autoPresetEnabled") || o.get("autoPresetEnabled").getAsBoolean();
            s.x = o.has("x") ? o.get("x").getAsInt() : 0;
            s.y = o.has("y") ? o.get("y").getAsInt() : 0;
            s.z = o.has("z") ? o.get("z").getAsInt() : 0;
            s.detectionRadius = o.has("detectionRadius") ? Math.max(1, o.get("detectionRadius").getAsInt()) : 200;
            s.totalFarmedSeconds = o.has("totalFarmedSeconds") ? Math.max(0L, o.get("totalFarmedSeconds").getAsLong()) : 0L;
            s.totalKills = o.has("totalKills") ? Math.max(0L, o.get("totalKills").getAsLong()) : 0L;
            s.totalSessions = o.has("totalSessions") ? Math.max(0, o.get("totalSessions").getAsInt()) : 0;
            s.lastTopItem = o.has("lastTopItem") ? o.get("lastTopItem").getAsString() : "";
            s.lastPlayerLevelHint = o.has("lastPlayerLevelHint") ? Math.max(0, o.get("lastPlayerLevelHint").getAsInt()) : 0;
            s.lastUpdatedIso = o.has("lastUpdatedIso") ? o.get("lastUpdatedIso").getAsString() : "";
            s.ingredientsFound = new LinkedHashMap<>();
            if (o.has("ingredientsFound") && o.get("ingredientsFound").isJsonObject()) {
                JsonObject ingredients = o.getAsJsonObject("ingredientsFound");
                for (Map.Entry<String, JsonElement> e : ingredients.entrySet()) {
                    if (e == null || e.getKey() == null || e.getValue() == null || e.getValue().isJsonNull()) continue;
                    String key = e.getKey().trim();
                    if (key.isEmpty()) continue;
                    int count;
                    try {
                        count = Math.max(0, e.getValue().getAsInt());
                    } catch (Exception ignored) {
                        count = 0;
                    }
                    if (count > 0) {
                        s.ingredientsFound.put(key, count);
                    }
                }
            }
            s.mythicsFound = new LinkedHashMap<>();
            if (o.has("mythicsFound") && o.get("mythicsFound").isJsonObject()) {
                JsonObject mythics = o.getAsJsonObject("mythicsFound");
                for (Map.Entry<String, JsonElement> e : mythics.entrySet()) {
                    if (e == null || e.getKey() == null || e.getValue() == null || e.getValue().isJsonNull()) continue;
                    String key = e.getKey().trim();
                    if (key.isEmpty()) continue;
                    int count;
                    try {
                        count = Math.max(0, e.getValue().getAsInt());
                    } catch (Exception ignored) {
                        count = 0;
                    }
                    if (count > 0) {
                        s.mythicsFound.put(key, count);
                    }
                }
            }
            s.mobNamesSummary = o.has("mobNamesSummary") ? o.get("mobNamesSummary").getAsString() : "";
            s.mobLevelRange = o.has("mobLevelRange") ? o.get("mobLevelRange").getAsString() : "";
            s.totalMoneyMade = o.has("totalMoneyMade") ? Math.max(0L, o.get("totalMoneyMade").getAsLong()) : 0L;
            s.sessionRecords = new ArrayList<>();
            if (o.has("sessionRecords") && o.get("sessionRecords").isJsonArray()) {
                JsonArray sessions = o.getAsJsonArray("sessionRecords");
                for (int i = 0; i < sessions.size(); i++) {
                    try {
                        SpotSessionRecord r = SpotSessionRecord.fromJson(sessions.get(i).getAsJsonObject());
                        if (r != null) s.sessionRecords.add(r);
                    } catch (Exception ignored) {}
                }
            }
            return s;
        }
    }

    public static class SessionHistoryEntry {
        public String duration;
        public int kills;
        public double gains;
        public double incomePerHour;
        public int mythicsDropped;
        public String professionName;
        public double levelPercentWon;
        public String topItem;
        public int topItemCount;

        static SessionHistoryEntry fromJson(JsonObject entry) {
            if (entry == null) {
                return null;
            }

            SessionHistoryEntry result = new SessionHistoryEntry();
            result.duration = entry.has("duration") ? entry.get("duration").getAsString() : "00:00";
            result.kills = entry.has("kills") ? Math.max(0, entry.get("kills").getAsInt()) : 0;
            result.gains = entry.has("gains") ? entry.get("gains").getAsDouble() : -1.0;
            result.incomePerHour = entry.has("incomePerHour") ? entry.get("incomePerHour").getAsDouble() : -1.0;
            result.mythicsDropped = entry.has("mythicsDropped") ? Math.max(0, entry.get("mythicsDropped").getAsInt()) : 0;
            result.professionName = entry.has("professionName") ? entry.get("professionName").getAsString() : "";
            result.levelPercentWon = entry.has("levelPercentWon") ? entry.get("levelPercentWon").getAsDouble() : -1.0;
            result.topItem = entry.has("topItem") ? entry.get("topItem").getAsString() : "";
            result.topItemCount = entry.has("topItemCount") ? Math.max(0, entry.get("topItemCount").getAsInt()) : 0;
            return result;
        }
    }
}
