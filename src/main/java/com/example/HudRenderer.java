package com.example;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

public class HudRenderer {
    public void render(
        GuiGraphics guiGraphics,
        Minecraft minecraft,
        int hudX,
        int hudY,
        int hudColor,
        double probability,
        SessionTracker sessionTracker,
        String apiStatus
    ) {
        String[] lines = MobKillerCalculatorClient.getHudPreviewLines();
        int lineSpacing = minecraft.font.lineHeight + 2;
        int totalTextHeight = lines.length <= 0
            ? minecraft.font.lineHeight
            : ((lines.length - 1) * lineSpacing) + minecraft.font.lineHeight;
        int topY = hudY - (totalTextHeight / 2);
        boolean drawShadow = MobKillerCalculatorClient.isHudTextShadowEnabled();
        boolean drawBackground = MobKillerCalculatorClient.isHudBackgroundEnabled();
        int bgColor = MobKillerCalculatorClient.getHudBackgroundColor();
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            int textY = topY + (i * lineSpacing);
            int textWidth = minecraft.font.width(line);
            int textX = hudX - (textWidth / 2);

            if (drawBackground) {
                guiGraphics.fill(textX - 3, textY - 1, textX + textWidth + 3, textY + minecraft.font.lineHeight + 1, bgColor);
            }

            guiGraphics.drawString(minecraft.font, line, textX, textY, hudColor, drawShadow);
        }
    }
}
