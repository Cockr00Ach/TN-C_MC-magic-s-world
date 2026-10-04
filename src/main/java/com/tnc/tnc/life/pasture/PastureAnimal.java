package com.tnc.tnc.life.pasture;

import com.tnc.tnc.home.HousingService;
import com.tnc.tnc.home.TownProtection;
import net.minecraft.core.*;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.*;
import net.minecraft.server.level.*;
import net.minecraft.sounds.*;
import net.minecraft.tags.*;
import net.minecraft.world.*;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.*;
import net.minecraft.world.entity.ai.goal.*;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.BlockPathTypes;
import net.minecraft.world.phys.*;
import javax.annotation.Nullable;
import java.util.*;

/** Independent registered species, conserved inventories and concrete work routines. */
public class PastureAnimal extends Animal implements PlayerRideableJumping {
    private static final EntityDataAccessor<Boolean> READY=SynchedEntityData.defineId(PastureAnimal.class,EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Integer> RESOURCE=SynchedEntityData.defineId(PastureAnimal.class,EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> WORKING=SynchedEntityData.defineId(PastureAnimal.class,EntityDataSerializers.BOOLEAN);
    private final PastureSpecies species;
    @Nullable private UUID owner,candidate;
    private int careCount,foodCredits,progress,resource,trips,endurance=5,wax;
    private long lastCare=Long.MIN_VALUE,lastActive,nextBreed,nextMana,dawnEpoch=Long.MIN_VALUE,lastWork,lastWarning,calmUntil;
    private boolean following,saddled,returning,pendingJump;
    @Nullable private BlockPos home,routeA,routeB,lightPos;
    private final NonNullList<ItemStack> cargo=NonNullList.withSize(4,ItemStack.EMPTY);
    private final List<BlockPos> jobs=new ArrayList<>();
    private final Map<Long,Long> pollinated=new LinkedHashMap<>();
    private final List<String> foundSeeds=new ArrayList<>();
    private long nextSeedExchange;

    public PastureAnimal(EntityType<? extends PastureAnimal> type,Level level,PastureSpecies species){
        super(type,level);this.species=species;lastActive=level.getGameTime();setPathfindingMalus(BlockPathTypes.WATER,0);
        if(species.habitat()==PastureSpecies.Habitat.WATER)navigation=new net.minecraft.world.entity.ai.navigation.AmphibiousPathNavigation(this,level);
        if(Set.of("post_heron","mirrorwing_moth","papersail_ray","dewbound_whale").contains(species.id())){navigation=new net.minecraft.world.entity.ai.navigation.FlyingPathNavigation(this,level);moveControl=new PastureFlight.Control(this);setNoGravity(true);}
        getNavigation().setCanFloat(true);if(navigation instanceof net.minecraft.world.entity.ai.navigation.FlyingPathNavigation air){air.setCanOpenDoors(false);air.setCanPassDoors(true);}
        goalSelector.addGoal(0,new Goal(){ {setFlags(EnumSet.of(Flag.MOVE));} @Override public boolean canUse(){return level().getGameTime()<calmUntil;} @Override public void start(){getNavigation().stop();} @Override public void tick(){getNavigation().stop();} });
        goalSelector.addGoal(0,new FloatGoal(this));goalSelector.addGoal(1,new PanicGoal(this,1.15));goalSelector.addGoal(2,new BreedGoal(this,1));
        goalSelector.addGoal(3,new TemptGoal(this,1.05,Ingredient.of(species.food()),false));goalSelector.addGoal(4,new FollowParentGoal(this,1));
        goalSelector.addGoal(7,Set.of("post_heron","mirrorwing_moth","papersail_ray","dewbound_whale").contains(species.id())?new PastureFlight.Roam(this):new WaterAvoidingRandomStrollGoal(this,.8));goalSelector.addGoal(8,new LookAtPlayerGoal(this,Player.class,7));goalSelector.addGoal(9,new RandomLookAroundGoal(this));
    }
    @Override protected void registerGoals(){}
    @Override public boolean canBreatheUnderwater(){return species!=null&&species.habitat()==PastureSpecies.Habitat.WATER;}
    @Override public boolean causeFallDamage(float distance,float multiplier,DamageSource source){return Set.of("post_heron","mirrorwing_moth","papersail_ray","dewbound_whale").contains(speciesId())?false:super.causeFallDamage(distance,multiplier,source);}
    public static AttributeSupplier.Builder attributes(){return Animal.createMobAttributes().add(Attributes.MAX_HEALTH,16).add(Attributes.MOVEMENT_SPEED,.23).add(Attributes.FLYING_SPEED,.18);}
    @Override protected void defineSynchedData(){super.defineSynchedData();entityData.define(READY,false);entityData.define(RESOURCE,0);entityData.define(WORKING,false);}
    public String speciesId(){return species.id();}
    public PastureSpecies species(){return species;}
    public boolean productionReady(){return entityData.get(READY);}
    public float resourceLevel(){return Math.min(1f,entityData.get(RESOURCE)/(float)capacity());}
    public float animationPhase(float partialTick){return(tickCount+partialTick)*.09f;}
    public boolean isWorking(){return entityData.get(WORKING);}
    @Nullable public UUID owner(){return owner;}
    public BlockPos flightAnchor(){return home==null?blockPosition():home;}
    public boolean flightBusy(){return following||returning||level().getGameTime()<calmUntil||isInLove()||isWorking()||!getNavigation().isDone();}
    public int storedResource(){return resource;}
    public void calmFor(int ticks){calmUntil=Math.max(calmUntil,level().getGameTime()+Math.max(0,Math.min(200,ticks)));getNavigation().stop();getMoveControl().setWantedPosition(getX(),getY(),getZ(),0);setDeltaMovement(Vec3.ZERO);swing(InteractionHand.MAIN_HAND);}
    public List<ItemStack> carriedItems(){return cargo.stream().map(ItemStack::copy).toList();}
    private int capacity(){return switch(speciesId()){case"dewbound_whale"->96;case"wirecall_lizard"->100;case"pillowlight_marten"->60;default->species.amount();};}
    private int interval(){return species.harvestDays()*24000;}
    private boolean hasHarvest(){return !(species.role()==PastureSpecies.Role.MEAT&&!speciesId().equals("apiary_toad")&&!speciesId().equals("froststride_fowl"))&&!speciesId().equals("dewbound_whale");}
    private void sync(){entityData.set(RESOURCE,resource);entityData.set(READY,!isBaby()&&hasHarvest()&&(speciesId().equals("wirecall_lizard")?resource>=50:speciesId().equals("pillowlight_marten")?wax>0:resource>0));}
    public boolean mayCareFor(ServerPlayer p){return !TownProtection.denied(p,blockPosition())&&(owner==null||owner.equals(p.getUUID())||p.isCreative());}
    public static boolean mayOperate(ServerLevel level,BlockPos pos,UUID owner){
        if(owner==null||!level.hasChunkAt(pos))return false;var plot=HousingService.ownedAt(level,pos);
        if(plot!=null){var t=HousingService.home(level.getServer(),plot.id());return t.hasUUID("Owner")&&owner.equals(t.getUUID("Owner"))&&!t.getBoolean("Preparing");}
        return HousingService.plotAt(level,pos)==null&&!TownProtection.town(level,pos);
    }
    @Override public boolean isFood(ItemStack stack){return stack.is(species.food())||species.habitat()==PastureSpecies.Habitat.WARM&&stack.is(PastureRegistry.item("warm_feed"));}
    private boolean care(ServerPlayer p,ItemStack food){
        if(!mayCareFor(p))return false;long now=level().getGameTime();
        if(owner==null){if(candidate!=null&&!candidate.equals(p.getUUID()))return false;if(careCount>0&&now-lastCare<1200){message(p,"有效认养照料间隔至少60秒。");return false;}candidate=p.getUUID();careCount++;lastCare=now;if(careCount>=3){owner=candidate;home=blockPosition();setPersistenceRequired();message(p,"它记住了你！用牧养册查看栖居与取产。");}else message(p,"有效照料 "+careCount+"/3；喜欢 "+new ItemStack(species.food()).getHoverName().getString());}
        boolean warm=food.is(PastureRegistry.item("warm_feed"));if(!p.isCreative())food.shrink(1);foodCredits=Math.min(16,foodCredits+(warm?2:1));heal(warm?2:1);swing(InteractionHand.MAIN_HAND);
        if(owner!=null&&!isBaby()&&canFallInLove())setInLove(p);return true;
    }
    @Override public InteractionResult mobInteract(Player player,InteractionHand hand){
        if(level().isClientSide)return InteractionResult.SUCCESS;if(!(player instanceof ServerPlayer p))return InteractionResult.PASS;ItemStack held=p.getItemInHand(hand);
        if(!mayCareFor(p)){message(p,"这只异兽已有主人，或当前地块禁止照料。");return InteractionResult.FAIL;}
        if(held.getItem() instanceof ManaBottleItem bottle)return bottle.interactAnimal(p,held,this,hand);
        if(held.getItem() instanceof PastureBookItem book){book.show(p,held,this,hand);return InteractionResult.SUCCESS;}
        if(held.getItem() instanceof PastureStaffItem){held.getOrCreateTag().putUUID("Animal",getUUID());held.getOrCreateTag().putString("Dimension",level().dimension().location().toString());message(p,"已选 "+species.name()+"；右键自己的托盘、栖居标记或工作地块。");return InteractionResult.SUCCESS;}
        if(held.getItem() instanceof PastureCageItem cage)return cage.capture(p,held,this);
        if(speciesId().equals("pageforage_raccoon")&&held.is(PastureRegistry.item("forage_paper_hide"))&&owner!=null){
            if(foundSeeds.isEmpty()){message(p,"先让它捡起你实际发现的种子，它只记得见过的品种。");return InteractionResult.CONSUME;}
            if(level().getGameTime()<nextSeedExchange){message(p,"种源交換每两游戏日一次。");return InteractionResult.CONSUME;}
            var id=net.minecraft.resources.ResourceLocation.tryParse(foundSeeds.get(random.nextInt(foundSeeds.size())));var seed=net.minecraftforge.registries.ForgeRegistries.ITEMS.getValue(id);if(seed==null||seed==Items.AIR)return InteractionResult.FAIL;
            nextSeedExchange=level().getGameTime()+48000;if(!p.isCreative())held.shrink(1);ItemStack result=new ItemStack(seed);result.getOrCreateTag().putBoolean("PastureSeedGift",true);p.getInventory().add(result);if(!result.isEmpty())spawnAtLocation(result);swing(InteractionHand.MAIN_HAND);message(p,"它送来已记住品种的一份种源。");return InteractionResult.SUCCESS;
        }
        if(isFood(held))return care(p,held)?InteractionResult.SUCCESS:InteractionResult.CONSUME;
        if(speciesId().equals("springhoof_strider")&&held.is(PastureRegistry.item("beast_saddle"))&&!isBaby()&&owner!=null){if(!saddled){if(!p.isCreative())held.shrink(1);saddled=true;}message(p,"兽鞍已系好，空手右键骑乘。");return InteractionResult.SUCCESS;}
        if(held.isEmpty()&&p.isShiftKeyDown()){if(owner==null){message(p,"先用它喜欢的食物照料三次。");return InteractionResult.CONSUME;}if(!cargo.stream().allMatch(ItemStack::isEmpty)){for(int i=0;i<4;i++){p.getInventory().add(cargo.get(i));if(cargo.get(i).isEmpty())cargo.set(i,ItemStack.EMPTY);}message(p,"实际货物已取；背包装不下的留在动物身上。");}else{following=!following;message(p,following?"它会跟着你。":"留在窝点附近。");}return InteractionResult.SUCCESS;}
        if(held.isEmpty()&&saddled&&speciesId().equals("springhoof_strider")&&!isBaby()&&owner!=null){p.startRiding(this);return InteractionResult.SUCCESS;}
        if((held.isEmpty()||held.is(Items.SHEARS)||held.is(PastureRegistry.item("pasture_scraper"))||held.is(Items.GLASS_BOTTLE)||held.is(PastureRegistry.item("empty_breath_jar")))&&owner!=null){if(harvest(p,held,hand))return InteractionResult.SUCCESS;message(p,status());return InteractionResult.CONSUME;}
        message(p,status());return InteractionResult.SUCCESS;
    }
    public static void message(ServerPlayer p,String text){p.displayClientMessage(Component.literal(text),true);}
    public String status(){String problem=habitatProblem();return species.name()+" · "+(owner==null?"野生／照料"+careCount+"/3":isBaby()?"幼体剩余 "+(-getAge()/20)+"秒":"已认养")+" · "+(problem.isEmpty()?"栖居合适":problem)+" · "+(resource>0?"储量 "+resource+"/"+capacity():"下次产物 "+Math.max(0,(interval()-progress)/20)+"秒")+" · 饲料 "+foodCredits;}
    public String habitatProblem(){
        BlockPos center=home==null?blockPosition():home;if(!level().hasChunkAt(center.offset(-4,-2,-4))||!level().hasChunkAt(center.offset(4,2,4)))return "窝点邻区尚未加载";
        switch(species.habitat()){
            case WATER->{int water=0;for(BlockPos pos:BlockPos.betweenClosed(center.offset(-1,-1,-1),center.offset(1,0,1)))if(level().getFluidState(pos).is(FluidTags.WATER))water++;if(water<9)return "需要3×3水池";}
            case WARM->{boolean warm=false;for(BlockPos pos:BlockPos.betweenClosed(center.offset(-4,-2,-4),center.offset(4,2,4))){BlockState s=level().getBlockState(pos);if((s.is(Blocks.CAMPFIRE)||s.is(Blocks.SOUL_CAMPFIRE))&&s.getValue(CampfireBlock.LIT)){warm=true;break;}}if(!warm)return "需要点燃营火的暖窝";}
            case PERCH->{boolean perch=false;for(BlockPos pos:BlockPos.betweenClosed(center.offset(-3,-1,-3),center.offset(3,2,3)))if(level().getBlockState(pos).is(BlockTags.FENCES)||level().getBlockState(pos).is(PastureRegistry.block("charging_perch"))){perch=true;break;}if(!perch)return "需要栅栏栖架";}
            case OPEN->{if(!level().canSeeSky(center.above()))return "需要露天净空";for(int x=-1;x<=1;x++)for(int z=-1;z<=1;z++)if(!level().getBlockState(center.offset(x,2,z)).isAir())return "需要3×3净空";}
            case LAND->{if(!level().getBlockState(center.below()).isSolid())return "需要可站立地面";if(owner!=null&&level().canSeeSky(center.above(2)))return "需要两格高遮棚";}
        }return "";
    }
    public void setHome(ServerPlayer p,BlockPos pos){if(owner!=null&&mayCareFor(p)&&mayOperate(p.serverLevel(),pos,owner)){home=pos.immutable();message(p,"窝点已设；"+status());}}
    public boolean setJob(ServerPlayer p,BlockPos pos){
        if(owner==null||!mayCareFor(p)||!mayOperate(p.serverLevel(),pos,owner))return false;
        if(speciesId().equals("watch_mantis")){if(!(level().getBlockEntity(pos) instanceof PastureFacilityEntity marker)||marker.kind()!=PastureFacilityBlock.Kind.MARKER||!owner.equals(marker.owner())){message(p,"巡逻两端用自己的栖居标记。");return false;}if(routeA==null){routeA=pos.immutable();message(p,"第一处巡逻标记已记下。");return true;}if(routeA.distSqr(pos)>1024||routeA.equals(pos))return false;routeB=pos.immutable();message(p,"夜间沿两个标记实际巡逻，有敌情才预警。");return true;}
        if(speciesId().equals("satchelback_runner")||speciesId().equals("post_heron")){if(!(level().getBlockEntity(pos) instanceof PastureFacilityEntity tray)||tray.kind()!=PastureFacilityBlock.Kind.TRAY||!owner.equals(tray.owner())){message(p,"两端都要是自己的牧场托盘。");return false;}if(routeA==null){routeA=pos.immutable();message(p,"起点已设，再选择终点托盘。");return true;}int distance=speciesId().equals("post_heron")?128:32;if(routeA.distSqr(pos)>distance*distance||routeA.equals(pos)){message(p,"终点需在 "+distance+" 格内且不同于起点。");return false;}routeB=pos.immutable();returning=false;message(p,"路线已设，走到托盘才交接，每四趟吃一份饲料。");return true;}
        if(speciesId().equals("bowlhorn_rhino")){if(foodCredits<1){message(p,"先喂一份甜菜。");return false;}jobs.clear();for(int x=-1;x<=1;x++)for(int z=-1;z<=1;z++){BlockPos j=pos.offset(x,0,z);if(mayOperate(p.serverLevel(),j,owner))jobs.add(j.immutable());}foodCredits--;message(p,"已批准3×3，逐格到达后开荒。");return true;}
        if(speciesId().equals("patternbuild_beaver")){if(jobs.size()>=8)return false;if(!level().getBlockState(pos).is(BlockTags.FENCES)){message(p,"右键已有栅栏记下允许修补的位置。");return false;}if(!jobs.contains(pos))jobs.add(pos.immutable());message(p,"记住栅栏位置 "+jobs.size()+"/8；托盘放木栅栏，缺口才修。");return true;}
        home=pos.immutable();message(p,"工作范围与窝点已设。");return true;
    }
    public int takeMana(ServerPlayer p,int requested){if(!speciesId().equals("dewbound_whale")||isBaby()||!mayCareFor(p)||owner==null||requested<=0||level().getGameTime()<nextMana)return 0;int actual=Math.min(24,Math.min(resource,requested));if(actual>0){resource-=actual;nextMana=level().getGameTime()+600;sync();swing(InteractionHand.MAIN_HAND);}return actual;}
    public int receiveMana(ServerPlayer p,int requested){if(!speciesId().equals("pillowlight_marten")||isBaby()||!mayCareFor(p)||owner==null||requested<=0)return 0;int actual=Math.min(requested,60-resource);resource+=actual;sync();if(actual>0)swing(InteractionHand.MAIN_HAND);return actual;}
    public int receiveStaticCharge(int amount){if(!speciesId().equals("wirecall_lizard")||amount<=0||resource+amount>100)return 0;resource+=amount;sync();return amount;}
    public boolean harvest(ServerPlayer p,ItemStack held,InteractionHand hand){
        if(owner==null||!mayCareFor(p)||isBaby()||!productionReady())return false;String id=species.product();
        if(speciesId().equals("wirecall_lizard")){if(resource<50)return false;resource-=50;id="storm_crystal";} else if(speciesId().equals("pillowlight_marten")){wax=0;progress=0;id="lamp_wax";}
        else{if(speciesId().equals("pattern_shell_snail")&&!held.is(PastureRegistry.item("pasture_scraper"))){message(p,"用木刮片取胶。");return false;}if((speciesId().equals("mistbelly_otter")||speciesId().equals("apiary_toad"))&&!held.is(Items.GLASS_BOTTLE)){message(p,"带空玻璃瓶来接液。");return false;}if(speciesId().equals("forgegill_tapir")&&!held.is(PastureRegistry.item("empty_breath_jar"))){message(p,"需要空暖息罐。");return false;}if(speciesId().equals("froststride_fowl"))id="froststride_egg";resource=0;progress=0;if((held.is(Items.GLASS_BOTTLE)||held.is(PastureRegistry.item("empty_breath_jar")))&&!p.isCreative())held.shrink(1);if(held.is(PastureRegistry.item("pasture_scraper")))held.hurtAndBreak(1,p,pl->pl.broadcastBreakEvent(hand));}
        ItemStack result=new ItemStack(PastureRegistry.item(id),speciesId().equals("wirecall_lizard")?1:species.amount());p.getInventory().add(result);if(!result.isEmpty())spawnAtLocation(result);swing(InteractionHand.MAIN_HAND);sync();((ServerLevel)level()).sendParticles(ParticleTypes.HAPPY_VILLAGER,getX(),getY()+.6,getZ(),6,.35,.2,.35,.02);return true;
    }
    @Override public boolean canMate(Animal other){return other instanceof PastureAnimal a&&a.getType()==getType()&&owner!=null&&owner.equals(a.owner)&&super.canMate(other);}
    @Override public boolean canFallInLove(){return !isBaby()&&owner!=null&&super.canFallInLove()&&level().getGameTime()>=nextBreed&&habitatProblem().isEmpty()&&breedingSpace();}
    private boolean breedingSpace(){int x=(blockPosition().getX()>>4)<<4,z=(blockPosition().getZ()>>4)<<4;AABB area=new AABB(x,level().getMinBuildHeight(),z,x+16,level().getMaxBuildHeight(),z+16);return level().getEntitiesOfClass(PastureAnimal.class,area,a->a.getType()==getType()).size()<8&&level().getEntitiesOfClass(Animal.class,area).size()<24;}
    @Override public PastureAnimal getBreedOffspring(ServerLevel l,AgeableMob partner){PastureAnimal child=PastureRegistry.TYPES.get(speciesId()).create(l);if(child!=null){child.setAge(-species.adultDays()*24000);if(partner instanceof PastureAnimal a&&owner!=null&&owner.equals(a.owner)){child.owner=owner;child.home=home;child.setPersistenceRequired();}}return child;}
    @Override public void finalizeSpawnChildFromBreeding(ServerLevel l,Animal partner,@Nullable AgeableMob child){
        if(Set.of("tideback_newt","froststride_fowl","pattern_shell_snail","mirrorwing_moth").contains(speciesId())){
            setAge(6000);partner.setAge(6000);resetLove();partner.resetLove();
            int amount=speciesId().equals("pattern_shell_snail")?2:1;
            for(int i=0;i<amount;i++){ItemStack egg=new ItemStack(PastureRegistry.item("fertile_pasture_egg"));egg.getOrCreateTag().putString("Species",speciesId());if(owner!=null)egg.getOrCreateTag().putUUID("EggOwner",owner);spawnAtLocation(egg);}
        }else super.finalizeSpawnChildFromBreeding(l,partner,child);
        nextBreed=l.getGameTime()+species.breedDays()*24000L;if(partner instanceof PastureAnimal a)a.nextBreed=nextBreed;if(child!=null)child.setAge(-species.adultDays()*24000);
    }
    @Override public void aiStep(){super.aiStep();if(!(level() instanceof ServerLevel server))return;lastActive=server.getGameTime();if(tickCount%20!=0||isBaby())return;entityData.set(WORKING,false);if(owner!=null){if(server.getGameTime()>=calmUntil){if(following){ServerPlayer p=server.getServer().getPlayerList().getPlayer(owner);if(p!=null&&p.level()==server&&distanceToSqr(p)>9&&distanceToSqr(p)<1024)getNavigation().moveTo(p,1.1);}else if(home!=null&&routeB==null&&jobs.isEmpty()&&distanceToSqr(Vec3.atCenterOf(home))>64)getNavigation().moveTo(home.getX()+.5,home.getY(),home.getZ()+.5,.8);}if(habitatProblem().isEmpty()){if(foodCredits==0)seekFood(server);tickProduction(server);if(server.getGameTime()>=calmUntil)tickWork(server);}}effects(server);sync();}
    void tickProduction(ServerLevel s){
        if(speciesId().equals("dewbound_whale")){long epoch=s.getGameTime()/24000;if(epoch!=dawnEpoch&&s.getDayTime()%24000<2400&&s.canSeeSky(blockPosition())&&(s.isRaining()||nearWater())&&foodCredits>0&&((progress=Math.min(1200,progress+20))>=1200)){resource=Math.min(96,resource+48);foodCredits--;dawnEpoch=epoch;progress=0;s.sendParticles(ParticleTypes.GLOW,getX(),getY()+.8,getZ(),12,.5,.2,.5,.01);}return;}
        if(speciesId().equals("wirecall_lizard")){if(s.isThundering()&&s.isRainingAt(blockPosition()))resource=Math.min(100,resource+5);if(resource<=20&&foodCredits>0)for(BlockPos p:BlockPos.betweenClosed(blockPosition().offset(-3,-2,-3),blockPosition().offset(3,2,3)))if(s.hasChunkAt(p)&&s.getBlockEntity(p) instanceof PastureFacilityEntity rack&&rack.kind()==PastureFacilityBlock.Kind.CHARGING&&owner.equals(rack.owner())&&rack.chargeAnimal(this)){foodCredits--;break;}return;}
        if(speciesId().equals("pillowlight_marten")){long epoch=s.getGameTime()/24000;if(s.isNight()&&epoch!=dawnEpoch&&foodCredits>0){resource=Math.min(60,resource+12);foodCredits--;dawnEpoch=epoch;}progress=Math.min(interval(),progress+20);if(progress>=interval())wax=1;return;}
        if(hasHarvest()&&progress<interval())progress=Math.min(interval(),progress+20);if(hasHarvest()&&progress>=interval()&&resource==0){if(speciesId().equals("forgegill_tapir")){if(foodCredits<=0)return;foodCredits--;}if(speciesId().equals("mistbelly_otter")){boolean drank=false;for(BlockPos at:BlockPos.betweenClosed(blockPosition().offset(-5,-2,-5),blockPosition().offset(5,2,5)))if(s.hasChunkAt(at)&&s.getBlockEntity(at) instanceof PastureFacilityEntity trough&&trough.kind()==PastureFacilityBlock.Kind.TROUGH&&owner.equals(trough.owner())&&trough.takeWater(4)==4){drank=true;break;}if(!drank)return;}resource=species.amount();if(speciesId().equals("dusk_lantern_deer")){dropToTray(s,new ItemStack(PastureRegistry.item("lantern_antler")));resource=0;progress=0;}}
    }
    private boolean nearWater(){for(BlockPos p:BlockPos.betweenClosed(blockPosition().offset(-3,-1,-3),blockPosition().offset(3,0,3)))if(level().hasChunkAt(p)&&level().getFluidState(p).is(FluidTags.WATER))return true;return false;}
    private void seekFood(ServerLevel s){for(BlockPos p:BlockPos.betweenClosed(blockPosition().offset(-5,-2,-5),blockPosition().offset(5,2,5)))if(s.hasChunkAt(p)&&s.getBlockEntity(p) instanceof PastureFacilityEntity trough&&trough.kind()==PastureFacilityBlock.Kind.TROUGH&&owner.equals(trough.owner())){if(distanceToSqr(Vec3.atCenterOf(p))<=4){if(trough.takeFood(species.food())){foodCredits++;swing(InteractionHand.MAIN_HAND);}return;}getNavigation().moveTo(p.getX()+.5,p.getY(),p.getZ()+.5,1);return;}}
    private void dropToTray(ServerLevel s,ItemStack item){for(BlockPos p:BlockPos.betweenClosed(blockPosition().offset(-3,-1,-3),blockPosition().offset(3,1,3)))if(s.hasChunkAt(p)&&s.getBlockEntity(p) instanceof PastureFacilityEntity tray&&tray.kind()==PastureFacilityBlock.Kind.DEW&&owner.equals(tray.owner())){item=tray.insert(item);if(item.isEmpty())return;}spawnAtLocation(item);}
    private void effects(ServerLevel s){switch(speciesId()){
        case"forgegill_tapir","emberback_hog"->{if(habitatProblem().isEmpty()){s.sendParticles(ParticleTypes.SMALL_FLAME,getX(),getY()+.6,getZ(),1,.3,.15,.3,0);for(Player p:s.getEntitiesOfClass(Player.class,getBoundingBox().inflate(2)))if(owner!=null&&owner.equals(p.getUUID()))p.setTicksFrozen(0);}}
        case"froststride_fowl"->{if(s.isNight())s.sendParticles(ParticleTypes.SNOWFLAKE,getX(),getY()+.15,getZ(),2,.2,.03,.2,.01);}
        case"papersail_ray","dewbound_whale"->{if(!isInWater()&&tickCount%40==0&&onGround())setDeltaMovement(getDeltaMovement().add(0,.14,0));}
        case"watch_mantis"->{if(owner!=null&&s.isNight()&&s.getGameTime()-lastWarning>100&&!s.getEntitiesOfClass(Monster.class,getBoundingBox().inflate(12),LivingEntity::isAlive).isEmpty()){lastWarning=s.getGameTime();s.sendParticles(ParticleTypes.FLAME,getX(),getY()+.7,getZ(),8,.3,.3,.3,.01);ServerPlayer p=s.getServer().getPlayerList().getPlayer(owner);if(p!=null&&p.level()==s&&distanceToSqr(p)<4096)message(p,"巡灯螳螂预警：12格内有敌意生物。");}}
        case"mirrorwing_moth"->{if(owner!=null&&foodCredits>0&&tickCount%100==0)pollinate(s);}
        case"pillowlight_marten"->{if(resource>0&&owner!=null&&mayOperate(s,blockPosition().above(),owner)){BlockPos p=blockPosition().above();if(s.getBlockState(p).isAir()||s.getBlockState(p).is(PastureRegistry.block("pasture_glow"))){if(lightPos!=null&&!lightPos.equals(p)&&s.getBlockEntity(lightPos) instanceof PastureFacilityEntity old&&getUUID().equals(old.lightAnimal()))s.removeBlock(lightPos,false);s.setBlock(p,PastureRegistry.block("pasture_glow").defaultBlockState(),3);if(s.hasChunkAt(p)&&s.getBlockEntity(p) instanceof PastureFacilityEntity light)light.setLight(getUUID(),s.getGameTime()+30);lightPos=p;resource--;}}}
        case"pattern_shell_snail"->{if(!nearWater())getNavigation().stop();}
        case"apiary_toad"->{if(s.isNight())s.playSound(null,blockPosition(),SoundEvents.FROG_AMBIENT,SoundSource.NEUTRAL,.15f,.8f);}
        case"stonebarrow_boar"->{if(s.isRaining()&&owner!=null&&tickCount%1200==0&&random.nextInt(3)==0)spawnAtLocation(Items.FLINT);}
        default->{}
    }}
    private void pollinate(ServerLevel s){
        long epoch=s.getGameTime()/24000;
        for(BlockPos p:BlockPos.betweenClosed(blockPosition().offset(-3,-1,-3),blockPosition().offset(3,1,3))){
            if(!mayOperate(s,p,owner)||Objects.equals(pollinated.get(p.asLong()),epoch))continue;
            BlockState state=s.getBlockState(p);boolean helped=false;
            if(s.getBlockEntity(p) instanceof com.tnc.tnc.life.botanical.BotanicalPlantEntity plant&&owner.equals(plant.owner)&&plant.reason(s).isEmpty()&&plant.growth<plant.species().firstTicks){plant.growth=Math.min(plant.species().firstTicks,plant.growth+1200);plant.changed();helped=true;}
            else if(state.getBlock() instanceof CropBlock crop&&!crop.isMaxAge(state)){s.setBlock(p,crop.getStateForAge(crop.getAge(state)+1),3);helped=true;}
            if(helped){pollinated.put(p.asLong(),epoch);while(pollinated.size()>64)pollinated.remove(pollinated.keySet().iterator().next());foodCredits--;s.sendParticles(ParticleTypes.HAPPY_VILLAGER,p.getX()+.5,p.getY()+.4,p.getZ()+.5,5,.3,.2,.3,.01);swing(InteractionHand.MAIN_HAND);return;}
        }
    }
    void tickWork(ServerLevel s){switch(speciesId()){case"satchelback_runner","post_heron"->transport(s);case"pageforage_raccoon"->forage(s);case"bowlhorn_rhino"->till(s);case"patternbuild_beaver"->repair(s);case"watch_mantis"->{if(s.isNight()&&routeA!=null&&routeB!=null&&foodCredits>0){if(approach(s,returning?routeA:routeB)){returning=!returning;if(++trips%4==0)foodCredits--;}}}default->{}}}
    private boolean approach(ServerLevel s,BlockPos p){if(!s.hasChunkAt(p)||!mayOperate(s,p,owner))return false;entityData.set(WORKING,true);if(distanceToSqr(Vec3.atCenterOf(p))>4){getNavigation().moveTo(p.getX()+.5,p.getY()+1,p.getZ()+.5,1);return false;}getNavigation().stop();return true;}
    private void transport(ServerLevel s){if(routeA==null||routeB==null||foodCredits<=0)return;boolean empty=cargo.stream().allMatch(ItemStack::isEmpty);BlockPos target=empty?(returning?routeB:routeA):(returning?routeA:routeB);if(!approach(s,target)||!(s.getBlockEntity(target) instanceof PastureFacilityEntity tray)||tray.kind()!=PastureFacilityBlock.Kind.TRAY||!owner.equals(tray.owner()))return;if(empty){for(int i=0;i<4;i++){ItemStack picked=tray.extract(16);if(picked.isEmpty())break;cargo.set(i,picked);}if(cargo.stream().allMatch(ItemStack::isEmpty))return;}else{for(int i=0;i<4;i++)cargo.set(i,tray.insert(cargo.get(i)));if(!cargo.stream().allMatch(ItemStack::isEmpty))return;returning=!returning;if(++trips%4==0)foodCredits--;lastWork=s.getGameTime();}swing(InteractionHand.MAIN_HAND);}
    private void forage(ServerLevel s){if(foodCredits<=0)return;BlockPos center=home==null?blockPosition():home;for(ItemEntity item:s.getEntitiesOfClass(ItemEntity.class,new AABB(center).inflate(8),i->i.isAlive()&&!i.getItem().isEmpty())){var foundId=net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(item.getItem().getItem());String path=foundId.getPath();if(!(path.contains("seed")||item.getItem().is(Items.PAPER))||!mayOperate(s,item.blockPosition(),owner))continue;CompoundTag saved=new CompoundTag();item.saveWithoutId(saved);if(saved.hasUUID("Thrower")&&!owner.equals(saved.getUUID("Thrower")))continue;if(saved.hasUUID("Owner")&&!owner.equals(saved.getUUID("Owner")))continue;if(!approach(s,item.blockPosition()))return;for(int i=0;i<2;i++){ItemStack slot=cargo.get(i);if(!slot.isEmpty()&&!ItemStack.isSameItemSameTags(slot,item.getItem()))continue;int count=Math.min(16-slot.getCount(),item.getItem().getCount());if(count<=0)continue;if(slot.isEmpty())cargo.set(i,item.getItem().copyWithCount(count));else slot.grow(count);item.getItem().shrink(count);if(item.getItem().isEmpty())item.discard();if(path.contains("seed")&&foundSeeds.size()<64&&!foundSeeds.contains(foundId.toString()))foundSeeds.add(foundId.toString());trips+=count;if(trips>=16){trips-=16;foodCredits--;}swing(InteractionHand.MAIN_HAND);return;}}}
    private void till(ServerLevel s){if(jobs.isEmpty())return;BlockPos p=jobs.get(0);if(!mayOperate(s,p,owner)){jobs.remove(0);return;}if(!approach(s,p))return;BlockState state=s.getBlockState(p);if((state.is(Blocks.DIRT)||state.is(Blocks.GRASS_BLOCK)||state.is(Blocks.DIRT_PATH))&&s.getBlockState(p.above()).isAir()){s.setBlock(p,Blocks.FARMLAND.defaultBlockState(),3);s.playSound(null,p,SoundEvents.HOE_TILL,SoundSource.NEUTRAL,.7f,1);swing(InteractionHand.MAIN_HAND);}jobs.remove(0);lastWork=s.getGameTime();}
    private void repair(ServerLevel s){if(foodCredits<=0)return;for(BlockPos p:jobs){if(!s.hasChunkAt(p)||!s.getBlockState(p).isAir()||!mayOperate(s,p,owner))continue;if(!approach(s,p))return;for(BlockPos q:BlockPos.betweenClosed(p.offset(-5,-2,-5),p.offset(5,2,5)))if(s.hasChunkAt(q)&&s.getBlockEntity(q) instanceof PastureFacilityEntity tray&&tray.kind()==PastureFacilityBlock.Kind.TRAY&&owner.equals(tray.owner())){ItemStack material=tray.extractMatching(stack->stack.getItem() instanceof BlockItem b&&b.getBlock().defaultBlockState().is(BlockTags.FENCES),1);if(material.isEmpty())continue;s.setBlock(p,((BlockItem)material.getItem()).getBlock().defaultBlockState(),3);swing(InteractionHand.MAIN_HAND);if(++trips%4==0)foodCredits--;lastWork=s.getGameTime();return;}return;}}
    @Override public void travel(Vec3 input){if(isAlive()&&speciesId().equals("springhoof_strider")&&getControllingPassenger() instanceof Player p){setYRot(p.getYRot());yRotO=getYRot();setXRot(p.getXRot()*.5f);yBodyRot=getYRot();yHeadRot=getYRot();setSpeed((float)getAttributeValue(Attributes.MOVEMENT_SPEED)*1.4f);super.travel(new Vec3(p.xxa*.5,0,p.zza));if(pendingJump&&onGround()&&endurance>0){setDeltaMovement(getDeltaMovement().add(0,.8,0));endurance--;pendingJump=false;if(endurance==0)lastWork=level().getGameTime();}if(endurance==0&&level().getGameTime()-lastWork>=200&&foodCredits>0){foodCredits--;endurance=5;}}else super.travel(input);}
    @Override @Nullable public LivingEntity getControllingPassenger(){return saddled&&getFirstPassenger() instanceof LivingEntity l?l:null;}
    @Override public boolean canJump(){return saddled&&speciesId().equals("springhoof_strider")&&endurance>0;}
    @Override public void onPlayerJump(int power){if(power>0&&canJump())pendingJump=true;}
    @Override public void handleStartJump(int power){onPlayerJump(power);}
    @Override public void handleStopJump(){}
    @Override protected void dropCustomDeathLoot(DamageSource source,int looting,boolean hit){super.dropCustomDeathLoot(source,looting,hit);for(ItemStack stack:cargo)if(!stack.isEmpty())spawnAtLocation(stack.copy());if(saddled)spawnAtLocation(PastureRegistry.item("beast_saddle"));String meat=switch(speciesId()){case"apiary_toad"->"apiary_meat";case"stonebarrow_boar","emberback_hog","tideback_newt","froststride_fowl","starfelt_hare"->species.product();default->null;};if(meat!=null){spawnAtLocation(new ItemStack(PastureRegistry.item(meat),isBaby()?1:speciesId().equals("apiary_toad")?2:species.amount()));if(!isBaby()){String extra=switch(speciesId()){case"stonebarrow_boar"->"stone_bone";case"emberback_hog"->"warm_fat";case"tideback_newt"->"water_membrane";case"froststride_fowl"->"frost_bone";case"apiary_toad"->"sweet_fat";default->"soft_down";};spawnAtLocation(PastureRegistry.item(extra));}}}
    @Override public void addAdditionalSaveData(CompoundTag t){super.addAdditionalSaveData(t);if(owner!=null)t.putUUID("PastureOwner",owner);if(candidate!=null)t.putUUID("CareCandidate",candidate);t.putInt("CareCount",careCount);t.putLong("LastCare",lastCare);t.putInt("Food",foodCredits);t.putInt("Progress",progress);t.putInt("Resource",resource);t.putInt("Wax",wax);t.putLong("LastActive",lastActive);t.putLong("NextBreed",nextBreed);t.putLong("NextMana",nextMana);t.putLong("Dawn",dawnEpoch);t.putBoolean("Follow",following);t.putBoolean("Saddle",saddled);t.putBoolean("Returning",returning);t.putInt("Trips",trips);t.putInt("Endurance",endurance);t.putLong("LastWork",lastWork);if(home!=null)t.putLong("Home",home.asLong());if(routeA!=null)t.putLong("RouteA",routeA.asLong());if(routeB!=null)t.putLong("RouteB",routeB.asLong());ContainerHelper.saveAllItems(t,cargo);t.putLongArray("Jobs",jobs.stream().mapToLong(BlockPos::asLong).toArray());ListTag flowers=new ListTag();pollinated.forEach((pos,epoch)->{CompoundTag f=new CompoundTag();f.putLong("Position",pos);f.putLong("Epoch",epoch);flowers.add(f);});t.put("Flowers",flowers);ListTag seeds=new ListTag();foundSeeds.forEach(id->seeds.add(StringTag.valueOf(id)));t.put("FoundSeeds",seeds);t.putLong("NextSeedExchange",nextSeedExchange);}
    @Override public void readAdditionalSaveData(CompoundTag t){super.readAdditionalSaveData(t);owner=t.hasUUID("PastureOwner")?t.getUUID("PastureOwner"):null;candidate=t.hasUUID("CareCandidate")?t.getUUID("CareCandidate"):null;careCount=Math.min(3,Math.max(0,t.getInt("CareCount")));lastCare=t.getLong("LastCare");foodCredits=Math.min(16,Math.max(0,t.getInt("Food")));progress=Math.min(interval(),Math.max(0,t.getInt("Progress")));resource=Math.min(capacity(),Math.max(0,t.getInt("Resource")));wax=Math.min(1,Math.max(0,t.getInt("Wax")));nextBreed=t.getLong("NextBreed");nextMana=t.getLong("NextMana");dawnEpoch=t.contains("Dawn")?t.getLong("Dawn"):Long.MIN_VALUE;following=t.getBoolean("Follow");saddled=t.getBoolean("Saddle");returning=t.getBoolean("Returning");trips=Math.max(0,t.getInt("Trips"));endurance=t.contains("Endurance")?Math.min(5,Math.max(0,t.getInt("Endurance"))):5;lastWork=t.getLong("LastWork");home=t.contains("Home")?BlockPos.of(t.getLong("Home")):null;routeA=t.contains("RouteA")?BlockPos.of(t.getLong("RouteA")):null;routeB=t.contains("RouteB")?BlockPos.of(t.getLong("RouteB")):null;ContainerHelper.loadAllItems(t,cargo);for(ItemStack item:cargo)item.setCount(Math.min(16,item.getCount()));jobs.clear();for(long p:t.getLongArray("Jobs"))if(jobs.size()<9)jobs.add(BlockPos.of(p));pollinated.clear();for(Tag tag:t.getList("Flowers",10)){CompoundTag f=(CompoundTag)tag;if(pollinated.size()<64)pollinated.put(f.getLong("Position"),f.getLong("Epoch"));}foundSeeds.clear();for(Tag seed:t.getList("FoundSeeds",8))if(foundSeeds.size()<64)foundSeeds.add(seed.getAsString());nextSeedExchange=t.getLong("NextSeedExchange");long now=level().getGameTime(),credit=t.contains("LastActive")?Math.max(0,now-t.getLong("LastActive"))/2:0;lastActive=now;if(getAge()<0)setAge((int)Math.min(0L,(long)getAge()+Math.min(species.adultDays()*24000L,credit)));if(hasHarvest()&&!speciesId().equals("wirecall_lizard"))progress=(int)Math.min(interval(),progress+credit);if(owner!=null)setPersistenceRequired();sync();}
}






