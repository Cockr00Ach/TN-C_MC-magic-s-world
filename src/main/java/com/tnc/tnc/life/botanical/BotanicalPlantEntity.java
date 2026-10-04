package com.tnc.tnc.life.botanical;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.animal.Bee;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import java.util.*;

/** One saved growing root per plant. Events and charge are never synthesized offline. */
public final class BotanicalPlantEntity extends BlockEntity {
    public UUID owner,motherId=UUID.randomUUID();
    public int growth,harvests,pattern,notes,pollinations,ladderHeight=1,dance,danceBefore;
    public long danceUpdated;
    public boolean wild,wildHarvested,bookStudied,ready,knownClock,wasGrowing,wasRain;
    public long lastActiveTick,wetUntil,lastDawn=-1,lastSleep=-1,lastPollination=-100,lastPlayback=-100,lastNight=-1;
    public int returns;
    public final List<UUID> spriteIds=new ArrayList<>();
    public final Set<UUID> nectarVisitors=new HashSet<>();
    public long lastClock=-1;
    public BotanicalPlantEntity(BlockPos p,BlockState s){super(BotanicalContent.PLANT_ENTITY,p,s);}
    public BotanicalSpecies species(){return ((BotanicalBlock)getBlockState().getBlock()).species;}
    public void plantedBy(UUID id){owner=species()==BotanicalSpecies.DANCE_BELL?null:id;wild=false;changed();}
    public void setWild(){wild=true;growth=species().firstTicks;ready=!species().eventCrop();notes=3;pollinations=3;changed();}
    public void changed(){setChanged();if(level!=null&&!level.isClientSide)level.sendBlockUpdated(worldPosition,getBlockState(),getBlockState(),2);}
    public void water(UUID player,boolean transfer){if(level==null)return;wetUntil=level.getGameTime()+12000;if(species()==BotanicalSpecies.DANCE_BELL&&(owner==null||transfer))owner=player;changed();}
    /** Permission is checked by the caller; this method never claims ownership. */
    public void water(int ticks){if(level==null)return;wetUntil=Math.max(wetUntil,level.getGameTime()+Math.max(0,Math.min(12000,ticks)));changed();}
    private boolean wet(ServerLevel l){if(l.getGameTime()<wetUntil||l.isRainingAt(worldPosition))return true;for(Direction d:Direction.Plane.HORIZONTAL)if(l.getFluidState(worldPosition.below().relative(d)).is(FluidTags.WATER))return true;return false;}
    private boolean adjacent(ServerLevel l,java.util.function.Predicate<BlockState> predicate,int r){for(BlockPos p:BlockPos.betweenClosed(worldPosition.offset(-r,-1,-r),worldPosition.offset(r,1,r)))if(l.hasChunkAt(p)&&predicate.test(l.getBlockState(p)))return true;return false;}
    private boolean sky(ServerLevel l){return l.canSeeSky(worldPosition);}
    public String reason(ServerLevel l){
        if(!wild) {
            int height=species()==BotanicalSpecies.LADDER_VINE?Math.min(4,Math.max(1,(growth+20)/7200)):species()==BotanicalSpecies.PAPER_TREE?3:1;
            return space(l,height)?"":"上方生长空间被挡住";
        }
        int sky=l.getBrightness(LightLayer.SKY,worldPosition);var s=species();return switch(s){
        case DAWN_DISK,STAR_REST -> sky(l)?"":"需要露天";
        case HEARTH_PEPPER -> adjacent(l,b->b.hasProperty(AbstractFurnaceBlock.LIT)&&b.getValue(AbstractFurnaceBlock.LIT)||b.is(Blocks.CAMPFIRE)&&b.getValue(CampfireBlock.LIT),2)?"":"没有真正燃烧的余热";
        case MIST_COTTON -> !wet(l)?"根旁缺水":sky<8||sky>11?"需要半阴（天空光8—11）":"";
        case STONE_FERN -> sky<=11?"":"石面太亮";
        case MIRROR_LOTUS -> getBlockState().canSurvive(l,worldPosition)?"":"需要浅静水，下为土或泥";
        case WISH_PUFF -> !wet(l)?"根旁缺水":l.isDay()&&l.getDayTime()%24000<12000?"等候黄昏或夜晚":"";
        case ECHO_BEAN -> notes<3?"还没有听过连续三音":!l.isDay()||sky<9?"等候日照":"";
        case LADDER_VINE -> hasFrame(l)?space(l,Math.min(4,Math.max(1,(growth+20)/7200)))?"":"上方空间被挡住":"需要相邻木支架";
        case FROST_CHIME -> l.getBiome(worldPosition).value().getBaseTemperature()<=.15F||adjacent(l,b->b.is(Blocks.ICE)||b.is(Blocks.PACKED_ICE)||b.is(Blocks.BLUE_ICE),1)?"":"需要寒地或相邻冷箱冰块";
        case SALT_INK -> sky>7?"盐墨菌需要阴处":saltHabitat(l)?"":"需要海洋水边或装水盐盆";
        case WIND_SAIL -> !sky(l)?"需要露天":l.isRaining()||l.isThundering()?"风帆避雨收拢":worldPosition.getY()<l.getSeaLevel()+20?"需要高坡":!hasFrame(l)?"需要相邻支架":"";
        case SLEEP_CLOCK -> sky>11?"需要阴处或半阴":adjacent(l,b->b.getBlock() instanceof BedBlock,2)?"":"需要两格内的床";
        case SHADOW_CUT -> sky<8||sky>11?"需要半阴":canopy(l)==0?"上方两格没有遮棚影纹":"";
        case PAPER_TREE -> !space(l,3)?"三格高生长包络被挡住":!bookStudied&&!adjacent(l,b->b.is(Blocks.LECTERN),2)?"需要讲台或用普通书照料":"";
        case FLIGHT_POD -> wet(l)?"":"根旁缺水";
        case HONEY_CLUSTER -> "";
        case STAR_DEW -> !sky(l)?"需要露天":l.isDay()?"等候露天夜晚":"";
        case DANCE_BELL -> l.isDay()&&sky>=9?"":"等候日照";
    };}
    private boolean hasFrame(ServerLevel l){return adjacent(l,b->b.is(BotanicalContent.FIELD_FRAME)||b.is(Blocks.OAK_FENCE)||b.is(Blocks.BAMBOO_FENCE),1);}
    private boolean space(ServerLevel l,int height){for(int y=1;y<height;y++){BlockPos p=worldPosition.above(y);if(!l.hasChunkAt(p))return false;var state=l.getBlockState(p);if(!state.isAir()&&!state.is(BotanicalContent.VINE_SEGMENT)&&!state.is(BotanicalContent.PAPER_SEGMENT))return false;}return true;}
    private boolean saltHabitat(ServerLevel l){if(adjacent(l,b->b.is(BotanicalContent.SALT_BASIN)||b.is(Blocks.WATER_CAULDRON),4))return true;var key=l.getBiome(worldPosition).unwrapKey();return key.isPresent()&&key.get().location().getPath().contains("ocean")&&wet(l);}
    private int canopy(ServerLevel l){int mask=0;for(int x=-1;x<=1;x++)for(int z=-1;z<=1;z++)if(l.hasChunkAt(worldPosition.offset(x,2,z))&&!l.getBlockState(worldPosition.offset(x,2,z)).isAir())mask|=1<<((x+1)*3+z+1);return mask;}
    public boolean canHarvest(){return growth>=species().firstTicks&&(!species().eventCrop()||ready)&&(species()!=BotanicalSpecies.HONEY_CLUSTER||pollinations>=3);}
    public void pollinate(){if(level==null||level.getGameTime()-lastPollination<20||pollinations>=3)return;lastPollination=level.getGameTime();pollinations++;changed();}
    public boolean pollinateOnce(){int before=pollinations;pollinate();return pollinations>before;}
    public void note(int pitch){if(level==null)return;pattern=Math.max(0,Math.min(24,pitch));notes=Math.min(3,notes+1);changed();}
    public void sleep(UUID id,long day){if(species()!=BotanicalSpecies.SLEEP_CLOCK||owner==null||!owner.equals(id)||growth<species().firstTicks||lastSleep==day)return;lastSleep=day;ready=true;changed();}
    public void harvest(){var s=species();ready=false;returns=0;pollinations=0;growth=s.firstTicks-s.regrowTicks;if(level instanceof ServerLevel l){updateSegments(l);l.setBlock(worldPosition,getBlockState().setValue(BotanicalBlock.AGE,1),3);}changed();}
    public String status(){if(!(level instanceof ServerLevel l))return species().help;String why=reason(l);if(growth<species().firstTicks)return species().name+" · "+(growth*100/species().firstTicks)+"% · "+(why.isEmpty()?"正在成长":why);if(species()==BotanicalSpecies.HONEY_CLUSTER&&pollinations<3)return "蜜簇已长成 · 授粉 "+pollinations+"/3";if(!canHarvest())return species().name+"已成株 · "+switch(species()){case DAWN_DISK ->"等待一次真实黎明";case MIST_COTTON ->"等待清晨或雨停结绒";case SLEEP_CLOCK ->"等认养者在床旁睡醒";case STAR_REST ->"花灵夜游中，等待至少两只回归";default -> why;};return "已经成熟 · "+species().help;}
    public static void tick(ServerLevel l,BlockPos p,BlockState state,BotanicalPlantEntity be){if(l.getGameTime()%20!=0)return;var species=be.species();long now=l.getGameTime();boolean consecutive=be.knownClock&&now-be.lastActiveTick<=40;boolean valid=be.reason(l).isEmpty();
        if(valid&&be.growth<species.firstTicks)be.growth=Math.min(species.firstTicks,be.growth+20);
        be.lastActiveTick=now;be.knownClock=true;be.wasGrowing=valid;
        int age=Math.max(be.harvests>0?1:0,Math.min(3,be.growth*3/species.firstTicks)),mode=valid?1:0;
        if(species==BotanicalSpecies.STONE_FERN){String rock=net.minecraftforge.registries.ForgeRegistries.BLOCKS.getKey(l.getBlockState(p.below()).getBlock()).getPath();be.pattern=rock.contains("basalt")?1:rock.contains("sand")?2:rock.contains("calcite")||rock.contains("andesite")?3:0;mode=be.pattern;}
        if(species==BotanicalSpecies.SHADOW_CUT){int mask=be.canopy(l);be.pattern=(mask^(mask>>3)^(mask>>6))&7;mode=be.pattern&3;}
        if(species==BotanicalSpecies.MIRROR_LOTUS)mode=l.isRaining()?2:l.isDay()?1:3;
        long day=l.getDayTime()/24000,clock=Math.floorMod(l.getDayTime(),24000);
        if(be.growth>=species.firstTicks){
            if(species==BotanicalSpecies.DAWN_DISK&&valid&&consecutive&&clock<200&&be.lastClock>=23000&&be.lastDawn!=day){be.lastDawn=day;be.ready=true;l.playSound(null,p,net.minecraft.sounds.SoundEvents.AMETHYST_BLOCK_CHIME,net.minecraft.sounds.SoundSource.BLOCKS,.35F,1.4F);}
            if(species==BotanicalSpecies.MIST_COTTON&&valid&&(clock<1200||consecutive&&be.wasRain&&!l.isRaining()))be.ready=true;
            if(species==BotanicalSpecies.HONEY_CLUSTER){be.nectarVisitors.removeIf(id->l.getEntity(id) instanceof Bee bee&&!bee.hasNectar());for(var bee:l.getEntitiesOfClass(Bee.class,new AABB(p).inflate(1.4)))if(bee.hasNectar()&&bee.hasSavedFlowerPos()&&p.equals(bee.getSavedFlowerPos())&&bee.distanceToSqr(p.getX()+.5,p.getY()+.7,p.getZ()+.5)<1&&!be.nectarVisitors.contains(bee.getUUID())&&be.nectarVisitors.size()<16){if(be.pollinateOnce())be.nectarVisitors.add(bee.getUUID());break;}mode=Math.min(3,be.pollinations);}
            if(species==BotanicalSpecies.ECHO_BEAN&&now-be.lastPlayback>=100&&!l.getEntitiesOfClass(ServerPlayer.class,new AABB(p).inflate(2),player->player.getDeltaMovement().horizontalDistanceSqr()>.0005).isEmpty()){be.lastPlayback=now;l.playSound(null,p,net.minecraft.sounds.SoundEvents.NOTE_BLOCK_HARP.get(),net.minecraft.sounds.SoundSource.BLOCKS,.4F,(float)Math.pow(2,(be.pattern-12)/12.0));}
            if(species==BotanicalSpecies.DANCE_BELL){var owner=be.owner==null?null:l.getServer().getPlayerList().getPlayer(be.owner);boolean near=owner!=null&&owner.level()==l&&owner.distanceToSqr(p.getX()+.5,p.getY()+.5,p.getZ()+.5)<(be.dance>0?144:64);be.danceBefore=be.dance;be.danceUpdated=now;be.dance=Math.max(0,Math.min(60,be.dance+(near?20:-20)));mode=be.dance>0?2:1;}
            if(species==BotanicalSpecies.STAR_REST&&valid)be.manageSprites(l,day);
        }else if(species==BotanicalSpecies.DANCE_BELL)be.dance=Math.max(0,be.dance-20);
        be.lastClock=clock;be.wasRain=l.isRaining();if(species==BotanicalSpecies.LADDER_VINE||species==BotanicalSpecies.PAPER_TREE)be.updateSegments(l);
        var current=l.getBlockState(p);if(current.getBlock()==state.getBlock()&&(current.getValue(BotanicalBlock.AGE)!=age||current.getValue(BotanicalBlock.MODE)!=mode))l.setBlock(p,current.setValue(BotanicalBlock.AGE,age).setValue(BotanicalBlock.MODE,mode),3);be.changed();
    }
    private void updateSegments(ServerLevel l){var species=species();if(species!=BotanicalSpecies.LADDER_VINE&&species!=BotanicalSpecies.PAPER_TREE)return;int target=species==BotanicalSpecies.LADDER_VINE?Math.min(4,Math.max(1,(growth+20)/7200)):growth>=species.firstTicks?3:growth>=species.firstTicks/2?2:1;Block segment=species==BotanicalSpecies.LADDER_VINE?BotanicalContent.VINE_SEGMENT:BotanicalContent.PAPER_SEGMENT;
        if(!space(l,target))return;for(int y=1;y<4;y++){BlockPos at=worldPosition.above(y);if(!l.hasChunkAt(at))return;var state=l.getBlockState(at);if(y<target&&state.isAir()&&allowed(l,at))l.setBlock(at,segment.defaultBlockState().setValue(BotanicalSegment.HEIGHT,y),3);else if(y>=target&&state.is(segment)&&state.getValue(BotanicalSegment.HEIGHT)==y)l.removeBlock(at,false);}ladderHeight=target;}
    private boolean allowed(ServerLevel l,BlockPos p){
        if(!l.hasChunkAt(p))return false;
        // A planted root keeps its owner's permissions when the owner is offline.
        // hazard() also includes purchased land, so it cannot authorize growth.
        if(owner==null)return !com.tnc.tnc.home.TownProtection.town(l,p)&&com.tnc.tnc.home.HousingService.plotAt(l,p)==null&&!com.tnc.tnc.home.HousingService.isOwnedPosition(l,p);
        var player=l.getServer().getPlayerList().getPlayer(owner);
        if(player==null||player.serverLevel()!=l)player=net.minecraftforge.common.util.FakePlayerFactory.get(l,new com.mojang.authlib.GameProfile(owner,"botanical_grow"));
        return !com.tnc.tnc.home.TownProtection.denied(player,p)&&l.mayInteract(player,p);
    }
    private void manageSprites(ServerLevel l,long night){
        if(l.isDay())return;for(int dx=-1;dx<=1;dx++)for(int dz=-1;dz<=1;dz++)if(!l.hasChunkAt(worldPosition.offset(dx*4,0,dz*4)))return;
        // Deduplicate loaded descendants by immutable mother UUID and slot, even
        // when a crash left entity serialization newer than the flower's list.
        var nearby=l.getEntitiesOfClass(BotanicalSprite.class,new AABB(worldPosition).inflate(7),sprite->motherId.equals(sprite.mother));Map<Integer,BotanicalSprite> slots=new HashMap<>();for(var sprite:nearby){if(sprite.night!=night){sprite.discard();continue;}var previous=slots.putIfAbsent(sprite.slot,sprite);if(previous!=null)sprite.discard();}
        if(lastNight==night)return;
        var chunkBox=new AABB((worldPosition.getX()>>4)*16,l.getMinBuildHeight(),(worldPosition.getZ()>>4)*16,(worldPosition.getX()>>4)*16+16,l.getMaxBuildHeight(),(worldPosition.getZ()>>4)*16+16);
        int count=l.getEntitiesOfClass(BotanicalSprite.class,chunkBox).size();if(count+3-slots.size()>24)return;
        spriteIds.clear();for(int i=0;i<3;i++){var sprite=slots.get(i);if(sprite==null){sprite=BotanicalContent.SPRITE.create(l);if(sprite==null)continue;sprite.bind(motherId,worldPosition,i,night);sprite.moveTo(worldPosition.getX()+.5,worldPosition.getY()+.8,worldPosition.getZ()+.5,0,0);l.addFreshEntity(sprite);}spriteIds.add(sprite.getUUID());}returns=0;lastNight=night;changed();
    }
    public void spriteReturned(int slot,long night){if(night!=lastNight||slot<0||slot>2)return;returns|=1<<slot;if(Integer.bitCount(returns)>=2)ready=true;changed();}
    public void removeSprites(ServerLevel l){for(var sprite:l.getEntitiesOfClass(BotanicalSprite.class,new AABB(worldPosition).inflate(8),e->motherId.equals(e.mother)))sprite.discard();for(int y=1;y<4;y++){var at=worldPosition.above(y);if(l.hasChunkAt(at)&&l.getBlockState(at).getBlock() instanceof BotanicalSegment&&l.getBlockState(at).getValue(BotanicalSegment.HEIGHT)==y)l.removeBlock(at,false);}}
    @Override public void onLoad(){super.onLoad();if(!(level instanceof ServerLevel l)||!(getBlockState().getBlock() instanceof BotanicalBlock))return;boolean valid=reason(l).isEmpty();if(knownClock&&wasGrowing&&valid&&growth<species().firstTicks){long elapsed=Math.max(0,l.getGameTime()-lastActiveTick);growth+=(int)Math.min(species().firstTicks-growth,elapsed/2);}lastActiveTick=l.getGameTime();knownClock=true;wasGrowing=valid;lastClock=Math.floorMod(l.getDayTime(),24000);wasRain=l.isRaining();changed();}
    @Override protected void saveAdditional(CompoundTag t){super.saveAdditional(t);if(owner!=null)t.putUUID("Owner",owner);t.putUUID("Mother",motherId);t.putInt("Growth",growth);t.putInt("Harvests",harvests);t.putInt("Pattern",pattern);t.putInt("Notes",notes);t.putInt("Pollinations",pollinations);t.putInt("Dance",dance);t.putInt("DanceBefore",danceBefore);t.putLong("DanceUpdated",danceUpdated);t.putBoolean("Wild",wild);t.putBoolean("WildHarvested",wildHarvested);t.putBoolean("Book",bookStudied);t.putBoolean("Ready",ready);t.putBoolean("WasRain",wasRain);t.putBoolean("WasGrowing",wasGrowing);if(knownClock)t.putLong("LastActiveTick",lastActiveTick);t.putLong("WetUntil",wetUntil);t.putLong("LastDawn",lastDawn);t.putLong("LastSleep",lastSleep);t.putLong("LastPollination",lastPollination);t.putLong("LastNight",lastNight);t.putInt("Returns",returns);t.putLong("LastClock",lastClock);int visitorIndex=0;for(var id:nectarVisitors)if(visitorIndex<16)t.putUUID("NectarVisitor"+(visitorIndex++),id);for(int i=0;i<spriteIds.size();i++)t.putUUID("Sprite"+i,spriteIds.get(i));}
    @Override public void load(CompoundTag t){super.load(t);owner=t.hasUUID("Owner")?t.getUUID("Owner"):null;motherId=t.hasUUID("Mother")?t.getUUID("Mother"):UUID.randomUUID();growth=Math.max(0,Math.min(species().firstTicks,t.getInt("Growth")));harvests=Math.max(0,t.getInt("Harvests"));pattern=Math.max(0,Math.min(24,t.getInt("Pattern")));notes=Math.max(0,Math.min(3,t.getInt("Notes")));pollinations=Math.max(0,Math.min(3,t.getInt("Pollinations")));dance=Math.max(0,Math.min(60,t.getInt("Dance")));danceBefore=Math.max(0,Math.min(60,t.getInt("DanceBefore")));danceUpdated=t.getLong("DanceUpdated");wild=t.getBoolean("Wild");wildHarvested=t.getBoolean("WildHarvested");bookStudied=t.getBoolean("Book");ready=t.getBoolean("Ready");wasRain=t.getBoolean("WasRain");wasGrowing=t.getBoolean("WasGrowing");knownClock=t.contains("LastActiveTick");lastActiveTick=t.getLong("LastActiveTick");wetUntil=t.getLong("WetUntil");lastDawn=t.contains("LastDawn")?t.getLong("LastDawn"):-1;lastSleep=t.contains("LastSleep")?t.getLong("LastSleep"):-1;lastPollination=t.contains("LastPollination")?t.getLong("LastPollination"):-100;lastNight=t.contains("LastNight")?t.getLong("LastNight"):-1;returns=t.getInt("Returns")&7;lastClock=t.contains("LastClock")?t.getLong("LastClock"):-1;nectarVisitors.clear();for(int i=0;i<16;i++)if(t.hasUUID("NectarVisitor"+i))nectarVisitors.add(t.getUUID("NectarVisitor"+i));spriteIds.clear();for(int i=0;i<3;i++)if(t.hasUUID("Sprite"+i))spriteIds.add(t.getUUID("Sprite"+i));}
    @Override public CompoundTag getUpdateTag(){return saveWithoutMetadata();}
    @Override public ClientboundBlockEntityDataPacket getUpdatePacket(){return ClientboundBlockEntityDataPacket.create(this);}
}
