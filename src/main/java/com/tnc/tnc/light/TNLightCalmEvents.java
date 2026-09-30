package com.tnc.tnc.light;

import com.tnc.tnc.TNMod;
import com.tnc.tnc.magic.TNEffects;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 光系 t5「天使的悲悯」的**怪物停手** ✓（作者 2026-10-01："范围内的怪物停止攻击五秒钟"）。
 *
 * <p>做法：法术给范围内的怪挂 {@code tnc:light_calm}（5 秒 ✓），这里**每 tick 把它们的攻击目标清掉** ✓ ——
 * 原版的选目标 goal 每 tick 都会重新锁人 ✗，所以只挂效果是不够的 ✓。
 * 伤害层面的最后一道保险在 {@code armor/TNIronArmorEvents.onCalmAttacker}（直接取消伤害 ✓）。
 *
 * <p>为什么用 {@code LivingTickEvent}：它是"每个生物每 tick 一次"✓，判据只有"有没有那个效果"✓，
 * 代价极低 ✓（比"每 tick 遍历玩家周围的怪"便宜且覆盖完整 ✓）。
 */
@Mod.EventBusSubscriber(modid = TNMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class TNLightCalmEvents {

    private TNLightCalmEvents() {
    }

    @SubscribeEvent
    public static void onLivingTick(LivingEvent.LivingTickEvent event) {
        if (!(event.getEntity() instanceof Mob mob) || mob.level().isClientSide()) {
            return;
        }
        if (!(mob instanceof Enemy)) {
            return;                              // 只对怪物生效 ✓（村民之类本来也不打人 ✓）
        }
        if (TNEffects.LIGHT_CALM.isPresent() && mob.hasEffect(TNEffects.LIGHT_CALM.get())) {
            mob.setTarget(null);
            mob.setLastHurtByMob(null);
        }
    }
}
