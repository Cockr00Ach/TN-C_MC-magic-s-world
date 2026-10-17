package com.tnc.tnc.world.stonecrest;
import net.minecraft.core.*;
import net.minecraft.resources.*;
import net.minecraft.server.level.*;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.phys.*;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
/** An isolated fixed estate; no patching player islands or existing overworld structures. */
@Mod.EventBusSubscriber(modid="tnc")
public final class HeroskandRealm {
 public static final ResourceKey<Level> KEY=ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION,ResourceLocation.fromNamespaceAndPath("tnc","heroskand"));
 public static final BlockPos ORIGIN=new BlockPos(-312,64,-432),LANDING=new BlockPos(0,65,452);
 private static final java.util.Set<java.util.UUID> PENDING=new java.util.HashSet<>();
 public static Block GATE;
 @Mod.EventBusSubscriber(modid="tnc",bus=Mod.EventBusSubscriber.Bus.MOD)
 public static class Content{
  @SubscribeEvent public static void register(net.minecraftforge.registries.RegisterEvent e){e.register(net.minecraft.core.registries.Registries.BLOCK,h->{GATE=new Block(BlockBehaviour.Properties.copy(Blocks.CRYING_OBSIDIAN).strength(4).lightLevel(s->10)){
   @Override public InteractionResult use(BlockState s,Level l,BlockPos p,Player player,InteractionHand hand,BlockHitResult hit){if(player instanceof ServerPlayer sp)travel(sp);return InteractionResult.sidedSuccess(l.isClientSide);}
  };h.register(ResourceLocation.fromNamespaceAndPath("tnc","heroskand_gate"),GATE);});e.register(net.minecraft.core.registries.Registries.ITEM,h->h.register(ResourceLocation.fromNamespaceAndPath("tnc","heroskand_gate"),new BlockItem(GATE,new Item.Properties())));}
  @SubscribeEvent public static void tab(net.minecraftforge.event.BuildCreativeModeTabContentsEvent e){if(e.getTabKey()==CreativeModeTabs.FUNCTIONAL_BLOCKS)e.accept(GATE);}
 }
 @SubscribeEvent public static void started(net.minecraftforge.event.server.ServerStartedEvent e){var l=e.getServer().getLevel(KEY);if(l!=null){l.getWorldBorder().setCenter(0,0);l.getWorldBorder().setSize(1024);}}
 @SubscribeEvent public static void stop(net.minecraftforge.event.server.ServerStoppedEvent e){PENDING.clear();}
 public static void travel(ServerPlayer p){if(!PENDING.add(p.getUUID()))return;var server=p.getServer();var tag=p.getPersistentData();boolean returning=p.level().dimension().equals(KEY);ServerLevel target;Vec3 at;float yaw,pitch;
  if(returning){var id=ResourceLocation.tryParse(tag.getString("HeroskandReturnDimension"));target=id==null?server.overworld():server.getLevel(ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION,id));if(target==null||target.dimension().equals(KEY))target=server.overworld();at=tag.contains("HeroskandReturnX")?new Vec3(tag.getDouble("HeroskandReturnX"),tag.getDouble("HeroskandReturnY"),tag.getDouble("HeroskandReturnZ")):Vec3.atBottomCenterOf(target.getSharedSpawnPos());yaw=tag.getFloat("HeroskandReturnYaw");pitch=tag.getFloat("HeroskandReturnPitch");}
  else{target=server.getLevel(KEY);if(target==null){PENDING.remove(p.getUUID());p.sendSystemMessage(net.minecraft.network.chat.Component.literal("赫萝斯堪德界域尚未载入，请重新启动世界。"));return;}tag.putString("HeroskandReturnDimension",p.level().dimension().location().toString());tag.putDouble("HeroskandReturnX",p.getX());tag.putDouble("HeroskandReturnY",p.getY());tag.putDouble("HeroskandReturnZ",p.getZ());tag.putFloat("HeroskandReturnYaw",p.getYRot());tag.putFloat("HeroskandReturnPitch",p.getXRot());at=Vec3.atBottomCenterOf(LANDING);yaw=180;pitch=0;}
  // Request only the landing chunk asynchronously. Estate slices are generated natively as explored.
  var dest=target;var point=at;float y=yaw,x=pitch;dest.getChunkSource().getChunkFuture(net.minecraft.util.Mth.floor(at.x)>>4,net.minecraft.util.Mth.floor(at.z)>>4,net.minecraft.world.level.chunk.ChunkStatus.FULL,true).whenCompleteAsync((chunk,error)->{try{if(error!=null||p.isRemoved()){if(error!=null)p.sendSystemMessage(net.minecraft.network.chat.Component.literal("界门暂时无法打开，请稍后重试。"));return;}if(!returning){dest.setBlock(LANDING.below(),Blocks.POLISHED_DEEPSLATE.defaultBlockState(),3);dest.setBlock(LANDING.above(),Blocks.AIR.defaultBlockState(),3);dest.setBlock(LANDING,GATE.defaultBlockState(),3);p.teleportTo(dest,point.x+1.5,point.y,point.z,y,x);}else p.teleportTo(dest,point.x,point.y,point.z,y,x);}finally{PENDING.remove(p.getUUID());}},server);
 }
}
