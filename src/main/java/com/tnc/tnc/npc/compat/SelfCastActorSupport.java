package com.tnc.tnc.npc.compat;

import com.mojang.logging.LogUtils;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;

import java.util.Comparator;
import java.util.List;

/**
 * 施法动作演示（演示角色）的<b>唯一入口</b> —— 第三种"知道 GeckoLib 存在与否"的地方 ✓
 * （另两个是 {@link GeoSelfSupport} 和 {@link NpcAnimationTrigger}，写法完全一样）。
 *
 * <p>为什么要有它：用户 2026-10-30 在 Blockbench 里给玩家录了三条施法动作
 * （{@code wavingstaff / magicsummon / playmagic_1}，见
 * {@code assets/tnc/animations/entity/player_magic-animation.json}），
 * 要"在游戏里看得见"。方法是在玩家跟前放一个<b>持杖版 Self</b>（同一个实体类型
 * {@code tnc:self}，开关是 {@code SelfBedrockNpcEntity#setWand}），
 * 然后点播那三条动作 —— 拿命令 {@code /tnc anim ...} 随时切。
 *
 * <ul>
 *   <li>{@link #available()} 只用 {@code ModList} 判断，不碰 GeckoLib 的类 ✓；</li>
 *   <li>真正引用 GeckoLib / {@code SelfBedrockNpcEntity} 的代码关在内部类 {@code Geo} 里，
 *       JVM 用到才解析 ✓；</li>
 *   <li>所有失败路径**只回 false**，绝不把游戏带下水 ✓。</li>
 * </ul>
 */
public final class SelfCastActorSupport {

    private static final Logger LOGGER = LogUtils.getLogger();

    /** 演示角色默认离玩家多远（格）。2.2 = 刚好一屏之内、又不会糊在脸上。 */
    public static final double DEFAULT_DISTANCE = 2.2D;

    /** 找演示角色时的搜索半径（格）。 */
    private static final double SEARCH_RADIUS = 24.0D;

    private SelfCastActorSupport() {
    }

    /** 装了 GeckoLib 吗？（不引用 GeckoLib 的任何类 ✓） */
    public static boolean available() {
        return GeoSelfSupport.available();
    }

    /**
     * 在玩家正前方放一个持杖演示角色（面朝玩家），并把这个坐标上原来的演示角色清掉。
     *
     * @return 放出来的实体；没装 GeckoLib / 生成失败 → {@code null}
     */
    public static Entity spawnDemo(ServerPlayer player, double distance) {
        if (!available()) {
            return null;
        }
        return Geo.spawn(player.serverLevel(), player, distance);
    }

    /** 找玩家附近最近的持杖演示角色；没有 → {@code null}。 */
    public static Entity nearestDemo(ServerPlayer player) {
        if (!available()) {
            return null;
        }
        return Geo.nearest(player);
    }

    /**
     * 让演示角色播一条动作。
     *
     * @param loop true = 循环（观察用）；false = 播完回站姿
     * @return 真的播了没有
     */
    public static boolean play(Entity actor, String animation, boolean loop) {
        if (actor == null || animation == null) {
            return false;
        }
        if (!available()) {
            return false;
        }
        return Geo.play(actor, animation, loop);
    }

    /** 让演示角色停下、回站姿。 */
    public static void stop(Entity actor) {
        if (actor == null || !available()) {
            return;
        }
        Geo.stop(actor);
    }

    /** 删掉玩家附近所有演示角色，回报删了几个。 */
    public static int removeAll(ServerPlayer player) {
        if (!available()) {
            return 0;
        }
        return Geo.removeAll(player);
    }

    /**
     * 真正碰 GeckoLib / {@code SelfBedrockNpcEntity} 的地方 ——
     * 只有 {@link #available()} 为真时才会被加载 ✓。
     */
    private static final class Geo {

