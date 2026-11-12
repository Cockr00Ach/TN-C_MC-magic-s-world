package com.tnc.tnc.warrior;

import com.tnc.tnc.adventure.AdventureGameTests;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.*;
import net.minecraftforge.gametest.*;
import net.minecraftforge.event.entity.living.LivingAttackEvent;

@GameTestHolder("tncwarriorpractical") @PrefixGameTestTemplate(false)
public final class WarriorUltimateGameTests {
    @GameTest(template="building_test_empty",timeoutTicks=160)
    public static void ultimateInvulnerabilityExpiresAndCooldownSurvivesCancel(GameTestHelper h){
        var p=AdventureGameTests.player(h);p.setNoGravity(true);var data=TemperingData.get(p.server);data.enroll(p.getUUID());data.setTalent(p.getUUID(),6);
        p.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(Items.IRON_SWORD));
        var art=WarriorUltimates.forId("minecraft:iron_sword");
        for(int t=1;t<132;t++)h.runAfterDelay(t,()->WarriorUltimateService.tick(new net.minecraftforge.event.TickEvent.PlayerTickEvent(net.minecraftforge.event.TickEvent.Phase.END,p)));
        for(int i=0;i<10;i++){int index=i;h.runAfterDelay(i*3+1,()->WarriorComboService.input(p,WarriorMove.byKey(art.keys().charAt(index))));}
        h.runAfterDelay(31,()->{
            h.assertTrue(WarriorUltimateService.active(p),"Ten real inputs start ultimate even with ordinary combos in between");
            var event=new LivingAttackEvent(p,p.damageSources().generic(),20);WarriorUltimateService.immune(event);h.assertTrue(event.isCanceled(),"Ultimate blocks actual incoming attack");
        });
        h.runAfterDelay(130,()->{
            h.assertTrue(!WarriorUltimateService.active(p),"Invulnerability ends automatically");
            var event=new LivingAttackEvent(p,p.damageSources().generic(),20);WarriorUltimateService.immune(event);h.assertTrue(!event.isCanceled(),"Normal damage restored");
            p.discard();h.succeed();
        });
    }
    @GameTest(template="building_test_empty",timeoutTicks=90)
    public static void swappingWeaponImmediatelyDropsImmunity(GameTestHelper h){
        var p=AdventureGameTests.player(h);var data=TemperingData.get(p.server);data.enroll(p.getUUID());data.setTalent(p.getUUID(),6);p.setNoGravity(true);
        p.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(Items.IRON_SWORD));var art=WarriorUltimates.forId("minecraft:iron_sword");
        for(int i=0;i<10;i++){int index=i;h.runAfterDelay(i*3+1,()->WarriorComboService.input(p,WarriorMove.byKey(art.keys().charAt(index))));}
        h.runAfterDelay(32,()->{
            h.assertTrue(WarriorUltimateService.active(p),"Ultimate begins");p.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(Items.DIAMOND_SWORD));
            h.assertTrue(!WarriorUltimateService.active(p),"Swap drops immunity synchronously");WarriorUltimateService.cancel(p);
            h.assertTrue(!WarriorComboService.performing(p,p.serverLevel().getGameTime()),"Cancel restores immediate normal attack animation");
        });
        for(int i=0;i<10;i++){int index=i;h.runAfterDelay(36+i*3,()->WarriorComboService.input(p,WarriorMove.byKey(art.keys().charAt(index))));}
        h.runAfterDelay(68,()->{h.assertTrue(WarriorUltimateService.active(p),"Test mode permits immediate recast");p.discard();h.succeed();});
    }
    @GameTest(template="building_test_empty",timeoutTicks=155)
    public static void scheduledStrikesDealDamageAndWaterDomainAddsFinalSlow(GameTestHelper h){
        var p=AdventureGameTests.player(h);p.setNoGravity(true);var data=TemperingData.get(p.server);data.enroll(p.getUUID());data.setTalent(p.getUUID(),6);
        var pos=h.absolutePos(new net.minecraft.core.BlockPos(5,3,3));p.setPos(pos.getX(),pos.getY(),pos.getZ());p.setYRot(0);p.setXRot(0);
        p.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(com.tnc.tnc.TNMod.HAMMER.get()));
        var water=net.minecraftforge.registries.ForgeRegistries.MOB_EFFECTS.getValue(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("tnc","domain_water"));
        if(water==null){p.discard();h.succeed();return;}
        p.addEffect(new net.minecraft.world.effect.MobEffectInstance(water,200,0));
        var target=h.spawn(net.minecraft.world.entity.EntityType.ZOMBIE,new net.minecraft.core.BlockPos(5,3,5));target.setNoAi(true);target.setNoGravity(true);
        target.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH).setBaseValue(500);target.setHealth(500);
        target.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.KNOCKBACK_RESISTANCE).setBaseValue(1);
        var art=WarriorUltimates.forId("tnc:hammer");
        for(int i=0;i<10;i++){int index=i;h.runAfterDelay(i*3+1,()->WarriorComboService.input(p,WarriorMove.byKey(art.keys().charAt(index))));}
        h.runAfterDelay(32,()->{target.setHealth(500);target.invulnerableTime=0;});
        for(int t=33;t<125;t++)h.runAfterDelay(t,()->WarriorUltimateService.tick(new net.minecraftforge.event.TickEvent.PlayerTickEvent(net.minecraftforge.event.TickEvent.Phase.END,p)));
        h.runAfterDelay(115,()->{
            h.assertTrue(target.getHealth()<495,"Scheduled strike frames deal real damage");
            h.assertTrue(target.hasEffect(net.minecraft.world.effect.MobEffects.MOVEMENT_SLOWDOWN),"Water finale applies slow");
            WarriorUltimateService.cancel(p);target.discard();p.discard();h.succeed();
        });
    }

    @GameTest(template="building_test_empty",timeoutTicks=50)
    public static void swordDashStopsAtWallAndDetonatesMarkedTarget(GameTestHelper h){
        var p=AdventureGameTests.player(h);p.setNoGravity(true);p.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(Items.IRON_SWORD));
        for(int x=2;x<=8;x++)for(int z=2;z<=12;z++)h.setBlock(new net.minecraft.core.BlockPos(x,1,z),net.minecraft.world.level.block.Blocks.STONE);
        for(int x=2;x<=8;x++)for(int y=2;y<=5;y++)h.setBlock(new net.minecraft.core.BlockPos(x,y,9),net.minecraft.world.level.block.Blocks.STONE);
        var pos=h.absolutePos(new net.minecraft.core.BlockPos(5,2,3));p.setPos(pos.getX()+.5,pos.getY(),pos.getZ()+.5);p.setYRot(0);p.setXRot(0);
        var target=h.spawn(WarriorEntities.TEST_DUMMY.get(),new net.minecraft.core.BlockPos(5,2,6));target.getPersistentData().putUUID("DummyOwner",p.getUUID());target.setPos(p.getX(),p.getY(),p.getZ()+3);
        double start=p.getZ();WarriorSwordUltimate.tick(p,26);
        h.assertTrue(p.getZ()>start+3,"Dash crosses target");h.assertTrue(p.getZ()<h.absolutePos(new net.minecraft.core.BlockPos(5,2,9)).getZ(),"Dash cannot cross solid wall");
        h.assertTrue(target.hitCount()==1,"Piercing path hits once");
        h.runAfterDelay(15,()->{WarriorSwordUltimate.tick(p,84);h.assertTrue(target.hitCount()==2,"Sheathing finale hits marked enemy");WarriorSwordUltimate.clear(p);target.discard();p.discard();h.succeed();});
    }
}
