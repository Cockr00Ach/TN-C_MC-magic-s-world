package com.tnc.tnc.magic.water;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.level.BlockEvent;
import java.util.ArrayList;
import java.util.List;

/** One bounded axial slice at a time. Never loads chunks and never deletes a block entity. */
public final class WaterTerrainBore {
    public static final int BLOCK_BUDGET = 128;
    private final Vec3 start, direction;
    private final double radius, range;
    private double length;
    private List<BlockPos> pending;
    private int cursor;
    private boolean stopped;
    public WaterTerrainBore(Vec3 start, Vec3 direction, double radius, double range) {
        this.start=start; this.direction=direction.normalize();
        this.radius=Math.max(.1,Math.min(4,radius)); this.range=Math.max(0,Math.min(64,range));
    }
    public double length() { return length; }
    public boolean stopped() { return stopped; }
    public static boolean protectedState(BlockState state, ServerLevel level, BlockPos pos) {
        return state.hasBlockEntity() || state.getDestroySpeed(level,pos)<0;
    }
    static boolean fluidOnly(BlockState state) { return state.getBlock() instanceof net.minecraft.world.level.block.LiquidBlock; }
    private boolean allowed(ServerLevel level, ServerPlayer player, BlockPos pos) {
        if (!level.hasChunkAt(pos) || !level.getWorldBorder().isWithinBounds(pos)
                || level.isOutsideBuildHeight(pos)) return false;
        BlockState state=level.getBlockState(pos);
        if (protectedState(state,level,pos)) return false;
        if (player.isSpectator() || player.gameMode.getGameModeForPlayer()==net.minecraft.world.level.GameType.ADVENTURE
                || !level.mayInteract(player,pos) || level.getServer().isUnderSpawnProtection(level,pos,player)) return false;
        return !MinecraftForge.EVENT_BUS.post(new BlockEvent.BreakEvent(level,pos,state,player));
    }
    private boolean safeSupport(ServerLevel level,BlockPos pos) {
        // Conservative first pass: don't remove supports of containers, attachments or falling blocks.
        // Such a cascade could otherwise bypass both claim checks and our direct-edit budget.
        for(var face:net.minecraft.core.Direction.values()) {
            BlockPos next=pos.relative(face);if(!level.hasChunkAt(next))return false;
            var state=level.getBlockState(next);
            if(state.isAir() || fluidOnly(state))continue;
            if(state.hasBlockEntity() || state.getBlock() instanceof net.minecraft.world.level.block.FallingBlock
                    || !state.isCollisionShapeFullBlock(level,next)) return false;
        }
        return true;
    }
    public int tick(ServerLevel level, ServerPlayer player) {
        if(stopped || length>=range) return 0;
        double next=Math.min(range,length+1);
        if(pending==null) {
            pending=new ArrayList<>(); cursor=0;
            Vec3 a=start.add(direction.scale(length)), b=start.add(direction.scale(next));
            BlockPos min=BlockPos.containing(Math.min(a.x,b.x)-radius-1,Math.min(a.y,b.y)-radius-1,Math.min(a.z,b.z)-radius-1);
            BlockPos max=BlockPos.containing(Math.max(a.x,b.x)+radius+1,Math.max(a.y,b.y)+radius+1,Math.max(a.z,b.z)+radius+1);
            for(BlockPos mutable:BlockPos.betweenClosed(min,max)) {
                Vec3 center=Vec3.atCenterOf(mutable);
                double along=center.subtract(start).dot(direction);
                // Guard every voxel VOLUME touched by this slice, not merely its center.
                // Otherwise a half-block of protected air could enter the visible damage beam.
                double axialExtent=.5*(Math.abs(direction.x)+Math.abs(direction.y)+Math.abs(direction.z));
                double radialSquared=center.distanceToSqr(start.add(direction.scale(along)));
                if(along+axialExtent<length || along-axialExtent>=next || radialSquared>Math.pow(radius+Math.sqrt(3)/2,2))continue;
                BlockPos pos=mutable.immutable();
                // Validate the complete slice before making any of its edits.
                if(!allowed(level,player,pos)) { stopped=true; pending=null; return 0; }
                if(along<length || along>=next || radialSquared>radius*radius)continue;
                if(!level.getBlockState(pos).isAir() && !fluidOnly(level.getBlockState(pos))) {
                    if(!safeSupport(level,pos)){stopped=true;pending=null;return 0;}
                    pending.add(pos);
                }
            }
        }
        int changed=0;
        while(cursor<pending.size() && changed<BLOCK_BUDGET) {
            BlockPos pos=pending.get(cursor++);
            if(!allowed(level,player,pos)) { stopped=true; pending=null; return changed; }
            if(!level.getBlockState(pos).isAir() && !fluidOnly(level.getBlockState(pos))) {
                if(!safeSupport(level,pos)){stopped=true;pending=null;return changed;}
                // No drops: a large beam must not create thousands of ticking item entities.
                if(!level.destroyBlock(pos,false,player)) { stopped=true; pending=null; return changed; }
                changed++;
            }
        }
        if(cursor>=pending.size()) { length=next; pending=null; }
        return changed;
    }
}
