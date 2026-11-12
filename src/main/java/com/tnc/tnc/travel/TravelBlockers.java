package com.tnc.tnc.travel;

import com.tnc.tnc.bosshud.ServerBossHud;
import com.tnc.tnc.combat.DownedCombat;
import com.tnc.tnc.dungeon.DungeonRealm;
import com.tnc.tnc.dungeon.DungeonService;
import com.tnc.tnc.skyacademy.AcademyLessons;
import com.tnc.tnc.world.eartharena.EarthArenaEncounter;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;

/**
 * 附身的区域/状态拦截。只在服务端用；返回 {@code null} 表示可以进。
 *
 * <p>城镇/住宅**允许进入和飞越**，只有相位/土遁/光行的落点才查领地——
 * 否则玩家在自家院子里都不能起飞，手感会很怪。
 */
public final class TravelBlockers {

    private TravelBlockers() {
    }

    public static Component enterDenied(ServerPlayer player) {
        if (!player.isAlive() || player.isSpectator()) {
            return Component.literal("当前状态无法附身");
        }
        if (DownedCombat.isDowned(player) || DungeonService.recovering(player)) {
            return Component.literal("倒地/恢复中无法附身");
        }
        if (DungeonRealm.inside(player.level()) || DungeonService.active(player)) {
            return Component.literal("地下城中无法附身");
        }
        if (EarthArenaEncounter.participant(player)) {
            return Component.literal("土之试炼中无法附身");
        }
        if (bossNear(player)) {
            return Component.literal("首领战中无法附身");
        }
        if (AcademyLessons.trainingProtected(player, player.blockPosition())) {
            return Component.literal("演武场教学区内无法附身");
        }
        if (player.isPassenger() || player.isSleeping()) {
            return Component.literal("载具/床上无法附身");
        }
        if (player.isFallFlying()) {
            return Component.literal("滑翔中无法附身");
        }
        if (!player.level().getWorldBorder().isWithinBounds(player.blockPosition())) {
            return Component.literal("世界边界外无法附身");
        }
        return null;
    }

    /** 48 格内有 TN-C 认识的存活 Boss。 */
    public static boolean bossNear(ServerPlayer player) {
        return !player.serverLevel().getEntitiesOfClass(LivingEntity.class,
                player.getBoundingBox().inflate(48.0D),
                e -> e.isAlive() && ServerBossHud.theme(e) != null).isEmpty();
    }

    /** 暗形态进入时清掉 32 格内怪物已有的目标。 */
    public static void clearMobTargets(ServerPlayer player) {
        for (Mob mob : player.serverLevel().getEntitiesOfClass(Mob.class,
                player.getBoundingBox().inflate(32.0D), Mob::isAlive)) {
            if (mob.getTarget() == player) {
                mob.setTarget(null);
            }
        }
    }
}
