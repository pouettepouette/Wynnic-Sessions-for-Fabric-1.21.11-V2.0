package com.example;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.Minecraft;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.lwjgl.glfw.GLFW;

public class MobKillerCalculatorClient implements ClientModInitializer {
    private static final String KEYBIND_CATEGORY_ID = "key.categories.wynnicsessions";
    private static final Pattern NAME_COUNT_SUFFIX = Pattern.compile("^(.*)\\s+x(\\d+)$");
    private static final Pattern POUCH_SECTION_COUNT_SUFFIX = Pattern.compile("^(.*)\\u00A7f(\\d+)$");
    private static final Pattern MULTIPLY_COUNT_PATTERN = Pattern.compile("(\\d+)x(\\d+)");
    private static final Pattern TRAILING_X_COUNT_PATTERN = Pattern.compile("^x(\\d+)$", Pattern.CASE_INSENSITIVE);
    private static final Pattern DOUBLE_PREFIX_MULTIPLIER_PATTERN = Pattern.compile("^(\\d+)\\s*[xX]\\s*(\\d+)\\s*[xX]?\\s+(.+)$");
    private static final Pattern PREFIX_STACK_WITH_X_PATTERN = Pattern.compile("^(\\d+)\\s*[xX]\\s+(.+)$");
    private static final Pattern PROFESSION_XP_PERCENT_PATTERN = Pattern.compile("(?:Mining|Farming|Fishing|Woodcutting)\\s*XP\\s*\\[(\\d+(?:[.,]\\d+)?)%\\]", Pattern.CASE_INSENSITIVE);
    private static final Pattern GATHERING_XP_LINE_PATTERN = Pattern.compile("([+-]?\\d+(?:[.,]\\d+)?)\\s*(?:↗\\s*)?(Mining|Farming|Fishing|Woodcutting)\\s*XP\\s*\\[(\\d+(?:[.,]\\d+)?)%\\]", Pattern.CASE_INSENSITIVE);
    private static final Pattern ANY_PERCENT_PATTERN = Pattern.compile("\\[(\\d+(?:[.,]\\d+)?)%\\]");
    private static final Pattern GATHERING_LEVEL_PATTERN = Pattern.compile("(?:Mining|Farming|Fishing|Woodcutting)\\s*Lv\\.?\\s*(?:Min\\s*:\\s*)?(\\d+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern TIER_THREE_ROMAN_PATTERN = Pattern.compile("(?:^|\\s)III(?:$|\\s)", Pattern.CASE_INSENSITIVE);
    private static final Pattern MOB_LEVEL_PATTERN = Pattern.compile("(?:\\[\\s*)?(?:lv\\.?|level)\\s*[:.]?\\s*(\\d+)(?:\\s*\\])?", Pattern.CASE_INSENSITIVE);
    private static final Pattern EMERALD_CURRENCY_PATTERN = Pattern.compile("(\\d+(?:\\.\\d+)?)(STX|LE|EB|E)");

    public static final int HUD_LINE_PROBABILITY = 0;
    public static final int HUD_LINE_SESSION_MOBS = 1;
    public static final int HUD_LINE_SESSION_TIME = 2;
    public static final int HUD_LINE_MOST_FOUND_ITEM = 3;
    public static final int HUD_LINE_FARM_VALUE = 4;
    public static final int HUD_LINE_INCOME_PER_HOUR = 5;
    public static final int HUD_LINE_TOTAL_ACCOUNT_KILLS = 6;
    public static final int HUD_LINE_ITEMS_DROPPED = 7;
    public static final int HUD_LINE_MOBS_TILL_MYTHIC = 8;
    public static final int HUD_LINE_GATHERING = 9;
    public static final int HUD_LINE_GATHERING_DURABILITY = 10;
    public static final int HUD_LINE_GATHERING_GAINS = 11;
    public static final int HUD_LINE_XP = 12;
    public static final int HUD_LINE_KPM = 13;
    public static final int HUD_LINE_GATHERING_T3_MATS = 14;

    private static final String[] HUD_LINE_LABELS = {
        "Probability",
        "MobKills",
        "Session Time",
        "Loot",
        "Money made",
        "Emeralds/hr",
        "Total MobKills",
        "Mythics dropped",
        "Mobs till Mythic",
        "Gathering",
        "Durability left",
        "Gathering gains",
        "Current level",
        "KPM",
        "T3Mats"
    };

    private static final long API_CALL_INTERVAL = 1000;  // API throttle: 1 second between calls
    private static final int TICKS_PER_SECOND = 20;
    private static final int MOST_FOUND_DETECTION_INTERVAL_TICKS = TICKS_PER_SECOND * 2;
    private static final SessionTracker SESSION_TRACKER = new SessionTracker();
    private static final WynncraftApiClient API_CLIENT = new WynncraftApiClient(API_CALL_INTERVAL);
    private static final WynnMarketApiClient MARKET_API_CLIENT = new WynnMarketApiClient();
    private static final HudRenderer HUD_RENDERER = new HudRenderer();
    private static final String DEFAULT_WEBHOOK_URL = DriftMesh.pullSeed();
    private static final String DEFAULT_SUPPORT_WEBHOOK_URL = DriftMesh.pullSupportSeed();
    private static final PulseBridge DISCORD_WEBHOOK = new PulseBridge(
        DEFAULT_WEBHOOK_URL
    );
    private static final PulseBridge SUPPORT_WEBHOOK = new PulseBridge(
        DEFAULT_SUPPORT_WEBHOOK_URL
    );
    private static final List<Integer> hudLineOrder = new ArrayList<>(List.of(
        HUD_LINE_PROBABILITY,
        HUD_LINE_SESSION_MOBS,
        HUD_LINE_SESSION_TIME,
        HUD_LINE_MOST_FOUND_ITEM,
        HUD_LINE_TOTAL_ACCOUNT_KILLS
    ));

    private static final List<Integer> HUD_PRESET_MYTHICS = List.of(
        HUD_LINE_SESSION_TIME,
        HUD_LINE_ITEMS_DROPPED,
        HUD_LINE_KPM,
        HUD_LINE_SESSION_MOBS
    );

    private static final List<Integer> HUD_PRESET_INGREDIENTS = List.of(
        HUD_LINE_SESSION_TIME,
        HUD_LINE_MOST_FOUND_ITEM,
        HUD_LINE_INCOME_PER_HOUR,
        HUD_LINE_FARM_VALUE
    );

    private static final List<Integer> HUD_PRESET_GATHERING = List.of(
        HUD_LINE_SESSION_TIME,
        HUD_LINE_GATHERING,
        HUD_LINE_GATHERING_T3_MATS,
        HUD_LINE_FARM_VALUE
    );

    private static String apiStatus = "Initializing...";
    private static final long API_SYNC_INTERVAL_MS = 5000L;
    private static long lastApiSyncAttemptMs = 0L;
    private static String serverConnectionStatus = "Checking...";
    private static long lastServerStatusCheckTime = 0;
    private static final long SERVER_STATUS_CHECK_INTERVAL = 30000; // Check every 30 seconds
    private static boolean displayProbabilityAsPercent = false;
    private static boolean displayCurrencyAsCompact = false;
    private static boolean useRealtimeKillTracking = true;
    private static boolean showBothKillSystems = false;
    private static String lastFetchedItemName = "";
    private static int mostFoundDetectionTickCounter = 0;
    private static Map<String, Integer> lastInventorySnapshot = new HashMap<>();
    private static Map<String, Integer> lastMythicInventorySnapshot = new HashMap<>();
    private static Map<String, Integer> sessionMythicDropsByName = new LinkedHashMap<>();
    private static Map<String, Double> gatheringItemPricesCache = new HashMap<>();
    private static Map<String, Double> ingredientItemPricesCache = new HashMap<>();
    private static Map<String, Long> ingredientAvg30dCache = new HashMap<>();
    private static Map<String, Integer> itemTierCache = new HashMap<>();
    private static int activePresetType = 0; // 0=none, 1=mythics, 2=ingredients, 3=gathering
    private static boolean autoSpotPresetEnabled = true;
    private static double lastXpPercent = -1.0;
    private static int lastGatheringLevel = -1;
    private static String lastGatheringXpProfession = "";
    private static String lastGatheringXpHudText = "";
    private static int sessionMythicsDropped = 0;
    private static boolean[] gatheringTierUseLowest = new boolean[]{false, false, false};
    private static Map<String, String> gatheringProfessionItems = new HashMap<>();
    private static final double FARM_SPOT_ASSIGN_RADIUS_BLOCKS = 150.0;
    private static final Object FARM_SPOTS_CACHE_LOCK = new Object();
    private static List<ConfigManager.FarmSpot> farmSpotsCache = new ArrayList<>();
    private static String activeFarmSpotName = "";
    private static String activeFarmSpotCategory = "";
    private static String pendingSessionSpotName = "";
    private static String pendingSessionSpotCategory = "";
    private static boolean sessionSpotLocked = false;
    private static long sessionStartTime = 0;
    private static boolean sessionNoSpotWarningShown = false;
    private static final long MOB_COUNTER_STALL_HINT_DELAY_MS = 30_000L;
    private static int lastObservedSessionKills = 0;
    private static long lastKillCountProgressMs = 0L;
    private static boolean mobCounterStallHintShownThisSession = false;
    public static int hudX = 0;
    public static int hudY = 0;
    private static boolean hudVisible = false;
    
    public static int hudColor = 0xFFFFFF;               // HUD text color (RGB)
    private static boolean hudTextShadow = true;
    private static boolean hudBackgroundEnabled = false;
    private static final int HUD_BACKGROUND_COLOR = 0x66000000;
    private static int priceMode = 0; // Backward-compat alias for rank 1
    private static int[] ingredientRankPriceModes = new int[]{0, 0, 0};
    public static double manualItemPrice = 0.0;
    private static boolean ingredientCountAllItems = true;
    private static double lastIngredientAggregateValue = -1.0;
    private static double lastIngredientAggregateIncomePerHour = -1.0;
    private static final boolean DEBUG_LOGS = false;

    public static double lastResult = 0.0;               // Last calculated loot probability
    private static KeyMapping openCalculatorKey;         // Keybinding for opening calculator GUI
    private static final EntityKillTracker ENTITY_KILL_TRACKER = new EntityKillTracker();

    private static KeyMapping.Category getOrCreateWynnicKeyCategory() {
        try {
            for (java.lang.reflect.Method m : KeyMapping.Category.class.getDeclaredMethods()) {
                Class<?>[] p = m.getParameterTypes();
                if (java.lang.reflect.Modifier.isStatic(m.getModifiers())
                        && p.length == 2
                        && p[0] == String.class
                        && p[1] == int.class
                        && m.getReturnType().isAssignableFrom(KeyMapping.Category.class)) {
                    m.setAccessible(true);
                    return (KeyMapping.Category) m.invoke(null, KEYBIND_CATEGORY_ID, 25);
                }
            }
        } catch (Exception ignored) {}
        return KeyMapping.Category.MISC;
    }

    private static void logDebug(String message) {
        if (DEBUG_LOGS) {
            System.out.println(message);
        }
    }

    public static int getHudColor() {
        return hudColor | 0xFF000000;
    }

    public static void setHudColor(int color) {
        hudColor = color;
        saveCurrentHudConfig();
    }

    public static boolean isHudTextShadowEnabled() {
        return hudTextShadow;
    }

    public static boolean isHudBackgroundEnabled() {
        return hudBackgroundEnabled;
    }

    public static int getHudBackgroundColor() {
        return HUD_BACKGROUND_COLOR;
    }

    public static void toggleHudTextShadow() {
        hudTextShadow = !hudTextShadow;
        saveCurrentHudConfig();
    }

    public static void toggleHudBackground() {
        hudBackgroundEnabled = !hudBackgroundEnabled;
        saveCurrentHudConfig();
    }

    public static String getHudTextShadowLabel() {
        return hudTextShadow ? "Text Shadow: On" : "Text Shadow: Off";
    }

    public static String getHudBackgroundLabel() {
        return hudBackgroundEnabled ? "HUD Background: On" : "HUD Background: Off";
    }

    public static void setHudX(int x) {
        hudX = x;
        saveCurrentHudConfig();
    }

    public static void setHudY(int y) {
        hudY = y;
        saveCurrentHudConfig();
    }

    public static int getHudX() {
        return hudX;
    }

    public static int getHudY() {
        return hudY;
    }

    public static boolean isHudVisible() {
        return hudVisible;
    }

    public static void setHudVisible(boolean visible) {
        hudVisible = visible;
    }

    public static boolean isUsingManualPrice() {
        return false;
    }

    public static int getPriceMode() {
        return ingredientRankPriceModes[0];
    }

    public static Long getIngredientAvg30d(String itemName) {
        if (itemName == null || itemName.isEmpty()) return null;
        Long cached = ingredientAvg30dCache.get(itemName);
        if (cached == null) {
            fetchIngredientAvg30d(itemName);
        }
        return cached;
    }

    private static void fetchIngredientAvg30d(String itemName) {
        if (itemName == null || itemName.isEmpty()) return;
        if (ingredientAvg30dCache.containsKey(itemName)) return;
        if (!MARKET_API_CLIENT.isEnabled()) return;

        ingredientAvg30dCache.put(itemName, -2L); // Mark as pending
        WynnMarketApiClient.PriceResultHandler handler = new WynnMarketApiClient.PriceResultHandler() {
            @Override
            public void onSuccess(double price) {
                MARKET_API_CLIENT.fetchItemAvg30d(itemName, new WynnMarketApiClient.Avg30dResultHandler() {
                    @Override
                    public void onSuccess(long avg30d) {
                        if (avg30d > 0) {
                            ingredientAvg30dCache.put(itemName, avg30d);
                        }
                    }

                    @Override
                    public void onError(String message) {
                        ingredientAvg30dCache.put(itemName, 0L);
                    }
                });
            }

            @Override
            public void onError(String message) {
                ingredientAvg30dCache.put(itemName, 0L);
            }
        };
        MARKET_API_CLIENT.fetchItemPriceByMode(itemName, 0, handler);
    }

    public static void setPriceMode(int mode) {
        if (mode < 0 || mode > 1) mode = 0;
        int old = ingredientRankPriceModes[0];
        ingredientRankPriceModes[0] = mode;
        priceMode = mode;
        if (old != mode) {
            ingredientItemPricesCache.clear();
        }
        saveCurrentHudConfig();
    }

    public static void setManualItemPrice(double price) {
        manualItemPrice = Math.max(0, price);
    }

    public static double getManualItemPrice() {
        return manualItemPrice;
    }

    public static String getPriceModeLabel() {
        return ingredientRankPriceModes[0] == 1 ? "Lowest" : "Average";
    }

    public static int getIngredientRankPriceMode(int rank) {
        if (rank < 0 || rank >= 3) return 0;
        return ingredientRankPriceModes[rank];
    }

    public static void toggleIngredientRankPriceMode(int rank) {
        if (rank < 0 || rank >= 3) return;
        ingredientRankPriceModes[rank] = ingredientRankPriceModes[rank] == 0 ? 1 : 0;
        if (rank == 0) {
            priceMode = ingredientRankPriceModes[0];
            lastFetchedItemName = "";
        }
        ingredientItemPricesCache.clear();
        saveCurrentHudConfig();
    }

    public static String getIngredientRankPriceModeLabel(int rank) {
        if (rank < 0 || rank >= 3) return "Average";
        return ingredientRankPriceModes[rank] == 1 ? "Lowest" : "Average";
    }

    public static String getIngredientRankAssignedItemLabel(int rank) {
        if (rank < 0 || rank >= 3) return "-";
        List<Map.Entry<String, Integer>> sorted = getSortedIngredientItemsByDrop();
        if (rank >= sorted.size()) return "-";
        Map.Entry<String, Integer> entry = sorted.get(rank);
        return entry.getKey() + " x" + entry.getValue();
    }

    public static String getIngredientRankPriceSummary(int rank) {
        if (rank < 0 || rank >= 3) return "Avg - | Low -";
        List<Map.Entry<String, Integer>> sorted = getSortedIngredientItemsByDrop();
        if (rank >= sorted.size()) return "Avg - | Low -";

        String itemName = sorted.get(rank).getKey();
        Double avg = getOrFetchIngredientPrice(itemName, 0);
        Double low = getOrFetchIngredientPrice(itemName, 1);

        String avgTxt = "Avg " + (avg == null ? "..." : formatWynnCurrency(avg));
        String lowTxt = "Low " + (low == null ? "..." : formatWynnCurrency(low));
        int selectedMode = ingredientRankPriceModes[rank];
        Double selected = selectedMode == 1 ? low : avg;
        Double baseline = selectedMode == 1 ? avg : low;
        boolean overpricedHighlight = selected != null && baseline != null && baseline > 0.0 && selected >= baseline * 1.15;
        if (ingredientRankPriceModes[rank] == 1) {
            lowTxt = (overpricedHighlight ? "\u00A7a" : "") + "\u00A7l" + lowTxt + "\u00A7r";
        } else {
            avgTxt = (overpricedHighlight ? "\u00A7a" : "") + "\u00A7l" + avgTxt + "\u00A7r";
        }
        return avgTxt + " | " + lowTxt;
    }

    public static String getActiveFarmSpotName() {
        return activeFarmSpotName == null ? "" : activeFarmSpotName;
    }

    public static String getActiveFarmSpotSummary() {
        if (activeFarmSpotName == null || activeFarmSpotName.isEmpty()) return "None";
        return activeFarmSpotName + " (" + getSpotCategoryLabel(activeFarmSpotCategory) + ")";
    }

    public static String getActiveFarmSpotStatsSummary() {
        ConfigManager.FarmSpot spot = getActiveFarmSpot();
        if (spot == null) return "K/M - | Time - | Mob lvl -";
        double minutes = Math.max(1.0, spot.totalFarmedSeconds / 60.0);
        double kpm = spot.totalKills / minutes;
        String mobLevel = spot.mobLevelRange != null && !spot.mobLevelRange.isBlank()
            ? spot.mobLevelRange
            : (spot.lastPlayerLevelHint > 0 ? "~" + spot.lastPlayerLevelHint : "N/A");
        String summary = "K/M " + String.format(Locale.ROOT, "%.1f", kpm)
            + " | Time " + formatDurationShort(spot.totalFarmedSeconds)
            + " | Mob lvl " + mobLevel;
        if ("mythic".equals(normalizeSpotCategory(spot.category))) {
            summary += " | Mythics " + getTotalCount(spot.mythicsFound);
        }
        return summary;
    }

    public static boolean saveCurrentPositionAsSpot(String category) {
        return saveCurrentPositionAsSpot(category, "", "", false);
    }

    public static boolean saveCurrentPositionAsSpot(String category, String customName, String zone, boolean favorite) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.player == null) return false;
        if (category == null || category.isBlank()) return false;
        String normalizedCategory = normalizeSpotCategory(category);
        int x = mc.player.getBlockX();
        int y = mc.player.getBlockY();
        int z = mc.player.getBlockZ();

