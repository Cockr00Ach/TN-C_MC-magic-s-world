package com.tnc.tnc.life.botanical;

import com.tnc.tnc.TNMod;
import com.tnc.tnc.home.TownProtection;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.*;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.Bee;
import net.minecraft.world.item.*;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingChangeTargetEvent;
import net.minecraftforge.event.entity.player.*;
import net.minecraftforge.event.level.*;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.*;
import net.minecraftforge.fml.common.Mod;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;

/** Real natural encounters and non-destructive old-map seed research. */
@Mod.EventBusSubscriber(modid=TNMod.MODID)
public final class BotanicalEvents {
    private record NewChunk(String dimension,long pos){}
    private static final Queue<NewChunk> NEW_CHUNKS=new ConcurrentLinkedQueue<>();
    private BotanicalEvents(){}
    @SubscribeEvent public static void newChunk(ChunkEvent.Load e){if(e.isNewChunk()&&e.getLevel() instanceof ServerLevel l&&NEW_CHUNKS.size()<8192)NEW_CHUNKS.add(new NewChunk(l.dimension().location().toString(),e.getChunk().getPos().toLong()));}
    @SubscribeEvent public static void stop(ServerStoppedEvent e){NEW_CHUNKS.clear();}
    @SubscribeEvent public static void nature(TickEvent.ServerTickEvent e){if(e.phase!=TickEvent.Phase.END)return;var server=net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer();if(server==null||server.getTickCount()%20!=0)return;
        for(int job=0;job<8;job++){var pending=NEW_CHUNKS.poll();if(pending==null)break;ServerLevel l=null;for(var world:server.getAllLevels())if(world.dimension().location().toString().equals(pending.dimension)){l=world;break;}if(l==null||l!=server.overworld())continue;var cp=new net.minecraft.world.level.ChunkPos(pending.pos);var chunk=l.getChunkSource().getChunkNow(cp.x,cp.z);if(chunk==null)continue;BlockPos anchor=com.tnc.tnc.world.SkyIslandSavedData.get(l).anchorPos("CENTER");if(anchor==null){NEW_CHUNKS.add(pending);continue;}if(chunk.getInhabitedTime()>1200||!chunk.getBlockEntitiesPos().isEmpty()||!chunk.getAllReferences().isEmpty())continue;
            var biome=l.getBiome(new BlockPos(cp.getMiddleBlockX(),l.getSeaLevel(),cp.getMiddleBlockZ())).unwrapKey().map(k->k.location().getPath()).orElse("");var candidates=naturalSpecies(biome);if(candidates.isEmpty()||l.random.nextInt(3)!=0)continue;var species=candidates.get(l.random.nextInt(candidates.size()));
            for(int attempt=0;attempt<24;attempt++){int x=cp.getMinBlockX()+l.random.nextInt(16),z=cp.getMinBlockZ()+l.random.nextInt(16);BlockPos at=l.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,new BlockPos(x,0,z));long dx=(long)at.getX()-anchor.getX(),dz=(long)at.getZ()-anchor.getZ();if(dx*dx+dz*dz<=810000||TownProtection.hazard(l,at)||com.tnc.tnc.home.HousingService.plotAt(l,at)!=null||!l.getBlockState(at).isAir())continue;var state=BotanicalContent.BLOCKS.get(species.id).defaultBlockState().setValue(BotanicalBlock.AGE,3).setValue(BotanicalBlock.WILD,true);if(!state.canSurvive(l,at)||!naturalSite(l,at,species))continue;if(species==BotanicalSpecies.PAPER_TREE&&(!l.getBlockState(at.above()).isAir()||!l.getBlockState(at.above(2)).isAir()))continue;l.setBlock(at,state,3);if(l.getBlockEntity(at) instanceof BotanicalPlantEntity be){be.setWild();if(species==BotanicalSpecies.PAPER_TREE)be.bookStudied=true;}break;}
        }
    }
    static List<BotanicalSpecies> naturalSpecies(String biome){return switch(biome){
        case "sunflower_plains","plains","meadow"->List.of(BotanicalSpecies.DAWN_DISK,BotanicalSpecies.DANCE_BELL,BotanicalSpecies.HONEY_CLUSTER);
        case "forest","birch_forest","old_growth_birch_forest"->List.of(BotanicalSpecies.MIST_COTTON,BotanicalSpecies.PAPER_TREE,BotanicalSpecies.SHADOW_CUT);
        case "dark_forest","old_growth_pine_taiga"->List.of(BotanicalSpecies.ECHO_BEAN,BotanicalSpecies.SLEEP_CLOCK,BotanicalSpecies.STAR_DEW);
        case "swamp","mangrove_swamp","river"->List.of(BotanicalSpecies.MIRROR_LOTUS,BotanicalSpecies.WISH_PUFF,BotanicalSpecies.FLIGHT_POD);
        case "snowy_slopes","snowy_taiga","grove"->List.of(BotanicalSpecies.FROST_CHIME);
        case "windswept_hills","jagged_peaks","stony_peaks"->List.of(BotanicalSpecies.WIND_SAIL,BotanicalSpecies.STONE_FERN,BotanicalSpecies.STAR_REST);
        case "jungle","sparse_jungle","bamboo_jungle"->List.of(BotanicalSpecies.LADDER_VINE,BotanicalSpecies.HONEY_CLUSTER,BotanicalSpecies.HEARTH_PEPPER);
        case "beach","stony_shore"->List.of(BotanicalSpecies.SALT_INK,BotanicalSpecies.STONE_FERN);
        default->List.of();};}
    /** Stable habitat only: no hour, rain event, beds or man-made furnace requirements. */
    static boolean naturalSite(ServerLevel l,BlockPos p,BotanicalSpecies species){
        var biome=l.getBiome(p).unwrapKey().map(k->k.location().getPath()).orElse("");
        if(!naturalSpecies(biome).contains(species))return false;
        int light=l.getBrightness(LightLayer.SKY,p);
        boolean water=near(l,p,3,q->q.getFluidState().is(FluidTags.WATER));
        return switch(species){
            case MIST_COTTON->water&&light>=8&&light<=11;
            case STONE_FERN,SLEEP_CLOCK->light<=11;
            case WISH_PUFF,FLIGHT_POD->water;
            case FROST_CHIME->l.getBiome(p).value().getBaseTemperature()<=.15F;
            case WIND_SAIL->l.canSeeSky(p)&&p.getY()>=l.getSeaLevel()+20;
            case SALT_INK->water&&light<=7;
            case SHADOW_CUT->light>=8&&light<=11&&near(l,p.above(2),1,q->!q.isAir());
            case PAPER_TREE,LADDER_VINE->l.getBlockState(p.above()).isAir()&&l.getBlockState(p.above(2)).isAir();
            case STAR_DEW,STAR_REST,DAWN_DISK->l.canSeeSky(p);
            case HEARTH_PEPPER->l.getBiome(p).value().getBaseTemperature()>=.8F;
            default->true;
        };
    }
    private static boolean wet(ServerLevel l,BlockPos p){if(l.isRainingAt(p))return true;for(var d:net.minecraft.core.Direction.Plane.HORIZONTAL)if(l.getFluidState(p.below().relative(d)).is(FluidTags.WATER))return true;return false;}
    private static boolean near(ServerLevel l,BlockPos p,int radius,java.util.function.Predicate<BlockState> predicate){for(var at:BlockPos.betweenClosed(p.offset(-radius,-1,-radius),p.offset(radius,1,radius)))if(l.hasChunkAt(at)&&predicate.test(l.getBlockState(at)))return true;return false;}
    public static List<BotanicalSpecies> samples(ServerPlayer p,BlockPos at,BlockState state){var l=p.serverLevel();int sky=l.getBrightness(LightLayer.SKY,at);long clock=Math.floorMod(l.getDayTime(),24000);String biome=l.getBiome(at).unwrapKey().map(k->k.location().getPath()).orElse("");List<BotanicalSpecies> out=new ArrayList<>();
        if(state.is(Blocks.SUNFLOWER)&&clock<200&&l.canSeeSky(at.above()))out.add(BotanicalSpecies.DAWN_DISK);
        if(state.is(Blocks.RED_MUSHROOM)&&near(l,at,2,b->b.is(Blocks.LAVA)||b.is(Blocks.CAMPFIRE)&&b.getValue(CampfireBlock.LIT)))out.add(BotanicalSpecies.HEARTH_PEPPER);
        if(state.is(Blocks.FERN)&&wet(l,at)&&sky>=8&&sky<=11&&(clock<1200||p.getPersistentData().getLong("TncRainStopped")>l.getGameTime()-200))out.add(BotanicalSpecies.MIST_COTTON);
        if(state.is(net.minecraft.tags.BlockTags.BASE_STONE_OVERWORLD)&&l.getChunkAt(at).getAllReferences().isEmpty())out.add(BotanicalSpecies.STONE_FERN);
        if(state.is(Blocks.LILY_PAD)&&l.getFluidState(at.below()).isSource()&&(l.getBlockState(at.below(2)).is(net.minecraft.tags.BlockTags.DIRT)||l.getBlockState(at.below(2)).is(Blocks.CLAY)))out.add(BotanicalSpecies.MIRROR_LOTUS);
        if(state.is(Blocks.BLUE_ORCHID)&&clock>=12000&&wet(l,at))out.add(BotanicalSpecies.WISH_PUFF);
        if(state.is(Blocks.VINE))out.add(BotanicalSpecies.LADDER_VINE);
        if((state.is(Blocks.FERN)||state.is(Blocks.SNOW))&&l.getBiome(at).value().getBaseTemperature()<=.15F)out.add(BotanicalSpecies.FROST_CHIME);
        if(state.is(Blocks.BROWN_MUSHROOM)&&(biome.contains("beach")||biome.contains("shore")||biome.contains("ocean"))&&near(l,at,4,b->b.is(Blocks.WATER)))out.add(BotanicalSpecies.SALT_INK);
        if((state.is(Blocks.GRASS)||state.is(Blocks.TALL_GRASS))&&at.getY()>=l.getSeaLevel()+20&&l.canSeeSky(at.above()))out.add(BotanicalSpecies.WIND_SAIL);
        if(state.is(Blocks.ALLIUM)&&p.getPersistentData().contains("TncActualWake")&&l.getGameTime()-p.getPersistentData().getLong("TncActualWake")<1200&&near(l,at,3,b->b.getBlock() instanceof BedBlock))out.add(BotanicalSpecies.SLEEP_CLOCK);
        if(state.is(Blocks.CORNFLOWER)&&sky>=8&&sky<=11)out.add(BotanicalSpecies.SHADOW_CUT);
        if(state.is(Blocks.BIRCH_SAPLING))out.add(BotanicalSpecies.PAPER_TREE);
        if(state.is(Blocks.FERN)&&wet(l,at)&&(biome.contains("river")||biome.contains("swamp")))out.add(BotanicalSpecies.FLIGHT_POD);
        if(state.is(Blocks.FLOWERING_AZALEA)&&!l.getEntitiesOfClass(Bee.class,new AABB(at).inflate(8)).isEmpty())out.add(BotanicalSpecies.HONEY_CLUSTER);
        if(state.is(Blocks.SWEET_BERRY_BUSH)&&clock>=13000&&l.canSeeSky(at))out.add(BotanicalSpecies.STAR_DEW);
        if(state.is(Blocks.POPPY)&&p.getOffhandItem().is(BotanicalContent.TOOLS.get("field_tuning_bell")))out.add(BotanicalSpecies.DANCE_BELL);
        return out;
    }
    public static boolean research(ServerPlayer p,BotanicalSpecies species){var data=p.getPersistentData();String key="TncBotanicalSamples_"+species.id;int tries=data.getInt(key)+1;data.putInt(key,tries);boolean known=data.getBoolean("TncBotanicalKnown_"+species.id);if(!known&&tries>=10||p.getRandom().nextInt(10)==0){ItemStack seed=new ItemStack(BotanicalContent.SEEDS.get(species.id),known?1:2);if(!p.getInventory().add(seed))p.drop(seed,false);data.putBoolean("TncBotanicalKnown_"+species.id,true);data.putInt(key,0);p.displayClientMessage(Component.literal("发现 "+species.name+" · "+species.help),false);return true;}p.displayClientMessage(Component.literal(species.name+"采样 "+tries+"/10；首份种源十次有效采样必得。"),true);return false;}
    @SubscribeEvent(priority=EventPriority.LOWEST) public static void sample(PlayerInteractEvent.RightClickBlock e){if(e.isCanceled()||e.getHand()!=InteractionHand.MAIN_HAND||!(e.getEntity() instanceof ServerPlayer p)||p.isSpectator()||TownProtection.denied(p,e.getPos()))return;var held=e.getItemStack();if(!held.is(Items.SHEARS)&&!held.is(BotanicalContent.TOOLS.get("plant_sample_clip")))return;var state=p.level().getBlockState(e.getPos());if(state.getBlock() instanceof BotanicalBlock)return;var data=p.getPersistentData();long now=p.level().getGameTime();if(data.contains("TncSampleAt")&&now-data.getLong("TncSampleAt")<40)return;
        if(state.is(Blocks.ALLIUM)&&!p.serverLevel().isDay()&&p.serverLevel().canSeeSky(e.getPos())&&e.getPos().getY()>=p.level().getSeaLevel()+20&&has(p,Items.AMETHYST_SHARD,1)){if(!data.contains("TncStarObserve")||data.getLong("TncStarObservePos")!=e.getPos().asLong()){data.putLong("TncStarObserve",now);data.putLong("TncStarObservePos",e.getPos().asLong());p.displayClientMessage(Component.literal("保持在此观察10秒，再次采样；紫晶会保留。"),true);}else if(now-data.getLong("TncStarObserve")>=200){research(p,BotanicalSpecies.STAR_REST);data.remove("TncStarObserve");}e.setCanceled(true);e.setCancellationResult(InteractionResult.CONSUME);return;}
        var candidates=samples(p,e.getPos(),state);if(candidates.isEmpty())return;var species=candidates.get(0);if(species==BotanicalSpecies.PAPER_TREE){if(!has(p,Items.PAPER,2)||!has(p,BotanicalContent.PRODUCTS.get("salt_ink"),1)){p.displayClientMessage(Component.literal("墨信树研究：桦苗上采样，随身纸2与盐墨1。保留原苗。"),true);return;}take(p,Items.PAPER,2);take(p,BotanicalContent.PRODUCTS.get("salt_ink"),1);ItemStack seed=new ItemStack(BotanicalContent.SEEDS.get(species.id));if(!p.getInventory().add(seed))p.drop(seed,false);data.putBoolean("TncBotanicalKnown_"+species.id,true);}else research(p,species);data.putLong("TncSampleAt",now);held.hurtAndBreak(1,p,user->user.broadcastBreakEvent(e.getHand()));e.setCanceled(true);e.setCancellationResult(InteractionResult.CONSUME);
    }
    private static boolean has(ServerPlayer p,Item item,int amount){int count=0;for(var stack:p.getInventory().items)if(stack.is(item))count+=stack.getCount();return count>=amount;}
    private static void take(ServerPlayer p,Item item,int amount){for(var stack:p.getInventory().items)if(stack.is(item)){int n=Math.min(amount,stack.getCount());stack.shrink(n);amount-=n;if(amount==0)return;}}
    @SubscribeEvent public static void note(NoteBlockEvent.Play e){if(e.isCanceled()||!(e.getLevel() instanceof ServerLevel l))return;for(var pos:BlockPos.betweenClosed(e.getPos().offset(-3,-2,-3),e.getPos().offset(3,2,3)))if(l.hasChunkAt(pos)&&l.getBlockEntity(pos) instanceof BotanicalPlantEntity be&&be.species()==BotanicalSpecies.ECHO_BEAN)be.note(e.getVanillaNoteId());
        for(var p:l.getEntitiesOfClass(ServerPlayer.class,new AABB(e.getPos()).inflate(5))){var data=p.getPersistentData();int step=data.getInt("TncEchoResearch");int pitch=e.getVanillaNoteId()%12,expected=step==0?6:step==1?10:1;if(pitch==expected){step++;data.putInt("TncEchoResearch",step);if(step==3){data.putInt("TncEchoResearch",0);if(!data.getBoolean("TncBotanicalKnown_echo_bean")){ItemStack seeds=new ItemStack(BotanicalContent.SEEDS.get("echo_bean"),2);if(!p.getInventory().add(seeds))p.drop(seeds,false);data.putBoolean("TncBotanicalKnown_echo_bean",true);p.displayClientMessage(Component.literal("三音 C—E—G 研究成功：发现回声豆种源。"),false);}}}else data.putInt("TncEchoResearch",pitch==6?1:0);}
    }
    @SubscribeEvent public static void wake(PlayerWakeUpEvent e){if(!(e.getEntity() instanceof ServerPlayer p)||e.wakeImmediately()||e.updateLevel()||p.getSleepTimer()<100)return;p.getPersistentData().putLong("TncActualWake",p.level().getGameTime());for(var at:BlockPos.betweenClosed(p.blockPosition().offset(-3,-2,-3),p.blockPosition().offset(3,2,3)))if(p.level().hasChunkAt(at)&&p.level().getBlockEntity(at) instanceof BotanicalPlantEntity be)be.sleep(p.getUUID(),p.level().getDayTime()/24000);}
    @SubscribeEvent public static void playerTick(TickEvent.PlayerTickEvent e){if(e.phase!=TickEvent.Phase.END||!(e.player instanceof ServerPlayer p)||p.tickCount%20!=0)return;var l=p.serverLevel();var data=p.getPersistentData();if(data.getBoolean("TncObservedRain")&&!l.isRaining())data.putLong("TncRainStopped",l.getGameTime());data.putBoolean("TncObservedRain",l.isRaining());
        if(data.getLong("TncWarmUntil")>l.getGameTime())p.setTicksFrozen(0);if(data.getLong("TncMirrorUntil")>l.getGameTime()&&p.isInWater())p.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.NIGHT_VISION,220,0));
        if(data.getLong("TncFogUntil")>l.getGameTime()){BlockPos center=BlockPos.of(data.getLong("TncFogCenter"));if(p.distanceToSqr(center.getX()+.5,center.getY(),center.getZ()+.5)>18)data.remove("TncFogUntil");else l.sendParticles(net.minecraft.core.particles.ParticleTypes.CLOUD,center.getX()+.5,center.getY()+1,center.getZ()+.5,10,2.3,.5,2.3,.005);}
        if(p.hasEffect(net.minecraft.world.effect.MobEffects.SLOW_FALLING)&&p.onGround()&&p.getCooldowns().isOnCooldown(BotanicalContent.PRODUCTS.get("folded_descent_cloth")))p.removeEffect(net.minecraft.world.effect.MobEffects.SLOW_FALLING);
    }
    @SubscribeEvent public static void target(LivingChangeTargetEvent e){if(e.getNewTarget() instanceof ServerPlayer p&&e.getEntity() instanceof Mob mob&&mob.getTarget()==null&&p.getPersistentData().getLong("TncFogUntil")>p.level().getGameTime()&&mob.distanceToSqr(p)>64)e.setCanceled(true);}
    @SubscribeEvent public static void attack(LivingAttackEvent e){if(e.getSource().getEntity() instanceof ServerPlayer p)p.getPersistentData().remove("TncFogUntil");}
}
