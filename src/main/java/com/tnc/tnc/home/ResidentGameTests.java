package com.tnc.tnc.home;

import com.tnc.tnc.adventure.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.*;
import net.minecraftforge.gametest.*;

@GameTestHolder("tnc") @PrefixGameTestTemplate(false)
public final class ResidentGameTests {
    @GameTest(template="building_test_empty",batch="residents",timeoutTicks=30)
    public static void giftAndSharedFeastReturnBothVesselsWithoutDuplicateHealth(GameTestHelper h){
        var p=AdventureGameTests.player(h);var def=ResidentService.ALL.get(0);var npc=com.tnc.tnc.npc.TNNpcs.RESIDENT.get().create(h.getLevel());npc.identity(def.id());
        var feast=com.tnc.tnc.TNMod.DELICACIES.get(0).get();p.getInventory().setItem(0,new ItemStack(feast,3));p.getFoodData().setFoodLevel(0);double base=p.getMaxHealth();
        ResidentService.food(p,npc,def,false);h.assertTrue(p.getInventory().getItem(0).getCount()==2&&p.getInventory().getItem(1).is(Items.BOWL)&&p.getMaxHealth()==base,"NPC gift returns bowl without applying feast to player");
        ResidentService.food(p,npc,def,true);h.assertTrue(p.getInventory().getItem(0).isEmpty()&&p.getInventory().getItem(1).getCount()==3&&p.getMaxHealth()==base+2,"Shared two portions return two more bowls; only player's eaten portion grants health");
        var other=AdventureGameTests.player(h);for(int i=0;i<36;i++)other.getInventory().setItem(i,new ItemStack(Items.STONE,64));other.getInventory().setItem(0,new ItemStack(feast,2));ResidentService.food(other,npc,def,false);
        h.assertTrue(other.getInventory().getItem(0).getCount()==2&&ResidentService.relation(other,def.id()).getInt("Gifts")==0,"No space for gift vessel leaves food and gift quota intact");h.succeed();
    }
    @GameTest(template="building_test_empty",batch="residents",timeoutTicks=30)
    public static void giftsSpendOfferedStackPreserveQuotaAndSeparatePlayers(GameTestHelper h){
        var a=AdventureGameTests.player(h);var b=AdventureGameTests.player(h);var def=ResidentService.ALL.get(0);var npc=com.tnc.tnc.npc.TNNpcs.RESIDENT.get().create(h.getLevel());npc.identity(def.id());
        a.getInventory().selected=5;a.getInventory().setItem(0,new ItemStack(Items.BREAD,20));a.getInventory().setItem(5,new ItemStack(Items.BREAD,3));
        ResidentService.food(a,npc,def,false);var r=ResidentService.relation(a,def.id());int first=r.getInt("Affection");ResidentService.food(a,npc,def,false);ResidentService.food(a,npc,def,false);
        h.assertTrue(a.getInventory().getItem(5).getCount()==1&&a.getInventory().getItem(0).getCount()==20&&r.getInt("Affection")==first+first/2,"Only offered food spent, duplicate dish diminishing, third gift blocked");
        h.assertTrue(ResidentService.relation(b,def.id()).getInt("Affection")==0,"Relationship belongs to each player independently");
        var store=AdventureSavedData.get(a.server);var restored=AdventureSavedData.load(store.save(new CompoundTag()));var saved=restored.housing.getCompound("Relations").getCompound(a.getUUID()+":"+def.id());h.assertTrue(saved.getInt("Gifts")==2,"Reload preserves interaction quota");
        store.activeTicks+=24000;ResidentService.food(a,npc,def,false);h.assertTrue(a.getInventory().getItem(5).isEmpty()&&r.getInt("Gifts")==1,"Active-time window resets after actual gameplay, not sleep/login");h.succeed();
    }
    @GameTest(template="building_test_empty",batch="residents",timeoutTicks=30)
    public static void invitationRequiresAdultReadinessAndRealHome(GameTestHelper h){
        var a=AdventureGameTests.player(h);var npc=com.tnc.tnc.npc.TNNpcs.RESIDENT.get().create(h.getLevel());npc.identity("lin");var r=ResidentService.relation(a,"lin");
        h.assertTrue(!npc.isBaby()&&ResidentService.invite(a,npc).contains("还没准备"),"Adult status alone does not imply consent");
        r.putInt("Affection",80);var dishes=new net.minecraft.nbt.ListTag();for(String s:java.util.List.of("bread","carrot","stew"))dishes.add(net.minecraft.nbt.StringTag.valueOf(s));r.put("Dishes",dishes);r.putInt("Meals",3);
        h.assertTrue(ResidentService.invite(a,npc).contains("购买并装修"),"Readiness without owned furnished home does not teleport resident");h.succeed();
    }
}
