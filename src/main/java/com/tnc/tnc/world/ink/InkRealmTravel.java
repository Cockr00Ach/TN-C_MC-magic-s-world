package com.tnc.tnc.world.ink;

import com.tnc.tnc.TNMod;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * Public server-side travel API for the ink realm.
 *
 * <p>Spells, items and portals should call this class instead of duplicating
 * dimension lookup and safe-height logic.  The first version deliberately has
 * no portal block: the command is the test harness and later content can use
 * the same methods without changing the dimension implementation.</p>
 */
public final class InkRealmTravel {
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(TNMod.MODID, "ink_realm");
    public static final ResourceKey<Level> INK_REALM = ResourceKey.create(Registries.DIMENSION, ID);

    private InkRealmTravel() {}

    public static boolean enter(ServerPlayer player) {
        ServerLevel target = player.server.getLevel(INK_REALM);
        if (target == null) {
            return false;
        }
        return teleport(player, target, player.getX(), player.getZ());
    }

    public static boolean leave(ServerPlayer player) {
        ServerLevel target = player.server.overworld();
        BlockPos spawn = target.getSharedSpawnPos();
        return teleport(player, target, spawn.getX() + 0.5D, spawn.getZ() + 0.5D);
    }

    /**
     * Teleports to a dimension at the requested horizontal position and places
     * the player on the highest solid surface.  This keeps the API usable if
     * the flat-world layer stack changes later.
     */
    public static boolean teleport(ServerPlayer player, ServerLevel target, double x, double z) {
        double safeX = clampToBorder(target, x, true);
        double safeZ = clampToBorder(target, z, false);
        int blockX = (int) Math.floor(safeX);
        int blockZ = (int) Math.floor(safeZ);
        int y = target.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, blockX, blockZ) + 1;
        y = Math.max(target.getMinBuildHeight() + 2, Math.min(y, target.getMaxBuildHeight() - 2));
        player.teleportTo(target, safeX, y, safeZ, player.getYRot(), player.getXRot());
        player.fallDistance = 0.0F;
        return true;
    }

    private static double clampToBorder(ServerLevel target, double coordinate, boolean xAxis) {
        // World borders are not required to be centred at 0,0.
        // Use their actual bounds so a moved border cannot put the arrival point outside it.
        double min = (xAxis ? target.getWorldBorder().getMinX() : target.getWorldBorder().getMinZ()) + 2.0D;
        double max = (xAxis ? target.getWorldBorder().getMaxX() : target.getWorldBorder().getMaxZ()) - 2.0D;
        return Math.max(min, Math.min(max, coordinate));
    }
}
