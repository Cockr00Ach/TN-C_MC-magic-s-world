package com.tnc.tnc.tavern;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.item.*;
import net.minecraftforge.gametest.*;
import com.tnc.tnc.npc.TNNpcs;
import java.util.List;

@GameTestHolder("tnc") @PrefixGameTestTemplate(false)
public final class TavernGameTests {
    @GameTest(template="building_test_empty",timeoutTicks=60)
    public static void patronPersistsSeatedCoordinatesAndClothes(GameTestHelper h) {
        var l=h.getLevel();var origin=h.absolutePos(new BlockPos(1,3,1));var guest=TNNpcs.TAVERN_GUEST.get().create(l);
        var spec=new TavernAtmosphere.Seat("test_seat",BlockPos.ZERO,"minecraft:oak_planks",.6,90,8,"远行者",List.of("今晚先歇一会儿。"));
        guest.configure(spec,origin);l.addFreshEntity(guest);guest.setDeltaMovement(1,1,1);
        h.runAfterDelay(5,()->{
            h.assertTrue(Math.abs(guest.getX()-origin.getX()-.5)<.001&&Math.abs(guest.getY()-origin.getY()-.6)<.001,"Seated patron must not fall or drift");
            var tag=new CompoundTag();guest.saveWithoutId(tag);var restored=TNNpcs.TAVERN_GUEST.get().create(l);restored.load(tag);
            h.assertTrue(restored.seatId().equals("test_seat")&&restored.isNoAi()&&restored.isNoGravity()&&restored.isInvulnerable(),"Loaded patron lost seated state");
            h.assertTrue(restored.getVillagerData().getType()==guest.getVillagerData().getType()&&restored.getVillagerData().getProfession()==guest.getVillagerData().getProfession(),"Clothes changed on reload");guest.discard();h.succeed();
        });
    }
    @GameTest(template="building_test_empty",timeoutTicks=60)
    public static void sameStateContainerRetainsItsInventory(GameTestHelper h)throws Exception {
        var l=h.getLevel();var pos=h.absolutePos(new BlockPos(1,2,1));var block=Blocks.CHEST.defaultBlockState();l.setBlock(pos,block,18);
        var chest=(net.minecraft.world.level.block.entity.ChestBlockEntity)l.getBlockEntity(pos);chest.setItem(0,new ItemStack(Items.DIAMOND,7));
        var template=chest.saveWithFullMetadata();template.remove("Items");
        TavernUpgrade.place(l,pos,new TavernUpgrade.Change(BlockPos.ZERO,Blocks.AIR.defaultBlockState(),block,template));
        h.assertTrue(chest.getItem(0).getCount()==7,"Idempotent tavern upgrade replaced existing inventory");h.succeed();
    }
    @GameTest(template="building_test_empty",timeoutTicks=60)
    public static void furnitureCopiesNbtAndRelocatesItsCoordinates(GameTestHelper h)throws Exception {
        var l=h.getLevel();var pos=h.absolutePos(new BlockPos(1,2,1));var sample=h.absolutePos(new BlockPos(3,2,1));var block=Blocks.CHEST.defaultBlockState();l.setBlock(sample,block,18);
        var chest=(net.minecraft.world.level.block.entity.ChestBlockEntity)l.getBlockEntity(sample);chest.setItem(0,new ItemStack(Items.BREAD,3));var template=chest.saveWithFullMetadata();
        TavernUpgrade.place(l,pos,new TavernUpgrade.Change(BlockPos.ZERO,Blocks.AIR.defaultBlockState(),block,template));
        var actual=(net.minecraft.world.level.block.entity.ChestBlockEntity)l.getBlockEntity(pos);h.assertTrue(actual.getBlockPos().equals(pos)&&actual.getItem(0).is(Items.BREAD)&&actual.getItem(0).getCount()==3,"Furniture placement lost NBT or position");h.succeed();
    }
    @GameTest(template="building_test_empty",timeoutTicks=60)
    public static void preflightRejectsPlayerEditsAndResumeRescans(GameTestHelper h)throws Exception {
        var l=h.getLevel();var p=h.absolutePos(new BlockPos(1,2,1));var s=new TavernUpgrade.State();s.origin=p;
        var c=new TavernUpgrade.Change(BlockPos.ZERO,Blocks.AIR.defaultBlockState(),Blocks.STONE.defaultBlockState(),null);
        var plan=new TavernUpgrade.Plan("fixture",List.of(c),List.of(),new TavernAtmosphere(List.of(),List.of(),0));l.setBlock(p,Blocks.GOLD_BLOCK.defaultBlockState(),18);
        boolean refused=false;try{TavernUpgrade.validate(l,s,plan);}catch(java.io.IOException expected){refused=true;}h.assertTrue(refused,"Player's blocks must be preserved");
        l.setBlock(p,Blocks.STONE.defaultBlockState(),18);TavernUpgrade.validate(l,s,plan);
        s.phase=1;s.cursor=7;var saved=s.save(new CompoundTag());var loaded=TavernUpgrade.State.load(saved);h.assertTrue(loaded.phase==0&&loaded.cursor==0,"Recovery must repeat preflight");h.succeed();
    }
    @GameTest(template="building_test_empty",timeoutTicks=60)
    public static void realSeatCatalogHasUniqueShortDialoguesAndIndoorRooms(GameTestHelper h)throws Exception {
        var a=TavernAtmosphere.load(h.getLevel().getServer());h.assertTrue(a.seats().size()==43&&a.totalSeats()==75,"Wrong patron occupancy after clearing the entrance group");
        h.assertTrue(a.seats().stream().noneMatch(s->s.local().getY()==90&&s.block().contains("red_cushion")),"Entrance cushions must all be empty");
        for(var seat:a.seats())h.assertTrue(com.tnc.tnc.dialogue.DialogueLoader.get(h.getLevel().getServer().getResourceManager(),ResourceLocation.fromNamespaceAndPath("tnc","tavern/"+seat.id())).isPresent(),"Missing patron dialogue: "+seat.id());
        h.assertTrue(a.rooms().stream().anyMatch(b->b.contains(465.5,90,292.5)),"Bartender's back walkway must be inside music region");h.assertTrue(a.rooms().stream().anyMatch(b->b.contains(429.5,81,293.5)),"Cellar must remain inside music region");h.assertTrue(a.rooms().stream().noneMatch(b->b.contains(418,90,291)),"Approach road must not start tavern music");h.succeed();
    }
    @GameTest(template="building_test_empty",timeoutTicks=60)
    public static void cellarAcceptsGeneratedSoilButRejectsPlayerWalls(GameTestHelper h)throws Exception {
        var l=h.getLevel();var p=h.absolutePos(new BlockPos(1,2,1));var change=new TavernUpgrade.Change(BlockPos.ZERO,Blocks.STONE.defaultBlockState(),Blocks.AIR.defaultBlockState(),null);
        l.setBlock(p,Blocks.GRASS_BLOCK.defaultBlockState(),18);TavernBasementUpgrade.validate(l,p,List.of(change));TavernUpgrade.place(l,p,change);h.assertTrue(l.getBlockState(p).isAir(),"Grass covering the cellar opening must be removed");
        l.setBlock(p,Blocks.GOLD_BLOCK.defaultBlockState(),18);boolean refused=false;try{TavernBasementUpgrade.validate(l,p,List.of(change));}catch(java.io.IOException expected){refused=true;}h.assertTrue(refused,"Cellar correction must preserve player construction");h.succeed();
    }
    @GameTest(template="building_test_empty",timeoutTicks=60)
    public static void cellarCorrectionAlignsExistingWorkersAndRetainsIdentity(GameTestHelper h) {
        var l=h.getLevel();var origin=h.absolutePos(new BlockPos(2,2,2)).subtract(new BlockPos(430,90,294));
        var self=TNNpcs.SELF.get().create(l);var old=origin.offset(430,90,294);self.moveTo(old.getX()+.5,old.getY(),old.getZ()+.5,180,0);l.addFreshEntity(self);
        var guild=TNNpcs.SERVICE_NPC.get().create(l);guild.role("guild");guild.moveTo(old.getX()+2.5,old.getY(),old.getZ()+.5,90,0);l.addFreshEntity(guild);var selfId=self.getUUID();var guildId=guild.getUUID();
        TavernBasementUpgrade.alignWorkers(l,origin);h.assertTrue(self.getUUID().equals(selfId)&&self.blockPosition().equals(origin.offset(465,90,292))&&self.getYRot()==90,"Self must retain UUID behind the bar facing west");
        h.assertTrue(guild.getUUID().equals(guildId)&&guild.blockPosition().equals(origin.offset(436,90,286))&&guild.getYRot()==0,"Eileen must retain UUID behind the lectern facing south");self.discard();guild.discard();h.succeed();
    }
    @GameTest(template="building_test_empty",timeoutTicks=60)
    public static void removedEntranceGuestsDisappearWithoutRemovingTheSeats(GameTestHelper h)throws Exception {
        var l=h.getLevel();var pos=h.absolutePos(new BlockPos(2,2,2));var origin=pos.subtract(new BlockPos(427,90,289));l.setBlock(pos,Blocks.OAK_PLANKS.defaultBlockState(),18);
        var guest=TNNpcs.TAVERN_GUEST.get().create(l);guest.configure(new TavernAtmosphere.Seat("guest_01",new BlockPos(427,90,289),"minecraft:oak_planks",.2,0,0,"入口客人",List.of("短句")),origin);l.addFreshEntity(guest);
        TavernUpgrade.ensureGuests(l,origin,TavernAtmosphere.load(l.getServer()));h.assertTrue(guest.isRemoved()&&l.getBlockState(pos).is(Blocks.OAK_PLANKS),"Remove the retired guest and preserve furniture");h.succeed();
    }
    @GameTest(template="building_test_empty",timeoutTicks=100)
    public static void guestCreationWaitsForEntitiesAndDoesNotDuplicate(GameTestHelper h) {
        var l=h.getLevel();var p=h.absolutePos(new BlockPos(1,2,1));l.setBlock(p,Blocks.OAK_PLANKS.defaultBlockState(),18);
        var seat=new TavernAtmosphere.Seat("test_unique",BlockPos.ZERO,"minecraft:oak_planks",.5,0,0,"客人",List.of("热汤很香。"));var a=new TavernAtmosphere(List.of(seat),List.of(),1);
        h.succeedWhen(()->{TavernUpgrade.ensureGuests(l,p,a);TavernUpgrade.ensureGuests(l,p,a);var entities=l.getEntitiesOfClass(TavernGuestEntity.class,new net.minecraft.world.phys.AABB(p).inflate(2),e->e.seatId().equals("test_unique"));h.assertTrue(entities.size()==1,"Loaded seat must have exactly one patron");});
    }
}
