package com.tnc.tnc.magic.fire;

import com.tnc.tnc.TNMod;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 火系核心资源：<b>热量</b>（2026-11-13 火法重做的地基）。
 *
 * <h2>一句话玩法</h2>
 * 火法不再是"扔更大火球"，而是<b>点火 → 攒热 → 引爆</b>：
 * 每次火系命中给目标叠热量，热量越高它越危险 —— 到燃点自己烧起来，
 * 到过燃当场炸开，而引爆链的法术强度直接乘在热量上。
 *
 * <h2>数值</h2>
 * <ul>
 *   <li>{@link #MAX} 100，{@link #IGNITE_AT} 60 = 燃点，到 100 = 过燃自爆</li>
 *   <li>每秒衰减 {@value #DECAY_PER_SECOND}：打完不去收尾会自己凉下来（所以要"攒着别凉"）</li>
 *   <li>燃点以上受到的火伤 ×{@value #IGNITED_TAKEN_MULTIPLIER}（火法越好越疼，但不夸张）</li>
 * </ul>
 *
 * <h2>为什么单独一个类</h2>
 * 热量要跨法术、跨实体共享（点火链攒的、引爆链花的、火手链按它放大爆炸），
 * 放在任何一个法术里都会被别的链抄一遍 ✗。所以这里是唯一真源：
 * 谁要给目标加热量就调 {@link #add}，谁要花热量就调 {@link #consume}。
 */
@Mod.EventBusSubscriber(modid = TNMod.MODID)
public final class FireHeat {

    /** 热量上限。 */
    public static final int MAX = 100;

    /** 燃点：到这个数就自燃（着火 + 遭受火伤加成）。 */
    public static final int IGNITE_AT = 60;

    /** 过燃：到这个数立刻自爆一次，然后清空。 */
    public static final int OVERHEAT_AT = 100;

    /** 每秒衰减多少。 */
    public static final int DECAY_PER_SECOND = 8;

    /** 燃点以上受到的火系伤害倍率。 */
    public static final float IGNITED_TAKEN_MULTIPLIER = 1.25F;

    /** 过燃自爆：每点热量换多少伤害。 */
    public static final float OVERHEAT_DAMAGE_PER_HEAT = 0.22F;

    /** 过燃自爆半径（格）。 */
    public static final double OVERHEAT_RADIUS = 3.5D;

    /** 记录：uuid → 热量（保留小数，小法术一次 +7.5 这类才不会全是整数跳变）。 */
    private static final Map<UUID, Float> HEAT = new ConcurrentHashMap<>();

    /** 给玩家看数字的节流：uuid → 上次提示的 tick。 */
    private static final Map<UUID, Long> LAST_HINT = new ConcurrentHashMap<>();

    private FireHeat() {
    }

    // ------------------------------------------------------------------ 对外

    /** 当前热量（0 表示没热）。 */
    public static float of(LivingEntity entity) {
        if (entity == null) {
            return 0.0F;
        }
        return HEAT.getOrDefault(entity.getUUID(), 0.0F);
    }

    /** 归一化：0~1，给渲染层用（热度越高火越旺）。 */
    public static float ratio(LivingEntity entity) {
        return Math.min(1.0F, of(entity) / MAX);
    }

    /** 有没有到燃点。 */
    public static boolean ignited(LivingEntity entity) {
        return of(entity) >= IGNITE_AT;
    }

    /**
     * 加热量。到燃点会自燃，到过燃会当场炸开。
     *
     * @param victim 被打的生物
     * @param caster 施法者（可为 null）
     * @param amount 加多少（<=0 直接忽略）
     * @return 加完之后的热量
     */
    public static float add(LivingEntity victim, ServerPlayer caster, float amount) {
        if (victim == null || victim.level().isClientSide() || !victim.isAlive() || amount <= 0.0F) {
            return of(victim);
        }
        // 焚元（燃烧形态 3 档）：所有火系叠热 +50%
        amount *= TNFireForms.heatMultiplier(caster);
        boolean wasIgnited = ignited(victim);
        float now = Math.min(MAX, of(victim) + amount);
        HEAT.put(victim.getUUID(), now);

        if (!wasIgnited && now >= IGNITE_AT) {
            // 燃点：自己烧起来（这是"点火成功"的即时反馈）
            victim.setSecondsOnFire(3);
            if (victim.level() instanceof ServerLevel level) {
                level.sendParticles(net.minecraft.core.particles.ParticleTypes.FLAME,
                        victim.getX(), victim.getY() + victim.getBbHeight() * 0.6, victim.getZ(),
                        14, 0.28, 0.42, 0.28, 0.02);
                level.playSound(null, victim.blockPosition(), SoundEvents.FIRECHARGE_USE,
                        SoundSource.PLAYERS, 0.7F, 0.75F);
            }
        }

        hint(caster, victim, now);

        if (now >= OVERHEAT_AT) {
            overheat(victim, caster, now);
        }
        return now;
    }

    /**
     * 把热量花掉（引爆链用）：返回花掉之前的热量，然后清零。
     *
     * <p>过燃自爆也走这里 —— 它本质是一样的"把攒的热一次花出去"。
     */
    public static float consume(LivingEntity victim) {
        if (victim == null) {
            return 0.0F;
        }
        Float old = HEAT.remove(victim.getUUID());
        return old == null ? 0.0F : old;
    }

    /** 直接清空（死亡/驱散）。 */
    public static void clear(LivingEntity victim) {
        if (victim != null) {
            HEAT.remove(victim.getUUID());
            LAST_HINT.remove(victim.getUUID());
        }
    }

    /** 当前有多少生物带着热量（自检/调试）。 */
    public static int activeCount() {
        return HEAT.size();
    }

    // ------------------------------------------------------------------ 结算

    /**
     * 过燃自爆：按热量算伤害、炸一圈、清空。
     *
     * <p>自爆<b>不再给周围目标加热量</b>（否则一个炸一个会连锁到把整片怪都点着 ✗）——
     * 连锁是引爆链 t2「连爆」的事，不是过燃的默认行为。
     */
    private static void overheat(LivingEntity victim, ServerPlayer caster, float heat) {
        float damage = heat * OVERHEAT_DAMAGE_PER_HEAT;
        if (victim.level() instanceof ServerLevel level) {
            var area = FireSpellRules.uprightArea(victim.position(), OVERHEAT_RADIUS, 2.0D);
            for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, area,
                    t -> FireSpellRules.hittable(caster, t))) {
                float falloff = 1.0F - (float) (target.position().distanceTo(victim.position())
                        / (OVERHEAT_RADIUS + 1.0D));
                if (falloff <= 0.0F) {
                    continue;
                }
                target.hurt(level.damageSources().onFire(), damage * falloff);
                target.setSecondsOnFire(4);
            }
            level.sendParticles(net.minecraft.core.particles.ParticleTypes.EXPLOSION,
                    victim.getX(), victim.getY() + victim.getBbHeight() * 0.5, victim.getZ(),
                    6, 0.4, 0.4, 0.4, 0.0);
            level.sendParticles(net.minecraft.core.particles.ParticleTypes.LAVA,
                    victim.getX(), victim.getY() + victim.getBbHeight() * 0.5, victim.getZ(),
                    24, 0.5, 0.5, 0.5, 0.4);
            level.playSound(null, victim.blockPosition(), SoundEvents.GENERIC_EXPLODE,
                    SoundSource.PLAYERS, 0.9F, 1.15F);
        }
        HEAT.remove(victim.getUUID());
    }

    /** 给玩家报一下热量数字（同一个人每 0.5 秒最多一次）。 */
    private static void hint(ServerPlayer caster, LivingEntity victim, float heat) {
        if (caster == null || caster.level().isClientSide()) {
            return;
        }
        long now = caster.level().getGameTime();
        Long last = LAST_HINT.get(caster.getUUID());
        if (last != null && now - last < 10) {
            return;
        }
        LAST_HINT.put(caster.getUUID(), now);
        String bar = heat >= IGNITE_AT ? "§c§l燃点" : "§e热量";
        caster.displayClientMessage(Component.literal(String.format(
                "§c[TN-C] §r%s §e%.0f §7/ %d", bar, heat, MAX)), true);
    }

    // ------------------------------------------------------------------ 事件

    /**
     * 燃点以上受到<b>火系</b>伤害 +25%。
     *
     * <p>只看 {@code spell_power} 命名空间的伤害（同 {@code WandResonance} 的判据），
     * 近战、摔落、别人的毒都吃不到这个加成 ✓
     */
    @SubscribeEvent
    public static void onHurt(LivingHurtEvent event) {
        LivingEntity victim = event.getEntity();
        if (!ignited(victim)) {
            return;
        }
        var key = event.getSource().typeHolder().unwrapKey()
                .map(k -> k.location()).orElse(null);
        if (key == null || !"spell_power".equals(key.getNamespace())) {
            return;
        }
        event.setAmount(event.getAmount() * IGNITED_TAKEN_MULTIPLIER);
    }

    /** 每秒衰减：打完不追着打，热量会自己掉下去。 */
    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.getServer() == null) {
            return;
        }
        if (event.getServer().getTickCount() % 20 != 0) {
            return;
        }
        if (HEAT.isEmpty()) {
            return;
        }
        for (Map.Entry<UUID, Float> entry : HEAT.entrySet()) {
            float left = entry.getValue() - DECAY_PER_SECOND;
            if (left <= 0.0F) {
                HEAT.remove(entry.getKey());
            } else {
                entry.setValue(left);
            }
        }
    }

    /** 死了就清掉，避免 UUID 复用。 */
    @SubscribeEvent
    public static void onDeath(LivingDeathEvent event) {
        clear(event.getEntity());
    }
}
