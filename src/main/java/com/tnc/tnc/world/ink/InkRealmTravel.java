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
        // Heightmaps return the first free block, not the solid block's Y.
        // Load the actual destination chunk before querying it; search nearby if obstructed.
        for(int radius=0;radius<=8;radius++)for(int dx=-radius;dx<=radius;dx++)for(int dz=-radius;dz<=radius;dz++){
            if(Math.max(Math.abs(dx),Math.abs(dz))!=radius)continue;
            int px=blockX+dx,pz=blockZ+dz;
            double cx=px+.5,cz=pz+.5;
            if(cx!=clampToBorder(target,cx,true)||cz!=clampToBorder(target,cz,false))continue;
            target.getChunk(px>>4,pz>>4);
            int top=target.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,px,pz);
            BlockPos support=new BlockPos(px,top-1,pz);var state=target.getBlockState(support);
            var shape=state.getCollisionShape(target,support);
            if(shape.isEmpty()||!state.getFluidState().isEmpty())continue;
            double feet=support.getY()+shape.max(net.minecraft.core.Direction.Axis.Y);
            if(feet<target.getMinBuildHeight()||feet>target.getMaxBuildHeight()-2)continue;
            var landing=new net.minecraft.world.phys.Vec3(cx,feet,cz);
            var box=player.getBoundingBox().move(landing.subtract(player.position()));
            if(!target.noCollision(player,box)||target.containsAnyLiquid(box))continue;
            player.teleportTo(target,cx,feet,cz,player.getYRot(),player.getXRot());
            player.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);player.fallDistance=0;
            return true;
        }
        return false;
    }

    private static double clampToBorder(ServerLevel target, double coordinate, boolean xAxis) {
        // World borders are not required to be centred at 0,0.
        // Use their actual bounds so a moved border cannot put the arrival point outside it.
        double min = (xAxis ? target.getWorldBorder().getMinX() : target.getWorldBorder().getMinZ()) + 2.0D;
        double max = (xAxis ? target.getWorldBorder().getMaxX() : target.getWorldBorder().getMaxZ()) - 2.0D;
        return Math.max(min, Math.min(max, coordinate));
    }
}
