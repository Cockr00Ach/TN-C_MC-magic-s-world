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

/** One bounded axial slice at a time; player-authorized terrain destruction, still claim-aware. */
public final class WaterTerrainBore {
    public static final int BLOCK_BUDGET = 128;
    private final Vec3 start, direction;
    private final double radius, range;
    private double length;
    private List<BlockPos> pending;
    private int cursor;
    private boolean stopped;
    private String stopReason="";
    public WaterTerrainBore(Vec3 start, Vec3 direction, double radius, double range) {
        this.start=start; this.direction=direction.normalize();
        this.radius=Math.max(.1,Math.min(WaterSpellRules.radius(5),radius)); this.range=Math.max(0,Math.min(96,range));
    }
    public double length() { return length; }
    public boolean stopped() { return stopped; }
    public String stopReason() { return stopReason; }
    public static boolean protectedState(BlockState state, ServerLevel level, BlockPos pos) {
        return state.is(net.minecraft.world.level.block.Blocks.BEDROCK);
    }
    static boolean fluidOnly(BlockState state) { return state.getBlock() instanceof net.minecraft.world.level.block.LiquidBlock; }
    private boolean allowed(ServerLevel level, ServerPlayer player, BlockPos pos) {
        if (!level.hasChunkAt(pos) || !level.getWorldBorder().isWithinBounds(pos)
                || level.isOutsideBuildHeight(pos)) {stopReason="边界/未加载区块 "+pos.toShortString();return false;}
        BlockState state=level.getBlockState(pos);
        if (protectedState(state,level,pos)) {stopReason="基岩 "+pos.toShortString();return false;}
        if (player.isSpectator() || player.gameMode.getGameModeForPlayer()==net.minecraft.world.level.GameType.ADVENTURE
                || !level.mayInteract(player,pos) || level.getServer().isUnderSpawnProtection(level,pos,player)) {stopReason="交互/出生点保护 "+pos.toShortString();return false;}
        boolean accepted=!MinecraftForge.EVENT_BUS.post(new BlockEvent.BreakEvent(level,pos,state,player));
        if(!accepted)stopReason="领地/事件保护 "+pos.toShortString();return accepted;
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
                    pending.add(pos);
                }
            }
        }
        int changed=0;
        while(cursor<pending.size() && changed<BLOCK_BUDGET) {
            BlockPos pos=pending.get(cursor++);
            if(!allowed(level,player,pos)) { stopped=true; pending=null; return changed; }
            if(!level.getBlockState(pos).isAir() && !fluidOnly(level.getBlockState(pos))) {
                // Capture normal block-entity loot BEFORE onRemove erases its NBT.
                // Shulkers encode contents in the dropped box; chests spill contents once in onRemove.
                BlockState state=level.getBlockState(pos);
                var blockEntity=level.getBlockEntity(pos);
                var loot=blockEntity==null?java.util.List.<net.minecraft.world.item.ItemStack>of():
                        net.minecraft.world.level.block.Block.getDrops(state,level,pos,blockEntity,player,net.minecraft.world.item.ItemStack.EMPTY);
                if(!WaterBoreEditGuard.remove(level,pos)) {
                    stopReason="方块移除失败/安全隔离不可用 "+pos.toShortString();
                    stopped=true; pending=null; return changed;
                }
                for(var stack:loot)net.minecraft.world.level.block.Block.popResource(level,pos,stack);
                changed++;
            }
        }
        if(cursor>=pending.size()) { length=next; pending=null; }
        return changed;
    }
}
