package com.example;

import java.util.HashMap;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

public class EntityKillTracker {
    private static final String[] KILL_INDICATORS = {"combat xp", "guild xp", "shared"};
    private static final long SEEN_TTL_MS = 300_000; // 5 minutes, similar to MKTv3
    private static final long RECENT_DISAPPEAR_MS = 1200;
    private static final double MAX_TRACK_DISTANCE_SQ = 45.0 * 45.0;
    private static final long WYNN_XP_SIGNAL_WINDOW_MS = 1500;

    private final Map<UUID, Long> seenIndicators = new HashMap<>();
    private final Map<UUID, TrackedMob> trackedMobs = new HashMap<>();
    private long localSessionKills = 0;
    private int tickCounter = 0;
    private long lastWynnXpSignalMs = 0;

    private static class TrackedMob {
        long lastSeenMs;
        double lastHealth;
        double lastDistanceSq;

        TrackedMob(long lastSeenMs, double lastHealth, double lastDistanceSq) {
            this.lastSeenMs = lastSeenMs;
            this.lastHealth = lastHealth;
            this.lastDistanceSq = lastDistanceSq;
        }
    }

    public void resetSession() {
        seenIndicators.clear();
        trackedMobs.clear();
        localSessionKills = 0;
        tickCounter = 0;
        lastWynnXpSignalMs = 0;
    }

    public long getLocalKillCount() {
        return localSessionKills;
    }

    public void setLocalKillCount(long count) {
        localSessionKills = Math.max(0, count);
    }

    public long scan(Minecraft client) {
        if (client == null || client.level == null || client.player == null) {
            return localSessionKills;
        }
        
        long now = System.currentTimeMillis();
        cleanupExpired(now);

        int textDisplayCount = 0;
        List<String> textSamples = new ArrayList<>();
        Set<UUID> seenLivingThisTick = new HashSet<>();
        for (Entity entity : client.level.entitiesForRendering()) {
            if (entity == null || entity.isRemoved()) {
                continue;
            }

            String entityTypeId = entity.getType().toString().toLowerCase(Locale.ROOT);
            boolean isTextDisplay = entityTypeId.contains("text_display") || entityTypeId.contains("text display");
            if (isTextDisplay) {
                textDisplayCount++;
            }

            String text = null;
            if (isTextDisplay) {
                text = extractTextDisplayText(entity);
            }
            if (text == null || text.isEmpty()) {
                text = entity.getName().getString();
            }
            
            if (text == null || text.isEmpty()) {
                continue;
            }

            if (isTextDisplay && textSamples.size() < 3) {
                textSamples.add(text);
            }

            String lower = normalizeText(text);

            if (isWynnXpSignal(lower)) {
                lastWynnXpSignalMs = now;
            }
            
            if (!containsKillIndicator(lower)) {
                continue;
            }

            UUID id = entity.getUUID();
            if (id == null || seenIndicators.containsKey(id)) {
                continue;
            }

            seenIndicators.put(id, now);
            localSessionKills++;
            System.out.println("[MobKillerCalculator] ✓ KILL COUNTED: " + text + " | Total session kills: " + localSessionKills);
            trackedMobs.remove(id);
        }
        for (Entity entity : client.level.entitiesForRendering()) {
            if (!(entity instanceof LivingEntity living)) {
                continue;
            }
            if (entity == client.player || entity.isRemoved()) {
                continue;
            }

            UUID id = entity.getUUID();
            if (id == null) {
                continue;
            }

            double distSq = entity.distanceToSqr(client.player);
            if (distSq > MAX_TRACK_DISTANCE_SQ) {
                continue;
            }

            seenLivingThisTick.add(id);
            TrackedMob tracked = trackedMobs.get(id);
            if (tracked == null) {
                tracked = new TrackedMob(now, living.getHealth(), distSq);
                trackedMobs.put(id, tracked);
            } else {
                tracked.lastSeenMs = now;
                tracked.lastHealth = living.getHealth();
                tracked.lastDistanceSq = distSq;
            }
            if (living.isDeadOrDying() && !seenIndicators.containsKey(id)) {
                seenIndicators.put(id, now);
                localSessionKills++;
                System.out.println("[MobKillerCalculator] ✓ KILL COUNTED (dead): " + entity.getName().getString()
                        + " | Total session kills: " + localSessionKills);
            }
        }
        Iterator<Map.Entry<UUID, TrackedMob>> iterator = trackedMobs.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, TrackedMob> entry = iterator.next();
            UUID id = entry.getKey();
            TrackedMob tracked = entry.getValue();

            if (seenLivingThisTick.contains(id)) {
                continue;
            }

            long unseenMs = now - tracked.lastSeenMs;
            if (unseenMs <= RECENT_DISAPPEAR_MS
                    && tracked.lastDistanceSq <= MAX_TRACK_DISTANCE_SQ
                    && tracked.lastHealth > 0.0
                    && hasRecentWynnXpSignal(now)
                    && !seenIndicators.containsKey(id)) {
                seenIndicators.put(id, now);
                localSessionKills++;
                System.out.println("[MobKillerCalculator] ✓ KILL COUNTED (disappeared) | Total session kills: "
                        + localSessionKills);
            }

            if (unseenMs > 3000) {
                iterator.remove();
            }
        }
        tickCounter++;
        if (tickCounter >= 20) {
            tickCounter = 0;
            System.out.println("[MobKillerCalculator] Scan: TextDisplays=" + textDisplayCount + ", localKills=" + localSessionKills);
            if (!textSamples.isEmpty()) {
                System.out.println("[MobKillerCalculator] Text samples: " + textSamples);
            }
        }

        return localSessionKills;
    }

    private void cleanupExpired(long now) {
        seenIndicators.entrySet().removeIf(entry -> now - entry.getValue() > SEEN_TTL_MS);
    }

    private boolean containsKillIndicator(String lowerName) {
        if ((lowerName.contains("combat") && lowerName.contains("xp"))
            || (lowerName.contains("guild") && lowerName.contains("xp"))) {
            return true;
        }

        for (String indicator : KILL_INDICATORS) {
            if (lowerName.contains(indicator)) {
                return true;
            }
        }
        return false;
    }

    private boolean isWynnXpSignal(String lowerText) {
        return (lowerText.contains("xp") && (lowerText.contains("combat") || lowerText.contains("guild")))
            || lowerText.contains("combat xp")
            || lowerText.contains("guild xp")
            || lowerText.contains("shared xp");
    }

    private boolean hasRecentWynnXpSignal(long now) {
        return now - lastWynnXpSignalMs <= WYNN_XP_SIGNAL_WINDOW_MS;
    }

    private String extractTextDisplayText(Entity entity) {
        try {
            Object textObj = entity.getClass().getMethod("getText").invoke(entity);
            if (textObj == null) {
                return null;
            }
            try {
                Object plain = textObj.getClass().getMethod("getString").invoke(textObj);
                if (plain instanceof String plainString && !plainString.isEmpty()) {
                    return plainString;
                }
            } catch (Exception ignored) {
            }

            return textObj.toString();
        } catch (Exception ignored) {
            return null;
        }
    }

    private String normalizeText(String text) {
        String noFormatting = text.replaceAll("§.", "");
        return noFormatting.toLowerCase(Locale.ROOT);
    }
}