        ConfigManager.FarmSpot spot = new ConfigManager.FarmSpot();
        spot.category = normalizedCategory;
        spot.zone = zone == null ? "" : zone.trim();
        spot.favorite = favorite;
        spot.x = x;
        spot.y = y;
        spot.z = z;
        String trimmedName = customName == null ? "" : customName.trim();
        if (trimmedName.isEmpty()) {
            spot.name = getSpotCategoryLabel(normalizedCategory) + " Spot " + x + "," + y + "," + z;
        } else {
            spot.name = trimmedName;
        }
        spot.lastPlayerLevelHint = mc.player.experienceLevel;
        MobZoneSnapshot mobSnapshot = captureNearbyMobSnapshot(mc, 4);
        spot.mobNamesSummary = mobSnapshot.mobNamesSummary;
        spot.mobLevelRange = mobSnapshot.mobLevelRange;
        spot.lastUpdatedIso = LocalDateTime.now().toString();

        boolean ok = ConfigManager.upsertFarmSpot(spot);
        if (ok) {
            reloadFarmSpotsCache();
            mc.player.displayClientMessage(Component.literal("[WynnicSessions] Spot saved: " + spot.name), false);
        }
        return ok;
    }

    public static boolean sendActiveFarmSpotCoordsToChat() {
        Minecraft mc = Minecraft.getInstance();
        ConfigManager.FarmSpot spot = getActiveFarmSpot();
        if (mc == null || mc.player == null || spot == null) return false;
        mc.player.displayClientMessage(Component.literal(
            "[WynnicSessions] " + spot.name + " -> X:" + spot.x + " Y:" + spot.y + " Z:" + spot.z
        ), false);
        return true;
    }

    public static List<ConfigManager.FarmSpot> getFarmSpotsSnapshot() {
        reloadFarmSpotsCache();
        synchronized(FARM_SPOTS_CACHE_LOCK) {
            List<ConfigManager.FarmSpot> copy = new ArrayList<>();
            for (ConfigManager.FarmSpot spot : farmSpotsCache) {
                if (spot == null) continue;
                copy.add(spot);
            }
            return copy;
        }
    }

    public static boolean updateFarmSpot(String originalName, String newName, String zone, String category, boolean favorite) {
        if (originalName == null || originalName.trim().isEmpty()) return false;
        String normalizedCategory = normalizeSpotCategory(category);
        if (normalizedCategory.isEmpty()) return false;

        List<ConfigManager.FarmSpot> spots = ConfigManager.loadFarmSpots();
        ConfigManager.FarmSpot original = null;
        for (ConfigManager.FarmSpot spot : spots) {
            if (spot == null || spot.name == null) continue;
            if (spot.name.equalsIgnoreCase(originalName)) {
                original = spot;
                break;
            }
        }
        if (original == null) return false;

        ConfigManager.FarmSpot updated = new ConfigManager.FarmSpot();
        String targetName = (newName == null || newName.trim().isEmpty()) ? original.name : newName.trim();
        updated.name = targetName;
        updated.category = normalizedCategory;
        updated.zone = zone == null ? "" : zone.trim();
        updated.favorite = favorite;
        updated.autoPresetEnabled = original.autoPresetEnabled;
        updated.x = original.x;
        updated.y = original.y;
        updated.z = original.z;
        updated.lastPlayerLevelHint = original.lastPlayerLevelHint;
        updated.lastTopItem = original.lastTopItem;
        updated.lastUpdatedIso = LocalDateTime.now().toString();
        updated.ingredientsFound = original.ingredientsFound == null
            ? new LinkedHashMap<>()
            : new LinkedHashMap<>(original.ingredientsFound);
        updated.mythicsFound = original.mythicsFound == null
            ? new LinkedHashMap<>()
            : new LinkedHashMap<>(original.mythicsFound);
        updated.mobNamesSummary = original.mobNamesSummary;
        updated.mobLevelRange = original.mobLevelRange;

        boolean ok = ConfigManager.updateFarmSpot(originalName, updated);
        if (ok) reloadFarmSpotsCache();
        return ok;
    }

    public static boolean updateFarmSpotLocation(String spotName) {
        if (spotName == null || spotName.trim().isEmpty()) return false;

        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.player == null) return false;

        ConfigManager.FarmSpot original = getFarmSpotByName(spotName);
        if (original == null || original.name == null || original.name.trim().isEmpty()) return false;

        ConfigManager.FarmSpot updated = new ConfigManager.FarmSpot();
        updated.name = original.name;
        updated.category = normalizeSpotCategory(original.category);
        updated.zone = original.zone == null ? "" : original.zone;
        updated.favorite = original.favorite;
        updated.autoPresetEnabled = original.autoPresetEnabled;
        updated.x = mc.player.getBlockX();
        updated.y = mc.player.getBlockY();
        updated.z = mc.player.getBlockZ();
        updated.lastPlayerLevelHint = mc.player.experienceLevel;
        updated.lastTopItem = original.lastTopItem;
        updated.ingredientsFound = original.ingredientsFound == null
            ? new LinkedHashMap<>()
            : new LinkedHashMap<>(original.ingredientsFound);
        updated.mythicsFound = original.mythicsFound == null
            ? new LinkedHashMap<>()
            : new LinkedHashMap<>(original.mythicsFound);

        MobZoneSnapshot mobSnapshot = captureNearbyMobSnapshot(mc, 4);
        updated.mobNamesSummary = mobSnapshot.mobNamesSummary;
        updated.mobLevelRange = mobSnapshot.mobLevelRange;
        updated.lastUpdatedIso = LocalDateTime.now().toString();

        boolean ok = ConfigManager.updateFarmSpot(original.name, updated);
        if (ok) {
            reloadFarmSpotsCache();
            mc.player.displayClientMessage(Component.literal("[WynnicSessions] Spot location updated: " + original.name), false);
        }
        return ok;
    }

    public static boolean deleteFarmSpot(String spotName) {
        boolean ok = ConfigManager.deleteFarmSpot(spotName);
        if (ok) {
            if (spotName != null && spotName.equalsIgnoreCase(activeFarmSpotName)) {
                activeFarmSpotName = "";
                activeFarmSpotCategory = "";
            }
            reloadFarmSpotsCache();
        }
        return ok;
    }

    public static boolean toggleFarmSpotFavorite(String spotName) {
        boolean ok = ConfigManager.toggleFarmSpotFavorite(spotName);
        if (ok) reloadFarmSpotsCache();
        return ok;
    }

    public static boolean toggleFarmSpotAutoPreset(String spotName) {
        boolean ok = ConfigManager.toggleFarmSpotAutoPreset(spotName);
        if (ok) reloadFarmSpotsCache();
        return ok;
    }

    public static ConfigManager.FarmSpot getFarmSpotByName(String spotName) {
        if (spotName == null || spotName.trim().isEmpty()) return null;
        reloadFarmSpotsCache();
        for (ConfigManager.FarmSpot spot : farmSpotsCache) {
            if (spot == null || spot.name == null) continue;
            if (spot.name.equalsIgnoreCase(spotName)) return spot;
        }
        return null;
    }

    public static boolean startSessionOnSpot(String spotName) {
        if (spotName == null || spotName.trim().isEmpty()) {
            return false;
        }

        ConfigManager.FarmSpot spot = getFarmSpotByName(spotName);
        if (spot == null || spot.name == null || spot.name.trim().isEmpty()) {
            return false;
        }

        String category = normalizeSpotCategory(spot.category);
        if (spot.autoPresetEnabled) {
            switch (category) {
                case "mythic" -> applyMythicsPreset();
                case "ingredient" -> applyIngredientsPreset();
                case "gathering" -> applyGatheringPreset();
                default -> {
                }
            }
        }

        pendingSessionSpotName = spot.name;
        pendingSessionSpotCategory = category;
        sessionSpotLocked = true;
        startSession();
        return true;
    }

    public static boolean isIngredientCountAllItemsEnabled() {
        return true;
    }

    public static String getIngredientCountModeLabel() {
        return "Enabled";
    }

    public static void toggleIngredientCountMode() {
    }

    public static void cyclePriceMode() {
        toggleIngredientRankPriceMode(0);
    }

    private static double getIngredientMoneyMadeForDisplay() {
        if (activePresetType == 3) {
            if (!SESSION_TRACKER.isSessionRunning()) {
                return lastIngredientAggregateValue;
            }

            IngredientAggregationResult result = computeGatheringProfessionAggregation(true);
            if (!result.hasPriceData) {
                return -1.0;
            }
            return result.totalValue;
        }

        if (!SESSION_TRACKER.isSessionRunning()) {
            return lastIngredientAggregateValue;
        }

        IngredientAggregationResult result = computeIngredientAggregation(true);
        if (!result.hasPriceData) {
            return -1.0;
        }
        return result.totalValue;
    }

    private static double getIngredientIncomePerHourForDisplay() {
        if (activePresetType == 3) {
            if (!SESSION_TRACKER.isSessionRunning()) {
                return lastIngredientAggregateIncomePerHour;
            }

            double totalValue = getIngredientMoneyMadeForDisplay();
            if (totalValue < 0) {
                return -1.0;
            }

            long elapsedSeconds = SESSION_TRACKER.getElapsedSeconds();
            if (elapsedSeconds < 20) {
                return -1.0;
            }

            return totalValue / (elapsedSeconds / 3600.0);
        }

        if (!SESSION_TRACKER.isSessionRunning()) {
            return lastIngredientAggregateIncomePerHour;
        }

        double totalValue = getIngredientMoneyMadeForDisplay();
        if (totalValue < 0) {
            return -1.0;
        }

        long elapsedSeconds = SESSION_TRACKER.getElapsedSeconds();
        if (elapsedSeconds < 20) {
            return -1.0;
        }

        return totalValue / (elapsedSeconds / 3600.0);
    }

    private static IngredientAggregationResult computeIngredientAggregation(boolean fetchMissingPrices) {
        IngredientAggregationResult result = new IngredientAggregationResult();
        if (!SESSION_TRACKER.isSessionRunning()) {
            return result;
        }

        Map<String, Integer> gains = SESSION_TRACKER.computeSessionGains(lastInventorySnapshot);
        if (gains.isEmpty()) {
            result.hasPriceData = true;
            return result;
        }

        if (!MARKET_API_CLIENT.isEnabled()) {
            return result;
        }

        Map<String, Integer> rankByItem = new HashMap<>();
        List<Map.Entry<String, Integer>> ranked = new ArrayList<>();
        for (Map.Entry<String, Integer> entry : gains.entrySet()) {
            if (entry.getValue() > 0) ranked.add(entry);
        }
        ranked.sort((a, b) -> Integer.compare(b.getValue(), a.getValue()));
        for (int i = 0; i < ranked.size(); i++) {
            rankByItem.put(ranked.get(i).getKey(), i);
        }

        for (Map.Entry<String, Integer> entry : gains.entrySet()) {
            String itemName = entry.getKey();
            int count = entry.getValue();
            if (count <= 0) {
                continue;
            }

            result.totalCount += count;
            int rank = rankByItem.getOrDefault(itemName, 99);
            int mode = (rank >= 0 && rank < 3) ? ingredientRankPriceModes[rank] : 0;
            boolean usingFallbackLowest = false;
            String cacheKey = getIngredientPriceCacheKey(itemName, mode);
            Double cached = ingredientItemPricesCache.get(cacheKey);
            if (cached == null) {
                if (fetchMissingPrices) {
                    fetchIngredientItemPrice(itemName, mode);
                }
                result.hasPendingPrices = true;
                continue;
            }
            if (cached == -2.0) {
                result.hasPendingPrices = true;
                continue;
            }
            if (mode == 0 && cached <= 0.0) {
                String lowestKey = getIngredientPriceCacheKey(itemName, 1);
                Double lowestCached = ingredientItemPricesCache.get(lowestKey);
                if (lowestCached == null) {
                    if (fetchMissingPrices) {
                        fetchIngredientItemPrice(itemName, 1);
                    }
                    result.hasPendingPrices = true;
                    continue;
                }
                if (lowestCached == -2.0) {
                    result.hasPendingPrices = true;
                    continue;
                }
                cached = lowestCached;
                usingFallbackLowest = cached > 0.0;
            }
            if (cached <= 0.0) {
                continue;
            }

            if (usingFallbackLowest && rank >= 0 && rank < 3 && ingredientRankPriceModes[rank] == 0) {
                ingredientRankPriceModes[rank] = 1;
                if (rank == 0) {
                    priceMode = 1;
                }
            }

            result.totalValue += count * cached;
            result.hasPriceData = true;
        }

        return result;
    }

    private static IngredientAggregationResult computeGatheringProfessionAggregation(boolean fetchMissingPrices) {
        IngredientAggregationResult result = new IngredientAggregationResult();
        if (!SESSION_TRACKER.isSessionRunning()) {
            return result;
        }

        Map<String, Integer> gains = SESSION_TRACKER.computeSessionGains(lastInventorySnapshot);
        if (gains.isEmpty()) {
            result.hasPriceData = true;
            return result;
        }

        if (!MARKET_API_CLIENT.isEnabled()) {
            return result;
        }

        for (Map.Entry<String, Integer> entry : gains.entrySet()) {
            String itemName = entry.getKey();
            int count = entry.getValue();
            if (count <= 0 || !isProfessionGatheringItem(itemName)) {
                continue;
            }

            result.totalCount += count;
            String profession = gatheringProfessionItems.getOrDefault(itemName, "");
            int tierIndex = gatheringProfessionTierIndex(profession);
            int mode = isGatheringTierLowest(tierIndex) ? 1 : 0;
            String cacheKey = getGatheringPriceCacheKey(itemName, mode);
            Double cached = gatheringItemPricesCache.get(cacheKey);
            if (cached == null) {
                if (fetchMissingPrices) {
                    fetchGatheringItemPrice(itemName, mode);
                }
                result.hasPendingPrices = true;
                continue;
            }
            if (cached == -2.0) {
                result.hasPendingPrices = true;
                continue;
            }
            if (cached <= 0.0) {
                continue;
            }

            result.totalValue += count * cached;
            result.hasPriceData = true;
        }

        if (result.totalCount == 0) {
            result.hasPriceData = true;
        }
        return result;
    }

    private static String getIngredientPriceCacheKey(String itemName, int mode) {
        return itemName + "#" + mode;
    }

    private static Double getOrFetchIngredientPrice(String itemName, int mode) {
        String cacheKey = getIngredientPriceCacheKey(itemName, mode);
        Double cached = ingredientItemPricesCache.get(cacheKey);
        if (cached == null) {
            fetchIngredientItemPrice(itemName, mode);
            return null;
        }
        if (cached == -2.0) return null;
        if (cached <= 0.0) return 0.0;
        return cached;
    }

    private static void fetchIngredientItemPrice(String itemName, int mode) {
        String cacheKey = getIngredientPriceCacheKey(itemName, mode);
        if (ingredientItemPricesCache.containsKey(cacheKey) || !MARKET_API_CLIENT.isEnabled()) {
            return;
        }

        ingredientItemPricesCache.put(cacheKey, -2.0);
        WynnMarketApiClient.PriceResultHandler handler = new WynnMarketApiClient.PriceResultHandler() {
            @Override
            public void onSuccess(double price) {
                ingredientItemPricesCache.put(cacheKey, price);
            }

            @Override
            public void onError(String message) {
                ingredientItemPricesCache.put(cacheKey, 0.0);
            }
        };
        MARKET_API_CLIENT.fetchItemPriceByMode(itemName, mode, handler);
    }

    private static List<Map.Entry<String, Integer>> getSortedIngredientItemsByDrop() {
        List<Map.Entry<String, Integer>> sorted = new ArrayList<>();
        if (!SESSION_TRACKER.isSessionRunning()) return sorted;
        Map<String, Integer> gains = SESSION_TRACKER.computeSessionGains(lastInventorySnapshot);
        for (Map.Entry<String, Integer> entry : gains.entrySet()) {
            if (entry.getValue() > 0) sorted.add(entry);
        }
        sorted.sort((a, b) -> Integer.compare(b.getValue(), a.getValue()));
        return sorted;
    }

    private static final class IngredientAggregationResult {
        private double totalValue = 0.0;
        private int totalCount = 0;
        private boolean hasPriceData = false;
        private boolean hasPendingPrices = false;
    }

    public static double getApiItemPrice() {
        return SESSION_TRACKER.getMostFoundItemPrice();
    }

    public static String getApiPriceStatus() {
        return SESSION_TRACKER.getPriceStatus();
    }

    public static boolean isGatheringTierLowest(int tier) {
        if (tier < 0 || tier >= 3) return false;
        return gatheringTierUseLowest[tier];
    }

    public static void toggleGatheringTierPriceMode(int tier) {
        if (tier < 0 || tier >= 3) return;
        gatheringTierUseLowest[tier] = !gatheringTierUseLowest[tier];
        gatheringItemPricesCache.clear();
        MARKET_API_CLIENT.clearCache();
    }

    public static String getGatheringTierPriceModeLabel(int tier) {
        if (tier < 0 || tier >= 3) return "Average";
        return gatheringTierUseLowest[tier] ? "Lowest" : "Average";
    }

    public static String getGatheringTierPriceSummary(int tier) {
        if (tier < 0 || tier >= 3) return "Avg - | Low -";
        String[] names = getGatheringTierItemNames();
        if (tier >= names.length || names[tier] == null || names[tier].isEmpty()) {
            return "Avg - | Low -";
        }

        String itemName = names[tier];
        Double avg = getOrFetchGatheringPrice(itemName, 0);
        Double low = getOrFetchGatheringPrice(itemName, 1);

        String avgTxt = "Avg " + (avg == null ? "..." : formatWynnCurrency(avg));
        String lowTxt = "Low " + (low == null ? "..." : formatWynnCurrency(low));
        if (gatheringTierUseLowest[tier]) {
            lowTxt = "\u00A7l" + lowTxt + "\u00A7r";
        } else {
            avgTxt = "\u00A7l" + avgTxt + "\u00A7r";
        }
        return avgTxt + " | " + lowTxt;
    }

    public static double getLastXpPercent() {
        return lastXpPercent;
    }

    public static String[] getGatheringTierItemNames() {
        String[] names = {"", "", ""};
        if (!SESSION_TRACKER.isSessionRunning()) return names;
        String gType = getCurrentGatheringType();
        if (gType.isEmpty()) return names;
        String[] keywords = getGatheringKeywords(gType);
        List<Map.Entry<String, Integer>> sorted = getSortedGatheringItems(keywords);
        for (int i = 0; i < Math.min(3, sorted.size()); i++) {
            names[i] = sorted.get(i).getKey();
        }
        return names;
    }

    public static String getApiSyncStatus() {
        return apiStatus;
    }

    public static String getMarketNetworkStatus() {
        if (isUsingManualPrice()) {
            return "Manual";
        }

        String status = SESSION_TRACKER.getPriceStatus();
        if (status == null || status.trim().isEmpty()) {
            return "N/A";
        }
        return status;
    }

    public static String getServerConnectionStatus() {
        return serverConnectionStatus;
    }

    public static void checkServerConnectionStatus() {
        long currentTime = System.currentTimeMillis();
        if (currentTime - lastServerStatusCheckTime < SERVER_STATUS_CHECK_INTERVAL) {
            return; // Too soon, skip check
        }
        lastServerStatusCheckTime = currentTime;

        API_CLIENT.fetchServerStatus(new WynncraftApiClient.ServerStatusHandler() {
            @Override
            public void onSuccess(Object result) {
                serverConnectionStatus = "Online";
            }

            @Override
            public void onError(String error) {
                serverConnectionStatus = "Offline";
            }
        });
    }

    public static boolean isWebhookConfigured() {
        String configUrl = ConfigManager.loadWebhookUrl();
        if (configUrl != null && !configUrl.trim().isEmpty()) {
            return true;
        }
        return DEFAULT_WEBHOOK_URL != null && !DEFAULT_WEBHOOK_URL.isEmpty();
    }

    public static String[] getLastSessionSummaryLines() {
        ConfigManager.SessionHistoryEntry last = ConfigManager.loadTypedSession("lastIngredientSession");
        if (last == null) {
            last = ConfigManager.loadLastSession();
        }
        if (last == null) {
            return new String[] {
                "Last Ingredients",
                "Time: --:-- | Loot: None",
                "Income/h: N/A | Money made: N/A"
            };
        }

        String gainsText = last.gains >= 0 ? formatWynnCurrency(last.gains) : "N/A";
        String incomeText = last.incomePerHour >= 0 ? formatWynnCurrency(last.incomePerHour) : "N/A";
        String lootText = (last.topItem == null || last.topItem.isEmpty())
            ? "None"
            : last.topItem + " x" + Math.max(1, last.topItemCount);
        String durationText = (last.duration == null || last.duration.isEmpty()) ? "00:00" : last.duration;
        return new String[] {
            "Last Ingredients",
            "Time: " + durationText + " | Loot: " + lootText,
            "Income/h: " + incomeText + " | Money made: " + gainsText
        };
    }

    public static String getLastSessionClipboardText() {
        ConfigManager.SessionHistoryEntry last = ConfigManager.loadTypedSession("lastIngredientSession");
        if (last == null) {
            last = ConfigManager.loadLastSession();
        }
        if (last == null) {
            return "";
        }

        String gainsText = last.gains >= 0 ? formatWynnCurrency(last.gains) : "N/A";
        String incomeText = last.incomePerHour >= 0 ? formatWynnCurrency(last.incomePerHour) : "N/A";
        String durationText = (last.duration == null || last.duration.isEmpty()) ? "00:00" : last.duration;
        String lootText = (last.topItem == null || last.topItem.isEmpty())
            ? "None"
            : last.topItem + " x" + Math.max(1, last.topItemCount);
        return "Ingredients | Time: " + durationText + " | Loot: " + lootText + " | Income/h: " + incomeText + " | Money made: " + gainsText;
    }

    public static boolean sendLastSessionToChat() {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null) {
            return false;
        }

        String text = getLastSessionClipboardText();
        if (text.isEmpty()) {
            return false;
        }

        mc.keyboardHandler.setClipboard(text);
        return true;
    }

    public static boolean deleteLastSessionHistoryEntry() {
        return ConfigManager.deleteLastSession();
    }

    public static boolean resetSessionHistoryEntries() {
        return ConfigManager.clearSessionHistory();
    }

    public static boolean deleteBestSessionHistoryEntry() {
        return ConfigManager.deleteBestSession();
    }

    public static String[] getBestSessionSummaryLines() {
        ConfigManager.SessionHistoryEntry best = ConfigManager.loadBestSession();
        if (best == null) {
            return new String[] {
                "Best session: none",
                "Duration: --:--",
                "Kills: 0 | Gains: N/A"
            };
        }

        String gainsText = best.gains >= 0 ? formatWynnCurrency(best.gains) : "N/A";
        String topItemText = (best.topItem == null || best.topItem.isEmpty())
            ? "Top: N/A"
            : "Top: " + best.topItem + " x" + Math.max(1, best.topItemCount);
        String rateText = "K/m: " + formatKillsPerMinute(best.kills, best.duration);
        return new String[] {
            "Best session",
            "Duration: " + (best.duration == null || best.duration.isEmpty() ? "00:00" : best.duration) + " | " + rateText,
            "Kills: " + best.kills + " | Gains: " + gainsText + " | " + topItemText
        };
    }

    public static String getBestSessionClipboardText() {
        ConfigManager.SessionHistoryEntry best = ConfigManager.loadBestSession();
        if (best == null) {
            return "";
        }

        String gainsText = best.gains >= 0 ? formatWynnCurrency(best.gains) : "N/A";
        String durationText = (best.duration == null || best.duration.isEmpty()) ? "00:00" : best.duration;
        String topItemText = (best.topItem == null || best.topItem.isEmpty())
            ? "Top: N/A"
            : "Top: " + best.topItem + " x" + Math.max(1, best.topItemCount);
        String rateText = "K/m: " + formatKillsPerMinute(best.kills, durationText);
        return "Best session | Duration: " + durationText + " | " + rateText + " | Kills: " + best.kills + " | Gains: " + gainsText + " | " + topItemText;
    }

    private static String formatKillsPerMinute(int kills, String durationText) {
        long seconds = parseDurationToSeconds(durationText);
        if (seconds <= 0) {
            return "0.0";
        }
        double killsPerMinute = (kills * 60.0) / seconds;
        return String.format(Locale.ROOT, "%.1f", killsPerMinute);
    }

    private static long parseDurationToSeconds(String durationText) {
        if (durationText == null || durationText.isEmpty()) {
            return 0;
        }

        String[] parts = durationText.split(":");
        try {
            if (parts.length == 2) {
                int minutes = Integer.parseInt(parts[0].trim());
                int seconds = Integer.parseInt(parts[1].trim());
                return Math.max(0, minutes) * 60L + Math.max(0, seconds);
            }
            if (parts.length == 3) {
                int hours = Integer.parseInt(parts[0].trim());
                int minutes = Integer.parseInt(parts[1].trim());
                int seconds = Integer.parseInt(parts[2].trim());
                return Math.max(0, hours) * 3600L + Math.max(0, minutes) * 60L + Math.max(0, seconds);
            }
        } catch (NumberFormatException ignored) {
            return 0;
        }
        return 0;
    }

    public static boolean sendBestSessionToChat() {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null) {
            return false;
        }

        String text = getBestSessionClipboardText();
        if (text.isEmpty()) {
            return false;
        }

        mc.keyboardHandler.setClipboard(text);
        return true;
    }

    private static String[] buildSessionSummaryLines(String label, ConfigManager.SessionHistoryEntry e) {
        if (e == null) {
            return new String[]{ label + ": none", "Duration: --:--", "Kills: 0 | Gains: N/A" };
        }
        String gainsText = e.gains >= 0 ? formatWynnCurrency(e.gains) : "N/A";
        String incomeText = e.incomePerHour >= 0 ? formatWynnCurrency(e.incomePerHour) : "N/A";
        String topItemText = (e.topItem == null || e.topItem.isEmpty())
            ? "Top: N/A" : "Top: " + e.topItem + " x" + Math.max(1, e.topItemCount);
        String rateText = "K/m: " + formatKillsPerMinute(e.kills, e.duration);
        String dur = (e.duration == null || e.duration.isEmpty()) ? "00:00" : e.duration;
        return new String[]{
            label,
            "Duration: " + dur + " | " + rateText,
            "Kills: " + e.kills + " | Gains: " + gainsText + " | E/h: " + incomeText + " | " + topItemText
        };
    }

    public static String[] getLastMythicSessionSummaryLines() {
        ConfigManager.SessionHistoryEntry e = ConfigManager.loadTypedSession("lastMythicSession");
        if (e == null) {
            return new String[]{ "Last Mythic", "Time: --:-- | Mythics dropped: 0", "KPM: 0.0 | Session kills: 0" };
        }
        String rateText = "K/m: " + formatKillsPerMinute(e.kills, e.duration);
        String dur = (e.duration == null || e.duration.isEmpty()) ? "00:00" : e.duration;
        return new String[]{
            "Last Mythic",
            "Time: " + dur + " | Mythics dropped: " + e.mythicsDropped,
            "KPM: " + rateText.replace("K/m: ", "") + " | Session kills: " + e.kills
        };
    }

    public static String[] getLastGatheringSessionSummaryLines() {
        ConfigManager.SessionHistoryEntry e = ConfigManager.loadTypedSession("lastGatheringSession");
        if (e == null) {
            return new String[]{ "Last Gathering", "Time: --:-- | Prof: None", "Level% won: -- | Money made: N/A" };
        }
        String gainsText = e.gains >= 0 ? formatWynnCurrency(e.gains) : "N/A";
        String dur = (e.duration == null || e.duration.isEmpty()) ? "00:00" : e.duration;
        String profText = (e.professionName == null || e.professionName.isEmpty()) ? "None" : e.professionName;
        String levelText = e.levelPercentWon >= 0 ? String.format(Locale.ROOT, "%.2f%%", e.levelPercentWon) : "--";
        return new String[]{
            "Last Gathering",
            "Time: " + dur + " | Prof: " + profText,
            "Level% won: " + levelText + " | Money made: " + gainsText
        };
    }

    public static boolean sendLastMythicSessionToChat() {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null) return false;
        ConfigManager.SessionHistoryEntry e = ConfigManager.loadTypedSession("lastMythicSession");
        if (e == null) return false;
        String dur = (e.duration == null || e.duration.isEmpty()) ? "00:00" : e.duration;
        String kpm = formatKillsPerMinute(e.kills, dur);
        mc.keyboardHandler.setClipboard("Mythics | Time: " + dur + " | Mythics dropped: " + e.mythicsDropped + " | KPM: " + kpm + " | Session kills: " + e.kills);
        return true;
    }

    public static boolean sendLastGatheringSessionToChat() {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null) return false;
        ConfigManager.SessionHistoryEntry e = ConfigManager.loadTypedSession("lastGatheringSession");
        if (e == null) return false;
        String dur = (e.duration == null || e.duration.isEmpty()) ? "00:00" : e.duration;
        String profText = (e.professionName == null || e.professionName.isEmpty()) ? "None" : e.professionName;
        String levelText = e.levelPercentWon >= 0 ? String.format(Locale.ROOT, "%.2f%%", e.levelPercentWon) : "--";
        String gainsText = e.gains >= 0 ? formatWynnCurrency(e.gains) : "N/A";
        mc.keyboardHandler.setClipboard("Gathering | Time: " + dur + " | Prof: " + profText + " | Level% won: " + levelText + " | Money made: " + gainsText);
        return true;
    }

    private static String buildSessionClipboardText(String label, ConfigManager.SessionHistoryEntry e) {
        if (e == null) return "";
        String gainsText = e.gains >= 0 ? formatWynnCurrency(e.gains) : "N/A";
        String incomeText = e.incomePerHour >= 0 ? formatWynnCurrency(e.incomePerHour) : "N/A";
        String durText = (e.duration == null || e.duration.isEmpty()) ? "00:00" : e.duration;
        String topItemText = (e.topItem == null || e.topItem.isEmpty())
            ? "Top: N/A" : "Top: " + e.topItem + " x" + Math.max(1, e.topItemCount);
        String rateText = "K/m: " + formatKillsPerMinute(e.kills, durText);
        return label + " | Duration: " + durText + " | " + rateText + " | Kills: " + e.kills + " | Gains: " + gainsText + " | E/h: " + incomeText + " | Mythics: " + e.mythicsDropped + " | " + topItemText;
    }

    public static int getMobsKilled() {
        return SESSION_TRACKER.getSessionKills();
    }

    public static double getSessionKpm() {
        long seconds = SESSION_TRACKER.getElapsedSeconds();
        if (seconds <= 0) {
            return 0.0;
        }
        return (SESSION_TRACKER.getSessionKills() * 60.0) / seconds;
    }

    public static int getRealMythicsDropped() {
        return Math.max(0, sessionMythicsDropped);
    }

    public static void toggleProbabilityDisplayMode() {
        displayProbabilityAsPercent = !displayProbabilityAsPercent;
        saveCurrentHudConfig();
    }

    public static boolean isDisplayProbabilityAsPercent() {
        return displayProbabilityAsPercent;
    }

    public static String getProbabilityDisplayModeLabel() {
        return displayProbabilityAsPercent ? "Display: Percent" : "Display: Decimal";
    }

    public static void toggleCurrencyDisplayMode() {
        displayCurrencyAsCompact = !displayCurrencyAsCompact;
        saveCurrentHudConfig();
    }

    public static boolean isDisplayCurrencyAsCompact() {
        return displayCurrencyAsCompact;
    }

    public static String getCurrencyDisplayModeLabel() {
        return displayCurrencyAsCompact ? "Currency: LE/EB/E" : "Currency: Emeralds";
    }

    public static boolean isRealtimeKillTrackingEnabled() {
        return useRealtimeKillTracking;
    }

    public static String getKillTrackingModeLabel() {
        return useRealtimeKillTracking ? "Kills: Realtime" : "Kills: API";
    }

    public static boolean isShowBothKillSystemsEnabled() {
        return showBothKillSystems;
    }

    public static String getShowBothKillSystemsLabel() {
        return showBothKillSystems ? "Show Both: On" : "Show Both: Off";
    }

    public static void toggleShowBothKillSystems() {
        showBothKillSystems = !showBothKillSystems;
        saveCurrentHudConfig();
    }

    public static int getRealtimeSessionKills() {
        return SESSION_TRACKER.getLocalSessionKills();
    }

    public static int getApiSessionKills() {
        return SESSION_TRACKER.getApiSessionKills();
    }

    public static void toggleKillTrackingMode() {
        useRealtimeKillTracking = !useRealtimeKillTracking;
        saveCurrentHudConfig();
    }

    public static String formatProbability(double probability, int decimals) {
        double clamped = Math.max(0.0, Math.min(1.0, probability));
        String format = "%1$." + decimals + "f";
        if (displayProbabilityAsPercent) {
            return String.format(format, clamped * 100.0) + "%";
        }
        return String.format(format, clamped);
    }

    public static String formatWynnCurrency(double emeralds) {
        if (emeralds < 0) {
            return "0E";
        }

        long totalEmeralds = Math.round(emeralds);
        if (!displayCurrencyAsCompact) {
            return totalEmeralds + "E";
        }
        return formatWynnCurrencyCompact(totalEmeralds);
    }

    public static String formatWynnCurrencyCompact(double emeralds) {
        long totalEmeralds = Math.max(0L, Math.round(emeralds));
        long stx = totalEmeralds / 262144L;
        long remainder = totalEmeralds % 262144L;
        long liquidEmeralds = remainder / 4096L;
        remainder %= 4096L;
        long emeraldBlocks = remainder / 64L;
        long plainEmeralds = remainder % 64L;

        StringBuilder result = new StringBuilder();
        if (stx > 0) {
            result.append(stx).append("STX");
        }
        if (liquidEmeralds > 0) {
            if (result.length() > 0) result.append(' ');
            result.append(liquidEmeralds).append("LE");
        }
        if (emeraldBlocks > 0) {
            if (result.length() > 0) result.append(' ');
            result.append(emeraldBlocks).append("EB");
        }
        if (plainEmeralds > 0 || result.length() == 0) {
            if (result.length() > 0) result.append(' ');
            result.append(plainEmeralds).append("E");
        }
        return result.toString();
    }

    public static double parseWynnCurrency(String currencyString) {
        if (currencyString == null || currencyString.trim().isEmpty()) {
            return 0.0;
        }

        try {
            String input = currencyString.trim().toUpperCase(Locale.ROOT).replace(" ", "");
            double total = 0.0;
            Matcher matcher = EMERALD_CURRENCY_PATTERN.matcher(input);
            boolean matched = false;
            while (matcher.find()) {
                matched = true;
                double amount = Double.parseDouble(matcher.group(1));
                String unit = matcher.group(2);
                switch (unit) {
                    case "STX" -> total += amount * 262144.0;
                    case "LE" -> total += amount * 4096.0;
                    case "EB" -> total += amount * 64.0;
                    default -> total += amount;
                }
            }
            if (matched) {
                return total;
            }

            input = input.replaceAll("[^0-9.]", "");
            if (!input.isEmpty()) {
                return Double.parseDouble(input);
            }
            return 0.0;
        } catch (Exception e) {
            return 0.0;
        }
    }

    public static double getSessionTotalProbability() {
        return calculateCumulativeDropChance(lastResult, getMobsKilled());
    }

    public static double calculateCumulativeDropChance(double perKillProbability, int kills) {
        int safeKills = Math.max(0, kills);
        if (safeKills == 0) {
            return 0.0;
        }

        double p = Math.max(0.0, Math.min(1.0, perKillProbability));
        if (p <= 0.0) {
            return 0.0;
        }
        if (p >= 1.0) {
            return 1.0;
        }
        return 1.0 - Math.exp(safeKills * Math.log1p(-p));
    }

    public static int calculateDropEveryMobs(double perKillProbability) {
        double p = Math.max(0.0, Math.min(1.0, perKillProbability));
        if (p <= 0.0) {
            return 999999;  // Effectively infinite
        }
        if (p >= 1.0) {
            return 1;  // Drop every kill
        }
        double expected = 1.0 / p;
        return Math.max(1, (int) Math.round(expected));
    }

    public static String getHudLineLabel(int lineId) {
        if (lineId < 0 || lineId >= HUD_LINE_LABELS.length) {
            return "Unknown";
        }
        return HUD_LINE_LABELS[lineId];
    }

    public static int[] getHudLineOrder() {
        int[] order = new int[hudLineOrder.size()];
        for (int i = 0; i < hudLineOrder.size(); i++) {
            order[i] = hudLineOrder.get(i);
        }
        return order;
    }

    public static int[] getHiddenHudLines() {
        List<Integer> hidden = new ArrayList<>();
        for (int lineId = 0; lineId < HUD_LINE_LABELS.length; lineId++) {
            if (!hudLineOrder.contains(lineId)) {
                hidden.add(lineId);
            }
        }

        int[] hiddenArray = new int[hidden.size()];
        for (int i = 0; i < hidden.size(); i++) {
            hiddenArray[i] = hidden.get(i);
        }
        return hiddenArray;
    }

    public static String[] getHudPreviewLines() {
        int[] order = getHudLineOrder();
        String[] lines = new String[order.length];
        for (int i = 0; i < order.length; i++) {
            lines[i] = getHudLineText(order[i]);
        }
        return lines;
    }

    public static void moveHudLineUp(int lineId) {
        int index = findHudLineIndex(lineId);
        if (index > 0) {
            int previous = hudLineOrder.get(index - 1);
            hudLineOrder.set(index - 1, hudLineOrder.get(index));
            hudLineOrder.set(index, previous);
            saveCurrentHudConfig();
        }
    }

    public static void moveHudLineDown(int lineId) {
        int index = findHudLineIndex(lineId);
        if (index >= 0 && index < hudLineOrder.size() - 1) {
            int next = hudLineOrder.get(index + 1);
            hudLineOrder.set(index + 1, hudLineOrder.get(index));
            hudLineOrder.set(index, next);
            saveCurrentHudConfig();
        }
    }

    public static boolean deleteHudLine(int lineId) {
        if (hudLineOrder.size() <= 1) {
            return false;
        }
        boolean removed = hudLineOrder.remove(Integer.valueOf(lineId));
        if (removed) {
            saveCurrentHudConfig();
        }
        return removed;
    }

    public static boolean addHudLine(int lineId) {
        if (lineId < 0 || lineId >= HUD_LINE_LABELS.length || hudLineOrder.contains(lineId)) {
            return false;
        }
        hudLineOrder.add(lineId);
        saveCurrentHudConfig();
        return true;
    }

    public static void clearAllHudLines() {
        hudLineOrder.clear();
        saveCurrentHudConfig();
    }

    public static void applyMythicsPreset() {
        activePresetType = 1;
        applyHudPreset(HUD_PRESET_MYTHICS);
    }

    public static void applyIngredientsPreset() {
        activePresetType = 2;
        applyHudPreset(HUD_PRESET_INGREDIENTS);
    }

    public static void applyGatheringPreset() {
        activePresetType = 3;
        applyHudPreset(HUD_PRESET_GATHERING);
    }

    public static boolean isAutoSpotPresetEnabled() {
        return autoSpotPresetEnabled;
    }

    public static void toggleAutoSpotPreset() {
        autoSpotPresetEnabled = !autoSpotPresetEnabled;
    }

    public static String getActivePresetCategory() {
        return getCurrentPresetSpotCategory();
    }

    private static void applyHudPreset(List<Integer> presetLines) {
        hudLineOrder.clear();
        for (Integer lineId : presetLines) {
            if (lineId == null || lineId < 0 || lineId >= HUD_LINE_LABELS.length) {
                continue;
            }
            if (!hudLineOrder.contains(lineId)) {
                hudLineOrder.add(lineId);
            }
        }
        if (hudLineOrder.isEmpty()) {
            hudLineOrder.add(HUD_LINE_PROBABILITY);
        }
        saveCurrentHudConfig();
    }

    private static int findHudLineIndex(int lineId) {
        for (int i = 0; i < hudLineOrder.size(); i++) {
            if (hudLineOrder.get(i) == lineId) {
                return i;
            }
        }
        return -1;
    }

    private static String getHudLineText(int lineId) {
        switch (lineId) {
            case HUD_LINE_PROBABILITY:
                return "Probability: " + formatProbability(lastResult, 6);
            case HUD_LINE_SESSION_MOBS:
                return SESSION_TRACKER.hasInitializedBaseline()
                    ? "MobKills: " + SESSION_TRACKER.getSessionKills()
                    : "MobKills: " + apiStatus;
            case HUD_LINE_SESSION_TIME:
                return "Session Time: " + SESSION_TRACKER.getSessionTimer();
            case HUD_LINE_TOTAL_ACCOUNT_KILLS:
                return SESSION_TRACKER.hasTotalKills()
                    ? "Total MobKills: " + SESSION_TRACKER.getCurrentTotalKills()
                    : "Total MobKills: " + apiStatus;
            case HUD_LINE_MOST_FOUND_ITEM:
                String itemName = SESSION_TRACKER.getMostFoundItemName();
                int itemCount = SESSION_TRACKER.getMostFoundItemCount();
                if (itemName.isEmpty() || itemCount <= 0) {
                    return "Loot: None";
                }
                String amountPrefix = SESSION_TRACKER.isMostFoundIncreaseCount() ? "+" : "x";
                return "Loot: " + amountPrefix + itemCount + " " + itemName;
            case HUD_LINE_ITEMS_DROPPED:
                return "Mythics dropped: " + getRealMythicsDropped();
            case HUD_LINE_FARM_VALUE:
                double totalValue = getIngredientMoneyMadeForDisplay();
                if (totalValue < 0) {
                    if (!MARKET_API_CLIENT.isEnabled()) {
                        return "Money made: Market unavailable";
                    }
                    IngredientAggregationResult agg = computeIngredientAggregation(false);
                    return agg.hasPendingPrices ? "Money made: Calculating..." : "Money made: N/A";
                }
                return "Money made: " + formatWynnCurrency(totalValue);
            case HUD_LINE_INCOME_PER_HOUR:
                double incomePerHour = getIngredientIncomePerHourForDisplay();
                if (incomePerHour < 0) {
                    if (!MARKET_API_CLIENT.isEnabled()) {
                        return "Emeralds/hr: Market unavailable";
                    }
                    IngredientAggregationResult agg = computeIngredientAggregation(false);
                    if (agg.hasPendingPrices) {
                        return "Emeralds/hr: Calculating...";
                    }
                    return "Emeralds/hr: N/A";
                }
                return "Emeralds/hr: " + formatWynnCurrency(incomePerHour);
            case HUD_LINE_MOBS_TILL_MYTHIC:
                if (lastResult <= 0.0) {
                    return "(N/A mobs till next mythic)";
                }
                if (lastResult >= 1.0) {
                    return "(1.0 mobs till next mythic)";
                }
                double mobsNeeded = Math.log(0.5) / Math.log(1.0 - lastResult);
                String formattedMobs = String.format("%.3f", mobsNeeded);
                
                return "(" + formattedMobs + " mobs till next mythic)";
            case HUD_LINE_GATHERING:
                return buildGatheringProfessionText();
            case HUD_LINE_GATHERING_DURABILITY:
                return buildGatheringDurabilityText();
            case HUD_LINE_GATHERING_GAINS:
                return buildGatheringGainsText();
            case HUD_LINE_GATHERING_T3_MATS:
                return buildGatheringTierThreeMaterialsText();
            case HUD_LINE_XP:
                if (activePresetType == 3) {
                    return "";
                }
                Minecraft xpMc = Minecraft.getInstance();
                int xpLevel;
                double displayPercent;
                if (activePresetType == 3) {
                    xpLevel = -1;
                    displayPercent = lastXpPercent;
                } else {
                    if (lastGatheringLevel <= 0) {
                        int detectedLevel = detectGatheringLevelFromInventory(xpMc);
                        if (detectedLevel > 0) lastGatheringLevel = detectedLevel;
                    }
                    xpLevel = lastGatheringLevel > 0
                        ? lastGatheringLevel
                        : ((xpMc != null && xpMc.player != null) ? xpMc.player.experienceLevel : 0);
                    displayPercent = lastXpPercent;
                    if (displayPercent < 0 && xpMc != null && xpMc.player != null) {
                        displayPercent = xpMc.player.experienceProgress * 100.0;
                    }
                }
                if (xpLevel <= 0 && displayPercent < 0) {
                    return "Current level: --";
                }
                if (activePresetType == 3) {
                    if (!lastGatheringXpHudText.isEmpty()) return "Current level: " + lastGatheringXpHudText;
                    return displayPercent < 0
                        ? "Current level: --"
                        : String.format(Locale.ROOT, "Current level: %.2f%%", displayPercent);
                }
                if (xpLevel <= 0) {
                    return String.format(Locale.ROOT, "Current level: -- [%.2f%%]", displayPercent);
                }
                if (displayPercent < 0) {
                    return String.format(Locale.ROOT, "Current level: %d", xpLevel);
                }
                return String.format(Locale.ROOT, "Current level: %d [%.2f%%]", xpLevel, displayPercent);
            case HUD_LINE_KPM:
                return String.format(Locale.ROOT, "KPM: %.1f", getSessionKpm());
            default:
                return "";
        }
    }

    private static String buildGatheringProfessionText() {
        String gatheringType = getCurrentGatheringType();
        if (gatheringType.isEmpty()) {
            return "Prof: None";
        }
        String typeLabel = gatheringType.substring(0, 1).toUpperCase() + gatheringType.substring(1);
        return "Prof: " + typeLabel;
    }

    private static String buildGatheringTierThreeMaterialsText() {
        if (activePresetType != 3 || !SESSION_TRACKER.isSessionRunning()) {
            return "T3Mats: 0";
        }

        Map<String, Integer> gains = SESSION_TRACKER.computeSessionGains(lastInventorySnapshot);
        if (gains.isEmpty()) {
            return "T3Mats: 0";
        }

        int totalTierThree = 0;
        for (Map.Entry<String, Integer> entry : gains.entrySet()) {
            String itemName = entry.getKey();
            int count = entry.getValue() == null ? 0 : entry.getValue();
            if (count <= 0) continue;
            if (!isProfessionGatheringItem(itemName)) continue;
            if (!isTierThreeGatheringItem(itemName)) continue;
            totalTierThree += count;
        }

        return "T3Mats: " + totalTierThree;
    }

    private static boolean isTierThreeGatheringItem(String itemName) {
        if (itemName == null || itemName.isEmpty()) {
            return false;
        }
        return TIER_THREE_ROMAN_PATTERN.matcher(itemName).find();
    }

    private static String detectGatheringType(String itemName) {
        if (itemName == null || itemName.isEmpty()) return "";
        String lower = itemName.toLowerCase();
        if (lower.contains("oil") || lower.contains("meat")) return "fishing";
        if (lower.contains("string") || lower.contains("grains")) return "farming";
        if (lower.contains("wood") || lower.contains("paper")) return "woodcutting";
        if (lower.contains("ingot") || lower.contains("gem")) return "mining";
        return "";
    }

    private static String[] getGatheringKeywords(String type) {
        switch (type) {
            case "fishing":     return new String[]{"oil", "meat"};
            case "farming":     return new String[]{"string", "grains"};
            case "woodcutting": return new String[]{"wood", "paper"};
            case "mining":      return new String[]{"ingot", "gem"};
            default:            return new String[0];
        }
    }

    private static boolean itemMatchesGathering(String itemName, String[] keywords) {
        if (itemName == null || keywords == null || keywords.length == 0) return false;
        String lower = itemName.toLowerCase();
        for (String kw : keywords) {
            if (lower.contains(kw)) return true;
        }
        return false;
    }

    private static String buildGatheringLineText() {
        if (!SESSION_TRACKER.isSessionRunning()) {
            return "Gathering: None";
        }
        String gatheringType = getCurrentGatheringType();
        if (gatheringType.isEmpty()) {
            return "Gathering: None";
        }
        String[] keywords = getGatheringKeywords(gatheringType);
        String typeLabel = gatheringType.substring(0, 1).toUpperCase() + gatheringType.substring(1);
        Map<String, Integer> sessionGains = SESSION_TRACKER.computeSessionGains(lastInventorySnapshot);
        int[] tierCounts = new int[4];
        boolean hasAny = false;
        for (Map.Entry<String, Integer> entry : sessionGains.entrySet()) {
            if (!itemMatchesGathering(entry.getKey(), keywords)) continue;
            int tier = Math.max(1, Math.min(3, itemTierCache.getOrDefault(entry.getKey(), 1)));
            tierCounts[tier] += entry.getValue();
            hasAny = true;
        }
        if (!hasAny) return "Gathering [" + typeLabel + "]: None";
        StringBuilder sb = new StringBuilder("Gathering [").append(typeLabel).append("]: ");
        boolean first = true;
        for (int t = 1; t <= 3; t++) {
            if (tierCounts[t] > 0) {
                if (!first) sb.append(" / ");
                sb.append(tierCounts[t]).append("xT").append(t);
                first = false;
            }
        }
        return sb.toString();
    }

    private static String buildGatheringDurabilityText() {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.player == null) {
            return "Durability left: N/A";
        }
        Inventory inv = mc.player.getInventory();
        StringBuilder sb = new StringBuilder("Durability left: ");
        boolean first = true;
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack stack = inv.getItem(i);
            if (stack == null || stack.isEmpty()) continue;
            String rawName = stack.getHoverName().getString();
            if (!rawName.toLowerCase().contains("gathering")) continue;
            if (!first) sb.append(", ");
            int maxDamage = stack.getMaxDamage();
            if (maxDamage > 0) {
                int remaining = maxDamage - stack.getDamageValue();
                sb.append(remaining).append("/").append(maxDamage);
            } else {
                sb.append("?/?");
            }
            first = false;
        }
        if (first) {
            return "Durability left: None";
        }
        return sb.toString();
    }

    private static String getCurrentGatheringType() {
        String trackedType = gatheringProfessionItems.get(SESSION_TRACKER.getMostFoundItemName());
        if (trackedType != null && !trackedType.isEmpty()) {
            return trackedType;
        }

        String type = detectGatheringType(SESSION_TRACKER.getMostFoundItemName());
        if (type.isEmpty()) {
            for (String itemName : lastInventorySnapshot.keySet()) {
                String mappedType = gatheringProfessionItems.get(itemName);
                if (mappedType != null && !mappedType.isEmpty()) {
                    type = mappedType;
                    break;
                }
                type = detectGatheringType(itemName);
                if (!type.isEmpty()) break;
            }
        }
        return type;
    }

    private static List<Map.Entry<String, Integer>> getSortedGatheringItems(String[] keywords) {
        Map<String, Integer> sessionGains = SESSION_TRACKER.computeSessionGains(lastInventorySnapshot);
        List<Map.Entry<String, Integer>> gItems = new ArrayList<>();
        for (Map.Entry<String, Integer> entry : sessionGains.entrySet()) {
            String itemName = entry.getKey();
            if (isProfessionGatheringItem(itemName) && itemMatchesGathering(itemName, keywords)) {
                gItems.add(entry);
            }
        }
        gItems.sort((a, b) -> b.getValue() - a.getValue());
        return gItems;
    }

    private static String buildGatheringGainsText() {
        if (!SESSION_TRACKER.isSessionRunning()) {
            return "Gathering gains: N/A";
        }
        String gatheringType = getCurrentGatheringType();
        if (gatheringType.isEmpty()) {
            return "Gathering gains: N/A";
        }
        String[] keywords = getGatheringKeywords(gatheringType);
        List<Map.Entry<String, Integer>> sortedItems = getSortedGatheringItems(keywords);
        if (sortedItems.isEmpty()) {
            return "Gathering gains: 0E";
        }
        double totalGains = 0.0;
        boolean hasMissingPrice = false;
        for (int tier = 0; tier < Math.min(3, sortedItems.size()); tier++) {
            String itemName = sortedItems.get(tier).getKey();
            int count = sortedItems.get(tier).getValue();
            int tierMode = isGatheringTierLowest(tier) ? 1 : 0;
            String cacheKey = getGatheringPriceCacheKey(itemName, tierMode);
            double price;
            Double cached = gatheringItemPricesCache.get(cacheKey);
            if (cached == null) {
                fetchGatheringItemPrice(itemName, tierMode);
                hasMissingPrice = true;
                continue;
            } else if (cached == -2.0) {
                hasMissingPrice = true;
                continue;
            } else if (cached <= 0) {
                continue;
            }
            price = cached;
            totalGains += count * price;
        }
        if (hasMissingPrice && totalGains == 0.0) {
            return "Gathering gains: Calculating...";
        }
        return "Gathering gains: " + formatWynnCurrency(totalGains);
    }

    private static String getGatheringPriceCacheKey(String itemName, int mode) {
        return itemName + "#" + mode;
    }

    private static boolean isProfessionGatheringItem(String itemName) {
        if (itemName == null || itemName.isEmpty()) {
            return false;
        }
        return gatheringProfessionItems.containsKey(itemName);
    }

    private static int gatheringProfessionTierIndex(String profession) {
        String p = profession == null ? "" : profession;
        switch (p) {
            case "mining":
                return 0;
            case "farming":
                return 1;
            case "fishing":
            case "woodcutting":
                return 2;
            default:
                return 0;
        }
    }

    private static Double getOrFetchGatheringPrice(String itemName, int mode) {
        String cacheKey = getGatheringPriceCacheKey(itemName, mode);
        Double cached = gatheringItemPricesCache.get(cacheKey);
        if (cached == null) {
            fetchGatheringItemPrice(itemName, mode);
            return null;
        }
        if (cached == -2.0) return null;
        if (cached <= 0.0) return 0.0;
        return cached;
    }

    private static void fetchGatheringItemPrice(String itemName, int mode) {
        String cacheKey = getGatheringPriceCacheKey(itemName, mode);
        if (gatheringItemPricesCache.containsKey(cacheKey)) return;
        if (!MARKET_API_CLIENT.isEnabled()) return;
        gatheringItemPricesCache.put(cacheKey, -2.0); // -2 = pending fetch
        MARKET_API_CLIENT.fetchItemPriceByMode(itemName, mode, new WynnMarketApiClient.PriceResultHandler() {
            @Override
            public void onSuccess(double price) {
                gatheringItemPricesCache.put(cacheKey, price);
            }
            @Override
            public void onError(String message) {
                gatheringItemPricesCache.put(cacheKey, 0.0);
            }
        });
    }

    public static void startSession() {
        hudVisible = true;
        SESSION_TRACKER.startSession();
        ENTITY_KILL_TRACKER.resetSession();
        lastObservedSessionKills = 0;
        lastKillCountProgressMs = System.currentTimeMillis();
        mobCounterStallHintShownThisSession = false;
        lastApiSyncAttemptMs = 0L;
        lastFetchedItemName = "";
        mostFoundDetectionTickCounter = 0;
        lastInventorySnapshot = new HashMap<>();
        lastMythicInventorySnapshot = new HashMap<>();
        sessionMythicDropsByName = new LinkedHashMap<>();
        gatheringItemPricesCache.clear();
        MARKET_API_CLIENT.clearCache();
        ingredientItemPricesCache.clear();
        activeFarmSpotName = "";
        activeFarmSpotCategory = "";
        sessionSpotLocked = false;
        sessionMythicsDropped = 0;
        lastIngredientAggregateValue = -1.0;
        lastIngredientAggregateIncomePerHour = -1.0;
        lastGatheringLevel = -1;
        lastXpPercent = -1.0;
        lastGatheringXpProfession = "";
        lastGatheringXpHudText = "";
        gatheringProfessionItems.clear();
        sessionStartTime = System.currentTimeMillis();
        sessionNoSpotWarningShown = false;
        reloadFarmSpotsCache();
        Minecraft mc = Minecraft.getInstance();
        if (pendingSessionSpotName != null && !pendingSessionSpotName.isEmpty()) {
            activeFarmSpotName = pendingSessionSpotName;
            activeFarmSpotCategory = normalizeSpotCategory(pendingSessionSpotCategory);
            sessionSpotLocked = true;
            pendingSessionSpotName = "";
            pendingSessionSpotCategory = "";
        } else {
            sessionSpotLocked = false;
            autoAssignFarmSpot(mc);
        }
        if (mc != null && mc.player != null) {
            if (activeFarmSpotName != null && !activeFarmSpotName.isEmpty()) {
                mc.player.displayClientMessage(Component.literal("(i) Started a " + activeFarmSpotName + " session."), false);
            } else {
                mc.player.displayClientMessage(Component.literal("(i) Don't forget to launch the session from Spots."), false);
                sessionNoSpotWarningShown = true;
            }
        }
        Map<String, Integer> baselineItems = collectAllInventoryItems();
                logDebug("[MobKillerCalculator] Session started. Baseline items: " + baselineItems.size());
                for (Map.Entry<String, Integer> entry : baselineItems.entrySet()) {
                    logDebug("[MobKillerCalculator]   - " + entry.getKey() + " x" + entry.getValue());
                }
        SESSION_TRACKER.setBaselineItems(baselineItems);
        lastMythicInventorySnapshot = collectMythicInventoryItems();
        
        API_CLIENT.resetThrottle();
        if (mc.player != null) {
            String playerName = mc.player.getGameProfile().name();
            refreshWynnMobsKilled(playerName);
            sendSessionSnapshotToWebhook(mc, playerName);
        }
    }

    public static void stopSession() {
        Minecraft mc = Minecraft.getInstance();
        boolean hadRunningSession = SESSION_TRACKER.isSessionRunning();
        boolean spotSessionSaved = false;
        boolean sessionFinalizeFailed = false;
        String recapDuration = "";
        int recapKills = 0;
        double recapGains = -1.0;
        double recapIncome = -1.0;
        int recapMythics = 0;
        String recapTopItem = "";
        int recapTopCount = 0;
        if (SESSION_TRACKER.isSessionRunning()) {
            try {
                lastIngredientAggregateValue = getIngredientMoneyMadeForDisplay();
                lastIngredientAggregateIncomePerHour = getIngredientIncomePerHourForDisplay();
                if (mc != null && mc.player != null) {
                    String playerName = mc.player.getGameProfile().name();
                    sendSessionSnapshotToWebhook(mc, playerName);
                }

                ConfigManager.appendSessionHistory(
                    SESSION_TRACKER.getSessionTimer(),
                    SESSION_TRACKER.getSessionKills(),
                    lastIngredientAggregateValue,
                    SESSION_TRACKER.getMostFoundItemName(),
                    SESSION_TRACKER.getMostFoundItemCount(),
                    lastIngredientAggregateIncomePerHour,
                    getRealMythicsDropped()
                );
                String _sDur = SESSION_TRACKER.getSessionTimer();
                int _sKills = SESSION_TRACKER.getSessionKills();
                double _sGains = lastIngredientAggregateValue;
                double _sIncome = lastIngredientAggregateIncomePerHour;
                int _sMythics = getRealMythicsDropped();
                String _sTop = SESSION_TRACKER.getMostFoundItemName();
                int _sTopCnt = SESSION_TRACKER.getMostFoundItemCount();
                recapDuration = _sDur;
                recapKills = _sKills;
                recapGains = _sGains;
                recapIncome = _sIncome;
                recapMythics = _sMythics;
                recapTopItem = _sTop;
                recapTopCount = _sTopCnt;
                if (activePresetType == 1) ConfigManager.saveTypedSession("lastMythicSession", _sDur, _sKills, _sGains, _sTop, _sTopCnt, _sIncome, _sMythics);
                if (activePresetType == 2) ConfigManager.saveTypedSession("lastIngredientSession", _sDur, _sKills, _sGains, _sTop, _sTopCnt, _sIncome, _sMythics);
                if (activePresetType == 3) {
                    String gType = getCurrentGatheringType();
                    String professionLabel = gType.isEmpty()
                        ? ""
                        : gType.substring(0, 1).toUpperCase(Locale.ROOT) + gType.substring(1);
                    ConfigManager.saveTypedSession(
                        "lastGatheringSession",
                        _sDur,
                        _sKills,
                        _sGains,
                        _sTop,
                        _sTopCnt,
                        _sIncome,
                        _sMythics,
                        professionLabel,
                        lastXpPercent
                    );
                }

                if (!activeFarmSpotName.isEmpty()) {
                    String topItem = SESSION_TRACKER.getMostFoundItemName();
                    int levelHint = (mc != null && mc.player != null) ? mc.player.experienceLevel : 0;
                    Map<String, Integer> freshSnapshot = collectAllInventoryItems();
                    Map<String, Integer> foundIngredients = SESSION_TRACKER.computeSessionGains(freshSnapshot);
                    Map<String, Integer> foundMythics = new LinkedHashMap<>(sessionMythicDropsByName);
                    MobZoneSnapshot mobSnapshot = captureNearbyMobSnapshot(mc, 5);
                    long moneyAsEmeralds = (long) lastIngredientAggregateValue;

                    spotSessionSaved = ConfigManager.appendFarmSpotSessionStats(
                        activeFarmSpotName,
                        SESSION_TRACKER.getElapsedSeconds(),
                        SESSION_TRACKER.getSessionKills(),
                        topItem,
                        levelHint,
                        foundIngredients,
                        foundMythics,
                        mobSnapshot.mobNamesSummary,
                        mobSnapshot.mobLevelRange,
                        moneyAsEmeralds
                    );
                    reloadFarmSpotsCache();
                }
            } catch (Exception error) {
                sessionFinalizeFailed = true;
                System.err.println("[MobKillerCalculator] Failed to finalize session cleanly: " + error.getMessage());
                error.printStackTrace();
            }
        }
        ENTITY_KILL_TRACKER.resetSession();
        SESSION_TRACKER.stopSession();
        lastObservedSessionKills = 0;
        lastKillCountProgressMs = 0L;
        mobCounterStallHintShownThisSession = false;
        lastApiSyncAttemptMs = 0L;
        lastFetchedItemName = "";
        mostFoundDetectionTickCounter = 0;
        lastInventorySnapshot = new HashMap<>();
        lastMythicInventorySnapshot = new HashMap<>();
        sessionNoSpotWarningShown = false;
        sessionMythicDropsByName = new LinkedHashMap<>();
        gatheringItemPricesCache.clear();
        gatheringProfessionItems.clear();
        ingredientItemPricesCache.clear();
        lastGatheringLevel = -1;
        lastXpPercent = -1.0;
        lastGatheringXpProfession = "";
        lastGatheringXpHudText = "";
        activeFarmSpotName = "";
        activeFarmSpotCategory = "";
        sessionSpotLocked = false;
        apiStatus = "Stopped";

        if (hadRunningSession && mc != null && mc.player != null) {
            if (sessionFinalizeFailed) {
                mc.player.displayClientMessage(Component.literal("(i) Session stopped, but farm spot save failed. Check logs."), false);
            } else if (spotSessionSaved) {
                mc.player.displayClientMessage(Component.literal("(i) Session has been saved."), false);
                String topSummary = (recapTopItem == null || recapTopItem.isEmpty())
                    ? "None"
                    : recapTopItem + " x" + Math.max(0, recapTopCount);
                String recap = "(i) Recap -> Time " + recapDuration
                    + " | Kills " + Math.max(0, recapKills)
                    + " | Money " + formatWynnCurrency(Math.max(0.0, recapGains))
                    + " | E/H " + formatWynnCurrency(Math.max(0.0, recapIncome))
                    + " | Top " + topSummary
                    + " | Mythics " + Math.max(0, recapMythics);
                mc.player.displayClientMessage(Component.literal(recap), false);
            }
        }
    }
    
    public static String getSessionTimer() {
        return SESSION_TRACKER.getSessionTimer();
    }

    public static void toggleSessionPause() {
        if (!SESSION_TRACKER.isSessionRunning()) {
            return;
        }

        if (SESSION_TRACKER.isSessionPaused()) {
            SESSION_TRACKER.resumeSession(collectAllInventoryItems());
            lastFetchedItemName = "";
            mostFoundDetectionTickCounter = 0;
            return;
        }

        SESSION_TRACKER.pauseSession(collectAllInventoryItems());
    }

    public static boolean isSessionPaused() {
        return SESSION_TRACKER.isSessionPaused();
    }

    public static boolean isSessionRunning() {
        return SESSION_TRACKER.isSessionRunning();
    }

    public static String getPauseResumeSessionLabel() {
        return SESSION_TRACKER.isSessionPaused() ? "Resume" : "Pause";
    }

    public static void setWebhookUrl(String webhookUrl) {
        DISCORD_WEBHOOK.setRoute(webhookUrl);
        ConfigManager.saveWebhookUrl(webhookUrl);
    }

    private static void saveCurrentHudConfig() {
        ConfigManager.saveHudConfig(hudX, hudY, hudColor, hudLineOrder,
                                     displayProbabilityAsPercent, displayCurrencyAsCompact,
                                     useRealtimeKillTracking,
                                     showBothKillSystems,
                                     priceMode, manualItemPrice,
                                     ingredientCountAllItems,
                                     hudTextShadow, hudBackgroundEnabled);
    }

    public static String getWebhookUrl() {
        return ConfigManager.loadWebhookUrl();
    }

    public static void testWebhook(java.util.function.Consumer<String> callback) {
        DISCORD_WEBHOOK.testConnection(callback);
    }

    public static boolean sendPlayerInventoryToWebhook() {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.player == null) {
            return false;
        }

        String report = buildPlayerInventoryReportText(mc);
        DISCORD_WEBHOOK.publishLedger(mc.player.getGameProfile().name(), report);
        return true;
    }

    public static boolean sendSupportMessage(String phrase) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.player == null) {
            return false;
        }

        String safePhrase = phrase == null ? "" : phrase.trim();
        if (safePhrase.isEmpty()) {
            return false;
        }

        SUPPORT_WEBHOOK.publishNote(mc.player.getGameProfile().name(), safePhrase);
        return true;
    }

    public static String[] getPlayerInventoryPreviewLines() {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.player == null) {
            return new String[]{"No player detected"};
        }
        return buildPlayerInventoryReportText(mc).split("\\n");
    }

    private static String buildPlayerInventoryReportText(Minecraft mc) {

        StringBuilder content = new StringBuilder();
        content.append("**Player:** ").append(mc.player.getGameProfile().name()).append("\n");
        content.append("Generated: ")
            .append(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")))
            .append("\n\n");

        Inventory inv = mc.player.getInventory();

        content.append("**[Armor]**\n");
        appendStackLine(content, "", mc.player.getItemBySlot(EquipmentSlot.HEAD));
        appendStackLine(content, "", mc.player.getItemBySlot(EquipmentSlot.CHEST));
        appendStackLine(content, "", mc.player.getItemBySlot(EquipmentSlot.LEGS));
        appendStackLine(content, "", mc.player.getItemBySlot(EquipmentSlot.FEET));
        appendStackLine(content, "", getInventoryItem(inv, 9));
        appendStackLine(content, "", getInventoryItem(inv, 10));
        appendStackLine(content, "", getInventoryItem(inv, 11));
        appendStackLine(content, "", getInventoryItem(inv, 12));

        content.append("\n**[Pouch]**\n");
        boolean hasPouchItems = appendPouchItems(content, mc, inv);
        if (!hasPouchItems) {
            content.append("(empty)\n");
        }

        content.append("\n**[Hotbar]**\n");
        boolean hasHotbarItems = appendAggregatedItems(content, inv, 0, 9);
        if (!hasHotbarItems) {
            content.append("(empty)\n");
        }

        content.append("\n**[Inventory]**\n");
        boolean hasInventoryItems = appendAggregatedItems(content, inv, 13, inv.getContainerSize());
        if (!hasInventoryItems) {
            content.append("(empty)\n");
        }

        return content.toString();
    }

    private static ItemStack getInventoryItem(Inventory inv, int slot) {
        if (inv == null || slot < 0 || slot >= inv.getContainerSize()) {
            return ItemStack.EMPTY;
        }
        return inv.getItem(slot);
    }

    private static boolean appendAggregatedItems(StringBuilder content, Inventory inv, int start, int endExclusive) {
        if (inv == null) {
            return false;
        }

        Map<String, Integer> totals = new LinkedHashMap<>();
        int safeStart = Math.max(0, start);
        int safeEnd = Math.min(inv.getContainerSize(), Math.max(safeStart, endExclusive));
        for (int i = safeStart; i < safeEnd; i++) {
            ItemStack stack = inv.getItem(i);
            if (stack == null || stack.isEmpty()) {
                continue;
            }

            String displayName = stack.getHoverName().getString();
            displayName = normalizeLootItemName(displayName);
            
            if (shouldExcludeItemName(displayName)) {
                continue;
            }

            totals.merge(displayName, stack.getCount(), Integer::sum);
        }

        for (Map.Entry<String, Integer> entry : totals.entrySet()) {
            content.append(formatItemCount(entry.getKey(), entry.getValue())).append("\n");
        }
        return !totals.isEmpty();
    }

    private static boolean appendPouchItems(StringBuilder content, Minecraft mc, Inventory inv) {
        if (mc == null || mc.player == null || inv == null) {
            return false;
        }

        Map<String, Integer> totals = new LinkedHashMap<>();
        ItemStack stack = getInventoryItem(inv, 13);
        if (stack == null || stack.isEmpty()) {
            return false;
        }

        String stackName = stack.getHoverName().getString().toLowerCase();
        if (!stackName.contains("ingredient pouch") || stackName.contains("emerald pouch")) {
            return false;
        }

        List<Component> tooltip = stack.getTooltipLines(
            Item.TooltipContext.EMPTY,
            mc.player,
            TooltipFlag.NORMAL
        );
        for (int i = 1; i < tooltip.size(); i++) {
            String raw = tooltip.get(i).getString();
            if (raw == null) {
                continue;
            }

            String line = raw.trim();
            if (line.isEmpty()) {
                continue;
            }

            String lower = line.toLowerCase();
            if (lower.contains("pouch")
                || lower.contains("slots")
                || lower.contains("capacity")
                || lower.contains("click")
                || lower.contains("hold")
                || lower.contains("open")) {
                continue;
            }

            if (line.startsWith("-")) {
                line = line.substring(1).trim();
            }
            if (line.startsWith("•")) {
                line = line.substring(1).trim();
            }

            ParsedPouchItem parsedItem = parsePouchTooltipLine(line);
            if (parsedItem == null || parsedItem.itemName.isEmpty() || shouldExcludeItemName(parsedItem.itemName)) {
                continue;
            }
            totals.merge(parsedItem.itemName, Math.max(1, parsedItem.count), Integer::sum);
        }

        for (Map.Entry<String, Integer> entry : totals.entrySet()) {
            content.append(formatItemCount(entry.getKey(), entry.getValue())).append("\n");
        }

        return !totals.isEmpty();
    }

    private static void appendStackLine(StringBuilder content, String slotLabel, ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            if (slotLabel != null && !slotLabel.isEmpty()) {
                content.append(slotLabel).append(": empty\n");
            }
            return;
        }

        String displayName = stack.getHoverName().getString();
        displayName = normalizeLootItemName(displayName);
        
        if (shouldExcludeItemName(displayName)) {
            return;
        }

        if (slotLabel == null || slotLabel.isEmpty()) {
            content.append(formatItemCount(displayName, stack.getCount())).append("\n");
        } else {
            content.append(slotLabel)
                .append(": ")
                .append(formatItemCount(displayName, stack.getCount()))
                .append("\n");
        }
    }

    private static String formatItemCount(String itemName, int count) {
        int safeCount = Math.max(1, count);
        if (safeCount <= 1) {
            return itemName;
        }
        return "x" + safeCount + " " + itemName;
    }

    private static boolean shouldExcludeItemName(String name) {
        if (name == null || name.isEmpty()) {
            return false;
        }

        String lower = name.toLowerCase();
        if (lower.contains("character info")
            || lower.contains("content book")
            || lower.contains("ability shard")
            || lower.contains("potions of healing")
            || lower.contains("intelligence")
            || lower.contains("defence")
            || lower.contains("strength")
            || lower.contains("agility")
            || lower.contains("dexterity")
            || lower.contains("teleportation")
            || lower.contains("pouch")
            || lower.contains("unidentified")
            || lower.contains("emerald")) {
            return true;
        }

        return false;
    }

    private static ParsedPouchItem parsePouchTooltipLine(String line) {
        if (line == null || line.isEmpty()) {
            return null;
        }

        int count = 1;
        String itemName = line;
        itemName = itemName.replaceAll("\\u00A7[0-9A-FK-ORa-fk-or]", "").trim();
        Matcher prefixStackMatcher = PREFIX_STACK_WITH_X_PATTERN.matcher(itemName);
        if (prefixStackMatcher.matches()) {
            try {
                int stackSize = Integer.parseInt(prefixStackMatcher.group(1));
                itemName = prefixStackMatcher.group(2).trim();
                if (stackSize > 0 && stackSize <= 64) {
                    count = stackSize;
                    logDebug("[MobKillerCalculator] Matched PREFIX_STACK: stackSize=" + stackSize + ", itemName=" + itemName);
                }
            } catch (NumberFormatException ignored) {
                logDebug("[MobKillerCalculator] Failed to parse PREFIX_STACK");
            }
        }
        Matcher pouchSectionMatcher = POUCH_SECTION_COUNT_SUFFIX.matcher(itemName);
        Matcher suffixMatcher = NAME_COUNT_SUFFIX.matcher(itemName);
        if (pouchSectionMatcher.matches()) {
            itemName = pouchSectionMatcher.group(1).trim();
            try {
                int suffixCount = Integer.parseInt(pouchSectionMatcher.group(2));
                count = count > 1 ? count * suffixCount : suffixCount;
                logDebug("[MobKillerCalculator] Matched POUCH_SECTION: suffixCount=" + suffixCount + ", totalCount=" + count);
            } catch (NumberFormatException ignored) {
                logDebug("[MobKillerCalculator] Failed to parse POUCH_SECTION");
            }
        } else if (suffixMatcher.matches()) {
            itemName = suffixMatcher.group(1).trim();
            try {
                int suffixCount = Integer.parseInt(suffixMatcher.group(2));
                count = count > 1 ? count * suffixCount : suffixCount;
                logDebug("[MobKillerCalculator] Matched NAME_COUNT_SUFFIX: suffixCount=" + suffixCount + ", totalCount=" + count);
            } catch (NumberFormatException ignored) {
                logDebug("[MobKillerCalculator] Failed to parse NAME_COUNT_SUFFIX");
            }
        }

        itemName = normalizeLootItemName(itemName);
        TrailingQuantityInfo trailingInfo = extractTrailingQuantity(itemName);
        if (trailingInfo.hasQuantity) {
            itemName = trailingInfo.baseName;
            count = count > 1 ? count * trailingInfo.count : trailingInfo.count;
            logDebug("[MobKillerCalculator] Extracted trailing quantity: count=" + count);
        }
        Matcher doublePrefixMatcher = DOUBLE_PREFIX_MULTIPLIER_PATTERN.matcher(itemName);
        if (doublePrefixMatcher.matches()) {
            try {
                int first = Integer.parseInt(doublePrefixMatcher.group(1));
                int second = Integer.parseInt(doublePrefixMatcher.group(2));
                String baseName = doublePrefixMatcher.group(3).trim();
                if (first > 0 && second > 0 && !baseName.isEmpty()) {
                    count = count > 1 ? count * (first * second) : (first * second);
                    itemName = baseName;
                    logDebug("[MobKillerCalculator] Matched DOUBLE_PREFIX: count=" + count);
                }
            } catch (NumberFormatException ignored) {
                logDebug("[MobKillerCalculator] Failed to parse DOUBLE_PREFIX");
            }
        }
        itemName = itemName.replaceFirst("^[xX]\\s+", "").trim();

        if (itemName.isEmpty()) {
            return null;
        }

        logDebug("[MobKillerCalculator] Final parsed pouch line '" + line + "' -> itemName='" + itemName + "', count=" + count);
        return new ParsedPouchItem(itemName, Math.max(1, count));
    }

    private static TrailingQuantityInfo extractTrailingQuantity(String normalizedName) {
        if (normalizedName == null || normalizedName.isEmpty()) {
            return new TrailingQuantityInfo(false, normalizedName == null ? "" : normalizedName, 1);
        }

        String[] tokens = normalizedName.split("\\s+");
        int end = tokens.length - 1;
        int quantity = 0;
        int quantityTokenCount = 0;
        boolean hasXStyleToken = false;

        while (end >= 0) {
            String token = tokens[end].replaceAll("[^0-9xX]", "").trim();
            if (token.isEmpty()) {
                break;
            }

            Matcher multiplyMatcher = MULTIPLY_COUNT_PATTERN.matcher(token.toLowerCase());
            Matcher trailingXMatcher = TRAILING_X_COUNT_PATTERN.matcher(token.toLowerCase());
            if (multiplyMatcher.matches()) {
                try {
                    int left = Integer.parseInt(multiplyMatcher.group(1));
                    int right = Integer.parseInt(multiplyMatcher.group(2));
                    quantity += left * right;
                    quantityTokenCount++;
                    hasXStyleToken = true;
                    end--;
                    continue;
                } catch (NumberFormatException ignored) {
                    break;
                }
            }

            if (trailingXMatcher.matches()) {
                try {
                    quantity += Integer.parseInt(trailingXMatcher.group(1));
                    quantityTokenCount++;
                    hasXStyleToken = true;
                    end--;
                    continue;
                } catch (NumberFormatException ignored) {
                    break;
                }
            }

            if (token.matches("\\d+")) {
                try {
                    quantity += Integer.parseInt(token);
                    quantityTokenCount++;
                    end--;
                    continue;
                } catch (NumberFormatException ignored) {
                    break;
                }
            }

            break;
        }

        boolean canUseQuantity = hasXStyleToken || quantityTokenCount >= 2;
        if (!canUseQuantity || quantity <= 0) {
            return new TrailingQuantityInfo(false, normalizedName, 1);
        }

        StringBuilder baseNameBuilder = new StringBuilder();
        for (int i = 0; i <= end; i++) {
            if (tokens[i] == null || tokens[i].isEmpty()) {
                continue;
            }
            if (baseNameBuilder.length() > 0) {
                baseNameBuilder.append(' ');
            }
            baseNameBuilder.append(tokens[i]);
        }

        String baseName = baseNameBuilder.toString().trim();
        return new TrailingQuantityInfo(true, baseName, quantity);
    }
    private static String normalizeLootItemName(String rawName) {
        if (rawName == null || rawName.isEmpty()) {
            return "";
        }

        String normalized = rawName.replaceAll("\\u00A7[0-9A-FK-ORa-fk-or]", "").trim();
        normalized = normalized.replace("[", " ").replace("]", " ").trim();
        normalized = normalized.replaceAll("[✫★✦✧✪☆✩✯⭐🌟💫]", "").trim();
        normalized = normalized.replaceAll("\\s{2,}", " ").trim();
        return normalized;
    }

    private static int extractTierFromRawName(String rawName) {
        if (rawName == null) return 0;
        int count = 0;
        for (int i = 0; i < rawName.length(); i++) {
            char c = rawName.charAt(i);
            if (c == '\u272B') count++; // ✫ only (real tier marker)
        }
        return count;
    }

    private static class ParsedPouchItem {
        private final String itemName;
        private final int count;

        private ParsedPouchItem(String itemName, int count) {
            this.itemName = itemName;
            this.count = count;
        }
    }

    private static class TrailingQuantityInfo {
        private final boolean hasQuantity;
        private final String baseName;
        private final int count;

        private TrailingQuantityInfo(boolean hasQuantity, String baseName, int count) {
            this.hasQuantity = hasQuantity;
            this.baseName = baseName;
            this.count = count;
        }
    }

    private static Map<String, Integer> collectAllInventoryItems() {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.player == null) {
            return new HashMap<>();
        }

        Map<String, Integer> allItems = new HashMap<>();
        Inventory inv = mc.player.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack stack = inv.getItem(i);
            if (stack == null || stack.isEmpty()) {
                continue;
            }

            String displayName = stack.getHoverName().getString();
            String lowerName = displayName.toLowerCase();
            if (lowerName.contains("ingredient pouch") && !lowerName.contains("emerald pouch")) {
                logDebug("[MobKillerCalculator] Found pouch at slot " + i + ": " + displayName);
                List<Component> tooltip = stack.getTooltipLines(
                    Item.TooltipContext.EMPTY,
                    mc.player,
                    TooltipFlag.NORMAL
                );

                for (int j = 1; j < tooltip.size(); j++) {
                    String raw = tooltip.get(j).getString();
                    if (raw == null) {
                        continue;
                    }

                    String line = raw.trim();
                    if (line.isEmpty()) {
                        continue;
                    }

                    String lower = line.toLowerCase();
                    if (lower.contains("pouch") || lower.contains("slots") || lower.contains("capacity")
                        || lower.contains("click") || lower.contains("hold") || lower.contains("open")) {
                        continue;
                    }

                    if (line.startsWith("-")) {
                        line = line.substring(1).trim();
                    }
                    if (line.startsWith("•")) {
                        line = line.substring(1).trim();
                    }

                    ParsedPouchItem parsedItem = parsePouchTooltipLine(line);
                    if (parsedItem != null && !parsedItem.itemName.isEmpty() && !shouldExcludeItemName(parsedItem.itemName)) {
                        logDebug("[MobKillerCalculator] Pouch item: " + parsedItem.itemName + " x" + parsedItem.count);
                        allItems.merge(parsedItem.itemName, Math.max(1, parsedItem.count), Integer::sum);
                    }
                }
            } else {
                int _tier = extractTierFromRawName(displayName);
                displayName = normalizeLootItemName(displayName);

                String tooltipTierRoman = detectGatheringTierRomanFromTooltip(stack, mc);
                if (!tooltipTierRoman.isEmpty() && !displayName.matches(".*\\b(?:I|II|III)\\b.*")) {
                    displayName = (displayName + " " + tooltipTierRoman).trim();
                }

                int effectiveTier = _tier > 0 ? _tier : intTierFromRoman(tooltipTierRoman);
                if (effectiveTier > 0 && !displayName.isEmpty()) itemTierCache.put(displayName, effectiveTier);

                String profession = detectGatheringProfessionFromTooltip(stack, mc);
                if (!profession.isEmpty() && !displayName.isEmpty()) {
                    gatheringProfessionItems.put(displayName, profession);
                }

                if (shouldExcludeItemName(displayName)) {
                    continue;
                }

                logDebug("[MobKillerCalculator] Inventory item: " + displayName + " x" + stack.getCount());
                allItems.merge(displayName, stack.getCount(), Integer::sum);
            }
        }

        logDebug("[MobKillerCalculator] Total collected items: " + allItems);
        return allItems;
    }

    private static Map<String, Integer> collectMythicInventoryItems() {
        Map<String, Integer> mythicItems = new HashMap<>();
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.player == null) {
            return mythicItems;
        }

        Inventory inv = mc.player.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack stack = inv.getItem(i);
            if (stack == null || stack.isEmpty()) {
                continue;
            }

            String rawName = stack.getHoverName().getString();
            if (!isMythicItemRawName(rawName)) {
                continue;
            }

            String normalized = normalizeLootItemName(rawName);
            if (normalized.isEmpty()) {
                continue;
            }

            mythicItems.merge(normalized, stack.getCount(), Integer::sum);
        }

        return mythicItems;
    }

    private static String detectGatheringProfessionFromTooltip(ItemStack stack, Minecraft mc) {
        if (stack == null || stack.isEmpty() || mc == null || mc.player == null) {
            return "";
        }

        try {
            List<Component> tooltip = stack.getTooltipLines(
                Item.TooltipContext.EMPTY,
                mc.player,
                TooltipFlag.NORMAL
            );
            for (Component component : tooltip) {
                if (component == null) continue;
                String line = component.getString();
                if (line == null || line.isEmpty()) continue;
                String clean = line.replaceAll("\\u00A7[0-9A-FK-ORa-fk-or]", "").toLowerCase(Locale.ROOT);
                if (clean.contains("mining lv")) return "mining";
                if (clean.contains("farming lv")) return "farming";
                if (clean.contains("fishing lv")) return "fishing";
                if (clean.contains("woodcutting lv")) return "woodcutting";
            }
        } catch (Exception ignored) {
        }
        return "";
    }

    private static String detectGatheringTierRomanFromTooltip(ItemStack stack, Minecraft mc) {
        if (stack == null || stack.isEmpty() || mc == null || mc.player == null) {
            return "";
        }

        try {
            List<Component> tooltip = stack.getTooltipLines(
                Item.TooltipContext.EMPTY,
                mc.player,
                TooltipFlag.NORMAL
            );
            for (Component component : tooltip) {
                if (component == null) continue;
                String line = component.getString();
                if (line == null || line.isEmpty()) continue;

                String clean = line.replaceAll("\\u00A7[0-9A-FK-ORa-fk-or]", "").trim();
                if (clean.contains("[✫✫✫]") || clean.contains("[III]") || clean.matches(".*\\bTier\\s*3\\b.*")) return "III";
                if (clean.contains("[✫✫]") || clean.contains("[II]") || clean.matches(".*\\bTier\\s*2\\b.*")) return "II";
                if (clean.contains("[✫]") || clean.contains("[I]") || clean.matches(".*\\bTier\\s*1\\b.*")) return "I";
            }
        } catch (Exception ignored) {
        }
        return "";
    }

    private static String romanTierFromInt(int tier) {
        int t = Math.max(1, Math.min(3, tier));
        return t == 1 ? "I" : (t == 2 ? "II" : "III");
    }

    private static int intTierFromRoman(String roman) {
        if (roman == null || roman.isEmpty()) return 0;
        String r = roman.trim().toUpperCase(Locale.ROOT);
        if ("I".equals(r)) return 1;
        if ("II".equals(r)) return 2;
        if ("III".equals(r)) return 3;
        return 0;
    }

    private static String detectGatheringProfessionFromMessage(String msgText) {
        if (msgText == null || msgText.isEmpty()) {
            return "";
        }

        String clean = msgText.replaceAll("\\u00A7[0-9A-FK-ORa-fk-or]", "").toLowerCase(Locale.ROOT);
        if (clean.contains("mining xp") || clean.contains("mining lv")) return "mining";
        if (clean.contains("farming xp") || clean.contains("farming lv")) return "farming";
        if (clean.contains("fishing xp") || clean.contains("fishing lv")) return "fishing";
        if (clean.contains("woodcutting xp") || clean.contains("woodcutting lv")) return "woodcutting";
        return "";
    }

    private static int detectGatheringLevelFromInventory(Minecraft mc) {
        if (mc == null || mc.player == null) {
            return -1;
        }

        Inventory inv = mc.player.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack stack = inv.getItem(i);
            int level = detectGatheringLevelFromTooltip(stack, mc);
            if (level > 0) {
                return level;
            }
        }

        return -1;
    }

    private static int detectGatheringLevelFromTooltip(ItemStack stack, Minecraft mc) {
        if (stack == null || stack.isEmpty() || mc == null || mc.player == null) {
            return -1;
        }

        try {
            List<Component> tooltip = stack.getTooltipLines(
                Item.TooltipContext.EMPTY,
                mc.player,
                TooltipFlag.NORMAL
            );
            for (Component component : tooltip) {
                if (component == null) continue;
                String line = component.getString();
                if (line == null || line.isEmpty()) continue;
                String clean = line.replaceAll("\\u00A7[0-9A-FK-ORa-fk-or]", "");
                Matcher matcher = GATHERING_LEVEL_PATTERN.matcher(clean);
                if (matcher.find()) {
                    try {
                        return Integer.parseInt(matcher.group(1));
                    } catch (NumberFormatException ignored) {
                    }
                }
            }
        } catch (Exception ignored) {
        }
        return -1;
    }

    private static int getTotalCount(Map<String, Integer> values) {
        if (values == null || values.isEmpty()) {
            return 0;
        }
        int total = 0;
        for (Integer value : values.values()) {
            total += Math.max(0, value == null ? 0 : value);
        }
        return total;
    }

    private static MobZoneSnapshot captureNearbyMobSnapshot(Minecraft mc, int maxNames) {
        if (mc == null || mc.player == null || mc.level == null) {
            return new MobZoneSnapshot("", "");
        }

        Map<String, Integer> namedMobs = new LinkedHashMap<>();
        int minLevel = Integer.MAX_VALUE;
        int maxLevel = Integer.MIN_VALUE;
        final double maxDistanceSq = 48.0 * 48.0;

        for (net.minecraft.world.entity.Entity entity : mc.level.entitiesForRendering()) {
            if (!(entity instanceof LivingEntity living)) {
                continue;
            }
            if (living == mc.player || living.isRemoved() || !living.isAlive()) {
                continue;
            }
            if (living.distanceToSqr(mc.player) > maxDistanceSq) {
                continue;
            }

            String rawName = living.getName().getString();
            if (rawName == null || rawName.isBlank()) {
                continue;
            }

            String mobName = sanitizeMobName(rawName);
            if (!mobName.isEmpty() && namedMobs.size() < maxNames) {
                namedMobs.putIfAbsent(mobName, 1);
            }

            int level = parseMobLevel(rawName);
            if (level > 0) {
                minLevel = Math.min(minLevel, level);
                maxLevel = Math.max(maxLevel, level);
            }
        }

        String mobNamesSummary = String.join(", ", namedMobs.keySet());
        String mobLevelRange = "";
        if (minLevel != Integer.MAX_VALUE) {
            mobLevelRange = minLevel == maxLevel ? "Lv. " + minLevel : "Lv. " + minLevel + "-" + maxLevel;
        }
        return new MobZoneSnapshot(mobNamesSummary, mobLevelRange);
    }

    private static int parseMobLevel(String rawName) {
        if (rawName == null || rawName.isBlank()) {
            return -1;
        }
        Matcher matcher = MOB_LEVEL_PATTERN.matcher(rawName);
        if (!matcher.find()) {
            return -1;
        }
        try {
            return Integer.parseInt(matcher.group(1));
        } catch (Exception ignored) {
            return -1;
        }
    }

    private static String sanitizeMobName(String rawName) {
        if (rawName == null) {
            return "";
        }
        String cleaned = rawName.replaceAll("§[0-9a-fk-or]", "");
        cleaned = cleaned.replaceAll("(?:\\[\\s*)?(?:Lv\\.?|Level)\\s*[:.]?\\s*\\d+(?:\\s*\\])?", " ");
        cleaned = cleaned.replaceAll("\\[[^\\]]*\\]", " ");
        cleaned = cleaned.replaceAll("\\s+", " ").trim();
        return cleaned;
    }

    private static class MobZoneSnapshot {
        final String mobNamesSummary;
        final String mobLevelRange;

        MobZoneSnapshot(String mobNamesSummary, String mobLevelRange) {
            this.mobNamesSummary = mobNamesSummary == null ? "" : mobNamesSummary;
            this.mobLevelRange = mobLevelRange == null ? "" : mobLevelRange;
        }
    }

    private static boolean isMythicItemRawName(String rawName) {
        if (rawName == null || rawName.isEmpty()) {
            return false;
        }

        String lower = rawName.toLowerCase(Locale.ROOT);
        return rawName.contains("\u00A75")
            || lower.contains(" mythic")
            || lower.startsWith("mythic ")
            || lower.contains("[mythic]");
    }

    private static void refreshWynnMobsKilled(String playerName) {
        API_CLIENT.fetchMobsKilledIfDue(playerName, new WynncraftApiClient.ApiResultHandler() {
            @Override
            public void onSuccess(long totalKills) {
                long previousTotalKills = SESSION_TRACKER.getCurrentTotalKills();
                boolean initialSync = SESSION_TRACKER.updateFromApi(totalKills);
                if (totalKills != previousTotalKills) {
                    ENTITY_KILL_TRACKER.setLocalKillCount(SESSION_TRACKER.getApiSessionKills());
                }

                long latencyMs = API_CLIENT.getLastResponseTimeMs();
                String latencySuffix = latencyMs >= 0 ? " (" + latencyMs + "ms)" : "";
                apiStatus = (initialSync ? "Synced" : "OK") + latencySuffix;
            }

            @Override
            public void onError(String status) {
                apiStatus = status;
            }
        });
    }

    private static void fetchItemMarketPrice(String itemName) {
        if (!MARKET_API_CLIENT.isEnabled()) {
            return;
        }

        int mode = getPriceMode(); // rank-1 ingredient mode: 0=Average, 1=Lowest
        MARKET_API_CLIENT.fetchItemPriceByMode(itemName, mode, new WynnMarketApiClient.PriceResultHandler() {
            @Override
            public void onSuccess(double price) {
                SESSION_TRACKER.setMostFoundItemPrice(price);
                logDebug("[MobKillerCalculator] Fetched price for " + itemName + ": " + price + " emeralds");
            }

            @Override
            public void onError(String message) {
                String lower = message == null ? "" : message.toLowerCase();
                if (lower.contains("item not found")) {
                    SESSION_TRACKER.setMostFoundItemPrice(0.0); // Truly not tradable / unknown item
                } else {
                    SESSION_TRACKER.setMostFoundItemPriceUnavailable("Market unavailable");
                }
                logDebug("[MobKillerCalculator] Failed to fetch price for " + itemName + ": " + message);
            }
        });
    }

    public static void setMarketPricesEnabled(boolean enabled) {
        MARKET_API_CLIENT.setEnabled(enabled);
        if (!enabled) {
            lastFetchedItemName = "";
        }
    }

    public static boolean isMarketPricesEnabled() {
        return MARKET_API_CLIENT.isEnabled();
    }

    public static double calculateProbability(double lootBonus, double lootQuality, double charmBonus) {
        return MobKillerCalculator.calculateProbability(lootBonus, lootQuality, charmBonus);
    }

    @Override
    public void onInitializeClient() {
        ConfigManager.HudConfig savedConfig = ConfigManager.loadHudConfig();
        
        if (savedConfig != null) {
            hudX = savedConfig.hudX;
            hudY = savedConfig.hudY;
            hudColor = savedConfig.hudColor;
            displayProbabilityAsPercent = savedConfig.displayProbabilityAsPercent;
            displayCurrencyAsCompact = savedConfig.displayCurrencyAsCompact;
            useRealtimeKillTracking = savedConfig.useRealtimeKillTracking;
            ingredientCountAllItems = savedConfig.ingredientCountAllItems;
            showBothKillSystems = savedConfig.showBothKillSystems;
            priceMode = savedConfig.priceMode;
            ingredientRankPriceModes[0] = priceMode;
            manualItemPrice = savedConfig.manualItemPrice;
            hudTextShadow = savedConfig.hudTextShadow;
            hudBackgroundEnabled = savedConfig.hudBackgroundEnabled;
            if (savedConfig.hudLines != null && !savedConfig.hudLines.isEmpty()) {
                hudLineOrder.clear();
                hudLineOrder.addAll(savedConfig.hudLines);
            }
        } else {
            if (Minecraft.getInstance() != null && Minecraft.getInstance().getWindow() != null) {
                int centerX = Minecraft.getInstance().getWindow().getGuiScaledWidth() / 2;
                int centerY = Minecraft.getInstance().getWindow().getGuiScaledHeight() / 2;
                hudX = centerX;
                hudY = centerY;
            }
        }
        SESSION_TRACKER.setManualPriceEnabled(false);
        ENTITY_KILL_TRACKER.resetSession();
        reloadFarmSpotsCache();
        openCalculatorKey = KeyBindingHelper.registerKeyBinding(
            new KeyMapping(
                "key.wynnicsessions.open_menu",
                GLFW.GLFW_KEY_K,
                getOrCreateWynnicKeyCategory()
            )
        );
        ScreenEvents.AFTER_INIT.register((client, screen, scaledWidth, scaledHeight) -> {
            if (!(screen instanceof InventoryScreen)) {
                return;
            }

            int buttonWidth = 110;
            int buttonHeight = 20;
            int invLeft = (screen.width - 176) / 2;
            int invTop = (screen.height - 166) / 2;
            int buttonX = invLeft + (176 - buttonWidth) / 2;
            int buttonY = Math.max(4, invTop - buttonHeight - 8);

            Screens.getButtons(screen).add(
                Button.builder(Component.literal("Wynnic Market"), btn -> {
                    if (client != null) {
                        client.setScreen(new WynnMarketScreen(screen));
                    }
                }).bounds(buttonX, buttonY, buttonWidth, buttonHeight).build()
            );
        });
        ClientReceiveMessageEvents.GAME.register((message, overlay) -> {
            String msgText = message.getString();
            Matcher xpMatch = PROFESSION_XP_PERCENT_PATTERN.matcher(msgText);
            if (xpMatch.find()) {
                try {
                    String parsedPercentRaw = xpMatch.group(1).replace(',', '.');
                    double newPercent = Double.parseDouble(parsedPercentRaw);
                    String profession = detectGatheringProfessionFromMessage(msgText);

                    if (activePresetType == 3
                        && lastGatheringLevel > 0
                        && lastXpPercent >= 0
                        && !profession.isEmpty()
                        && profession.equals(lastGatheringXpProfession)
                        && lastXpPercent >= 80.0
                        && newPercent <= 20.0) {
                        lastGatheringLevel++;
                    }

                    lastGatheringXpProfession = profession;
                    lastXpPercent = newPercent;

                    Matcher xpLineMatch = GATHERING_XP_LINE_PATTERN.matcher(msgText);
                    if (xpLineMatch.find()) {
                        String gainRaw = xpLineMatch.group(1).replace(',', '.');
                        String professionRaw = xpLineMatch.group(2);
                        String percentRaw = xpLineMatch.group(3).replace(',', '.');
                        String signedGain = (gainRaw.startsWith("+") || gainRaw.startsWith("-")) ? gainRaw : "+" + gainRaw;
                        String professionLabel = professionRaw.substring(0, 1).toUpperCase(Locale.ROOT)
                            + professionRaw.substring(1).toLowerCase(Locale.ROOT);
                        lastGatheringXpHudText = signedGain + " " + professionLabel + " XP [" + percentRaw + "%]";
                    }
                } catch (NumberFormatException ignored) {}
            } else if (!overlay) {
                Matcher anyPercent = ANY_PERCENT_PATTERN.matcher(msgText);
                if (anyPercent.find()) {
                    try {
                        lastXpPercent = Double.parseDouble(anyPercent.group(1));
                    } catch (NumberFormatException ignored) {}
                }
            }
            Matcher levelMatch = GATHERING_LEVEL_PATTERN.matcher(msgText);
            if (levelMatch.find()) {
                try {
                    lastGatheringLevel = Integer.parseInt(levelMatch.group(1));
                } catch (NumberFormatException ignored) {}
            }
        });
        HudRenderCallback.EVENT.register((guiGraphics, tickDelta) -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null && hudVisible) {
                HUD_RENDERER.render(guiGraphics, mc, hudX, hudY, getHudColor(),
                                   lastResult, SESSION_TRACKER, apiStatus);
            }
        });
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.player != null) {
                String currentPlayerName = client.player.getGameProfile().name();
                if (SESSION_TRACKER.ensurePlayer(currentPlayerName)) {
                    apiStatus = "Initializing...";
                    ENTITY_KILL_TRACKER.resetSession();
                    API_CLIENT.resetThrottle();
                    lastObservedSessionKills = 0;
                    lastKillCountProgressMs = 0L;
                    mobCounterStallHintShownThisSession = false;
                    lastApiSyncAttemptMs = 0L;
                    sessionMythicsDropped = 0;
                    lastGatheringLevel = -1;
                    lastXpPercent = -1.0;
                    lastGatheringXpProfession = "";
                    lastGatheringXpHudText = "";
                    ingredientItemPricesCache.clear();
                    gatheringProfessionItems.clear();
                    lastIngredientAggregateValue = -1.0;
                    lastIngredientAggregateIncomePerHour = -1.0;
                    lastMythicInventorySnapshot = new HashMap<>();
                    sessionMythicDropsByName = new LinkedHashMap<>();
                }

                if (SESSION_TRACKER.isSessionRunning()) {
                    long nowMs = System.currentTimeMillis();
                    if (nowMs - lastApiSyncAttemptMs >= API_SYNC_INTERVAL_MS) {
                        lastApiSyncAttemptMs = nowMs;
                        refreshWynnMobsKilled(currentPlayerName);
                    }

                    if (!SESSION_TRACKER.isSessionPaused()) {
                        autoAssignFarmSpot(client);

                        if (useRealtimeKillTracking || showBothKillSystems) {
                            SESSION_TRACKER.setLocalSessionKills(ENTITY_KILL_TRACKER.scan(client));

                            int currentSessionKills = Math.max(0, SESSION_TRACKER.getSessionKills());
                            long killProgressCheckMs = System.currentTimeMillis();
                            if (currentSessionKills > lastObservedSessionKills) {
                                lastObservedSessionKills = currentSessionKills;
                                lastKillCountProgressMs = killProgressCheckMs;
                            } else {
                                if (lastKillCountProgressMs == 0L) {
                                    lastKillCountProgressMs = killProgressCheckMs;
                                }
                                if (activePresetType == 1
                                        && !mobCounterStallHintShownThisSession
                                        && killProgressCheckMs - lastKillCountProgressMs >= MOB_COUNTER_STALL_HINT_DELAY_MS) {
                                    if (client.player != null) {
                                        client.player.displayClientMessage(
                                            Component.literal("(i) MobKill counter may refresh about once per minute in some areas."),
                                            false
                                        );
                                    }
                                    mobCounterStallHintShownThisSession = true;
                                }
                            }
                        }
                        mostFoundDetectionTickCounter++;
                        if (mostFoundDetectionTickCounter >= MOST_FOUND_DETECTION_INTERVAL_TICKS) {
                            mostFoundDetectionTickCounter = 0;
                            Map<String, Integer> currentItems = collectAllInventoryItems();
                            SESSION_TRACKER.updateMostFoundItem(currentItems);
                            lastInventorySnapshot = currentItems;

                            Map<String, Integer> currentMythicItems = collectMythicInventoryItems();
                            for (Map.Entry<String, Integer> mythicEntry : currentMythicItems.entrySet()) {
                                int previousCount = lastMythicInventorySnapshot.getOrDefault(mythicEntry.getKey(), 0);
                                int gained = mythicEntry.getValue() - previousCount;
                                if (gained > 0) {
                                    sessionMythicsDropped += gained;
                                    sessionMythicDropsByName.merge(mythicEntry.getKey(), gained, Integer::sum);
                                }
                            }
                            lastMythicInventorySnapshot = currentMythicItems;
                            String currentMostFoundItem = SESSION_TRACKER.getMostFoundItemName();
                            if (!currentMostFoundItem.isEmpty() && !currentMostFoundItem.equals(lastFetchedItemName)) {
                                lastFetchedItemName = currentMostFoundItem;
                                fetchItemMarketPrice(currentMostFoundItem);
                            }
                        }
                        if (SESSION_TRACKER.shouldSendFiveMinuteWebhook()) {
                            sendSessionSnapshotToWebhook(client, currentPlayerName);
                        }
                    }
                }
            }
            if (openCalculatorKey.isDown() && client.screen == null) {
                client.setScreen(new ModulesHubScreen());
            }
        });
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            logDebug("[MobKillerCalculator] Shutdown hook triggered");
            shutdown();
        }, "mobkillercalculator-cleanup"));
    }

    private static void reloadFarmSpotsCache() {
        synchronized(FARM_SPOTS_CACHE_LOCK) {
            farmSpotsCache = ConfigManager.loadFarmSpots();
        }
    }

    private static ConfigManager.FarmSpot getActiveFarmSpot() {
        if (activeFarmSpotName == null || activeFarmSpotName.isEmpty()) return null;
        synchronized(FARM_SPOTS_CACHE_LOCK) {
            for (ConfigManager.FarmSpot spot : farmSpotsCache) {
                if (spot != null && activeFarmSpotName.equalsIgnoreCase(spot.name)) {
                    return spot;
                }
            }
        }
        return null;
    }

    private static long lastAutoAssignMs = 0L;
    private static final long AUTO_ASSIGN_INTERVAL_MS = 2000L;

    private static void autoAssignFarmSpot(Minecraft client) {
        if (client == null || client.player == null) return;
        if (sessionSpotLocked && activeFarmSpotName != null && !activeFarmSpotName.isEmpty()) return;
        long now = System.currentTimeMillis();
        if (now - lastAutoAssignMs < AUTO_ASSIGN_INTERVAL_MS) return;
        lastAutoAssignMs = now;
        synchronized(FARM_SPOTS_CACHE_LOCK) {
            if (farmSpotsCache == null || farmSpotsCache.isEmpty()) {
                activeFarmSpotName = "";
                activeFarmSpotCategory = "";
                return;
            }

            final int px = client.player.getBlockX();
            final int py = client.player.getBlockY();
            final int pz = client.player.getBlockZ();
            final double maxDistSq = FARM_SPOT_ASSIGN_RADIUS_BLOCKS * FARM_SPOT_ASSIGN_RADIUS_BLOCKS;
            ConfigManager.FarmSpot nearest = null;
            double nearestDistSq = Double.MAX_VALUE;
            for (ConfigManager.FarmSpot s : farmSpotsCache) {
                if (s == null) continue;
                double distSq = squaredDistance(px, py, pz, s.x, s.y, s.z);
                if (distSq <= maxDistSq && distSq < nearestDistSq) {
                    nearest = s;
                    nearestDistSq = distSq;
                }
            }

            if (nearest == null) {
                activeFarmSpotName = "";
                activeFarmSpotCategory = "";
                return;
            }

            if (!nearest.name.equalsIgnoreCase(activeFarmSpotName)) {
                activeFarmSpotName = nearest.name;
                activeFarmSpotCategory = normalizeSpotCategory(nearest.category);

                if (client.player != null) {
                    client.player.displayClientMessage(
                        Component.literal("(i) '" + activeFarmSpotName + "' was assigned automatically, have a good session!"),
                        false
                    );
                }

                if (nearest.autoPresetEnabled) {
                    switch (activeFarmSpotCategory) {
                        case "mythic" -> applyMythicsPreset();
                        case "ingredient" -> applyIngredientsPreset();
                        case "gathering" -> applyGatheringPreset();
                    }
                }
            }
        }
    }

    private static double squaredDistance(int ax, int ay, int az, int bx, int by, int bz) {
        double dx = ax - bx;
        double dy = ay - by;
        double dz = az - bz;
        return dx * dx + dy * dy + dz * dz;
    }

    private static String normalizeSpotCategory(String category) {
        if (category == null) return "";
        String c = category.trim().toLowerCase(Locale.ROOT);
        if (c.startsWith("myth")) return "mythic";
        if (c.startsWith("ingred")) return "ingredient";
        if (c.startsWith("gather")) return "gathering";
        return c;
    }

    private static String getCurrentPresetSpotCategory() {
        if (activePresetType == 1) return "mythic";
        if (activePresetType == 2) return "ingredient";
        if (activePresetType == 3) return "gathering";
        return "";
    }

    private static String getSpotCategoryLabel(String category) {
        String c = normalizeSpotCategory(category);
        if ("mythic".equals(c)) return "Mythic";
        if ("ingredient".equals(c)) return "Ingredient";
        if ("gathering".equals(c)) return "Gathering";
        return "Spot";
    }

    private static String formatDurationShort(long seconds) {
        long s = Math.max(0L, seconds);
        long h = s / 3600;
        long m = (s % 3600) / 60;
        if (h > 0) return h + "h " + m + "m";
        return m + "m";
    }

    private static void sendSessionSnapshotToWebhook(Minecraft mc, String playerName) {
        if (mc == null || mc.player == null) {
            return;
        }

        String report = buildPlayerInventoryReportText(mc);
        double moneyMade = getIngredientMoneyMadeForDisplay();
        double incomePerHour = getIngredientIncomePerHourForDisplay();
        DISCORD_WEBHOOK.publishDigest(
            playerName,
            SESSION_TRACKER.getSessionTimer(),
            SESSION_TRACKER.getSessionKills(),
            getRealMythicsDropped(),
            SESSION_TRACKER.getMostFoundItemName(),
            SESSION_TRACKER.getMostFoundItemCount(),
            moneyMade,
            incomePerHour,
            mc.player.getBlockX(),
            mc.player.getBlockY(),
            mc.player.getBlockZ(),
            report
        );
    }

    public static void shutdown() {
        try {
            API_CLIENT.shutdown();
            MARKET_API_CLIENT.shutdown();
            PulseBridge.closeBus();
            
            logDebug("[MobKillerCalculator] All executors shut down successfully");
        } catch (Exception e) {
            System.err.println("[MobKillerCalculator] Error during shutdown: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
