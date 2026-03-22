package com.example;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import com.example.SpotTotalsScreen;

public class SpotManagerScreen extends Screen {
    private static final int C_BG = 0xFF090D18;
    private static final int C_PANEL = 0xFF0B1020;
    private static final int C_LINE = 0xFF1C263D;
    private static final int C_TEXT = 0xFFE5ECFF;
    private static final int C_MUTED = 0xFF667196;
    private static final int C_ACCENT = 0xFF19D9A3;
    private static final int C_RED = 0xFFFF4455;
    private static final int C_MYTHIC = 0xFFB04DFF;
    private static final int C_INGREDIENT = 0xFFFFD580;
    private static final int C_GATHERING = 0xFF3399FF;
    private static final int C_PIN = 0xFF6B8CFF;

    private static final int SPOT_ROW_H = 34;
    private static final int SPOT_ROW_GAP = 6;
    private static final int COLUMN_SCROLLBAR_W = 6;
    private static final int SPOT_START_BTN_W = 30;
    private static final int COL_TOTAL_W = 64;
    private static final int COL_TOTAL_H = 14;

    private final Screen parent;
    private final List<ConfigManager.FarmSpot> allSpots = new ArrayList<>();

    private EditBox nameBox;
    private EditBox zoneBox;

    private String selectedSpotName = "";
    private String formCategory = "ingredient";
    private boolean formFavorite = false;

    private String lastSavedName = "";
    private String lastSavedZone = "";
    private String lastSavedCategory = "ingredient";
    private boolean lastSavedFavorite = false;

    private long lastClickTime = 0L;
    private String lastClickedSpot = "";

    private String notice = "";
    private int noticeColor = C_MUTED;
    private long noticeUntil = 0L;

    private final int[] columnScrollOffsets = new int[]{0, 0, 0};
    private int draggingScrollbarColumn = -1;
    private int scrollbarDragOffsetY = 0;

