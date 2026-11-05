package com.tnc.tnc.warrior.forge;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.*;
import net.minecraft.world.phys.*;
import net.minecraftforge.gametest.*;

@GameTestHolder("tncwarrior")
@PrefixGameTestTemplate(false)
public final class ForgeGameTests {
    private ForgeGameTests(){}
    private static ServerPlayer player(GameTestHelper h){return com.tnc.tnc.adventure.AdventureGameTests.player(h);}
    private static WarriorForgeEntity place(GameTestHelper h,BlockPos relative,net.minecraft.world.level.block.Block block){
        h.setBlock(relative,block);return (WarriorForgeEntity)h.getLevel().getBlockEntity(h.absolutePos(relative));
    }
    private static BlockHitResult hit(WarriorForgeEntity be){return new BlockHitResult(Vec3.atCenterOf(be.getBlockPos()).add(0,.3,0),Direction.UP,be.getBlockPos(),false);}
    private static void aim(ServerPlayer p,WarriorForgeEntity be,int zone){
        var pos=be.getBlockPos();p.setPos(pos.getX()+.15+zone/3.,pos.getY()+2,pos.getZ()+.5);p.setXRot(90);p.setYRot(0);
    }
    @GameTest(template="building_test_empty",timeoutTicks=30)
    public static void independentStationsAndRecipesLoad(GameTestHelper h){
        for(String id:new String[]{"warrior_hearth","warrior_anvil","warrior_quench_bath","warrior_forge_hammer","warrior_sword_grip","warrior_long_grip","warrior_mace_grip"})
            h.assertTrue(h.getLevel().getRecipeManager().byKey(WarriorForgeContent.id(id)).isPresent(),"Survival recipe "+id);
        var be=place(h,new BlockPos(2,2,2),WarriorForgeContent.ANVIL);
        h.assertTrue(be.kind()==WarriorForgeBlock.Kind.ANVIL,"Own independent anvil entity");h.succeed();
    }
    @GameTest(template="building_test_empty",timeoutTicks=30)
    public static void fullPhysicalCraftConsumesInputsOnceAndPreservesAllScores(GameTestHelper h){
        var p=player(h);var hearth=place(h,new BlockPos(2,2,2),WarriorForgeContent.HEARTH);
        var anvil=place(h,new BlockPos(4,2,2),WarriorForgeContent.ANVIL);
        var bath=place(h,new BlockPos(6,2,2),WarriorForgeContent.BATH);
        aim(p,hearth,1);p.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(Items.IRON_INGOT,4));p.getInventory().setItem(1,new ItemStack(Items.COAL));
        hearth.interact(p,hit(hearth));h.assertTrue(!hearth.work.isEmpty(),"Real material starts work");
        h.assertTrue(p.getMainHandItem().isEmpty()&&p.getInventory().getItem(1).isEmpty(),"Iron and coal consumed once");
        hearth.session.heat=750;hearth.interact(p,hit(hearth));
        h.assertTrue(p.getMainHandItem().is(WarriorForgeContent.BILLET)&&hearth.work.isEmpty(),"Only one transferable billet");
        aim(p,anvil,1);h.assertTrue(anvil.accept(p,p.getMainHandItem()),"Hot billet placed on anvil");
        p.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(WarriorForgeContent.HAMMER));
        var s=anvil.session;
        for(int i=0;i<12;i++){
            for(int t=0;t<28;t++)s.tick();aim(p,anvil,s.pattern.zone(i));anvil.release(p,s.pattern.heavy(i)?16:3);
        }
        h.assertTrue(s.strokes==12,"All twelve server strikes resolved");
        p.setItemInHand(InteractionHand.MAIN_HAND,ItemStack.EMPTY);anvil.interact(p,hit(anvil));
        aim(p,bath,1);bath.water=10;h.assertTrue(bath.accept(p,p.getMainHandItem()),"Shaped billet reaches bath");
        p.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(WarriorForgeContent.HAMMER));bath.pose=0;bath.release(p,80);
        h.assertTrue(bath.water==9&&bath.session.quenchScore==100,"Real quench uses exactly one bath charge");
        p.setItemInHand(InteractionHand.MAIN_HAND,ItemStack.EMPTY);bath.interact(p,hit(bath));
        aim(p,anvil,1);h.assertTrue(anvil.accept(p,p.getMainHandItem()),"Quenched head returns for assembly");
        p.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(WarriorForgeContent.HAMMER));p.getInventory().setItem(1,new ItemStack(WarriorForgeContent.SWORD_GRIP));anvil.release(p,20);
        h.assertTrue(ForgeWeaponData.quality(anvil.work)==ForgeQuality.PRIMORDIAL,"Five perfect processes produce primordial quality");
        h.assertTrue(p.getInventory().getItem(1).isEmpty(),"One grip consumed");
        p.setItemInHand(InteractionHand.MAIN_HAND,ItemStack.EMPTY);anvil.take(p);var id=p.getMainHandItem().getTag().getCompound(ForgeWeaponData.KEY).getUUID("WeaponId");
        h.assertTrue(ForgeWeaponData.name(p,p.getMainHandItem(),id,"照夜"),"Maker can name divine weapon");
        h.assertTrue(!ForgeWeaponData.name(p,p.getMainHandItem(),id,"复制"),"Repeat naming packet cannot reissue a reward");
        h.assertTrue(anvil.work.isEmpty()&&anvil.session==null,"Claim empties station before returning result");
        p.discard();h.succeed();
    }
    @GameTest(template="building_test_empty",timeoutTicks=30)
    public static void coldIncompleteWorkCanBeTakenBackWithoutResettingMistakes(GameTestHelper h){
        var p=player(h);var anvil=place(h,new BlockPos(2,2,2),WarriorForgeContent.ANVIL);aim(p,anvil,1);
        var billet=new ItemStack(WarriorForgeContent.BILLET);billet.getOrCreateTag().putUUID("Maker",p.getUUID());
        var s=new ForgeSession(ForgePattern.JIAN);s.heat=750;s.takeFromFire();s.strike(2,2);s.heat=620;
        billet.getOrCreateTag().put(ForgeJobData.KEY,ForgeJobData.save(s));p.setItemInHand(InteractionHand.MAIN_HAND,billet);anvil.accept(p,billet);
        p.setItemInHand(InteractionHand.MAIN_HAND,ItemStack.EMPTY);anvil.interact(p,hit(anvil));
        h.assertTrue(anvil.work.isEmpty()&&p.getMainHandItem().is(WarriorForgeContent.BILLET),"Cold unfinished work is retrievable");
        var hearth=place(h,new BlockPos(4,2,2),WarriorForgeContent.HEARTH);aim(p,hearth,1);hearth.accept(p,p.getMainHandItem());
        h.assertTrue(hearth.session.heat==750&&hearth.session.strokes==1&&hearth.session.strikeTotal<50,"Reheat recovers heat but preserves earlier strike and error");
        p.discard();h.succeed();
    }
    @GameTest(template="building_test_empty",timeoutTicks=30)
    public static void processSaveReloadAndOwnershipNeverCloneTheWorkpiece(GameTestHelper h){
        var p=player(h);var be=place(h,new BlockPos(2,2,2),WarriorForgeContent.ANVIL);aim(p,be,1);
        var s=new ForgeSession(ForgePattern.MACE);s.heat=750;s.takeFromFire();s.strike(2,2);
        be.work=new ItemStack(WarriorForgeContent.BILLET);be.owner=p.getUUID();be.session=s;be.changed();
        var saved=be.saveWithoutMetadata();var restored=new WarriorForgeEntity(be.getBlockPos(),be.getBlockState());restored.load(saved);
        h.assertTrue(restored.owner.equals(p.getUUID())&&restored.session.strokes==1&&restored.session.strikeTotal==s.strikeTotal,"Pause/reload preserves owner and scores");
        var other=java.util.UUID.randomUUID();var stranger=new ItemStack(WarriorForgeContent.BILLET);stranger.getOrCreateTag().putUUID("Maker",other);stranger.getOrCreateTag().put(ForgeJobData.KEY,ForgeJobData.save(s));
        h.assertTrue(!be.accept(p,stranger)&&stranger.getCount()==1,"Occupied/foreign work not consumed");
        p.discard();h.succeed();
    }
    @GameTest(template="building_test_empty",timeoutTicks=30)
    public static void normalHammerInteractionReachesItemUseAndOnlyForgedWeaponsChangeDurability(GameTestHelper h){
        var p=player(h);var be=place(h,new BlockPos(2,2,2),WarriorForgeContent.ANVIL);aim(p,be,1);
        p.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(WarriorForgeContent.HAMMER));
        var result=be.getBlockState().use(h.getLevel(),p,InteractionHand.MAIN_HAND,hit(be));
        h.assertTrue(result==net.minecraft.world.InteractionResult.PASS,"Block must not swallow hammer held-use");
        be.owner=p.getUUID();be.work=new ItemStack(WarriorForgeContent.BILLET);
        be.session=new ForgeSession(ForgePattern.JIAN);be.session.heat=750;be.session.takeFromFire();aim(p,be,0);
        var aim=(BlockHitResult)p.pick(5,1,false);
        WarriorForgeContent.HAMMER.useOn(new net.minecraft.world.item.context.UseOnContext(p,InteractionHand.MAIN_HAND,aim));
        h.assertTrue(p.isUsingItem(),"Vanilla item use really starts server-measured hammer hold");
        p.releaseUsingItem();
        h.assertTrue(be.session.strokes==1,"Vanilla release really resolves one light hammer strike");
        var normal=new ItemStack(com.tnc.tnc.TNMod.HAN_IRON_JIAN.get());var forged=normal.copy();var data=new net.minecraft.nbt.CompoundTag();data.putInt("Quality",6);forged.getOrCreateTag().put(ForgeWeaponData.KEY,data);
        h.assertTrue(normal.getMaxDamage()==850&&forged.getMaxDamage()==1360,"Quality durability hook applies only to forged item");
        p.discard();h.succeed();
    }
    @GameTest(template="building_test_empty",timeoutTicks=30)
    public static void emptyBathCannotUndoSuccessfulQuenching(GameTestHelper h){
        var p=player(h);var be=place(h,new BlockPos(2,2,2),WarriorForgeContent.BATH);aim(p,be,1);
        be.owner=p.getUUID();be.work=new ItemStack(WarriorForgeContent.BILLET);
        be.session=new ForgeSession(ForgePattern.JIAN);be.session.stage=ForgeSession.Stage.ASSEMBLY;be.session.quenched=true;be.session.quenchScore=97;be.water=0;
        p.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(WarriorForgeContent.HAMMER));
        h.assertTrue(!be.beginHold(p)&&be.session.stage==ForgeSession.Stage.ASSEMBLY&&be.session.quenchScore==97,"Empty bath does not undo existing successful quench");
        p.discard();h.succeed();
    }
}
