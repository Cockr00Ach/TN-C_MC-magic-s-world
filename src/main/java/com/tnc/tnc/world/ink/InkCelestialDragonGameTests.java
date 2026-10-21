package com.tnc.tnc.world.ink;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.*;
@GameTestHolder("tncink") @PrefixGameTestTemplate(false)
public final class InkCelestialDragonGameTests {
    private static InkCelestialDragonEntity dragon(GameTestHelper h){
        var e=h.spawn(InkBossRegistration.INK_CELESTIAL_DRAGON,new BlockPos(5,80,5));e.setGroundedForObservation(false);e.setNoAi(true);return e;
    }
    private static net.minecraft.world.entity.animal.Pig victim(GameTestHelper h){
        var e=h.spawn(net.minecraft.world.entity.EntityType.PIG,new BlockPos(-50,70,5));e.setNoAi(true);e.setNoGravity(true);
        e.getAttribute(Attributes.MAX_HEALTH).setBaseValue(200);e.setHealth(200);return e;
    }
    @GameTest(template="ink_crane_empty",timeoutTicks=90)
    public static void breathStopsAtWallAndDoesNotDamageThroughIt(GameTestHelper h){
        var e=dragon(h);var p=victim(h);
        for(int y=66;y<=134;y++)for(int z=1;z<=9;z++)h.setBlock(new BlockPos(-25,y,z),net.minecraft.world.level.block.Blocks.STONE);
        e.startInkAttack(1,p);
        h.runAtTickTime(65,()->{
            h.assertTrue(e.breathHit(),"Ray hits solid wall");h.assertTrue(p.getHealth()==200,"Wall shields target from beam and splash");
            h.assertBlockPresent(net.minecraft.world.level.block.Blocks.STONE,new BlockPos(-25,76,5));
            for(int y=66;y<=134;y++)for(int z=1;z<=9;z++)h.setBlock(new BlockPos(-25,y,z),net.minecraft.world.level.block.Blocks.AIR);
            e.discard();p.discard();h.succeed();
        });
    }
    @GameTest(template="ink_crane_empty",timeoutTicks=70)
    public static void dragonReallyFliesInOpenAir(GameTestHelper h){
        var e=dragon(h);e.setPos(e.getX(),h.getLevel().getMaxBuildHeight()-85,e.getZ());e.setNoAi(false);Vec3 at=e.position();
        for(int x=-15;x<=15;x++)for(int z=-15;z<=15;z++)h.getLevel().getChunkAt(e.blockPosition().offset(x*16,0,z*16));
        h.runAtTickTime(50,()->{boolean moved=e.position().distanceTo(at)>2&&e.isNoGravity();String detail=" ticks="+e.tickCount+" from="+at+" to="+e.position()+" noAI="+e.isNoAi();e.discard();h.assertTrue(moved,"Airborne cruise must actually move without gravity"+detail);h.succeed();});
    }
    @GameTest(template="ink_crane_empty",timeoutTicks=90)
    public static void breathOpensJawTracksTargetAndDealsDamage(GameTestHelper h){
        var e=dragon(h);var p=victim(h);h.assertTrue(e.startInkAttack(1,p),"Breath starts");
        h.assertTrue(!e.startInkAttack(2,p),"Cannot overlap moves");
        h.runAtTickTime(65,()->{h.assertTrue(e.jawOpen(0)>.95&&e.inkMove()==1,"Jaw remains open while breathing");
            h.assertTrue(p.getHealth()<200,"Actual server breath damages target");
            h.assertTrue(e.effectVector("point").distanceTo(e.mouthPosition())<=144.1,"Ray is range limited");e.discard();p.discard();h.succeed();});
    }
    @GameTest(template="ink_crane_empty",timeoutTicks=165)
    public static void diveActuallyHitsAndResets(GameTestHelper h){
        var e=dragon(h);e.setPos(e.position().add(0,90,0));var p=victim(h);h.assertTrue(e.startInkAttack(2,p),"Dive starts");
        h.runAtTickTime(60,()->h.assertTrue(e.effectVector("high").y>p.getY()+15,"Dragon ascends before gathering"));
        h.runAtTickTime(125,()->h.assertTrue(p.getHealth()<=168,"Impact applies server damage exactly at landing"));
        h.runAtTickTime(155,()->{h.assertTrue(e.inkMove()==0,"Move resets after burst");e.discard();p.discard();h.succeed();});
    }
    @GameTest(template="ink_crane_empty",timeoutTicks=35)
    public static void targetRemovalCancelsAndDoesNotLeaveOpenJaw(GameTestHelper h){
        var e=dragon(h);var p=victim(h);e.startInkAttack(1,p);
        h.runAtTickTime(20,p::discard);h.runAtTickTime(25,()->{h.assertTrue(e.inkMove()==0&&e.jawOpen(0)==0,"Removing victim cancels spell and jaw");e.discard();h.succeed();});
    }
    @GameTest(template="ink_crane_empty",timeoutTicks=70)
    public static void approvedDragonIsIndependentAndRemainsHoveringWithoutGriefing(GameTestHelper h){
        var dragon=h.spawn(InkBossRegistration.INK_CELESTIAL_DRAGON,new BlockPos(5,8,5));dragon.setGroundedForObservation(false);dragon.setNoAi(true);Vec3 at=dragon.position();
        h.assertTrue(dragon.getMaxHealth()==1000&&dragon.getBbHeight()==30,"Dedicated server attributes and horizontal flight core registered");
        h.assertTrue(InkBossRegistration.INK_ABYSS_DRAKE!=null&&InkBossRegistration.INK_CRANE_BOSS!=null&&InkBossRegistration.INK_WINGED_TIGER!=null,"Older bosses remain registered");
        h.runAtTickTime(45,()->{h.assertTrue(dragon.position().distanceTo(at)<.01&&dragon.isNoGravity(),"Hovering anchor never falls or wanders");
            h.assertTrue(dragon.getTarget()==null&&!dragon.isPushable(),"Display incarnation does not invent aggression or push players");
            h.assertTrue(!dragon.removeWhenFarAway(100000),"Persistent dragon does not despawn");dragon.discard();h.succeed();});
    }
    @GameTest(template="ink_crane_empty",timeoutTicks=90)
    public static void groundObservationLandsStaysAndPersists(GameTestHelper h){
        h.setBlock(new BlockPos(5,4,5),net.minecraft.world.level.block.Blocks.STONE);
        var e=h.spawn(InkBossRegistration.INK_CELESTIAL_DRAGON,new BlockPos(5,80,5));e.setGroundedForObservation(true);
        var p=victim(h);
        h.assertTrue(e.isGroundedForObservation()&&!e.startInkAttack(1,p),"Default ground observation rejects attacks");
        h.runAtTickTime(5,()->{
            int surface=h.getLevel().getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,e.blockPosition().getX(),e.blockPosition().getZ());
            h.assertTrue(Math.abs(e.getY()-surface)<.01,"Anchor lands at terrain surface");
            Vec3 at=e.position();
            var tag=new net.minecraft.nbt.CompoundTag();e.saveWithoutId(tag);
            var restored=InkBossRegistration.INK_CELESTIAL_DRAGON.create(h.getLevel());restored.load(tag);
            h.assertTrue(restored.isGroundedForObservation(),"Ground mode survives save/load");
            tag.remove("InkGroundObservation");restored.load(tag);
            h.assertTrue(!restored.isGroundedForObservation(),"Missing mode defaults to flying");
            tag.putBoolean("InkGroundObservation",true);tag.putBoolean("NoAI",true);tag.remove("InkMotionVersion");restored.load(tag);
            h.assertTrue(!restored.isGroundedForObservation()&&!restored.isNoAi(),"Old observation saves migrate back to active flight, including old display-only NoAI flags");
            h.runAtTickTime(70,()->{
                h.assertTrue(e.position().distanceTo(at)<.001&&e.getTarget()==null&&e.inkMove()==0,"AI-enabled grounded dragon stays put without combat");
                e.discard();p.discard();h.succeed();
            });
        });
    }
    @GameTest(template="ink_crane_empty",timeoutTicks=25)
    public static void modelEntitySurvivesSaveLoadAndDragonCommandIsOperatorOnly(GameTestHelper h){
        var dragon=h.spawn(InkBossRegistration.INK_CELESTIAL_DRAGON,new BlockPos(5,8,5));dragon.setYRot(123);dragon.setHealth(777);
        var tag=new net.minecraft.nbt.CompoundTag();dragon.saveWithoutId(tag);
        var restored=InkBossRegistration.INK_CELESTIAL_DRAGON.create(h.getLevel());restored.load(tag);
        h.assertTrue(restored.isNoGravity()&&restored.getHealth()==777&&restored.position().distanceTo(dragon.position())<.01,"Save retains health, anchor and gravity");
        var dispatcher=h.getLevel().getServer().getCommands().getDispatcher();var source=h.getLevel().getServer().createCommandSourceStack();
        h.assertTrue(!dispatcher.parse("tnc ink dragon",source.withPermission(2)).getReader().canRead(),"Operator command parses completely");
        h.assertTrue(dispatcher.parse("tnc ink dragon",source.withPermission(0)).getReader().canRead(),"Non-operator cannot use ink commands");
        dragon.discard();h.succeed();
    }
}
