package com.tnc.tnc.life;

import com.tnc.tnc.TNMod;
import com.tnc.tnc.adventure.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.*;
import net.minecraftforge.gametest.*;

@GameTestHolder("tnc") @PrefixGameTestTemplate(false)
public final class LifeGameTests {
    @GameTest(template="building_test_empty",timeoutTicks=30)
    public static void vanillaBossDeathsDropSeparateCuisineIngredients(GameTestHelper h){
        var player=AdventureGameTests.player(h);var level=h.getLevel();var pos=h.absolutePos(new net.minecraft.core.BlockPos(2,2,2));
        var dragon=new net.minecraft.world.entity.boss.enderdragon.EnderDragon(net.minecraft.world.entity.EntityType.ENDER_DRAGON,level);
        dragon.setPos(pos.getX(),pos.getY(),pos.getZ());dragon.setNoAi(true);level.addFreshEntity(dragon);
        dragon.hurt(dragon.head,player.damageSources().playerAttack(player),10000);
        var wither=new net.minecraft.world.entity.boss.wither.WitherBoss(net.minecraft.world.entity.EntityType.WITHER,level);
        wither.setPos(pos.getX(),pos.getY(),pos.getZ());wither.setNoAi(true);level.addFreshEntity(wither);
        wither.hurt(player.damageSources().playerAttack(player),10000);
        var drops=level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,new net.minecraft.world.phys.AABB(pos).inflate(20));
        for(int i=0;i<2;i++){final int ingredient=i;h.assertTrue(drops.stream().filter(e->e.getItem().is(TNMod.BOSS_INGREDIENTS.get(ingredient).get())).mapToInt(e->e.getItem().getCount()).sum()==1,"Real vanilla boss death supplies one independent ingredient: "+i);}
        h.assertTrue(drops.stream().anyMatch(e->e.getItem().is(Items.NETHER_STAR)),"Cuisine does not consume the wither's original reward");
        dragon.discard();wither.discard();h.succeed();
    }
    @GameTest(template="building_test_empty",batch="farm",timeoutTicks=100)
    public static void actualFarmCastHasBoundedGrowthManaCostAndPersistentCooldown(GameTestHelper h){
        var p=AdventureGameTests.player(h);var center=h.absolutePos(new net.minecraft.core.BlockPos(2,2,2));
        // Other batches can leave tall structures around this fixture. Supply real light
        // before planting so crops survive without depending on an unobstructed sky.
        for(int x:new int[]{-2,2})for(int z:new int[]{-2,2})h.getLevel().setBlockAndUpdate(center.offset(x,1,z),net.minecraft.world.level.block.Blocks.GLOWSTONE.defaultBlockState());
        h.runAfterDelay(5,()->{
        for(int x=-2;x<=2;x++)for(int z=-2;z<=2;z++){
            var pos=center.offset(x,0,z);h.getLevel().setBlockAndUpdate(pos.below(),net.minecraft.world.level.block.Blocks.FARMLAND.defaultBlockState().setValue(net.minecraft.world.level.block.FarmBlock.MOISTURE,7));h.getLevel().setBlockAndUpdate(pos,net.minecraft.world.level.block.Blocks.WHEAT.defaultBlockState());
        }
        p.setPos(center.getX()+.5,center.getY()+3,center.getZ()+.5);p.setXRot(90);p.setYRot(0);
        for(int x=-2;x<=2;x++)for(int z=-2;z<=2;z++){var pos=center.offset(x,0,z);var state=h.getLevel().getBlockState(pos);h.assertTrue(state.is(net.minecraft.world.level.block.Blocks.WHEAT)&&state.canSurvive(h.getLevel(),pos),"Pre-cast farm invalid at "+pos+" crop="+state+" soil="+h.getLevel().getBlockState(pos.below())+" light="+h.getLevel().getRawBrightness(pos,0)+" sky="+h.getLevel().canSeeSky(pos));}
        var magic=com.tnc.tnc.magic.MagicStone.getOrNull(p);magic.assignDefaultAffinities(3);magic.setMana(magic.getMaxMana());int mana=magic.getMana();FarmMagic.learn(p);
        String result=FarmMagic.cast(p,1);h.assertTrue(result.contains("已施放"),"Real raycast selects farm: "+result);
        int grown=0;for(int x=-2;x<=2;x++)for(int z=-2;z<=2;z++){var state=h.getLevel().getBlockState(center.offset(x,0,z));h.assertTrue(state.is(net.minecraft.world.level.block.Blocks.WHEAT),"Farm fixture stays wheat at "+x+","+z);if(state.getValue(net.minecraft.world.level.block.CropBlock.AGE)>0)grown++;}
        h.assertTrue(grown==9&&magic.getMana()==mana-15,"Exactly 3x3 advances once and costs 15 mana");FarmMagic.cast(p,1);h.assertTrue(magic.getMana()==mana-15,"Cooldown rejects repeat cast without debit");
        var saved=LifeSavedData.get(p.server);var account=saved.account(p.getUUID());h.assertTrue(LifeSavedData.load(saved.save(new CompoundTag())).account(p.getUUID()).farmCooldown==account.farmCooldown,"Cooldown survives restart");h.succeed();
        });
    }
    @GameTest(template="building_test_empty",timeoutTicks=30)
    public static void equipmentFoodSlotsAndFullInventoryCoins(GameTestHelper h){
        var p=AdventureGameTests.player(h);var a=LifeSavedData.get(p.server).account(p.getUUID());var menu=new TravelBagMenu(1,p.getInventory(),a.bag);
        h.assertTrue(!menu.slots.get(0).mayPlace(new ItemStack(Items.BREAD))&&!menu.slots.get(2).mayPlace(new ItemStack(Items.BREAD)),"Food requires equipped pouch and cannot equip as money pouch");
        a.bag.setItem(0,new ItemStack(TNMod.MONEY_POUCH.get()));a.bag.setItem(1,new ItemStack(TNMod.FOOD_POUCH.get()));
        h.assertTrue(menu.slots.size()==43&&menu.slots.get(6).mayPlace(new ItemStack(Items.BREAD))&&!menu.slots.get(6).mayPlace(new ItemStack(Items.STONE)),"Exactly five food slots, legitimate food only");
        var account=AdventureService.profile(p);account.credit(100000,"test");for(int i=0;i<36;i++)p.getInventory().setItem(i,new ItemStack(Items.STONE,64));menu.clickMenuButton(p,12);
        h.assertTrue(account.coins()==100000,"Full pack cannot debit gold withdrawal");
        p.getInventory().setItem(0,ItemStack.EMPTY);new TravelBagMenu(2,p.getInventory(),a.bag).clickMenuButton(p,12);
        h.assertTrue(account.coins()==90000&&p.getInventory().getItem(0).is(TNMod.GOLD_COIN.get()),"One tangible coin debits exact denomination");
        new TravelBagMenu(3,p.getInventory(),a.bag).clickMenuButton(p,13);h.assertTrue(account.coins()==100000&&p.getInventory().getItem(0).isEmpty(),"Deposit consumes physical coin once");h.succeed();
    }
    @GameTest(template="building_test_empty",timeoutTicks=100)
    public static void quickMealsReturnContainersAndCannotResetCooldown(GameTestHelper h){
        var p=AdventureGameTests.player(h);var store=LifeSavedData.get(p.server);var a=store.account(p.getUUID());a.bag.setItem(1,new ItemStack(TNMod.FOOD_POUCH.get()));a.bag.setItem(2,new ItemStack(Items.MUSHROOM_STEW));a.bag.setItem(3,new ItemStack(Items.MUSHROOM_STEW));p.getFoodData().setFoodLevel(0);
        new TravelBagMenu(1,p.getInventory(),a.bag).clickMenuButton(p,0);
        h.assertTrue(a.bag.getItem(2).isEmpty()&&p.getInventory().contains(new ItemStack(Items.BOWL)),"Actual stew consumption returns a bowl");
        new TravelBagMenu(2,p.getInventory(),a.bag).clickMenuButton(p,1);
        h.assertTrue(!a.bag.getItem(3).isEmpty(),"Reopening menu cannot bypass meal duration");
        var loaded=LifeSavedData.load(store.save(new CompoundTag())).account(p.getUUID());h.assertTrue(loaded.nextMealTick==a.nextMealTick&&!loaded.bag.getItem(3).isEmpty(),"Food and duration survive persistence");
        h.runAfterDelay(40,()->{new TravelBagMenu(3,p.getInventory(),a.bag).clickMenuButton(p,1);h.assertTrue(a.bag.getItem(3).isEmpty()&&p.getInventory().getItem(0).getCount()==2,"Next meal returns second bowl");h.succeed();});
    }
    @GameTest(template="building_test_empty",timeoutTicks=30)
    public static void distinctDelicaciesAddHealthOnceAndRespawnKeepsEntitlement(GameTestHelper h){
        var p=AdventureGameTests.player(h);double base=p.getMaxHealth();LifeEvents.taste(p,TNMod.DELICACIES.get(0).get());LifeEvents.taste(p,TNMod.DELICACIES.get(0).get());LifeEvents.taste(p,TNMod.DELICACIES.get(1).get());
        h.assertTrue(p.getMaxHealth()==base+4,"Two distinct feasts add four health, duplicate adds none");
        for(var feast:TNMod.DELICACIES){LifeEvents.taste(p,feast.get());LifeEvents.taste(p,feast.get());}
        h.assertTrue(p.getMaxHealth()==base+16,"All eight unique feasts cap the entitlement at sixteen health");
        LifeEvents.respawn(new net.minecraftforge.event.entity.player.PlayerEvent.PlayerRespawnEvent(p,false));
        h.assertTrue(p.getMaxHealth()==base+16&&p.getFoodData().getFoodLevel()==6&&p.getFoodData().getSaturationLevel()==0,"True respawn preserves feast health and sets 30 percent hunger");
        p.getFoodData().setFoodLevel(13);LifeEvents.respawn(new net.minecraftforge.event.entity.player.PlayerEvent.PlayerRespawnEvent(p,true));h.assertTrue(p.getFoodData().getFoodLevel()==13,"End return is not death");h.succeed();
    }
}
