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
        h.assertTrue(boss.getBbWidth()<5&&boss.getBbHeight()<10&&boss.fireImmune(),"Combat scale is bounded and the rock body resists fire");
        h.assertTrue(boss.getAttributeValue(Attributes.FOLLOW_RANGE)==88,"Boss can detect ranged players");
        h.assertTrue(StoneLancerEntity.moveFor(0,70*70)>=2&&StoneLancerEntity.moveFor(1,30*30)==0,"Distant players get reachable skills instead of short-range spikes or expired lances");
        boss.setHealth(450);h.assertTrue(boss.enraged(),"Half health enables the second combat phase");boss.discard();h.succeed();
    }
    @GameTest(template="building_test_empty",timeoutTicks=30,batch="stone_lancer")
    public static void allFourSkillsHaveSolidFiniteAttacksAndAnEntityBudget(GameTestHelper h){
        var boss=h.spawn(StoneLancerRegistration.TYPE,new BlockPos(2,3,2));boss.setNoAi(true);
        var target=boss.position().add(0,0,18);
        for(int move=0;move<4;move++)StoneLancerCombat.release(boss,move,target);
        h.assertTrue(boss.attacks.size()==23,"Six lances, fifteen spikes, one sky spear and one core orb are created");
        var sky=boss.attacks.stream().filter(e->e.mode()==3).findFirst().orElseThrow();
        h.assertTrue(sky.dropHeight()==38&&sky.height()==25&&sky.scale()==8,"The visible sky spear is actually suspended above the target");
        h.assertTrue(boss.attacks.stream().allMatch(e->e.noPhysics&&!e.isAttackable()&&e.waitTicks()<30),"Attacks are timed visual entities rather than permanent world blocks");
        for(int i=0;i<6;i++)StoneLancerCombat.release(boss,0,target);
        h.assertTrue(boss.attacks.size()==StoneLancerCombat.MAX_ATTACKS,"Repeated skills cannot exceed the entity budget");
        var attacks=java.util.List.copyOf(boss.attacks);boss.discard();
        h.assertTrue(attacks.stream().allMatch(e->e.isRemoved()),"Removing the boss cleans up its remaining attack entities");h.succeed();
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
        h.assertTrue(seen.size()==4,"Every skill appears within one six-cast cycle");
        restored.discard();boss.discard();enemy.discard();h.succeed();
    }
}
