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
        clearAscentCorridor(h);
        var boss=h.spawn(TNNpcs.WATER_LEVIATHAN.get(),new BlockPos(20,4,10));
        boss.setNoAi(true);
        h.assertTrue(Math.abs(boss.getBbWidth()-19.2F)<0.01 && Math.abs(boss.getBbHeight()-16.4F)<0.01,
                "Collision dimensions are four times the original on the real registered entity");
        return boss;
    }

    private static LivingEntity target(GameTestHelper h) {
        var target=h.spawn(EntityType.VILLAGER,new BlockPos(20,4,47));
        target.setNoAi(true); target.setNoGravity(true);
        target.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1000);
        target.setHealth(1000); return target;
    }

    @GameTest(template="leviathan_empty",timeoutTicks=65)
    public static void giantWaterOrbReplacesTsunamiAndUsesItsFullHitRadius(GameTestHelper h) {
        var boss=boss(h); var enemy=target(h); float hp=boss.getHealth();
        boss.setPos(boss.position().add(0,11.2,0));
        enemy.setPos(enemy.position().add(0,0,13));
        h.assertTrue(!LeviathanSpells.cast(boss,enemy,"tsunami"),"Tsunami is removed from the boss spell dispatcher");
        h.assertTrue(LeviathanSpells.cast(boss,enemy,LeviathanSpellRules.BASIC),"Giant water orb is fired");
        h.runAtTickTime(2,()->{
            var shot=h.getLevel().getEntitiesOfClass(TNWaterBoltEntity.class,boss.getBoundingBox().inflate(80))
                    .stream().filter(e->e.getOwner()==boss && e.tier()==1).findFirst().orElseThrow();
            h.assertTrue(shot.spellScale()==20F && Math.abs(shot.visualRadius()-6.4D)<0.001,
                    "Basic water orb is twenty times the standard ball");
            h.assertTrue(shot.collisionRadius()==shot.visualRadius(),"Huge ball has a matching swept hit radius");
            h.assertTrue(boss.mouthOpen(),"The beast opens its mouth to spit the ordinary orb");
            enemy.setPos(enemy.position().add(4.5D,0,0)); // Only a genuinely large swept ball can hit this sidestep.
            h.assertTrue(h.getLevel().getEntitiesOfClass(TNWaterFieldEntity.class,boss.getBoundingBox().inflate(80))
                    .stream().noneMatch(e->e.kind()==1),"No tsunami field is created");
        });
        h.runAtTickTime(40,()->{
            h.assertTrue(enemy.getHealth()<1000 && boss.getHealth()==hp,"Real giant orb hits without damaging its caster");
            boss.discard();enemy.discard();h.succeed();
        });
    }

    @GameTest(template="leviathan_empty",timeoutTicks=130)
    public static void realBarrageAndDragonRuinUseMobOwnership(GameTestHelper h) {
        var boss=boss(h); var enemy=target(h);
        h.assertTrue(LeviathanSpells.cast(boss,enemy,"dragon_roar"),"T3 barrage created");
        final float[] afterBarrage={1000};
        final BlockPos[] marker={null}, bedrock={null};
        h.runAtTickTime(45,()->{
            h.assertTrue(enemy.getHealth()<1000,"Actual barrage bolts must hit with a mob owner");
            afterBarrage[0]=enemy.getHealth();
            h.assertTrue(LeviathanSpells.cast(boss,enemy,"dragon_ruin"),"T5 beam created");
            var beam=h.getLevel().getEntitiesOfClass(TNWaterSpellEntity.class,boss.getBoundingBox().inflate(80))
                    .stream().filter(e->e.getUUID().equals(boss.channelId()) && e.tier()==5).findFirst().orElseThrow();
            marker[0]=BlockPos.containing(beam.position().add(beam.direction().scale(3)));
            h.getLevel().setBlockAndUpdate(marker[0],Blocks.STONE.defaultBlockState());
            bedrock[0]=BlockPos.containing(beam.position().add(beam.direction().scale(4)));
            h.getLevel().setBlockAndUpdate(bedrock[0],Blocks.BEDROCK.defaultBlockState());
            for(int step=0;step<=96;step+=8) {
                var p=BlockPos.containing(beam.position().add(beam.direction().scale(step)));
                h.getLevel().setChunkForced(Math.floorDiv(p.getX(),16),Math.floorDiv(p.getZ(),16),true);
            }
        });
        h.runAtTickTime(100,()->{
            h.assertTrue(enemy.getHealth()<=afterBarrage[0]-14,"Boss T5 damages a victim behind stone and bedrock");
            h.assertTrue(h.getLevel().getEntitiesOfClass(TNWaterSpellEntity.class,
                    boss.getBoundingBox().inflate(80)).stream()
                    .anyMatch(e->e.tier()==5 && e.firedAt()>=0 && Math.abs(e.length()-WaterSpellRules.range(5))<.01),
                    "Boss T5 retains its entire range through terrain");
            h.assertTrue(h.getLevel().getBlockState(marker[0]).is(Blocks.STONE) && h.getLevel().getBlockState(bedrock[0]).is(Blocks.BEDROCK),
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
        // Large spell origins cross the original fixture's first chunk; keep the whole arena ticking.
        BlockPos low=h.absolutePos(new BlockPos(4,0,-40)),high=h.absolutePos(new BlockPos(40,0,65));
        for (int cx=Math.floorDiv(low.getX(),16);cx<=Math.floorDiv(high.getX(),16);cx++)
            for (int cz=Math.floorDiv(low.getZ(),16);cz<=Math.floorDiv(high.getZ(),16);cz++)
                h.getLevel().setChunkForced(cx,cz,true);

        // The 48-high fixture starts underground. Clear the entire ascent corridor into open sky.
        for (BlockPos p : BlockPos.betweenClosed(h.absolutePos(new BlockPos(8,2,-8)),
                h.absolutePos(new BlockPos(32,190,64))))
            if (!h.getLevel().getBlockState(p).isAir()) h.getLevel().setBlock(p,Blocks.AIR.defaultBlockState(),2);
    }

    @GameTest(template="leviathan_empty",timeoutTicks=185)
    public static void cryptKeepsItsRealSwordTimelineWithAMobCaster(GameTestHelper h) {
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
        final TNWaterFieldEntity[] prison={null};
        final var locked=enemy.position();
        h.runAtTickTime(2,()->{
            beamRef[0]=(TNWaterSpellEntity)h.getLevel().getEntity(boss.channelId());
            h.assertTrue(beamRef[0]!=null && beamRef[0].duration()==200,"Boss beam lasts 200 active ticks");
            assertMouthAperture(h,boss,beamRef[0]);
            prison[0]=h.getLevel().getEntitiesOfClass(TNWaterFieldEntity.class,enemy.getBoundingBox().inflate(3))
                    .stream().filter(e->e.kind()==2 && e.tier()==2).findFirst().orElseThrow();
            h.assertTrue(beamRef[0].firedAt()<0,"Prison is active before T5 fires");
        });
        h.assertTrue(WaterSpellRules.duration(5)==240,"Player beam duration is preserved");
        h.runAtTickTime(90,()->{
            boss.setDeltaMovement(1,1,1);enemy.setDeltaMovement(1,1,1);
            h.assertTrue(boss.mouthOpen(),"Mouth must remain open after the old breath animation ends");
        });
        h.runAtTickTime(210,()->{
            h.assertTrue(!prison[0].isRemoved() && enemy.position().distanceToSqr(locked)<.01,
                    "Prison holds the victim throughout the beam, including attempted knockback");
            h.assertTrue(!beamRef[0].isRemoved() && boss.mouthOpen(),"Still channeling near the end");
            h.assertTrue(boss.position().distanceToSqr(at)<0.001,"Channel anchors the boss against drift");
        });
        h.runAtTickTime(230,()->{
            h.assertTrue(prison[0].isRemoved(),"Water prison ends with the beam");
            enemy.setPos(enemy.position().add(2,0,0));
            h.assertTrue(beamRef[0].isRemoved() && !boss.mouthOpen(),"Mouth closes when the real beam ends");
        });
        h.runAtTickTime(233,()->{
            h.assertTrue(enemy.position().distanceToSqr(locked)>1,"No residual restraint after T5 ends");
            boss.discard();enemy.discard();h.succeed();
        });
    }

    @GameTest(template="leviathan_empty",timeoutTicks=455)
    public static void cryptAscendsToSealFacesDownAndEndsAfterOneSword(GameTestHelper h) {
        var boss=boss(h); var enemy=target(h);
        h.assertTrue(LeviathanSpells.cast(boss,enemy,"sea_god_crypt"),"Crypt created");
        final TNWaterFieldEntity[] fieldRef={null};
        h.runAtTickTime(2,()->{
            var field=(TNWaterFieldEntity)h.getLevel().getEntity(boss.channelId()); fieldRef[0]=field;
            h.assertTrue(field!=null && field.life()==200 && field.swordCount()==1,"Boss crypt is ten seconds and one sword; field="+field+" channel="+boss.channelId()+" pose="+boss.castPose()+" alive="+boss.isAlive()+" at="+boss.position());
            h.assertTrue(field.swordImpactIndex(140)==0 && field.swordImpactIndex(200)==-1,"Only the first impact is scheduled");
        });
        h.runAtTickTime(8,()->{
            h.assertTrue(boss.castPose()==2 && boss.getXRot()==90F && boss.castTilt(1)>.99F,
                    "Boss turns head-down during ascent within a quarter second");
        });
        h.runAtTickTime(55,()->{
            h.assertTrue(boss.castPose()==3 && boss.mouthOpen(),"Arrives before the sword appears and opens its mouth; pose="+boss.castPose()+" boss="+boss.position()+" station="+fieldRef[0].skySealCenter()+" removed="+fieldRef[0].isRemoved()+" age="+fieldRef[0].age());
            h.assertTrue(boss.position().distanceTo(fieldRef[0].skySealCenter())<boss.MOUTH_FORWARD+boss.APERTURE_GAP+1,"Boss is stationed at the actual sky seal");
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
            enemy.setPos(h.absoluteVec(new net.minecraft.world.phys.Vec3(20.5,4,44.5)));
            boss.setNoAi(false);
        });
        final float[] hpAfterReturn={1000};
        h.runAtTickTime(260,()->{
            h.assertTrue(!boss.isReturningFromSky() && boss.distanceTo(enemy)<48,
                    "Boss must come back down to the victim within three seconds");
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
        h.runAtTickTime(2,()->{
            var cast=(TNWaterSpellEntity)h.getLevel().getEntity(spellId);
            h.assertTrue(cast.spellScale()==3F && cast.circleRadius()==WaterSpellRules.circleRadius(3)*3,
                    "Boss T3 aperture is three times the player's");
        });
        h.runAtTickTime(11,()->h.assertTrue(h.getLevel().getEntitiesOfClass(TNWaterBoltEntity.class,
                boss.getBoundingBox().inflate(100)).stream().anyMatch(e->e.getOwner()==boss && e.tier()==3 && e.spellScale()==3F),
                "T3 uses real three-times-size synchronized projectile entities"));
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
    @GameTest(template="leviathan_empty",timeoutTicks=35)
    public static void bossBoltsPierceEnemiesOnceAndStopAtWalls(GameTestHelper h) {
        var boss=boss(h);var first=target(h);var second=target(h);
        var start=h.absoluteVec(new net.minecraft.world.phys.Vec3(20.5,28,15));
        first.setPos(start.add(0,0,4));second.setPos(start.add(0,0,10));
        var shot=com.tnc.tnc.magic.TNOrbEntities.WATER_BOLT.get().create(h.getLevel());
        shot.configure(boss,1,start,new net.minecraft.world.phys.Vec3(0,0,1));h.getLevel().addFreshEntity(shot);
        var wall=BlockPos.containing(start.add(0,0,18));h.getLevel().setBlockAndUpdate(wall,Blocks.STONE.defaultBlockState());
        h.runAtTickTime(7,()->{
            h.assertTrue(first.getHealth()==996 && !shot.isRemoved(),"First victim takes one hit without consuming the ball");
            first.invulnerableTime=0;first.setPos(shot.position().add(0,0,1));
        });
        h.runAtTickTime(15,()->h.assertTrue(first.getHealth()==996 && second.getHealth()==996 && !shot.isRemoved(),
                "A ball pierces multiple enemies but never damages the same enemy twice"));
        h.runAtTickTime(24,()->{
            h.assertTrue(shot.isRemoved(),"Terrain still stops a piercing boss projectile");
            boss.discard();first.discard();second.discard();h.succeed();
        });
    }
    @GameTest(template="leviathan_empty",timeoutTicks=35)
    public static void interruptedT5ImmediatelyReleasesPrison(GameTestHelper h) {
        var boss=boss(h);var enemy=target(h);var at=enemy.position();
        h.assertTrue(LeviathanSpells.cast(boss,enemy,"dragon_ruin"),"T5 created");
        h.runAtTickTime(5,()->h.getLevel().getEntity(boss.channelId()).discard());
        h.runAtTickTime(8,()->enemy.setPos(at.add(3,0,0)));
        h.runAtTickTime(12,()->{
            h.assertTrue(enemy.position().distanceToSqr(at)>4,"Interrupted beam immediately releases its prison lease");
            h.assertTrue(h.getLevel().getEntitiesOfClass(TNWaterFieldEntity.class,enemy.getBoundingBox().inflate(8))
                    .stream().noneMatch(e->e.kind()==2 && e.tier()==2),"Interrupted prison model is removed");
            boss.discard();enemy.discard();h.succeed();
        });
    }
    private static void assertMouthAperture(GameTestHelper h,TNWaterLeviathanEntity boss,TNWaterSpellEntity spell) {
        var mouth=boss.mouthPosition(boss.getYRot(),boss.castingPitch(1));
        var gap=spell.position().subtract(mouth);
        h.assertTrue(Math.abs(gap.length()-boss.APERTURE_GAP)<0.01 && gap.normalize().dot(spell.direction())>0.999,
                "Spell aperture must remain scaled and ahead of the actual pitched mouth");
    }
    @GameTest(template="leviathan_empty",timeoutTicks=115)
    public static void cryptSkyLayoutStaysFixedAtHighAltitudeWhileBossFindsIt(GameTestHelper h) {
        var boss=boss(h);var enemy=target(h);
        boss.setPos(boss.getX(),192,boss.getZ());enemy.setPos(enemy.getX(),192,enemy.getZ());
        h.assertTrue(LeviathanSpells.cast(boss,enemy,"sea_god_crypt"),"High-altitude crypt created");
        final TNWaterFieldEntity[] field={null};final net.minecraft.world.phys.Vec3[] origin={null};
        h.runAtTickTime(2,()->{
            field[0]=(TNWaterFieldEntity)h.getLevel().getEntity(boss.channelId());origin[0]=field[0].position();
            h.assertTrue(field[0].skySealHeight()==116F && field[0].skySealCenter().y==308,
                    "Fixed sword/sky-seal layout is not lowered to fit the giant caster");
        });
        h.runAtTickTime(98,()->{
            h.assertTrue(boss.castPose()==3,"Boss flies to the independently fixed sky seal");
            h.assertTrue(field[0].position().equals(origin[0]) && field[0].skySealCenter().y==308,
                    "The field and sky seal do not follow the moving boss");
            h.assertTrue(boss.aperturePosition(boss.getYRot(),90).distanceTo(field[0].skySealCenter())<.01,
                    "Boss moves its mouth to the fixed seal rather than moving the seal to its mouth");
            boss.discard();enemy.discard();h.succeed();
        });
    }
}
