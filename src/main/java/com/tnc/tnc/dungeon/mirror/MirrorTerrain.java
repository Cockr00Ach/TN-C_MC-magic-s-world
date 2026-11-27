package com.tnc.tnc.dungeon.mirror;

import com.tnc.tnc.dungeon.DungeonData;
import com.tnc.tnc.dungeon.DungeonRealm;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.*;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.*;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.*;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.*;
import net.minecraftforge.fml.common.Mod;
import java.util.*;

/** All temporary terrain, including spreading water, is journalled before the arena is restored. */
@Mod.EventBusSubscriber(modid="tnc")
public final class MirrorTerrain extends SavedData {
    static final int LIFETIME = 200;
    static final int WATER_LIFETIME = 600;
    private record Cell(UUID run, long group, String kind, BlockState before, BlockState placed, long until) {}
    private final Map<BlockPos, Cell> cells = new HashMap<>();

    static MirrorTerrain get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(t -> read(level,t), MirrorTerrain::new, "tnc_echo_constructions");
    }
    static MirrorTerrain read(ServerLevel level, CompoundTag tag) {
        var ledger=new MirrorTerrain();
        var blocks=level.holderLookup(Registries.BLOCK);
        for (Tag value:tag.getList("Cells",Tag.TAG_COMPOUND)) {
            var t=(CompoundTag)value;
            if (t.hasUUID("Run")) ledger.cells.put(BlockPos.of(t.getLong("At")),new Cell(t.getUUID("Run"),
                    t.getLong("Group"),t.getString("Kind"),NbtUtils.readBlockState(blocks,t.getCompound("Before")),
                    NbtUtils.readBlockState(blocks,t.getCompound("Placed")),t.getLong("Until")));
        }
        return ledger;
    }
    @Override public CompoundTag save(CompoundTag tag) {
        var list=new ListTag();
        cells.forEach((pos,c)->{
            var t=new CompoundTag();t.putLong("At",pos.asLong());t.putUUID("Run",c.run);
            t.putLong("Group",c.group);t.putString("Kind",c.kind);t.putLong("Until",c.until);
            t.put("Before",NbtUtils.writeBlockState(c.before));t.put("Placed",NbtUtils.writeBlockState(c.placed));list.add(t);
        });
        tag.put("Cells",list);return tag;
    }
    private static boolean change(ServerLevel level,BlockPos pos,BlockState state) {
        DungeonRealm.write(()->level.setBlock(pos,state,Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE));
        return level.getBlockState(pos).equals(state);
    }
    boolean place(ServerLevel level,UUID run,BlockPos pos,String kind,long group) {
        if (!level.hasChunkAt(pos)||cells.containsKey(pos))return false;
        var before=level.getBlockState(pos);
        if (!before.isAir()&&!(kind.equals("web")&&before.is(Blocks.WATER)))return false;
        Block block=switch(kind) {
            case "web"->Blocks.COBWEB;case "lava"->Blocks.LAVA;case "water"->Blocks.WATER;
            case "foot"->Blocks.CHISELED_DEEPSLATE;case "stone"->Blocks.COBBLESTONE;
            default->throw new IllegalArgumentException(kind);
        };
        // No vanilla onPlace lava/water reaction can write an unjournalled adjacent block.
        if (block==Blocks.LAVA||block==Blocks.WATER)for(Direction dir:Direction.values()) {
            var fluid=level.getFluidState(pos.relative(dir));
            if (!fluid.isEmpty()&&(block==Blocks.LAVA||fluid.getType().isSame(Fluids.LAVA)))return false;
        }
        if (!change(level,pos,block.defaultBlockState()))return false;
        cells.put(pos.immutable(),new Cell(run,group,kind,before,block.defaultBlockState(),level.getGameTime()+(kind.equals("water")?WATER_LIFETIME:LIFETIME)));
        setDirty();return true;
    }
    boolean cool(ServerLevel level,UUID run,BlockPos pos) {
        var cell=cells.get(pos);
        if (cell==null||!cell.run.equals(run)||!cell.kind.equals("lava")||!level.getBlockState(pos).equals(cell.placed))return false;
        var obsidian=Blocks.OBSIDIAN.defaultBlockState();
        if (!change(level,pos,obsidian))return false;
        cells.put(pos,new Cell(run,cell.group,"quenched",cell.before,obsidian,cell.until));
        level.playSound(null,pos,SoundEvents.LAVA_EXTINGUISH,SoundSource.BLOCKS,.7f,1.2f);
        level.sendParticles(ParticleTypes.CLOUD,pos.getX()+.5,pos.getY()+1,pos.getZ()+.5,8,.18,.1,.18,.02);
        setDirty();return true;
    }
    boolean coversOldWater(ServerLevel level,UUID run,BlockPos pos){
        var c=cells.get(pos);
        return c!=null && c.run.equals(run) && c.before.is(Blocks.WATER) && level.getBlockState(pos).equals(c.placed);
    }
    private void remove(ServerLevel level,BlockPos at) {
        var c=cells.get(at);if (c==null||!level.hasChunkAt(at))return;
        if (level.getBlockState(at).equals(c.placed)) {
            if(!change(level,at,c.before))return;
            level.sendParticles(ParticleTypes.SPLASH,at.getX()+.5,at.getY()+.7,at.getZ()+.5,5,.23,.15,.23,.035);
        }
        cells.remove(at);setDirty();
    }
    boolean reclaimWater(ServerLevel level,UUID run,BlockPos pos) {
        var c=cells.get(pos);
        if(c==null||!c.run.equals(run)||!c.kind.equals("water")||!level.getBlockState(pos).equals(c.placed))return false;
        remove(level,pos);
        for (var entry : new ArrayList<>(cells.entrySet())) {
            var flow=entry.getValue();
            if (flow.run.equals(run) && flow.group==c.group && flow.kind.equals("water_flow")) remove(level,entry.getKey());
        }
        return true;
    }

    void spreadWater(ServerLevel level) {
        for (var entry : new ArrayList<>(cells.entrySet())) {
            var from=entry.getKey();var flow=entry.getValue();
            if ((!flow.kind.equals("water") && !flow.kind.equals("water_flow")) || !level.hasChunkAt(from)
                    || !level.getBlockState(from).equals(flow.placed)) continue;
            var source=BlockPos.of(flow.group);var root=cells.get(source);
            if (root==null || !root.kind.equals("water") || !root.run.equals(flow.run)) continue;
            var below=from.below();
            if (below.getY()>=source.getY()-18 && level.hasChunkAt(below) &&
                    (level.isEmptyBlock(below) || level.getBlockState(below).is(Blocks.WATER))) {
                flowInto(level,flow,below,8);
                continue;
            }
            int depth=flow.placed.getValue(LiquidBlock.LEVEL);
            if (depth>=8) depth=0;
            if (depth>=7) continue;
            for (var direction : Direction.Plane.HORIZONTAL) {
                var dest=from.relative(direction);
                if (Math.abs(dest.getX()-source.getX())+Math.abs(dest.getZ()-source.getZ())<=7
                        && level.hasChunkAt(dest)) flowInto(level,flow,dest,depth+1);
            }
        }
    }
    private void flowInto(ServerLevel level,Cell flow,BlockPos dest,int depth) {
        var run=DungeonData.get(level.getServer()).runs.get(flow.run);
        if (run!=null) {
            var fight=run.state().events.getCompound("EchoingMirror");
            if (fight.contains("Origin")) {
                var center=BlockPos.of(fight.getLong("Origin"));
                if (Math.abs(dest.getX()-center.getX())>21 || Math.abs(dest.getZ()-center.getZ())>21
                        || dest.getY()<=center.getY() || dest.getY()>center.getY()+18) return;
            }
        }
        if (cool(level,flow.run,dest)) return;
        var before=level.getBlockState(dest);
        if (cells.containsKey(dest) || !before.isAir()) return;
        var placed=Blocks.WATER.defaultBlockState().setValue(LiquidBlock.LEVEL,depth);
        if (change(level,dest,placed)) {
            cells.put(dest,new Cell(flow.run,flow.group,"water_flow",before,placed,flow.until));
            setDirty();
        }
    }
    boolean breakTool(ServerLevel level,UUID run,BlockPos pos,ItemStack tool) {
        var cell=cells.get(pos);
        if(cell==null||!cell.run.equals(run)||!level.getBlockState(pos).equals(cell.placed))return false;
        if(cell.kind.equals("web")&&(tool.getItem() instanceof SwordItem||tool.is(Items.SHEARS)))remove(level,pos);
        else if((cell.kind.equals("stone")||cell.kind.equals("foot")||cell.kind.equals("quenched"))&&tool.getItem() instanceof PickaxeItem) {
            if(cell.kind.equals("foot")) {
                for(var e:new ArrayList<>(cells.entrySet()))if(e.getValue().run.equals(run)&&e.getValue().group==cell.group)remove(level,e.getKey());
            } else remove(level,pos);
        } else return false;
        level.playSound(null,pos,SoundEvents.AMETHYST_CLUSTER_BREAK,SoundSource.BLOCKS,.55f,1.4f);return true;
    }
    static void cleanup(ServerLevel level,UUID run) {
        var journal=get(level);
        for(var e:new ArrayList<>(journal.cells.entrySet()))if(e.getValue().run.equals(run)){
            if(level.hasChunkAt(e.getKey()))journal.remove(level,e.getKey());
            else {var c=e.getValue();journal.cells.put(e.getKey(),new Cell(c.run,c.group,c.kind,c.before,c.placed,0));journal.setDirty();}
        }
    }
    void expire(ServerLevel level,long now) {
        if(cells.isEmpty())return;
        for(var e:new ArrayList<>(cells.entrySet()))if(e.getValue().until<=now)remove(level,e.getKey());
    }
    @SubscribeEvent public static void tick(TickEvent.LevelTickEvent e) {
        if(e.phase==TickEvent.Phase.END&&e.level instanceof ServerLevel level&&DungeonRealm.inside(level)) {
            var journal=get(level);
            journal.expire(level,level.getGameTime());
            if (level.getGameTime()%5==0) journal.spreadWater(level);
        }
    }
    @SubscribeEvent(priority=EventPriority.HIGH) public static void dig(PlayerInteractEvent.LeftClickBlock e) {
        if (!(e.getEntity() instanceof ServerPlayer p)||e.getAction()!=PlayerInteractEvent.LeftClickBlock.Action.START
                ||!MirrorFight.silent(p)||p.distanceToSqr(Vec3.atCenterOf(e.getPos()))>36)return;
        if(get(p.serverLevel()).breakTool(p.serverLevel(),MirrorFight.activeRun(p),e.getPos(),p.getMainHandItem()))e.setCanceled(true);
    }
    private static boolean bucket(ServerPlayer p,InteractionHand hand,BlockHitResult hit) {
        if(hit.getType()!=HitResult.Type.BLOCK || !MirrorFight.silent(p)||p.distanceToSqr(hit.getLocation())>36)return false;
        var stack=p.getItemInHand(hand);var run=MirrorFight.activeRun(p);if(run==null)return false;
        var level=p.serverLevel();var journal=get(level);var pos=hit.getBlockPos();Item result;
        if(stack.is(Items.WATER_BUCKET)){
            var room=com.tnc.tnc.dungeon.DungeonService.room(p);
            var dungeon=com.tnc.tnc.dungeon.DungeonService.run(p);
            if(room==null||dungeon==null)return false;
            var dest=pos.relative(hit.getDirection());var center=DungeonRealm.center(dungeon,room);
            if(Math.abs(dest.getX()-center.getX())>21||Math.abs(dest.getZ()-center.getZ())>21
                    ||dest.getY()<center.getY()||dest.getY()>center.getY()+18)return false;
            if(!journal.cool(level,run,pos)&&!journal.cool(level,run,dest)&&!journal.place(level,run,dest,"water",dest.asLong()))return false;
            p.clearFire();result=Items.BUCKET;
        }else if(stack.is(Items.BUCKET)&&journal.reclaimWater(level,run,pos))result=Items.WATER_BUCKET;
        else return false;
        var next=new ItemStack(result);
        if(stack.hasTag()&&stack.getTag().hasUUID("tncMirrorLoan"))next.getOrCreateTag().putUUID("tncMirrorLoan",stack.getTag().getUUID("tncMirrorLoan"));
        if(!p.isCreative()){
            if(stack.getCount()==1)p.setItemInHand(hand,next);
            else {stack.shrink(1);if(!p.getInventory().add(next))p.drop(next,false);}
        }
        level.playSound(null,pos,result==Items.BUCKET?SoundEvents.BUCKET_EMPTY:SoundEvents.BUCKET_FILL,SoundSource.PLAYERS,.8f,1f);
        p.swing(hand,true);return true;
    }
    @SubscribeEvent(priority=EventPriority.HIGH) public static void onBucket(PlayerInteractEvent.RightClickItem e){
        if(!(e.getEntity() instanceof ServerPlayer p)||!MirrorFight.silent(p)
                ||!(e.getItemStack().is(Items.WATER_BUCKET)||e.getItemStack().is(Items.BUCKET)))return;
        var start=p.getEyePosition();var hit=p.level().clip(new ClipContext(start,start.add(p.getLookAngle().scale(5)),ClipContext.Block.OUTLINE,ClipContext.Fluid.SOURCE_ONLY,p));
        if(bucket(p,e.getHand(),hit)){e.setCancellationResult(InteractionResult.SUCCESS);e.setCanceled(true);}
    }
    @SubscribeEvent(priority=EventPriority.HIGH) public static void onBlock(PlayerInteractEvent.RightClickBlock e){
        if(e.getEntity() instanceof ServerPlayer p&&(e.getItemStack().is(Items.WATER_BUCKET)||e.getItemStack().is(Items.BUCKET))
                &&bucket(p,e.getHand(),e.getHitVec())){e.setCancellationResult(InteractionResult.SUCCESS);e.setCanceled(true);}
    }
}
