package com.example;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class SessionTracker {
    private static final long WEBHOOK_INTERVAL_SECONDS = 300; // 5 minutes
    private static final long INCOME_WINDOW_SECONDS = 60;
    private static final long INCOME_MIN_SECONDS = 20;
    private static final double INCOME_EMA_ALPHA = 0.20;
    private static final double MIN_WINDOW_WEIGHT = 0.15;
    private static final double MAX_WINDOW_WEIGHT = 0.60;
    private final Object stateLock = new Object();

    private long sessionBaselineKills = -1;
    private long currentTotalKills = 0;
    private long sessionKills = 0;
    private long sessionStartTimeMs = 0;
    private long localSessionKills = 0;
    private boolean usingLocalKillTracking = false;
    private boolean localKillDetectionValidated = false;
    private boolean sessionPaused = false;
    private long sessionPauseStartedMs = 0;
    private long accumulatedPausedDurationMs = 0;
    private long pauseBaselineKills = 0;
    private long webhookIntervalsSent = 0;
    private long lastSessionKills = 0;
    private long lastSessionTotalKills = 0;
    private long lastSessionDurationSeconds = 0;
    private double lastSessionGains = -1.0;
    private double lastSessionIncomePerHour = -1.0;
    private boolean hasLastSessionSnapshot = false;
    private String trackedPlayerName = "";
    private Map<String, Integer> sessionBaselineItems = new ConcurrentHashMap<>();
    private Map<String, Integer> pauseBaselineItems = new ConcurrentHashMap<>();
    private String mostFoundItemName = "";
    private int mostFoundItemCount = 0;
    private int sessionTotalItemsDropped = 0;
    private boolean mostFoundIsIncrease = true;
    private double mostFoundItemPrice = -1.0; // -1 = not fetched, 0 = not tradable
    private boolean manualPriceEnabled = false;
    private double manualPriceValue = 0.0;
    private String priceStatus = "N/A";
    private long incomeWindowStartTimeMs = 0;
    private double incomeWindowStartValue = 0.0;
    private double smoothedWindowRatePerHour = -1.0;
    private long processedIncomeWindows = 0;

    public void resetForPlayer(String playerName) {
        synchronized (stateLock) {
            trackedPlayerName = playerName;
            sessionBaselineKills = -1;
            currentTotalKills = 0;
            sessionKills = 0;
            sessionStartTimeMs = 0;
            localSessionKills = 0;
            usingLocalKillTracking = false;
            localKillDetectionValidated = false;
            sessionPaused = false;
            sessionPauseStartedMs = 0;
            accumulatedPausedDurationMs = 0;
            pauseBaselineKills = 0;
            webhookIntervalsSent = 0;
            lastSessionKills = 0;
            lastSessionTotalKills = 0;
            lastSessionDurationSeconds = 0;
            lastSessionGains = -1.0;
            lastSessionIncomePerHour = -1.0;
            hasLastSessionSnapshot = false;
            sessionBaselineItems.clear();
            pauseBaselineItems.clear();
            mostFoundItemName = "";
            mostFoundItemCount = 0;
            sessionTotalItemsDropped = 0;
            mostFoundIsIncrease = true;
            mostFoundItemPrice = -1.0;
            priceStatus = "N/A";
            incomeWindowStartTimeMs = 0;
            incomeWindowStartValue = 0.0;
            smoothedWindowRatePerHour = -1.0;
            processedIncomeWindows = 0;
        }
    }

    public boolean ensurePlayer(String playerName) {
        if (!playerName.equals(trackedPlayerName)) {
            resetForPlayer(playerName);
            return true;
        }
        return false;
    }

    public void startSession() {
        synchronized (stateLock) {
            sessionStartTimeMs = System.currentTimeMillis();
            sessionKills = 0;
            sessionBaselineKills = currentTotalKills > 0 ? currentTotalKills : -1;
            localSessionKills = 0;
            usingLocalKillTracking = false;
            localKillDetectionValidated = false;
            sessionPaused = false;
            sessionPauseStartedMs = 0;
            accumulatedPausedDurationMs = 0;
            pauseBaselineKills = 0;
            webhookIntervalsSent = 0;
            sessionBaselineItems.clear();
            pauseBaselineItems.clear();
            mostFoundItemName = "";
            mostFoundItemCount = 0;
            sessionTotalItemsDropped = 0;
            mostFoundIsIncrease = true;
            mostFoundItemPrice = -1.0;
            priceStatus = "N/A";
            incomeWindowStartTimeMs = sessionStartTimeMs;
            incomeWindowStartValue = Math.max(0.0, getTotalFarmValue());
            smoothedWindowRatePerHour = -1.0;
            processedIncomeWindows = 0;
        }
    }

    public void stopSession() {
        synchronized (stateLock) {
            if (isSessionRunning()) {
                lastSessionKills = getSessionKills();
                lastSessionTotalKills = currentTotalKills;
                lastSessionDurationSeconds = getElapsedSeconds();
                lastSessionGains = getTotalFarmValue();
                lastSessionIncomePerHour = getIncomePerHour();
                hasLastSessionSnapshot = true;
            }

            sessionBaselineKills = -1;
            currentTotalKills = 0;
            sessionKills = 0;
            sessionStartTimeMs = 0;
            localSessionKills = 0;
            usingLocalKillTracking = false;
            localKillDetectionValidated = false;
            sessionPaused = false;
            sessionPauseStartedMs = 0;
            accumulatedPausedDurationMs = 0;
            pauseBaselineKills = 0;
            webhookIntervalsSent = 0;
            sessionBaselineItems.clear();
            pauseBaselineItems.clear();
            mostFoundItemPrice = -1.0;
            priceStatus = "N/A";
            incomeWindowStartTimeMs = 0;
            incomeWindowStartValue = 0.0;
            smoothedWindowRatePerHour = -1.0;
            processedIncomeWindows = 0;
        }
    }

    public boolean updateFromApi(long totalKills) {
        synchronized (stateLock) {
            long previousTotalKills = currentTotalKills;
            currentTotalKills = totalKills;
            if (sessionBaselineKills < 0) {
                sessionBaselineKills = totalKills;
                sessionKills = 0;
                localSessionKills = 0;
                if (!usingLocalKillTracking) {
                    sessionKills = 0;
                }
                return true;
            }

            if (sessionPaused) {
                return false;
            }

            long apiSessionKills = Math.max(0, totalKills - sessionBaselineKills);
            sessionKills = apiSessionKills;
            if (totalKills != previousTotalKills) {
                localSessionKills = apiSessionKills;
                if (localSessionKills > 0) {
                    localKillDetectionValidated = true;
                    usingLocalKillTracking = true;
                }
            }
            return false;
        }
    }

    public void pauseSession(Map<String, Integer> currentItems) {
        synchronized (stateLock) {
            if (!isSessionRunning() || sessionPaused) {
                return;
            }

            sessionPaused = true;
            sessionPauseStartedMs = System.currentTimeMillis();
            pauseBaselineKills = currentTotalKills;
            pauseBaselineItems.clear();
            if (currentItems != null) {
                pauseBaselineItems.putAll(currentItems);
            }
        }
    }

    public void resumeSession(Map<String, Integer> currentItems) {
        synchronized (stateLock) {
            if (!isSessionRunning() || !sessionPaused) {
                return;
            }

            long now = System.currentTimeMillis();
            accumulatedPausedDurationMs += Math.max(0, now - sessionPauseStartedMs);

            long pausedKillDelta = Math.max(0, currentTotalKills - pauseBaselineKills);
            if (sessionBaselineKills >= 0) {
                sessionBaselineKills += pausedKillDelta;
            }
            if (!usingLocalKillTracking) {
                sessionKills = Math.max(0, currentTotalKills - sessionBaselineKills);
            }

            if (currentItems != null && !currentItems.isEmpty()) {
                for (Map.Entry<String, Integer> entry : currentItems.entrySet()) {
                    String itemName = entry.getKey();
                    int resumeCount = entry.getValue();
                    int pauseCount = pauseBaselineItems.getOrDefault(itemName, 0);
                    int pausedGain = resumeCount - pauseCount;
                    if (pausedGain > 0) {
                        int baseline = sessionBaselineItems.getOrDefault(itemName, 0);
                        sessionBaselineItems.put(itemName, baseline + pausedGain);
                    }
                }
            }

            sessionPaused = false;
            sessionPauseStartedMs = 0;
            pauseBaselineKills = 0;
            pauseBaselineItems.clear();
            incomeWindowStartTimeMs = now;
            incomeWindowStartValue = Math.max(0.0, getTotalFarmValue());
            smoothedWindowRatePerHour = -1.0;
            processedIncomeWindows = 0;
        }
    }

    public boolean isSessionPaused() {
        return isSessionRunning() && sessionPaused;
    }

    public long getCurrentTotalKills() {
        if (!isSessionRunning() && hasLastSessionSnapshot) {
            return lastSessionTotalKills;
        }
        return currentTotalKills;
    }

    public int getSessionKills() {
        if (!isSessionRunning() && hasLastSessionSnapshot) {
            return (int) lastSessionKills;
        }
        if (usingLocalKillTracking && localKillDetectionValidated) {
            return (int) localSessionKills;
        }
        return (int) sessionKills;
    }

    public int getApiSessionKills() {
        if (!isSessionRunning() && hasLastSessionSnapshot) {
            return (int) lastSessionKills;
        }
        return (int) sessionKills;
    }

    public int getLocalSessionKills() {
        if (!isSessionRunning() && hasLastSessionSnapshot) {
            return (int) lastSessionKills;
        }
        return (int) localSessionKills;
    }

    public void setLocalSessionKills(long count) {
        if (isSessionRunning() && !isSessionPaused()) {
            localSessionKills = Math.max(count, 0);
            if (localSessionKills > 0) {
                localKillDetectionValidated = true;
                usingLocalKillTracking = true;
            }
        }
    }

    public String getSessionTimer() {
        if (sessionStartTimeMs == 0) {
            if (hasLastSessionSnapshot) {
                long minutes = lastSessionDurationSeconds / 60;
                long seconds = lastSessionDurationSeconds % 60;
                return String.format("%02d:%02d", minutes, seconds);
            }
            return "00:00";
        }

        long elapsedSeconds = getElapsedSeconds();
        long minutes = elapsedSeconds / 60;
        long seconds = elapsedSeconds % 60;
        return String.format("%02d:%02d", minutes, seconds);
    }

    public boolean hasTotalKills() {
        return isSessionRunning() ? currentTotalKills > 0 : hasLastSessionSnapshot;
    }

    public boolean hasInitializedBaseline() {
        return sessionBaselineKills >= 0 || hasLastSessionSnapshot;
    }

    public boolean isSessionRunning() {
        return sessionStartTimeMs > 0;
    }

    public long getElapsedSeconds() {
        if (sessionStartTimeMs == 0) {
            return 0;
        }

        long now = System.currentTimeMillis();
        long pausedMs = accumulatedPausedDurationMs;
        if (sessionPaused && sessionPauseStartedMs > 0) {
            pausedMs += Math.max(0, now - sessionPauseStartedMs);
        }

        long elapsedMs = Math.max(0, (now - sessionStartTimeMs) - pausedMs);
        return elapsedMs / 1000;
    }

    public boolean shouldSendFiveMinuteWebhook() {
        if (!isSessionRunning()) {
            return false;
        }

        long completedIntervals = getElapsedSeconds() / WEBHOOK_INTERVAL_SECONDS;
        if (completedIntervals > webhookIntervalsSent) {
            webhookIntervalsSent = completedIntervals;
            return true;
        }
        return false;
    }

    public String getTrackedPlayerName() {
        return trackedPlayerName;
    }

    public void setBaselineItems(Map<String, Integer> items) {
        sessionBaselineItems.clear();
        if (items != null) {
            sessionBaselineItems.putAll(items);
        }
    }

    public void updateMostFoundItem(Map<String, Integer> currentItems) {
        if (!isSessionRunning() || currentItems == null) {
            return;
        }

        String bestGainItem = "";
        int maxGainCount = 0;
        int totalDropped = 0;

        for (Map.Entry<String, Integer> entry : currentItems.entrySet()) {
            String itemName = entry.getKey();
            int currentCount = entry.getValue();
            int baselineCount = sessionBaselineItems.getOrDefault(itemName, 0);
            int sessionGain = currentCount - baselineCount;

            if (sessionGain > 0) {
                totalDropped += sessionGain;
            }

            if (sessionGain > maxGainCount) {
                maxGainCount = sessionGain;
                bestGainItem = itemName;
            }
        }

        sessionTotalItemsDropped = totalDropped;

        if (!bestGainItem.isEmpty() && maxGainCount > 0) {
            mostFoundItemName = bestGainItem;
            mostFoundItemCount = maxGainCount;
            mostFoundIsIncrease = true;
        } else {
            mostFoundItemName = "";
            mostFoundItemCount = 0;
            mostFoundIsIncrease = true;
        }
    }

    public String getMostFoundItemName() {
        return mostFoundItemName;
    }

    public int getMostFoundItemCount() {
        return mostFoundItemCount;
    }

    public int getSessionTotalItemsDropped() {
        return sessionTotalItemsDropped;
    }

    public Map<String, Integer> computeSessionGains(Map<String, Integer> currentItems) {
        if (!isSessionRunning() || currentItems == null) {
            return new HashMap<>();
        }
        Map<String, Integer> gains = new HashMap<>();
        for (Map.Entry<String, Integer> entry : currentItems.entrySet()) {
            String itemName = entry.getKey();
            int currentCount = entry.getValue();
            int baselineCount = sessionBaselineItems.getOrDefault(itemName, 0);
            int sessionGain = currentCount - baselineCount;
            if (sessionGain > 0) {
                gains.put(itemName, sessionGain);
            }
        }
        return gains;
    }

    public boolean isMostFoundIncreaseCount() {
        return mostFoundIsIncrease;
    }

    public void setMostFoundItemPrice(double price) {
        this.mostFoundItemPrice = price;
        if (price < 0) {
            priceStatus = "N/A";
        } else if (price == 0) {
            priceStatus = "Not tradable";
        } else {
            priceStatus = "OK";
        }
    }

    public void setMostFoundItemPriceUnavailable(String status) {
        this.mostFoundItemPrice = -1.0;
        if (status == null || status.trim().isEmpty()) {
            this.priceStatus = "Market unavailable";
        } else {
            this.priceStatus = status;
        }
    }

    public double getMostFoundItemPrice() {
        return mostFoundItemPrice;
    }

    public void setManualPriceEnabled(boolean enabled) {
        this.manualPriceEnabled = enabled;
    }

    public void setManualPriceValue(double price) {
        this.manualPriceValue = Math.max(0.0, price);
    }

    public String getPriceStatus() {
        return priceStatus;
    }

    public double getTotalFarmValue() {
        if (!isSessionRunning() && hasLastSessionSnapshot) {
            return lastSessionGains;
        }

        if (!isSessionRunning()) {
            return -1.0;
        }

        if (mostFoundItemCount <= 0) {
            return 0.0;
        }

        double effectivePrice = getEffectiveItemPrice();
        if (effectivePrice <= 0) {
            return -1.0;
        }
        return effectivePrice * mostFoundItemCount;
    }

    private double getEffectiveItemPrice() {
        if (manualPriceEnabled && manualPriceValue > 0.0) {
            return manualPriceValue;
        }
        return mostFoundItemPrice;
    }

    public double getIncomePerHour() {
        if (!isSessionRunning() && hasLastSessionSnapshot) {
            return lastSessionIncomePerHour;
        }

        double totalValue = getTotalFarmValue();
        if (totalValue < 0 || !isSessionRunning()) {
            return -1.0;
        }

        long elapsedSeconds = getElapsedSeconds();
        if (elapsedSeconds < INCOME_MIN_SECONDS) {
            return -1.0;
        }

        updateIncomeWindowRates(totalValue);
        double hours = elapsedSeconds / 3600.0;
        double sessionRatePerHour = totalValue / hours;

        if (processedIncomeWindows <= 0 || smoothedWindowRatePerHour < 0) {
            return sessionRatePerHour;
        }
        double elapsedFactor = Math.min(1.0, elapsedSeconds / 1800.0); // 30 min to full confidence
        double windowWeight = MIN_WINDOW_WEIGHT + (MAX_WINDOW_WEIGHT - MIN_WINDOW_WEIGHT) * elapsedFactor;
        double sessionWeight = 1.0 - windowWeight;

        double blendedRate = (sessionRatePerHour * sessionWeight) + (smoothedWindowRatePerHour * windowWeight);
        return Math.max(0.0, blendedRate);
    }

    private void updateIncomeWindowRates(double currentTotalValue) {
        if (incomeWindowStartTimeMs <= 0) {
            incomeWindowStartTimeMs = System.currentTimeMillis();
            incomeWindowStartValue = currentTotalValue;
            return;
        }

        long now = System.currentTimeMillis();
        long windowDurationMs = INCOME_WINDOW_SECONDS * 1000;

        long elapsedMs = now - incomeWindowStartTimeMs;
        if (elapsedMs < windowDurationMs) {
            return;
        }

        int completeWindows = (int) (elapsedMs / windowDurationMs);
        if (completeWindows <= 0) {
            return;
        }

        double totalDelta = currentTotalValue - incomeWindowStartValue;
        double deltaPerWindow = totalDelta / completeWindows;

        for (int i = 0; i < completeWindows; i++) {
            double cappedDelta = Math.max(deltaPerWindow, -Math.max(currentTotalValue * 0.20, 500.0));
            double windowHours = INCOME_WINDOW_SECONDS / 3600.0;
            double windowRatePerHour = cappedDelta / windowHours;

            if (smoothedWindowRatePerHour < 0) {
                smoothedWindowRatePerHour = windowRatePerHour;
            } else {
                smoothedWindowRatePerHour = (INCOME_EMA_ALPHA * windowRatePerHour)
                    + ((1.0 - INCOME_EMA_ALPHA) * smoothedWindowRatePerHour);
            }
            processedIncomeWindows++;
        }

        incomeWindowStartTimeMs += (long) completeWindows * windowDurationMs;
        incomeWindowStartValue = currentTotalValue;
    }
}
