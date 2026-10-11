package com.tnc.tnc.life.routes;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.event.*;
import net.minecraftforge.event.level.*;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
/** Only fresh wilderness chunks; installs no structures and never changes the town. */
@Mod.EventBusSubscriber(modid="tnc")
public final class RouteNature {
    private record Pending(ServerLevel level,net.minecraft.world.level.ChunkPos pos){}
    private static final Queue<Pending> QUEUE=new ConcurrentLinkedQueue<>();
    @SubscribeEvent public static void load(ChunkEvent.Load e){if(e.isNewChunk()&&e.getLevel() instanceof ServerLevel l&&l==l.getServer().overworld()&&QUEUE.size()<4096)QUEUE.add(new Pending(l,e.getChunk().getPos()));}
    @SubscribeEvent public static void stop(net.minecraftforge.event.server.ServerStoppedEvent e){QUEUE.clear();}
    @SubscribeEvent public static void tick(TickEvent.ServerTickEvent e){if(e.phase!=TickEvent.Phase.END)return;for(int jobs=0;jobs<2;jobs++){var next=QUEUE.poll();if(next==null)return;var l=next.level;var cp=next.pos;var chunk=l.getChunkSource().getChunkNow(cp.x,cp.z);if(chunk==null||chunk.getInhabitedTime()>1200||!chunk.getAllReferences().isEmpty()||!chunk.getBlockEntitiesPos().isEmpty()||l.random.nextInt(4)!=0)continue;var town=com.tnc.tnc.world.SkyIslandSavedData.get(l).anchorPos("CENTER");if(town==null)continue;String biome=l.getBiome(new BlockPos(cp.getMiddleBlockX(),l.getSeaLevel(),cp.getMiddleBlockZ())).unwrapKey().map(k->k.location().getPath()).orElse("");var list=candidates(biome);if(list.isEmpty())continue;var kind=list.get(l.random.nextInt(list.size()));for(int attempt=0;attempt<12;attempt++){var p=l.getHeightmapPos(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,new BlockPos(cp.getMinBlockX()+l.random.nextInt(16),0,cp.getMinBlockZ()+l.random.nextInt(16)));long dx=(long)p.getX()-town.getX(),dz=(long)p.getZ()-town.getZ();if(dx*dx+dz*dz<=810000||com.tnc.tnc.home.TownProtection.hazard(l,p)||com.tnc.tnc.home.HousingService.plotAt(l,p)!=null||!l.getBlockState(p).isAir())continue;if(l.random.nextInt(4)==0&&RouteWildSpecimens.place(l,p,biome))break;var state=RouteContent.PLANTS.get(kind.id).get().defaultBlockState().setValue(RoutePlantBlock.WILD,true).setValue(RoutePlantBlock.AGE,3);if(!state.canSurvive(l,p))continue;l.setBlock(p,state,3);if(l.getBlockEntity(p) instanceof RoutePlantEntity be){be.growth=18000;be.setChanged();}break;}}}
    static List<NewPlantKind> candidates(String biome){if(biome.contains("forest")||biome.contains("jungle"))return List.of(NewPlantKind.COMPOST,NewPlantKind.PAGE,NewPlantKind.SHADOW);if(biome.contains("swamp")||biome.contains("river"))return List.of(NewPlantKind.DEW,NewPlantKind.COMPOST);if(biome.contains("peak")||biome.contains("hill")||biome.contains("slope"))return List.of(NewPlantKind.ORE,NewPlantKind.THERMAL,NewPlantKind.STORM);if(biome.contains("plains")||biome.contains("meadow")||biome.contains("savanna"))return List.of(NewPlantKind.CYCLE,NewPlantKind.PRISM,NewPlantKind.STORM);return List.of();}
    @SubscribeEvent public static void bolt(net.minecraftforge.event.entity.EntityJoinLevelEvent e){if(!(e.getLevel() instanceof ServerLevel l)||!(e.getEntity() instanceof net.minecraft.world.entity.LightningBolt bolt))return;if(bolt.getCause()!=null||bolt.getPersistentData().getBoolean("TncArtificial"))return;for(var p:BlockPos.betweenClosed(bolt.blockPosition().offset(-3,-1,-3),bolt.blockPosition().offset(3,2,3)))if(l.hasChunkAt(p)&&l.getBlockEntity(p) instanceof RoutePlantEntity be&&be.kind()==NewPlantKind.STORM&&be.growth>=18000&&RouteAccess.allowed(l,p,be.owner)&&MagicSoilBlock.growing(l,p))be.naturalLightning();}
}
