package com.tnc.tnc.skyacademy.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

/** Shared with the opening prologue: identical typography, spacing and warm white. */
public final class CinematicCard {
    public static void draw(GuiGraphics graphics, Component text, float alpha) {
        int width=graphics.guiWidth(), height=graphics.guiHeight();
        var font=Minecraft.getInstance().font;
        if (alpha <= .022F) return;
        float scale = width >= 480 ? 1.4F : 1.05F;
        int lineWidth = Math.max(80, Math.min(254, (int) ((width - 48) / scale)));
        var lines = font.split(text, lineWidth);
        int lineHeight = font.lineHeight + 5;
        int color = (Math.round(alpha * 255) << 24) | 0xf3eee5;
        var pose = graphics.pose();
        pose.pushPose();
        pose.translate(width / 2F, height / 2F - lines.size() * lineHeight * scale / 2, 0);
        pose.scale(scale, scale, 1);
        for (int i = 0; i < lines.size(); i++) {
            var line = lines.get(i);
            graphics.drawString(font, line, -font.width(line) / 2, i * lineHeight, color, false);
        }
        pose.popPose();
    }
    public static float envelope(double time, double duration, double fade) {
        float t=(float)Math.max(0,Math.min(1,Math.min(time,duration-time)/fade));
        return t*t*(3-2*t);
    }
}
