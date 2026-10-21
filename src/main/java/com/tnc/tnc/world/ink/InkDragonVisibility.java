package com.tnc.tnc.world.ink;

import net.minecraft.world.phys.Vec3;
import java.util.UUID;

/** Bounded cosmetic snapshots, not extra entities or chunk tickets. */
public final class InkDragonVisibility {
    public static final int MAX_CACHED=128,MAX_SENT=16;
    public static final double RANGE=512;
    public record Snapshot(UUID id,Vec3 position,float yaw,net.minecraft.nbt.CompoundTag state) {
        public Snapshot(UUID id,Vec3 position,float yaw){this(id,position,yaw,groundState());}
        private static net.minecraft.nbt.CompoundTag groundState(){var t=new net.minecraft.nbt.CompoundTag();t.putBoolean("ground",true);return t;}
    }
    private InkDragonVisibility(){}
    public static boolean retainAfterRemoval(net.minecraft.world.entity.Entity.RemovalReason reason){
        // Tracking can end before an unload removal reason has been assigned.
        return reason==null||reason==net.minecraft.world.entity.Entity.RemovalReason.UNLOADED_TO_CHUNK;
    }
    public static double distanceSquared(Snapshot s,Vec3 viewer){
        var b=s.state().getBoolean("ground")?InkDragonFlightRules.restingBounds(s.position(),s.yaw()):InkDragonFlightRules.bounds(s.position(),s.yaw());
        double x=Math.max(b.minX,Math.min(b.maxX,viewer.x));
        double y=Math.max(b.minY,Math.min(b.maxY,viewer.y));
        double z=Math.max(b.minZ,Math.min(b.maxZ,viewer.z));
        return viewer.distanceToSqr(x,y,z);
    }
}
