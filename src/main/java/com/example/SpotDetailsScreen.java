package com.example;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public class SpotDetailsScreen extends Screen {
    private static final int C_BG = 0xFF090D18;
    private static final int C_PANEL = 0xFF0B1020;
    private static final int C_LINE = 0xFF1C263D;
    private static final int C_TEXT = 0xFFE5ECFF;
    private static final int C_MUTED = 0xFF667196;
    private static final int C_ACCENT = 0xFF19D9A3;
    private static final int C_MYTHIC = 0xFFB04DFF;

    private final Screen parent;
    private final String spotName;
    private ConfigManager.FarmSpot spot;
    private int scrollOffset = 0;
    private float splitRatio = 0.5f;
    private boolean draggingSplitBar = false;

    private static final int SPLIT_MIN_COL_W = 180;
    private static final int SPLIT_HANDLE_W = 6;
    private static final int SPLIT_SNAP_PX = 14;

    public SpotDetailsScreen(Screen parent, String spotName) {
        super(Component.literal("Spot Details"));
        this.parent = parent;
        this.spotName = spotName == null ? "" : spotName;
    }

    @Override
    protected void init() {
        this.spot = MobKillerCalculatorClient.getFarmSpotByName(spotName);
        this.splitRatio = ConfigManager.loadSpotDetailsSplitRatio();
    }

    @Override
    public void render(GuiGraphics gui, int mouseX, int mouseY, float partialTicks) {
        gui.fill(0, 0, this.width, this.height, C_BG);
        gui.fill(0, 0, this.width, 40, C_PANEL);
        gui.fill(0, 39, this.width, 40, C_LINE);

        drawButton(gui, 10, 10, 22, 18, "<", C_ACCENT);
        gui.drawString(this.font, "Spot Details", 38, 13, C_TEXT, false);
        drawButton(gui, this.width - 170, 10, 84, 18, "Total Stats", C_ACCENT);
        gui.drawString(this.font, "Esc: Back", this.width - 70, 13, C_MUTED, false);

        if (spot == null) {
            gui.drawString(this.font, "Spot not found", 16, 56, 0xFFFF4455, false);
            super.render(gui, mouseX, mouseY, partialTicks);
            return;
        }

        int y = 54;
        boolean mythicSpot = isMythicSpot();
        gui.pose().pushMatrix();
        gui.pose().translate(16, y);
        gui.pose().scale(1.7f, 1.7f);
        if (spot.favorite) {
            drawAnimatedFavoriteText(gui, safe(spot.name), 0, 0, 143);
        } else {
            gui.drawString(this.font, safe(spot.name), 0, 0, C_TEXT, false);
        }
        gui.pose().popMatrix();
        y += 26;

        gui.drawString(this.font, "Category: " + safe(spot.category), 16, y, C_MUTED, false); y += 14;
        
        gui.drawString(this.font, "Zone: " + (spot.zone != null && !spot.zone.isEmpty() ? spot.zone : "(no zone set)"), 16, y, C_MUTED, false); y += 14;

        String statsLine = "Sessions: " + spot.totalSessions + " | Kills: " + spot.totalKills;
        if (mythicSpot) {
            statsLine += " | Mythics: " + totalFoundCount(spot.mythicsFound);
            gui.drawString(this.font, statsLine, 16, y, C_MUTED, false);
        } else {
            statsLine += " | Money: " + formatEmeralds(spot.totalMoneyMade);
            gui.drawString(this.font, statsLine, 16, y, C_MUTED, false);
        }
        y += 14;
        gui.drawString(this.font, "Farm Time: " + formatDuration(spot.totalFarmedSeconds), 16, y, C_MUTED, false); y += 20;

        if (mythicSpot) {
            String mobLevel = safe(spot.mobLevelRange);
            String mobNames = safe(compact(spot.mobNamesSummary, 72));
            gui.drawString(this.font, "Mob levels: " + mobLevel, 16, y, C_MUTED, false); y += 14;
            gui.drawString(this.font, "Mobs: " + mobNames, 16, y, C_MUTED, false); y += 20;
        }

        List<Map.Entry<String, Integer>> entries = sortedFoundEntries();
        List<ConfigManager.SpotSessionRecord> records =
            spot.sessionRecords == null ? List.of() : spot.sessionRecords;

        int panelTop = y;
        int panelLeft = 14;
        int panelRight = this.width - 14;
        int panelBottom = this.height - 14;
        gui.fill(panelLeft, panelTop, panelRight, panelBottom, C_PANEL);
        gui.fill(panelLeft, panelTop, panelRight, panelTop + 1, C_LINE);
        gui.fill(panelLeft, panelBottom - 1, panelRight, panelBottom, C_LINE);

        int lineH = 14;
        if (mythicSpot) {
            gui.drawString(this.font, "Mythics Found", 22, panelTop + 6, C_MYTHIC, false);
            int listTop = panelTop + 18;
            int listBottom = panelBottom - 6;
            int maxScroll = Math.max(0, entries.size() * lineH - (listBottom - listTop));
            scrollOffset = Math.max(0, Math.min(scrollOffset, maxScroll));

            if (entries.isEmpty()) {
                gui.drawString(this.font, "No mythic data yet for this spot.", 22, listTop + 4, C_MUTED, false);
            } else {
                int drawY = listTop - scrollOffset;
                for (Map.Entry<String, Integer> entry : entries) {
                    if (drawY >= listTop - lineH && drawY <= listBottom) {
                        String line = entry.getKey() + " x" + entry.getValue();
                        gui.drawString(this.font, line, 22, drawY, C_TEXT, false);
                    }
                    drawY += lineH;
                }
            }
        } else {
            int panelW = panelRight - panelLeft;
            int baseMidX = panelLeft + panelW / 2;
            int midX = panelLeft + Math.round(panelW * splitRatio);
            int minMid = panelLeft + SPLIT_MIN_COL_W;
            int maxMid = panelRight - SPLIT_MIN_COL_W;
            midX = Math.max(minMid, Math.min(maxMid, midX));
            if (Math.abs(midX - baseMidX) <= SPLIT_SNAP_PX) {
                midX = baseMidX;
            }
            splitRatio = (midX - panelLeft) / (float) panelW;

            boolean splitHover = mouseX >= midX - 4 && mouseX <= midX + 4
                && mouseY >= panelTop + 2 && mouseY <= panelBottom - 2;
            int splitColor = (draggingSplitBar || splitHover) ? C_ACCENT : C_LINE;
            gui.fill(midX - (SPLIT_HANDLE_W / 2), panelTop + 2, midX - (SPLIT_HANDLE_W / 2) + SPLIT_HANDLE_W, panelBottom - 2, splitColor);

            gui.drawString(this.font, "Ingredients Found", panelLeft + 8, panelTop + 6, C_ACCENT, false);
            gui.drawString(this.font, "History", midX + 8, panelTop + 6, C_ACCENT, false);

            int listTop = panelTop + 18;
            int listBottom = panelBottom - 6;
            int maxRows = Math.max(entries.size(), records.size());
            int maxScroll = Math.max(0, maxRows * lineH - (listBottom - listTop));
            scrollOffset = Math.max(0, Math.min(scrollOffset, maxScroll));

            if (entries.isEmpty()) {
                gui.drawString(this.font, "No ingredient data yet for this spot.", panelLeft + 8, listTop + 4, C_MUTED, false);
            } else {
                int drawY = listTop - scrollOffset;
                for (Map.Entry<String, Integer> entry : entries) {
                    if (drawY >= listTop - lineH && drawY <= listBottom) {
                        String line = compact(entry.getKey() + " x" + entry.getValue(), 28);
                        gui.drawString(this.font, line, panelLeft + 8, drawY, C_TEXT, false);
                    }
                    drawY += lineH;
                }
            }

            if (records.isEmpty()) {
                gui.drawString(this.font, "No session history for this spot.", midX + 8, listTop + 4, C_MUTED, false);
            } else {
                int copyBtnW = 38;
                int copyBtnX = panelRight - copyBtnW - 8;
                int drawY = listTop - scrollOffset;
                int maxTextWidth = copyBtnX - midX - 12; // Ensure text doesn't reach Copy button
                for (ConfigManager.SpotSessionRecord record : records) {
                    if (record != null && drawY >= listTop - lineH && drawY <= listBottom) {
                        String line = buildHistoryLine(record);
                        if (this.font.width(line) > maxTextWidth) {
                            line = line.substring(0, Math.max(20, line.length() - 20));
                            if (!line.endsWith("...")) line += "...";
                        }
                        gui.drawString(this.font, line, midX + 8, drawY, C_TEXT, false);

                        boolean copyHover = mouseX >= copyBtnX && mouseX <= copyBtnX + copyBtnW
                            && mouseY >= drawY - 1 && mouseY <= drawY + lineH;
                        gui.fill(copyBtnX, drawY - 1, copyBtnX + copyBtnW, drawY + lineH, copyHover ? 0x55305870 : 0x33305870);
                        gui.fill(copyBtnX, drawY - 1, copyBtnX + 2, drawY + lineH, C_ACCENT);
                        gui.drawString(this.font, "Copy", copyBtnX + 6, drawY + 2, C_TEXT, false);
                    }
                    drawY += lineH;
                }
            }
        }

        super.render(gui, mouseX, mouseY, partialTicks);
    }

    private List<Map.Entry<String, Integer>> sortedFoundEntries() {
        List<Map.Entry<String, Integer>> entries = new ArrayList<>();
        Map<String, Integer> source = isMythicSpot() ? spot.mythicsFound : spot.ingredientsFound;
        if (spot == null || source == null || source.isEmpty()) {
            return entries;
        }
        entries.addAll(source.entrySet());
        entries.removeIf(e -> e == null || e.getKey() == null || e.getKey().trim().isEmpty() || e.getValue() == null || e.getValue() <= 0);
        entries.sort((a, b) -> Comparator
            .comparingInt((Map.Entry<String, Integer> e) -> e.getValue())
            .reversed()
            .thenComparing(Map.Entry::getKey)
            .compare(a, b));
        return entries;
    }

    private boolean isMythicSpot() {
        return spot != null && spot.category != null && spot.category.toLowerCase().startsWith("myth");
    }

    private int totalFoundCount(Map<String, Integer> values) {
        if (values == null || values.isEmpty()) {
            return 0;
        }
        int total = 0;
        for (Integer value : values.values()) {
            total += Math.max(0, value == null ? 0 : value);
        }
        return total;
    }

    private String compact(String value, int maxLen) {
        String safeValue = safe(value);
        if (safeValue.length() <= maxLen) {
            return safeValue;
        }
        return safeValue.substring(0, Math.max(0, maxLen - 3)) + "...";
    }

    private static String safe(String value) {
        return value == null || value.isEmpty() ? "-" : value;
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

    private static String formatDateShort(String iso) {
        if (iso == null || iso.length() < 16) {
            return "-";
        }
        String day = iso.substring(5, 10);
        String time = iso.substring(11, 16);
        return day + " " + time;
    }

    private String buildHistoryLine(ConfigManager.SpotSessionRecord record) {
        long duration = Math.max(0L, record.durationSeconds);
        long moneyMade = Math.max(0L, record.moneyMade);
        long emeraldsPerHour = duration > 0 ? Math.round((moneyMade * 3600.0) / duration) : 0L;
        String loot = (record.loot == null || record.loot.isBlank()) ? "-" : record.loot;
        return formatDateShort(record.dateIso)
            + "    |    Time: " + formatDuration(duration)
            + "    |    Loot: " + loot
            + "    |    Money made: " + formatEmeralds(moneyMade)
            + "    |    Emeralds/hr: " + formatEmeralds(emeraldsPerHour);
    }

    private String buildHistoryClipboardText(ConfigManager.SpotSessionRecord record) {
        long duration = Math.max(0L, record.durationSeconds);
        long moneyMade = Math.max(0L, record.moneyMade);
        long emeraldsPerHour = duration > 0 ? Math.round((moneyMade * 3600.0) / duration) : 0L;
        String loot = (record.loot == null || record.loot.isBlank()) ? "-" : record.loot;
        return "Spot: " + safe(spot.name)
            + " | Date: " + formatDateShort(record.dateIso)
            + " | Time: " + formatDuration(duration)
            + " | Loot: " + loot
            + " | Money made: " + formatEmeralds(moneyMade)
            + " | Emeralds/hr: " + formatEmeralds(emeraldsPerHour);
    }

    private int computePanelTop(boolean mythicSpot) {
        int y = 54;
        y += 26; // title
        y += 14; // category
        y += 14; // zone
        y += 14; // stats
        y += 20; // farm time
        if (mythicSpot) {
            y += 14; // mob levels
            y += 20; // mobs line + spacing
        }
        return y;
    }

    private static String formatCurrency(long amount) {
        long a = Math.max(0L, amount);
        if (a >= 1_000_000_000L) {
            return String.format("%.1fB", a / 1_000_000_000.0);
        }
        if (a >= 1_000_000L) {
            return String.format("%.1fM", a / 1_000_000.0);
        }
        if (a >= 1_000L) {
            return String.format("%.1fK", a / 1_000.0);
        }
        return String.valueOf(a);
    }

    private static String formatEmeralds(long emeralds) {
        return MobKillerCalculatorClient.formatWynnCurrencyCompact(emeralds);
    }

    private void drawButton(GuiGraphics gui, int x, int y, int w, int h, String text, int accent) {
        gui.fill(x, y, x + w, y + h, 0x332A2F3D);
        gui.fill(x, y, x + 2, y + h, accent);
        gui.drawString(this.font, text, x + 8, y + 5, C_TEXT, false);
    }

    private void drawAnimatedFavoriteText(GuiGraphics gui, String text, int x, int y, int offsetSeed) {
        if (text == null || text.isEmpty()) {
            return;
        }

        long now = System.currentTimeMillis();
        float baseHue = ((now % 2200L) / 2200.0f + (offsetSeed % 19) * 0.03125f) % 1.0f;
        int drawX = x;
        for (int i = 0; i < text.length(); i++) {
            String ch = text.substring(i, i + 1);
            int color = hsvToRgb((baseHue + i * 0.085f) % 1.0f, 0.72f, 1.0f);
            gui.drawString(this.font, ch, drawX, y, color, false);
            drawX += this.font.width(ch);
        }
    }

    private static int hsvToRgb(float hue, float saturation, float value) {
        float h = (hue % 1.0f) * 6.0f;
        int sector = (int) Math.floor(h);
        float fraction = h - sector;
        float p = value * (1.0f - saturation);
        float q = value * (1.0f - fraction * saturation);
        float t = value * (1.0f - (1.0f - fraction) * saturation);

        float red;
        float green;
        float blue;
        switch (sector) {
            case 0 -> {
                red = value;
                green = t;
                blue = p;
            }
            case 1 -> {
                red = q;
                green = value;
                blue = p;
            }
            case 2 -> {
                red = p;
                green = value;
                blue = t;
            }
            case 3 -> {
                red = p;
                green = q;
                blue = value;
            }
            case 4 -> {
                red = t;
                green = p;
                blue = value;
            }
            default -> {
                red = value;
                green = p;
                blue = q;
            }
        }

        int r = Math.max(0, Math.min(255, Math.round(red * 255.0f)));
        int g = Math.max(0, Math.min(255, Math.round(green * 255.0f)));
        int b = Math.max(0, Math.min(255, Math.round(blue * 255.0f)));
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        scrollOffset -= (int)(scrollY * 14);
        return true;
    }

    @Override
    public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent event, boolean consumed) {
        if (super.mouseClicked(event, consumed)) {
            return true;
        }
        if (event.button() != 0) {
            return false;
        }
        int mx = (int) event.x();
        int my = (int) event.y();
        if (mx >= 10 && mx <= 32 && my >= 10 && my <= 28) {
            onClose();
            return true;
        }
        if (mx >= this.width - 170 && mx <= this.width - 86 && my >= 10 && my <= 28) {
            this.minecraft.setScreen(new SpotTotalsScreen(this, safe(spot.category)));
            return true;
        }

        if (spot != null && !isMythicSpot()) {
            int panelLeft = 14;
            int panelRight = this.width - 14;
            int panelTop = computePanelTop(false);
            int panelBottom = this.height - 14;
            int panelW = panelRight - panelLeft;
            int baseMidX = panelLeft + panelW / 2;
            int midX = panelLeft + Math.round(panelW * splitRatio);
            int minMid = panelLeft + SPLIT_MIN_COL_W;
            int maxMid = panelRight - SPLIT_MIN_COL_W;
            midX = Math.max(minMid, Math.min(maxMid, midX));
            if (Math.abs(midX - baseMidX) <= SPLIT_SNAP_PX) {
                midX = baseMidX;
            }
            if (mx >= midX - 5 && mx <= midX + 5 && my >= panelTop + 2 && my <= panelBottom - 2) {
                draggingSplitBar = true;
                return true;
            }

            List<ConfigManager.SpotSessionRecord> records =
                spot.sessionRecords == null ? List.of() : spot.sessionRecords;
            if (!records.isEmpty()) {
                int lineH = 14;
                int listTop = panelTop + 18;
                int listBottom = panelBottom - 6;
                int copyBtnW = 38;
                int copyBtnX = panelRight - copyBtnW - 8;

                int drawY = listTop - scrollOffset;
                for (ConfigManager.SpotSessionRecord record : records) {
                    if (record != null
                        && drawY >= listTop - lineH && drawY <= listBottom
                        && mx >= copyBtnX && mx <= copyBtnX + copyBtnW
                        && my >= drawY - 1 && my <= drawY + lineH) {
                        this.minecraft.keyboardHandler.setClipboard(buildHistoryClipboardText(record));
                        return true;
                    }
                    drawY += lineH;
                }
            }
        }
        return false;
    }

    @Override
    public boolean mouseDragged(net.minecraft.client.input.MouseButtonEvent event, double deltaX, double deltaY) {
        if (draggingSplitBar && spot != null && !isMythicSpot() && event.button() == 0) {
            int panelLeft = 14;
            int panelRight = this.width - 14;
            int panelW = panelRight - panelLeft;
            int panelTop = computePanelTop(false);
            int panelBottom = this.height - 14;
            int mouseX = (int) event.x();
            int mouseY = (int) event.y();

            if (mouseY >= panelTop - 10 && mouseY <= panelBottom + 10) {
                int minMid = panelLeft + SPLIT_MIN_COL_W;
                int maxMid = panelRight - SPLIT_MIN_COL_W;
                int midX = Math.max(minMid, Math.min(maxMid, mouseX));
                int baseMidX = panelLeft + panelW / 2;
                if (Math.abs(midX - baseMidX) <= SPLIT_SNAP_PX) {
                    midX = baseMidX;
                }
                splitRatio = (midX - panelLeft) / (float) panelW;
            }
            return true;
        }
        return super.mouseDragged(event, deltaX, deltaY);
    }

    @Override
    public boolean mouseReleased(net.minecraft.client.input.MouseButtonEvent event) {
        if (draggingSplitBar && event.button() == 0) {
            draggingSplitBar = false;
            int panelLeft = 14;
            int panelRight = this.width - 14;
            int panelW = panelRight - panelLeft;
            int baseMidX = panelLeft + panelW / 2;
            int midX = panelLeft + Math.round(panelW * splitRatio);
            if (Math.abs(midX - baseMidX) <= SPLIT_SNAP_PX) {
                splitRatio = 0.5f;
            }
            ConfigManager.saveSpotDetailsSplitRatio(splitRatio);
            return true;
        }
        return super.mouseReleased(event);
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
}
