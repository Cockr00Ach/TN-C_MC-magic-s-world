package com.tnc.tnc.magic;

import com.tnc.tnc.TNMod;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.event.entity.living.LivingChangeTargetEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 隐匿的两条硬规则：
 *
 * <ol>
 *   <li><b>不被索敌</b>：敌对生物换目标时，如果新目标是带隐匿的实体，直接取消这次换目标。</li>
 *   <li><b>主动/被动破隐</b>：带隐匿的实体造成伤害或受到伤害，立刻移除效果——
 *       “出手就打断了潜行”，而不是靠玩家手动取消。</li>
 * </ol>
 *
 * <p>注意这里只处理“伤害”事件（{@link LivingHurtEvent}），治疗、给buff、放置方块都不破隐。
 */
@Mod.EventBusSubscriber(modid = TNMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class TNConcealmentEvents {

    private TNConcealmentEvents() {
    }

    @SubscribeEvent
    public static void onChangingTarget(LivingChangeTargetEvent event) {
        LivingEntity target = event.getNewTarget();
        if (target == null) {
            return;
        }
        MobEffect effect = TNEffects.CONCEALMENT.get();
        if (target.hasEffect(effect)) {
            // 取消这次换目标：怪物保持原目标（或继续待机），不会因为玩家靠近而转身。
            event.setNewTarget(null);
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onHurt(LivingHurtEvent event) {
        if (event.getAmount() <= 0.0F) {
            return;
        }
        MobEffect effect = TNEffects.CONCEALMENT.get();

        LivingEntity victim = event.getEntity();
        if (victim.hasEffect(effect)) {
            victim.removeEffect(effect);
        }

        Entity attacker = event.getSource().getEntity();
        if (attacker instanceof LivingEntity living && living.hasEffect(effect)) {
            living.removeEffect(effect);
        }
    }
}
