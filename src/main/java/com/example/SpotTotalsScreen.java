package com.example;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public class SpotTotalsScreen extends Screen {
    private static final int C_BG = 0xFF090D18;
    private static final int C_PANEL = 0xFF0B1020;
    private static final int C_LINE = 0xFF1C263D;
    private static final int C_TEXT = 0xFFE5ECFF;
    private static final int C_MUTED = 0xFF667196;
    private static final int C_ACCENT = 0xFF19D9A3;
    private static final int C_MYTHIC = 0xFFB04DFF;

    private final Screen parent;
    private final String category;
    private final List<Map.Entry<String, Integer>> totals = new ArrayList<>();
    private int scrollOffset = 0;
    private int totalSessions = 0;
    private long totalKills = 0;
    private long totalFarmSeconds = 0;
    private int totalSpots = 0;
    private long totalMoneyMade = 0;

    public SpotTotalsScreen(Screen parent, String category) {
        super(Component.literal("Total Stats"));
        this.parent = parent;
        this.category = normalizeCategory(category);
    }

    @Override
    protected void init() {
        Map<String, Integer> aggregate = new LinkedHashMap<>();
        for (ConfigManager.FarmSpot spot : MobKillerCalculatorClient.getFarmSpotsSnapshot()) {
            if (spot == null || !normalizeCategory(spot.category).equals(category)) {
                continue;
            }
            totalSpots++;
            totalSessions += Math.max(0, spot.totalSessions);
            totalKills += Math.max(0L, spot.totalKills);
            totalFarmSeconds += Math.max(0L, spot.totalFarmedSeconds);
            totalMoneyMade += Math.max(0L, spot.totalMoneyMade);
            Map<String, Integer> source = isMythicCategory() ? spot.mythicsFound : spot.ingredientsFound;
            if (source == null || source.isEmpty()) {
                continue;
            }
            for (Map.Entry<String, Integer> entry : source.entrySet()) {
                if (entry == null || entry.getKey() == null || entry.getKey().isBlank()) {
                    continue;
                }
                int count = Math.max(0, entry.getValue() == null ? 0 : entry.getValue());
                if (count <= 0) {
                    continue;
                }
                aggregate.merge(entry.getKey().trim(), count, Integer::sum);
            }
        }
        totals.clear();
        totals.addAll(aggregate.entrySet());
        totals.sort((a, b) -> Comparator.comparingInt((Map.Entry<String, Integer> e) -> e.getValue()).reversed().thenComparing(Map.Entry::getKey).compare(a, b));
    }

    @Override
    public void render(GuiGraphics gui, int mouseX, int mouseY, float partialTicks) {
        gui.fill(0, 0, this.width, this.height, C_BG);
        gui.fill(0, 0, this.width, 40, C_PANEL);
        gui.fill(0, 39, this.width, 40, C_LINE);

        String label = isMythicCategory() ? "Mythic Total Stats" : "Spot Total Stats";
        gui.drawString(this.font, label, 14, 13, C_TEXT, false);
        gui.drawString(this.font, "Esc: Back", this.width - 70, 13, C_MUTED, false);

        int y = 54;
        gui.drawString(this.font, "Category: " + displayCategory(category), 16, y, C_MUTED, false); y += 14;
        gui.drawString(this.font, "Spots: " + totalSpots + " | Sessions: " + totalSessions + " | Kills: " + totalKills, 16, y, C_MUTED, false); y += 14;
        gui.drawString(this.font, "Farm Time: " + formatDuration(totalFarmSeconds) + " | Money made: " + MobKillerCalculatorClient.formatWynnCurrency(totalMoneyMade), 16, y, C_MUTED, false); y += 20;

        String section = isMythicCategory() ? "All Mythics Found" : "All Ingredients Found";
        int color = isMythicCategory() ? C_MYTHIC : C_ACCENT;
        gui.drawString(this.font, section, 16, y, color, false); y += 12;

        gui.fill(14, y, this.width - 14, this.height - 14, C_PANEL);
        gui.fill(14, y, this.width - 14, y + 1, C_LINE);
        gui.fill(14, this.height - 15, this.width - 14, this.height - 14, C_LINE);

        int listTop = y + 6;
        int listBottom = this.height - 20;
        int lineH = 12;
        int maxScroll = Math.max(0, totals.size() * lineH - (listBottom - listTop));
        scrollOffset = Math.max(0, Math.min(scrollOffset, maxScroll));

        if (totals.isEmpty()) {
            gui.drawString(this.font, isMythicCategory() ? "No mythic totals available yet." : "No ingredient totals available yet.", 22, listTop + 4, C_MUTED, false);
        } else {
            int drawY = listTop - scrollOffset;
            for (Map.Entry<String, Integer> entry : totals) {
                if (drawY >= listTop - lineH && drawY <= listBottom) {
                    gui.drawString(this.font, entry.getKey() + " x" + entry.getValue(), 22, drawY, C_TEXT, false);
                }
                drawY += lineH;
            }
        }

        super.render(gui, mouseX, mouseY, partialTicks);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        scrollOffset -= (int) (scrollY * 14);
        return true;
    }

    @Override
    public boolean keyPressed(net.minecraft.client.input.KeyEvent event) {
        if (event.key() == org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE) {
            onClose();
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public void onClose() {
        this.minecraft.setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private boolean isMythicCategory() {
        return "mythic".equals(category);
    }

    private static String normalizeCategory(String value) {
        if (value == null) {
            return "";
        }
        String lowered = value.trim().toLowerCase(Locale.ROOT);
        if (lowered.startsWith("myth")) return "mythic";
        if (lowered.startsWith("ingred")) return "ingredient";
        if (lowered.startsWith("gather")) return "gathering";
        return lowered;
    }

    private static String displayCategory(String value) {
        return switch (normalizeCategory(value)) {
            case "mythic" -> "Mythic";
            case "ingredient" -> "Ingredient";
            case "gathering" -> "Gathering";
            default -> "Spot";
        };
    }

    private static String formatDuration(long seconds) {
        long s = Math.max(0L, seconds);
        long h = s / 3600;
        long m = (s % 3600) / 60;
        long sec = s % 60;
        if (h > 0) return h + "h " + m + "m " + sec + "s";
        if (m > 0) return m + "m " + sec + "s";
        return sec + "s";
    }
}
