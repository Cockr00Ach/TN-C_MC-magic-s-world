package com.tnc.tnc.world.elementarena;
import java.util.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.item.*;
import net.minecraftforge.event.*;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.*;
import static net.minecraft.commands.Commands.literal;
@Mod.EventBusSubscriber(modid="tnc")
public final class ElementArenas {
 public static final ResourceKey<Level> WIND=key("wind_arena"),WATER=key("water_arena");
 public static Block LIGHT,DARK;
 public static final Block[] STONES=new Block[8];
 private static final Set<UUID> PENDING=new HashSet<>();
 private static ResourceLocation id(String name){return ResourceLocation.fromNamespaceAndPath("tnc",name);}
 private static ResourceKey<Level> key(String name){return ResourceKey.create(Registries.DIMENSION,id(name));}
 @Mod.EventBusSubscriber(modid="tnc",bus=Mod.EventBusSubscriber.Bus.MOD)
 public static final class Content {
  @SubscribeEvent public static void register(RegisterEvent event){
   event.register(Registries.BLOCK,h->{for(int i=0;i<8;i++){STONES[i]=new Block(BlockBehaviour.Properties.copy(Blocks.STONE).strength(4,15));h.register(id("wind_arena_runestone_"+(i+1)),STONES[i]);}LIGHT=STONES[0];DARK=STONES[7];});
   event.register(Registries.ITEM,h->{for(int i=0;i<8;i++)h.register(id("wind_arena_runestone_"+(i+1)),new BlockItem(STONES[i],new Item.Properties()));});
   event.register(Registries.CHUNK_GENERATOR,h->h.register(id("element_arena"),ElementArenaChunkGenerator.CODEC));
  }
  @SubscribeEvent public static void tab(BuildCreativeModeTabContentsEvent event){if(event.getTabKey().equals(com.tnc.tnc.TNMod.TNC_TAB.getKey())){for(var stone:STONES)event.accept(stone);}}
 }
 @SubscribeEvent public static void commands(RegisterCommandsEvent event){
  for(String theme:new String[]{"wind","water"})event.getDispatcher().register(literal("tnc").then(literal(theme).requires(s->s.hasPermission(2))
   .executes(c->{travel(c.getSource().getPlayerOrException(),theme.equals("water")?WATER:WIND);return 1;})
   .then(literal("boss").executes(c->{var p=c.getSource().getPlayerOrException();var k=theme.equals("water")?WATER:WIND;if(!p.level().dimension().equals(k)){c.getSource().sendFailure(Component.literal("请先进入对应战场，再召唤 Boss。"));return 0;}
    var type=ForgeRegistries.ENTITY_TYPES.getValue(id(theme.equals("water")?"water_leviathan":"canglan_warlord"));if(type==null)return 0;
    var entity=type.create(p.level());if(entity==null)return 0;entity.moveTo(0.5,theme.equals("water")?48:40,0.5,180,0);p.serverLevel().addFreshEntity(entity);return 1;}))));
 }
 public static void travel(ServerPlayer player,ResourceKey<Level> arena){
  if(!PENDING.add(player.getUUID()))return;
  var server=player.getServer();boolean back=player.level().dimension().equals(arena);String prefix="ElementArena."+arena.location().getPath()+".";
  CompoundTag data=player.getPersistentData().getCompound(net.minecraft.world.entity.player.Player.PERSISTED_NBT_TAG);player.getPersistentData().put(net.minecraft.world.entity.player.Player.PERSISTED_NBT_TAG,data);
  var dest=server.getLevel(arena);double x=8.5,y=32,z=8.5;float yaw=0,pitch=0;
  if(back){
   var previous=ResourceLocation.tryParse(data.getString(prefix+"dimension"));dest=previous==null?server.overworld():server.getLevel(ResourceKey.create(Registries.DIMENSION,previous));if(dest==null||dest.dimension().equals(arena))dest=server.overworld();
   var spawn=dest.getSharedSpawnPos();x=data.contains(prefix+"x")?data.getDouble(prefix+"x"):spawn.getX()+.5;y=data.contains(prefix+"y")?data.getDouble(prefix+"y"):spawn.getY();z=data.contains(prefix+"z")?data.getDouble(prefix+"z"):spawn.getZ()+.5;yaw=data.getFloat(prefix+"yaw");pitch=data.getFloat(prefix+"pitch");
  }else if(dest!=null){data.putString(prefix+"dimension",player.level().dimension().location().toString());data.putDouble(prefix+"x",player.getX());data.putDouble(prefix+"y",player.getY());data.putDouble(prefix+"z",player.getZ());data.putFloat(prefix+"yaw",player.getYRot());data.putFloat(prefix+"pitch",player.getXRot());}
  if(dest==null){PENDING.remove(player.getUUID());player.sendSystemMessage(Component.literal("战场尚未载入，请重新进入存档。"));return;}
  final var target=dest;final double tx=x,ty=y,tz=z;final float ry=yaw,rp=pitch;
  target.getChunkSource().getChunkFuture(net.minecraft.util.Mth.floor(x)>>4,net.minecraft.util.Mth.floor(z)>>4,net.minecraft.world.level.chunk.ChunkStatus.FULL,true).whenCompleteAsync((chunk,error)->{
   try{if(error!=null||chunk==null||chunk.left().isEmpty()||player.hasDisconnected()||!player.isAlive())return;double landingY=back?ty:target.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.OCEAN_FLOOR,net.minecraft.util.Mth.floor(tx),net.minecraft.util.Mth.floor(tz));var box=player.getBoundingBox().move(new net.minecraft.world.phys.Vec3(tx,landingY,tz).subtract(player.position()));if(!target.noCollision(player,box)){player.sendSystemMessage(Component.literal("落点受阻，请清理入口上方空间后重试。"));return;}player.teleportTo(target,tx,landingY,tz,ry,rp);player.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);player.fallDistance=0;player.setAirSupply(player.getMaxAirSupply());player.sendSystemMessage(Component.literal(back?"已返回原来的位置。":"已进入"+(arena.equals(WATER)?"水下棋盘战场":"苍岚柱阵战场")+"；再次使用同一指令返回，追加 boss 召唤 Boss。"));}finally{PENDING.remove(player.getUUID());}
  },server);
 }
 @SubscribeEvent public static void breathe(TickEvent.PlayerTickEvent e){if(e.phase==TickEvent.Phase.END&&!e.player.level().isClientSide&&e.player.level().dimension().equals(WATER))e.player.setAirSupply(e.player.getMaxAirSupply());}
 @SubscribeEvent public static void stopped(net.minecraftforge.event.server.ServerStoppedEvent e){PENDING.clear();}
}
