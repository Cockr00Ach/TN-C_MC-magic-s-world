package com.tnc.tnc.adventure;

import com.tnc.tnc.magic.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.*;
import net.minecraftforge.gametest.*;
import java.util.*;

@GameTestHolder("tnc") @PrefixGameTestTemplate(false)
public final class TownRevisionGameTests {
    @GameTest(template="building_test_empty",timeoutTicks=40)
    public static void castingRequiresManualLearningAndCorrectWand(GameTestHelper h){
        var p=AdventureGameTests.player(h);var data=MagicStone.getOrNull(p);data.assignDefaultAffinities(3);
        var entry=SpellCatalog.byId(ResourceLocation.parse("tnc:water_ball"));int points=data.getPointsAvailable(com.tnc.tnc.Config.pointThresholds);
        h.assertTrue(ManaGate.evaluate(data,entry,true)==ManaGate.Decision.NOT_LEARNED&&!data.hasLearned(entry.id())&&data.getPointsAvailable(com.tnc.tnc.Config.pointThresholds)==points,"Pure check cannot buy a spell");
        p.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,ElementWands.stack(ElementWands.find("fire_wand_1")));
        h.assertTrue(ManaGate.shouldBlock(p,entry.id())&&!data.hasLearned(entry.id())&&data.getPointsAvailable(com.tnc.tnc.Config.pointThresholds)==points,"Wrong wand blocks before learning and spending");
        p.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,ElementWands.stack(ElementWands.find("water_wand_1")));
        h.assertTrue(ManaGate.shouldBlock(p,entry.id())&&!data.hasLearned(entry.id())&&data.getPointsAvailable(com.tnc.tnc.Config.pointThresholds)==points,"Correct wand cannot automatically learn or spend points");
        h.assertTrue(MagicStoneLearning.unlock(data,entry)==MagicStoneLearning.Result.OK,"Player explicitly unlocks spell");
        h.assertTrue(!ManaGate.shouldBlock(p,entry.id())&&data.hasLearned(entry.id())&&data.getPointsAvailable(com.tnc.tnc.Config.pointThresholds)==points-com.tnc.tnc.Config.learnCostForTier(1),"Manually learned real cast is allowed");
        ManaGate.shouldBlock(p,entry.id());h.assertTrue(data.getPointsAvailable(com.tnc.tnc.Config.pointThresholds)==points-com.tnc.tnc.Config.learnCostForTier(1),"Second cast cannot buy again");h.succeed();
    }
    @GameTest(template="building_test_empty",timeoutTicks=40)
    public static void actualPeriodicCatchUpKeepsChaosAndForgottenSpellsSafe(GameTestHelper h)throws Exception{
        var p=AdventureGameTests.player(h);var data=MagicStone.getOrNull(p);data.assignDefaultAffinities(5);
        var known=SpellCatalog.byId(ResourceLocation.parse("tnc:water_ball"));var forgotten=SpellCatalog.byId(ResourceLocation.parse("tnc:dragon_ruin"));
        data.setProgress(known.element(),known.chain(),1);data.setProgress(forgotten.element(),forgotten.chain(),5);data.learn(forgotten.id());data.forget(forgotten.id());
        int points=data.getPointsAvailable(com.tnc.tnc.Config.pointThresholds);
        var repair=TnSpellMechanics.class.getDeclaredMethod("autoLearnUnlockedAndSync",net.minecraft.server.level.ServerPlayer.class);repair.setAccessible(true);repair.invoke(null,p);repair.invoke(null,p);
        h.assertTrue(data.hasLearned(known.id())&&!data.hasLearned(ResourceLocation.parse("tnc:chaos_magic"))&&!data.hasLearned(forgotten.id())&&data.getPointsAvailable(com.tnc.tnc.Config.pointThresholds)==points,"Actual periodic method repairs known tiers without null element, automatic new purchases or forgotten spell restoration");h.succeed();
    }
    @GameTest(template="building_test_empty",timeoutTicks=40)
    public static void bankConservesValueAndKeepsCoinsOnRejectedTransactions(GameTestHelper h){
        var p=AdventureGameTests.player(h);var a=AdventureService.profile(p);
        p.getInventory().setItem(0,new ItemStack(com.tnc.tnc.TNMod.COPPER_COIN.get(),7));
        p.getInventory().setItem(1,new ItemStack(com.tnc.tnc.TNMod.SILVER_COIN.get(),2));
        p.getInventory().setItem(2,new ItemStack(com.tnc.tnc.TNMod.GOLD_COIN.get()));
        BankCounter.deposit(p);BankCounter.deposit(p);
        h.assertTrue(a.coins()==10207&&p.getInventory().isEmpty(),"Deposit removes all denominations and a second request cannot duplicate value");
        BankCounter.withdraw(p,"silver");BankCounter.withdraw(p,"invalid");
        h.assertTrue(a.coins()==10107&&InventoryTransaction.count(p.getInventory(),new ContractCatalog.Material("tnc:silver_coin",false,1))==1,"Withdrawal debits the precise denomination; invalid input is inert");
        for(int i=0;i<p.getInventory().items.size();i++)p.getInventory().setItem(i,new ItemStack(Items.STONE,64));
        BankCounter.withdraw(p,"gold");h.assertTrue(a.coins()==10107,"Full inventory cannot debit");
        p.getInventory().setItem(0,new ItemStack(com.tnc.tnc.TNMod.COPPER_COIN.get()));a.coins=AdventureRules.MAX_COINS;
        BankCounter.deposit(p);h.assertTrue(p.getInventory().getItem(0).getCount()==1&&a.coins()==AdventureRules.MAX_COINS,"Balance cap preserves deposited coin");
        h.succeed();
    }
    @GameTest(template="building_test_empty",timeoutTicks=40)
    public static void armorAndWandHaveSeparateFirstOrderFeesAndShopAuthorization(GameTestHelper h){
        var p=AdventureGameTests.player(h);AdventureService.register(p);var a=AdventureService.profile(p);
        p.getInventory().setItem(0,new ItemStack(Items.RAW_IRON,5));p.getInventory().setItem(1,new ItemStack(Items.COAL));
        AdventureService.action(p,AdventurePackets.Action.ORDER,"equipment_helmet");
        h.assertTrue(a.smithReady<0&&p.getInventory().getItem(0).getCount()==5,"Remote forged packet cannot debit an iron order");
        AdventureService.order(p,"equipment_helmet");AdventureSavedData.get(p.server).activeTicks=a.smithReady;AdventureService.claim(p);AdventureService.claim(p);
        h.assertTrue(a.armorCrafted&&!a.crafted&&a.coins()==0&&p.getInventory().items.stream().filter(s->s.is(Items.IRON_HELMET)).count()==1&&!a.hasMilestone("ordered"),"First iron order is free once and does not fake wand progress");
        p.getInventory().setItem(2,new ItemStack(Items.STICK,4));p.getInventory().setItem(3,new ItemStack(Items.COPPER_INGOT,2));
        AdventureService.order(p,"water_wand_1");h.assertTrue(a.smithReady>=0&&a.smithFree,"First wand stays free after armor");
        AdventureSavedData.get(p.server).activeTicks=a.smithReady;AdventureService.claim(p);
        var saved=AdventureSavedData.readProfile(AdventureSavedData.writeProfile(a));h.assertTrue(saved.armorCrafted&&saved.crafted,"Both discount histories survive saving");h.succeed();
    }
    @GameTest(template="building_test_empty",timeoutTicks=40)
    public static void selfKeepsOriginalStoryAndRetriesGiftWithoutDuplicating(GameTestHelper h){
        var p=AdventureGameTests.player(h);for(int i=0;i<p.getInventory().items.size();i++)p.getInventory().setItem(i,new ItemStack(Items.STONE,64));
        p.getInventory().setItem(0,ItemStack.EMPTY);
        var id=ResourceLocation.parse("tnc:self_first");var q=ResourceLocation.parse("tnc:story_fixture");
        var script=new com.tnc.tnc.dialogue.DialogueScript(id,q,"gold",List.of(q),q,List.of(q),List.of(q),List.of(new com.tnc.tnc.dialogue.DialogueScript.Line("Self","original","wave")));
        var first=TownServices.introduce(p,script);h.assertTrue(!AdventureService.profile(p).hasMilestone("self_materials")&&p.getInventory().getItem(0).isEmpty(),"Half-fit gift is atomic and can be retried");
        h.assertTrue(first.id().equals(id)&&first.next().equals(q)&&first.quests().equals(script.quests())&&first.activate().equals(q)&&first.requires().equals(script.requires())&&first.excludes().equals(script.excludes())&&first.lines().get(3).equals(script.lines().get(0)),"Town directions retain all original story metadata and actions");
        p.getInventory().setItem(1,ItemStack.EMPTY);TownServices.introduce(p,script);var again=TownServices.introduce(p,script);
        h.assertTrue(AdventureService.profile(p).hasMilestone("self_materials")&&InventoryTransaction.count(p.getInventory(),new ContractCatalog.Material("minecraft:stick",false,1))==4&&InventoryTransaction.count(p.getInventory(),new ContractCatalog.Material("minecraft:copper_ingot",false,1))==2&&again==script,"Gift retries once; later conversation remains original");h.succeed();
    }
    @GameTest(template="building_test_empty",timeoutTicks=40)
    public static void selfMigrationPersistsOldPositionAndPreservesOtherManualPlacements(GameTestHelper h){
        var tag=new CompoundTag();tag.putInt("DefaultsVersion",7);var list=new net.minecraft.nbt.ListTag();
        for(String id:new String[]{"self","cava"}){var t=new CompoundTag();t.putString("Id",id);t.putString("Anchor","ABSOLUTE");t.putInt("DX",55);t.putInt("DY",100);t.putInt("DZ",66);list.add(t);}tag.put("Npcs",list);
        var data=com.tnc.tnc.npc.NpcPlacementSavedData.load(tag);data.seedDefaults();var saved=data.save(new CompoundTag());
        h.assertTrue(data.get("self").anchor().equals("ORIGIN")&&data.get("self").dx()==430&&data.get("cava").dx()==55&&saved.getCompound("PreviousSelf").getInt("DX")==55,"Self migrates to tavern and records old entity lookup location, keeping other manual v7 placements");
        var loaded=com.tnc.tnc.npc.NpcPlacementSavedData.load(saved);loaded.seedDefaults();h.assertTrue(loaded.save(new CompoundTag()).getCompound("PreviousSelf").getInt("DX")==55,"Pending original entity lookup survives restart without being overwritten");h.succeed();
    }
    @GameTest(template="building_test_empty",batch="self_move",timeoutTicks=200)
    public static void actualSelfMovesWithSameUuidAndNoDuplicate(GameTestHelper h){
        var l=h.getLevel();var old=h.absolutePos(new BlockPos(3,2,3));var target=old.offset(40,0,0);var forced=new ArrayList<net.minecraft.world.level.ChunkPos>();
        for(var base:List.of(old,target)){var center=new net.minecraft.world.level.ChunkPos(base);for(int x=-2;x<=2;x++)for(int z=-2;z<=2;z++){var c=new net.minecraft.world.level.ChunkPos(center.x+x,center.z+z);if(!l.getForcedChunks().contains(c.toLong())){forced.add(c);l.setChunkForced(c.x,c.z,true);}l.getChunk(c.x,c.z);}}
        l.setBlockAndUpdate(old.below(),net.minecraft.world.level.block.Blocks.STONE.defaultBlockState());
        l.setBlockAndUpdate(target.below(),net.minecraft.world.level.block.Blocks.STONE.defaultBlockState());l.setBlockAndUpdate(target,net.minecraft.world.level.block.Blocks.AIR.defaultBlockState());l.setBlockAndUpdate(target.above(),net.minecraft.world.level.block.Blocks.AIR.defaultBlockState());
        var type=net.minecraftforge.registries.ForgeRegistries.ENTITY_TYPES.getValue(ResourceLocation.parse("tnc:self"));
        var original=new net.minecraft.world.entity.Entity[1];
        var tag=new CompoundTag();tag.putInt("DefaultsVersion",8);var prev=new CompoundTag();prev.putString("Anchor","ABSOLUTE");prev.putInt("DX",old.getX());prev.putInt("DY",old.getY());prev.putInt("DZ",old.getZ());tag.put("PreviousSelf",prev);
        var data=com.tnc.tnc.npc.NpcPlacementSavedData.load(tag);var placement=new com.tnc.tnc.npc.NpcPlacementSavedData.Placement("self","ABSOLUTE",target.getX(),target.getY(),target.getZ());data.put(placement);
        h.succeedWhen(()->{
            h.assertTrue(l.isPositionEntityTicking(old)&&l.isPositionEntityTicking(target)&&forced.stream().allMatch(c->l.areEntitiesLoaded(c.toLong())),"Waiting for the original and destination entity sections before creating Self");
            if(original[0]==null){var npc=type.create(l);npc.moveTo(old.getX()+0.5,old.getY(),old.getZ()+0.5,0,0);h.assertTrue(l.addFreshEntity(npc),"Original Self is really accepted by the loaded fixture");original[0]=npc;}
            data.ensureOne(l,placement);data.ensureOne(l,placement);var moved=com.tnc.tnc.npc.NpcPlacementSavedData.findNear(l,type,target,4);
            h.assertTrue(moved!=null&&moved.getUUID().equals(original[0].getUUID())&&l.getEntitiesOfClass(net.minecraft.world.entity.Entity.class,new net.minecraft.world.phys.AABB(target).inflate(24),e->e.getType()==type).size()==1&&!data.save(new CompoundTag()).contains("PreviousSelf"),"Original Self retains UUID and repeated ensure cannot create duplicate");
            original[0].discard();for(var c:forced)l.setChunkForced(c.x,c.z,false);
        });
    }
    @GameTest(template="building_test_empty",timeoutTicks=40)
    public static void wrongShopCannotClaimOtherMerchantsOrder(GameTestHelper h){
        var p=AdventureGameTests.player(h);p.setPos(h.absolutePos(new BlockPos(4,2,4)).getX(),h.absolutePos(new BlockPos(4,2,4)).getY(),h.absolutePos(new BlockPos(4,2,4)).getZ());AdventureService.register(p);var a=AdventureService.profile(p);
        var merchant=com.tnc.tnc.npc.TNNpcs.SERVICE_NPC.get().create(h.getLevel());merchant.moveTo(p.getX(),p.getY(),p.getZ(),0,0);merchant.role("smith");h.getLevel().addFreshEntity(merchant);
        try{a.smithDesign="equipment_helmet";a.smithReady=0;AdventureService.action(p,AdventurePackets.Action.CLAIM,"");h.assertTrue(a.smithReady==0&&!a.armorCrafted,"Liya cannot deliver Dorn's iron equipment");
            merchant.role("armorer");a.smithDesign="water_wand_1";AdventureService.action(p,AdventurePackets.Action.CLAIM,"");h.assertTrue(a.smithReady==0&&!a.crafted,"Dorn cannot deliver Liya's wand");h.succeed();
        }finally{merchant.discard();}
    }
    @GameTest(template="building_test_empty",timeoutTicks=40)
    public static void allThirtyFiveRealOrdersRetainTheirIdentity(GameTestHelper h){
        for(var d:ElementWands.ALL){var p=AdventureGameTests.player(h);AdventureService.register(p);var a=AdventureService.profile(p);a.addXp(AdventureRules.xpAtLevel(100));a.credit(100000,"fixture");
            int slot=0;for(var m:d.materials())p.getInventory().setItem(slot++,new ItemStack(net.minecraftforge.registries.ForgeRegistries.ITEMS.getValue(ResourceLocation.parse(m.id())),m.count()));
            AdventureService.order(p,d.id());h.assertTrue(a.smithDesign.equals(d.id())&&a.smithReady>=0,"Exact design accepted: "+d.id());
            var copy=AdventureSavedData.readProfile(AdventureSavedData.writeProfile(a));h.assertTrue(copy.smithDesign.equals(d.id()),"Design survives save");
            AdventureSavedData.get(p.server).activeTicks=a.smithReady;AdventureService.claim(p);AdventureService.claim(p);
            h.assertTrue(p.getInventory().items.stream().filter(s->s.is(ElementWands.ITEMS.get(d.id()).get())).count()==1,"Only one exact item delivered: "+d.id());
        }h.succeed();
    }
    @GameTest(template="building_test_empty",timeoutTicks=30)
    public static void typedSelectionKeepsLowerFallbackAndIndependentChaos(GameTestHelper h){
        var water=SpellCatalog.all().stream().filter(e->e.element()==Element.WATER&&e.chain()==SpellCatalog.Chain.WATER_BALL).toList();
        var chaos=ResourceLocation.parse("tnc:chaos_magic");var learned=new ArrayList<ResourceLocation>();water.forEach(e->learned.add(e.id()));learned.add(chaos);
        var selected=ElementWands.supported(learned,ElementWands.find("water_wand_1"));
        h.assertTrue(selected.contains(chaos)&&selected.contains(water.stream().filter(e->e.tier()==1).findFirst().orElseThrow().id())&&selected.size()==2,"Chaos and low-tier fallback coexist without null element");
        h.assertTrue(ElementWands.supported(learned,ElementWands.find("fire_wand_1")).equals(List.of(chaos)),"Other element does not leak");h.succeed();
    }
    @GameTest(template="building_test_empty",timeoutTicks=30)
    public static void castingGateRecognizesTypedWandsAndRejectsWrongTier(GameTestHelper h){
        var p=AdventureGameTests.player(h);p.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,ElementWands.stack(ElementWands.find("water_wand_1")));
        var one=SpellCatalog.all().stream().filter(e->e.element()==Element.WATER&&e.tier()==1).findFirst().orElseThrow();var high=SpellCatalog.all().stream().filter(e->e.element()==Element.WATER&&e.tier()==4).findFirst().orElseThrow();var fire=SpellCatalog.all().stream().filter(e->e.element()==Element.FIRE).findFirst().orElseThrow();
        h.assertTrue(ManaGate.hasWand(p)&&ManaGate.hasWandFor(p,one)&&!ManaGate.hasWandFor(p,high)&&!ManaGate.hasWandFor(p,fire),"Typed wand reaches real mana gate with element and tier enforced");
        p.setItemInHand(net.minecraft.world.InteractionHand.OFF_HAND,ElementWands.stack(ElementWands.find("water_wand_4")));h.assertTrue(ManaGate.hasWandFor(p,high),"Supported offhand wand is allowed");h.succeed();
    }
    @GameTest(template="building_test_empty",timeoutTicks=30)
    public static void forgedPacketCannotOrderCheapLegacyUniversalWand(GameTestHelper h){
        var p=AdventureGameTests.player(h);AdventureService.register(p);p.getInventory().setItem(0,new ItemStack(Items.STICK,4));p.getInventory().setItem(1,new ItemStack(Items.COPPER_INGOT,2));
        AdventureService.order(p,"");AdventureService.order(p,"fire_wand_5");
        h.assertTrue(AdventureService.profile(p).smithReady<0&&p.getInventory().getItem(0).getCount()==4,"Empty ID and unmet level cannot debit or create a universal wand");h.succeed();
    }
    @GameTest(template="building_test_empty",batch="native_bountiful",timeoutTicks=100)
    public static void tavernDecreesPreserveCustomSlots(GameTestHelper h)throws Exception{
        if(!net.minecraftforge.fml.ModList.get().isLoaded("bountiful")){h.succeed();return;}
        var l=h.getLevel();var pos=h.absolutePos(new BlockPos(2,2,2));var block=net.minecraftforge.registries.ForgeRegistries.BLOCKS.getValue(ResourceLocation.parse("bountiful:bountyboard"));
        l.setBlock(pos.below(),net.minecraft.world.level.block.Blocks.OAK_PLANKS.defaultBlockState(),3);l.setBlock(pos,block.defaultBlockState(),3);var be=l.getBlockEntity(pos);
        var dataClass=Class.forName("io.ejekta.bountiful.bounty.DecreeData");var itemClass=Class.forName("io.ejekta.bountiful.content.DecreeItem");var companion=itemClass.getField("Companion").get(null);var decree=dataClass.getConstructor(java.util.List.class).newInstance(java.util.List.of("inventor"));
        var original=(ItemStack)companion.getClass().getMethod("create",dataClass).invoke(companion,decree);var inventory=net.minecraft.core.NonNullList.withSize(3,ItemStack.EMPTY);inventory.set(0,original.copy());var saved=be.saveWithoutMetadata();var tag=new CompoundTag();net.minecraft.world.ContainerHelper.saveAllItems(tag,inventory);saved.put("decree_inv",tag);be.load(saved);
        h.assertTrue(TownServices.seedDecrees(be),"Decorated board can add town commissions beside the existing decree");
        var after=net.minecraft.core.NonNullList.withSize(3,ItemStack.EMPTY);net.minecraft.world.ContainerHelper.loadAllItems(be.saveWithoutMetadata().getCompound("decree_inv"),after);
        h.assertTrue(ItemStack.matches(original,after.get(0)),"Existing custom decree is unchanged");var ids=new java.util.HashSet<String>();
        for(var item:after){var itemTag=item.getTag();if(itemTag!=null&&itemTag.contains("bountiful:decree_data"))com.google.gson.JsonParser.parseString(itemTag.getString("bountiful:decree_data")).getAsJsonObject().getAsJsonArray("ids").forEach(value->ids.add(value.getAsString()));}
        h.assertTrue(ids.containsAll(java.util.List.of("tnc_supply","tnc_food","tnc_hunt")),"All three town pools fit the remaining two slots");
        var snapshot=be.saveWithoutMetadata().getCompound("decree_inv").copy();TownServices.seedDecrees(be);h.assertTrue(snapshot.equals(be.saveWithoutMetadata().getCompound("decree_inv")),"Repeated seed does not duplicate or replace decrees");h.succeed();
    }
    @GameTest(template="building_test_empty",batch="native_bountiful",timeoutTicks=100)
    public static void realBountifulPoolGenerationAndPaperCashIn(GameTestHelper h)throws Exception{
        if(!net.minecraftforge.fml.ModList.get().isLoaded("bountiful")){h.succeed();return;}
        var l=h.getLevel();var pos=h.absolutePos(new BlockPos(2,2,2));var store=l.getDataStorage();var skyClass=com.tnc.tnc.world.SkyIslandSavedData.class;
        var old=com.tnc.tnc.world.SkyIslandSavedData.get(l);var load=skyClass.getDeclaredMethod("load",CompoundTag.class);load.setAccessible(true);var fixture=old.save(new CompoundTag());
        fixture.putString("Phase","COMPLETE");fixture.putBoolean("LayoutReady",true);var origin=pos.subtract(com.tnc.tnc.tavern.TavernUpgrade.BOARD);fixture.putInt("OriginX",origin.getX());fixture.putInt("OriginY",origin.getY());fixture.putInt("OriginZ",origin.getZ());
        store.set("tnc_sky_island_v5",(net.minecraft.world.level.saveddata.SavedData)load.invoke(null,fixture));
        var p=AdventureGameTests.player(h);p.setPos(pos.getX()+0.5,pos.getY(),pos.getZ()+1.5);AdventureService.register(p);
        var playersField=net.minecraftforge.fml.util.ObfuscationReflectionHelper.findField(net.minecraft.server.players.PlayerList.class,"f_11196_");
        @SuppressWarnings("unchecked") var players=(java.util.List<net.minecraft.server.level.ServerPlayer>)playersField.get(p.server.getPlayerList());
        players.add(p);
        try{
            var block=net.minecraftforge.registries.ForgeRegistries.BLOCKS.getValue(ResourceLocation.parse("bountiful:bountyboard"));l.setBlock(pos.below(),net.minecraft.world.level.block.Blocks.OAK_PLANKS.defaultBlockState(),3);l.setBlock(pos,block.defaultBlockState(),3);
            var be=l.getBlockEntity(pos);h.assertTrue(TownServices.seedDecrees(be),"Native decree creation works against actual jar");
            var inv=(net.minecraft.world.Container)be.getClass().getMethod("fullInventoryCopy").invoke(be);ItemStack paper=ItemStack.EMPTY;
            for(int i=0;i<inv.getContainerSize();i++)if(NativeBountiful.isTownPaper(inv.getItem(i))){paper=inv.getItem(i).copy();break;}
            h.assertTrue(!paper.isEmpty(),"Actual Bountiful generated a TN-C paper using all loaded pools");
            var dc=Class.forName("io.ejekta.bountiful.bounty.BountyData");var companion=dc.getField("Companion").get(null);var data=companion.getClass().getMethod("get",ItemStack.class).invoke(companion,paper);
            @SuppressWarnings("unchecked") var rewards=(List<Object>)dc.getMethod("getRewards").invoke(data);
            h.assertTrue(!rewards.isEmpty(),"Generated paper contains a real reward");
            // Two actual command rewards must still count as one completed native paper.
            while(rewards.size()>1)rewards.remove(rewards.size()-1);
            // Always cover first-time food rewards: the farm gift is awarded while
            // native Bountiful still holds the original paper reference.
            var contentField=rewards.get(0).getClass().getDeclaredField("content");contentField.setAccessible(true);
            String rewardCommand=(String)contentField.get(rewards.get(0));
            contentField.set(rewards.get(0),rewardCommand.replace("\"supply\"","\"food\"").replace("\"hunt\"","\"food\""));
            rewards.add(rewards.get(0));
            int slot=1;for(Object objective:(List<?>)dc.getMethod("getObjectives").invoke(data)){
                var ec=objective.getClass();int count=(Integer)ec.getMethod("getAmount").invoke(objective);String content=(String)ec.getMethod("getContent").invoke(objective);
                if(ec.getMethod("getLogicId").invoke(objective).toString().endsWith(":item")){var item=net.minecraftforge.registries.ForgeRegistries.ITEMS.getValue(ResourceLocation.parse(content));p.getInventory().setItem(slot++,new ItemStack(item,count));}
                else ec.getMethod("setCurrent",int.class).invoke(objective,count);
            }
            companion.getClass().getMethod("set",ItemStack.class,Object.class).invoke(companion,paper,data);p.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,paper);
            var hit=new net.minecraft.world.phys.BlockHitResult(net.minecraft.world.phys.Vec3.atCenterOf(pos),net.minecraft.core.Direction.SOUTH,pos,false);
            var wrongPos=pos.offset(2,0,0);var wrongHit=new net.minecraft.world.phys.BlockHitResult(net.minecraft.world.phys.Vec3.atCenterOf(wrongPos),net.minecraft.core.Direction.SOUTH,wrongPos,false);
            var wrongEvent=new net.minecraftforge.event.entity.player.PlayerInteractEvent.RightClickBlock(p,net.minecraft.world.InteractionHand.MAIN_HAND,wrongPos,wrongHit);
            TownServices.use(wrongEvent);h.assertTrue(wrongEvent.isCanceled()&&!p.getMainHandItem().isEmpty()&&AdventureService.profile(p).coins()==0,"Wrong board blocks before native consumption");
            AdventureService.profile(p).registered=false;var unregistered=new net.minecraftforge.event.entity.player.PlayerInteractEvent.RightClickBlock(p,net.minecraft.world.InteractionHand.MAIN_HAND,pos,hit);
            TownServices.use(unregistered);h.assertTrue(unregistered.isCanceled()&&!p.getMainHandItem().isEmpty(),"Registration failure preserves paper and objectives");AdventureService.profile(p).registered=true;
            int originalPaperCount=paper.getCount();
            l.getBlockState(pos).use(l,p,net.minecraft.world.InteractionHand.MAIN_HAND,hit);NativeBountiful.refreshCount(p);
            h.assertTrue(p.getMainHandItem().isEmpty()&&AdventureService.profile(p).coins()>0&&AdventureService.profile(p).nativeBounties==1,"One native paper pays and counts exactly once, including multi-reward papers: empty="+p.getMainHandItem().isEmpty()+", coins="+AdventureService.profile(p).coins()+", count="+AdventureService.profile(p).nativeBounties+", atBoard="+TownServices.atBoard(p)+", originalCount="+originalPaperCount+", held="+p.getMainHandItem()+", paperRemaining="+paper.getCount());
            h.assertTrue(p.getInventory().contains(new ItemStack(com.tnc.tnc.TNMod.FARM_FOCUS.get())),"First food completion also grants its farm focus");
            long coins=AdventureService.profile(p).coins();l.getBlockState(pos).use(l,p,net.minecraft.world.InteractionHand.MAIN_HAND,hit);NativeBountiful.refreshCount(p);h.assertTrue(AdventureService.profile(p).coins()==coins&&AdventureService.profile(p).nativeBounties==1,"Second use cannot pay an empty paper");
            h.assertTrue(p.server.getCommands().getDispatcher().parse("tnc_bounty_reward \"%PLAYER_NAME%\" \"food\" \"%BOUNTY_AMOUNT%\"",p.server.createCommandSourceStack()).getExceptions().isEmpty(),"Native placeholder command parses before substitution");
        }finally{players.remove(p);store.set("tnc_sky_island_v5",old);l.removeBlock(pos,false);}
        h.succeed();
    }
}
