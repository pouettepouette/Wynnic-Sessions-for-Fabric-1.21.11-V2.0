package com.example;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public class HudPresetEditorScreen extends Screen {
    private static final int C_BG = 0xFF090D18;
    private static final int C_PANEL = 0xFF0E1424;
    private static final int C_PANEL_ALT = 0xFF0B1020;
    private static final int C_LINE = 0xFF1C263D;
    private static final int C_TEXT = 0xFFE5ECFF;
    private static final int C_MUTED = 0xFF667196;
    private static final int C_ACCENT = 0xFF19D9A3;
    private static final int C_MYTHIC = 0xFFC86CFF;
    private static final int C_INGR   = 0xFFFFD580;
    private static final int C_GATHER = 0xFF3399FF;
    private static final int C_DRAG = 0xAA19D9A3;
    private static final int ROW_H = 22;
    private static final int PANEL_GAP = 16;

    private final Screen parent;
    private final String presetKey;
    private final List<Integer> activeLines = new ArrayList<>();
    private final List<Integer> availableLines = new ArrayList<>();

    private int draggedLineId = -1;
    private boolean draggingFromActive = false;
    private int dragSourceIndex = -1;
    private int dragHoverIndex = -1;
    private boolean dragHoverActivePanel = false;
    private int dragMouseX = 0;
    private int dragMouseY = 0;

    public HudPresetEditorScreen(Screen parent, String presetKey) {
        super(Component.literal("Edit HUD Preset"));
        this.parent = parent;
        this.presetKey = presetKey;
    }

    @Override
    protected void init() {
        reloadLists();

        int buttonY = this.height - 30;
        this.addRenderableWidget(Button.builder(Component.literal("Reset preset"), btn -> {
            MobKillerCalculatorClient.resetHudPreset(presetKey);
            reloadLists();
        }).bounds(this.width / 2 - 48, buttonY, 96, 20).build());
    }

    @Override
    public void onClose() {
        MobKillerCalculatorClient.applyHudPresetByKey(presetKey);
        if (this.minecraft != null) {
            this.minecraft.setScreen(parent);
        }
    }

    @Override
    public void render(GuiGraphics gui, int mouseX, int mouseY, float partialTick) {
        gui.fill(0, 0, this.width, this.height, C_BG);

        int contentX = 28;
        int contentTop = 28;
        int contentBottom = this.height - 44;
        int panelWidth = (this.width - contentX * 2 - PANEL_GAP) / 2;
        int activeX = contentX;
        int availableX = activeX + panelWidth + PANEL_GAP;

        String presetDisplayName = MobKillerCalculatorClient.getHudPresetDisplayName(presetKey);
        int presetNameColor;
        switch (presetKey) {
            case "mythic":     presetNameColor = C_MYTHIC;  break;
            case "ingredient": presetNameColor = C_INGR;    break;
            case "gathering":  presetNameColor = C_GATHER;  break;
            default:           presetNameColor = C_ACCENT;  break;
        }
        gui.pose().pushMatrix();
        gui.pose().translate(contentX, 5);
        gui.pose().scale(1.6f, 1.6f);
        gui.drawString(this.font, presetDisplayName, 0, 0, presetNameColor, false);
        gui.pose().popMatrix();
        gui.drawString(this.font,
            "Drag between columns or reorder inside Active Lines.",
            contentX, 20, C_MUTED, false);

        drawPanel(gui, activeX, contentTop, panelWidth, contentBottom - contentTop, "Active Lines", true, mouseX, mouseY);
        drawPanel(gui, availableX, contentTop, panelWidth, contentBottom - contentTop, "Available Lines", false, mouseX, mouseY);

        if (draggedLineId >= 0) {
            drawDraggedRow(gui, mouseX, mouseY, draggedLineId);
        }

        super.render(gui, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent event, boolean consumed) {
        if (super.mouseClicked(event, consumed)) {
            return true;
        }
        if (event.button() != 0) {
            return false;
        }

        double mouseX = event.x();
        double mouseY = event.y();

        RowHit hit = findRowHit((int) mouseX, (int) mouseY);
        if (hit != null) {
            draggedLineId = hit.lineId;
            draggingFromActive = hit.activePanel;
            dragSourceIndex = hit.index;
            dragHoverIndex = hit.index;
            dragHoverActivePanel = hit.activePanel;
            dragMouseX = (int) mouseX;
            dragMouseY = (int) mouseY;
            return true;
        }

        return false;
    }

    @Override
    public boolean mouseDragged(net.minecraft.client.input.MouseButtonEvent event, double dragX, double dragY) {
        if (draggedLineId >= 0) {
            double mouseX = event.x();
            double mouseY = event.y();
            dragMouseX = (int) mouseX;
            dragMouseY = (int) mouseY;
            updateHover((int) mouseX, (int) mouseY);
            return true;
        }
        return super.mouseDragged(event, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(net.minecraft.client.input.MouseButtonEvent event) {
        if (draggedLineId >= 0) {
            double mouseX = event.x();
            double mouseY = event.y();
            handleDrop((int) mouseX, (int) mouseY);
            draggedLineId = -1;
            dragSourceIndex = -1;
            dragHoverIndex = -1;
            return true;
        }
        return super.mouseReleased(event);
    }

    private void drawPanel(GuiGraphics gui, int x, int y, int width, int height, String title, boolean activePanel, int mouseX, int mouseY) {
        gui.fill(x, y, x + width, y + height, activePanel ? C_PANEL : C_PANEL_ALT);
        gui.fill(x, y, x + width, y + 22, C_LINE);
        gui.drawString(this.font, title, x + 8, y + 7, C_TEXT, false);

        int listTop = y + 28;
        if (activePanel) {
            for (int index = 0; index < activeLines.size(); index++) {
                int lineId = activeLines.get(index);
                int rowTop = listTop + index * (ROW_H + 4);
                int rowBottom = rowTop + ROW_H;
                boolean hovered = mouseX >= x + 8 && mouseX <= x + width - 8 && mouseY >= rowTop && mouseY <= rowBottom;
                boolean hiddenBecauseDragged = draggedLineId == lineId && draggingFromActive;
                if (hiddenBecauseDragged) {
                    continue;
                }
                int fillColor = hovered ? 0x44243A5A : 0x22243A5A;
                gui.fill(x + 8, rowTop, x + width - 8, rowBottom, fillColor);
                gui.drawString(this.font, "[::] ", x + 14, rowTop + 7, C_ACCENT, false);
                gui.drawString(this.font, MobKillerCalculatorClient.getHudLineLabel(lineId), x + 38, rowTop + 7, C_TEXT, false);
            }
        } else {
            List<AvailableEntry> entries = buildAvailableEntries();
            for (int row = 0; row < entries.size(); row++) {
                AvailableEntry entry = entries.get(row);
                int rowTop = listTop + row * (ROW_H + 4);
                int rowBottom = rowTop + ROW_H;

                if (entry.header) {
                    gui.fill(x + 8, rowTop, x + width - 8, rowBottom, 0x3320364D);
                    gui.fill(x + 8, rowTop, x + 10, rowBottom, entry.color);
                    gui.drawString(this.font, entry.title, x + 15, rowTop + 7, entry.color, false);
                    continue;
                }

                boolean hovered = mouseX >= x + 8 && mouseX <= x + width - 8 && mouseY >= rowTop && mouseY <= rowBottom;
                boolean hiddenBecauseDragged = draggedLineId == entry.lineId && !draggingFromActive;
                if (hiddenBecauseDragged) {
                    continue;
                }

                int fillColor = hovered ? 0x44243A5A : 0x22243A5A;
                gui.fill(x + 8, rowTop, x + width - 8, rowBottom, fillColor);
                gui.drawString(this.font, "[+] ", x + 14, rowTop + 7, entry.color, false);

                String label = MobKillerCalculatorClient.getHudLineLabel(entry.lineId);
                gui.drawString(this.font, label, x + 38, rowTop + 7, C_TEXT, false);
            }
        }

        if (draggedLineId >= 0 && dragHoverActivePanel == activePanel) {
            int indicatorY;
            if (activePanel) {
                indicatorY = listTop + Math.max(0, dragHoverIndex) * (ROW_H + 4) - 2;
                if (dragHoverIndex >= activeLines.size()) {
                    indicatorY = listTop + activeLines.size() * (ROW_H + 4) - 2;
                }
            } else {
                indicatorY = computeAvailableIndicatorY(listTop, dragHoverIndex);
            }
            gui.fill(x + 8, indicatorY, x + width - 8, indicatorY + 2, C_DRAG);
        }
    }

    private void drawDraggedRow(GuiGraphics gui, int mouseX, int mouseY, int lineId) {
        int width = 180;
        int rowTop = mouseY - 10;
        gui.fill(mouseX - width / 2, rowTop, mouseX + width / 2, rowTop + ROW_H, 0xCC1C263D);
        gui.drawString(this.font, MobKillerCalculatorClient.getHudLineLabel(lineId), mouseX - width / 2 + 10, rowTop + 7, C_TEXT, false);
    }

    private void handleDrop(int mouseX, int mouseY) {
        updateHover(mouseX, mouseY);
        if (draggedLineId < 0) {
            return;
        }

        if (dragHoverActivePanel) {
            if (draggingFromActive) {
                int moved = activeLines.remove(dragSourceIndex);
                int targetIndex = Math.max(0, Math.min(dragHoverIndex, activeLines.size()));
                activeLines.add(targetIndex, moved);
            } else {
                availableLines.remove(Integer.valueOf(draggedLineId));
                int targetIndex = Math.max(0, Math.min(dragHoverIndex, activeLines.size()));
                if (!activeLines.contains(draggedLineId)) {
                    activeLines.add(targetIndex, draggedLineId);
                }
            }
            MobKillerCalculatorClient.setHudPresetLineOrder(presetKey, activeLines);
            reloadLists();
            return;
        }

        if (draggingFromActive && activeLines.size() > 1) {
            activeLines.remove(Integer.valueOf(draggedLineId));
            MobKillerCalculatorClient.setHudPresetLineOrder(presetKey, activeLines);
            reloadLists();
        }
    }

    private void updateHover(int mouseX, int mouseY) {
        int contentX = 28;
        int contentTop = 28;
        int panelWidth = (this.width - contentX * 2 - PANEL_GAP) / 2;
        int activeX = contentX;
        int availableX = activeX + panelWidth + PANEL_GAP;
        int listTop = contentTop + 28;

        if (mouseX >= activeX && mouseX <= activeX + panelWidth) {
            dragHoverActivePanel = true;
            dragHoverIndex = computeIndex(mouseY, listTop, activeLines.size());
            return;
        }
        if (mouseX >= availableX && mouseX <= availableX + panelWidth) {
            dragHoverActivePanel = false;
            dragHoverIndex = computeAvailableInsertIndex(mouseY, listTop);
            return;
        }
        dragHoverActivePanel = draggingFromActive;
        dragHoverIndex = dragSourceIndex;
    }

    private int computeIndex(int mouseY, int listTop, int size) {
        int relative = Math.max(0, mouseY - listTop);
        int index = relative / (ROW_H + 4);
        return Math.max(0, Math.min(index, size));
    }

    private int computeAvailableInsertIndex(int mouseY, int listTop) {
        List<AvailableEntry> entries = buildAvailableEntries();
        int fallback = availableLines.size();
        for (int row = 0; row < entries.size(); row++) {
            AvailableEntry entry = entries.get(row);
            if (entry.header) {
                continue;
            }
            int rowTop = listTop + row * (ROW_H + 4);
            int midpoint = rowTop + (ROW_H / 2);
            if (mouseY < midpoint) {
                return Math.max(0, entry.lineIndex);
            }
            fallback = Math.max(fallback, entry.lineIndex + 1);
        }
        return Math.max(0, Math.min(fallback, availableLines.size()));
    }

    private int computeAvailableIndicatorY(int listTop, int insertIndex) {
        List<AvailableEntry> entries = buildAvailableEntries();
        int fallbackY = listTop + entries.size() * (ROW_H + 4) - 2;
        for (int row = 0; row < entries.size(); row++) {
            AvailableEntry entry = entries.get(row);
            if (entry.header) {
                continue;
            }
            int rowTop = listTop + row * (ROW_H + 4);
            if (entry.lineIndex >= insertIndex) {
                return rowTop - 2;
            }
            fallbackY = rowTop + (ROW_H + 4) - 2;
        }
        return fallbackY;
    }

    private RowHit findRowHit(int mouseX, int mouseY) {
        int contentX = 28;
        int contentTop = 28;
        int panelWidth = (this.width - contentX * 2 - PANEL_GAP) / 2;
        int activeX = contentX;
        int availableX = activeX + panelWidth + PANEL_GAP;
        RowHit activeHit = findRowInPanel(mouseX, mouseY, activeX, contentTop, panelWidth, activeLines, true);
        if (activeHit != null) {
            return activeHit;
        }
        return findRowInAvailablePanel(mouseX, mouseY, availableX, contentTop, panelWidth);
    }

    private RowHit findRowInPanel(int mouseX, int mouseY, int x, int y, int width, List<Integer> lines, boolean activePanel) {
        int listTop = y + 28;
        for (int index = 0; index < lines.size(); index++) {
            int rowTop = listTop + index * (ROW_H + 4);
            int rowBottom = rowTop + ROW_H;
            if (mouseX >= x + 8 && mouseX <= x + width - 8 && mouseY >= rowTop && mouseY <= rowBottom) {
                return new RowHit(lines.get(index), index, activePanel);
            }
        }
        return null;
    }

    private RowHit findRowInAvailablePanel(int mouseX, int mouseY, int x, int y, int width) {
        int listTop = y + 28;
        List<AvailableEntry> entries = buildAvailableEntries();
        for (int row = 0; row < entries.size(); row++) {
            AvailableEntry entry = entries.get(row);
            if (entry.header) {
                continue;
            }
            int rowTop = listTop + row * (ROW_H + 4);
            int rowBottom = rowTop + ROW_H;
            if (mouseX >= x + 8 && mouseX <= x + width - 8 && mouseY >= rowTop && mouseY <= rowBottom) {
                return new RowHit(entry.lineId, entry.lineIndex, false);
            }
        }
        return null;
    }

    private List<AvailableEntry> buildAvailableEntries() {
        Map<String, List<Integer>> grouped = new LinkedHashMap<>();
        grouped.put("mythic",     new ArrayList<>());
        grouped.put("ingredient", new ArrayList<>());
        grouped.put("gathering",  new ArrayList<>());

        for (int lineId : availableLines) {
            String category = MobKillerCalculatorClient.getHudLineBasePresetCategory(lineId);
            List<Integer> bucket = grouped.getOrDefault(category, grouped.get("ingredient"));
            bucket.add(lineId);
        }

        List<AvailableEntry> entries = new ArrayList<>();
        addAvailableCategory(entries, grouped.get("mythic"),     "Mythic",      C_MYTHIC);
        addAvailableCategory(entries, grouped.get("ingredient"), "Ingredient",  C_INGR);
        addAvailableCategory(entries, grouped.get("gathering"),  "Gathering",   C_GATHER);
        return entries;
    }

    private void addAvailableCategory(List<AvailableEntry> entries, List<Integer> lines, String title, int color) {
        if (lines == null || lines.isEmpty()) {
            return;
        }
        entries.add(AvailableEntry.header(title, color));
        for (int lineId : lines) {
            int index = availableLines.indexOf(lineId);
            entries.add(AvailableEntry.line(lineId, index, color));
        }
    }

    private String trimToWidth(String value, int maxWidth) {
        if (value == null || value.isEmpty() || maxWidth <= 0) {
            return "";
        }
        if (this.font.width(value) <= maxWidth) {
            return value;
        }
        String ellipsis = "...";
        int ellipsisWidth = this.font.width(ellipsis);
        if (ellipsisWidth >= maxWidth) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (this.font.width(builder.toString() + c) + ellipsisWidth > maxWidth) {
                break;
            }
            builder.append(c);
        }
        return builder + ellipsis;
    }

    private void reloadLists() {
        activeLines.clear();
        for (int lineId : MobKillerCalculatorClient.getHudPresetLineOrder(presetKey)) {
            activeLines.add(lineId);
        }
        availableLines.clear();
        for (int lineId : MobKillerCalculatorClient.getHiddenHudLinesForPreset(presetKey)) {
            availableLines.add(lineId);
        }
    }

    private static final class RowHit {
        final int lineId;
        final int index;
        final boolean activePanel;

        RowHit(int lineId, int index, boolean activePanel) {
            this.lineId = lineId;
            this.index = index;
            this.activePanel = activePanel;
        }
    }

    private static final class AvailableEntry {
        final boolean header;
        final String title;
        final int lineId;
        final int lineIndex;
        final int color;

        private AvailableEntry(boolean header, String title, int lineId, int lineIndex, int color) {
            this.header = header;
            this.title = title;
            this.lineId = lineId;
            this.lineIndex = lineIndex;
            this.color = color;
        }

        static AvailableEntry header(String title, int color) {
            return new AvailableEntry(true, title, -1, -1, color);
        }

        static AvailableEntry line(int lineId, int lineIndex, int color) {
            return new AvailableEntry(false, "", lineId, lineIndex, color);
        }
    }
}