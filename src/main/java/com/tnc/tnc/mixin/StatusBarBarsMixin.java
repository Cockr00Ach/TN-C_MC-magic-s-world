package com.tnc.tnc.mixin;

import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 接管 {@code whisperingstatusbar} 那三条状态条的归属：
 * <b>血条 / 饱食度交回原版，护甲条（"磨砺条"）让给我们自己画。</b>
 *
 * <h2>为什么能这么干（读它的字节码得出的）</h2>
 * 它的 {@code StatusBarOverlayEvents.onOverlayRender} 逻辑是：
 * <pre>
 *   type = StatusBarType.fromOverlayId(event.getOverlay().id());
 *   if (!StatusBarRenderer.shouldRender(type)) return;   // ← 不取消原版，原版自己画
 *   event.setCanceled(true);                              // ← 只有它要画时才压掉原版
 *   ...画自己那条...
 * </pre>
 * 所以"让它别管这条"就等价于"原版那条回来"，不用去碰原版的任何东西。
 *
 * <h2>三条各自怎么处理</h2>
 * <ul>
 *   <li>{@code player_health} / {@code food_level}：<b>整个跳过</b>它的处理 →
 *       事件不被取消 → <b>原版的心和鸡腿自己画</b>（用户要的"一格一格"）。</li>
 *   <li>{@code armor_level}：<b>取消原版</b>并且不让它画 → 这一行留空，
 *       由我们自己的 {@code MagicStoneHud.drawTemperingBar}（Temperingbar 那把剑）填上。</li>
 *   <li>其它（经验条、气泡、快捷栏配件……）：一概不动，照原样。</li>
 * </ul>
 *
 * <h2>合法性 / 健壮性</h2>
 * 我们<b>没有</b>复制或修改那个 mod 的任何资源与代码（它是 All Rights Reserved），
 * 只是拦了它自己的一个事件处理函数。混入配置里 {@code required:false} +
 * {@code defaultRequire: 0}：哪个整合包没装它、或它改了方法名，这个混入静默失效
 * —— 三条状态条都回到它原来的样子，游戏不会崩。
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
            // 让它别管这两条：不取消事件 → 原版的心/鸡腿照常画出来
            ci.cancel();
        } else if (TNC$ARMOR.equals(id)) {
            // 护甲这一行我们自己画（磨砺条），所以原版的图标也要压掉
            event.setCanceled(true);
            ci.cancel();
        }
    }
}
