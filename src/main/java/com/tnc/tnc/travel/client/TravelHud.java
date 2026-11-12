package com.tnc.tnc.travel.client;

import com.tnc.tnc.TNMod;
import com.tnc.tnc.magic.Element;
import com.tnc.tnc.travel.TravelClientState;
import com.tnc.tnc.travel.TravelForms;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Locale;

/**
 * 元素附身 HUD：魔力条上方一条细条。
 *
 * <p>只在<b>附身中</b>或<b>冷却中</b>出现，平时不占屏幕；位置避开
 * {@code MagicStoneHud} 的磨砺条（高-63）和魔力条（高-50）。
 */
@Mod.EventBusSubscriber(modid = TNMod.MODID, value = Dist.CLIENT)
public final class TravelHud {

    private static final int WIDTH = 132;
    private static final int HEIGHT = 8;
    private static final int BOTTOM_MARGIN = 78;

    private TravelHud() {
    }

    @SubscribeEvent
    public static void onRender(RenderGuiEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.options.hideGui || minecraft.player == null || minecraft.screen != null) {
            return;
        }
        boolean active = TravelClientState.active();
        int cooldown = TravelClientState.cooldownTicks();
        if (!active && cooldown <= 0) {
            return;
        }
        GuiGraphics graphics = event.getGuiGraphics();
        int screenWidth = minecraft.getWindow().getGuiScaledWidth();
        int screenHeight = minecraft.getWindow().getGuiScaledHeight();
        int x = (screenWidth - WIDTH) / 2;
        int y = screenHeight - BOTTOM_MARGIN;
        var font = minecraft.font;

        if (!active) {
            String text = "附身冷却 " + seconds(cooldown) + "s";
            graphics.drawString(font, text, screenWidth / 2 - font.width(text) / 2, y,
                    0xFFB0B0B0, true);
            return;
        }

        Element element = TravelClientState.element();
        if (element == null) {
            return;
        }
        int color = TravelForms.color(element);
        graphics.fill(x - 1, y - 1, x + WIDTH + 1, y + HEIGHT + 1, 0xCC101010);
        graphics.fill(x, y, x + WIDTH, y + HEIGHT, 0xFF202020);
        int ratio = Math.max(0, Math.min(WIDTH,
                Math.round(WIDTH * (float) TravelClientState.ticksLeft() / TravelClientState.totalTicks())));
        graphics.fill(x, y, x + ratio, y + HEIGHT, color);

        String time = String.format(Locale.ROOT, "%.1fs", TravelClientState.ticksLeft() / 20.0D);
        String label = TravelForms.elementName(element) + "·" + TravelForms.formName(element)
                + " " + TravelClientState.tier() + "阶 " + time;
        graphics.drawString(font, label, screenWidth / 2 - font.width(label) / 2, y - 10,
                color | 0xFF000000, true);
        if (TravelClientState.skillCooldownTicks() > 0) {
            String skill = TravelForms.skillName(element) + " "
                    + seconds(TravelClientState.skillCooldownTicks()) + "s";
            graphics.drawString(font, skill, x + WIDTH - font.width(skill), y + HEIGHT + 2,
                    0xFFB0B0B0, true);
        } else {
            String hint = "右键 " + TravelForms.skillName(element) + " · 松手退出";
            graphics.drawString(font, hint, x + WIDTH - font.width(hint), y + HEIGHT + 2,
                    0xFFB0B0B0, true);
        }
    }

    private static int seconds(int ticks) {
        return Math.max(0, (ticks + 19) / 20);
    }
}
