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

    @SubscribeEvent
    private static boolean has(ServerPlayer player,
                               net.minecraftforge.registries.RegistryObject<
                                       net.minecraft.world.effect.MobEffect> effect) {
        return effect.isPresent() && player.hasEffect(effect.get());
    }
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