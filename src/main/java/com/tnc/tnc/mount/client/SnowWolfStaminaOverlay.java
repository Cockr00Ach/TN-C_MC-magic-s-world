package com.tnc.tnc.mount.client;

import com.tnc.tnc.mount.LandMount;
import com.tnc.tnc.mount.MountMotion;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = "tnc", value = Dist.CLIENT)
public final class SnowWolfStaminaOverlay {
    private static final int FULL_DISPLAY_TICKS = 20;
    private static LandMount displayedWolf;
    private static int lastStamina;
    private static int stamina;
    private static int linger;

    private SnowWolfStaminaOverlay() {}

    private static LandMount riddenWolf() {
        var rider = Minecraft.getInstance().player;
        return rider != null && rider.isAlive() && rider.getVehicle() instanceof LandMount wolf
                && wolf.snow() && wolf.getControllingPassenger() == rider ? wolf : null;
    }

    @SubscribeEvent
    public static void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        LandMount wolf = riddenWolf();
        if (wolf == null) {
            displayedWolf = null;
            linger = 0;
            return;
        }
        int current = wolf.swimStamina();
        lastStamina = displayedWolf == wolf ? stamina : current;
        stamina = current;
        displayedWolf = wolf;
        if (wolf.isSurfaceSwimming() || stamina < MountMotion.SWIM_CAPACITY) linger = FULL_DISPLAY_TICKS;
        else if (linger > 0) --linger;
    }

    public static void render(Minecraft minecraft, GuiGraphics graphics, float partialTick, int x, int y, int width) {
        if (linger <= 0 || displayedWolf == null || displayedWolf != riddenWolf()) return;
        final int ice = 0xFF7DE5F5;
        final int amber = 0xFFF4C958;
        final int exhausted = 0xFFF58666;
        int accent = stamina <= MountMotion.SWIM_WARNING_THRESHOLD ? exhausted
                : stamina <= MountMotion.SWIM_SLOW_THRESHOLD ? amber : ice;
        int end = x + width;

        graphics.fill(x, y + 2, end, y + 11, 0xF3131B26);
        graphics.fill(x + 2, y + 1, end - 2, y + 12, 0xF3131B26);
        graphics.fill(x + 2, y + 1, end - 2, y + 2, 0xFF4A6576);
        graphics.fill(x + 2, y + 11, end - 2, y + 12, 0xFF273C4D);
        graphics.fill(x + 2, y + 2, x + 5, y + 3, accent);
        graphics.fill(end - 5, y + 2, end - 2, y + 3, accent);

        // Three stepped rows form the ice crystal on the left end.
        graphics.fill(x + 6, y + 3, x + 7, y + 10, accent);
        graphics.fill(x + 4, y + 5, x + 9, y + 8, accent);
        graphics.fill(x + 5, y + 6, x + 8, y + 7, 0xFFF1FDFF);

        int track = x + 12;
        int trackEnd = end - 3;
        graphics.fill(track, y + 7, trackEnd, y + 10, 0xFF253746);
        float fraction = Mth.lerp(partialTick, lastStamina, stamina) / MountMotion.SWIM_CAPACITY;
        int filled = track + Mth.ceil((trackEnd - track) * fraction);
        if (filled > track) {
            graphics.fill(track, y + 8, filled, y + 10, accent);
            graphics.fill(track, y + 7, filled, y + 8, 0xFFDDEFF4);
        }
        Component label = stamina == 0 ? Component.translatable("hud.tnc.snow_wolf.swim_exhausted")
                : Component.translatable("hud.tnc.snow_wolf.swim_stamina",
                        Mth.ceil(100f * stamina / MountMotion.SWIM_CAPACITY));
        graphics.pose().pushPose();
        graphics.pose().translate((track + trackEnd) / 2f, y + 2, 0);
        float textScale = Math.min(.5f, (trackEnd - track) / (float) Math.max(1, minecraft.font.width(label)));
        graphics.pose().scale(textScale, textScale, 1);
        graphics.drawCenteredString(minecraft.font, label, 0, 0, 0xFFEAF9FF);
        graphics.pose().popPose();
    }
}
