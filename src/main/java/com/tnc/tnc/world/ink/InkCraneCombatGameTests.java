package com.tnc.tnc.world.ink;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/** Opt-in real server tests: runGameTestServer -PinkCraneTests. */
@GameTestHolder("tncink")
@PrefixGameTestTemplate(false)
public final class InkCraneCombatGameTests {
    @GameTest(template="ink_crane_empty",timeoutTicks=30)
    public static void oldGroundStrokesBecomePaperWithoutChangingThePaintingOrBuildings(GameTestHelper h) {
        var realm=h.getLevel().getServer().getLevel(InkRealmTravel.INK_REALM);
        var chunk=realm.getChunk(0,0);int y=realm.getMinBuildHeight()+4;
        var black=new BlockPos(3,y,3);var gray=new BlockPos(4,y,3);var building=new BlockPos(5,y,3);
        realm.setBlock(black,Blocks.BLACK_CONCRETE.defaultBlockState(),2);
        realm.setBlock(gray,Blocks.GRAY_CONCRETE.defaultBlockState(),2);
        realm.setBlock(building,Blocks.DIAMOND_BLOCK.defaultBlockState(),2);
        InkRealmDecoration.paint(chunk);
        h.assertTrue(realm.getBlockState(black).is(Blocks.WHITE_CONCRETE)
                && realm.getBlockState(gray).is(Blocks.WHITE_CONCRETE),"Legacy painted strokes must be erased");
        h.assertTrue(realm.getBlockState(building).is(Blocks.DIAMOND_BLOCK),"Non-paper blocks must be preserved");
        h.assertTrue(realm.getBlockState(black.above()).isAir(),"Flat ground must stay flat");
        h.succeed();
    }
    @GameTest(template="ink_crane_empty",timeoutTicks=145)
    public static void ballPullIsStrongHorizontalExcludesCasterAndStopsAtExplosion(GameTestHelper h) {
        var boss=boss(h);var victim=target(h,32);Vec3 home=boss.position(),start=victim.position();
        // NoAI mobs skip travel physics; keep physics enabled but disable voluntary walking.
        ((net.minecraft.world.entity.Mob)victim).setNoAi(false);
        victim.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(0);
        var ritual=InkCranePhantomEntity.spawn(boss,victim,InkCranePhantomEntity.RITUAL,
                boss.position().add(0,0,20),Vec3.ZERO);
        h.runAtTickTime(25,()->h.assertTrue(victim.position().distanceTo(start)<.01,"No pull before the ball forms"));
        h.runAtTickTime(85,()->{
            double moved=victim.getZ()-start.z;
            h.assertTrue(moved>4.5 && moved<9,"Strong pull moves several blocks without snapping: "+moved);
            h.assertTrue(Math.abs(victim.getY()-start.y)<.01,"Pull never lifts the target");
            h.assertTrue(boss.position().distanceTo(home)<.01,"Caster must not pull itself");
        });
        h.runAtTickTime(105,()->{victim.setPos(start);victim.setDeltaMovement(Vec3.ZERO);});
        h.runAtTickTime(130,()->{
            h.assertTrue(victim.position().distanceTo(start)<.01,"Harmless aftermath must not keep attracting");
            ritual.discard();victim.discard();boss.discard();h.succeed();
        });
    }
    @GameTest(template="ink_crane_empty",timeoutTicks=60)
    public static void landscapeIsBudgetedAndPreservesExistingBuildings(GameTestHelper h) {
        var level=h.getLevel().getServer().getLevel(InkRealmTravel.INK_REALM);
        h.assertTrue(level!=null,"Ink dimension must be registered");
        var chunk=level.getChunk(6,6);
        int base=level.getMinBuildHeight()+4;
        var protectedPos=new BlockPos(104,base+1,104);
        level.setBlock(protectedPos,Blocks.DIAMOND_BLOCK.defaultBlockState(),2);
        // Recreate exactly the obsolete scenery, then verify the migration only removes its own work.
        for(int x=96;x<112;x++)for(int z=96;z<112;z++) {
            if(x==104 && z==104)continue;
            var column=InkLandscapePattern.column(x,z);
            for(int y=1;y<=InkLandscapePattern.MAX_HEIGHT;y++) {
                var m=column.at(y);if(m==null)continue;
                var block=switch(m) {
                    case INK->Blocks.BLACK_CONCRETE;case CHARCOAL->Blocks.GRAY_CONCRETE;
                    case WASH->Blocks.LIGHT_GRAY_CONCRETE;case PAPER->Blocks.WHITE_CONCRETE;
                    case NEEDLES->Blocks.BLACK_WOOL;case PALE_NEEDLES->Blocks.GRAY_WOOL;
                };
                level.setBlock(new BlockPos(x,base+y,z),block.defaultBlockState(),2);
            }
        }
        var job=new InkRealmLandscape.Job(chunk,true);
        int before=countLandscapeBlocks(chunk,base);
        boolean complete=job.advance(level);
        int after=countLandscapeBlocks(chunk,base);
        h.assertTrue(before-after<=384,"Shared cleanup budget must cap writes at 384 per tick");
        int steps=1;
        while(!complete && steps++<200)complete=job.advance(level);
        h.assertTrue(complete && countLandscapeBlocks(chunk,base)==1,"Generated mountain is removed, leaving a flat world and the player's block");
        h.assertTrue(level.getBlockState(protectedPos).is(Blocks.DIAMOND_BLOCK)
                && level.getBlockState(protectedPos.above()).isAir(),"Existing building protects its whole column");
        h.succeed();
    }
    private static int countLandscapeBlocks(net.minecraft.world.level.chunk.LevelChunk chunk,int base) {
        int count=0;
        for(int x=96;x<112;x++)for(int z=96;z<112;z++)for(int y=1;y<=InkLandscapePattern.MAX_HEIGHT;y++)
            if(!chunk.getBlockState(new BlockPos(x,base+y,z)).isAir())count++;
        return count;
    }
    private static InkCraneBossEntity boss(GameTestHelper h) {
        BlockPos low=h.absolutePos(new BlockPos(0,0,0)), high=h.absolutePos(new BlockPos(48,0,55));
        for(int x=Math.floorDiv(low.getX(),16);x<=Math.floorDiv(high.getX(),16);x++)
            for(int z=Math.floorDiv(low.getZ(),16);z<=Math.floorDiv(high.getZ(),16);z++) h.getLevel().setChunkForced(x,z,true);
        for(BlockPos p:BlockPos.betweenClosed(h.absolutePos(new BlockPos(4,4,4)),h.absolutePos(new BlockPos(44,145,51))))
            if(!h.getLevel().getBlockState(p).isAir()) h.getLevel().setBlock(p,Blocks.AIR.defaultBlockState(),2);
        for(int x=4;x<=44;x++)for(int z=4;z<=51;z++)h.setBlock(x,3,z,Blocks.STONE);
        var boss=h.spawn(InkBossRegistration.INK_CRANE_BOSS,new BlockPos(20,4,20));
        boss.setNoAi(true);boss.setYRot(0);return boss;
    }
    private static LivingEntity target(GameTestHelper h,int z) {
        var target=h.spawn(EntityType.VILLAGER,new BlockPos(20,4,z));
        target.setNoAi(true);target.setNoGravity(true);
        target.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1000);target.setHealth(1000);return target;
    }
    private static java.util.List<InkCranePhantomEntity> effects(InkCraneBossEntity boss) {
        return boss.level().getEntitiesOfClass(InkCranePhantomEntity.class,boss.getBoundingBox().inflate(160),e->e.ownedBy(boss));
    }

    @GameTest(template="ink_crane_empty",timeoutTicks=140)
    public static void tenRealPursuitPhantomsHitWithoutHurtingCaster(GameTestHelper h) {
        var boss=boss(h);var target=target(h,46);Set<UUID> seen=new HashSet<>();
        h.assertTrue(boss.startInkAttack(1,target),"Barrage starts");
        for(int t=2;t<=114;t+=2) h.runAtTickTime(t,()->effects(boss).stream()
                .filter(e->e.mode()==InkCranePhantomEntity.PURSUIT).forEach(e->seen.add(e.getUUID())));
        h.runAtTickTime(125,()->{
            h.assertTrue(seen.size()==10,"Exactly ten actual phantom entities must be released, got "+seen.size());
            h.assertTrue(target.getHealth()<1000,"Real swept phantoms must damage the target");
            h.assertTrue(boss.getHealth()==boss.getMaxHealth(),"Caster must not hurt itself");
            boss.discard();target.discard();h.succeed();
        });
    }

    @GameTest(template="ink_crane_empty",timeoutTicks=180)
    public static void longWingIsForwardOnlyAndCannotHitRepeatedly(GameTestHelper h) {
        var boss=boss(h);var front=target(h,30);var rear=target(h,10);
        h.assertTrue(boss.startInkAttack(2,front),"Long wing starts");
        h.runAtTickTime(35,()->h.assertTrue(effects(boss).isEmpty(),"Avatar must wait until the upward cry completes"));
        h.runAtTickTime(50,()->h.assertTrue(effects(boss).stream().anyMatch(e->e.mode()==InkCranePhantomEntity.LONG_WINGS
                && e.position().z<boss.position().z-8),"Full avatar forms behind the crane after its cry"));
        Vec3 frontPosition=front.position();
        for(int reset:new int[]{106,132}) h.runAtTickTime(reset,()->{front.setPos(frontPosition);front.setDeltaMovement(Vec3.ZERO);});
        h.runAtTickTime(158,()->{
            h.assertTrue(Math.abs(front.getHealth()-916)<.1,"Three passes each hit once for 28 damage: "+front.getHealth());
            h.assertTrue(rear.getHealth()==1000,"A victim behind the crane is outside the arc");
            boss.discard();front.discard();rear.discard();h.succeed();
        });
    }

    @GameTest(template="ink_crane_empty",timeoutTicks=330)
    public static void skyCastReallyRisesHitsExplodesAndRestoresGravity(GameTestHelper h) {
        var boss=boss(h);var target=target(h,36);Vec3 home=boss.position();
        h.assertTrue(boss.startInkAttack(3,target),"Sky cast starts");
        h.runAtTickTime(8,()->h.assertTrue(boss.skyShroudVisible(),"Attached shadow is already visible during ascent"));
        h.runAtTickTime(36,()->h.assertTrue(boss.getY()>home.y+25&&boss.isNoGravity()
                && boss.skyShroudVisible(),"Actual boss and its attached shadow must ascend together"));
        Vec3 landing=target.position();
        h.runAtTickTime(65,()->h.assertTrue(effects(boss).stream().anyMatch(e->e.mode()==InkCranePhantomEntity.RITUAL
                && e.chargeAge()<0),"Seal must not charge while the real boss is still descending"));
        h.runAtTickTime(65,()->h.assertTrue(boss.skyShroudVisible()
                && effects(boss).stream().noneMatch(e->e.mode()==InkCranePhantomEntity.GIANT_DIVE),
                "Dive keeps its attached shadow; no independent giant dive is spawned"));
        h.runAtTickTime(86,()->h.assertTrue(boss.position().distanceTo(landing)<1
                && effects(boss).stream().anyMatch(e->e.mode()==InkCranePhantomEntity.RITUAL && e.chargeAge()>=0),
                "Actual boss arrives at seal before black ball charging begins"));
        h.runAtTickTime(86,()->h.assertTrue(!boss.skyShroudVisible()
                && effects(boss).stream().anyMatch(e->e.mode()==InkCranePhantomEntity.RITUAL && e.chargeAge()<32),
                "Shadow disappears before the black ball forms"));
        h.runAtTickTime(90,()->h.assertTrue(target.getHealth()<1000,"Giant dive deals real impact damage"));
        h.runAtTickTime(190,()->h.assertTrue(target.getHealth()<=930,"Growing black ball deals a second, separate blast"));
        h.runAtTickTime(220,()->{
            h.assertTrue(boss.inkMove()==0&&!boss.isNoGravity()&&boss.position().distanceTo(landing)<1,"Flight ends at the impact point, not the old hovering position");
            h.assertTrue(effects(boss).stream().anyMatch(e->e.mode()==InkCranePhantomEntity.RITUAL),
                    "Mountain/cloud painting remains after the explosion");
        });
        h.runAtTickTime(315,()->{
            h.assertTrue(effects(boss).isEmpty(),"Harmless painting fades after six seconds, without lingering entities");
            boss.discard();target.discard();h.succeed();
        });
    }

    @GameTest(template="ink_crane_empty",timeoutTicks=45)
    public static void interruptedAscentRemovesTheAttachedShadowAndRestoresGravity(GameTestHelper h) {
        var boss=boss(h);var target=target(h,36);Vec3 home=boss.position();
        h.assertTrue(boss.startInkAttack(3,target),"Sky move starts");
        h.runAtTickTime(18,()->{h.assertTrue(boss.skyShroudVisible(),"Airborne shadow active");target.discard();});
        h.runAtTickTime(25,()->{
            h.assertTrue(!boss.skyShroudVisible() && boss.inkMove()==0 && !boss.isNoGravity()
                    && boss.position().distanceTo(home)<1,"Interrupted shadow and airborne state must not get stuck");
            boss.discard();h.succeed();
        });
    }

    @GameTest(template="ink_crane_empty",timeoutTicks=180)
    public static void summonedDrakeHitsCreatesSealAndDetonates(GameTestHelper h) {
        var boss=boss(h);var target=target(h,32);
        h.assertTrue(boss.startInkAttack(4,target),"Drake charge starts");
        h.runAtTickTime(55,()->{
            h.assertTrue(target.getHealth()<1000,"Charging drake must hit");
            h.assertTrue(effects(boss).stream().anyMatch(e->e.mode()==InkCranePhantomEntity.RITUAL),"Drake hit must create a ritual");
        });
        h.runAtTickTime(160,()->{
            h.assertTrue(target.getHealth()<=932,"Drake impact and black ball must both hurt");
            boss.discard();target.discard();h.succeed();
        });
    }

    @GameTest(template="ink_crane_empty",timeoutTicks=110)
    public static void pursuitFindsANewEnemyWhenItsOriginalTargetDisappears(GameTestHelper h) {
        var boss=boss(h);var old=target(h,46);var replacement=target(h,42);
        replacement.setPos(replacement.position().add(7,0,0));
        Vec3 origin=boss.position().add(0,3.23,0);
        var phantom=InkCranePhantomEntity.spawn(boss,old,InkCranePhantomEntity.PURSUIT,
                origin,old.getEyePosition().subtract(origin).normalize().scale(.95));
        old.discard();
        h.runAtTickTime(85,()->{
            h.assertTrue(replacement.getHealth()<1000,"A flying phantom must acquire and home onto a replacement target");
            h.assertTrue(phantom.isRemoved()||phantom.finished(),"Homing projectile must not pass through its new target");
            boss.discard();replacement.discard();h.succeed();
        });
    }
}
