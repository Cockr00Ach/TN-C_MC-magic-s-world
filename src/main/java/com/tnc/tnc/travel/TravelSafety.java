package com.tnc.tnc.travel;

import com.tnc.tnc.home.TownProtection;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.Optional;

/**
 * 位移落点计算。全部在服务端跑，客户端只发“我要用技能”，不发坐标。
 *
 * <p>这里只负责“这个点安不安全”，不负责扣魔力/冷却——那些在
 * {@link TravelFormService} 里。所有落点都拒绝：未加载区块、世界边界、
 * 城镇/住宅领地、岩浆/火、以及玩家碰撞箱放不下的位置。
 */
public final class TravelSafety {

    private TravelSafety() {
    }

    /**
     * 暗形态相位：视线前方先撞到一堵墙，再从墙后找第一个能站人的空气点。
     * 没有墙、墙后没有安全点、或落点在领地里，都返回空。
     */
    public static Optional<Vec3> phaseTarget(ServerPlayer player, int maxBlocks) {
        if (maxBlocks <= 0) {
            return Optional.empty();
        }
        Vec3 start = player.position().add(0.0D, 0.1D, 0.0D);
        Vec3 look = player.getLookAngle();
        if (look.y < -0.35D) {
            return Optional.empty();   // 不往脚下/虚空里钻
        }
        Vec3 end = start.add(look.scale(maxBlocks));
        BlockHitResult wall = player.serverLevel().clip(new ClipContext(
                start, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
        if (wall.getType() == HitResult.Type.MISS) {
            return Optional.empty();
        }
        // 从墙面后 0.5 格开始，每 0.25 格找第一个安全点。
        Vec3 probe = wall.getLocation().add(look.scale(0.5D));
        Vec3 limit = start.add(look.scale(maxBlocks + 1.5D));
        for (int i = 0; i < maxBlocks * 6 && probe.distanceToSqr(start) <= limit.distanceToSqr(start); i++) {
            if (safe(player, probe)) {
                return Optional.of(probe);
            }
            probe = probe.add(look.scale(0.25D));
        }
        return Optional.empty();
    }

    /**
     * 光行「光跃」：沿视线瞬移，最远 {@code maxBlocks}。
     *
     * <p>先打一条视线射线决定最远能到哪（撞墙就停在墙前 0.6 格），再从最远处往回
     * 每 0.25 格找一个安全点；视线朝上抬时额外往地面吸最多 3 格，免得悬在半空。
     */
    public static Optional<Vec3> blinkTarget(ServerPlayer player, int maxBlocks) {
        if (maxBlocks <= 0) {
            return Optional.empty();
        }
        Vec3 start = player.position().add(0.0D, 0.1D, 0.0D);
        Vec3 look = player.getLookAngle();
        if (look.y < -0.55D) {
            return Optional.empty();   // 低头不瞬移，免得一脚踩进虚空
        }
        BlockHitResult wall = player.serverLevel().clip(new ClipContext(
                start, start.add(look.scale(maxBlocks)), ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE, player));
        double reach = wall.getType() == HitResult.Type.MISS
                ? maxBlocks
                : wall.getLocation().distanceTo(start) - 0.6D;
        if (reach < 1.5D) {
            return Optional.empty();   // 贴脸是墙：没有可跃的距离
        }
        for (double d = reach; d >= 1.5D; d -= 0.25D) {
            Vec3 probe = start.add(look.scale(d));
            if (safe(player, probe)) {
                return Optional.of(probe);
            }
            for (double drop = 0.5D; drop <= 3.0D; drop += 0.5D) {
                Vec3 down = probe.subtract(0.0D, drop, 0.0D);
                if (safe(player, down)) {
                    return Optional.of(down);
                }
            }
        }
        return Optional.empty();
    }

    /**
     * 土遁「潜地」：顺着水平视线钻进前方地表，最远 {@code maxBlocks}。
     *
     * <p>不要求视线通畅（土遁本来就是钻地），但落点必须是<b>地表</b>：
     * {@code canSeeSky} 挡住"钻到封闭洞穴里憋死"的情况。每前进 1 格从上方
     * 往下扫一次地面，取第一个能站人的点。
     */
    public static Optional<Vec3> burrowTarget(ServerPlayer player, int maxBlocks) {
        if (maxBlocks <= 0) {
            return Optional.empty();
        }
        Vec3 start = player.position();
        Vec3 look = player.getLookAngle();
        Vec3 flat = new Vec3(look.x, 0.0D, look.z);
        if (flat.lengthSqr() < 1.0E-4D) {
            return Optional.empty();
        }
        flat = flat.normalize();
        ServerLevel level = player.serverLevel();
        for (int step = 2; step <= maxBlocks; step++) {
            Vec3 column = start.add(flat.scale(step));
            for (double y = start.y + 4.0D; y >= start.y - 8.0D; y -= 0.5D) {
                Vec3 candidate = new Vec3(column.x, y, column.z);
                BlockPos feet = BlockPos.containing(candidate);
                if (!level.canSeeSky(feet)) {
                    continue;
                }
                if (level.getBlockState(feet.below()).getCollisionShape(level, feet.below()).isEmpty()) {
                    continue;   // 脚下得是实心地面，空中不算"潜地"
                }
                if (safe(player, candidate)) {
                    return Optional.of(candidate);
                }
            }
        }
        return Optional.empty();
    }

    /** 落点是否是 2 格高、无碰撞、非液体、在已加载区块/世界边界内的安全点。 */
    public static boolean safe(ServerPlayer player, Vec3 target) {
        ServerLevel level = player.serverLevel();
        BlockPos feet = BlockPos.containing(target);
        if (!level.hasChunkAt(feet) || !level.hasChunkAt(feet.above())
                || !level.getWorldBorder().isWithinBounds(feet)) {
            return false;
        }
        if (feet.getY() < level.getMinBuildHeight() + 2 || feet.getY() > level.getMaxBuildHeight() - 2) {
            return false;   // 不落进虚空/世界顶
        }
        if (level.dimension() == net.minecraft.world.level.Level.NETHER && feet.getY() >= 126) {
            return false;   // 不穿下界基岩顶
        }
        if (!level.getBlockState(feet).getFluidState().isEmpty()
                || !level.getBlockState(feet.above()).getFluidState().isEmpty()) {
            return false;
        }
        if (level.getBlockState(feet).is(net.minecraft.world.level.block.Blocks.FIRE)
                || level.getBlockState(feet.above()).is(net.minecraft.world.level.block.Blocks.FIRE)
                || level.getBlockState(feet).is(net.minecraft.world.level.block.Blocks.SOUL_FIRE)
                || level.getBlockState(feet.above()).is(net.minecraft.world.level.block.Blocks.SOUL_FIRE)) {
            return false;
        }
        if (TownProtection.denied(player, feet)) {
            return false;
        }
        AABB box = player.getBoundingBox().move(
                target.x - player.getX(), target.y - player.getY(), target.z - player.getZ());
        return level.noCollision(player, box);
    }

    /** 服务端权威传送；客户端发来的坐标一律不用。 */
    public static void teleport(ServerPlayer player, Vec3 target) {
        player.teleportTo(player.serverLevel(), target.x, target.y, target.z,
                player.getYRot(), player.getXRot());
        player.fallDistance = 0.0F;
        player.setDeltaMovement(Vec3.ZERO);
        player.hurtMarked = true;
    }
}
