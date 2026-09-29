package com.tnc.tnc.adventure;

import com.tnc.tnc.npc.TNNpcs;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.gametest.*;

@GameTestHolder("tnc") @PrefixGameTestTemplate(false)
public final class TownCounterGameTests {
    @GameTest(template="building_test_empty",timeoutTicks=40)
    public static void clickedCounterWinsEvenWhenSeveralWorkersAreNearby(GameTestHelper h){
        var player=AdventureGameTests.player(h);player.teleportTo(h.absolutePos(new BlockPos(4,2,4)).getX(),h.absolutePos(new BlockPos(4,2,4)).getY(),h.absolutePos(new BlockPos(4,2,4)).getZ());
        for(var role:new String[]{"armorer","smith","broker","guild"}){
            var npc=TNNpcs.SERVICE_NPC.get().create(h.getLevel());npc.role(role);npc.moveTo(player.getX(),player.getY(),player.getZ(),0,0);h.getLevel().addFreshEntity(npc);
        }
        for(var role:new String[]{"broker","smith","armorer","guild"}){
            var tag=AdventureService.snapshot(player,"",role);
            h.assertTrue(tag.getString("ServiceRole").equals(role),"Clicked counter is explicit, nearby workers cannot change it: "+role);
        }
        h.assertTrue(AdventureService.snapshot(player,"","profile").getString("ServiceRole").equals("profile"),"Handbook stays personal even beside a counter");h.succeed();
    }
    @GameTest(template="building_test_empty",timeoutTicks=40)
    public static void oldWorkerMovesInsideOnceWithoutChangingUuidOrDuplicating(GameTestHelper h){
        var level=h.getLevel();var origin=h.absolutePos(BlockPos.ZERO);
        for(int x=3;x<=5;x++)for(int z=3;z<=5;z++)h.setBlock(new BlockPos(x,1,z),Blocks.OAK_PLANKS);
        var post=new TownServices.Post("broker","Test bank",new BlockPos(4,2,4),VillagerProfession.CARTOGRAPHER,new TownServices.Room(3,2,3,5,5),0);
        var npc=TNNpcs.SERVICE_NPC.get().create(level);npc.role("broker");var old=origin.offset(1,2,4);npc.moveTo(old.getX()+.5,old.getY(),old.getZ()+.5,180,0);level.addFreshEntity(npc);
        var record=new CompoundTag();record.putUUID("UUID",npc.getUUID());record.putLong("Pos",old.asLong());var uuid=npc.getUUID();
        h.assertTrue(TownServices.ensurePost(level,origin,post,record),"Old door position migrates");
        h.assertTrue(npc.getUUID().equals(uuid)&&npc.blockPosition().equals(origin.offset(4,2,4)),"Original entity is placed at interior counter");
        npc.teleportTo(origin.getX()+3.5,origin.getY()+2,origin.getZ()+3.5);
        h.assertTrue(!TownServices.ensurePost(level,origin,post,record)&&npc.blockPosition().equals(origin.offset(3,2,3)),"Later author positioning is preserved after one migration");
        h.assertTrue(level.getEntitiesOfClass(TownServiceNpc.class,new net.minecraft.world.phys.AABB(origin,origin.offset(8,5,8)),n->n.role().equals("broker")).size()==1,"No duplicate worker");h.succeed();
    }
    @GameTest(template="building_test_empty",timeoutTicks=40)
    public static void blockedInteriorNeverFallsBackToDoorOrStreet(GameTestHelper h){
        var origin=h.absolutePos(BlockPos.ZERO);
        var post=new TownServices.Post("smith","Test wand shop",new BlockPos(4,2,4),VillagerProfession.LIBRARIAN,new TownServices.Room(4,2,4,4,4),0);
        h.setBlock(new BlockPos(4,1,4),Blocks.OAK_PLANKS);h.setBlock(new BlockPos(4,2,4),Blocks.STONE);
        h.setBlock(new BlockPos(3,1,4),Blocks.OAK_PLANKS);var record=new CompoundTag();
        h.assertTrue(TownServices.standing(h.getLevel(),origin,post)==null&&!TownServices.ensurePost(h.getLevel(),origin,post,record)&&!record.hasUUID("UUID"),"Wait for a usable interior instead of spawning outside");h.succeed();
    }
}
