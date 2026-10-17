package com.tnc.tnc.magic.wind;

import com.tnc.tnc.magic.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.*;

@GameTestHolder("tnc") @PrefixGameTestTemplate(false)
public final class WindArsenalGameTests {
    @GameTest(template="building_test_empty",timeoutTicks=30,batch="wind_arsenal")
    public static void tiersLaunchTheirRealWeaponAndSynchronizeTheAttackPose(GameTestHelper h){
        var owner=h.spawn(EntityType.VILLAGER,new BlockPos(1,3,1));owner.setNoAi(true);
        var enemy=h.spawn(EntityType.ZOMBIE,new BlockPos(5,3,1));enemy.setNoAi(true);
        var identity=net.minecraftforge.common.util.FakePlayerFactory.getMinecraft(h.getLevel());
        var spirit=TNOrbEntities.WIND_SPIRIT.get().create(h.getLevel());spirit.setPos(owner.position().add(0,2,0));
        var weapons=new java.util.ArrayList<WindWeaponEntity>();var forms=new java.util.HashSet<Integer>();
        for(int tier=1;tier<=5;tier++)for(int slot=0;slot<(tier==5?3:1);slot++){
            spirit.bind(identity,slot,tier,0);var weapon=WindArsenal.attack(spirit,owner);weapons.add(weapon);
            h.assertTrue(weapon!=null&&weapon.tier()==tier,"Every tier launches its own real projectile");
            if(tier<=4)h.assertTrue(weapon.form()==new int[]{0,0,0,1,2}[tier],"Orb, sword and spear tiers select the intended model");
            else forms.add(weapon.form());
            h.assertTrue(spirit.windWeaponForm()==weapon.form()&&spirit.windAttackTime()==h.getLevel().getGameTime(),"The escort lunge uses the same form and time as its actual attack");
            h.assertTrue(spirit.windAttackDirection().dot(weapon.getDeltaMovement().normalize())>.999,"The synchronized pose points along the projectile direction");
        }
        h.assertTrue(forms.size()==3,"The highest tier uses sword, spear and chakram");
        weapons.forEach(WindWeaponEntity::discard);spirit.discard();owner.discard();enemy.discard();h.succeed();
    }
    @GameTest(template="building_test_empty",timeoutTicks=30,batch="wind_arsenal")
    public static void realWeaponHitsOnceAndPullsInThreeDimensions(GameTestHelper h){
        var owner=h.spawn(EntityType.VILLAGER,new BlockPos(1,3,1));owner.setNoAi(true);
        var enemy=h.spawn(EntityType.ZOMBIE,new BlockPos(5,3,1));enemy.setNoAi(true);
        var spirit=TNOrbEntities.WIND_SPIRIT.get().create(h.getLevel());spirit.setPos(owner.position().add(0,2,0));
        float health=enemy.getHealth();var weapon=WindArsenal.attack(spirit,owner);
        h.assertTrue(weapon!=null&&enemy.getHealth()==health,"Attacking launches a registered weapon without instant duplicate damage");
        h.assertTrue(enemy.getDeltaMovement().x<0&&enemy.getDeltaMovement().y>0,"Wind pulls toward the elevated core in all three dimensions");
        for(int i=0;i<8&&!weapon.isRemoved();i++)weapon.tick();
        h.assertTrue(weapon.isRemoved()&&enemy.getHealth()<health,"The swept projectile hits and is consumed");
        float after=enemy.getHealth();weapon.tick();h.assertTrue(enemy.getHealth()==after,"A consumed projectile cannot deal a second hit");
        owner.discard();enemy.discard();spirit.discard();h.succeed();
    }
    @GameTest(template="building_test_empty",timeoutTicks=30,batch="wind_arsenal")
    public static void wallStopsFastWeaponsAndOwnerDeathCleansThemUp(GameTestHelper h){
        var owner=h.spawn(EntityType.VILLAGER,new BlockPos(1,3,1));owner.setNoAi(true);
        var enemy=h.spawn(EntityType.ZOMBIE,new BlockPos(5,3,1));enemy.setNoAi(true);
        h.setBlock(new BlockPos(3,3,1),Blocks.STONE);h.setBlock(new BlockPos(3,4,1),Blocks.STONE);
        var weapon=WindArsenalRegistration.WEAPON.create(h.getLevel());
        weapon.configure(owner,owner.position().add(0,.8,0),new Vec3(1,0,0),2,5,10);
        float before=enemy.getHealth();for(int i=0;i<8&&!weapon.isRemoved();i++)weapon.tick();
        h.assertTrue(weapon.isRemoved()&&enemy.getHealth()==before,"A fast spear cannot tunnel through solid blocks");
        var orphan=WindArsenalRegistration.WEAPON.create(h.getLevel());orphan.configure(owner,owner.position().add(0,4,0),new Vec3(0,0,1),1,3,4);
        owner.discard();orphan.tick();h.assertTrue(orphan.isRemoved(),"Removing the caster removes its remaining weapons");enemy.discard();h.succeed();
    }
    @GameTest(template="building_test_empty",timeoutTicks=30,batch="wind_arsenal")
    public static void repeatedCastsHaveAFixedBudgetAndFiniteLifetime(GameTestHelper h){
        // This empty template is small. Keep the ranged fixture above adjacent tests' walls.
        var owner=h.spawn(EntityType.VILLAGER,new BlockPos(1,30,1));owner.setNoAi(true);
        var enemy=h.spawn(EntityType.ZOMBIE,new BlockPos(5,30,1));enemy.setNoAi(true);
        h.assertTrue(owner.hasLineOfSight(enemy),"The budget fixture has an unobstructed target");
        var spirit=TNOrbEntities.WIND_SPIRIT.get().create(h.getLevel());spirit.setPos(owner.position().add(0,2,0));
        var list=new java.util.ArrayList<WindWeaponEntity>();
        for(int i=0;i<30;i++){var w=WindArsenal.attack(spirit,owner);if(w!=null)list.add(w);}
        h.assertTrue(list.size()==WindArsenalRules.MAX_PROJECTILES,"Repeated casting fills the 16-projectile budget; actual="+list.size());
        for(var w:list)w.discard();
        var ttl=WindArsenalRegistration.WEAPON.create(h.getLevel());ttl.configure(owner,owner.position().add(0,8,0),new Vec3(0,1,0),0,1,1);
        for(int i=0;i<WindArsenalRules.LIFE+1&&!ttl.isRemoved();i++)ttl.tick();
        h.assertTrue(ttl.isRemoved(),"A missed projectile expires");owner.discard();enemy.discard();spirit.discard();h.succeed();
    }
}
