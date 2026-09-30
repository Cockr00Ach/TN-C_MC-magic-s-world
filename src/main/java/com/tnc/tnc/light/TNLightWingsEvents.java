package com.tnc.tnc.light;

import com.tnc.tnc.TNMod;
import com.tnc.tnc.magic.Element;
import com.tnc.tnc.magic.MagicStone;
import com.tnc.tnc.magic.MagicStoneData;
import com.tnc.tnc.magic.TNEffects;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 光翼 —— 光亲和力达到门槛的玩家：挂"光翼"标记 + <b>获得飞行</b> ✓。
 *
 * <p>作者 2026-09-30："我希望是能飞的并且在飞的时候会动" ✓。
 * 飞行用原版能力位（{@code mayfly}）实现 ✓ —— 只要不是创造/观察者，脱掉光翼时会把飞行关掉 ✓。
 * 翅膀的视觉与扇动在客户端 {@code light.client.TNLightWingsRenderer} ✓。
 */
@Mod.EventBusSubscriber(modid = TNMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class TNLightWingsEvents {

    /** 光亲和力达到这个值就给光翼 ✓（想更严格就调高 ✓）。 */
    public static final int LIGHT_AFFINITY_REQUIRED = 3;

    /** 效果每次续 3 秒（每 20 tick 续一次，掉线/退回也很快消失 ✓）。 */
    private static final int REFRESH_TICKS = 60;

    private TNLightWingsEvents() {
    }

    /**
     * 判断玩家身上有没有某个效果 ✓ —— 纯工具方法。
     *
     * <p>★★ 2026-10-01 事故：这个方法原来被误标了 {@code @SubscribeEvent} ✗ ——
     * Forge 的 {@code EventAccessTransformer} 会直接报
     * {@code Illegal private member annotated as @SubscribeEvent} ✗，
     * 并且**连带整批自动订阅者注册失败**（{@code Failed to register automatic subscribers. ModID: tnc}）✗，
     * 于是 {@code CONSTRUCT} 生命周期出错（{@code Failed to complete lifecycle event CONSTRUCT, 1 errors found}）✗
     * ⇒ 整个游戏进入 "broken mod state" ✗ ⇒ **模组自带资源包全都没注册** ✗
     * ⇒ paramagic 找不到自己的 shader 直接 fast-fail 崩端 ✗✗（作者："整合包打开失败"）。
     * 教训：{@code @SubscribeEvent} 只许标在"事件方法"上（public static void、参数是事件）✗，
     * 工具方法一个都不能标 ✓。
     */
    private static boolean has(ServerPlayer player,
                               net.minecraftforge.registries.RegistryObject<
                                       net.minecraft.world.effect.MobEffect> effect) {
        return effect.isPresent() && player.hasEffect(effect.get());
    }

    /** 每 tick（服务端）：有光系链的 buff ⇒ 挂光翼标记 + 开飞行 ✓。 */
    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        if (!(event.player instanceof ServerPlayer player) || player.level().isClientSide()) {
            return;
        }
        // ★ 2026-10-01 作者："这条链的技能使用期间都可以展开光翼飞行" ✓
        //   所以看的是【光系链的任一 buff】是否生效，而不是光亲和力 ✗。
        boolean winged = has(player, TNEffects.LIGHT_FLIGHT)
                || has(player, TNEffects.LIGHT_SWIFT_FLIGHT)
                || has(player, TNEffects.LIGHT_WINGSPAN);

        if (TNEffects.LIGHT_WINGS.isPresent()) {
            if (winged) {
                player.addEffect(new MobEffectInstance(TNEffects.LIGHT_WINGS.get(),
                        REFRESH_TICKS, 0, false, false, false));
            } else {
                player.removeEffect(TNEffects.LIGHT_WINGS.get());
            }
        }

        boolean canFlyOnItsOwn = player.isCreative() || player.isSpectator();
        if (winged) {
            if (!player.getAbilities().mayfly) {
                player.getAbilities().mayfly = true;
                player.onUpdateAbilities();
            }
        } else if (!canFlyOnItsOwn) {
            if (player.getAbilities().mayfly || player.getAbilities().flying) {
                player.getAbilities().mayfly = false;
                player.getAbilities().flying = false;
                player.onUpdateAbilities();
            }
        }
    }
}