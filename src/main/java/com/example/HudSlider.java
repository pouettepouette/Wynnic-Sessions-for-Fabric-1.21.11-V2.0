package com.example;

import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

public class HudSlider extends AbstractWidget {
    private double value;
    private final double min;
    private final double max;
    private final int sliderWidth;
    private final int sliderHeight;
    private final int barColor = 0xFFAAAAAA; // Neutral gray
    private final HudSliderCallback callback;

    public interface HudSliderCallback {
        void onValueChanged(double value);
    }

    private final String label;

    public HudSlider(int x, int y, int width, int height, double min, double max, double initial, String label, HudSliderCallback callback) {
        super(x, y, width, height, Component.literal(label));
        this.label = label;
        this.min = min;
        this.max = max;
        this.value = initial;
        this.sliderWidth = width;
        this.sliderHeight = height;
        this.callback = callback;
    }

    @Override
    public void renderWidget(GuiGraphics gui, int mouseX, int mouseY, float partialTicks) {
        int barThickness = 8; // Wider bar thickness
        int barY = getY() + sliderHeight / 2 - barThickness / 2;
        gui.fill(getX(), barY, getX() + sliderWidth, barY + barThickness, barColor);
        int knobSize = 14; // Slightly larger knob for the wider bar
        double percent = (value - min) / (max - min);
        int knobX = getX() + (int) (percent * (sliderWidth - knobSize));
        knobX = Math.max(getX(), Math.min(getX() + sliderWidth - knobSize, knobX));
        int knobY = getY() + sliderHeight / 2 - knobSize / 2;
        int knobColor = 0xFFFFFFFF;
        gui.fill(knobX, knobY, knobX + knobSize, knobY + knobSize, knobColor);
        int textColor = 0xFFFFFF;
        gui.drawString(net.minecraft.client.Minecraft.getInstance().font, label, getX(), getY() - 10, textColor);
        gui.drawString(net.minecraft.client.Minecraft.getInstance().font, String.valueOf((int) value), getX() + sliderWidth + 8, getY() + sliderHeight / 2 - 4, textColor);
    }

    @Override
    protected void updateWidgetNarration(net.minecraft.client.gui.narration.NarrationElementOutput narrationElementOutput) {
    }

    @Override
    public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent event, boolean consumed) {
        if (event.button() == 0 && isHoveredOrFocused()) {
            updateValue(event.x());
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseDragged(net.minecraft.client.input.MouseButtonEvent event, double dragX, double dragY) {
        if (event.button() == 0) {
            updateValue(event.x());
            return true;
        }
        return false;
    }

    private void updateValue(double mouseX) {
        int divisor = sliderWidth - 8;
        if (divisor <= 0) return;
        double rel = (mouseX - getX()) / divisor;
        rel = Math.max(0, Math.min(1, rel));
        value = min + rel * (max - min);
        callback.onValueChanged(value);
    }

    public double getValue() {
        return value;
    }
}
