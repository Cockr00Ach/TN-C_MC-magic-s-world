package com.tnc.tnc.mixin;

import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Let TN-C own health, hunger and armor drawing through public Forge GUI events.
 * This optional mixin skips the third-party status renderer for those overlays;
 * HealthGaugeHud/HungerGaugeHud cancel vanilla icons, MagicStoneHud draws armor.
 * Does not copy or modify the third-party mod's resources.
 */
@Mixin(targets = "com.lirxowo.whisperingstatusbar.client.statusbar.event.StatusBarOverlayEvents", remap = false)
public class StatusBarBarsMixin {

    private static final ResourceLocation TNC$HEALTH = ResourceLocation.withDefaultNamespace("player_health");
    private static final ResourceLocation TNC$FOOD = ResourceLocation.withDefaultNamespace("food_level");
    private static final ResourceLocation TNC$ARMOR = ResourceLocation.withDefaultNamespace("armor_level");

    @Inject(method = "onOverlayRender", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private static void tnc$claimBars(RenderGuiOverlayEvent.Pre event, CallbackInfo ci) {
        ResourceLocation id = event.getOverlay().id();
        if (TNC$HEALTH.equals(id) || TNC$FOOD.equals(id)) {
            // 专属生命/饱食度 HUD 各自取消原版；这里仅跳过第三方绘制。
            ci.cancel();
        } else if (TNC$ARMOR.equals(id)) {
            // 护甲这一行我们自己画（磨砺条），所以原版的图标也要压掉
            event.setCanceled(true);
            ci.cancel();
        }
    }
}
