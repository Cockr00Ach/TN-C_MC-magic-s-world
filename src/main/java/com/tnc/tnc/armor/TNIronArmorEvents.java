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
        // ★ 2026-10-01：减伤一律【取最高档、不相乘】✓ —— 铁甲 99% / 光耀链按档位 / 光龙鳞甲
        float keep = 1.0F;                     // 1.0 = 不减伤
        if (victim.getItemBySlot(EquipmentSlot.CHEST).getItem() instanceof TNIronArmorItem) {
            keep = Math.min(keep, DAMAGE_TAKEN);          // 0.01
        }
        // ★ 2026-10-02：光翼链删掉了（飞行并进光耀 ✓），它那两档减伤（50% / 75%）也一起没了 ✓；
        //   光龙链的 t2「光龙鳞甲」给 50% ✓；暗龙链的 t2「暗龙鳞甲」同样 50% ✓（它的暗属性镜像 ✓）
        if (has(victim, com.tnc.tnc.magic.TNEffects.LIGHT_DRAGON_SCALES)) {
            keep = Math.min(keep, 0.50F);
        }
        if (has(victim, com.tnc.tnc.magic.TNEffects.DARK_DRAGON_SCALES)) {
            keep = Math.min(keep, 0.50F);
        }
        // ★ 2026-10-01 光系第二条链（治疗/减伤）：t1 25% / t2 50% / t3 50% / t4 70% / t5 70%
        //   —— 同样走"取最高档、不相乘"✓（作者给的数值直接写在这里，一处可调 ✓）
        keep = Math.min(keep, lightChainKeep(victim));
        // ★ 2026-10-10 暗系第一条链「黑夜之手」t4 起：命中后给目标挂"易伤"（**受创加重**）✓
        //   （作者：「命中的敌人和其他玩家增加易伤，t4 开始」）
        //   ⚠️ 它和上面那几档**不同轴**（那些是 keep ≤ 1 的减伤，这个是 taken ≥ 1 的增伤）
        //      ⇒ 这里是**相乘**，不是"取最高档" ✓（99% 减伤的敌人吃到易伤依然只吃 ~1.3%）。
        float taken = vulnerableTaken(victim);
        if (keep < 1.0F || taken > 1.0F) {
            event.setAmount(event.getAmount() * keep * taken);
        }
    }

    /**
     * 易伤：每层 <b>+15%</b> 受创 ✓（t4 = 1 层 ⇒ ×1.15，t5 = 2 层 ⇒ ×1.30）。
     *
     * <p>层数直接读效果的 amplifier（原版那套"amplifier + 1 = 层数" ✓），
     * 所以数值只有两处能调：这里的 {@link #VULNERABLE_PER_LEVEL}，
     * 以及给目标挂效果的那边写的 amplifier（见 {@code TNNightGripEntity}）✓。
     */
    private static final float VULNERABLE_PER_LEVEL = 0.15F;

    private static float vulnerableTaken(LivingEntity victim) {
        if (!has(victim, com.tnc.tnc.magic.TNEffects.VULNERABLE)) {
            return 1.0F;
        }
        net.minecraft.world.effect.MobEffectInstance instance =
                victim.getEffect(com.tnc.tnc.magic.TNEffects.VULNERABLE.get());
        int level = instance == null ? 1 : instance.getAmplifier() + 1;
        return 1.0F + VULNERABLE_PER_LEVEL * level;
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