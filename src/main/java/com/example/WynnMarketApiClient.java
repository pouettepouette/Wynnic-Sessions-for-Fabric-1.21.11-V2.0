package com.example;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class WynnMarketApiClient {
    private static final String API_BASE_URL = "https://wynnmarket.com/api";
    private static final long CACHE_DURATION_MS = 60000; // 60 seconds cache
    private static final long MIN_REQUEST_INTERVAL_MS = 2000; // 2 seconds between requests
    private static final int DEFAULT_STACK_SIZE = 64;
    private static final boolean DEBUG_LOGS = false;
    private static final String[] LAST_SALE_FIELDS = {
        "last_sale",
        "lastSale",
        "last_price",
        "lastPrice"
    };
    private static final Pattern LISTING_EVENT_PATTERN = Pattern.compile(
        "([0-9][0-9,]*)!\\[Image[^\\]]*:\\s*(E|EB|LE)\\]\\([^)]*\\)"
            + "|"
            + "([0-9][0-9,]*)\\s*([0-9]+[a-z]+)\\s+ago",
        Pattern.CASE_INSENSITIVE | Pattern.DOTALL
    );
    private static final Pattern AGE_TOKEN_PATTERN = Pattern.compile("(\\d+)([a-z]+)", Pattern.CASE_INSENSITIVE);
    
    private final Map<String, CachedPrice> priceCache = new HashMap<>();
    private final ExecutorService apiExecutor;
    private long lastRequestTime = 0;
    private boolean enabled = true;

    public WynnMarketApiClient() {
        this.apiExecutor = Executors.newFixedThreadPool(2, r -> {
            Thread t = new Thread(r, "wynnmarket-api");
            t.setDaemon(true);
            return t;
        });
    }

    private static void logDebug(String message) {
        if (DEBUG_LOGS) {
            System.out.println(message);
        }
    }

    private static class CachedPrice {
        final double price;
        final long timestamp;

        CachedPrice(double price, long timestamp) {
            this.price = price;
            this.timestamp = timestamp;
        }

        boolean isExpired() {
            return System.currentTimeMillis() - timestamp > CACHE_DURATION_MS;
        }
    }

    private static class ListingEntry {
        final double pricePerUnitEm;
        final int quantity;
        final String timeAgo;

        ListingEntry(double pricePerUnitEm, int quantity, String timeAgo) {
            this.pricePerUnitEm = pricePerUnitEm;
            this.quantity = quantity;
            this.timeAgo = timeAgo;
        }
    }

    public interface PriceResultHandler {
        void onSuccess(double price);
        void onError(String message);
    }

    public interface Avg30dResultHandler {
        void onSuccess(long avg30d);
        void onError(String message);
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
        if (!enabled) {
            priceCache.clear();
        }
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void fetchItemPrice(String itemName, PriceResultHandler handler) {
        if (!enabled) {
            handler.onError("Market prices disabled");
            return;
        }

        if (itemName == null || itemName.trim().isEmpty()) {
            handler.onError("Invalid item name");
            return;
        }

        String cleanName = cleanItemName(itemName);
        CachedPrice cached = priceCache.get(cleanName);
        if (cached != null && !cached.isExpired()) {
            handler.onSuccess(cached.price);
            return;
        }
        final long waitMs;
        final long requestStamp;
        synchronized (this) {
            long now = System.currentTimeMillis();
            long nextAllowed = lastRequestTime + MIN_REQUEST_INTERVAL_MS;
            waitMs = Math.max(0, nextAllowed - now);
            requestStamp = now + waitMs;
            lastRequestTime = requestStamp;
        }
        apiExecutor.execute(() -> {
            try {
                if (waitMs > 0) {
                    Thread.sleep(waitMs);
                }
                double price = fetchPriceFromApi(cleanName);
                priceCache.put(cleanName, new CachedPrice(price, requestStamp));
                handler.onSuccess(price);
            } catch (Exception e) {
                logDebug("[MobKillerCalculator] Failed to fetch price for " + cleanName + ": " + e.getMessage());
                handler.onError(e.getMessage());
            }
        });
    }

    private double fetchPriceFromApi(String itemName) throws Exception {
        String encodedName = URLEncoder.encode(itemName, StandardCharsets.UTF_8.toString());
        String urlString = API_BASE_URL + "/items?search=" + encodedName;
        String normalizedQuery = normalizeForComparison(itemName);

        URL url = new URL(urlString);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        
        try {
            connection.setRequestMethod("GET");
            connection.setRequestProperty("User-Agent", "MobFarmSessions-Mod/1.0");
            connection.setConnectTimeout(5000);
            connection.setReadTimeout(5000);

            int responseCode = connection.getResponseCode();
            if (responseCode != 200) {
                throw new Exception("API returned code " + responseCode);
            }

            BufferedReader reader = new BufferedReader(
                new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8)
            );
            StringBuilder response = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                response.append(line);
            }
            reader.close();

            String responseStr = response.toString();
            logDebug("[MobKillerCalculator] WynnMarket API response for '" + itemName + "': " + responseStr);
            JsonElement root = JsonParser.parseString(responseStr);
            if (!root.isJsonArray()) {
                throw new Exception("Unexpected market response format");
            }

            JsonArray items = root.getAsJsonArray();
            if (items.size() == 0) {
                throw new Exception("Item not found on market");
            }

            JsonObject selected = selectBestItem(items, normalizedQuery, true);
            if (selected == null) {
                throw new Exception("No matching item found");
            }

            PriceValue preferred = extractPreferredPrice(selected);
            if (preferred != null) {
                logDebug("[MobKillerCalculator] Found price (" + preferred.field + "): " + preferred.value);
                return preferred.value;
            }

            logDebug("[MobKillerCalculator] Could not find price (last sale or avg_30d) in response format");
            throw new Exception("No price data (last sale / avg_30d)");

        } finally {
            connection.disconnect();
        }
    }

    private String cleanItemName(String itemName) {
        if (itemName == null) {
            return "";
        }
        
        String original = itemName;
        String cleaned = itemName.replaceAll("§[0-9a-fk-or]", "");
        cleaned = cleaned.replace("[", " ").replace("]", " ");
        cleaned = cleaned.replaceAll("[✫★✦✧✪☆✩✯⭐🌟💫]", "");
        cleaned = cleaned.replaceAll("^x\\d+\\s+", ""); // "x24 Item" -> "Item"
        cleaned = cleaned.replaceAll("^\\+\\d+\\s+", ""); // "+24 Item" -> "Item"
        cleaned = cleaned.trim().replaceAll("\\s+", " ");


        if (!original.equals(cleaned)) {
            logDebug("[MobKillerCalculator] Cleaned item name: '" + original + "' -> '" + cleaned + "'");
        }
        
        return cleaned;
    }

    private boolean hasUsablePrice(JsonObject candidate) {
        return extractPreferredPrice(candidate) != null;
    }

    private PriceValue extractPreferredPrice(JsonObject candidate) {
        if (candidate == null) {
            return null;
        }
        for (String field : LAST_SALE_FIELDS) {
            if (!candidate.has(field) || candidate.get(field).isJsonNull()) {
                continue;
            }
            try {
                double value = candidate.get(field).getAsDouble();
                if (value > 0) {
                    return new PriceValue(field, value);
                }
            } catch (Exception ignored) {
            }
        }
        if (candidate.has("avg_30d") && !candidate.get("avg_30d").isJsonNull()) {
            try {
                double value = candidate.get("avg_30d").getAsDouble();
                if (value > 0) {
                    return new PriceValue("avg_30d", value);
                }
            } catch (Exception ignored) {
            }
        }

        return null;
    }

    private static class PriceValue {
        final String field;
        final double value;

        PriceValue(String field, double value) {
            this.field = field;
            this.value = value;
        }
    }

    private String normalizeForComparison(String value) {
        if (value == null) {
            return "";
        }
        return value.toLowerCase().replaceAll("[^a-z0-9 ]", "").replaceAll("\\s+", " ").trim();
    }

    private JsonObject selectBestItem(JsonArray items, String normalizedQuery, boolean requireUsablePrice) {
        if (items == null || items.isEmpty()) {
            return null;
        }

        JsonObject best = null;
        int bestScore = Integer.MIN_VALUE;
        for (JsonElement element : items) {
            if (!element.isJsonObject()) {
                continue;
            }
            JsonObject candidate = element.getAsJsonObject();
            if (!candidate.has("name") || candidate.get("name").isJsonNull()) {
                continue;
            }
            if (requireUsablePrice && !hasUsablePrice(candidate)) {
                continue;
            }

            String normalizedCandidate = normalizeForComparison(candidate.get("name").getAsString());
            int score = matchScore(normalizedQuery, normalizedCandidate);
            if (score > bestScore) {
                bestScore = score;
                best = candidate;
            }
        }

        if (best != null) {
            return best;
        }
        if (requireUsablePrice) {
            return selectBestItem(items, normalizedQuery, false);
        }
        return null;
    }

    private int matchScore(String query, String candidate) {
        if (query == null || candidate == null || candidate.isEmpty()) {
            return Integer.MIN_VALUE / 2;
        }

        String queryTier = extractRomanTierToken(query);
        String candidateTier = extractRomanTierToken(candidate);
        if (!queryTier.isEmpty()) {
            if (!queryTier.equals(candidateTier)) {
                return Integer.MIN_VALUE / 4;
            }
        }

        if (candidate.equals(query)) {
            return 12000;
        }
        if (candidate.startsWith(query)) {
            return 9000 - Math.abs(candidate.length() - query.length());
        }
        if (candidate.contains(query)) {
            return 5000 - Math.abs(candidate.length() - query.length());
        }

        int overlap = 0;
        String[] queryTokens = query.split(" ");
        for (String token : queryTokens) {
            if (!token.isEmpty() && candidate.contains(token)) {
                overlap++;
            }
        }
        return overlap * 100 - Math.abs(candidate.length() - query.length());
    }

    private String extractRomanTierToken(String normalizedText) {
        if (normalizedText == null || normalizedText.isEmpty()) {
            return "";
        }
        String[] tokens = normalizedText.trim().split("\\s+");
        for (String token : tokens) {
            if ("i".equals(token) || "ii".equals(token) || "iii".equals(token)) {
                return token;
            }
        }
        return "";
    }

    public void fetchItemPriceByMode(String itemName, int priceMode, PriceResultHandler handler) {
        if (!enabled) { handler.onError("Market prices disabled"); return; }
        if (itemName == null || itemName.trim().isEmpty()) { handler.onError("Invalid item name"); return; }

        String cleanName = cleanItemName(itemName);
        String cacheKey = cleanName + "|mode" + priceMode;

        CachedPrice cached = priceCache.get(cacheKey);
        if (cached != null && !cached.isExpired()) { handler.onSuccess(cached.price); return; }

        final long waitMs;
        final long requestStamp;
        synchronized (this) {
            long now = System.currentTimeMillis();
            long nextAllowed = lastRequestTime + MIN_REQUEST_INTERVAL_MS;
            waitMs = Math.max(0, nextAllowed - now);
            requestStamp = now + waitMs;
            lastRequestTime = requestStamp;
        }

        apiExecutor.execute(() -> {
            try {
                if (waitMs > 0) {
                    Thread.sleep(waitMs);
                }
                double price = fetchPriceFromPageApi(cleanName, priceMode);
                priceCache.put(cacheKey, new CachedPrice(price, requestStamp));
                handler.onSuccess(price);
            } catch (Exception e) {
                logDebug("[MobKillerCalculator] Failed to fetch mode-" + priceMode + " price for " + cleanName + ": " + e.getMessage());
                handler.onError(e.getMessage());
            }
        });
    }

    public void fetchItemAvg30d(String itemName, Avg30dResultHandler handler) {
        if (!enabled) { handler.onError("Market prices disabled"); return; }
        if (itemName == null || itemName.trim().isEmpty()) { handler.onError("Invalid item name"); return; }

        apiExecutor.execute(() -> {
            try {
                long avg30d = fetchAvg30dFromApi(itemName);
                handler.onSuccess(avg30d);
            } catch (Exception e) {
                logDebug("[MobKillerCalculator] Failed to fetch avg30d for " + itemName + ": " + e.getMessage());
                handler.onError(e.getMessage());
            }
        });
    }

    private long fetchAvg30dFromApi(String itemName) throws Exception {
        String encodedName = URLEncoder.encode(itemName, StandardCharsets.UTF_8.toString());
        String urlString = API_BASE_URL + "/items/page?search=" + encodedName;
        
        String response = fetchText(urlString, 10000);
        JsonObject root = JsonParser.parseString(response).getAsJsonObject();
        JsonArray items = root.has("items") ? root.getAsJsonArray("items") : null;
        if (items == null || items.isEmpty()) throw new Exception("No items found");

        JsonObject best = selectBestItem(items, normalizeForComparison(itemName), false);
        if (best == null) throw new Exception("No matching item found");

        if (best.has("avg_30d") && !best.get("avg_30d").isJsonNull()) {
            return Math.round(best.get("avg_30d").getAsDouble());
        }
        return 0L;
    }

    private double fetchPriceFromPageApi(String itemName, int priceMode) throws Exception {
        String encodedName = URLEncoder.encode(itemName, StandardCharsets.UTF_8.toString());
        String urlString = API_BASE_URL + "/items/page?search=" + encodedName;
        String normalizedQuery = normalizeForComparison(itemName);

        URL url = new URL(urlString);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        try {
            connection.setRequestMethod("GET");
            connection.setRequestProperty("User-Agent", "MobFarmSessions-Mod/1.0");
            connection.setConnectTimeout(5000);
            connection.setReadTimeout(5000);

            int responseCode = connection.getResponseCode();
            if (responseCode != 200) throw new Exception("API returned code " + responseCode);

            BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8));
            StringBuilder response = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) response.append(line);
            reader.close();

            JsonObject root = JsonParser.parseString(response.toString()).getAsJsonObject();
            JsonArray items = root.has("items") ? root.getAsJsonArray("items") : null;
            if (items == null || items.isEmpty()) throw new Exception("Item not found on market");

            JsonObject selected = selectBestItem(items, normalizedQuery, false);
            if (selected == null) {
                throw new Exception("No matching item found");
            }
            double fallbackLowest = -1;
            if (selected.has("listingSummary") && selected.get("listingSummary").isJsonObject()) {
                JsonObject summary = selected.getAsJsonObject("listingSummary");
                if (summary.has("min") && !summary.get("min").isJsonNull()) {
                    try {
                        double v = summary.get("min").getAsDouble();
                        if (v > 0) fallbackLowest = v;
                    } catch (Exception ignored) {}
                }
            }
            double fallbackAverage = -1;
            if (selected.has("avg_30d") && !selected.get("avg_30d").isJsonNull()) {
                try {
                    double v = selected.get("avg_30d").getAsDouble();
                    if (v > 0) fallbackAverage = v;
                } catch (Exception ignored) {}
            }
            if (fallbackAverage <= 0 && fallbackLowest > 0) {
                fallbackAverage = fallbackLowest;
            }

            String exactName = selected.has("name") && !selected.get("name").isJsonNull()
                ? selected.get("name").getAsString()
                : itemName;

            String encodedExact = URLEncoder.encode(exactName, StandardCharsets.UTF_8.toString()).replace("+", "%20");
            String mirrorUrl = "https://r.jina.ai/http://wynnmarket.com/items/" + encodedExact;
            try {
                String page = fetchText(mirrorUrl, 12000);

                List<ListingEntry> listings = parseRecentListings(page);
                if (listings.isEmpty()) {
                    throw new Exception("No recent listings data");
                }

                List<ListingEntry> considered = filterListingsByWindow(listings, 7.0);
                if (considered.isEmpty()) {
                    considered = filterListingsByWindow(listings, 30.0);
                }
                if (considered.isEmpty()) {
                    throw new Exception("No listings in last month");
                }

                double lowest = findLowest(considered);
                if (lowest <= 0) {
                    throw new Exception("No valid listing price");
                }
                if (priceMode == 1) {
                    return lowest;
                }

                double avg = computeAverageWithCap(considered, lowest);
                return avg > 0 ? avg : lowest;
            } catch (Exception mirrorError) {
                if (priceMode == 1 && fallbackLowest > 0) {
                    return fallbackLowest;
                }
                if (priceMode == 0 && fallbackAverage > 0) {
                    return fallbackAverage;
                }
                if (fallbackLowest > 0) {
                    return fallbackLowest;
                }
                throw mirrorError;
            }
        } finally {
            connection.disconnect();
        }
    }

    private List<ListingEntry> parseRecentListings(String page) {
        List<ListingEntry> listings = new ArrayList<>();
        int start = page.indexOf("Recently Listed");
        int end = page.indexOf("Data provided by", Math.max(0, start));
        String section = (start >= 0)
            ? page.substring(start, end > start ? end : page.length())
            : page;

        Matcher events = LISTING_EVENT_PATTERN.matcher(section);
        double pendingPrice = 0;
        while (events.find()) {
            if (events.group(1) != null && events.group(2) != null) {
                int amount = parseIntSafe(events.group(1));
                String cur = events.group(2).toUpperCase();
                if ("LE".equals(cur)) pendingPrice += amount * 4096.0;
                else if ("EB".equals(cur)) pendingPrice += amount * 64.0;
                else pendingPrice += amount;
                continue;
            }

            if (events.group(3) != null && events.group(4) != null && pendingPrice > 0) {
                int qty = parseIntSafe(events.group(3));
                String timeAgo = events.group(4) + " ago";
                listings.add(new ListingEntry(pendingPrice, Math.max(1, qty), timeAgo));
                pendingPrice = 0;
                if (listings.size() >= 100) break;
            }
        }
        return listings;
    }

    private List<ListingEntry> filterListingsByWindow(List<ListingEntry> all, double maxDays) {
        List<ListingEntry> out = new ArrayList<>();
        for (ListingEntry e : all) {
            double ageDays = ageInDays(e.timeAgo);
            if (ageDays >= 0 && ageDays <= maxDays && e.pricePerUnitEm > 0) {
                out.add(e);
            }
        }
        return out;
    }

    private double findLowest(List<ListingEntry> listings) {
        double min = Double.MAX_VALUE;
        for (ListingEntry e : listings) {
            if (e.pricePerUnitEm > 0 && e.pricePerUnitEm < min) {
                min = e.pricePerUnitEm;
            }
        }
        return min == Double.MAX_VALUE ? -1 : min;
    }

    private double computeAverageWithCap(List<ListingEntry> listings, double lowest) {
        double cap = lowest * 3.0;  // 300% amplitude instead of 500%
        double sum = 0;
        int count = 0;
        for (ListingEntry e : listings) {
            if (e.pricePerUnitEm > 0 && e.pricePerUnitEm <= cap) {
                sum += e.pricePerUnitEm;
                count++;
            }
        }
        return count > 0 ? (sum / count) : -1;
    }

    private int parseIntSafe(String s) {
        try {
            return Integer.parseInt(s.replace(",", ""));
        } catch (Exception e) {
            return -1;
        }
    }

    private double ageInDays(String timeAgo) {
        Matcher m = AGE_TOKEN_PATTERN.matcher(timeAgo == null ? "" : timeAgo.toLowerCase());
        if (!m.find()) return -1;
        int value = parseIntSafe(m.group(1));
        if (value < 0) return -1;
        String unit = m.group(2);
        if (unit.startsWith("m") && !unit.startsWith("mo")) return value / 1440.0;
        if (unit.startsWith("h")) return value / 24.0;
        if (unit.startsWith("d")) return value;
        if (unit.startsWith("w")) return value * 7.0;
        if (unit.startsWith("mo")) return value * 30.0;
        if (unit.startsWith("y")) return value * 365.0;
        return -1;
    }

    private String fetchText(String urlString, int timeoutMs) throws Exception {
        URL url = new URL(urlString);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        try {
            connection.setRequestMethod("GET");
            connection.setRequestProperty("User-Agent", "MobFarmSessions-Mod/1.0");
            connection.setConnectTimeout(6000);
            connection.setReadTimeout(timeoutMs);
            int responseCode = connection.getResponseCode();
            if (responseCode != 200) {
                throw new Exception("Mirror returned code " + responseCode);
            }

            BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append('\n');
            }
            reader.close();
            return sb.toString();
        } finally {
            connection.disconnect();
        }
    }

    public void clearCache() {
        priceCache.clear();
    }

    public int getCacheSize() {
        return priceCache.size();
    }

    public void shutdown() {
        if (!apiExecutor.isShutdown()) {
            apiExecutor.shutdown();
            try {
                if (!apiExecutor.awaitTermination(5, java.util.concurrent.TimeUnit.SECONDS)) {
                    apiExecutor.shutdownNow();
                }
            } catch (InterruptedException e) {
                apiExecutor.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
    }
}
