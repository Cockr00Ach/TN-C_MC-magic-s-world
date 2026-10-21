package com.tnc.tnc.world.ink;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.*;
@GameTestHolder("tncink") @PrefixGameTestTemplate(false)
public final class InkWingedTigerGameTests {
    private static InkWingedTigerEntity tiger(GameTestHelper h){
        var low=h.absolutePos(new BlockPos(0,0,0));var high=h.absolutePos(new BlockPos(48,0,55));
        for(int x=Math.floorDiv(low.getX(),16);x<=Math.floorDiv(high.getX(),16);x++)for(int z=Math.floorDiv(low.getZ(),16);z<=Math.floorDiv(high.getZ(),16);z++)h.getLevel().setChunkForced(x,z,true);
        for(var p:BlockPos.betweenClosed(h.absolutePos(new BlockPos(4,4,4)),h.absolutePos(new BlockPos(44,30,51))))h.getLevel().setBlock(p,Blocks.AIR.defaultBlockState(),2);
        for(int x=4;x<=44;x++)for(int z=4;z<=51;z++)h.setBlock(x,3,z,Blocks.STONE);
        var t=h.spawn(InkBossRegistration.INK_WINGED_TIGER,new BlockPos(20,4,20));t.setNoAi(true);t.setYRot(0);return t;
    }
    private static LivingEntity target(GameTestHelper h,int z){var e=h.spawn(EntityType.VILLAGER,new BlockPos(20,4,z));e.setNoAi(true);e.setNoGravity(true);e.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1000);e.setHealth(1000);return e;}
    private static java.util.List<InkTigerEffectEntity> effects(GameTestHelper h,InkWingedTigerEntity t){return h.getLevel().getEntitiesOfClass(InkTigerEffectEntity.class,t.getBoundingBox().inflate(100),e->e.ownedBy(t));}
    private static void clean(GameTestHelper h,InkWingedTigerEntity t,LivingEntity... enemies){for(var e:effects(h,t))e.discard();t.discard();for(var e:enemies)e.discard();}
    @GameTest(template="ink_crane_empty",timeoutTicks=220)
    public static void skyShotChargesThenHitsGrowsAndExplodesOnlyOnce(GameTestHelper h){
        var t=tiger(h);var e=target(h,36);Vec3 home=t.position();t.startInkAttack(1,e);
        h.runAtTickTime(40,()->{h.assertTrue(t.getY()>home.y+10&&t.isNoGravity(),"Real body rises before spit");h.assertTrue(effects(h,t).isEmpty(),"No premature projectile");});
        h.runAtTickTime(100,()->h.assertTrue(effects(h,t).stream().anyMatch(v->v.mode()==InkTigerEffectEntity.BALL),"Shot collision becomes anchored growing ball"));
        h.runAtTickTime(125,()->h.assertTrue(t.inkMove()==0&&!t.isNoGravity()&&t.position().distanceTo(home)<1,"Caster descends and restores gravity"));
        h.runAtTickTime(205,()->{h.assertTrue(e.getHealth()<992,"Impact and eventual explosion damage, health="+e.getHealth());
            h.assertTrue(t.getHealth()==t.getMaxHealth(),"Caster immune to own ball");
            h.assertTrue(effects(h,t).stream().anyMatch(v->v.mode()==InkTigerEffectEntity.BALL&&v.age()>=100),"Landscape aftermath persists");
            h.assertTrue(h.getBlockState(new BlockPos(20,3,36)).is(Blocks.STONE),"No terrain destruction");clean(h,t,e);h.succeed();});
    }
    @GameTest(template="ink_crane_empty",timeoutTicks=100)
    public static void giantGhostFormsBehindTigerThenChargesTarget(GameTestHelper h){
        var t=tiger(h);var e=target(h,36);Vec3 home=t.position();t.startInkAttack(2,e);
        h.runAtTickTime(12,()->{var ghost=effects(h,t).stream().filter(v->v.mode()==InkTigerEffectEntity.GHOST).findFirst().orElseThrow();h.assertTrue(ghost.getZ()<t.getZ()-4,"Ghost forms behind caster");h.assertTrue(e.getHealth()==1000,"No windup damage");});
        h.runAtTickTime(75,()->{h.assertTrue(e.getHealth()==970,"One ghost impact: "+e.getHealth());h.assertTrue(t.position().distanceTo(home)<1,"Ghost, not caster, rushes");clean(h,t,e);h.succeed();});
    }
    @GameTest(template="ink_crane_empty",timeoutTicks=170)
    public static void roarPullsThenDetonatesThenWingSlashes(GameTestHelper h){
        var t=tiger(h);var e=target(h,30);var far=target(h,47);t.startInkAttack(3,e);double z=e.getZ();
        h.runAtTickTime(65,()->{h.assertTrue(e.getDeltaMovement().z<-.001||e.getZ()<z-.2,"Server applies inward impulse even to no-AI fixture: "+e.getDeltaMovement());h.assertTrue(e.getHealth()==1000,"Charge does not detonate early");});
        h.runAtTickTime(95,()->h.assertTrue(e.getHealth()<=972,"Ball detonates before wing spin"));
        h.runAtTickTime(145,()->{h.assertTrue(e.getHealth()<972,"Subsequent wing slash damage");h.assertTrue(far.getHealth()==1000,"Outside suction/blast/slash range");h.assertTrue(t.getHealth()==t.getMaxHealth(),"Caster safe");clean(h,t,e,far);h.succeed();});
    }
    @GameTest(template="ink_crane_empty",timeoutTicks=65)
    public static void interruptedOrReloadedFlightNeverLeavesTigerFloating(GameTestHelper h){
        var t=tiger(h);var e=target(h,36);Vec3 home=t.position();t.startInkAttack(1,e);
        h.runAtTickTime(35,()->{var tag=new net.minecraft.nbt.CompoundTag();t.saveWithoutId(tag);var restored=InkBossRegistration.INK_WINGED_TIGER.create(h.getLevel());restored.load(tag);
            h.assertTrue(!restored.isNoGravity()&&restored.inkMove()==0&&restored.position().distanceTo(home)<1,"Reload returns to safe ground");e.discard();});
        h.runAtTickTime(45,()->{h.assertTrue(!t.isNoGravity()&&t.inkMove()==0&&t.position().distanceTo(home)<1,"Target loss cancels flight");clean(h,t);h.succeed();});
    }
    @GameTest(template="ink_crane_empty",timeoutTicks=20)
    public static void blinkChecksBodyVolumeAndLeavesTwoWatercolorStains(GameTestHelper h){
        var t=tiger(h);Vec3 before=t.position(),at=before.add(10,0,0);
        h.assertTrue(t.blinkTo(at),"Safe supported destination accepted");h.assertTrue(t.position().distanceTo(at)<.01,"Actual teleport");
        h.assertTrue(effects(h,t).stream().filter(v->v.mode()==InkTigerEffectEntity.STAIN).count()==2,"Departure and arrival stains");
        h.setBlock(20,5,20,Blocks.STONE);h.assertTrue(!t.blinkTo(before),"Full body obstacle rejects blink");
        h.assertTrue(!t.blinkTo(before.add(0,12,0)),"Unsupported air destination rejected");
        h.setBlock(20,5,20,Blocks.AIR);clean(h,t);h.succeed();
    }
    @GameTest(template="ink_crane_empty",timeoutTicks=65)
    public static void ceilingLimitsFlightAndDeadCasterCancelsPendingDamage(GameTestHelper h){
        var t=tiger(h);var e=target(h,36);double y=t.getY();
        for(var p:BlockPos.betweenClosed(h.absolutePos(new BlockPos(16,14,16)),h.absolutePos(new BlockPos(24,14,24))))h.getLevel().setBlock(p,Blocks.STONE.defaultBlockState(),2);
        t.startInkAttack(1,e);
        h.runAtTickTime(40,()->{h.assertTrue(t.getY()<y+3,"Body does not rise through ceiling");t.kill();});
        h.runAtTickTime(50,()->{h.assertTrue(e.getHealth()==1000,"No postmortem projectile damage");clean(h,t,e);h.succeed();});
    }
    @GameTest(template="ink_crane_empty",timeoutTicks=75)
    public static void wallAndAlliesBlockSuctionAndShotDamage(GameTestHelper h){
        var t=tiger(h);var e=target(h,36);var ally=target(h,24);
        var board=h.getLevel().getScoreboard();var team=board.addPlayerTeam("tiger"+t.getId());
        board.addPlayerToTeam(t.getStringUUID(),team);board.addPlayerToTeam(ally.getStringUUID(),team);
        for(var p:BlockPos.betweenClosed(h.absolutePos(new BlockPos(16,4,28)),h.absolutePos(new BlockPos(24,20,28))))h.getLevel().setBlock(p,Blocks.STONE.defaultBlockState(),2);
        InkTigerEffectEntity.pull(t,t.position(),22,.045,.22);
        h.assertTrue(e.getDeltaMovement().lengthSqr()==0&&ally.getDeltaMovement().lengthSqr()==0,"Neither wall-hidden enemy nor ally pulled");
        InkTigerEffectEntity.spawn(t,e,InkTigerEffectEntity.SHOT,t.mouthPosition(false));
        h.runAtTickTime(60,()->{h.assertTrue(e.getHealth()==1000&&ally.getHealth()==1000,"No projectile damage through wall or to allies");
            h.assertTrue(effects(h,t).stream().anyMatch(v->v.mode()==InkTigerEffectEntity.BALL),"Wall impact anchors ball without tunnelling");
            board.removePlayerTeam(team);clean(h,t,e,ally);h.succeed();});
    }
    @GameTest(template="ink_crane_empty",timeoutTicks=145)
    public static void tigerBallPullsRealPhysicsTargetSeveralBlocksAndStopsAfterBlast(GameTestHelper h){
        var t=tiger(h);var e=target(h,32);((Mob)e).setNoAi(false);e.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(0);
        Vec3 start=e.position();var ball=InkTigerEffectEntity.spawn(t,e,InkTigerEffectEntity.BALL,t.position().add(0,0,20));
        h.runAtTickTime(25,()->h.assertTrue(e.position().distanceTo(start)<.01,"No early pull"));
        h.runAtTickTime(85,()->{double moved=e.getZ()-start.z;
            h.assertTrue(moved>4.5&&moved<9,"Tiger ball now strongly pulls actual travel physics: "+moved);
            h.assertTrue(Math.abs(e.getY()-start.y)<.01,"No vertical launching");});
        h.runAtTickTime(105,()->{e.setPos(start);e.setDeltaMovement(Vec3.ZERO);});
        h.runAtTickTime(130,()->{h.assertTrue(e.position().distanceTo(start)<.01,"Attraction ends after detonation");
            h.assertTrue(t.getBbWidth()>7&&t.getBbHeight()>7,"Body-sized hitbox follows visual scale");clean(h,t,e);h.succeed();});
    }
}
