package com.tnc.tnc.life.pasture;

import com.tnc.tnc.adventure.AdventureGameTests;
import com.tnc.tnc.production.energy.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.gametest.*;
import net.minecraftforge.registries.ForgeRegistries;
import java.util.*;

@GameTestHolder("tnc")
@PrefixGameTestTemplate(false)
public final class PastureGameTests {
    @GameTest(template="building_test_empty",batch="pasture",timeoutTicks=30)
    public static void generationPredicateNeverLoadsItsBackingServerChunk(GameTestHelper h){
        // WorldGenRegion implements this interface but is not a ServerLevel.
        // A forbidden getLevel() call reproduces the cross-thread chunk deadlock.
        var region=(net.minecraft.world.level.ServerLevelAccessor)java.lang.reflect.Proxy.newProxyInstance(
                PastureGameTests.class.getClassLoader(),new Class[]{net.minecraft.world.level.ServerLevelAccessor.class},
                (proxy,method,args)->switch(method.getName()){
                    case "getBlockState" -> Blocks.GRASS_BLOCK.defaultBlockState();
                    case "getRawBrightness" -> 15;
                    case "getLevel" -> throw new AssertionError("Generation may not read chunks through its backing server");
                    default -> throw new AssertionError("Unexpected region operation: "+method.getName());
                });
        h.assertTrue(PastureEcology.canSpawn(PastureRegistry.TYPES.get("stonebarrow_boar"),region,
                net.minecraft.world.entity.MobSpawnType.CHUNK_GENERATION,BlockPos.ZERO,net.minecraft.util.RandomSource.create()),"Wilderness chunk generation checks its own local ground and light without server access");h.succeed();
    }
    private static PastureAnimal animal(GameTestHelper h,String id,ServerPlayer owner,BlockPos pos){
        PastureAnimal animal=PastureRegistry.TYPES.get(id).create(h.getLevel());h.assertTrue(animal!=null,"Independent species exists: "+id);
        if(owner!=null){CompoundTag tag=new CompoundTag();tag.putUUID("PastureOwner",owner.getUUID());tag.putInt("Food",8);tag.putLong("Home",pos.asLong());animal.readAdditionalSaveData(tag);}
        animal.setPos(pos.getX()+.5,pos.getY(),pos.getZ()+.5);return animal;
    }
    private static PastureFacilityEntity facility(GameTestHelper h,String id,ServerPlayer owner,BlockPos pos){h.getLevel().setBlock(pos,PastureRegistry.block(id).defaultBlockState(),3);var entity=(PastureFacilityEntity)h.getLevel().getBlockEntity(pos);entity.claim(owner.getUUID());return entity;}
    @GameTest(template="building_test_empty",batch="pasture",timeoutTicks=30)
    public static void allIndependentSpeciesKeepIdentityAndTwoDayOrLongerYoung(GameTestHelper h){
        h.assertTrue(PastureRegistry.TYPES.size()==23,"Twenty-three new registered species supplement existing independent bellwool");Set<Object> types=new HashSet<>();var owner=AdventureGameTests.player(h);
        for(var species:PastureSpecies.ALL){BlockPos pos=h.absolutePos(new BlockPos(2,2,2));PastureAnimal parent=animal(h,species.id(),owner,pos),mate=animal(h,species.id(),owner,pos);h.assertTrue(types.add(parent.getType()),"Independent EntityType for "+species.id());h.assertTrue(ForgeRegistries.ENTITY_TYPES.getKey(parent.getType()).getPath().equals(species.id()),"Stable saved type ID");PastureAnimal child=parent.getBreedOffspring(h.getLevel(),mate);h.assertTrue(child!=null&&child.getType()==parent.getType()&&child.getAge()<=-48000,"Species-specific offspring takes natural days to grow");h.assertTrue(child.owner().equals(owner.getUUID()),"Same-owner parentage is retained");CompoundTag saved=new CompoundTag();parent.addAdditionalSaveData(saved);PastureAnimal loaded=animal(h,species.id(),null,pos);loaded.readAdditionalSaveData(saved);h.assertTrue(owner.getUUID().equals(loaded.owner()),"Caretaker survives save-load");h.assertTrue(loaded.storedResource()==0,"New livestock never loads with invented mana/electricity/products");parent.discard();mate.discard();child.discard();loaded.discard();}h.succeed();
    }
    @GameTest(template="building_test_empty",batch="pasture",timeoutTicks=30)
    public static void threeSeparatedFeedingsAdoptButFastSpamAndOtherOwnerFail(GameTestHelper h){
        var owner=AdventureGameTests.player(h);var stranger=AdventureGameTests.player(h);BlockPos pos=h.absolutePos(new BlockPos(2,2,2));PastureAnimal animal=animal(h,"stonebarrow_boar",null,pos);ItemStack food=new ItemStack(Items.POTATO,4);owner.setItemInHand(InteractionHand.MAIN_HAND,food);
        animal.mobInteract(owner,InteractionHand.MAIN_HAND);h.assertTrue(food.getCount()==3&&animal.owner()==null,"First valid care consumes one food but does not instantly tame");animal.mobInteract(owner,InteractionHand.MAIN_HAND);h.assertTrue(food.getCount()==3,"Spam within sixty seconds does not consume food or advance adoption");
        CompoundTag tag=new CompoundTag();animal.addAdditionalSaveData(tag);tag.putLong("LastCare",h.getLevel().getGameTime()-1200);animal.readAdditionalSaveData(tag);animal.mobInteract(owner,InteractionHand.MAIN_HAND);animal.addAdditionalSaveData(tag);tag.putLong("LastCare",h.getLevel().getGameTime()-1200);animal.readAdditionalSaveData(tag);animal.mobInteract(owner,InteractionHand.MAIN_HAND);
        h.assertTrue(owner.getUUID().equals(animal.owner())&&food.getCount()==1,"Three separated feedings establish real owner");h.assertTrue(!animal.mayCareFor(stranger),"Another player's feed, harvest or transport is refused");animal.discard();h.succeed();
    }
    @GameTest(template="building_test_empty",batch="pasture",timeoutTicks=30)
    public static void livingGlueHarvestDebitsBeforeDropAndRelogDoesNotDuplicate(GameTestHelper h){
        var player=AdventureGameTests.player(h);BlockPos pos=h.absolutePos(new BlockPos(2,2,2));PastureAnimal snail=animal(h,"pattern_shell_snail",player,pos);CompoundTag tag=new CompoundTag();snail.addAdditionalSaveData(tag);tag.putInt("Resource",1);tag.putInt("Progress",24000);snail.readAdditionalSaveData(tag);ItemStack scraper=new ItemStack(PastureRegistry.item("pasture_scraper"));
        h.assertTrue(snail.harvest(player,scraper,InteractionHand.MAIN_HAND),"Wooden scraper collects actual living shell glue");h.assertTrue(snail.storedResource()==0&&!snail.harvest(player,scraper,InteractionHand.MAIN_HAND),"Second harvest cannot duplicate stock");snail.addAdditionalSaveData(tag);PastureAnimal loaded=animal(h,"pattern_shell_snail",null,pos);loaded.readAdditionalSaveData(tag);h.assertTrue(!loaded.harvest(player,scraper,InteractionHand.MAIN_HAND),"Reload preserves consumed stock/cooldown");h.assertTrue(player.getInventory().countItem(PastureRegistry.item("shell_glue"))==1,"Exactly one product entered inventory");snail.discard();loaded.discard();h.succeed();
    }
    @GameTest(template="building_test_empty",batch="pasture",timeoutTicks=30)
    public static void whaleManaBottleAndCompanionConserveBalancesAcrossCopies(GameTestHelper h){
        var player=AdventureGameTests.player(h);BlockPos pos=h.absolutePos(new BlockPos(2,2,2));PastureAnimal whale=animal(h,"dewbound_whale",player,pos),marten=animal(h,"pillowlight_marten",player,pos);CompoundTag tag=new CompoundTag();whale.addAdditionalSaveData(tag);tag.putInt("Resource",48);whale.readAdditionalSaveData(tag);
        ItemStack bottle=new ItemStack(PastureRegistry.item("mana_bottle"));var ledger=PastureBottleLedger.get(h.getLevel());int taken=whale.takeMana(player,24);h.assertTrue(taken==24&&ledger.deposit(bottle,24,taken)==24,"Whale debit equals bottle balance");h.assertTrue(whale.takeMana(player,24)==0&&whale.storedResource()==24,"Thirty-second collection cooldown shared by all bottles");ItemStack copied=bottle.copy();int received=marten.receiveMana(player,ledger.amount(copied));ledger.withdraw(copied,received);h.assertTrue(marten.storedResource()==24&&ledger.amount(bottle)==0,"NBT bottle copies refer to one authoritative ledger, never duplicate mana");CompoundTag saved=ledger.save(new CompoundTag());var loaded=PastureBottleLedger.load(saved);h.assertTrue(loaded.amount(copied)==0,"Consumed balance survives world reload");whale.discard();marten.discard();h.succeed();
    }
    @GameTest(template="building_test_empty",batch="pasture",timeoutTicks=30)
    public static void electricalPerchConsumesOneHundredToStoreEightyAndCrystalsDebitFifty(GameTestHelper h){
        var player=AdventureGameTests.player(h);BlockPos pos=h.absolutePos(new BlockPos(2,2,2));var perch=facility(h,"charging_perch",player,pos);BlockPos energyPos=pos.east();h.getLevel().setBlock(energyPos,EnergyContent.BATTERY.defaultBlockState(),3);var battery=(EnergyBlockEntity)h.getLevel().getBlockEntity(energyPos);battery.claim(player.getUUID());battery.addEnergy(100);PastureAnimal lizard=animal(h,"wirecall_lizard",player,pos);
        h.assertTrue(perch.chargeAnimal(lizard)&&battery.energy()==0&&lizard.storedResource()==80,"Charging cannot invent FE from food");h.assertTrue(lizard.harvest(player,ItemStack.EMPTY,InteractionHand.MAIN_HAND)&&lizard.storedResource()==30,"Crystal removes fifty real FE from animal");h.assertTrue(!lizard.harvest(player,ItemStack.EMPTY,InteractionHand.MAIN_HAND),"Thirty remaining FE cannot pay for another fifty-FE crystal");h.assertTrue(!perch.chargeAnimal(lizard),"Empty battery cannot recharge");lizard.discard();h.succeed();
    }
    @GameTest(template="building_test_empty",batch="pasture",timeoutTicks=30)
    public static void foodTroughConsumesActualStockAndConcentrateNeedsRealWater(GameTestHelper h){
        var player=AdventureGameTests.player(h);BlockPos pos=h.absolutePos(new BlockPos(2,2,2));var trough=facility(h,"pasture_trough",player,pos);h.assertTrue(trough.insert(new ItemStack(Items.COD,2)).isEmpty(),"Accepted food enters real four-slot stock");h.assertTrue(trough.takeFood(Items.COD)&&trough.getItem(0).getCount()==1,"One feeding consumes one item");h.assertTrue(!trough.takeFood(Items.APPLE),"Absent food cannot be invented");player.moveTo(pos.getX()+.5,pos.getY(),pos.getZ()+.5);var bucket=new ItemStack(Items.WATER_BUCKET);trough.use(player,bucket);h.assertTrue(bucket.isEmpty()&&player.getInventory().countItem(Items.BUCKET)==1,"One real water bucket is consumed and empty bucket returned");h.assertTrue(trough.takeWater(4)==4&&trough.takeWater(12)==12&&trough.takeWater(1)==0,"Water ledger holds precisely sixteen portions");h.succeed();
    }
    @GameTest(template="building_test_empty",batch="pasture",timeoutTicks=30)
    public static void porterWalksBeforeTakingAndDeliveringRealFourSlotCargo(GameTestHelper h){
        var player=AdventureGameTests.player(h);BlockPos start=h.absolutePos(new BlockPos(1,2,1)),end=h.absolutePos(new BlockPos(4,2,4));var source=facility(h,"pasture_tray",player,start);var destination=facility(h,"pasture_tray",player,end);source.insert(new ItemStack(Items.WHEAT,16));PastureAnimal porter=animal(h,"satchelback_runner",player,end);
        h.assertTrue(porter.setJob(player,start)&&porter.setJob(player,end),"Both own trays define route");porter.tickWork(h.getLevel());h.assertTrue(source.getItem(0).getCount()==16&&porter.carriedItems().stream().allMatch(ItemStack::isEmpty),"Distant destination cannot teleport source cargo");porter.setPos(start.getX()+.5,start.getY(),start.getZ()+.5);porter.tickWork(h.getLevel());h.assertTrue(source.isEmpty()&&porter.carriedItems().get(0).getCount()==16,"Arriving at source moves real stock into animal");porter.tickWork(h.getLevel());h.assertTrue(destination.isEmpty(),"Remaining at source cannot instantly deliver");CompoundTag saved=new CompoundTag();porter.addAdditionalSaveData(saved);PastureAnimal loaded=animal(h,"satchelback_runner",null,end);loaded.readAdditionalSaveData(saved);loaded.setPos(end.getX()+.5,end.getY(),end.getZ()+.5);loaded.tickWork(h.getLevel());h.assertTrue(destination.getItem(0).getCount()==16&&loaded.carriedItems().stream().allMatch(ItemStack::isEmpty),"Saved cargo reaches destination once");porter.discard();loaded.discard();h.succeed();
    }
    @GameTest(template="building_test_empty",batch="pasture",timeoutTicks=30)
    public static void earthWorkerOnlyTillsApprovedReachableDirtNotSolidBuilding(GameTestHelper h){
        var player=AdventureGameTests.player(h);BlockPos center=h.absolutePos(new BlockPos(2,1,2));h.getLevel().setBlock(center,Blocks.DIRT.defaultBlockState(),3);h.getLevel().setBlock(center.east(),Blocks.STONE_BRICKS.defaultBlockState(),3);PastureAnimal rhino=animal(h,"bowlhorn_rhino",player,center.above());h.assertTrue(rhino.setJob(player,center),"Feeding one portion approves real three-by-three plan");for(int i=0;i<9;i++)rhino.tickWork(h.getLevel());h.assertTrue(h.getLevel().getBlockState(center).is(Blocks.FARMLAND),"Approved dirt changes into actual farmland");h.assertTrue(h.getLevel().getBlockState(center.east()).is(Blocks.STONE_BRICKS),"Worker cannot tear down masonry");rhino.discard();h.succeed();
    }
    @GameTest(template="building_test_empty",batch="pasture",timeoutTicks=30)
    public static void cageTicketIsSingleUseAndEntityInventoryRetainsPermanentUuid(GameTestHelper h){
        var player=AdventureGameTests.player(h);BlockPos pos=h.absolutePos(new BlockPos(2,2,2));PastureAnimal animal=animal(h,"satchelback_runner",player,pos);h.getLevel().addFreshEntity(animal);var uuid=animal.getUUID();ItemStack cage=new ItemStack(PastureRegistry.item("pasture_cage"));var item=(PastureCageItem)cage.getItem();h.assertTrue(item.capture(player,cage,animal).consumesAction(),"Owner can move a real animal into persistent escrow");h.assertTrue(animal.isRemoved(),"Captured animal leaves loaded world");var ticket=cage.getTag().getUUID("Ticket");var ledger=PastureCageLedger.get(player.server);var saved=ledger.peek(ticket);h.assertTrue(saved!=null&&saved.getUUID("UUID").equals(uuid),"Transport stores the original permanent entity UUID");var loaded=PastureCageLedger.load(ledger.save(new CompoundTag()));h.assertTrue(loaded.consume(ticket)&&!loaded.consume(ticket)&&loaded.peek(ticket)==null,"Copied cage tickets cannot release two entities");ledger.consume(ticket);h.succeed();
    }
    @GameTest(template="building_test_empty",batch="pasture",timeoutTicks=60)
    public static void matureEggCreatesRealJuvenileAndFullPastureKeepsRemainingEgg(GameTestHelper h){
        var player=AdventureGameTests.player(h);BlockPos pos=h.absolutePos(new BlockPos(2,2,2));var rack=facility(h,"egg_rack",player,pos);ItemStack egg=new ItemStack(PastureRegistry.item("fertile_pasture_egg"));egg.getOrCreateTag().putString("Species","froststride_fowl");egg.getOrCreateTag().putUUID("EggOwner",player.getUUID());egg.getOrCreateTag().putInt("Incubation",47980);h.assertTrue(rack.insert(egg).isEmpty(),"One actual fertilized egg enters own incubation rack");
        h.runAfterDelay(25,()->{var children=h.getLevel().getEntitiesOfClass(PastureAnimal.class,new AABB(pos).inflate(4),a->a.speciesId().equals("froststride_fowl")&&player.getUUID().equals(a.owner()));h.assertTrue(children.size()==1&&children.get(0).isBaby(),"Loaded-time incubation creates exactly one living juvenile and consumes egg");h.assertTrue(rack.isEmpty(),"Hatched egg is no longer in inventory");for(var child:children)child.discard();List<PastureAnimal> full=new ArrayList<>();for(int i=0;i<8;i++){PastureAnimal adult=animal(h,"froststride_fowl",player,pos);adult.setNoAi(true);h.getLevel().addFreshEntity(adult);full.add(adult);}ItemStack waiting=egg.copy();waiting.getOrCreateTag().putInt("Incubation",48000);rack.insert(waiting);h.runAfterDelay(25,()->{h.assertTrue(rack.getItem(0).is(PastureRegistry.item("fertile_pasture_egg")),"Eight same-species animals pause hatching and retain mature egg");for(var adult:full)adult.discard();h.succeed();});});
    }
    @GameTest(template="building_test_empty",batch="pasture",timeoutTicks=60)
    public static void mobileCompanionLightExpiresAndNeverRemovesReplacementBlock(GameTestHelper h){
        var player=AdventureGameTests.player(h);BlockPos pos=h.absolutePos(new BlockPos(2,2,2));var light=facility(h,"pasture_glow",player,pos);light.setLight(UUID.randomUUID(),h.getLevel().getGameTime()+5);
        h.runAfterDelay(8,()->{h.assertTrue(h.getLevel().getBlockState(pos).isAir(),"Real temporary light removes itself after actual expiry");var second=facility(h,"pasture_glow",player,pos);second.setLight(UUID.randomUUID(),h.getLevel().getGameTime()+5);h.getLevel().setBlock(pos,Blocks.STONE.defaultBlockState(),3);h.runAfterDelay(8,()->{h.assertTrue(h.getLevel().getBlockState(pos).is(Blocks.STONE),"Animal light expiry cannot destroy a player's replacement block");h.succeed();});});
    }
}


