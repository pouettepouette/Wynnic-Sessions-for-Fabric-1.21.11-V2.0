package com.example;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public class CurrencyConverterModal extends Screen {
    private static final int C_BG = 0xFF090D18;
    private static final int C_PANEL = 0xFF0A1020;
    private static final int C_LINE = 0xFF1C263D;
    private static final int C_TEXT = 0xFFE5ECFF;
    private static final int C_MUTED = 0xFF667196;
    private static final int C_ACCENT = 0xFF19D9A3;
    private static final int C_INGR = 0xFFFFD580;

    private static final int MODAL_W = 320;
    private static final int MODAL_H = 180;
    private static final double EURO_PER_60_LE = 2.4d;
    private static final double EURO_PER_EMERALD = EURO_PER_60_LE / (60.0d * 4096.0d);
    private static final double USD_PER_EURO = 1.1d;

    private final Screen parent;
    private int modalX;
    private int modalY;

    private EditBox converterStxBox;
    private EditBox converterLeBox;
    private EditBox converterEbBox;
    private EditBox converterEBox;
    private EditBox converterFiatBox;
    private boolean updatingConverterFields = false;

    private int closeButtonX;
    private int closeButtonY;
    private int closeButtonSize = 12;
    
    private boolean showUSD = false;
    private int currencySwitchX;
    private int currencySwitchY;
    private int currencySwitchW = 58;
    private int currencySwitchH = 16;

    public CurrencyConverterModal(Screen parent) {
        super(Component.literal("Currency Converter"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        super.init();
        this.modalX = (this.width - MODAL_W) / 2;
        this.modalY = (this.height - MODAL_H) / 2;

        this.closeButtonX = this.modalX + MODAL_W - 16;
        this.closeButtonY = this.modalY + 8;

        int boxW = 50;
        int boxGap = 6;
        int startX = this.modalX + 10;
        int startY = this.modalY + 30;

        converterStxBox = makeConverterBox(startX, startY, boxW, "stx");
        converterLeBox = makeConverterBox(startX + boxW + boxGap, startY, boxW, "le");
        converterEbBox = makeConverterBox(startX + (boxW + boxGap) * 2, startY, boxW, "eb");
        converterEBox = makeConverterBox(startX + (boxW + boxGap) * 3, startY, boxW, "e");

        converterFiatBox = makeFiatConverterBox(startX, startY + 66, 230, "eur/usd");

        currencySwitchX = startX;
        currencySwitchY = this.modalY + 66;

        updateConverterFields(0L);
    }

    private EditBox makeConverterBox(int x, int y, int width, String label) {
        EditBox box = new EditBox(this.font, x, y, width, 18, Component.literal(label));
        box.setMaxLength(8);
        box.setFilter(value -> value.isEmpty() || value.matches("\\d+"));
        box.setResponder(value -> {
            if (!updatingConverterFields) {
                updateConverterFromBoxes();
            }
        });
        this.addRenderableWidget(box);
        return box;
    }

    private EditBox makeFiatConverterBox(int x, int y, int width, String label) {
        EditBox box = new EditBox(this.font, x, y, width, 18, Component.literal(label));
        box.setMaxLength(16);
        box.setFilter(value -> value.isEmpty() || value.matches("\\d*(\\.\\d*)?"));
        box.setResponder(value -> {
            if (!updatingConverterFields) {
                updateConverterFromFiatBox();
            }
        });
        this.addRenderableWidget(box);
        return box;
    }

    @Override
    public void render(GuiGraphics gui, int mouseX, int mouseY, float partialTicks) {
        gui.fill(0, 0, this.width, this.height, 0xAA000000);
        gui.fill(this.modalX, this.modalY, this.modalX + MODAL_W, this.modalY + MODAL_H, C_PANEL);
        gui.fill(this.modalX, this.modalY, this.modalX + MODAL_W, this.modalY + 1, C_LINE);
        gui.fill(this.modalX, this.modalY + 24, this.modalX + MODAL_W, this.modalY + 25, C_LINE);
        gui.drawString(this.font, "Currency Converter", this.modalX + 10, this.modalY + 8, C_INGR, false);
        boolean closeHovered = mouseX >= this.closeButtonX - 6 && mouseX <= this.closeButtonX + 6
            && mouseY >= this.closeButtonY - 6 && mouseY <= this.closeButtonY + 6;
        int closeColor = closeHovered ? C_ACCENT : C_MUTED;
        gui.drawString(this.font, "✕", this.closeButtonX, this.closeButtonY, closeColor, false);
        int startX = converterStxBox.getX();
        gui.drawString(this.font, "STX", startX + 12, this.modalY + 52, C_MUTED, false);
        gui.drawString(this.font, "LE", startX + 56 + 14, this.modalY + 52, C_MUTED, false);
        gui.drawString(this.font, "EB", startX + 112 + 14, this.modalY + 52, C_MUTED, false);
        gui.drawString(this.font, "E", startX + 168 + 18, this.modalY + 52, C_MUTED, false);
        boolean switchHovered = mouseX >= currencySwitchX && mouseX <= currencySwitchX + currencySwitchW
            && mouseY >= currencySwitchY && mouseY <= currencySwitchY + currencySwitchH;
        int switchBg = switchHovered ? 0xFF1B3A52 : 0xFF0F2437;
        gui.fill(currencySwitchX, currencySwitchY, currencySwitchX + currencySwitchW, currencySwitchY + currencySwitchH, switchBg);
        gui.fill(currencySwitchX, currencySwitchY, currencySwitchX + currencySwitchW, currencySwitchY + 1, C_LINE);
        gui.fill(currencySwitchX, currencySwitchY + currencySwitchH - 1, currencySwitchX + currencySwitchW, currencySwitchY + currencySwitchH, C_LINE);
        gui.fill(currencySwitchX, currencySwitchY, currencySwitchX + 1, currencySwitchY + currencySwitchH, C_LINE);
        gui.fill(currencySwitchX + currencySwitchW - 1, currencySwitchY, currencySwitchX + currencySwitchW, currencySwitchY + currencySwitchH, C_LINE);
        
        String currencyLabel = showUSD ? "USD" : "EUR";
        gui.drawCenteredString(this.font, currencyLabel, currencySwitchX + currencySwitchW / 2, currencySwitchY + 4, C_ACCENT);

        super.render(gui, mouseX, mouseY, partialTicks);
    }

    private void updateConverterFromBoxes() {
        long emeralds = getConverterEmeralds();
        updateConverterFields(emeralds);
    }

    private void updateConverterFromFiatBox() {
        String rawValue = converterFiatBox.getValue();
        if (rawValue == null || rawValue.isBlank()) {
            setEmeraldBoxes(0L);
            return;
        }

        double fiatValue = parseDouble(rawValue);
        double emeraldValue = showUSD 
            ? fiatValue / (EURO_PER_EMERALD * USD_PER_EURO)
            : fiatValue / EURO_PER_EMERALD;
        long emeralds = Math.round(emeraldValue);
        setEmeraldBoxes(emeralds);
        this.setFocused(converterFiatBox);
        converterFiatBox.setFocused(true);
    }

    private void updateConverterFields(long emeralds) {
        setEmeraldBoxes(emeralds);

        long safeEmeralds = Math.max(0L, emeralds);
        double fiatValue = showUSD
            ? safeEmeralds * EURO_PER_EMERALD * USD_PER_EURO
            : safeEmeralds * EURO_PER_EMERALD;

        updatingConverterFields = true;
        converterFiatBox.setValue(formatCurrency(fiatValue));
        updatingConverterFields = false;
    }

    private void setEmeraldBoxes(long emeralds) {
        long safeEmeralds = Math.max(0L, emeralds);
        long stx = safeEmeralds / 262144L;
        long remainder = safeEmeralds % 262144L;
        long le = remainder / 4096L;
        remainder %= 4096L;
        long eb = remainder / 64L;
        long e = remainder % 64L;

        updatingConverterFields = true;
        converterStxBox.setValue(Long.toString(stx));
        converterLeBox.setValue(Long.toString(le));
        converterEbBox.setValue(Long.toString(eb));
        converterEBox.setValue(Long.toString(e));
        updatingConverterFields = false;
    }

    private long getConverterEmeralds() {
        return parseLong(converterStxBox.getValue()) * 262144L
            + parseLong(converterLeBox.getValue()) * 4096L
            + parseLong(converterEbBox.getValue()) * 64L
            + parseLong(converterEBox.getValue());
    }

    private static long parseLong(String value) {
        if (value == null || value.isBlank()) {
            return 0L;
        }
        try {
            return Long.parseLong(value.replaceAll("[^0-9]", ""));
        } catch (NumberFormatException ex) {
            return 0L;
        }
    }

    private static double parseDouble(String value) {
        if (value == null || value.isBlank()) {
            return 0.0d;
        }
        try {
            return Double.parseDouble(value.replaceAll("[^0-9.]", ""));
        } catch (NumberFormatException ex) {
            return 0.0d;
        }
    }

    private static String formatEuros(double euros) {
        if (euros >= 100.0d) return String.format(java.util.Locale.ROOT, "%.2f", euros);
        if (euros >= 1.0d) return String.format(java.util.Locale.ROOT, "%.3f", euros);
        return String.format(java.util.Locale.ROOT, "%.5f", euros);
    }

    private static String formatCurrency(double value) {
        if (value == 0.0d) return "0";  // Display "0" instead of "0.0000"
        if (value >= 100.0d) return String.format(java.util.Locale.ROOT, "%.2f", value);
        if (value >= 1.0d) return String.format(java.util.Locale.ROOT, "%.3f", value);
        return String.format(java.util.Locale.ROOT, "%.5f", value);
    }

    @Override
    public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent event, boolean consumed) {
        if (super.mouseClicked(event, consumed)) return true;
        double mx = event.x();
        double my = event.y();
        if (event.button() != 0) return false;
        if (mx >= this.closeButtonX - 6 && mx <= this.closeButtonX + 6
            && my >= this.closeButtonY - 6 && my <= this.closeButtonY + 6) {
            this.onClose();
            return true;
        }
        if (mx >= currencySwitchX && mx <= currencySwitchX + currencySwitchW
            && my >= currencySwitchY && my <= currencySwitchY + currencySwitchH) {
            showUSD = !showUSD;
            updateConverterFields(getConverterEmeralds());
            return true;
        }

        return false;
    }

    @Override
    public boolean keyPressed(net.minecraft.client.input.KeyEvent event) {
        if (event.key() == org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE) {
            this.onClose();
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public void onClose() {
        if (this.minecraft != null) {
            this.minecraft.setScreen(this.parent);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