        static Entity spawn(ServerLevel level, ServerPlayer player, double distance) {
            Vec3 look = player.getLookAngle();
            double x = player.getX() + look.x * distance;
            double z = player.getZ() + look.z * distance;
            double y = player.getY();

            // 这一带已经有一个演示角色 → 挪过去重新面朝你（反复敲命令不该越堆越多）
            Entity existing = nearest(player);
            if (existing != null) {
                existing.moveTo(x, y, z, player.getYRot() + 180.0F, 0.0F);
                face(existing, player);
                return existing;
            }

            // 实体类型仍是 tnc:self（注册类型是 SelfNpcEntity），装 GeckoLib 时实例是 Bedrock 版
            com.tnc.tnc.npc.SelfNpcEntity created = com.tnc.tnc.npc.TNNpcs.SELF.get().create(level);
            if (created == null) {
                LOGGER.warn("TN-C cast demo: entity type tnc:self could not be created");
                return null;
            }
            created.moveTo(x, y, z, player.getYRot() + 180.0F, 0.0F);
            if (created instanceof com.tnc.tnc.npc.SelfBedrockNpcEntity bedrock) {
                bedrock.setWand(true);
            } else {
                LOGGER.warn("TN-C cast demo: tnc:self is not the Bedrock variant (no GeckoLib?) - wand unavailable");
                created.discard();
                return null;
            }
            created.setNoAi(true);
            created.setInvulnerable(true);
            created.setCustomName(net.minecraft.network.chat.Component.literal("施法演示 / Cast Demo"));
            created.setCustomNameVisible(true);
            level.addFreshEntity(created);
            face(created, player);
            LOGGER.info("TN-C cast demo: spawned wand Self at {}/{}/{}", x, y, z);
            return created;
        }

        /** 让实体面朝玩家（身体 + 头一起转，否则只有头转过来看着很怪）。 */
        static void face(Entity actor, ServerPlayer player) {
            double dx = player.getX() - actor.getX();
            double dz = player.getZ() - actor.getZ();
            float yaw = (float) (Math.toDegrees(Math.atan2(dz, dx))) - 90.0F;
            actor.moveTo(actor.getX(), actor.getY(), actor.getZ(), yaw, 0.0F);
            actor.setYHeadRot(yaw);
        }

        static Entity nearest(ServerPlayer player) {
            List<Entity> found = player.serverLevel().getEntities(
                    net.minecraft.world.level.entity.EntityTypeTest.forClass(Entity.class),
                    new net.minecraft.world.phys.AABB(player.blockPosition()).inflate(SEARCH_RADIUS),
                    e -> e instanceof com.tnc.tnc.npc.SelfBedrockNpcEntity self && self.isWand());
            return found.stream()
                    .min(Comparator.comparingDouble(e -> e.distanceToSqr(player)))
                    .orElse(null);
        }

        static boolean play(Entity actor, String animation, boolean loop) {
            if (!(actor instanceof com.tnc.tnc.npc.SelfBedrockNpcEntity self)) {
                return false;
            }
            try {
                boolean ok = self.playCastAnimation(animation, loop);
                if (!ok) {
                    LOGGER.info("TN-C cast demo: '{}' rejected (not a wand Self or unknown animation)", animation);
                }
                return ok;
            } catch (Throwable t) {
                LOGGER.warn("TN-C cast demo: failed to play '{}' -> {}", animation, t.toString());
                return false;
            }
        }

        static void stop(Entity actor) {
            if (actor instanceof com.tnc.tnc.npc.SelfBedrockNpcEntity self) {
                self.stopCastAnimation();
            }
        }

        static int removeAll(ServerPlayer player) {
            List<Entity> found = player.serverLevel().getEntities(
                    net.minecraft.world.level.entity.EntityTypeTest.forClass(Entity.class),
                    new net.minecraft.world.phys.AABB(player.blockPosition()).inflate(SEARCH_RADIUS),
                    e -> e instanceof com.tnc.tnc.npc.SelfBedrockNpcEntity self && self.isWand());
            found.forEach(Entity::discard);
            return found.size();
        }
    }
}
