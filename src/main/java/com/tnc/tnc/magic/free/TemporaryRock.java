package com.tnc.tnc.magic.free;

import net.minecraft.core.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.*;
import net.minecraft.server.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.*;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.RegisterEvent;
import java.util.*;

/** Persistent expiry ledger: unloaded chunks are never forced, expired constructs vanish on return. */
public final class TemporaryRock extends SavedData {
    public static final IntegerProperty CRACK=IntegerProperty.create("crack",0,2);
    public static final Block BLOCK=new Block(BlockBehaviour.Properties.of().strength(1.5f,6).sound(SoundType.STONE).noLootTable().pushReaction(PushReaction.BLOCK)){
        @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> b){b.add(CRACK);}
    };
    public static final Block LIGHT=new Block(BlockBehaviour.Properties.of().noCollission().noLootTable().lightLevel(state->15).pushReaction(PushReaction.DESTROY)){@Override public RenderShape getRenderShape(BlockState state){return RenderShape.INVISIBLE;}};
    private record Stone(UUID owner,long end,boolean light){}
    private final Map<BlockPos,Stone> stones=new HashMap<>();
    private static final Set<ServerLevel> LEVELS=Collections.newSetFromMap(new IdentityHashMap<>());
    @Mod.EventBusSubscriber(modid="tnc",bus=Mod.EventBusSubscriber.Bus.MOD)
    public static final class TemporaryRockRegistry {@SubscribeEvent public static void register(RegisterEvent e){e.register(Registries.BLOCK,h->{h.register(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("tnc","temporary_magic_rock"),BLOCK);h.register(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("tnc","temporary_magic_light"),LIGHT);});}}
    @Mod.EventBusSubscriber(modid="tnc")
    public static final class Lifecycle {
        @SubscribeEvent public static void load(net.minecraftforge.event.level.LevelEvent.Load e){if(e.getLevel() instanceof ServerLevel level)get(level);}
        @SubscribeEvent public static void unload(net.minecraftforge.event.level.LevelEvent.Unload e){if(e.getLevel() instanceof ServerLevel level)LEVELS.remove(level);}
    }
    static TemporaryRock get(ServerLevel level){LEVELS.add(level);return level.getDataStorage().computeIfAbsent(TemporaryRock::load,TemporaryRock::new,"tnc_temporary_magic_rock");}
    private static TemporaryRock load(CompoundTag tag){var data=new TemporaryRock();for(var raw:tag.getList("stones",Tag.TAG_COMPOUND)){var entry=(CompoundTag)raw;data.stones.put(BlockPos.of(entry.getLong("pos")),new Stone(entry.getUUID("owner"),entry.getLong("end"),entry.getBoolean("light")));}return data;}
    @Override public CompoundTag save(CompoundTag tag){var list=new ListTag();stones.forEach((pos,stone)->{var entry=new CompoundTag();entry.putLong("pos",pos.asLong());entry.putUUID("owner",stone.owner);entry.putLong("end",stone.end);entry.putBoolean("light",stone.light);list.add(entry);});tag.put("stones",list);return tag;}
    static boolean placeable(ServerPlayer owner,BlockPos pos){var l=owner.serverLevel();return l.hasChunkAt(pos)&&!l.isOutsideBuildHeight(pos)&&l.getWorldBorder().isWithinBounds(pos)&&l.mayInteract(owner,pos)&&l.getBlockState(pos).isAir()&&l.getBlockEntity(pos)==null;}
    static boolean place(ServerPlayer owner,BlockPos pos,int duration){if(!placeable(owner,pos))return false;
        var level=owner.serverLevel();var snapshot=net.minecraftforge.common.util.BlockSnapshot.create(level.dimension(),level,pos);
        if(net.minecraftforge.event.ForgeEventFactory.onBlockPlace(owner,snapshot,Direction.UP))return false;
        if(!level.setBlock(pos,BLOCK.defaultBlockState().setValue(CRACK,0),3))return false;
        var data=get(level);data.stones.put(pos.immutable(),new Stone(owner.getUUID(),level.getGameTime()+duration,false));data.setDirty();return true;
    }
    static List<BlockPos> owned(ServerPlayer owner,Vec3 center,double radius){return get(owner.serverLevel()).stones.entrySet().stream().filter(e->e.getValue().owner.equals(owner.getUUID())&&e.getKey().distToCenterSqr(center)<radius*radius&&owner.level().hasChunkAt(e.getKey())&&owner.level().getBlockState(e.getKey()).is(BLOCK)).map(Map.Entry::getKey).limit(128).toList();}
    static void remove(ServerLevel level,BlockPos pos){var data=get(level);if(data.stones.remove(pos)==null)return;if(level.getBlockState(pos).is(LIGHT)){level.removeBlock(pos,false);}else if(level.getBlockState(pos).is(BLOCK)){for(var entity:level.getEntitiesOfClass(net.minecraft.world.entity.LivingEntity.class,new AABB(pos).inflate(.5,2,.5)))entity.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.SLOW_FALLING,80,0,false,true));level.destroyBlock(pos,false);}data.setDirty();}
    static void illuminate(ServerPlayer owner,BlockPos pos){var level=owner.serverLevel();if(!placeable(owner,pos)&&!level.getBlockState(pos).is(LIGHT))return;level.setBlock(pos,LIGHT.defaultBlockState(),3);var data=get(level);data.stones.put(pos.immutable(),new Stone(owner.getUUID(),level.getGameTime()+35,true));data.setDirty();}
    static void tickAll(){for(var level:new ArrayList<>(LEVELS)){var data=get(level);if(level.getGameTime()%5!=0)continue;
        for(var entry:new ArrayList<>(data.stones.entrySet())){var pos=entry.getKey();if(!level.hasChunkAt(pos))continue;long remaining=entry.getValue().end-level.getGameTime();
            if(!level.getBlockState(pos).is(BLOCK)&&!level.getBlockState(pos).is(LIGHT)){data.stones.remove(pos);data.setDirty();}
            else if(remaining<=0)remove(level,pos);else if(remaining<60&&level.getBlockState(pos).is(BLOCK)){int crack=remaining<25?2:1;if(level.getBlockState(pos).getValue(CRACK)!=crack)level.setBlock(pos,level.getBlockState(pos).setValue(CRACK,crack),3);}
        }
    }}
}
