package com.tnc.tnc.armor;

import com.tnc.tnc.TNMod;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 铁甲的 99% 减伤 ✓ —— 只在这里实现（一道乘法 ✓）。
 *
 * <p>为什么用事件而不是护甲属性：原版护甲减伤有上限（约 80%）✗，写多少点护甲都到不了 99% ✓；
 * 而 {@code LivingHurtEvent} 里直接改数值就是"最终减伤" ✓（对近战/远程/魔法等都生效 ✓）。
 */
@Mod.EventBusSubscriber(modid = TNMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class TNIronArmorEvents {

    /** 只吃 1% 伤害 = 99% 减伤 ✓（想改成 90% 就写成 0.1F ✓）。 */
    private static final float DAMAGE_TAKEN = 0.01F;

    private TNIronArmorEvents() {
    }

    @SubscribeEvent
    public static void onLivingHurt(LivingHurtEvent event) {
        LivingEntity victim = event.getEntity();
        if (victim.level().isClientSide()) {
            return;
        }
        // ★ 2026-10-01：减伤一律【取最高档、不相乘】✓ —— 铁甲 99% / 光翼展开 50% / 极速飞行 25% ✓
        float keep = 1.0F;                     // 1.0 = 不减伤
        if (victim.getItemBySlot(EquipmentSlot.CHEST).getItem() instanceof TNIronArmorItem) {
            keep = Math.min(keep, DAMAGE_TAKEN);          // 0.01
        }
        if (com.tnc.tnc.magic.TNEffects.LIGHT_WINGSPAN.isPresent()
                && victim.hasEffect(com.tnc.tnc.magic.TNEffects.LIGHT_WINGSPAN.get())) {
            keep = Math.min(keep, 0.50F);
        } else if (com.tnc.tnc.magic.TNEffects.LIGHT_SWIFT_FLIGHT.isPresent()
                && victim.hasEffect(com.tnc.tnc.magic.TNEffects.LIGHT_SWIFT_FLIGHT.get())) {
            keep = Math.min(keep, 0.75F);
        }
        // ★ 2026-10-01 光系第二条链（治疗/减伤）：t1 25% / t2 50% / t3 50% / t4 70% / t5 70%
        //   —— 同样走"取最高档、不相乘"✓（作者给的数值直接写在这里，一处可调 ✓）
        keep = Math.min(keep, lightChainKeep(victim));
        if (keep < 1.0F) {
            event.setAmount(event.getAmount() * keep);
        }
    }

    /** 光系第二条链的减伤（返回"该吃多少"，1.0 = 不减伤 ✓）。 */
    private static float lightChainKeep(LivingEntity victim) {
        var e = com.tnc.tnc.magic.TNEffects.class;
        if (has(victim, com.tnc.tnc.magic.TNEffects.LIGHT_MERCY)) return 0.30F;      // t5 天使的悲悯 70%
        if (has(victim, com.tnc.tnc.magic.TNEffects.LIGHT_DESCENT)) return 0.30F;    // t4 天使降临 70%
        if (has(victim, com.tnc.tnc.magic.TNEffects.LIGHT_DIVINE)) return 0.50F;     // t3 神光 50%
        if (has(victim, com.tnc.tnc.magic.TNEffects.LIGHT_HOLY)) return 0.50F;       // t2 圣光 50%
        if (has(victim, com.tnc.tnc.magic.TNEffects.LIGHT_RADIANCE)) return 0.75F;   // t1 光芒照耀 25%
        return 1.0F;
    }

    private static boolean has(LivingEntity entity,
                               net.minecraftforge.registries.RegistryObject<
                                       net.minecraft.world.effect.MobEffect> effect) {
        return effect.isPresent() && entity.hasEffect(effect.get());
    }

    /**
     * 光系 t5「天使的悲悯」：带 {@code tnc:light_calm} 的怪物**打出的伤害直接取消** ✓。
     *
     * <p>这是"停手五秒"的最后一道保险 ✓ —— {@code light/TNLightCalmEvents} 每 tick 已经在清它们的
     * 攻击目标了，但它可能已经挥出去了（或者被别的东西触发了攻击 ✓）⇒ 这里再按攻击者判一次 ✓。
     */
    @SubscribeEvent
    public static void onCalmAttacker(LivingHurtEvent event) {
        if (event.getSource().getEntity() instanceof LivingEntity attacker
                && has(attacker, com.tnc.tnc.magic.TNEffects.LIGHT_CALM)) {
            event.setAmount(0.0F);
        }
    }
}