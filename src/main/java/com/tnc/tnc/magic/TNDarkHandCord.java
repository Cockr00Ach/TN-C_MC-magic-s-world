package com.tnc.tnc.magic;

import com.tnc.tnc.TNMod;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.spell_engine.api.spell.Spell;
import net.spell_engine.entity.SpellProjectile;

import java.util.List;

/**
 * ★ <b>黑夜之手的"黑索"</b>：把飞出去的手腕和施法者的**后背**用一条黑线连起来 ✓
 *
 * <p>作者 2026-10-10：「**黑带很怪，要那种从我的后背伸出来的感觉不是黑雾，
 * 可以做一个黑线连接着手腕跟后背**」。
 *
 * <h2>为什么线不能做进模型</h2>
 * 手会飞离施法者 ✗ —— 烤进模型的那条"黑带"长度是固定的，
 * 所以要么够不到施法者、要么在近处穿进他身体里 ✗。
 * 线必须**每一帧按"施法者后背 → 当前手位置"重新算** ✓，那是渲染/粒子的活，不是模型的活 ✓。
 *
 * <h2>实现选择：粒子连成的线</h2>
 * 服务端每 tick 沿「后背 → 手」这条线段等距撒一小颗 `squid_ink` ✓。
 * 为什么不用真正的几何线：那要自己写世界空间渲染（`RenderLevelStageEvent` + 自建 RenderType），
 * 风险与工作量都大得多 ✗；而**密到 0.35 格间距的粒子本来就成线** ✓，
 * 且它不像"雾"（作者明确说雾该留给黑雾链 ✓）—— 一条绷紧的线不是云 ✓。
 *
 * <p>⚠ 间隔 {@link #SPACING} 越小线越实、粒子越多 ⇒ 卡就把它调大，或把
 * {@link #MAX_POINTS} 调小 ✓。
 */
@Mod.EventBusSubscriber(modid = TNMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class TNDarkHandCord {

    /** 只有这条链的手才拉线（与 {@code DarkFogCloudEntity} 用 group 判断同一个办法 ✓）。 */
    private static final String HAND_GROUP = "dark_hand";

    /** 只在施法者这个半径内找自己的手 ✓（手最多飞 range=48~52 ⇒ 56 够用）。 */
    private static final double CORD_RANGE = 56.0D;

    /** 线上每多少格一颗粒子 ✓（0.35 已经很密；卡就调大）。 */
    private static final double SPACING = 0.35D;

    /** 一条线最多几颗（防止 t5 的 32 格大手拉出几百颗）✓。 */
    private static final int MAX_POINTS = 48;

    /** 起点往外挪一点，让线看起来是**从后背**出来的，而不是从脚底 ✓。 */
    private static final double BACK_OFFSET = 0.45D;

    private TNDarkHandCord() {
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !net.minecraftforge.fml.ModList.get().isLoaded("spell_engine")) {
            return;
        }
        if (!(event.player instanceof ServerPlayer player)) {
            return;
        }
        tick(player);
    }

    private static void tick(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        List<SpellProjectile> flying = level.getEntitiesOfClass(SpellProjectile.class,
                player.getBoundingBox().inflate(CORD_RANGE));
        if (flying.isEmpty()) {
            return;
        }
        for (SpellProjectile bolt : flying) {
            Spell spell = bolt.getSpell();
            if (spell == null || !HAND_GROUP.equals(spell.group)) {
                continue;                       // 不是黑夜之手（比如以伤换伤的血爪）⇒ 不拉线 ✓
            }
            Entity owner = bolt.getOwner();
            if (owner != player) {
                continue;                       // 别人的手不归我画 ✓
            }
            cord(level, player, bolt);
        }
    }

    /** 沿「施法者后背 → 手」这一条线段撒一串黑粒子 ✓。 */
    private static void cord(ServerLevel level, ServerPlayer player, SpellProjectile bolt) {
        Vec3 look = player.getLookAngle();
        double sx = player.getX() - look.x * BACK_OFFSET;
        double sy = player.getY() + player.getBbHeight() * 0.65D;
        double sz = player.getZ() - look.z * BACK_OFFSET;
        double ex = bolt.getX();
        double ey = bolt.getY();
        double ez = bolt.getZ();

        double dx = ex - sx;
        double dy = ey - sy;
        double dz = ez - sz;
        double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (distance < 0.6D) {
            return;                             // 手刚出膛，还没拉开距离 ⇒ 不用画 ✓
        }
        int points = (int) Math.min(MAX_POINTS, Math.max(2.0D, distance / SPACING));
        for (int i = 0; i <= points; i++) {
            double t = i / (double) points;
            level.sendParticles(ParticleTypes.SQUID_INK,
                    sx + dx * t, sy + dy * t, sz + dz * t,
                    1,                       // 每个点 1 颗 ⇒ 靠"密"成线，不靠"多" ✓
                    0.0D, 0.0D, 0.0D,        // 零扩散：线才绷得住 ✓
                    0.0D);
        }
    }
}
