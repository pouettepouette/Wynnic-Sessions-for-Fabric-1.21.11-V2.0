package com.example;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public class ModulesHubScreen extends Screen {
    private static final int C_BG         = 0xFF090D18;
    private static final int C_SIDEBAR    = 0xFF0E1424;
    private static final int C_CONTENT    = 0xFF0B1020;
    private static final int C_LINE       = 0xFF1C263D;
    private static final int C_ROW_HOVER  = 0x55131D30;
    private static final int C_ROW_EVEN   = 0x18101A2D;
    private static final int C_TEXT       = 0xFFE5ECFF;
    private static final int C_MUTED      = 0xFF667196;
    private static final int C_ACCENT     = 0xFF19D9A3;
    private static final int C_SWITCH_OFF = 0xFF3A3F52;
    private static final int C_KNOB       = 0xFFEFF4FF;
    private static final int C_TAG_BG     = 0xFF151E34;
    private static final int C_MYTHIC     = 0xFFC86CFF;
    private static final int C_INGR       = 0xFFFFD580;
    private static final int C_GATHER     = 0xFF3399FF;
    private static final int C_RED        = 0xFFFF4455;
    private static final int C_HISTORY    = 0xFFFFA347;
    private static final int C_INFO       = 0xFF4DD6C8;
    private static final int C_STATUS     = 0xFF7FD6FF;
    private static final int C_CREDITS    = 0xFFFF8AB6;
    private static final int C_LINK       = 0xFF6BB5FF;
    private static final int C_PIN        = 0xFF6B8CFF;

    private static final int SIDEBAR_W = 118;
    private static final int HEADER_H  = 40;
    private static final int ROW_H     = 26;
    private static final int SPOT_ROW_H = 34;
    private static final int SPOT_ROW_GAP = 6;
    private static final int SPOT_SCROLLBAR_W = 6;
    private static final int SPOT_START_BTN_W = 20;
    private static final int SPOT_PIN_BTN_W = 16;
    private static final int SPOT_COL_TOTAL_W = 64;
    private static final int SPOT_COL_TOTAL_H = 14;
    private static final int SPOT_CATEGORY_BTN_W = 128;
    private static final int SPOT_CATEGORY_BTN_H = 18;

    private static final int[] HUD_COLOR_VALUES = {
        0x3399FF, 0x33FF33, 0xFFCC00, 0xFF66FF, 0xFFFFFF, 0xFF3333, 0xB04DFF
    };
    private static final String[] HUD_COLOR_NAMES = {
        "Blue", "Green", "Yellow", "Pink", "White", "Fabled Red", "Mythic Purple"
    };
    private static final int TAB_SPOTS        = 0;
    private static final int TAB_SESSIONS     = 1;
    private static final int TAB_HUD_SETTINGS = 2;
    private static final int TAB_VALUES       = 3;
    private static final int TAB_INFO         = 4;
    private int activeTab = TAB_SPOTS;
    private int scrollOffset = 0;
    private final List<Row> sessionRows  = new ArrayList<>();
    private final List<Row> valuesRows   = new ArrayList<>();
    private final List<Row> spotRows     = new ArrayList<>();
    private final List<Row> hudSettingsRows = new ArrayList<>();
    private final List<Row> infoRows     = new ArrayList<>();
    private EditBox lootBonusBox, lootQualityBox, charmBonusBox;
    private EditBox supportMessageBox;
    private long supportThanksUntilMs;
    private String toastMessage = "";
    private int toastColor;
    private long toastUntilMs;
    private boolean isDraggingHud = false;
    private double dragStartMouseX, dragStartMouseY;
    private int dragStartHudX, dragStartHudY;
    private boolean dragAxisXUnlocked = false;
    private boolean dragAxisYUnlocked = false;
    private boolean dragPrimaryAxisChosen = false;
    private int selectedHudLineId = MobKillerCalculatorClient.HUD_LINE_PROBABILITY;
    private int selectedAddHudLineId = -1;
    private int selectedColorIndex = 0;  // Track current HUD color index
    private float hudZoom = 1.0f;        // HUD preview zoom level
    private final List<ConfigManager.FarmSpot> embeddedSpots = new ArrayList<>();
    private EditBox spotNameBox;
    private EditBox spotZoneBox;
    private String spotSelectedName = "";
    private String spotFormCategory = "ingredient";
    private boolean spotFormFavorite = false;
    private String spotLastSavedName = "";
    private String spotLastSavedZone = "";
    private String spotLastSavedCategory = "ingredient";
    private boolean spotLastSavedFavorite = false;
    private long spotLastClickTime = 0L;
    private String spotLastClicked = "";
    private final int[] spotColumnScroll = new int[]{0, 0, 0};
    private int draggingSpotScrollbarColumn = -1;
    private int draggingSpotScrollbarOffsetY = 0;
    private final String[] embeddedSpotColumnOrder = new String[]{"mythic", "ingredient", "gathering"};
    private float embeddedSpotDividerRatio1 = 1.0f / 3.0f;
    private float embeddedSpotDividerRatio2 = 2.0f / 3.0f;
    private int draggingSpotDividerIndex = -1;
    private int draggingSpotColumnFrom = -1;
    private String draggingSpotName = "";
    private String draggingSpotSourceCategory = "";
    private int draggingSpotStartX = 0;
    private int draggingSpotStartY = 0;
    private int draggingSpotMouseX = 0;
    private int draggingSpotMouseY = 0;
    private boolean draggingSpotRow = false;
    private String spotNotice = "";
    private int spotNoticeColor = C_MUTED;
    private long spotNoticeUntil = 0L;

    private static final int SPOT_COL_MIN_W = 160;
    private static final int SPOT_DIVIDER_W = 5;
    private static final int SPOT_DIVIDER_SNAP_PX = 12;
    private long lastAutoSaveTime = 0L;
    private static final long AUTO_SAVE_DELAY_MS = 500L;

    public ModulesHubScreen() {
        super(Component.literal("Wynnic Sessions"));
    }

    @Override
    protected void init() {
        MobKillerCalculatorClient.checkServerConnectionStatus();
        scrollOffset = 0;

        int boxX = this.width - 160;
        double[] saved = ConfigManager.loadValues();
        lootBonusBox   = mkBox(boxX, 0, 90, "Loot Bonus");   lootBonusBox.setValue(String.valueOf(saved[0]));
        lootQualityBox = mkBox(boxX, 0, 90, "Loot Quality"); lootQualityBox.setValue(String.valueOf(saved[1]));
        charmBonusBox  = mkBox(boxX, 0, 90, "Charm Bonus");  charmBonusBox.setValue(String.valueOf(saved[2]));

        supportMessageBox = mkBox(SIDEBAR_W + 20, 0, this.width - SIDEBAR_W - 50, "Feedback");
        supportMessageBox.setMaxLength(220);

        spotNameBox = mkBox(SIDEBAR_W + 20, 0, 220, "Name");
        spotNameBox.setMaxLength(64);
        spotZoneBox = mkBox(SIDEBAR_W + 250, 0, 220, "Zone");
        spotZoneBox.setMaxLength(64);

        this.addRenderableWidget(lootBonusBox);
        this.addRenderableWidget(lootQualityBox);
        this.addRenderableWidget(charmBonusBox);
        this.addRenderableWidget(supportMessageBox);
        this.addRenderableWidget(spotNameBox);
        this.addRenderableWidget(spotZoneBox);
        lootBonusBox.setResponder(s -> onBonusValueChanged());
        lootQualityBox.setResponder(s -> onBonusValueChanged());
        charmBonusBox.setResponder(s -> onBonusValueChanged());
        buildSessionRows();
        buildValuesRows();
        buildSpotRows();
        buildHudSettingsRows();
        buildInfoRows();
        ConfigManager.EmbeddedSpotsLayout spotLayout = ConfigManager.loadEmbeddedSpotsLayout();
        if (spotLayout != null) {
            for (int i = 0; i < 3; i++) {
                embeddedSpotColumnOrder[i] = normalizeSpotCategory(spotLayout.columnOrder[i]);
            }
            embeddedSpotDividerRatio1 = spotLayout.dividerRatio1;
            embeddedSpotDividerRatio2 = spotLayout.dividerRatio2;
        }
        reloadEmbeddedSpots();
    }

    private EditBox mkBox(int x, int y, int w, String label) {
        return new EditBox(this.font, x, y, w, 16, Component.literal(label));
    }

    private void buildSessionRows() {
        sessionRows.clear();
        sessionRows.add(Row.header("Session Control"));
        sessionRows.add(Row.toggle("Start / Pause",
            () -> MobKillerCalculatorClient.isSessionRunning() && !MobKillerCalculatorClient.isSessionPaused(),
            () -> sessionStateDesc(), "CORE",
            () -> {
                if (!MobKillerCalculatorClient.isSessionRunning()) {
                    triggerCalculation();
                    MobKillerCalculatorClient.startSession();
                    MobKillerCalculatorClient.setHudVisible(true);
                } else {
                    MobKillerCalculatorClient.toggleSessionPause();
                }
            }));
        sessionRows.add(Row.action("Stop Session", () -> "End the running session.", "CORE",
            () -> { MobKillerCalculatorClient.stopSession(); }));
        sessionRows.add(Row.toggle("Show HUD",
            () -> MobKillerCalculatorClient.isHudVisible(),
            () -> MobKillerCalculatorClient.isHudVisible() ? "HUD visible" : "HUD hidden", "RENDER",
            () -> MobKillerCalculatorClient.setHudVisible(!MobKillerCalculatorClient.isHudVisible())));
        sessionRows.add(Row.header("HUD Presets"));
        sessionRows.add(Row.action("Mythics", () -> "Mythic tracking layout.", "PRESET",
            () -> { MobKillerCalculatorClient.applyMythicsPreset(); refreshHudOrderState(); }));
        sessionRows.add(Row.action("Ingredients", () -> "Ingredient drop layout.", "PRESET",
            () -> { MobKillerCalculatorClient.applyIngredientsPreset(); refreshHudOrderState(); }));
        sessionRows.add(Row.action("Gathering", () -> "Gathering activity layout.", "PRESET",
            () -> { MobKillerCalculatorClient.applyGatheringPreset(); refreshHudOrderState(); }));
    }

    private void buildValuesRows() {
        valuesRows.clear();
        valuesRows.add(Row.header("Mythics"));
        valuesRows.add(Row.toggle("Probability Format",
            () -> MobKillerCalculatorClient.isDisplayProbabilityAsPercent(),
            () -> MobKillerCalculatorClient.getProbabilityDisplayModeLabel(), "MYTHIC",
            () -> MobKillerCalculatorClient.toggleProbabilityDisplayMode()));
        valuesRows.add(Row.editBox("Loot Bonus", lootBonusBox, "MYTHIC"));
        valuesRows.add(Row.editBox("Loot Quality", lootQualityBox, "MYTHIC"));
        valuesRows.add(Row.editBox("Charm Bonus", charmBonusBox, "MYTHIC"));
        valuesRows.add(Row.header("Ingredients"));
        valuesRows.add(Row.toggle("Currency Format",
            () -> MobKillerCalculatorClient.isDisplayCurrencyAsCompact(),
            () -> MobKillerCalculatorClient.getCurrencyDisplayModeLabel(), "INGR",
            () -> MobKillerCalculatorClient.toggleCurrencyDisplayMode()));
        valuesRows.add(Row.toggle("1rst ingredient price",
            () -> MobKillerCalculatorClient.getIngredientRankPriceMode(0) == 1,
            () -> MobKillerCalculatorClient.getIngredientRankPriceSummary(0)
                + " | " + MobKillerCalculatorClient.getIngredientRankAssignedItemLabel(0), "INGR",
            () -> MobKillerCalculatorClient.toggleIngredientRankPriceMode(0)));
        valuesRows.add(Row.toggle("2nd ingredient price",
            () -> MobKillerCalculatorClient.getIngredientRankPriceMode(1) == 1,
            () -> MobKillerCalculatorClient.getIngredientRankPriceSummary(1)
                + " | " + MobKillerCalculatorClient.getIngredientRankAssignedItemLabel(1), "INGR",
            () -> MobKillerCalculatorClient.toggleIngredientRankPriceMode(1)));
        valuesRows.add(Row.toggle("3rd ingredient price",
            () -> MobKillerCalculatorClient.getIngredientRankPriceMode(2) == 1,
            () -> MobKillerCalculatorClient.getIngredientRankPriceSummary(2)
                + " | " + MobKillerCalculatorClient.getIngredientRankAssignedItemLabel(2), "INGR",
            () -> MobKillerCalculatorClient.toggleIngredientRankPriceMode(2)));
        valuesRows.add(Row.header("Gathering"));
        for (int t = 0; t < 3; t++) {
            final int tier = t;
            valuesRows.add(Row.toggle("Tier " + (t + 1) + " price",
                () -> MobKillerCalculatorClient.isGatheringTierLowest(tier),
                () -> MobKillerCalculatorClient.getGatheringTierPriceSummary(tier), "GATHER",
                () -> MobKillerCalculatorClient.toggleGatheringTierPriceMode(tier)));
        }
    }

    private void buildSpotRows() {
        spotRows.clear();

        spotRows.add(Row.header("Mythics"));
        spotRows.add(Row.action("Save Current Spot", () -> "Save your current coords as mythic spot.", "SPOT",
            () -> {
                if (!MobKillerCalculatorClient.saveCurrentPositionAsSpot("mythic")) {
                    toast("Could not save mythic spot", C_RED);
                } else {
                    toast("Mythic spot saved", C_ACCENT);
                }
            }));

        spotRows.add(Row.header("Ingredients"));
        spotRows.add(Row.action("Save Current Spot", () -> "Save your current coords as ingredient spot.", "SPOT",
            () -> {
                if (!MobKillerCalculatorClient.saveCurrentPositionAsSpot("ingredient")) {
                    toast("Could not save ingredient spot", C_RED);
                } else {
                    toast("Ingredient spot saved", C_ACCENT);
                }
            }));

        spotRows.add(Row.header("Gathering"));
        spotRows.add(Row.action("Save Current Spot", () -> "Save your current coords as gathering spot.", "SPOT",
            () -> {
                if (!MobKillerCalculatorClient.saveCurrentPositionAsSpot("gathering")) {
                    toast("Could not save gathering spot", C_RED);
                } else {
                    toast("Gathering spot saved", C_ACCENT);
                }
            }));

        spotRows.add(Row.header("Farm Spots"));
        spotRows.add(Row.action("Share Active Spot", () -> "Send active spot coords in local chat.", "SPOT",
            () -> {
                if (!MobKillerCalculatorClient.sendActiveFarmSpotCoordsToChat()) {
                    toast("No active spot to share", C_RED);
                } else {
                    toast("Spot sent in chat", C_ACCENT);
                }
            }));
        spotRows.add(Row.label("Active Spot", () -> MobKillerCalculatorClient.getActiveFarmSpotSummary()));
        spotRows.add(Row.label("Spot Stats", () -> MobKillerCalculatorClient.getActiveFarmSpotStatsSummary()));
    }

    private void buildHudSettingsRows() {
        hudSettingsRows.clear();
        hudSettingsRows.add(Row.header("HUD Appearance"));
        hudSettingsRows.add(Row.toggle("Text Shadow",
            () -> MobKillerCalculatorClient.isHudTextShadowEnabled(),
            () -> MobKillerCalculatorClient.getHudTextShadowLabel(), "RENDER",
            () -> MobKillerCalculatorClient.toggleHudTextShadow()));
        hudSettingsRows.add(Row.toggle("HUD Background",
            () -> MobKillerCalculatorClient.isHudBackgroundEnabled(),
            () -> MobKillerCalculatorClient.getHudBackgroundLabel(), "RENDER",
            () -> MobKillerCalculatorClient.toggleHudBackground()));
        hudSettingsRows.add(Row.action("Reset HUD Position", () -> "Center the HUD overlay.", "RENDER",
            () -> { MobKillerCalculatorClient.setHudX(this.width / 2); MobKillerCalculatorClient.setHudY(this.height / 2); }));
        hudSettingsRows.add(Row.header("HUD Colors"));
        hudSettingsRows.add(Row.action("Current Color",
            this::getHudColorPalettePreview,
            "COLOR",
            () -> shiftHudColorSelection(1)));
        hudSettingsRows.add(Row.header("HUD Line Order"));
        hudSettingsRows.add(Row.action("Selected Line", this::selectedLineDesc, "HUD",
            this::cycleSelectedHudLine));
            hudSettingsRows.add(Row.action("Move Up ↑", () -> "", "HUD",
            () -> MobKillerCalculatorClient.moveHudLineUp(selectedHudLineId)));
            hudSettingsRows.add(Row.action("Move Down ↓", () -> "", "HUD",
            () -> MobKillerCalculatorClient.moveHudLineDown(selectedHudLineId)));
        hudSettingsRows.add(Row.action("Delete Line -", () -> "", "HUD",
            () -> { if (MobKillerCalculatorClient.deleteHudLine(selectedHudLineId)) refreshHudOrderState(); }));
        hudSettingsRows.add(Row.action("Add Line", this::addLineDesc, "HUD",
            this::cycleAddHudLine));
        hudSettingsRows.add(Row.action("Confirm Add +", () -> "", "HUD",
            () -> { MobKillerCalculatorClient.addHudLine(selectedAddHudLineId); refreshHudOrderState(); }));
    }

    private void buildInfoRows() {
        infoRows.clear();
        infoRows.add(Row.header("Feedback"));
        infoRows.add(Row.editBox("Message", supportMessageBox, "INFO"));
        infoRows.add(Row.action("Send Feedback", () -> "Submit your feedback to devs.", "INFO",
            () -> {
                MobKillerCalculatorClient.sendSupportMessage(supportMessageBox.getValue());
                supportMessageBox.setValue("");
                supportThanksUntilMs = System.currentTimeMillis() + 3000L;
                toast("Thanks for your feedback!", C_ACCENT);
            }));

        infoRows.add(Row.header("Status"));
        infoRows.add(Row.label("API", () -> MobKillerCalculatorClient.getApiSyncStatus()));
        infoRows.add(Row.label("Market", () -> MobKillerCalculatorClient.getMarketNetworkStatus()));

        infoRows.add(Row.header("Links"));
        infoRows.add(Row.action("GitHub Releases", () -> "Check latest version.", "LINK",
            () -> {
                try {
                    java.awt.Desktop.getDesktop().browse(new java.net.URI("https://github.com/pouettepouette?tab=repositories"));
                } catch (Exception e) {
                    toast("Could not open link", C_RED);
                }
            }));
        infoRows.add(Row.action("Documentation", () -> "Open mod README.", "LINK",
            () -> {
                try {
                    java.awt.Desktop.getDesktop().browse(new java.net.URI("https://github.com/pouettepouette?tab=repositories"));
                } catch (Exception e) {
                    toast("Could not open README", C_RED);
                }
            }));

        infoRows.add(Row.header("Credits"));
        infoRows.add(Row.label("Author", () -> "PouettePouette"));
        infoRows.add(Row.label("Version", () -> "1.0.0"));
    }

    @Override
    public void render(GuiGraphics gui, int mouseX, int mouseY, float partialTicks) {
        if (isDraggingHud) { renderDragOverlay(gui); return; }

        gui.fill(0, 0, this.width, this.height, C_BG);

        renderSidebar(gui, mouseX, mouseY);
        renderHeader(gui);
        renderContent(gui, mouseX, mouseY);
        renderHudPreview(gui);
        renderToast(gui);
        positionEditBoxes();

        super.render(gui, mouseX, mouseY, partialTicks);
    }

    private void renderSidebar(GuiGraphics gui, int mouseX, int mouseY) {
        gui.fill(0, 0, SIDEBAR_W, this.height, C_SIDEBAR);
        gui.fill(SIDEBAR_W - 1, 0, SIDEBAR_W, this.height, C_LINE);

        gui.drawString(this.font, "WYNNIC", 14, 14, C_ACCENT, false);

        String[] tabs = {"Spots", "Sessions", "HUD", "Values", "Informations"};
        int y = 48;
        for (int i = 0; i < tabs.length; i++) {
            boolean active = (i == activeTab);
            boolean hover = mouseX >= 6 && mouseX <= SIDEBAR_W - 6 && mouseY >= y - 2 && mouseY <= y + 18;
            if (active) {
                gui.fill(6, y - 2, SIDEBAR_W - 6, y + 18, 0xFF13253B);
                gui.fill(6, y - 2, 8, y + 18, C_ACCENT);
            } else if (hover) {
                gui.fill(6, y - 2, SIDEBAR_W - 6, y + 18, 0x44131D30);
            }
            gui.drawString(this.font, tabs[i], 14, y + 3, active ? C_TEXT : C_MUTED, false);
            y += 26;
            if (i == 3) {
                gui.fill(10, y - 4, SIDEBAR_W - 10, y - 3, C_LINE);
            }
        }
        int cardX1 = 9;
        int cardX2 = SIDEBAR_W - 9;
        int cardH = 14;
        int tradeY = this.height - 74;
        int converterY = tradeY + 18;
        int githubY = converterY + 18;

        gui.fill(8, tradeY - 7, SIDEBAR_W - 8, tradeY - 6, C_LINE);

        boolean tmHover = mouseX >= cardX1 && mouseX <= cardX2 && mouseY >= tradeY && mouseY <= tradeY + cardH;
        int tmBg = tmHover ? 0x55305870 : 0x33305870;
        gui.fill(cardX1, tradeY, cardX2, tradeY + cardH, tmBg);
        gui.fill(cardX1, tradeY, cardX1 + 2, tradeY + cardH, C_LINK);
        gui.drawString(this.font, "Wynnic Market", 14, tradeY + 3, 0xFFE8F3FF, false);

        boolean ccHover = mouseX >= cardX1 && mouseX <= cardX2 && mouseY >= converterY && mouseY <= converterY + cardH;
        int ccBg = ccHover ? 0x55305870 : 0x33305870;
        gui.fill(cardX1, converterY, cardX2, converterY + cardH, ccBg);
        gui.fill(cardX1, converterY, cardX1 + 2, converterY + cardH, C_ACCENT);
        gui.drawString(this.font, "Currency Conv.", 14, converterY + 3, 0xFFE8F3FF, false);

        boolean ghHover = mouseX >= cardX1 && mouseX <= cardX2 && mouseY >= githubY && mouseY <= githubY + cardH;
        int ghBg = ghHover ? 0x55404455 : 0x332A2F3D;
        gui.fill(cardX1, githubY, cardX2, githubY + cardH, ghBg);
        gui.fill(cardX1, githubY, cardX1 + 2, githubY + cardH, C_MUTED);
        gui.drawString(this.font, "GitHub", 14, githubY + 3, 0xFFD5DBEE, false);
    }
    

    private void renderHeader(GuiGraphics gui) {
        gui.fill(SIDEBAR_W, 0, this.width, HEADER_H, 0xF0090D18);
        gui.fill(SIDEBAR_W, HEADER_H, this.width, HEADER_H + 1, C_LINE);

        int x = SIDEBAR_W + 16;
        gui.pose().pushMatrix();
        gui.pose().translate(x, 7);
        gui.pose().scale(1.4f, 1.4f);
        gui.drawString(this.font, "Wynnic Sessions", 0, 0, C_TEXT, false);
        gui.pose().popMatrix();
        gui.drawString(this.font, "by PouettePouette", x, 24, C_MUTED, false);
        if (activeTab == TAB_SPOTS) {
            int btnW = 98;
            int btnX = this.width - btnW - 10;
            int btnY = 11;
            gui.fill(btnX, btnY, btnX + btnW, btnY + 18, 0x332A2F3D);
            gui.fill(btnX, btnY, btnX + 2, btnY + 18, C_ACCENT);
            gui.drawString(this.font, "Total Stats", btnX + 8, btnY + 5, C_TEXT, false);
        }
    }

    private void renderContent(GuiGraphics gui, int mouseX, int mouseY) {
        if (activeTab == TAB_SPOTS) {
            renderEmbeddedSpotManager(gui, mouseX, mouseY);
            return;
        }

        List<Row> rows = getRowsForActiveTab();
        int contentTop = HEADER_H + 2;
        int contentBottom = this.height - 4;
        int contentH = contentBottom - contentTop;
        int totalH = rows.size() * ROW_H;
        int maxScroll = Math.max(0, totalH - contentH);
        scrollOffset = Math.max(0, Math.min(scrollOffset, maxScroll));
        int x0 = SIDEBAR_W + 1;
        int rowW = this.width - SIDEBAR_W - 2;

        int y = contentTop - scrollOffset;
        int currentHeaderColor = C_ACCENT;
        for (int idx = 0; idx < rows.size(); idx++) {
            Row row = rows.get(idx);
            int ry = y;
            if (row.type == RowType.HEADER) {
                currentHeaderColor = headerColor(row.name);
            }
            if (ry + ROW_H > contentTop && ry < contentBottom) {
                renderRow(gui, row, x0, ry, rowW, mouseX, mouseY, idx, currentHeaderColor);
            }
            y += ROW_H;
        }
    }

    private void renderRow(GuiGraphics gui, Row row, int x, int y, int w, int mx, int my, int idx, int blockColor) {
        boolean hovered = mx >= x && mx < x + w && my >= y && my < y + ROW_H;

        if (row.type == RowType.HEADER) {
            int accent = headerColor(row.name);
            gui.fill(x, y, x + w, y + ROW_H, (accent & 0x00FFFFFF) | 0x22000000);
            gui.fill(x, y + ROW_H - 1, x + w, y + ROW_H, accent & 0xAAFFFFFF);
            gui.drawString(this.font, row.name, x + 10, y + 8, accent, false);
            gui.fill(x + 10 + this.font.width(row.name) + 6, y + 13, x + w - 10, y + 14, accent & 0x55FFFFFF);
            return;
        }
        int bg = hovered ? C_ROW_HOVER : (idx % 2 == 0 ? C_ROW_EVEN : 0x00000000);
        gui.fill(x, y, x + w, y + ROW_H - 1, bg);
        gui.fill(x, y, x + w, y + ROW_H - 1, (blockColor & 0x00FFFFFF) | 0x14000000);
        int rowLineColor = (activeTab == TAB_HUD_SETTINGS) ? 0xFF2A3E63 : C_LINE;
        gui.fill(x, y + ROW_H - 1, x + w, y + ROW_H, rowLineColor);
        gui.fill(x, y, x + 4, y + ROW_H - 1, blockColor);
        if (row.type == RowType.TOGGLE) {
            boolean on = row.stateGetter.get();
            renderSwitch(gui, x + 8, y + 7, on, blockColor);
        } else if (row.type == RowType.CYCLE) {
            int pos = row.cycleGetter != null ? row.cycleGetter.get() : 0;
            renderTripleSwitch(gui, x + 8, y + 7, pos, blockColor);
        } else if (row.type == RowType.ACTION) {
            gui.fill(x + 14, y + 11, x + 18, y + 15, blockColor);
        }
        int textX = (row.type == RowType.EDIT_BOX || row.type == RowType.LABEL) ? x + 10 : x + 34;
        gui.drawString(this.font, row.name, textX, y + 8, C_TEXT, false);
        String desc = row.descGetter != null ? row.descGetter.get() : "";
        if (!desc.isEmpty()) {
            int descX = Math.max(textX + this.font.width(row.name) + 12, x + w / 3);
            if (activeTab == TAB_HUD_SETTINGS && "Current Color".equals(row.name)) {
                renderHudColorPalettePreview(gui, descX, y + 8);
            } else {
                String trimmed = desc.length() > 60 ? desc.substring(0, 57) + "..." : desc;
                gui.drawString(this.font, trimmed, descX, y + 8, C_MUTED, false);
            }
        }
        if (row.tag != null) {
            int tagW = this.font.width(row.tag) + 8;
            int tagX = x + w - tagW - 8;
            gui.fill(tagX, y + 6, tagX + tagW, y + 18, (blockColor & 0x00FFFFFF) | 0x44000000);
            gui.drawString(this.font, row.tag, tagX + 4, y + 8, blockColor & 0xEEFFFFFF, false);
        }
    }

    private void renderSwitch(GuiGraphics gui, int x, int y, boolean on, int accentColor) {
        int w = 18, h = 10;
        int fillColor = on ? accentColor : C_SWITCH_OFF;
        gui.fill(x, y, x + w, y + h, fillColor);
        gui.fill(x - 1, y + 1, x, y + h - 1, fillColor);
        gui.fill(x + w, y + 1, x + w + 1, y + h - 1, fillColor);
        int knobX = on ? x + w - 8 : x + 2;
        gui.fill(knobX, y + 2, knobX + 6, y + h - 2, C_KNOB);
    }

    private void renderTripleSwitch(GuiGraphics gui, int x, int y, int pos, int accentColor) {
        int w = 26, h = 10;
        gui.fill(x, y, x + w, y + h, C_SWITCH_OFF);
        gui.fill(x - 1, y + 1, x, y + h - 1, C_SWITCH_OFF);
        gui.fill(x + w, y + 1, x + w + 1, y + h - 1, C_SWITCH_OFF);
        for (int i = 0; i < 3; i++) {
            int dotX = x + 3 + i * 9;
            int dotColor = (i == pos) ? 0xFFFFFFFF : 0x44FFFFFF;
            gui.fill(dotX, y + h - 3, dotX + 4, y + h - 1, dotColor);
        }
        int knobX = x + 2 + pos * 9;
        gui.fill(knobX, y + 2, knobX + 6, y + h - 4, C_KNOB);
    }

    private void renderHudPreview(GuiGraphics gui) {
        String[] hudLines = MobKillerCalculatorClient.getHudPreviewLines();
        if (hudLines.length == 0) return;
        int hx = MobKillerCalculatorClient.getHudX();
        int hy = MobKillerCalculatorClient.getHudY();
        int lsp = this.font.lineHeight + 2;
        int totalTextHeight = hudLines.length <= 0
            ? this.font.lineHeight
            : ((hudLines.length - 1) * lsp) + this.font.lineHeight;
        int topY = hy - (totalTextHeight / 2);
        boolean shadow = MobKillerCalculatorClient.isHudTextShadowEnabled();
        boolean bg = MobKillerCalculatorClient.isHudBackgroundEnabled();
        int bgCol = MobKillerCalculatorClient.getHudBackgroundColor();
        int col = MobKillerCalculatorClient.getHudColor();
        int maxW = 0;
        for (String l : hudLines) maxW = Math.max(maxW, this.font.width(l));
        gui.pose().pushMatrix();
        gui.pose().translate(hx, hy);
        gui.pose().scale(hudZoom, hudZoom);
        gui.pose().translate(-hx, -hy);
        
        for (int i = 0; i < hudLines.length; i++) {
            String line = hudLines[i];
            int ty = topY + i * lsp;
            int tw = this.font.width(line);
            int tx = hx - tw / 2;
            if (bg) gui.fill(tx - 3, ty - 1, tx + tw + 3, ty + this.font.lineHeight + 1, bgCol);
            gui.drawString(this.font, line, tx, ty, col, shadow);
        }
        int bx1 = hx - maxW / 2 - 5, bx2 = hx + maxW / 2 + 5;
        int by1 = topY - 4, by2 = topY + totalTextHeight + 3;
        int bc = 0x60FF3333;
        gui.fill(bx1, by1, bx2, by1 + 1, bc);
        gui.fill(bx1, by2 - 1, bx2, by2, bc);
        gui.fill(bx1, by1, bx1 + 1, by2, bc);
        gui.fill(bx2 - 1, by1, bx2, by2, bc);
        
        gui.pose().popMatrix();
    }

    private void renderToast(GuiGraphics gui) {
        if (!toastMessage.isEmpty() && System.currentTimeMillis() < toastUntilMs) {
            int tw = this.font.width(toastMessage);
            int tx = (this.width + SIDEBAR_W) / 2 - tw / 2;
            int ty = this.height - 20;
            gui.fill(tx - 6, ty - 3, tx + tw + 6, ty + 12, 0xCC090D18);
            gui.drawString(this.font, toastMessage, tx, ty, toastColor, false);
        }
    }
    private void reloadEmbeddedSpots() {
        embeddedSpots.clear();
        embeddedSpots.addAll(MobKillerCalculatorClient.getFarmSpotsSnapshot());
        embeddedSpots.sort(
            Comparator
                .comparing((ConfigManager.FarmSpot s) -> !s.favorite)
                .thenComparing(s -> normalizeSpotCategory(s.category))
                .thenComparing(s -> s.name == null ? "" : s.name.toLowerCase(Locale.ROOT))
        );
        spotLastSavedName = spotSelectedName;
        spotLastSavedZone = spotZoneBox.getValue() == null ? "" : spotZoneBox.getValue();
        spotLastSavedCategory = spotFormCategory;
        spotLastSavedFavorite = spotFormFavorite;
    }

    private static String embeddedSpotColumnLabel(String category) {
        return switch (normalizeSpotCategory(category)) {
            case "mythic" -> "Mythics";
            case "ingredient" -> "Ingredients";
            case "gathering" -> "Gathering";
            default -> "Spots";
        };
    }

    private int[] getEmbeddedSpotDividers(int columnsX, int columnsW) {
        int defaultDiv1 = columnsX + columnsW / 3;
        int defaultDiv2 = columnsX + (columnsW * 2) / 3;

        int div1 = columnsX + Math.round(columnsW * embeddedSpotDividerRatio1);
        int div2 = columnsX + Math.round(columnsW * embeddedSpotDividerRatio2);

        int minDiv1 = columnsX + SPOT_COL_MIN_W;
        int maxDiv2 = columnsX + columnsW - SPOT_COL_MIN_W;
        div1 = Math.max(minDiv1, Math.min(div1, maxDiv2 - SPOT_COL_MIN_W));
        div2 = Math.max(div1 + SPOT_COL_MIN_W, Math.min(div2, maxDiv2));

        if (Math.abs(div1 - defaultDiv1) <= SPOT_DIVIDER_SNAP_PX) {
            div1 = defaultDiv1;
        }
        if (Math.abs(div2 - defaultDiv2) <= SPOT_DIVIDER_SNAP_PX) {
            div2 = defaultDiv2;
        }

        embeddedSpotDividerRatio1 = (div1 - columnsX) / (float) columnsW;
        embeddedSpotDividerRatio2 = (div2 - columnsX) / (float) columnsW;
        return new int[]{div1, div2};
    }

    private void clearEmbeddedSpotSelection() {
        spotSelectedName = "";
        spotNameBox.setValue("");
        spotZoneBox.setValue("");
        spotFormFavorite = false;
        spotLastSavedName = "";
        spotLastSavedZone = "";
        spotLastSavedCategory = spotFormCategory;
        spotLastSavedFavorite = false;
    }

    private void renderEmbeddedSpotManager(GuiGraphics gui, int mouseX, int mouseY) {
        int contentX = SIDEBAR_W + 1;
        int contentTop = HEADER_H + 2;
        int contentW = this.width - SIDEBAR_W - 2;
        int columnsTop = contentTop + 10;
        int columnsBottom = this.height - 130;
        int columnsX = contentX + 12;
        int columnsW = contentW - 24;

        gui.fill(columnsX, columnsTop, columnsX + columnsW, columnsBottom, C_CONTENT);
        gui.fill(columnsX, columnsTop, columnsX + columnsW, columnsTop + 1, C_LINE);
        gui.fill(columnsX, columnsBottom - 1, columnsX + columnsW, columnsBottom, C_LINE);

        int[] dividers = getEmbeddedSpotDividers(columnsX, columnsW);
        int[] colX = new int[]{columnsX, dividers[0], dividers[1]};
        int[] colW = new int[]{dividers[0] - columnsX, dividers[1] - dividers[0], (columnsX + columnsW) - dividers[1]};

        for (int c = 0; c < 3; c++) {
            int columnX = colX[c];
            int columnW = colW[c];
            String category = embeddedSpotColumnOrder[c];
            String label = embeddedSpotColumnLabel(category);
            if (c > 0) gui.fill(columnX, columnsTop, columnX + 1, columnsBottom, C_LINE);

            gui.drawString(this.font, label, columnX + 10, columnsTop + 8, spotCategoryColor(category), false);
            int totalBtnX = columnX + columnW - SPOT_COL_TOTAL_W - 10;
            gui.fill(totalBtnX, columnsTop + 6, totalBtnX + SPOT_COL_TOTAL_W, columnsTop + 6 + SPOT_COL_TOTAL_H, 0x332A2F3D);
            gui.fill(totalBtnX, columnsTop + 6, totalBtnX + 2, columnsTop + 6 + SPOT_COL_TOTAL_H, spotCategoryColor(category));
            gui.drawString(this.font, "Total", totalBtnX + 8, columnsTop + 9, C_TEXT, false);

            List<ConfigManager.FarmSpot> spots = getEmbeddedSpotsByCategory(category);
            int listTop = columnsTop + 24;
            int listBottom = columnsBottom - 8;
            int listH = listBottom - listTop;
            int rowX = columnX + 8;
            int rowW = columnW - 8 - 8 - SPOT_SCROLLBAR_W - 4;

            int contentH = Math.max(0, spots.size() * (SPOT_ROW_H + SPOT_ROW_GAP) - SPOT_ROW_GAP);
            int maxScroll = Math.max(0, contentH - listH);
            spotColumnScroll[c] = Math.max(0, Math.min(spotColumnScroll[c], maxScroll));

            int y = listTop - spotColumnScroll[c];
            for (ConfigManager.FarmSpot spot : spots) {
                if (y + SPOT_ROW_H >= listTop && y <= listBottom) {
                    int rowColor = (spotCategoryColor(category) & 0x00FFFFFF) | 0x22000000;
                    boolean selected = isEmbeddedSpotSelected(spot);
                    boolean hovered = isInRect(mouseX, mouseY, rowX, y, rowW, SPOT_ROW_H);

                    gui.fill(rowX, y, rowX + rowW, y + SPOT_ROW_H, rowColor);
                    gui.fill(rowX, y, rowX + 2, y + SPOT_ROW_H, spotCategoryColor(category));
                    gui.fill(rowX, y + SPOT_ROW_H - 1, rowX + rowW, y + SPOT_ROW_H, C_LINE);
                    if (hovered) gui.fill(rowX, y, rowX + rowW, y + SPOT_ROW_H, 0x18000000);
                    if (selected) {
                        gui.fill(rowX, y, rowX + rowW, y + 1, C_ACCENT);
                        gui.fill(rowX + rowW - 1, y, rowX + rowW, y + SPOT_ROW_H, C_ACCENT);
                    }

                    int startX = rowX + rowW - SPOT_START_BTN_W - 2;
                    int pinX = startX - SPOT_PIN_BTN_W - 4;
                    int textW = Math.max(14, pinX - rowX - 8);
                    String title = compactText(spot.name, Math.max(10, textW / 6));
                    String zone = compactText(spot.zone == null || spot.zone.isEmpty() ? "No zone" : spot.zone, Math.max(8, textW / 7));
                    if (spot.favorite) {
                        drawAnimatedFavoriteText(gui, "★ " + title, rowX + 6, y + 6, y + c * 41);
                    } else {
                        gui.drawString(this.font, title, rowX + 6, y + 6, C_TEXT, false);
                    }
                    gui.drawString(this.font, zone, rowX + 6, y + 18, C_MUTED, false);

                    int startBg = hovered ? 0x33435D7A : 0x2A2A3A4D;
                    gui.fill(pinX, y + 8, pinX + SPOT_PIN_BTN_W, y + SPOT_ROW_H - 8, 0x2A2A3A4D);
                    gui.drawString(this.font, "📍", pinX + 2, y + 10, C_PIN, false);

                    gui.fill(startX, y + 6, startX + SPOT_START_BTN_W, y + SPOT_ROW_H - 6, startBg);
                    boolean isActiveSpot = MobKillerCalculatorClient.isSessionRunning()
                        && spot.name.equalsIgnoreCase(MobKillerCalculatorClient.getActiveFarmSpotName());
                    gui.fill(startX, y + 6, startX + 2, y + SPOT_ROW_H - 6, isActiveSpot ? C_RED : C_ACCENT);
                    gui.drawString(this.font, isActiveSpot ? "■" : "▶", startX + 5, y + 12, isActiveSpot ? C_RED : C_TEXT, false);
                }
                y += SPOT_ROW_H + SPOT_ROW_GAP;
            }

            int trackX = rowX + rowW + 4;
            gui.fill(trackX, listTop, trackX + SPOT_SCROLLBAR_W, listBottom, 0x221C263D);
            if (maxScroll > 0) {
                int trackH = listH;
                int thumbH = Math.max(18, (int) ((long) listH * trackH / Math.max(1, contentH)));
                int thumbRange = trackH - thumbH;
                int thumbY = listTop + (thumbRange > 0 ? (spotColumnScroll[c] * thumbRange / maxScroll) : 0);
                int thumbColor = draggingSpotScrollbarColumn == c ? C_ACCENT : C_MUTED;
                gui.fill(trackX, thumbY, trackX + SPOT_SCROLLBAR_W, thumbY + thumbH, thumbColor);
            }
        }

        if (draggingSpotRow && draggingSpotMouseX > 0 && draggingSpotMouseY > 0) {
            for (int c = 0; c < 3; c++) {
                if (draggingSpotMouseX >= colX[c] && draggingSpotMouseX <= colX[c] + colW[c]) {
                    gui.fill(colX[c] + 2, columnsTop + 2, colX[c] + colW[c] - 2, columnsBottom - 2, 0x1128D2A0);
                    break;
                }
            }
            int ghostW = 180;
            int ghostH = SPOT_ROW_H;
            int ghostX = draggingSpotMouseX + 8;
            int ghostY = draggingSpotMouseY - (ghostH / 2);
            if (ghostX + ghostW > this.width - 8) ghostX = this.width - ghostW - 8;
            if (ghostY < columnsTop + 2) ghostY = columnsTop + 2;
            if (ghostY + ghostH > columnsBottom - 2) ghostY = columnsBottom - ghostH - 2;
            gui.fill(ghostX, ghostY, ghostX + ghostW, ghostY + ghostH, 0xD0121E35);
            gui.fill(ghostX, ghostY, ghostX + 2, ghostY + ghostH, C_ACCENT);
            gui.drawString(this.font, "Move: " + compactText(draggingSpotName, 20), ghostX + 8, ghostY + 12, C_TEXT, false);
        }

        for (int d = 0; d < 2; d++) {
            int dx = dividers[d];
            boolean hoverDivider = isInRect(mouseX, mouseY, dx - 4, columnsTop + 2, 8, columnsBottom - columnsTop - 4);
            int dividerColor = (draggingSpotDividerIndex == d || hoverDivider) ? C_ACCENT : C_LINE;
            gui.fill(dx - (SPOT_DIVIDER_W / 2), columnsTop + 2, dx - (SPOT_DIVIDER_W / 2) + SPOT_DIVIDER_W, columnsBottom - 2, dividerColor);
        }

        int panelY = this.height - 120;
        gui.fill(contentX, panelY, this.width, this.height, C_CONTENT);
        gui.fill(contentX, panelY, this.width, panelY + 1, C_LINE);

        gui.drawString(this.font, "Name", contentX + 18, panelY + 8, C_MUTED, false);
        gui.drawString(this.font, "Zone", contentX + 248, panelY + 8, C_MUTED, false);
        if (spotSelectedName == null || spotSelectedName.isEmpty()) {
            gui.drawString(this.font, "Category (new spot)", contentX + 480, panelY + 8, C_MUTED, false);
            String categoryLabel = switch (normalizeSpotCategory(spotFormCategory)) {
                case "mythic" -> "Mythic";
                case "gathering" -> "Gathering";
                default -> "Ingredient";
            };
            int categoryBtnX = contentX + 480;
            int categoryBtnY = panelY + 22;
            gui.fill(categoryBtnX, categoryBtnY, categoryBtnX + SPOT_CATEGORY_BTN_W, categoryBtnY + SPOT_CATEGORY_BTN_H, 0x332A2F3D);
            gui.fill(categoryBtnX, categoryBtnY, categoryBtnX + 2, categoryBtnY + SPOT_CATEGORY_BTN_H, spotCategoryColor(spotFormCategory));
            gui.drawString(this.font, categoryLabel + "  <->", categoryBtnX + 8, categoryBtnY + 5, C_TEXT, false);
        }

        final int actionY = panelY + 44;
        final int actionH = 20;
        drawEmbeddedButton(gui, contentX + 18, actionY, 108, actionH, spotSelectedName.isEmpty() ? "Create Here" : "Update", C_ACCENT);
        drawEmbeddedButton(gui, contentX + 132, actionY, 96, actionH, "Delete", C_RED);

        int favColor = spotFormFavorite ? 0x443A2D0A : 0x332A2F3D;
        gui.fill(contentX + 236, actionY, contentX + 316, actionY + actionH, favColor);
        gui.fill(contentX + 236, actionY, contentX + 238, actionY + actionH, 0xFFFFD166);
        gui.drawString(this.font, spotFormFavorite ? "Favorite" : "Normal", contentX + 244, actionY + 6, C_TEXT, false);

        boolean selectedAuto = false;
        if (!spotSelectedName.isEmpty()) {
            ConfigManager.FarmSpot selectedSpot = MobKillerCalculatorClient.getFarmSpotByName(spotSelectedName);
            selectedAuto = selectedSpot != null && selectedSpot.autoPresetEnabled;
        }
        int autoX = contentX + 322;
        int autoColor = selectedAuto ? 0x2D193D29 : 0x332A2F3D;
        gui.fill(autoX, actionY, autoX + 126, actionY + actionH, autoColor);
        gui.fill(autoX, actionY, autoX + 2, actionY + actionH, selectedAuto ? C_ACCENT : C_MUTED);
        gui.drawString(this.font, selectedAuto ? "Auto-preset: ON" : "Auto-preset: OFF", autoX + 8, actionY + 6, C_TEXT, false);

        renderEmbeddedSpotNotice(gui, contentX, contentTop);
    }

    private boolean handleEmbeddedSpotClick(int mx, int my) {
        if (handleEmbeddedColumnTotalClick(mx, my)) return true;
        if (tryStartEmbeddedSpotDividerDrag(mx, my)) return true;
        if (tryStartEmbeddedSpotColumnDrag(mx, my)) return true;
        if (tryStartEmbeddedSpotScrollbarDrag(mx, my)) return true;
        if (handleEmbeddedSpotRowClick(mx, my)) return true;

        int contentX = SIDEBAR_W + 1;
        int panelY = this.height - 120;
        if (isInRect(mx, my, contentX + 236, panelY + 44, 80, 20)) { spotFormFavorite = !spotFormFavorite; return true; }
        if ((spotSelectedName == null || spotSelectedName.isEmpty())
                && isInRect(mx, my, contentX + 480, panelY + 22, SPOT_CATEGORY_BTN_W, SPOT_CATEGORY_BTN_H)) {
            cycleSpotCreationCategory();
            return true;
        }
        if (isInRect(mx, my, contentX + 18, panelY + 44, 108, 20)) return spotSelectedName.isEmpty() ? onEmbeddedCreateSpot() : onEmbeddedUpdateSpot();
        if (isInRect(mx, my, contentX + 132, panelY + 44, 96, 20)) return onEmbeddedDeleteSpot();
        if (isInRect(mx, my, contentX + 322, panelY + 44, 126, 20)) {
            if (spotSelectedName == null || spotSelectedName.isEmpty()) {
                pushEmbeddedSpotNotice("Select a spot first", C_RED);
                return true;
            }
            boolean ok = MobKillerCalculatorClient.toggleFarmSpotAutoPreset(spotSelectedName);
            if (!ok) {
                pushEmbeddedSpotNotice("Could not change preset mode", C_RED);
                return true;
            }
            reloadEmbeddedSpots();
            pushEmbeddedSpotNotice("Auto-preset updated", C_ACCENT);
            return true;
        }

        int contentTop = HEADER_H + 2;
        int contentW = this.width - SIDEBAR_W - 2;
        int columnsTop = contentTop + 10;
        int columnsBottom = this.height - 130;
        int columnsX = contentX + 12;
        int columnsW = contentW - 24;
        if (isInRect(mx, my, columnsX, columnsTop, columnsW, columnsBottom - columnsTop)) {
            clearEmbeddedSpotSelection();
            return true;
        }

        return false;
    }

    private boolean handleEmbeddedSpotScroll(int mx, int my, double scrollY) {
        int contentX = SIDEBAR_W + 1;
        int contentTop = HEADER_H + 2;
        int contentW = this.width - SIDEBAR_W - 2;
        int columnsTop = contentTop + 10;
        int columnsBottom = this.height - 130;
        int columnsX = contentX + 12;
        int columnsW = contentW - 24;
        int[] dividers = getEmbeddedSpotDividers(columnsX, columnsW);
        int[] colX = new int[]{columnsX, dividers[0], dividers[1]};
        int[] colW = new int[]{dividers[0] - columnsX, dividers[1] - dividers[0], (columnsX + columnsW) - dividers[1]};

        for (int c = 0; c < 3; c++) {
            int columnX = colX[c];
            int columnW = colW[c];
            if (mx < columnX || mx > columnX + columnW || my < columnsTop || my > columnsBottom) continue;
            int listTop = columnsTop + 24;
            int listBottom = columnsBottom - 8;
            int listH = listBottom - listTop;
            List<ConfigManager.FarmSpot> spots = getEmbeddedSpotsByCategory(embeddedSpotColumnOrder[c]);
            int contentH = Math.max(0, spots.size() * (SPOT_ROW_H + SPOT_ROW_GAP) - SPOT_ROW_GAP);
            int maxScroll = Math.max(0, contentH - listH);
            spotColumnScroll[c] = (int) Math.max(0, Math.min(spotColumnScroll[c] - scrollY * 16, maxScroll));
            return true;
        }
        return false;
    }

    private void handleEmbeddedSpotScrollbarDrag(int mouseY) {
        int c = draggingSpotScrollbarColumn;
        if (c < 0 || c > 2) return;

        int contentX = SIDEBAR_W + 1;
        int contentTop = HEADER_H + 2;
        int contentW = this.width - SIDEBAR_W - 2;
        int columnsTop = contentTop + 10;
        int columnsBottom = this.height - 130;
        int columnsX = contentX + 12;
        int columnsW = contentW - 24;
        int[] dividers = getEmbeddedSpotDividers(columnsX, columnsW);
        int[] colX = new int[]{columnsX, dividers[0], dividers[1]};
        int[] colW = new int[]{dividers[0] - columnsX, dividers[1] - dividers[0], (columnsX + columnsW) - dividers[1]};
        int columnX = colX[c];
        int columnW = colW[c];

        int listTop = columnsTop + 24;
        int listBottom = columnsBottom - 8;
        int listH = listBottom - listTop;
        List<ConfigManager.FarmSpot> spots = getEmbeddedSpotsByCategory(embeddedSpotColumnOrder[c]);
        int contentH = Math.max(0, spots.size() * (SPOT_ROW_H + SPOT_ROW_GAP) - SPOT_ROW_GAP);
        int maxScroll = Math.max(0, contentH - listH);
        if (maxScroll <= 0) {
            spotColumnScroll[c] = 0;
            return;
        }

        int trackH = listH;
        int thumbH = Math.max(18, (int) ((long) listH * trackH / Math.max(1, contentH)));
        int thumbRange = Math.max(1, trackH - thumbH);
        int rawThumbY = mouseY - draggingSpotScrollbarOffsetY;
        int clampedThumbY = Math.max(listTop, Math.min(rawThumbY, listTop + trackH - thumbH));
        int thumbPos = clampedThumbY - listTop;
        spotColumnScroll[c] = Math.max(0, Math.min((thumbPos * maxScroll) / thumbRange, maxScroll));
    }

    private boolean handleEmbeddedColumnTotalClick(int mx, int my) {
        int contentX = SIDEBAR_W + 1;
        int contentTop = HEADER_H + 2;
        int contentW = this.width - SIDEBAR_W - 2;
        int columnsTop = contentTop + 10;
        int columnsX = contentX + 12;
        int columnsW = contentW - 24;
        int[] dividers = getEmbeddedSpotDividers(columnsX, columnsW);
        int[] colX = new int[]{columnsX, dividers[0], dividers[1]};
        int[] colW = new int[]{dividers[0] - columnsX, dividers[1] - dividers[0], (columnsX + columnsW) - dividers[1]};

        for (int c = 0; c < 3; c++) {
            int columnX = colX[c];
            int columnW = colW[c];
            int totalBtnX = columnX + columnW - SPOT_COL_TOTAL_W - 10;
            if (isInRect(mx, my, totalBtnX, columnsTop + 6, SPOT_COL_TOTAL_W, SPOT_COL_TOTAL_H)) {
                this.minecraft.setScreen(new SpotTotalsScreen(this, embeddedSpotColumnOrder[c]));
                return true;
            }
        }
        return false;
    }

    private boolean tryStartEmbeddedSpotDividerDrag(int mx, int my) {
        int contentX = SIDEBAR_W + 1;
        int contentTop = HEADER_H + 2;
        int contentW = this.width - SIDEBAR_W - 2;
        int columnsTop = contentTop + 10;
        int columnsBottom = this.height - 130;
        int columnsX = contentX + 12;
        int columnsW = contentW - 24;
        int[] dividers = getEmbeddedSpotDividers(columnsX, columnsW);

        for (int d = 0; d < 2; d++) {
            int dx = dividers[d];
            if (isInRect(mx, my, dx - 5, columnsTop + 2, 10, columnsBottom - columnsTop - 4)) {
                draggingSpotDividerIndex = d;
                return true;
            }
        }
        return false;
    }

    private boolean tryStartEmbeddedSpotColumnDrag(int mx, int my) {
        int contentX = SIDEBAR_W + 1;
        int contentTop = HEADER_H + 2;
        int contentW = this.width - SIDEBAR_W - 2;
        int columnsTop = contentTop + 10;
        int columnsX = contentX + 12;
        int columnsW = contentW - 24;
        int[] dividers = getEmbeddedSpotDividers(columnsX, columnsW);
        int[] colX = new int[]{columnsX, dividers[0], dividers[1]};
        int[] colW = new int[]{dividers[0] - columnsX, dividers[1] - dividers[0], (columnsX + columnsW) - dividers[1]};

        for (int c = 0; c < 3; c++) {
            int columnX = colX[c];
            int columnW = colW[c];
            if (isInRect(mx, my, columnX + 8, columnsTop + 4, Math.max(20, columnW - 16), 16)) {
                draggingSpotColumnFrom = c;
                return true;
            }
        }
        return false;
    }

    private boolean tryStartEmbeddedSpotScrollbarDrag(int mx, int my) {
        int contentX = SIDEBAR_W + 1;
        int contentTop = HEADER_H + 2;
        int contentW = this.width - SIDEBAR_W - 2;
        int columnsTop = contentTop + 10;
        int columnsBottom = this.height - 130;
        int columnsX = contentX + 12;
        int columnsW = contentW - 24;
        int[] dividers = getEmbeddedSpotDividers(columnsX, columnsW);
        int[] colX = new int[]{columnsX, dividers[0], dividers[1]};
        int[] colW = new int[]{dividers[0] - columnsX, dividers[1] - dividers[0], (columnsX + columnsW) - dividers[1]};

        for (int c = 0; c < 3; c++) {
            int columnX = colX[c];
            int columnW = colW[c];
            int listTop = columnsTop + 24;
            int listBottom = columnsBottom - 8;
            int listH = listBottom - listTop;
            int rowX = columnX + 8;
            int rowW = columnW - 8 - 8 - SPOT_SCROLLBAR_W - 4;
            int trackX = rowX + rowW + 4;
            List<ConfigManager.FarmSpot> spots = getEmbeddedSpotsByCategory(embeddedSpotColumnOrder[c]);
            int contentH = Math.max(0, spots.size() * (SPOT_ROW_H + SPOT_ROW_GAP) - SPOT_ROW_GAP);
            int maxScroll = Math.max(0, contentH - listH);
            if (maxScroll <= 0) continue;
            int trackH = listH;
            int thumbH = Math.max(18, (int) ((long) listH * trackH / Math.max(1, contentH)));
            int thumbRange = trackH - thumbH;
            int thumbY = listTop + (thumbRange > 0 ? (spotColumnScroll[c] * thumbRange / maxScroll) : 0);
            if (isInRect(mx, my, trackX, thumbY, SPOT_SCROLLBAR_W, thumbH)) {
                draggingSpotScrollbarColumn = c;
                draggingSpotScrollbarOffsetY = my - thumbY;
                return true;
            }
        }
        return false;
    }

    private boolean handleEmbeddedSpotRowClick(int mx, int my) {
        int contentX = SIDEBAR_W + 1;
        int contentTop = HEADER_H + 2;
        int contentW = this.width - SIDEBAR_W - 2;
        int columnsTop = contentTop + 10;
        int columnsBottom = this.height - 130;
        int columnsX = contentX + 12;
        int columnsW = contentW - 24;
        int[] dividers = getEmbeddedSpotDividers(columnsX, columnsW);
        int[] colX = new int[]{columnsX, dividers[0], dividers[1]};
        int[] colW = new int[]{dividers[0] - columnsX, dividers[1] - dividers[0], (columnsX + columnsW) - dividers[1]};

        for (int c = 0; c < 3; c++) {
            int columnX = colX[c];
            int columnW = colW[c];
            List<ConfigManager.FarmSpot> spots = getEmbeddedSpotsByCategory(embeddedSpotColumnOrder[c]);
            int listTop = columnsTop + 24;
            int listBottom = columnsBottom - 8;
            int listH = listBottom - listTop;
            int rowX = columnX + 8;
            int rowW = columnW - 8 - 8 - SPOT_SCROLLBAR_W - 4;
            int contentH = Math.max(0, spots.size() * (SPOT_ROW_H + SPOT_ROW_GAP) - SPOT_ROW_GAP);
            int maxScroll = Math.max(0, contentH - listH);
            spotColumnScroll[c] = Math.max(0, Math.min(spotColumnScroll[c], maxScroll));

            int y = listTop - spotColumnScroll[c];
            for (ConfigManager.FarmSpot spot : spots) {
                int startX = rowX + rowW - SPOT_START_BTN_W - 2;
                int pinX = startX - SPOT_PIN_BTN_W - 4;
                if (isInRect(mx, my, pinX, y + 8, SPOT_PIN_BTN_W, SPOT_ROW_H - 16)) {
                    spotSelectedName = spot.name == null ? "" : spot.name;
                    pingEmbeddedSpot();
                    return true;
                }
                if (isInRect(mx, my, startX, y + 6, SPOT_START_BTN_W, SPOT_ROW_H - 12)) {
                    String name = spot.name == null ? "" : spot.name;
                    if (name.isEmpty()) {
                        pushEmbeddedSpotNotice("Spot name missing", C_RED);
                        return true;
                    }
                    String activeSpotName = MobKillerCalculatorClient.getActiveFarmSpotName();
                    boolean isActiveRunningSpot = MobKillerCalculatorClient.isSessionRunning()
                        && activeSpotName != null
                        && name.equalsIgnoreCase(activeSpotName);

                    if (isActiveRunningSpot) {
                        MobKillerCalculatorClient.stopSession();
                        pushEmbeddedSpotNotice("Session stopped", C_ACCENT);
                        return true;
                    }

                    if (MobKillerCalculatorClient.isSessionRunning()) {
                        MobKillerCalculatorClient.stopSession();
                    }
                    boolean started = MobKillerCalculatorClient.startSessionOnSpot(name);
                    if (started) {
                        spotSelectedName = name;
                        spotNameBox.setValue(name);
                        spotZoneBox.setValue(spot.zone == null ? "" : spot.zone);
                        spotFormCategory = normalizeSpotCategory(spot.category);
                        spotFormFavorite = spot.favorite;
                        this.minecraft.setScreen(null);
                    } else {
                        pushEmbeddedSpotNotice("Could not start session", C_RED);
                    }
                    return true;
                }

                if (isInRect(mx, my, rowX, y, rowW, SPOT_ROW_H)) {
                    String name = spot.name == null ? "" : spot.name;
                    draggingSpotName = name;
                    draggingSpotSourceCategory = normalizeSpotCategory(spot.category);
                    draggingSpotStartX = mx;
                    draggingSpotStartY = my;
                    draggingSpotMouseX = mx;
                    draggingSpotMouseY = my;
                    draggingSpotRow = false;
                    long now = System.currentTimeMillis();
                    if (name.equals(spotLastClicked) && now - spotLastClickTime < 300) {
                        this.minecraft.setScreen(new SpotDetailsScreen(this, name));
                        spotLastClickTime = 0L;
                        spotLastClicked = "";
                        return true;
                    }
                    spotLastClickTime = now;
                    spotLastClicked = name;
                    spotSelectedName = name;
                    spotNameBox.setValue(name);
                    spotZoneBox.setValue(spot.zone == null ? "" : spot.zone);
                    spotFormCategory = normalizeSpotCategory(spot.category);
                    spotFormFavorite = spot.favorite;
                    spotLastSavedName = spotSelectedName;
                    spotLastSavedZone = spotZoneBox.getValue() == null ? "" : spotZoneBox.getValue();
                    spotLastSavedCategory = spotFormCategory;
                    spotLastSavedFavorite = spotFormFavorite;
                    return true;
                }
                y += SPOT_ROW_H + SPOT_ROW_GAP;
            }
        }
        return false;
    }

    private String getSelectedSpotCategory() {
        if (!spotSelectedName.isEmpty()) {
            ConfigManager.FarmSpot spot = MobKillerCalculatorClient.getFarmSpotByName(spotSelectedName);
            if (spot != null) {
                return normalizeSpotCategory(spot.category);
            }
        }
        String fallback = normalizeSpotCategory(spotFormCategory);
        return fallback.isEmpty() ? "ingredient" : fallback;
    }

    private void cycleSpotCreationCategory() {
        String normalized = normalizeSpotCategory(spotFormCategory);
        if ("mythic".equals(normalized)) {
            spotFormCategory = "ingredient";
        } else if ("ingredient".equals(normalized)) {
            spotFormCategory = "gathering";
        } else {
            spotFormCategory = "mythic";
        }
    }

    private void syncSelectedHudColorIndex() {
        for (int i = 0; i < HUD_COLOR_VALUES.length; i++) {
            if (MobKillerCalculatorClient.hudColor == HUD_COLOR_VALUES[i]) {
                selectedColorIndex = i;
                return;
            }
        }
        selectedColorIndex = 0;
    }

    private String getCurrentHudColorName() {
        syncSelectedHudColorIndex();
        return HUD_COLOR_NAMES[selectedColorIndex];
    }

    private String getHudColorPalettePreview() {
        return String.join(" | ", HUD_COLOR_NAMES);
    }

    private void renderHudColorPalettePreview(GuiGraphics gui, int x, int y) {
        syncSelectedHudColorIndex();
        int drawX = x;
        for (int i = 0; i < HUD_COLOR_NAMES.length; i++) {
            if (i > 0) {
                String sep = " | ";
                gui.drawString(this.font, sep, drawX, y, C_MUTED, false);
                drawX += this.font.width(sep);
            }

            String name = HUD_COLOR_NAMES[i];
            int color = (i == selectedColorIndex) ? (0xFF000000 | HUD_COLOR_VALUES[i]) : C_MUTED;
            gui.drawString(this.font, name, drawX, y, color, false);
            drawX += this.font.width(name);
        }
    }

    private void shiftHudColorSelection(int delta) {
        syncSelectedHudColorIndex();
        int len = HUD_COLOR_VALUES.length;
        selectedColorIndex = (selectedColorIndex + delta + len) % len;
        MobKillerCalculatorClient.setHudColor(HUD_COLOR_VALUES[selectedColorIndex]);
    }

    private boolean onEmbeddedMoveSpotToCategory(String spotName, String targetCategory) {
        if (spotName == null || spotName.isEmpty()) return false;
        ConfigManager.FarmSpot spot = MobKillerCalculatorClient.getFarmSpotByName(spotName);
        if (spot == null) return false;
        String normalizedTarget = normalizeSpotCategory(targetCategory);
        String normalizedCurrent = normalizeSpotCategory(spot.category);
        if (normalizedTarget.isEmpty() || normalizedTarget.equals(normalizedCurrent)) return true;

        boolean ok = MobKillerCalculatorClient.updateFarmSpot(
            spot.name,
            spot.name,
            spot.zone == null ? "" : spot.zone,
            normalizedTarget,
            spot.favorite
        );
        if (!ok) {
            pushEmbeddedSpotNotice("Could not move spot", C_RED);
            return false;
        }

        if (!spotSelectedName.isEmpty() && spotSelectedName.equalsIgnoreCase(spot.name)) {
            spotFormCategory = normalizedTarget;
            spotLastSavedCategory = normalizedTarget;
        }
        reloadEmbeddedSpots();
        pushEmbeddedSpotNotice("Spot moved to " + embeddedSpotColumnLabel(normalizedTarget), C_ACCENT);
        return true;
    }

    private boolean onEmbeddedCreateSpot() {
        String name = spotNameBox.getValue() == null ? "" : spotNameBox.getValue().trim();
        String zone = spotZoneBox.getValue() == null ? "" : spotZoneBox.getValue().trim();
        boolean ok = MobKillerCalculatorClient.saveCurrentPositionAsSpot(spotFormCategory, name, zone, spotFormFavorite);
        if (!ok) {
            pushEmbeddedSpotNotice("Could not create the spot", C_RED);
            return true;
        }
        reloadEmbeddedSpots();
        if (!name.isEmpty()) spotSelectedName = name;
        pushEmbeddedSpotNotice("Spot created", C_ACCENT);
        return true;
    }

    private boolean onEmbeddedUpdateSpot() {
        if (spotSelectedName == null || spotSelectedName.isEmpty()) {
            pushEmbeddedSpotNotice("Select a spot first", C_RED);
            return true;
        }
        String currentName = spotNameBox.getValue() == null ? "" : spotNameBox.getValue().trim();
        String currentZone = spotZoneBox.getValue() == null ? "" : spotZoneBox.getValue().trim();
        if (currentName.isEmpty()) {
            pushEmbeddedSpotNotice("Name cannot be empty", C_RED);
            return true;
        }
        boolean ok = MobKillerCalculatorClient.updateFarmSpot(spotLastSavedName, currentName, currentZone, spotFormCategory, spotFormFavorite);
        if (!ok) {
            pushEmbeddedSpotNotice("Could not update spot", C_RED);
            return true;
        }
        spotSelectedName = currentName;
        spotLastSavedName = currentName;
        spotLastSavedZone = currentZone;
        spotLastSavedCategory = spotFormCategory;
        spotLastSavedFavorite = spotFormFavorite;
        reloadEmbeddedSpots();
        pushEmbeddedSpotNotice("Spot updated", C_ACCENT);
        return true;
    }

    private boolean onEmbeddedDeleteSpot() {
        if (spotSelectedName == null || spotSelectedName.isEmpty()) {
            pushEmbeddedSpotNotice("Select a spot first", C_RED);
            return true;
        }
        boolean ok = MobKillerCalculatorClient.deleteFarmSpot(spotSelectedName);
        if (!ok) {
            pushEmbeddedSpotNotice("Could not delete spot", C_RED);
            return true;
        }
        spotSelectedName = "";
        spotNameBox.setValue("");
        spotZoneBox.setValue("");
        reloadEmbeddedSpots();
        pushEmbeddedSpotNotice("Spot deleted", C_ACCENT);
        return true;
    }

    private void pingEmbeddedSpot() {
        ConfigManager.FarmSpot spot = MobKillerCalculatorClient.getFarmSpotByName(spotSelectedName);
        if (spot == null || this.minecraft == null || this.minecraft.player == null) return;

        String clipboardCoords = spot.x + " " + spot.y + " " + spot.z;
        String compassCoords = spot.x + " " + spot.z;
        this.minecraft.keyboardHandler.setClipboard(clipboardCoords);
        try {
            this.minecraft.player.connection.sendCommand("compass " + compassCoords);
        } catch (Exception ignored) {
        }
        this.minecraft.player.displayClientMessage(Component.literal("§6Pinned: " + clipboardCoords + " §7(copied + /compass " + compassCoords + ")"), true);
        pushEmbeddedSpotNotice("Pinged and copied: " + clipboardCoords, C_ACCENT);
    }

    private void renderEmbeddedSpotNotice(GuiGraphics gui, int contentX, int contentTop) {
        if (spotNotice.isEmpty() || System.currentTimeMillis() > spotNoticeUntil) return;
        int tw = this.font.width(spotNotice);
        int x = contentX + (this.width - contentX) / 2 - tw / 2;
        int y = contentTop + 2;
        gui.fill(x - 6, y - 2, x + tw + 6, y + 10, 0xCC090D18);
        gui.drawString(this.font, spotNotice, x, y, spotNoticeColor, false);
    }

    private void pushEmbeddedSpotNotice(String msg, int color) {
        spotNotice = msg == null ? "" : msg;
        spotNoticeColor = color;
        spotNoticeUntil = System.currentTimeMillis() + 2200L;
    }

    private List<ConfigManager.FarmSpot> getEmbeddedSpotsByCategory(String category) {
        String expected = normalizeSpotCategory(category);
        List<ConfigManager.FarmSpot> out = new ArrayList<>();
        for (ConfigManager.FarmSpot spot : embeddedSpots) {
            if (spot == null) continue;
            if (!normalizeSpotCategory(spot.category).equals(expected)) continue;
            out.add(spot);
        }
        return out;
    }

    private boolean isEmbeddedSpotSelected(ConfigManager.FarmSpot spot) {
        if (spot == null || spot.name == null || spotSelectedName == null) return false;
        return spot.name.equalsIgnoreCase(spotSelectedName);
    }

    private void drawEmbeddedButton(GuiGraphics gui, int x, int y, int w, int h, String text, int accent) {
        gui.fill(x, y, x + w, y + h, 0x332A2F3D);
        gui.fill(x, y, x + 2, y + h, accent);
        gui.drawString(this.font, text, x + 8, y + 6, C_TEXT, false);
    }

    private static boolean isInRect(int mx, int my, int x, int y, int w, int h) {
        return mx >= x && mx <= x + w && my >= y && my <= y + h;
    }

    private static String compactText(String text, int max) {
        if (text == null) return "";
        if (text.length() <= max) return text;
        return text.substring(0, Math.max(1, max - 1)) + "~";
    }

    private static String normalizeSpotCategory(String category) {
        if (category == null) return "";
        String c = category.trim().toLowerCase(Locale.ROOT);
        if (c.startsWith("myth")) return "mythic";
        if (c.startsWith("ingred")) return "ingredient";
        if (c.startsWith("gather")) return "gathering";
        return c;
    }

    private static int spotCategoryColor(String category) {
        return switch (normalizeSpotCategory(category)) {
            case "mythic" -> C_MYTHIC;
            case "ingredient" -> C_INGR;
            case "gathering" -> C_GATHER;
            default -> C_ACCENT;
        };
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

    private void renderDragOverlay(GuiGraphics gui) {
        gui.fill(0, 0, this.width, this.height, 0xD0000000);
        int hx = MobKillerCalculatorClient.getHudX(), hy = MobKillerCalculatorClient.getHudY();
        int cx = this.width / 2, cy = this.height / 2;
        int dash = 0xA0FF3333;
        for (int xx = Math.min(hx, cx); xx < Math.max(hx, cx); xx += 9) gui.fill(xx, hy, Math.min(xx + 5, Math.max(hx, cx)), hy + 1, dash);
        for (int yy = Math.min(hy, cy); yy < Math.max(hy, cy); yy += 9) gui.fill(hx, yy, hx + 1, Math.min(yy + 5, Math.max(hy, cy)), dash);
        gui.fill(cx - 10, cy, cx + 10, cy + 1, 0x80FFFFFF);
        gui.fill(cx, cy - 10, cx + 1, cy + 10, 0x80FFFFFF);
        renderHudPreview(gui);
        int dx = hx - cx, dy = hy - cy;
        gui.drawCenteredString(this.font, "X:" + (dx>=0?"+":"") + dx + " Y:" + (dy>=0?"+":"") + dy, cx, this.height - 20, 0xFFAAAAAA);
        gui.drawCenteredString(this.font, "Drag to reposition \u2022 Release to confirm", cx, this.height - 34, C_RED);
    }

    private void positionEditBoxes() {
        if (activeTab == TAB_SPOTS) {
            int contentX = SIDEBAR_W + 1;
            int panelY = this.height - 120;
            spotNameBox.setX(contentX + 18);
            spotNameBox.setY(panelY + 22);
            spotZoneBox.setX(contentX + 248);
            spotZoneBox.setY(panelY + 22);
            spotNameBox.visible = true;
            spotZoneBox.visible = true;

            lootBonusBox.visible = false;
            lootQualityBox.visible = false;
            charmBonusBox.visible = false;
            supportMessageBox.visible = false;
            return;
        }

        List<Row> rows = getRowsForActiveTab();
        int contentTop = HEADER_H + 2;
        int y = contentTop - scrollOffset;
        int boxX = this.width - 160;

        for (Row row : rows) {
            if (row.type == RowType.EDIT_BOX && row.editBox != null) {
                boolean vis = (y + ROW_H > contentTop + 1 && y < this.height - 4);
                row.editBox.setY(y + 4);
                if (row.editBox == supportMessageBox) {
                    row.editBox.setX(SIDEBAR_W + 10 + this.font.width(row.name) + 12);
                    row.editBox.setWidth(this.width - row.editBox.getX() - 14);
                } else {
                    row.editBox.setX(boxX);
                }
                row.editBox.visible = vis && isEditBoxInActiveTab(row.editBox);
            }
            y += ROW_H;
        }
        if (activeTab != TAB_VALUES) {
            lootBonusBox.visible = false; lootQualityBox.visible = false;
            charmBonusBox.visible = false;
        }
        if (activeTab != TAB_INFO) {
            supportMessageBox.visible = false;
        }
        if (activeTab != TAB_SPOTS) {
            spotNameBox.visible = false;
            spotZoneBox.visible = false;
        }
    }

    private boolean isEditBoxInActiveTab(EditBox box) {
        if (box == supportMessageBox) return activeTab == TAB_INFO;
        return activeTab == TAB_VALUES;
    }

    @Override
    public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent event, boolean consumed) {
        double mx = event.x(), my = event.y();
        int button = event.button();

        if (super.mouseClicked(event, consumed)) return true;
        if (button != 0) return false;
        int ty = 48;
        for (int i = 0; i < 5; i++) {
            if (mx >= 6 && mx <= SIDEBAR_W - 6 && my >= ty - 2 && my <= ty + 18) {
                activeTab = i;
                scrollOffset = 0;
                if (activeTab == TAB_SPOTS) {
                    reloadEmbeddedSpots();
                }
                return true;
            }
            ty += 26;
        }
        int cardX1 = 9;
        int cardX2 = SIDEBAR_W - 9;
        int cardH = 14;
        int tradeY = this.height - 74;
        if (mx >= cardX1 && mx <= cardX2 && my >= tradeY && my <= tradeY + cardH) {
            this.minecraft.setScreen(new WynnMarketScreen(this));
            return true;
        }
        int converterY = tradeY + 18;
        if (mx >= cardX1 && mx <= cardX2 && my >= converterY && my <= converterY + cardH) {
            this.minecraft.setScreen(new CurrencyConverterModal(this));
            return true;
        }
        int githubY = converterY + 18;
        if (mx >= cardX1 && mx <= cardX2 && my >= githubY && my <= githubY + cardH) {
            try {
                java.awt.Desktop.getDesktop().browse(new java.net.URI("https://github.com/pouettepouette?tab=repositories"));
            } catch (Exception e) {
                toast("Could not open GitHub", C_RED);
            }
            return true;
        }
        if (activeTab == TAB_SPOTS) {
            int btnW = 98;
            int btnX = this.width - btnW - 10;
            if (mx >= btnX && mx <= btnX + btnW && my >= 11 && my <= 29) {
                String cat = MobKillerCalculatorClient.getActivePresetCategory();
                if (cat.isEmpty()) cat = "mythic";
                this.minecraft.setScreen(new SpotTotalsScreen(this, cat));
                return true;
            }
        }
        String[] hudLines = MobKillerCalculatorClient.getHudPreviewLines();
        if (hudLines.length > 0) {
            int hx = MobKillerCalculatorClient.getHudX(), hy = MobKillerCalculatorClient.getHudY();
            int lsp = this.font.lineHeight + 2;
            int totalTextHeight = ((hudLines.length - 1) * lsp) + this.font.lineHeight;
            int topY = hy - (totalTextHeight / 2);
            int maxW = 0;
            for (String l : hudLines) maxW = Math.max(maxW, this.font.width(l));
            int bx1 = hx - maxW / 2 - 5, bx2 = hx + maxW / 2 + 5;
            int by1 = topY - 4, by2 = topY + totalTextHeight + 3;
            if (mx >= bx1 && mx <= bx2 && my >= by1 && my <= by2) {
                isDraggingHud = true; dragStartMouseX = mx; dragStartMouseY = my;
                dragStartHudX = hx; dragStartHudY = hy;
                dragAxisXUnlocked = false;
                dragAxisYUnlocked = false;
                dragPrimaryAxisChosen = false;
                return true;
            }
        }
        if (activeTab == TAB_SPOTS) {
            return handleEmbeddedSpotClick((int) mx, (int) my);
        }
        List<Row> rows = getRowsForActiveTab();
        int contentTop = HEADER_H + 2;
        int x0 = SIDEBAR_W + 1;
        int rowW = this.width - SIDEBAR_W - 2;
        int y = contentTop - scrollOffset;
        for (Row row : rows) {
            if (my >= y && my < y + ROW_H && mx >= x0 && mx < x0 + rowW && y + ROW_H > contentTop && y < this.height - 4) {
                if (row.type == RowType.TOGGLE || row.type == RowType.CYCLE) {
                    if (row.onClick != null) row.onClick.run();
                    return true;
                } else if (row.type == RowType.ACTION) {
                    if (row.onClick != null) row.onClick.run();
                    return true;
                } else if (row.type == RowType.EDIT_BOX && row.editBox != null) {
                    this.setFocused(row.editBox);
                    row.editBox.setFocused(true);
                    return true;
                }
            }
            y += ROW_H;
        }
        return false;
    }

    @Override
    public boolean mouseDragged(net.minecraft.client.input.MouseButtonEvent event, double deltaX, double deltaY) {
        if (activeTab == TAB_SPOTS && !draggingSpotName.isEmpty() && event.button() == 0) {
            draggingSpotMouseX = (int) event.x();
            draggingSpotMouseY = (int) event.y();
            if (!draggingSpotRow) {
                int dx = Math.abs((int) event.x() - draggingSpotStartX);
                int dy = Math.abs((int) event.y() - draggingSpotStartY);
                draggingSpotRow = dx >= 5 || dy >= 5;
            }
            if (draggingSpotRow) {
                return true;
            }
        }
        if (activeTab == TAB_SPOTS && draggingSpotDividerIndex >= 0 && event.button() == 0) {
            int contentX = SIDEBAR_W + 1;
            int contentW = this.width - SIDEBAR_W - 2;
            int columnsX = contentX + 12;
            int columnsW = contentW - 24;
            int[] dividers = getEmbeddedSpotDividers(columnsX, columnsW);
            int div1 = dividers[0];
            int div2 = dividers[1];
            int mouseX = (int) event.x();

            if (draggingSpotDividerIndex == 0) {
                int min = columnsX + SPOT_COL_MIN_W;
                int max = div2 - SPOT_COL_MIN_W;
                div1 = Math.max(min, Math.min(max, mouseX));
            } else {
                int min = div1 + SPOT_COL_MIN_W;
                int max = columnsX + columnsW - SPOT_COL_MIN_W;
                div2 = Math.max(min, Math.min(max, mouseX));
            }

            int defaultDiv1 = columnsX + columnsW / 3;
            int defaultDiv2 = columnsX + (columnsW * 2) / 3;
            if (Math.abs(div1 - defaultDiv1) <= SPOT_DIVIDER_SNAP_PX) div1 = defaultDiv1;
            if (Math.abs(div2 - defaultDiv2) <= SPOT_DIVIDER_SNAP_PX) div2 = defaultDiv2;

            embeddedSpotDividerRatio1 = (div1 - columnsX) / (float) columnsW;
            embeddedSpotDividerRatio2 = (div2 - columnsX) / (float) columnsW;
            return true;
        }
        if (activeTab == TAB_SPOTS && draggingSpotScrollbarColumn >= 0 && event.button() == 0) {
            handleEmbeddedSpotScrollbarDrag((int) event.y());
            return true;
        }
        if (isDraggingHud && event.button() == 0) {
            double mx = event.x(), my = event.y();
            int targetX = (int) Math.round(dragStartHudX + mx - dragStartMouseX);
            int targetY = (int) Math.round(dragStartHudY + my - dragStartMouseY);
            int[] magnetized = applyMagnetizedHudDrag(targetX, targetY);
            MobKillerCalculatorClient.setHudX(magnetized[0]);
            MobKillerCalculatorClient.setHudY(magnetized[1]);
            return true;
        }
        return super.mouseDragged(event, deltaX, deltaY);
    }

    @Override
    public boolean mouseReleased(net.minecraft.client.input.MouseButtonEvent event) {
        if (activeTab == TAB_SPOTS && !draggingSpotName.isEmpty() && event.button() == 0) {
            boolean moved = draggingSpotRow;
            String spotName = draggingSpotName;
            String sourceCategory = draggingSpotSourceCategory;
            draggingSpotName = "";
            draggingSpotSourceCategory = "";
            draggingSpotMouseX = 0;
            draggingSpotMouseY = 0;
            draggingSpotRow = false;

            if (moved) {
                int mx = (int) event.x();
                int contentX = SIDEBAR_W + 1;
                int contentW = this.width - SIDEBAR_W - 2;
                int columnsX = contentX + 12;
                int columnsW = contentW - 24;
                int[] dividers = getEmbeddedSpotDividers(columnsX, columnsW);
                int[] colX = new int[]{columnsX, dividers[0], dividers[1]};
                int[] colW = new int[]{dividers[0] - columnsX, dividers[1] - dividers[0], (columnsX + columnsW) - dividers[1]};

                for (int c = 0; c < 3; c++) {
                    if (mx < colX[c] || mx > colX[c] + colW[c]) continue;
                    String targetCategory = embeddedSpotColumnOrder[c];
                    if (!normalizeSpotCategory(targetCategory).equals(normalizeSpotCategory(sourceCategory))) {
                        onEmbeddedMoveSpotToCategory(spotName, targetCategory);
                    }
                    return true;
                }
                return true;
            }
        }
        if (activeTab == TAB_SPOTS && draggingSpotColumnFrom >= 0 && event.button() == 0) {
            int mx = (int) event.x();
            int contentX = SIDEBAR_W + 1;
            int contentW = this.width - SIDEBAR_W - 2;
            int columnsX = contentX + 12;
            int columnsW = contentW - 24;
            int[] dividers = getEmbeddedSpotDividers(columnsX, columnsW);
            int[] colX = new int[]{columnsX, dividers[0], dividers[1]};
            int[] colW = new int[]{dividers[0] - columnsX, dividers[1] - dividers[0], (columnsX + columnsW) - dividers[1]};

            int target = -1;
            for (int c = 0; c < 3; c++) {
                if (mx >= colX[c] && mx <= colX[c] + colW[c]) {
                    target = c;
                    break;
                }
            }

            if (target >= 0 && target != draggingSpotColumnFrom) {
                String tmp = embeddedSpotColumnOrder[draggingSpotColumnFrom];
                embeddedSpotColumnOrder[draggingSpotColumnFrom] = embeddedSpotColumnOrder[target];
                embeddedSpotColumnOrder[target] = tmp;

                int scrollTmp = spotColumnScroll[draggingSpotColumnFrom];
                spotColumnScroll[draggingSpotColumnFrom] = spotColumnScroll[target];
                spotColumnScroll[target] = scrollTmp;
            }

            draggingSpotColumnFrom = -1;
            ConfigManager.saveEmbeddedSpotsLayout(embeddedSpotColumnOrder, embeddedSpotDividerRatio1, embeddedSpotDividerRatio2);
            return true;
        }
        if (activeTab == TAB_SPOTS && draggingSpotDividerIndex >= 0 && event.button() == 0) {
            draggingSpotDividerIndex = -1;
            ConfigManager.saveEmbeddedSpotsLayout(embeddedSpotColumnOrder, embeddedSpotDividerRatio1, embeddedSpotDividerRatio2);
            return true;
        }
        if (activeTab == TAB_SPOTS && draggingSpotScrollbarColumn >= 0 && event.button() == 0) {
            draggingSpotScrollbarColumn = -1;
            return true;
        }
        if (isDraggingHud && event.button() == 0) {
            isDraggingHud = false;
            dragAxisXUnlocked = false;
            dragAxisYUnlocked = false;
            dragPrimaryAxisChosen = false;
            return true;
        }
        return super.mouseReleased(event);
    }

    private int[] applyMagnetizedHudDrag(int targetX, int targetY) {
        int centerX = this.width / 2;
        int centerY = this.height / 2;

        int deltaFromDragStartX = Math.abs(targetX - dragStartHudX);
        int deltaFromDragStartY = Math.abs(targetY - dragStartHudY);

        int unlockPrimaryThreshold = 6;
        int unlockSecondaryThreshold = 14;
        int magnetRadius = 10;
        int stickyRadius = 16;

        if (!dragPrimaryAxisChosen) {
            if (deltaFromDragStartX >= unlockPrimaryThreshold || deltaFromDragStartY >= unlockPrimaryThreshold) {
                dragPrimaryAxisChosen = true;
                if (deltaFromDragStartX >= deltaFromDragStartY) {
                    dragAxisXUnlocked = true;
                } else {
                    dragAxisYUnlocked = true;
                }
            }
        }

        if (dragPrimaryAxisChosen) {
            if (!dragAxisXUnlocked && deltaFromDragStartX >= unlockSecondaryThreshold) {
                dragAxisXUnlocked = true;
            }
            if (!dragAxisYUnlocked && deltaFromDragStartY >= unlockSecondaryThreshold) {
                dragAxisYUnlocked = true;
            }
        }

        int finalX = targetX;
        int finalY = targetY;

        if (Math.abs(targetX - centerX) <= magnetRadius) {
            finalX = centerX;
        }
        if (Math.abs(targetY - centerY) <= magnetRadius) {
            finalY = centerY;
        }

        if (!dragAxisXUnlocked && Math.abs(finalX - centerX) <= stickyRadius) {
            finalX = centerX;
        }
        if (!dragAxisYUnlocked && Math.abs(finalY - centerY) <= stickyRadius) {
            finalY = centerY;
        }

        finalX = Math.max(0, Math.min(this.width, finalX));
        finalY = Math.max(0, Math.min(this.height, finalY));
        return new int[]{finalX, finalY};
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (activeTab == TAB_SPOTS) {
            if (handleEmbeddedSpotScroll((int) mouseX, (int) mouseY, scrollY)) {
                return true;
            }
            return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
        }
        if (scrollX != 0.0) {
            hudZoom += (float)(scrollX * 0.05);  // Pinch to zoom
            hudZoom = Math.max(0.5f, Math.min(hudZoom, 3.0f));  // Clamp between 0.5x and 3.0x
            return true;
        }
        if (mouseX > SIDEBAR_W) {
            scrollOffset -= (int)(scrollY * 16);
            List<Row> rows = getRowsForActiveTab();
            int contentH = this.height - HEADER_H - 6;
            int maxScroll = Math.max(0, rows.size() * ROW_H - contentH);
            scrollOffset = Math.max(0, Math.min(scrollOffset, maxScroll));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    private List<Row> getRowsForActiveTab() {
        return switch (activeTab) {
            case TAB_SESSIONS -> sessionRows;
            case TAB_HUD_SETTINGS -> hudSettingsRows;
            case TAB_VALUES -> valuesRows;
            case TAB_SPOTS -> spotRows;
            case TAB_INFO -> infoRows;
            default -> sessionRows;
        };
    }

    @Override
    public boolean keyPressed(net.minecraft.client.input.KeyEvent event) {
        if (event.key() == org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER || event.key() == org.lwjgl.glfw.GLFW.GLFW_KEY_KP_ENTER) {
            if (activeTab == TAB_SPOTS) {
                return spotSelectedName.isEmpty() ? onEmbeddedCreateSpot() : onEmbeddedUpdateSpot();
            }
            triggerCalculation();
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public void tick() {
        super.tick();
    }

    private void onBonusValueChanged() {
        triggerCalculation();
        lastAutoSaveTime = System.currentTimeMillis();
        scheduleAutoSave();
    }

    private long lastAutoSaveScheduledTime = 0L;

    private void scheduleAutoSave() {
        long now = System.currentTimeMillis();
        if (now - lastAutoSaveScheduledTime < 100L) {
            return; // Already scheduled
        }
        lastAutoSaveScheduledTime = now;
        new Thread(() -> {
            try {
                Thread.sleep(AUTO_SAVE_DELAY_MS);
                double lb = parseDouble(lootBonusBox.getValue());
                double lq = parseDouble(lootQualityBox.getValue());
                double cb = parseDouble(charmBonusBox.getValue());
                ConfigManager.saveValues(lb, lq, cb);
            } catch (Exception e) {
            }
        }).start();
    }





    @Override
    public void onClose() {
        double lb = parseDouble(lootBonusBox.getValue());
        double lq = parseDouble(lootQualityBox.getValue());
        double c  = parseDouble(charmBonusBox.getValue());
        ConfigManager.saveValues(lb, lq, c);
        super.onClose();
    }

    private void triggerCalculation() {
        double lb = parseDouble(lootBonusBox.getValue());
        double lq = parseDouble(lootQualityBox.getValue());
        double c  = parseDouble(charmBonusBox.getValue());
        MobKillerCalculatorClient.lastResult = MobKillerCalculatorClient.calculateProbability(lb, lq, c);
    }

    private double parseDouble(String v) {
        try { return Double.parseDouble(v.replace(',', '.')); } catch (Exception e) { return 0; }
    }

    private void toast(String msg, int color) { toastMessage = msg; toastColor = color; toastUntilMs = System.currentTimeMillis() + 2500L; }

    private int blendColors(int color1, int color2) {
        int a = 0xFF;
        int r1 = (color1 >> 16) & 0xFF, g1 = (color1 >> 8) & 0xFF, b1 = color1 & 0xFF;
        int r2 = (color2 >> 16) & 0xFF, g2 = (color2 >> 8) & 0xFF, b2 = color2 & 0xFF;
        int r = (r1 + r2) / 2, g = (g1 + g2) / 2, b = (b1 + b2) / 2;
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    private int headerColor(String name) {
        return switch (name) {
            case "Mythics" -> C_MYTHIC;
            case "Ingredients" -> C_INGR;
            case "Gathering" -> C_GATHER;
            case "Session Control" -> C_ACCENT;
            case "HUD Presets" -> 0xFFFFB870;
            case "HUD Appearance", "HUD Colors", "HUD Line Order" -> C_RED;
            case "History" -> C_HISTORY;
            case "Farm Spots" -> C_ACCENT;
            case "Feedback" -> C_INFO;
            case "Status" -> C_STATUS;
            case "Links" -> C_LINK;
            case "Credits" -> C_CREDITS;
            default -> C_TEXT;
        };
    }

    private int rowAccentColor(Row row) {
        if (row.tag == null || row.tag.isEmpty()) {
            if (row.type == RowType.LABEL) {
                if ("API".equals(row.name) || "Market".equals(row.name)) return C_STATUS;
                if ("Author".equals(row.name) || "Version".equals(row.name)) return C_CREDITS;
            }
            return C_ACCENT;
        }

        return switch (row.tag) {
            case "MYTHIC" -> C_MYTHIC;
            case "INGR" -> C_INGR;
            case "GATHER" -> C_GATHER;
            case "HUD", "COLOR", "RENDER" -> C_RED;
            case "HISTORY" -> C_HISTORY;
            case "INFO" -> C_INFO;
            case "LINK" -> C_LINK;
            case "QOL" -> 0xFF8ED2FF;
            case "CORE" -> C_ACCENT;
            case "PRESET" -> 0xFFFFB870;
            default -> C_TEXT;
        };
    }

    private String sessionStateDesc() {
        if (!MobKillerCalculatorClient.isSessionRunning()) return "Click to start a new session.";
        return MobKillerCalculatorClient.isSessionPaused() ? "Paused – click to resume." : "Running – click to pause.";
    }
    private String selectedLineDesc() { return "Line: " + MobKillerCalculatorClient.getHudLineLabel(selectedHudLineId); }
    private String addLineDesc() {
        return selectedAddHudLineId == -1 ? "Add Line: None" : "Add: " + MobKillerCalculatorClient.getHudLineLabel(selectedAddHudLineId);
    }
    private String lastMythicDesc() { String[] l = MobKillerCalculatorClient.getLastMythicSessionSummaryLines(); return l[1] + " | " + l[2]; }
    private String lastSessionDesc() { String[] l = MobKillerCalculatorClient.getLastSessionSummaryLines(); return l[1] + " | " + l[2]; }
    private String lastGatheringDesc() { String[] l = MobKillerCalculatorClient.getLastGatheringSessionSummaryLines(); return l[1] + " | " + l[2]; }

    private void cycleSelectedHudLine() {
        int[] order = MobKillerCalculatorClient.getHudLineOrder();
        if (order.length == 0) return;
        int idx = 0;
        for (int i = 0; i < order.length; i++) { if (order[i] == selectedHudLineId) { idx = i; break; } }
        selectedHudLineId = order[(idx + 1) % order.length];
    }
    private void cycleAddHudLine() {
        int[] hidden = MobKillerCalculatorClient.getHiddenHudLines();
        if (hidden.length == 0) { selectedAddHudLineId = -1; return; }
        int idx = 0;
        for (int i = 0; i < hidden.length; i++) { if (hidden[i] == selectedAddHudLineId) { idx = i; break; } }
        selectedAddHudLineId = hidden[(idx + 1) % hidden.length];
    }
    private void refreshHudOrderState() {
        int[] order = MobKillerCalculatorClient.getHudLineOrder();
        if (order.length > 0) selectedHudLineId = order[0];
        int[] hidden = MobKillerCalculatorClient.getHiddenHudLines();
        if (hidden.length > 0 && selectedAddHudLineId == -1) selectedAddHudLineId = hidden[0];
    }

    private void refreshPriceBoxDisplay(boolean forceManualValue) {
    }

    private void refreshGatheringTierPriceBoxes() {
    }

    private enum RowType { HEADER, TOGGLE, ACTION, EDIT_BOX, LABEL, CYCLE }

    @FunctionalInterface
    private interface BoolGetter { boolean get(); }
    @FunctionalInterface
    private interface StringGetter { String get(); }
    @FunctionalInterface
    private interface IntGetter { int get(); }

    private static final class Row {
        final RowType type;
        final String name;
        final String tag;
        final BoolGetter stateGetter;
        final StringGetter descGetter;
        final Runnable onClick;
        final EditBox editBox;
        final IntGetter cycleGetter;

        private Row(RowType type, String name, String tag, BoolGetter stateGetter, StringGetter descGetter, Runnable onClick, EditBox editBox, IntGetter cycleGetter) {
            this.type = type; this.name = name; this.tag = tag;
            this.stateGetter = stateGetter; this.descGetter = descGetter;
            this.onClick = onClick; this.editBox = editBox; this.cycleGetter = cycleGetter;
        }

        static Row header(String name) { return new Row(RowType.HEADER, name, null, null, null, null, null, null); }
        static Row toggle(String name, BoolGetter state, StringGetter desc, String tag, Runnable onClick) {
            return new Row(RowType.TOGGLE, name, tag, state, desc, onClick, null, null);
        }
        static Row action(String name, StringGetter desc, String tag, Runnable onClick) {
            return new Row(RowType.ACTION, name, tag, null, desc, onClick, null, null);
        }
        static Row editBox(String name, EditBox box, String tag) {
            return new Row(RowType.EDIT_BOX, name, tag, null, null, null, box, null);
        }
        static Row label(String name, StringGetter desc) {
            return new Row(RowType.LABEL, name, null, null, desc, null, null, null);
        }
        static Row cycle(String name, IntGetter state, StringGetter desc, String tag, Runnable onClick) {
            return new Row(RowType.CYCLE, name, tag, null, desc, onClick, null, state);
        }
    }
}
