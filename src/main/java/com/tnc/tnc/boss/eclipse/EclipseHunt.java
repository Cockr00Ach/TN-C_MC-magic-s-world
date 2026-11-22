package com.tnc.tnc.boss.eclipse;

import com.tnc.tnc.TNMod;
import com.tnc.tnc.world.skyhaven.SkyhavenContent;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.UUID;

/** Owns the single encounter in each loaded Skyhaven world. */
@Mod.EventBusSubscriber(modid = TNMod.MODID)
public final class EclipseHunt {
    static final double SEA_SURFACE = 49;
    static final double DEEP_ENTRY_Y = 41;
    static final double TRACK_RANGE = 72;
    static final int EXPOSURE_TICKS = 52;
    static final int WARNING_TICK = 16;
    private static final int REAPPEAR_DELAY = 160;
    private static final Map<ServerLevel, Hunt> HUNTS = new IdentityHashMap<>();

    private EclipseHunt() {}

    static boolean huntable(ServerPlayer player) {
        return player.isAlive() && !player.isCreative() && !player.isSpectator()
                && player.level().dimension().equals(SkyhavenContent.KEY)
                && player.getY() < SEA_SURFACE && player.isInFluidType(SkyhavenContent.TYPE)
                && seaColumn(player.serverLevel(), player.getX(), player.getZ());
    }

    @SubscribeEvent
    public static void tick(TickEvent.LevelTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.level instanceof ServerLevel level)
                || !level.dimension().equals(SkyhavenContent.KEY)) return;
        if (level.getDifficulty() == net.minecraft.world.Difficulty.PEACEFUL) {
            clear(level);
            return;
        }
        HUNTS.computeIfAbsent(level, ignored -> new Hunt()).tick(level);
    }

    @SubscribeEvent
    public static void unload(LevelEvent.Unload event) {
        if (event.getLevel() instanceof ServerLevel level) clear(level);
    }

    private static void clear(ServerLevel level) {
        Hunt hunt = HUNTS.remove(level);
        if (hunt != null && hunt.active != null) hunt.active.discard();
    }

    /** Require the generated sea layer, so a bucket of abyssal water on the island stays safe. */
    static boolean seaColumn(ServerLevel level, double x, double z) {
        var pos = BlockPos.containing(x, SEA_SURFACE - 1, z);
        var chunk = level.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4);
        return chunk != null && chunk.getFluidState(pos).getFluidType() == SkyhavenContent.TYPE;
    }

    /** Collision and movement never request missing chunks. Tail and fin membranes are visual only. */
    static boolean room(ServerLevel level, EclipseLeviathanEntity entity, Vec3 at) {
        var body = new AABB(at.x - 2.75, at.y - 1.75, at.z - 2.75,
                at.x + 2.75, at.y + 2.75, at.z + 2.75);
        if (!level.getWorldBorder().isWithinBounds(body) || !seaColumn(level, at.x, at.z)) return false;
        var min = BlockPos.containing(body.minX, body.minY, body.minZ);
        var max = BlockPos.containing(body.maxX, body.maxY, body.maxZ);
        for (int cx = min.getX() >> 4; cx <= max.getX() >> 4; cx++) {
            for (int cz = min.getZ() >> 4; cz <= max.getZ() >> 4; cz++) {
                if (level.getChunkSource().getChunkNow(cx, cz) == null) return false;
            }
        }
        return !level.getBlockCollisions(entity, body).iterator().hasNext();
    }

    private static final class Hunt {
        private final Map<UUID, Integer> exposure = new HashMap<>();
        private EclipseLeviathanEntity active;
        private long readyAt;

        private void tick(ServerLevel level) {
            if (active != null) {
                if (!active.isRemoved() && level.getGameTime() < active.expiresAt()) return;
                active.discard();
                active = null;
                readyAt = level.getGameTime() + REAPPEAR_DELAY;
            }
            if (level.getGameTime() < readyAt) return;
            exposure.keySet().removeIf(id -> !(level.getPlayerByUUID(id) instanceof ServerPlayer player)
                    || !huntable(player));
            for (ServerPlayer player : level.players()) {
                if (!huntable(player)) continue;
                int wetTicks = exposure.merge(player.getUUID(), 1, (previous, step) -> Math.min(EXPOSURE_TICKS, previous + step));
                if (wetTicks == WARNING_TICK) warn(level, player);
                if (wetTicks == EXPOSURE_TICKS && level.getGameTime() % 5 == 0
                        && (active = spawn(level, player)) != null) {
                    exposure.clear();
                    return;
                }
            }
        }

        private static void warn(ServerLevel level, ServerPlayer player) {
            level.playSound(null, player.getX(), SEA_SURFACE, player.getZ(), SoundEvents.WARDEN_HEARTBEAT,
                    SoundSource.HOSTILE, 2.0F, .65F);
            level.sendParticles(ParticleTypes.REVERSE_PORTAL, player.getX(), SEA_SURFACE - .1, player.getZ(),
                    18, 4.5, .1, 4.5, .045);
        }

        private static EclipseLeviathanEntity spawn(ServerLevel level, ServerPlayer target) {
            double start = level.random.nextDouble() * Math.PI * 2;
            for (int i = 0; i < 12; i++) {
                double angle = start + i * Math.PI / 6;
                Vec3 at = new Vec3(target.getX() + 26 * Math.cos(angle), DEEP_ENTRY_Y,
                        target.getZ() + 26 * Math.sin(angle));
                if (!level.isPositionEntityTicking(BlockPos.containing(at))) continue;
                if (!room(level, null, at) || !room(level, null, at.with(Direction.Axis.Y, SEA_SURFACE))) continue;
                var boss = EclipseRegistration.ECLIPSE_LEVIATHAN.create(level);
                if (boss == null) return null;
                boss.emergeAt(at, target);
                return level.addFreshEntity(boss) ? boss : null;
            }
            return null;
        }
    }
}
