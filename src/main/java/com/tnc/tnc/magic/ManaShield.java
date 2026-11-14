package com.tnc.tnc.magic;

import com.tnc.tnc.TNMod;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Arrays;

/**
 * 独立法术：<b>法力护盾</b>（2026-11-15 作者设计）。
 *
 * <h2>规则</h2>
 * <ul>
 *   <li>凝聚周围魔力，生成一面<b>持续 60 秒</b>的护盾</li>
 *   <li>护盾能<b>完全挡下一次任何伤害</b>（不论类型、不论大小），挡完就碎</li>
 *   <li>最多<b>叠 3 层</b>，每层各自计时；<b>无冷却</b></li>
 * </ul>
 *
 * <h2>为什么用 persistentData 存到期时刻的数组</h2>
 * 每一层是独立的 60 秒（先放的那层先到期），存"到期的游戏刻"数组最直接：
 * 不用每 tick 维护一个实体/效果，读的时候顺手把过期的剔掉 ✓
 *
 * <p>纯逻辑（{@link #add} / {@link #consume} / {@link #active}）是静态无副作用的，
 * 单测直接钉，不用起世界 ✓
 */
@Mod.EventBusSubscriber(modid = TNMod.MODID)
public final class ManaShield {

    /** 最多三层。 */
    public static final int MAX_STACKS = 3;

    /** 每层持续 60 秒。 */
    public static final int DURATION_TICKS = 1200;

    private static final String TAG = "TncManaShield";

    private ManaShield() {
    }

    // ------------------------------------------------------------------ 纯逻辑

    /** 剔掉已过期的层，按到期时间升序返回（先到期的在前 = 先被消耗）。 */
    public static long[] prune(long[] expiries, long now) {
        if (expiries == null || expiries.length == 0) {
            return new long[0];
        }
        long[] alive = Arrays.stream(expiries).filter(t -> t > now).sorted().toArray();
        return alive.length > MAX_STACKS ? Arrays.copyOfRange(alive, alive.length - MAX_STACKS, alive.length) : alive;
    }

    /** 当前有几层。 */
    public static int active(long[] expiries, long now) {
        return prune(expiries, now).length;
    }

    /**
     * 加一层。满 3 层时<b>刷新最早到期的那一层</b>（等于"再按一次补满时间"），
     * 而不是什么都没有 —— 无冷却法术按下去必须有反馈 ✓
     */
    public static long[] add(long[] expiries, long now) {
        long[] alive = prune(expiries, now);
        long until = now + DURATION_TICKS;
        if (alive.length < MAX_STACKS) {
            long[] next = Arrays.copyOf(alive, alive.length + 1);
            next[alive.length] = until;
            Arrays.sort(next);
            return next;
        }
        long[] next = alive.clone();
        next[0] = until;
        Arrays.sort(next);
        return next;
    }

    /** 消耗一层（最早到期的那层）。 */
    public static long[] consume(long[] expiries, long now) {
        long[] alive = prune(expiries, now);
        if (alive.length == 0) {
            return alive;
        }
        return Arrays.copyOfRange(alive, 1, alive.length);
    }

    // ------------------------------------------------------------------ 施法

    public static boolean handles(ResourceLocation spellId) {
        return spellId != null && spellId.getPath().equals("mana_shield");
    }

    /** 施放：加一层护盾。 */
    public static void cast(ServerPlayer player, ResourceLocation spellId) {
        if (!handles(spellId) || !(player.level() instanceof ServerLevel level)) {
            return;
        }
        CompoundTag data = player.getPersistentData();
        long now = level.getGameTime();
        long[] before = data.getLongArray(TAG);
        long[] after = add(before, now);
        data.putLongArray(TAG, after);
        int stacks = after.length;
        player.displayClientMessage(Component.literal(
                "§b[TN-C] §r法力护盾 §f" + stacks + " §7/ " + MAX_STACKS + " 层"), true);
        // 凝聚表现：一圈蓝色魔力 + 一层薄光
        Vec3 at = player.position().add(0, player.getBbHeight() * 0.5, 0);
        level.sendParticles(ParticleTypes.END_ROD, at.x, at.y, at.z, 26, 0.55, 0.75, 0.55, 0.02);
        level.sendParticles(ParticleTypes.ENCHANT, at.x, at.y, at.z, 30, 0.6, 0.8, 0.6, 0.05);
        level.playSound(null, player.blockPosition(), SoundEvents.ENCHANTMENT_TABLE_USE,
                SoundSource.PLAYERS, 0.9F, 1.45F);
    }

    // ------------------------------------------------------------------ 挡伤

    /**
     * 挡下<b>一次任何伤害</b>：取消这次伤害、碎一层护盾。
     *
     * <p>唯一不挡的是 {@link DamageTypeTags#BYPASSES_INVULNERABILITY}（虚空、/kill）——
     * 那种"伤害"本来就不该被游戏内手段免疫 ✓
     */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onHurt(LivingHurtEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        if (event.getSource().is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            return;
        }
        CompoundTag data = player.getPersistentData();
        long[] stored = data.getLongArray(TAG);
        if (stored.length == 0 || !(player.level() instanceof ServerLevel level)) {
            return;
        }
        long now = level.getGameTime();
        if (active(stored, now) == 0) {
            data.remove(TAG);
            return;
        }
        long[] left = consume(stored, now);
        if (left.length == 0) {
            data.remove(TAG);
        } else {
            data.putLongArray(TAG, left);
        }
        event.setCanceled(true);
        event.setAmount(0.0F);

        Vec3 at = player.position().add(0, player.getBbHeight() * 0.55, 0);
        level.sendParticles(ParticleTypes.END_ROD, at.x, at.y, at.z, 40, 0.6, 0.7, 0.6, 0.12);
        level.sendParticles(ParticleTypes.GLOW, at.x, at.y, at.z, 24, 0.7, 0.7, 0.7, 0.05);
        level.playSound(null, player.blockPosition(), SoundEvents.GLASS_BREAK,
                SoundSource.PLAYERS, 0.9F, 1.5F);
        player.displayClientMessage(Component.literal(
                left.length == 0 ? "§b[TN-C] §r护盾破碎！" : "§b[TN-C] §r护盾挡下一击 §7（剩 "
                        + left.length + " 层）"), true);
    }

    /** 当前层数（给 HUD / 命令 / 测试用）。 */
    public static int stacks(ServerPlayer player) {
        long[] stored = player.getPersistentData().getLongArray(TAG);
        return active(stored, player.level().getGameTime());
    }
}
