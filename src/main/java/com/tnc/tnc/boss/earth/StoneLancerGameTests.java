package com.tnc.tnc.boss.earth;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraftforge.gametest.*;

@GameTestHolder("tnc") @PrefixGameTestTemplate(false)
public final class StoneLancerGameTests {
    @GameTest(template="building_test_empty",timeoutTicks=30,batch="stone_lancer")
    public static void bossRegistersAttributesEggAndBoundedPhysicalScale(GameTestHelper h){
        var boss=StoneLancerRegistration.TYPE.create(h.getLevel());
        h.assertTrue(boss!=null&&boss.getMaxHealth()==1000,"Boss has its registered attribute set");
        h.assertTrue(StoneLancerRegistration.EGG instanceof net.minecraftforge.common.ForgeSpawnEggItem,"Spawn egg is registered");
        h.assertTrue(Math.abs(boss.getBbWidth()-29.7)<.01&&Math.abs(boss.getBbHeight()-26.66)<.01&&boss.fireImmune(),"Combat scale is bounded and the rock body resists fire");
        h.assertTrue(boss.getAttributeValue(Attributes.FOLLOW_RANGE)==400,"Boss can detect ranged players");
        h.assertTrue(StoneLancerEntity.moveFor(0,350*350)>=2&&StoneLancerEntity.moveFor(1,150*150)==0,"Distant players get reachable skills instead of short-range spikes or expired lances");
        boss.setHealth(450);h.assertTrue(boss.enraged(),"Half health enables the second combat phase");boss.discard();h.succeed();
    }
    @GameTest(template="building_test_empty",timeoutTicks=30,batch="stone_lancer")
    public static void allFourSkillsHaveSolidFiniteAttacksAndAnEntityBudget(GameTestHelper h){
        var boss=h.spawn(StoneLancerRegistration.TYPE,new BlockPos(2,3,2));boss.setNoAi(true);
        var target=boss.position().add(0,0,18);
        for(int move=0;move<4;move++)StoneLancerCombat.release(boss,move,target);
        h.assertTrue(boss.attacks.size()==23,"Six lances, fifteen spikes, one sky spear and one core orb are created");
        var sky=boss.attacks.stream().filter(e->e.mode()==3).findFirst().orElseThrow();
        h.assertTrue(sky.dropHeight()==190&&sky.height()==250&&sky.scale()==80,"The suspended sky spear has doubled width and length");
        h.assertTrue(boss.attacks.stream().filter(e->e.mode()==0).allMatch(e->e.getY()>=boss.getY()+boss.getBbHeight()+25&&e.scale()==15),"The doubled volley forms above the boss's head");
        h.assertTrue(boss.attacks.stream().allMatch(e->e.noPhysics&&!e.isAttackable()&&e.waitTicks()<30),"Attacks are timed visual entities rather than permanent world blocks");
        for(int i=0;i<6;i++)StoneLancerCombat.release(boss,0,target);
        h.assertTrue(boss.attacks.size()==StoneLancerCombat.MAX_ATTACKS,"Repeated skills cannot exceed the entity budget");
        var attacks=java.util.List.copyOf(boss.attacks);boss.discard();
        h.assertTrue(attacks.stream().allMatch(e->e.isRemoved()),"Removing the boss cleans up its remaining attack entities");h.succeed();
    }
    @GameTest(template="building_test_empty",timeoutTicks=30,batch="stone_lancer")
    public static void earthquakeUsesHugeRadiusWithoutMultiplyingEntityCount(GameTestHelper h){
        var boss=h.spawn(StoneLancerRegistration.TYPE,new BlockPos(2,3,2));boss.setNoAi(true);
        StoneLancerCombat.release(boss,4,boss.position());
        h.assertTrue(boss.attacks.size()==24,"The huge quake keeps a fixed 24-spike budget");
        h.assertTrue(boss.attacks.stream().mapToDouble(a->a.position().subtract(boss.position()).horizontalDistance()).max().orElseThrow()>179,"The final front reaches 180 blocks");
        h.assertTrue(boss.attacks.stream().allMatch(a->a.model().startsWith("stone_lancer_")&&a.scale()>=12&&a.height()>=25),"Boss attacks use the black/gold material and enlarged geometry");
        var enemy=h.spawn(EntityType.VILLAGER,new BlockPos(12,3,2));enemy.setNoAi(true);
        for(int i=0;i<10;i++)boss.tick();
        h.assertTrue(!enemy.isAlive(),"The expanding ground wave actually hits nearby enemies");
        h.assertTrue(StoneLancerEntity.QUAKE_RADIUS==200,"The complete quake spans a 400-block diameter");
        boss.discard();enemy.discard();h.succeed();
    }
    @GameTest(template="building_test_empty",timeoutTicks=30,batch="stone_lancer")
    public static void productionWindupReleasesOnceAndSavedWindupDoesNotStick(GameTestHelper h){
        var boss=h.spawn(StoneLancerRegistration.TYPE,new BlockPos(2,3,2));boss.setNoAi(true);
        var enemy=h.spawn(EntityType.VILLAGER,new BlockPos(6,3,6));enemy.setNoAi(true);boss.setTarget(enemy);
        boss.beginCast(0,enemy.position());h.assertTrue(boss.isCasting(),"The real casting entry sets the synchronized windup flag");
        for(int i=0;i<28;i++)boss.aiStep();
        h.assertTrue(!boss.isCasting()&&boss.attacks.size()==6,"The real windup releases exactly one six-lance volley");
        boss.beginCast(2,enemy.position());var tag=new CompoundTag();boss.addAdditionalSaveData(tag);
        var restored=StoneLancerRegistration.TYPE.create(h.getLevel());restored.readAdditionalSaveData(tag);
        h.assertTrue(!restored.isCasting(),"Reloading a cast cannot leave a permanently frozen pose");
        var seen=new java.util.HashSet<Integer>();for(int i=0;i<6;i++)seen.add(StoneLancerEntity.moveFor(i));
        h.assertTrue(seen.size()==5,"All five skills appear within one six-cast cycle");
        restored.discard();boss.discard();enemy.discard();h.succeed();
    }
}
