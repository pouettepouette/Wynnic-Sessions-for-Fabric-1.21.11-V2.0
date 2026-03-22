package com.example;

import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public class SpotSessionHistoryScreen extends Screen {
    private static final int C_BG      = 0xFF090D18;
    private static final int C_PANEL   = 0xFF0B1020;
    private static final int C_LINE    = 0xFF1C263D;
    private static final int C_TEXT    = 0xFFE5ECFF;
    private static final int C_MUTED   = 0xFF667196;
    private static final int C_ACCENT  = 0xFF19D9A3;
    private static final int C_RED     = 0xFFFF4455;
    private static final int C_YELLOW  = 0xFFFFD580;

    private final Screen parent;
    private final String spotName;
    private ConfigManager.FarmSpot spot;
    private int scrollOffset = 0;

    public SpotSessionHistoryScreen(Screen parent, String spotName) {
        super(Component.literal("Session History"));
        this.parent = parent;
        this.spotName = spotName == null ? "" : spotName;
    }

    @Override
    protected void init() {
        this.spot = MobKillerCalculatorClient.getFarmSpotByName(spotName);
    }

    @Override
    public void render(GuiGraphics gui, int mouseX, int mouseY, float partialTicks) {
        gui.fill(0, 0, this.width, this.height, C_BG);
        gui.fill(0, 0, this.width, 40, C_PANEL);
        gui.fill(0, 39, this.width, 40, C_LINE);

        gui.drawString(this.font, "Session History", 14, 13, C_TEXT, false);
        gui.drawString(this.font, "Esc: Back", this.width - 70, 13, C_MUTED, false);

        if (spot == null) {
            gui.drawString(this.font, "Spot not found: " + spotName, 16, 56, C_RED, false);
            super.render(gui, mouseX, mouseY, partialTicks);
            return;
        }

        int y = 52;
        gui.fill(12, y, this.width - 12, y + 26, C_PANEL);
        gui.fill(12, y, this.width - 12, y + 1, C_LINE);
        gui.fill(12, y + 25, this.width - 12, y + 26, C_LINE);

        String catColor = getCategoryColor(spot.category);
        gui.drawString(this.font, safe(spot.name), 20, y + 4, C_TEXT, false);
        gui.drawString(this.font,
            catColor + safe(spot.category) + "§r  §7Total: " + spot.totalSessions + " sessions | " + formatDuration(spot.totalFarmedSeconds),
            20, y + 14, 0xFFFFFFFF, false);
        y += 32;

        List<ConfigManager.SpotSessionRecord> records =
            spot.sessionRecords == null ? List.of() : spot.sessionRecords;

        if (records.isEmpty()) {
            gui.drawString(this.font, "No session history recorded yet for this spot.", 20, y + 10, C_MUTED, false);
            gui.drawString(this.font, "(Sessions completed after this update will appear here)", 20, y + 22, C_MUTED, false);
            super.render(gui, mouseX, mouseY, partialTicks);
            return;
        }
        int colDate  = 20;
        int colTime  = 128;
        int colLoot  = 215;
        int colMoney = 355;
        int colEh    = 455;
        int copyBtnW = 42;
        int copyBtnX = this.width - 14 - copyBtnW;
        int minCopySpacing = 60;
        if (colEh + minCopySpacing > copyBtnX) {
            colEh = Math.max(355, copyBtnX - minCopySpacing);
        }

        int headerY = y;
        gui.fill(12, headerY, this.width - 12, headerY + 14, 0x22FFFFFF);
        gui.drawString(this.font, "Date",     colDate,  headerY + 3, C_MUTED, false);
        gui.drawString(this.font, "Time",     colTime,  headerY + 3, C_MUTED, false);
        gui.drawString(this.font, "Loot",     colLoot,  headerY + 3, C_MUTED, false);
        gui.drawString(this.font, "Money made", colMoney, headerY + 3, C_MUTED, false);
        gui.drawString(this.font, "Emeralds/hr", colEh, headerY + 3, C_MUTED, false);
        y += 16;

        int listTop = y;
        int listBottom = this.height - 10;
        int lineH = 14;
        int maxScroll = Math.max(0, records.size() * lineH - (listBottom - listTop));
        scrollOffset = Math.max(0, Math.min(scrollOffset, maxScroll));

        gui.fill(12, listTop, this.width - 12, listBottom, C_PANEL);

        int drawY = listTop - scrollOffset;
        for (int i = 0; i < records.size(); i++) {
            ConfigManager.SpotSessionRecord r = records.get(i);
            if (r == null) continue;
            if (drawY + lineH >= listTop && drawY <= listBottom) {
                if (i % 2 == 0) gui.fill(12, drawY, this.width - 12, drawY + lineH, 0x11FFFFFF);
                String date  = formatDate(r.dateIso);
                long duration = Math.max(0L, r.durationSeconds);
                String time   = formatDuration(duration);
                String loot   = (r.loot == null || r.loot.isBlank()) ? "-" : r.loot;
                long moneyMade = Math.max(0L, r.moneyMade);
                String money = moneyMade > 0 ? formatEmeralds(moneyMade) : "0E";
                long emeraldsPerHour = duration > 0 ? Math.round((moneyMade * 3600.0) / duration) : 0L;
                String eh = formatEmeralds(emeraldsPerHour);

                gui.drawString(this.font, date,  colDate,  drawY + 3, C_TEXT,   false);
                gui.drawString(this.font, time,  colTime,  drawY + 3, C_MUTED,  false);
                gui.drawString(this.font, loot,  colLoot,  drawY + 3, C_ACCENT, false);
                gui.drawString(this.font, money, colMoney, drawY + 3, C_YELLOW, false);
                gui.drawString(this.font, eh,    colEh,    drawY + 3, C_MUTED, false);
                boolean copyHover = mouseX >= copyBtnX && mouseX <= copyBtnX + copyBtnW
                    && mouseY >= drawY && mouseY <= drawY + lineH;
                gui.fill(copyBtnX, drawY + 1, copyBtnX + copyBtnW, drawY + lineH - 1,
                    copyHover ? 0x55305870 : 0x33305870);
                gui.fill(copyBtnX, drawY + 1, copyBtnX + 2, drawY + lineH - 1, C_ACCENT);
                gui.drawString(this.font, "Copy", copyBtnX + 6, drawY + 3, C_TEXT, false);
            }
            drawY += lineH;
        }
        gui.fill(12, listTop, 12 + 1, listBottom, C_LINE);
        gui.fill(this.width - 13, listTop, this.width - 12, listBottom, C_LINE);
        gui.fill(12, listTop, this.width - 12, listTop + 1, C_LINE);
        gui.fill(12, listBottom - 1, this.width - 12, listBottom, C_LINE);

        super.render(gui, mouseX, mouseY, partialTicks);
    }

    @Override
    public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent event, boolean consumed) {
        if (event.button() != 0) return super.mouseClicked(event, consumed);
        if (spot == null) return false;
        List<ConfigManager.SpotSessionRecord> records =
            spot.sessionRecords == null ? List.of() : spot.sessionRecords;
        if (records.isEmpty()) return false;

        double mx = event.x(), my = event.y();
        int copyBtnW = 42;
        int copyBtnX = this.width - 14 - copyBtnW;
        int y = 52 + 32 + 16; // header + spot header + col header
        int listTop = y;
        int lineH = 14;

        int drawY = listTop - scrollOffset;
        for (ConfigManager.SpotSessionRecord r : records) {
            if (r != null && mx >= copyBtnX && mx <= copyBtnX + copyBtnW
                    && my >= drawY && my <= drawY + lineH) {
                long duration = Math.max(0L, r.durationSeconds);
                String time = formatDuration(duration);
                String loot = (r.loot == null || r.loot.isBlank()) ? "-" : r.loot;
                long moneyMade = Math.max(0L, r.moneyMade);
                String money = formatEmeralds(moneyMade);
                long emeraldsPerHour = duration > 0 ? Math.round((moneyMade * 3600.0) / duration) : 0L;
                String eh = formatEmeralds(emeraldsPerHour);
                String date  = formatDate(r.dateIso);
                String text  = "Spot: " + spot.name + " | " + date
                    + " | Time: " + time
                    + " | Loot: " + loot
                    + " | Money made: " + money
                    + " | Emeralds/hr: " + eh;
                this.minecraft.keyboardHandler.setClipboard(text);
                return true;
            }
            drawY += lineH;
        }
        return super.mouseClicked(event, consumed);
    }

    private String getCategoryColor(String cat) {
        if (cat == null) return "§7";
        String c = cat.toLowerCase();
        if (c.startsWith("myth")) return "§d";
        if (c.startsWith("ingred")) return "§e";
        if (c.startsWith("gather")) return "§9";
        return "§7";
    }

    private static String formatDate(String iso) {
        if (iso == null || iso.length() < 10) return "-";
        String date = iso.substring(0, 10);
        String time = iso.length() >= 16 ? iso.substring(11, 16) : "";
        return date + (time.isEmpty() ? "" : " " + time);
    }

    private static String formatDuration(long seconds) {
        long s = Math.max(0L, seconds);
        long h = s / 3600;
        long m = (s % 3600) / 60;
        long sec = s % 60;
        if (h > 0) return h + "h " + m + "m";
        if (m > 0) return m + "m " + sec + "s";
        return sec + "s";
    }

    private static String formatEmeralds(long emeralds) {
        return MobKillerCalculatorClient.formatWynnCurrencyCompact(emeralds);
    }

    private static String safe(String value) {
        return value == null || value.isEmpty() ? "-" : value;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        scrollOffset -= (int)(scrollY * 13);
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
}
