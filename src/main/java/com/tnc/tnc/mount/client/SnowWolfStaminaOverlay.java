package com.tnc.tnc.mount.client;

import com.tnc.tnc.mount.LandMount;
import com.tnc.tnc.mount.MountMotion;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = "tnc", value = Dist.CLIENT)
public final class SnowWolfStaminaOverlay {
    private static final int WIDTH = 81;
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

    private static void render(ForgeGui gui, GuiGraphics graphics, float partialTick, int width, int height) {
        var minecraft = Minecraft.getInstance();
        if (minecraft.options.hideGui || linger <= 0 || displayedWolf == null || displayedWolf != riddenWolf()) return;
        int left = 10;
        int right = left + WIDTH;
        int y = height / 2 + 15;
        float fraction = Mth.lerp(partialTick, lastStamina, stamina) / MountMotion.SWIM_CAPACITY;
        int color = stamina <= MountMotion.SWIM_WARNING_THRESHOLD ? 0xFFFF854D
                : stamina <= MountMotion.SWIM_SLOW_THRESHOLD ? 0xFFF4C958 : 0xFF61CAFB;
        graphics.fill(left, y, right, y + 6, 0xDC182631);
        int filled = Mth.ceil((WIDTH - 2) * fraction);
        if (filled > 0) graphics.fill(left + 1, y + 1, left + 1 + filled, y + 5, color);
        Component label = stamina == 0 ? Component.translatable("hud.tnc.snow_wolf.swim_exhausted")
                : Component.translatable("hud.tnc.snow_wolf.swim_stamina",
                        Mth.ceil(100f * stamina / MountMotion.SWIM_CAPACITY));
        graphics.drawString(minecraft.font, label, left, y - 10, color);
    }

    @Mod.EventBusSubscriber(modid = "tnc", bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static final class Registration {
        @SubscribeEvent
        public static void registerOverlay(RegisterGuiOverlaysEvent event) {
            event.registerAbove(VanillaGuiOverlay.AIR_LEVEL.id(), "snow_wolf_stamina", SnowWolfStaminaOverlay::render);
        }
    }
}
