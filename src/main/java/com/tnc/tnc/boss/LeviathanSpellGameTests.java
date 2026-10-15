package com.tnc.tnc.boss;

import com.tnc.tnc.magic.water.*;
import com.tnc.tnc.npc.TNNpcs;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.gametest.*;

@GameTestHolder("tncleviathan")
@PrefixGameTestTemplate(false)
public final class LeviathanSpellGameTests {
    private static TNWaterLeviathanEntity boss(GameTestHelper h) {
        var boss=h.spawn(TNNpcs.WATER_LEVIATHAN.get(),new BlockPos(20,4,8));
        boss.setNoAi(true); return boss;
    }

    private static LivingEntity target(GameTestHelper h) {
        var target=h.spawn(EntityType.VILLAGER,new BlockPos(20,4,28));
        target.setNoAi(true); target.setNoGravity(true);
        target.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1000);
        target.setHealth(1000); return target;
    }

    @GameTest(template="leviathan_empty",timeoutTicks=65)
    public static void realTsunamiHitsEnemiesButNotTheCaster(GameTestHelper h) {
        var boss=boss(h); var enemy=target(h); float hp=boss.getHealth();
        var player=h.makeMockSurvivalPlayer();
        h.assertTrue(WaterSpellRules.enemy(boss,player),"Boss water spells must hit survival players");
        h.assertTrue(!WaterSpellRules.enemy(boss,boss),"Boss cannot hit itself");
        h.assertTrue(!WaterSpellRules.enemy(player,enemy),"Player water cannot start harming friendly villagers");
        h.assertTrue(LeviathanSpells.cast(boss,enemy,"tsunami"),"T4 tsunami entity created");
        h.runAtTickTime(40,()->{
            h.assertTrue(enemy.getHealth()<1000 && boss.getHealth()==hp,
                    "Real boss-owned wave must persist and damage enemies without hitting its owner");
            boss.discard(); enemy.discard(); h.succeed();
        });
    }

    @GameTest(template="leviathan_empty",timeoutTicks=130)
    public static void realBarrageAndDragonRuinUseMobOwnership(GameTestHelper h) {
        var boss=boss(h); var enemy=target(h);
        h.assertTrue(LeviathanSpells.cast(boss,enemy,"dragon_roar"),"T3 barrage created");
        final float[] afterBarrage={1000};
        final BlockPos[] marker={null};
        h.runAtTickTime(45,()->{
            h.assertTrue(enemy.getHealth()<1000,"Actual barrage bolts must hit with a mob owner");
            afterBarrage[0]=enemy.getHealth();
            h.assertTrue(LeviathanSpells.cast(boss,enemy,"dragon_ruin"),"T5 beam created");
            var beam=h.getLevel().getEntitiesOfClass(TNWaterSpellEntity.class,boss.getBoundingBox().inflate(80))
                    .stream().filter(e->e.tier()==5).findFirst().orElseThrow();
            marker[0]=BlockPos.containing(beam.position().add(beam.direction().scale(8))
                    .add(WaterSpellRules.right(beam.direction()).scale(10)));
            h.getLevel().setBlockAndUpdate(marker[0],Blocks.STONE.defaultBlockState());
        });
        h.runAtTickTime(100,()->{
            h.assertTrue(enemy.getHealth()<afterBarrage[0],"T5 beam deals real damage after charge");
            h.assertTrue(h.getLevel().getEntitiesOfClass(TNWaterSpellEntity.class,
                    boss.getBoundingBox().inflate(80)).stream()
                    .anyMatch(e->e.tier()==5 && e.firedAt()>=0 && e.length()>0),
                    "The actual T5 beam must finish charging and acquire a real length");
            h.assertTrue(h.getLevel().getBlockState(marker[0]).is(Blocks.STONE),
                    "Mob beam cannot borrow a player's drilling permissions");
            boss.discard(); enemy.discard();
        });
        h.runAtTickTime(105,()->{
            h.assertTrue(h.getLevel().getEntitiesOfClass(TNWaterSpellEntity.class,
                    boss.getBoundingBox().inflate(45)).isEmpty(),"Large spells end when their boss disappears");
            h.succeed();
        });
    }

    private static void clearAscentCorridor(GameTestHelper h) {
        // The 48-high fixture starts underground. Clear the entire ascent corridor into open sky.
        for (BlockPos p : BlockPos.betweenClosed(h.absolutePos(new BlockPos(16,2,4)),
                h.absolutePos(new BlockPos(24,145,34))))
            if (!h.getLevel().getBlockState(p).isAir()) h.getLevel().setBlock(p,Blocks.AIR.defaultBlockState(),2);
    }

    @GameTest(template="leviathan_empty",timeoutTicks=185)
    public static void cryptKeepsItsRealSwordTimelineWithAMobCaster(GameTestHelper h) {
        clearAscentCorridor(h);
        var boss=boss(h); var enemy=target(h); float hp=boss.getHealth();
        h.assertTrue(LeviathanSpells.cast(boss,enemy,"sea_god_crypt"),"T5 crypt created");
        h.runAtTickTime(155,()->{
            h.assertTrue(enemy.getHealth()<=1000-SeaGodSwordRules.DAMAGE,
                    "Boss crypt must survive until the real descending sword strikes");
            h.assertTrue(boss.getHealth()==hp,"Caster inside its own crypt is immune");
            boss.discard(); enemy.discard(); h.succeed();
        });
    }
    @GameTest(template="leviathan_empty",timeoutTicks=245)
    public static void beamHoldsMouthAndPositionUntilTenSecondsOfFiringEnd(GameTestHelper h) {
        var boss=boss(h); var enemy=target(h); var at=boss.position();
        h.assertTrue(LeviathanSpells.cast(boss,enemy,"dragon_ruin"),"T5 created");
        final TNWaterSpellEntity[] beamRef={null};
        h.runAtTickTime(2,()->{
            beamRef[0]=(TNWaterSpellEntity)h.getLevel().getEntity(boss.channelId());
            h.assertTrue(beamRef[0]!=null && beamRef[0].duration()==200,"Boss beam lasts 200 active ticks");
            assertMouthAperture(h,boss,beamRef[0]);
        });
        h.assertTrue(WaterSpellRules.duration(5)==240,"Player beam duration is preserved");
        h.runAtTickTime(90,()->{
            boss.setDeltaMovement(1,1,1);
            h.assertTrue(boss.mouthOpen(),"Mouth must remain open after the old breath animation ends");
        });
        h.runAtTickTime(210,()->{
            h.assertTrue(!beamRef[0].isRemoved() && boss.mouthOpen(),"Still channeling near the end");
            h.assertTrue(boss.position().distanceToSqr(at)<0.001,"Channel anchors the boss against drift");
        });
        h.runAtTickTime(230,()->{
            h.assertTrue(beamRef[0].isRemoved() && !boss.mouthOpen(),"Mouth closes when the real beam ends");
            boss.discard(); enemy.discard(); h.succeed();
        });
    }

    @GameTest(template="leviathan_empty",timeoutTicks=455)
    public static void cryptAscendsToSealFacesDownAndEndsAfterOneSword(GameTestHelper h) {
        clearAscentCorridor(h);
        var boss=boss(h); var enemy=target(h);
        h.assertTrue(LeviathanSpells.cast(boss,enemy,"sea_god_crypt"),"Crypt created");
        final TNWaterFieldEntity[] fieldRef={null};
        h.runAtTickTime(2,()->{
            var field=(TNWaterFieldEntity)h.getLevel().getEntity(boss.channelId()); fieldRef[0]=field;
            h.assertTrue(field!=null && field.life()==200 && field.swordCount()==1,"Boss crypt is ten seconds and one sword");
            h.assertTrue(field.swordImpactIndex(140)==0 && field.swordImpactIndex(200)==-1,"Only the first impact is scheduled");
        });
        h.runAtTickTime(95,()->{
            h.assertTrue(boss.castPose()==3 && boss.mouthOpen(),"Arrives before the sword appears and opens its mouth; pose="+boss.castPose()+" boss="+boss.position()+" station="+fieldRef[0].skySealCenter()+" removed="+fieldRef[0].isRemoved()+" age="+fieldRef[0].age());
            h.assertTrue(boss.position().distanceTo(fieldRef[0].skySealCenter())<8,"Boss is stationed at the actual sky seal");
            h.assertTrue(boss.aperturePosition(boss.getYRot(),90F).distanceTo(fieldRef[0].skySealCenter())<0.01,
                    "The sky seal is just in front of the downward-facing mouth");
            h.assertTrue(boss.getLookAngle().dot(new net.minecraft.world.phys.Vec3(0,-1,0))>0.99,
                    "Boss faces the same downward direction as the falling sword");
        });
        h.runAtTickTime(195,()->h.assertTrue(boss.mouthOpen() && !fieldRef[0].isRemoved(),"Holds the pose until the crypt ends"));
        h.runAtTickTime(210,()->{
            h.assertTrue(fieldRef[0].isRemoved() && !boss.mouthOpen(),"Crypt and mouth end together");
            h.assertTrue(boss.isReturningFromSky(),"Crypt ends with an active return flight");
            // Move the victim after the ritual: return must follow them, not the old field.
            enemy.setPos(h.absoluteVec(new net.minecraft.world.phys.Vec3(20.5,4,24.5)));
            boss.setNoAi(false);
        });
        final float[] hpAfterReturn={1000};
        h.runAtTickTime(310,()->{
            h.assertTrue(!boss.isReturningFromSky() && boss.distanceTo(enemy)<24,
                    "Boss must come back down to the victim within five seconds");
            h.assertTrue(boss.getTarget()==enemy,"The original victim is reacquired outside the high-altitude follow range");
            hpAfterReturn[0]=enemy.getHealth();
        });
        h.runAtTickTime(440,()->{
            h.assertTrue(enemy.getHealth()<hpAfterReturn[0],"Boss resumes actual frequent attacks after descending");
            boss.discard(); enemy.discard(); h.succeed();
        });
    }

    @GameTest(template="leviathan_empty",timeoutTicks=45)
    public static void barrageInterruptionReleasesTheMouthPose(GameTestHelper h) {
        var boss=boss(h); var enemy=target(h); var at=boss.position();
        h.assertTrue(LeviathanSpells.cast(boss,enemy,"dragon_roar"),"T3 created");
        var spellId=boss.channelId();
        h.runAtTickTime(20,()->{
            h.assertTrue(boss.mouthOpen() && boss.position().distanceToSqr(at)<0.001,"T3 holds its mouth and body");
            assertMouthAperture(h,boss,(TNWaterSpellEntity)h.getLevel().getEntity(spellId));
            h.getLevel().getEntity(spellId).discard();
        });
        h.runAtTickTime(25,()->{
            h.assertTrue(!boss.mouthOpen(),"Interrupted spells must release the pose");
            boss.discard(); enemy.discard(); h.succeed();
        });
    }
    private static void assertMouthAperture(GameTestHelper h,TNWaterLeviathanEntity boss,TNWaterSpellEntity spell) {
        var mouth=boss.mouthPosition(boss.getYRot(),boss.castingPitch(1));
        var gap=spell.position().subtract(mouth);
        h.assertTrue(Math.abs(gap.length()-boss.APERTURE_GAP)<0.01 && gap.normalize().dot(spell.direction())>0.999,
                "Spell aperture must be 0.85 blocks ahead of the actual pitched mouth");
    }
}