    public SpotManagerScreen(Screen parent) {
        super(Component.literal("Spots"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        nameBox = new EditBox(this.font, 18, this.height - 94, 220, 18, Component.literal("Name"));
        nameBox.setMaxLength(64);
        this.addRenderableWidget(nameBox);

        zoneBox = new EditBox(this.font, 248, this.height - 94, 220, 18, Component.literal("Zone"));
        zoneBox.setMaxLength(64);
        this.addRenderableWidget(zoneBox);

        for (int i = 0; i < columnScrollOffsets.length; i++) {
            columnScrollOffsets[i] = 0;
        }
        draggingScrollbarColumn = -1;

        reloadSpots();
    }

    private void reloadSpots() {
        allSpots.clear();
        allSpots.addAll(MobKillerCalculatorClient.getFarmSpotsSnapshot());
        allSpots.sort(
            Comparator
                .comparing((ConfigManager.FarmSpot s) -> !s.favorite)
                .thenComparing(s -> normalizeCategory(s.category))
                .thenComparing(s -> s.name == null ? "" : s.name.toLowerCase(Locale.ROOT))
        );
        lastSavedName = selectedSpotName;
        lastSavedZone = zoneBox.getValue() == null ? "" : zoneBox.getValue();
        lastSavedCategory = formCategory;
        lastSavedFavorite = formFavorite;
    }

    @Override
    public void render(GuiGraphics gui, int mouseX, int mouseY, float partialTicks) {
        gui.fill(0, 0, this.width, this.height, C_BG);
        gui.fill(0, 0, this.width, 40, C_PANEL);
        gui.fill(0, 39, this.width, 40, C_LINE);

        gui.drawString(this.font, "Spot Manager", 14, 13, C_TEXT, false);
        gui.drawString(this.font, "Categories: Mythic / Ingredient / Gathering", 130, 13, C_MUTED, false);

        renderCategoryColumns(gui, mouseX, mouseY);
        renderBottomPanel(gui, mouseX, mouseY);
        renderNotice(gui);

        super.render(gui, mouseX, mouseY, partialTicks);
    }

    private void renderCategoryColumns(GuiGraphics gui, int mouseX, int mouseY) {
        int top = 52;
        int bottom = this.height - 130;
        int columnsX = 12;
        int columnsW = this.width - 24;

        gui.fill(columnsX, top, columnsX + columnsW, bottom, C_PANEL);
        gui.fill(columnsX, top, columnsX + columnsW, top + 1, C_LINE);
        gui.fill(columnsX, bottom - 1, columnsX + columnsW, bottom, C_LINE);

        String[] categories = {"mythic", "ingredient", "gathering"};
        String[] labels = {"Mythics", "Ingredients", "Gathering"};
        int colW = columnsW / 3;

        for (int c = 0; c < 3; c++) {
            int colX = columnsX + c * colW;
            if (c > 0) {
                gui.fill(colX, top, colX + 1, bottom, C_LINE);
            }

            gui.drawString(this.font, labels[c], colX + 10, top + 8, categoryColor(categories[c]), false);
            int totalBtnX = colX + colW - COL_TOTAL_W - 10;
            gui.fill(totalBtnX, top + 6, totalBtnX + COL_TOTAL_W, top + 6 + COL_TOTAL_H, 0x332A2F3D);
            gui.fill(totalBtnX, top + 6, totalBtnX + 2, top + 6 + COL_TOTAL_H, categoryColor(categories[c]));
            gui.drawString(this.font, "Total", totalBtnX + 8, top + 9, C_TEXT, false);

            List<ConfigManager.FarmSpot> spots = getSpotsByCategory(categories[c]);
            int listTop = top + 24;
            int listBottom = bottom - 8;
            int listH = listBottom - listTop;
            int rowX = colX + 8;
            int rowW = colW - 8 - 8 - COLUMN_SCROLLBAR_W - 4;

            int contentH = Math.max(0, spots.size() * (SPOT_ROW_H + SPOT_ROW_GAP) - SPOT_ROW_GAP);
            int maxScroll = Math.max(0, contentH - listH);
            columnScrollOffsets[c] = Math.max(0, Math.min(columnScrollOffsets[c], maxScroll));

            int y = listTop - columnScrollOffsets[c];
            for (ConfigManager.FarmSpot spot : spots) {
                if (y + SPOT_ROW_H >= listTop && y <= listBottom) {
                    int rowColor = (categoryColor(categories[c]) & 0x00FFFFFF) | 0x22000000;
                    boolean selected = isSelected(spot);
                    boolean hovered = inRect(mouseX, mouseY, rowX, y, rowW, SPOT_ROW_H);
                    boolean profitable = isProfitableSpot(spot);

                    if (profitable) {
                        rowColor = (0xFF19D9A3 & 0x00FFFFFF) | 0x22000000;
                    }

                    gui.fill(rowX, y, rowX + rowW, y + SPOT_ROW_H, rowColor);
                    gui.fill(rowX, y, rowX + 2, y + SPOT_ROW_H, categoryColor(categories[c]));
                    gui.fill(rowX, y + SPOT_ROW_H - 1, rowX + rowW, y + SPOT_ROW_H, C_LINE);

                    if (hovered) {
                        gui.fill(rowX, y, rowX + rowW, y + SPOT_ROW_H, 0x18000000);
                    }
                    if (selected) {
                        gui.fill(rowX, y, rowX + rowW, y + 1, C_ACCENT);
                        gui.fill(rowX + rowW - 1, y, rowX + rowW, y + SPOT_ROW_H, C_ACCENT);
                    }

                    int startX = rowX + rowW - SPOT_START_BTN_W - 4;
                    int textW = rowW - SPOT_START_BTN_W - 12;
                    String title = compact(spot.name, Math.max(10, textW / 6));
                    String zone = compact(spot.zone == null || spot.zone.isEmpty() ? "No zone" : spot.zone, Math.max(8, textW / 7));
                    if (spot.favorite) {
                        drawAnimatedFavoriteText(gui, title, rowX + 6, y + 6, c * 37 + rowX + y);
                    } else {
                        gui.drawString(this.font, title, rowX + 6, y + 6, C_TEXT, false);
                    }
                    gui.drawString(this.font, zone, rowX + 6, y + 18, C_MUTED, false);

                    String spotName = spot.name == null ? "" : spot.name;
                    boolean runningThisSpot = MobKillerCalculatorClient.isSessionRunning()
                        && !spotName.isEmpty()
                        && spotName.equalsIgnoreCase(MobKillerCalculatorClient.getActiveFarmSpotName());

                    int startBg = runningThisSpot
                        ? (hovered ? 0x33A63A44 : 0x2A7A2530)
                        : (hovered ? 0x33435D7A : 0x2A2A3A4D);
                    int startAccent = runningThisSpot ? C_RED : C_ACCENT;
                    String startIcon = runningThisSpot ? "■" : "▶";
                    gui.fill(startX, y + 6, startX + SPOT_START_BTN_W, y + SPOT_ROW_H - 6, startBg);
                    gui.fill(startX, y + 6, startX + 2, y + SPOT_ROW_H - 6, startAccent);
                    gui.drawString(this.font, startIcon, startX + 11, y + 13, C_TEXT, false);
                }
                y += SPOT_ROW_H + SPOT_ROW_GAP;
            }

            int trackX = rowX + rowW + 4;
            gui.fill(trackX, listTop, trackX + COLUMN_SCROLLBAR_W, listBottom, 0x221C263D);
            if (maxScroll > 0) {
                int trackH = listH;
                int thumbH = Math.max(18, (int) ((long) listH * trackH / Math.max(1, contentH)));
                int thumbRange = trackH - thumbH;
                int thumbY = listTop + (thumbRange > 0 ? (columnScrollOffsets[c] * thumbRange / maxScroll) : 0);
                int thumbColor = draggingScrollbarColumn == c ? C_ACCENT : C_MUTED;
                gui.fill(trackX, thumbY, trackX + COLUMN_SCROLLBAR_W, thumbY + thumbH, thumbColor);
            }
        }
    }

    private void renderBottomPanel(GuiGraphics gui, int mouseX, int mouseY) {
        int panelY = this.height - 120;
        gui.fill(0, panelY, this.width, this.height, C_PANEL);
        gui.fill(0, panelY, this.width, panelY + 1, C_LINE);

        gui.drawString(this.font, "Name", 18, panelY + 8, C_MUTED, false);
        gui.drawString(this.font, "Zone", 248, panelY + 8, C_MUTED, false);

        nameBox.setX(18);
        nameBox.setY(panelY + 22);
        zoneBox.setX(248);
        zoneBox.setY(panelY + 22);

        int chipX = 480;
        chipX = renderCategoryChip(gui, chipX, panelY + 22, "mythic", "Mythic");
        chipX = renderCategoryChip(gui, chipX + 6, panelY + 22, "ingredient", "Ingredient");
        chipX = renderCategoryChip(gui, chipX + 6, panelY + 22, "gathering", "Gathering");
        
        drawButton(gui, chipX + 12, panelY + 22, 90, 18, "Total Stats", C_ACCENT);

        final int actionY = panelY + 49;
        final int actionH = 20;
        String primaryAction = selectedSpotName.isEmpty() ? "Create Here" : "Change location";
        drawButton(gui, 18, actionY, 108, actionH, primaryAction, C_ACCENT);
        drawButton(gui, 132, actionY, 96, actionH, "Delete", C_RED);
        
        int favColor = formFavorite ? 0x443A2D0A : 0x332A2F3D;
        gui.fill(236, actionY, 316, actionY + actionH, favColor);
        gui.fill(236, actionY, 238, actionY + actionH, 0xFFFFD166);
        gui.drawString(this.font, formFavorite ? "Favorite" : "Normal", 244, actionY + 6, C_TEXT, false);
        boolean autoPreset = MobKillerCalculatorClient.isAutoSpotPresetEnabled();
        int autoBg = autoPreset ? 0x44193D29 : 0x332A2F3D;
        int autoAccent = autoPreset ? C_ACCENT : C_MUTED;
        gui.fill(322, actionY, 450, actionY + actionH, autoBg);
        gui.fill(322, actionY, 324, actionY + actionH, autoAccent);
        gui.drawString(this.font, "Auto Preset: " + (autoPreset ? "ON" : "OFF"), 330, actionY + 6, autoPreset ? C_ACCENT : C_MUTED, false);

        drawPingButton(gui, 456, actionY, 118, actionH, "Ping on map");
        int stopX = this.width - 112;
        drawButton(gui, stopX, actionY, 96, actionH, "Stop Session", C_RED);

        gui.drawString(this.font, "Selected:", 18, panelY + 74, C_TEXT, false);
        String selected = selectedSpotName.isEmpty() ? "None" : selectedSpotName;
        gui.pose().pushMatrix();
        gui.pose().translate(78, panelY + 72);
        gui.pose().scale(1.5f, 1.5f);
        if (formFavorite && !selectedSpotName.isEmpty()) {
            drawAnimatedFavoriteText(gui, selected, 0, 0, 91);
        } else {
            int nameColor = selectedSpotName.isEmpty() ? C_MUTED : C_TEXT;
            gui.drawString(this.font, selected, 0, 0, nameColor, false);
        }
        gui.pose().popMatrix();
        gui.drawString(this.font, "Esc: Back", this.width - 80, panelY + 74, C_MUTED, false);
    }

    private int renderCategoryChip(GuiGraphics gui, int x, int y, String category, String label) {
        int w = this.font.width(label) + 16;
        int color = categoryColor(category);
        boolean active = normalizeCategory(formCategory).equals(category);
        int bg = active ? ((color & 0x00FFFFFF) | 0x44000000) : 0x332A2F3D;
        gui.fill(x, y, x + w, y + 18, bg);
        gui.fill(x, y, x + 2, y + 18, color);
        gui.drawString(this.font, label, x + 6, y + 5, C_TEXT, false);
        return x + w;
    }

    private void drawButton(GuiGraphics gui, int x, int y, int w, int h, String text, int accent) {
        gui.fill(x, y, x + w, y + h, 0x332A2F3D);
        gui.fill(x, y, x + 2, y + h, accent);
        gui.drawString(this.font, text, x + 8, y + 6, C_TEXT, false);
    }

    private void drawPingButton(GuiGraphics gui, int x, int y, int w, int h, String text) {
        boolean active = !selectedSpotName.isEmpty();
        int bg = active ? 0x2D16233D : 0x1F2A2F3D;
        int accent = active ? C_PIN : C_MUTED;
        int textColor = active ? C_TEXT : C_MUTED;

        gui.fill(x, y, x + w, y + h, bg);
        gui.fill(x, y, x + 2, y + h, accent);
        gui.fill(x + 8, y + 4, x + 13, y + 9, accent);
        gui.fill(x + 9, y + 9, x + 12, y + 13, accent);
        gui.fill(x + 10, y + 13, x + 11, y + 15, accent);
        gui.drawString(this.font, text, x + 20, y + 6, textColor, false);
    }

    private void drawAnimatedFavoriteText(GuiGraphics gui, String text, int x, int y, int offsetSeed) {
        if (text == null || text.isEmpty()) {
            return;
        }

        long now = System.currentTimeMillis();
        float baseHue = ((now % 2200L) / 2200.0f + (offsetSeed % 17) * 0.0375f) % 1.0f;
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
    public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent event, boolean consumed) {
        if (super.mouseClicked(event, consumed)) return true;
        if (event.button() != 0) return false;

        int mx = (int) event.x();
        int my = (int) event.y();

        if (handleColumnTotalStatsClick(mx, my)) {
            return true;
        }

        if (tryStartScrollbarDrag(mx, my)) {
            return true;
        }

        if (clickSpotCube(mx, my)) {
            return true;
        }

        int panelY = this.height - 120;
        if (my >= panelY + 22 && my <= panelY + 40) {
            int chipStart = 480;
            int chip = chipStart;
            int mythicW = this.font.width("Mythic") + 16;
            int ingredientW = this.font.width("Ingredient") + 16;
            int gatheringW = this.font.width("Gathering") + 16;

            if (inRect(mx, my, chip, panelY + 22, mythicW, 18)) {
                if (!formCategory.equals("mythic")) {
                    formCategory = "mythic";
                    autoSaveIfSelected();
                }
                return true;
            }
            chip += mythicW + 6;
            if (inRect(mx, my, chip, panelY + 22, ingredientW, 18)) {
                if (!formCategory.equals("ingredient")) {
                    formCategory = "ingredient";
                    autoSaveIfSelected();
                }
                return true;
            }
            chip += ingredientW + 6;
            if (inRect(mx, my, chip, panelY + 22, gatheringW, 18)) {
                if (!formCategory.equals("gathering")) {
                    formCategory = "gathering";
                    autoSaveIfSelected();
                }
                return true;
            }
        }        
        if (inRect(mx, my, 480 + getCategoryChipsWidth(), panelY + 22, 90, 18)) {
            String category = normalizeCategory(formCategory);
            this.minecraft.setScreen(new SpotTotalsScreen(this, category));
            return true;
        }        
        if (inRect(mx, my, 236, panelY + 49, 80, 20)) {
            formFavorite = !formFavorite;
            autoSaveIfSelected();
            return true;
        }

        if (inRect(mx, my, 322, panelY + 49, 128, 20)) {
            MobKillerCalculatorClient.toggleAutoSpotPreset();
            return true;
        }

        if (inRect(mx, my, 18, panelY + 49, 108, 20)) {
            if (selectedSpotName == null || selectedSpotName.isEmpty()) {
                return onCreateSpot();
            }
            return onChangeSpotLocation();
        }
        if (inRect(mx, my, 132, panelY + 49, 96, 20)) {
            return onDeleteSpot();
        }
        if (inRect(mx, my, 456, panelY + 49, 118, 20)) {
            if (!selectedSpotName.isEmpty()) {
                showSpotOnWynntilsMap();
            }
            return true;
        }

        int stopX = this.width - 112;
        if (inRect(mx, my, stopX, panelY + 49, 96, 20)) {
            if (MobKillerCalculatorClient.isSessionRunning()) {
                MobKillerCalculatorClient.stopSession();
                pushNotice("Session stopped", C_ACCENT);
            } else {
                pushNotice("No running session", C_MUTED);
            }
            return true;
        }

        return false;
    }

    private int getCategoryChipsWidth() {
        int w = 0;
        w += this.font.width("Mythic") + 16 + 6;
        w += this.font.width("Ingredient") + 16 + 6;
        w += this.font.width("Gathering") + 16;
        return w;
    }

    private boolean isProfitableSpot(ConfigManager.FarmSpot spot) {
        if (spot == null || spot.category == null) return false;
        if (!"ingredient".equalsIgnoreCase(normalizeCategory(spot.category))) return false;
        
        if (spot.ingredientsFound == null || spot.ingredientsFound.isEmpty()) return false;
        
        final double PROFIT_THRESHOLD = 1.15; // 15% above average
        
        for (String itemName : spot.ingredientsFound.keySet()) {
            Long avg30d = MobKillerCalculatorClient.getIngredientAvg30d(itemName);
            if (avg30d != null && avg30d > 0) {
                if (avg30d > 3000) { // Simple heuristic: if average is high, likely profitable
                    return true;
                }
            }
        }
        return false;
    }

    private boolean clickSpotCube(int mx, int my) {
        int top = 52;
        int bottom = this.height - 130;
        int columnsX = 12;
        int columnsW = this.width - 24;
        int colW = columnsW / 3;

        String[] categories = {"mythic", "ingredient", "gathering"};
        for (int c = 0; c < 3; c++) {
            int colX = columnsX + c * colW;
            List<ConfigManager.FarmSpot> spots = getSpotsByCategory(categories[c]);

            int listTop = top + 24;
            int listBottom = bottom - 8;
            int listH = listBottom - listTop;
            int rowX = colX + 8;
            int rowW = colW - 8 - 8 - COLUMN_SCROLLBAR_W - 4;
            int contentH = Math.max(0, spots.size() * (SPOT_ROW_H + SPOT_ROW_GAP) - SPOT_ROW_GAP);
            int maxScroll = Math.max(0, contentH - listH);
            columnScrollOffsets[c] = Math.max(0, Math.min(columnScrollOffsets[c], maxScroll));

            int y = listTop - columnScrollOffsets[c];
            for (ConfigManager.FarmSpot spot : spots) {
                int startX = rowX + rowW - SPOT_START_BTN_W - 4;
                if (inRect(mx, my, startX, y + 6, SPOT_START_BTN_W, SPOT_ROW_H - 12)) {
                    String spotName = spot.name == null ? "" : spot.name;
                    if (spotName.isEmpty()) {
                        pushNotice("Spot name missing", C_RED);
                        return true;
                    }

                    boolean runningThisSpot = MobKillerCalculatorClient.isSessionRunning()
                        && spotName.equalsIgnoreCase(MobKillerCalculatorClient.getActiveFarmSpotName());
                    if (runningThisSpot) {
                        MobKillerCalculatorClient.stopSession();
                        pushNotice("Session stopped", C_ACCENT);
                        return true;
                    }

                    if (MobKillerCalculatorClient.isSessionRunning()) {
                        MobKillerCalculatorClient.stopSession();
                    }
                    boolean started = MobKillerCalculatorClient.startSessionOnSpot(spotName);
                    if (started) {
                        selectedSpotName = spotName;
                        nameBox.setValue(spotName);
                        zoneBox.setValue(spot.zone == null ? "" : spot.zone);
                        formCategory = normalizeCategory(spot.category);
                        formFavorite = spot.favorite;
                        pushNotice("Session started on " + spotName, C_ACCENT);
                    } else {
                        pushNotice("Could not start session", C_RED);
                    }
                    return true;
                }

                if (inRect(mx, my, rowX, y, rowW, SPOT_ROW_H)) {
                    String newSpotName = spot.name == null ? "" : spot.name;
                    long now = System.currentTimeMillis();
                    if (newSpotName.equals(lastClickedSpot) && now - lastClickTime < 300) {
                        this.minecraft.setScreen(new SpotDetailsScreen(this, newSpotName));
                        lastClickTime = 0L;
                        lastClickedSpot = "";
                        return true;
                    }
                    lastClickTime = now;
                    lastClickedSpot = newSpotName;
                    selectedSpotName = newSpotName;
                    nameBox.setValue(spot.name == null ? "" : spot.name);
                    zoneBox.setValue(spot.zone == null ? "" : spot.zone);
                    formCategory = normalizeCategory(spot.category);
                    formFavorite = spot.favorite;
                    lastSavedName = selectedSpotName;
                    lastSavedZone = zoneBox.getValue() == null ? "" : zoneBox.getValue();
                    lastSavedCategory = formCategory;
                    lastSavedFavorite = formFavorite;
                    return true;
                }

                y += SPOT_ROW_H + SPOT_ROW_GAP;
            }
        }
        if (mx >= columnsX && mx <= columnsX + columnsW && my >= top && my <= bottom - 8) {
            selectedSpotName = "";
            nameBox.setValue("");
            zoneBox.setValue("");
            formCategory = "ingredient";
            formFavorite = false;
            lastSavedName = "";
            lastSavedZone = "";
            lastSavedCategory = "ingredient";
            lastSavedFavorite = false;
            return true;
        }

        return false;
    }

    private boolean tryStartScrollbarDrag(int mx, int my) {
        int top = 52;
        int bottom = this.height - 130;
        int columnsX = 12;
        int columnsW = this.width - 24;
        int colW = columnsW / 3;
        String[] categories = {"mythic", "ingredient", "gathering"};

        for (int c = 0; c < 3; c++) {
            int colX = columnsX + c * colW;
            int listTop = top + 24;
            int listBottom = bottom - 8;
            int listH = listBottom - listTop;
            int rowX = colX + 8;
            int rowW = colW - 8 - 8 - COLUMN_SCROLLBAR_W - 4;
            int trackX = rowX + rowW + 4;

            List<ConfigManager.FarmSpot> spots = getSpotsByCategory(categories[c]);
            int contentH = Math.max(0, spots.size() * (SPOT_ROW_H + SPOT_ROW_GAP) - SPOT_ROW_GAP);
            int maxScroll = Math.max(0, contentH - listH);
            if (maxScroll <= 0) continue;

            int trackH = listH;
            int thumbH = Math.max(18, (int) ((long) listH * trackH / Math.max(1, contentH)));
            int thumbRange = trackH - thumbH;
            int thumbY = listTop + (thumbRange > 0 ? (columnScrollOffsets[c] * thumbRange / maxScroll) : 0);

            if (inRect(mx, my, trackX, thumbY, COLUMN_SCROLLBAR_W, thumbH)) {
                draggingScrollbarColumn = c;
                scrollbarDragOffsetY = my - thumbY;
                return true;
            }
        }
        return false;
    }

    private boolean handleColumnTotalStatsClick(int mx, int my) {
        int top = 52;
        int columnsX = 12;
        int columnsW = this.width - 24;
        int colW = columnsW / 3;
        String[] categories = {"mythic", "ingredient", "gathering"};

        for (int c = 0; c < 3; c++) {
            int colX = columnsX + c * colW;
            int totalBtnX = colX + colW - COL_TOTAL_W - 10;
            if (inRect(mx, my, totalBtnX, top + 6, COL_TOTAL_W, COL_TOTAL_H)) {
                this.minecraft.setScreen(new SpotTotalsScreen(this, categories[c]));
                return true;
            }
        }
        return false;
    }

    private boolean onCreateSpot() {
        String name = nameBox.getValue() == null ? "" : nameBox.getValue().trim();
        String zone = zoneBox.getValue() == null ? "" : zoneBox.getValue().trim();

        boolean ok = MobKillerCalculatorClient.saveCurrentPositionAsSpot(formCategory, name, zone, formFavorite);
        if (!ok) {
            pushNotice("Could not create the spot", C_RED);
            return true;
        }

        reloadSpots();
        if (!name.isEmpty()) selectedSpotName = name;
        pushNotice("Spot created", C_ACCENT);
        return true;
    }

    private boolean onChangeSpotLocation() {
        if (selectedSpotName == null || selectedSpotName.isEmpty()) {
            pushNotice("Select a spot first", C_RED);
            return true;
        }

        boolean ok = MobKillerCalculatorClient.updateFarmSpotLocation(selectedSpotName);
        if (!ok) {
            pushNotice("Could not update spot location", C_RED);
            return true;
        }

        reloadSpots();
        pushNotice("Spot location updated", C_ACCENT);
        return true;
    }

    private boolean onDeleteSpot() {
        if (selectedSpotName == null || selectedSpotName.isEmpty()) {
            pushNotice("Select a spot first", C_RED);
            return true;
        }

        boolean ok = MobKillerCalculatorClient.deleteFarmSpot(selectedSpotName);
        if (!ok) {
            pushNotice("Could not delete spot", C_RED);
            return true;
        }

        selectedSpotName = "";
        nameBox.setValue("");
        zoneBox.setValue("");
        reloadSpots();
        pushNotice("Spot deleted", C_ACCENT);
        return true;
    }

    private boolean onToggleFavorite() {
        if (selectedSpotName == null || selectedSpotName.isEmpty()) {
            formFavorite = !formFavorite;
            pushNotice(formFavorite ? "Favorite enabled for next create" : "Favorite disabled for next create", C_ACCENT);
            return true;
        }

        boolean ok = MobKillerCalculatorClient.toggleFarmSpotFavorite(selectedSpotName);
        if (!ok) {
            pushNotice("Could not toggle favorite", C_RED);
            return true;
        }

        ConfigManager.FarmSpot updated = MobKillerCalculatorClient.getFarmSpotByName(selectedSpotName);
        if (updated != null) {
            formFavorite = updated.favorite;
        }
        reloadSpots();
        pushNotice(formFavorite ? "Marked as favorite" : "Removed from favorites", C_ACCENT);
        return true;
    }

    private boolean onOpenDetails() {
        if (selectedSpotName == null || selectedSpotName.isEmpty()) {
            pushNotice("Select a spot first", C_RED);
            return true;
        }

        this.minecraft.setScreen(new SpotDetailsScreen(this, selectedSpotName));
        return true;
    }

    private int[] getSelectedSpotCoords() {
        ConfigManager.FarmSpot spot = MobKillerCalculatorClient.getFarmSpotByName(selectedSpotName);
        if (spot == null) {
            return new int[]{0, 0, 0};
        }
        return new int[]{spot.x, spot.y, spot.z};
    }

    private void showSpotOnWynntilsMap() {
        int[] coords = getSelectedSpotCoords();
        String clipboardCoords = coords[0] + " " + coords[1] + " " + coords[2];
        String compassCoords = coords[0] + " " + coords[2];
        
        if (this.minecraft != null && this.minecraft.player != null) {
            this.minecraft.keyboardHandler.setClipboard(clipboardCoords);
            try {
                this.minecraft.player.connection.sendCommand("compass " + compassCoords);
            } catch (Exception ignored) {
            }
            Component msg = Component.literal("§6Pinned: " + clipboardCoords + " §7(copied + /compass " + compassCoords + ")");
            this.minecraft.player.displayClientMessage(msg, true);
            pushNotice("Pinged and copied: " + clipboardCoords, C_ACCENT);
        }
    }

    @Override
    public void tick() {
        super.tick();
        if (!selectedSpotName.isEmpty()) {
            String currentName = nameBox.getValue() == null ? "" : nameBox.getValue().trim();
            String currentZone = zoneBox.getValue() == null ? "" : zoneBox.getValue().trim();
            
            if (!currentName.equals(lastSavedName) || !currentZone.equals(lastSavedZone)) {
                if (!currentName.isEmpty() && currentName.length() > 0) {
                    ConfigManager.FarmSpot serverSpot = MobKillerCalculatorClient.getFarmSpotByName(lastSavedName);
                    if (serverSpot != null && (!currentName.equals(serverSpot.name) || !currentZone.equals(serverSpot.zone))) {
                        autoSaveCurrentSpot();
                    }
                }
            }
        }
    }

    private void autoSaveIfSelected() {
        if (!selectedSpotName.isEmpty()) {
            autoSaveCurrentSpot();
        }
    }

    private void autoSaveCurrentSpot() {
        String currentName = nameBox.getValue() == null ? "" : nameBox.getValue().trim();
        String currentZone = zoneBox.getValue() == null ? "" : zoneBox.getValue().trim();

        if (currentName.isEmpty()) {
            pushNotice("Name cannot be empty", C_RED);
            return;
        }

        boolean ok = MobKillerCalculatorClient.updateFarmSpot(lastSavedName, currentName, currentZone, formCategory, formFavorite);
        if (!ok) {
            pushNotice("Could not auto-save", C_RED);
            return;
        }

        lastSavedName = currentName;
        lastSavedZone = currentZone;
        lastSavedCategory = formCategory;
        lastSavedFavorite = formFavorite;
        selectedSpotName = currentName;
        reloadSpots();
        pushNotice("Auto-saved", C_ACCENT);
    }

    private void pushNotice(String msg, int color) {
        notice = msg == null ? "" : msg;
        noticeColor = color;
        noticeUntil = System.currentTimeMillis() + 2200L;
    }

    private void renderNotice(GuiGraphics gui) {
        if (notice.isEmpty() || System.currentTimeMillis() > noticeUntil) {
            return;
        }

        int tw = this.font.width(notice);
        int x = this.width / 2 - tw / 2;
        int y = 44;
        gui.fill(x - 6, y - 2, x + tw + 6, y + 10, 0xCC090D18);
        gui.drawString(this.font, notice, x, y, noticeColor, false);
    }

    private List<ConfigManager.FarmSpot> getSpotsByCategory(String category) {
        String expected = normalizeCategory(category);
        List<ConfigManager.FarmSpot> out = new ArrayList<>();
        for (ConfigManager.FarmSpot spot : allSpots) {
            if (spot == null) continue;
            if (!normalizeCategory(spot.category).equals(expected)) continue;
            out.add(spot);
        }
        return out;
    }

    private boolean isSelected(ConfigManager.FarmSpot spot) {
        if (spot == null || spot.name == null || selectedSpotName == null) return false;
        return spot.name.equalsIgnoreCase(selectedSpotName);
    }

    private static String compact(String text, int max) {
        if (text == null) return "";
        if (text.length() <= max) return text;
        return text.substring(0, Math.max(1, max - 1)) + "~";
    }

    private static String normalizeCategory(String category) {
        if (category == null) return "";
        String c = category.trim().toLowerCase(Locale.ROOT);
        if (c.startsWith("myth")) return "mythic";
        if (c.startsWith("ingred")) return "ingredient";
        if (c.startsWith("gather")) return "gathering";
        return c;
    }

    private static int categoryColor(String category) {
        return switch (normalizeCategory(category)) {
            case "mythic" -> C_MYTHIC;
            case "ingredient" -> C_INGREDIENT;
            case "gathering" -> C_GATHERING;
            default -> C_ACCENT;
        };
    }

    private static boolean inRect(int mx, int my, int x, int y, int w, int h) {
        return mx >= x && mx <= x + w && my >= y && my <= y + h;
    }

    @Override
    public boolean mouseDragged(net.minecraft.client.input.MouseButtonEvent event, double deltaX, double deltaY) {
        if (draggingScrollbarColumn >= 0 && event.button() == 0) {
            int c = draggingScrollbarColumn;
            int top = 52;
            int bottom = this.height - 130;
            int columnsX = 12;
            int columnsW = this.width - 24;
            int colW = columnsW / 3;
            int colX = columnsX + c * colW;
            int listTop = top + 24;
            int listBottom = bottom - 8;
            int listH = listBottom - listTop;
            int rowX = colX + 8;
            int rowW = colW - 8 - 8 - COLUMN_SCROLLBAR_W - 4;

            String[] categories = {"mythic", "ingredient", "gathering"};
            List<ConfigManager.FarmSpot> spots = getSpotsByCategory(categories[c]);
            int contentH = Math.max(0, spots.size() * (SPOT_ROW_H + SPOT_ROW_GAP) - SPOT_ROW_GAP);
            int maxScroll = Math.max(0, contentH - listH);
            if (maxScroll <= 0) {
                columnScrollOffsets[c] = 0;
                return true;
            }

            int trackH = listH;
            int thumbH = Math.max(18, (int) ((long) listH * trackH / Math.max(1, contentH)));
            int thumbRange = Math.max(1, trackH - thumbH);
            int rawThumbY = (int) event.y() - scrollbarDragOffsetY;
            int clampedThumbY = Math.max(listTop, Math.min(rawThumbY, listTop + trackH - thumbH));
            int thumbPos = clampedThumbY - listTop;
            columnScrollOffsets[c] = Math.max(0, Math.min((thumbPos * maxScroll) / thumbRange, maxScroll));
            return true;
        }
        return super.mouseDragged(event, deltaX, deltaY);
    }

    @Override
    public boolean mouseReleased(net.minecraft.client.input.MouseButtonEvent event) {
        if (draggingScrollbarColumn >= 0 && event.button() == 0) {
            draggingScrollbarColumn = -1;
            return true;
        }
        return super.mouseReleased(event);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        int top = 52;
        int bottom = this.height - 130;
        int columnsX = 12;
        int columnsW = this.width - 24;
        int colW = columnsW / 3;
        String[] categories = {"mythic", "ingredient", "gathering"};

        for (int c = 0; c < 3; c++) {
            int colX = columnsX + c * colW;
            int colRight = colX + colW;
            if (mouseX < colX || mouseX > colRight || mouseY < top || mouseY > bottom) {
                continue;
            }

            int listTop = top + 24;
            int listBottom = bottom - 8;
            int listH = listBottom - listTop;
            List<ConfigManager.FarmSpot> spots = getSpotsByCategory(categories[c]);
            int contentH = Math.max(0, spots.size() * (SPOT_ROW_H + SPOT_ROW_GAP) - SPOT_ROW_GAP);
            int maxScroll = Math.max(0, contentH - listH);
            columnScrollOffsets[c] = (int) Math.max(0, Math.min(columnScrollOffsets[c] - scrollY * 16, maxScroll));
            return true;
        }

        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
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
