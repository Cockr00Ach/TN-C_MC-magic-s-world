package com.tnc.tnc.dungeon.mirror;

import com.tnc.tnc.adventure.AdventureGameTests;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.*;
import java.util.UUID;

@GameTestHolder("tncmirror") @PrefixGameTestTemplate(false)
public final class MirrorDuelGameTests {
    @GameTest(template="building_test_empty",batch="echo_duel_mechanics",timeoutTicks=60)
    public static void terrainExpiryOwnershipCollapseAndQuench(GameTestHelper h){
        var l=h.getLevel();var ledger=MirrorTerrain.get(l);
        var at=h.absolutePos(new BlockPos(2,3,2));var run=UUID.randomUUID();
        for(int i=0;i<5;i++)l.setBlockAndUpdate(at.above(i),Blocks.AIR.defaultBlockState());
        h.assertTrue(ledger.place(l,run,at,"foot",at.asLong())&&ledger.place(l,run,at.above(),"stone",at.asLong()),"Two actual block placements");
        h.assertTrue(!ledger.breakTool(l,UUID.randomUUID(),at,new ItemStack(Items.IRON_PICKAXE))&&!ledger.breakTool(l,run,at,new ItemStack(Items.IRON_SWORD)),"Only owner encounter and suitable tool");
        h.assertTrue(ledger.breakTool(l,run,at,new ItemStack(Items.IRON_PICKAXE))&&l.isEmptyBlock(at.above()),"Breaking the base collapses its group");
        h.assertTrue(ledger.place(l,run,at,"web",at.asLong())&&ledger.breakTool(l,run,at,new ItemStack(Items.IRON_SWORD)),"Sword cuts the spectral web");
        h.assertTrue(ledger.place(l,run,at,"lava",at.asLong())&&ledger.cool(l,run,at)&&l.getBlockState(at).is(Blocks.OBSIDIAN),"Water quenches lava into a real solid");
        var reloaded=MirrorTerrain.read(l,ledger.save(new CompoundTag()));
        long now=l.getGameTime();reloaded.expire(l,now+199);
        h.assertTrue(l.getBlockState(at).is(Blocks.OBSIDIAN),"Cooling preserves original ten-second timer");
        reloaded.expire(l,now+200);ledger.expire(l,now+200);
        h.assertTrue(l.isEmptyBlock(at),"Original cell restored at expiry");
        h.assertTrue(ledger.place(l,run,at,"water",at.asLong()),"Recoverable water");
        l.setBlockAndUpdate(at,Blocks.DIAMOND_BLOCK.defaultBlockState());MirrorMelee.cleanup(l,run);
        h.assertTrue(l.getBlockState(at).is(Blocks.DIAMOND_BLOCK),"Foreign replacements survive cleanup");h.succeed();
    }
    @GameTest(template="building_test_empty",batch="echo_duel_mechanics",timeoutTicks=60)
    public static void waterFlowsSeveralBlocksAndDrainsAsOne(GameTestHelper h){
        var level=h.getLevel();var ledger=MirrorTerrain.get(level);
        var source=h.absolutePos(new BlockPos(3,3,3));var run=UUID.randomUUID();
        for(int x=-4;x<=4;x++)for(int z=-4;z<=4;z++){
            var at=source.offset(x,0,z);
            level.setBlockAndUpdate(at,Blocks.AIR.defaultBlockState());
            level.setBlockAndUpdate(at.below(),Blocks.STONE.defaultBlockState());
        }
        h.assertTrue(ledger.place(level,run,source,"water",source.asLong()),"Bucket fills source");
        for(int i=0;i<4;i++)ledger.spreadWater(level);
        h.assertTrue(!level.getFluidState(source.east(3)).isEmpty()
                    && !level.getFluidState(source.east(3)).isSource(),"Water spreads several blocks as flow");
        long now=level.getGameTime();
        ledger.expire(level,now+599);
        h.assertTrue(!level.getFluidState(source.east(3)).isEmpty(),"Water lasts 30 seconds");
        h.assertTrue(ledger.reclaimWater(level,run,source) && level.getFluidState(source.east(3)).isEmpty(),
                    "Picking up the source drains the field");
        h.assertTrue(ledger.place(level,run,source,"water",source.asLong()),"Second bucket");
        for(int i=0;i<4;i++)ledger.spreadWater(level);
        ledger.expire(level,now+600);
        h.assertTrue(level.getFluidState(source).isEmpty() && level.getFluidState(source.east(3)).isEmpty(),
                    "Source and flow expire together");
        h.succeed();
    }

    @GameTest(template="building_test_empty",batch="echo_duel_mechanics",timeoutTicks=60)
    public static void pearlThrowsAndRequiresASafeFloorImpact(GameTestHelper h){
        var p=AdventureGameTests.player(h);var m=duel(h,p);var level=h.getLevel();
        h.assertTrue(level.addFreshEntity(m),"Mirror owner added");
        var at=m.blockPosition().east(4).above();
        level.setBlockAndUpdate(at,Blocks.AIR.defaultBlockState());
        level.setBlockAndUpdate(at.above(),Blocks.AIR.defaultBlockState());
        level.setBlockAndUpdate(at.below(),Blocks.STONE.defaultBlockState());
        var brain=commit(m,p,"pearl",21);var aim=Vec3.atBottomCenterOf(at);
        brain.putDouble("AimX",aim.x);brain.putDouble("AimY",aim.y);brain.putDouble("AimZ",aim.z);
        for(int i=0;i<8;i++)MirrorMelee.tick(m,p);
        var pearls=level.getEntitiesOfClass(net.minecraft.world.entity.projectile.ThrownEnderpearl.class,
                m.getBoundingBox().inflate(8),pearl->pearl.getOwner()==m);
        h.assertTrue(pearls.size()==1 && brain.getInt("PearlsUsed")==1,"Mirror throws one physical pearl");
        var hit=new net.minecraft.world.phys.BlockHitResult(aim,net.minecraft.core.Direction.UP,at.below(),false);
        MirrorMelee.echoPearl(new net.minecraftforge.event.entity.ProjectileImpactEvent(pearls.get(0),hit));
        h.assertTrue(pearls.get(0).isRemoved() && m.position().distanceToSqr(aim)<.01,"Safe pillar top accepts landing");
        m.discard();h.succeed();
    }

    @GameTest(template="building_test_empty",batch="echo_duel_mechanics",timeoutTicks=60)
    public static void overheadLavaRequiresAnIntactWeb(GameTestHelper h){
        var p=AdventureGameTests.player(h);var m=duel(h,p);var level=h.getLevel();
        var at=p.blockPosition();
        for(int i=0;i<2;i++)level.setBlockAndUpdate(at.above(i),Blocks.AIR.defaultBlockState());
        var brain=commit(m,p,"web_lava",29);brain.putLong("Snare",at.asLong());
        for(int i=0;i<10;i++)MirrorMelee.tick(m,p);
        h.assertTrue(level.getBlockState(at).is(Blocks.COBWEB) && m.getMainHandItem().is(Items.LAVA_BUCKET)
                && level.isEmptyBlock(at.above()),"Web comes first, with a visible bucket wind-up");
        for(int i=0;i<9;i++)MirrorMelee.tick(m,p);
        h.assertTrue(level.getBlockState(at.above()).is(Blocks.LAVA) && !brain.getBoolean("Lava"),"Lava lands above trapped target");
        MirrorMelee.cleanup(level,m.runId());
        brain=commit(m,p,"web_lava",29);brain.putLong("Snare",at.asLong());brain.putBoolean("Snared",false);
        brain.putBoolean("Lava",true);
        for(int i=0;i<10;i++)MirrorMelee.tick(m,p);
        h.assertTrue(MirrorTerrain.get(level).breakTool(level,m.runId(),at,new ItemStack(Items.IRON_SWORD)),
                "Cut the web during the bucket wind-up");
        for(int i=0;i<9;i++)MirrorMelee.tick(m,p);
        h.assertTrue(level.isEmptyBlock(at.above()) && brain.getBoolean("Lava"),"Breaking the snare stops the follow-up");
        h.succeed();
    }

    private static EchoDouble duel(GameTestHelper h,ServerPlayer p){
        var c=h.absolutePos(new BlockPos(3,2,3));var m=MirrorEntities.DOUBLE.get().create(h.getLevel());
        m.bind(p,UUID.randomUUID(),c);m.enterMelee();m.setPos(Vec3.atBottomCenterOf(c));p.setPos(m.position().add(0,0,2));
        return m;
    }
    private static CompoundTag commit(EchoDouble m,ServerPlayer p,String move,int end){
        MirrorMelee.tick(m,p);
        var s=m.getPersistentData().getCompound("TncEchoDuel");
        s.putUUID("Opponent",p.getUUID());s.putString("Move",move);s.putInt("Age",0);s.putInt("End",end);return s;
    }
    @GameTest(template="building_test_empty",batch="echo_duel_mechanics",timeoutTicks=60)
    public static void realShieldSurvivesAxeAndSideStepBeatsLockedSwing(GameTestHelper h){
        var p=AdventureGameTests.player(h);var m=duel(h,p);
        p.setYRot(180);p.setItemSlot(EquipmentSlot.OFFHAND,new ItemStack(Items.SHIELD));
        p.startUsingItem(InteractionHand.OFF_HAND);
        p.setNoGravity(true);for(int i=0;i<6;i++)p.doTick();
        h.assertTrue(p.isBlocking(),"Actual shield raised");
        var s=commit(m,p,"axe",26);
        for(int i=0;i<13;i++)MirrorMelee.tick(m,p);
        h.assertTrue(!p.getCooldowns().isOnCooldown(Items.SHIELD),"Thirteen ticks of readable warning");
        MirrorMelee.tick(m,p);
        h.assertTrue(p.getCooldowns().isOnCooldown(Items.SHIELD)&&p.getOffhandItem().is(Items.SHIELD),"Only t14 disables shield and keeps physical item");
        s.putInt("Age",0);s.putString("Move","axe");p.setHealth(20);
        for(int i=0;i<9;i++)MirrorMelee.tick(m,p);
        p.setPos(m.position().add(-2,0,0));
        for(int i=0;i<5;i++)MirrorMelee.tick(m,p);
        h.assertTrue(p.getHealth()==20,"Side step after shoulder lock evades damage");
        h.succeed();
    }
    @GameTest(template="building_test_empty",batch="echo_duel_mechanics",timeoutTicks=60)
    public static void eatingCanBeInterruptedAndConsumesOneOfTwoActualFruit(GameTestHelper h){
        var p=AdventureGameTests.player(h);var m=duel(h,p);var s=commit(m,p,"apple",32);
        m.setHealth(8);
        for(int i=0;i<14;i++)MirrorMelee.tick(m,p);
        m.hurt(m.damageSources().playerAttack(p),2);MirrorMelee.tick(m,p);
        h.assertTrue(s.getString("Move").equals("recover")&&s.getInt("Apples")==64&&!m.hasEffect(MobEffects.ABSORPTION),"Valid incoming damage interrupts an unfinished bite");
        s.putBoolean("Interrupted",false);s.putString("Move","apple");s.putInt("Age",0);s.putInt("End",32);
        for(int i=0;i<31;i++)MirrorMelee.tick(m,p);
        h.assertTrue(!m.hasEffect(MobEffects.ABSORPTION),"Full use time required");
        MirrorMelee.tick(m,p);
        h.assertTrue(s.getInt("Apples")==63&&m.hasEffect(MobEffects.ABSORPTION)&&m.hasEffect(MobEffects.REGENERATION),"Normal golden apple grants real effects");
        h.succeed();
    }
    @GameTest(template="building_test_empty",batch="echo_duel_mechanics",timeoutTicks=110)
    public static void jumpsBuildIndividualBlocksAndReachBowPerch(GameTestHelper h){
        var p=AdventureGameTests.player(h);var m=duel(h,p);var l=h.getLevel();var at=m.blockPosition();
        for(int x=-1;x<=1;x++)for(int z=-1;z<=1;z++)for(int y=-1;y<10;y++)
            l.setBlockAndUpdate(at.offset(x,y,z),(y<0?Blocks.STONE:Blocks.AIR).defaultBlockState());
        p.setPos(m.position().add(0,4,5));l.addFreshEntity(m);m.setOnGround(true);
        int[] frames={0},previous={0};boolean[] sawBow={false};
        h.onEachTick(()->{
            if(!m.isAlive())return;
            MirrorMelee.tick(m,p);frames[0]++;
            int count=0;
            for(int i=0;i<5;i++)if(l.getBlockState(at.above(i)).is(i==0?Blocks.CHISELED_DEEPSLATE:Blocks.COBBLESTONE))count++;
            h.assertTrue(count<=previous[0]+1,"No instantly generated stack");previous[0]=count;
            if(count>=4&&m.getMainHandItem().is(Items.BOW))sawBow[0]=true;
        });
        h.succeedWhen(()->{
            h.assertTrue(sawBow[0],"Wait for four real jumps, blocks and drawn bow; blocks="+previous[0]+" y="+m.getY()+" ticks="+frames[0]+" state="+m.getPersistentData().getCompound("TncEchoDuel"));
            MirrorMelee.cleanup(l,m.runId());m.discard();
        });
    }
    @GameTest(template="building_test_empty",batch="echo_duel_mechanics",timeoutTicks=60)
    public static void hookLeavesHandOnEightAndAJumpCutNeedsDescent(GameTestHelper h){
        var p=AdventureGameTests.player(h);var m=duel(h,p);var l=h.getLevel();
        p.setNoGravity(true);p.setPos(m.position().add(0,0,6));
        var s=commit(m,p,"rod",26);
        s.putDouble("AimX",p.getX());s.putDouble("AimY",p.getY()+1);s.putDouble("AimZ",p.getZ());
        var volume=m.getBoundingBox().inflate(2);
        for(int i=0;i<7;i++)MirrorMelee.tick(m,p);
        h.assertTrue(l.getEntitiesOfClass(MirrorHook.class,volume).isEmpty(),"Backswing must finish before projectile exists");
        MirrorMelee.tick(m,p);
        var hooks=l.getEntitiesOfClass(MirrorHook.class,volume);
        h.assertTrue(hooks.size()==1&&hooks.get(0).getDeltaMovement().length()>.9,"One travelling physical hook from rod hand");
        hooks.forEach(MirrorHook::discard);
        // A newly constructed ServerPlayer also has a private 60-tick login shield.
        // Advance that real lifecycle before testing normal PvP damage.
        for(int i=0;i<61;i++)p.tick();
        p.setPos(m.position().add(0,0,2));p.setHealth(20);p.invulnerableTime=0;
        s.putString("Move","leap");s.putInt("Age",4);s.putInt("End",23);s.putBoolean("Swung",false);
        m.setOnGround(false);m.setDeltaMovement(0,.13,0);
        MirrorMelee.tick(m,p);h.assertTrue(p.getHealth()==20,"Rising into the player is not a critical hit");
        m.setDeltaMovement(0,-.12,0);MirrorMelee.tick(m,p);
        h.assertTrue(p.getHealth()<20&&s.getBoolean("Swung"),"One descending sword hit: hp="+p.getHealth()+" swing="+s.getBoolean("Swung")
                +" range="+m.distanceTo(p)+" aim="+m.getLookAngle()+" LOS="+m.hasLineOfSight(p));float after=p.getHealth();
        for(int i=0;i<5;i++)MirrorMelee.tick(m,p);
        h.assertTrue(p.getHealth()==after,"No repeated damage in the same swing");h.succeed();
    }
    @GameTest(template="building_test_empty",batch="echo_duel_mechanics",timeoutTicks=60)
    public static void pressurePreviewsWebBeforeItAppearsThenSprintsAway(GameTestHelper h){
        var p=AdventureGameTests.player(h);var m=duel(h,p);var level=h.getLevel();
        var s=commit(m,p,"recover",1);s.remove("Move");s.putInt("Pressure",2);s.putInt("HitAt",s.getInt("Clock"));s.putBoolean("Lava",false);s.putInt("ReadyPillar",Integer.MAX_VALUE);s.putInt("ReadyTrap",0);
        var at=m.blockPosition();level.setBlockAndUpdate(at.offset(0,0,1),Blocks.AIR.defaultBlockState());
        MirrorMelee.tick(m,p);
        h.assertTrue(s.getString("Move").equals("web")&&m.getMainHandItem().is(Items.COBWEB),"Mirror shows web before laying trap");
        var trap=p.blockPosition().east();
        level.setBlockAndUpdate(trap,Blocks.AIR.defaultBlockState());
        p.setPos(Vec3.atBottomCenterOf(trap));
        for(int i=0;i<6;i++)MirrorMelee.tick(m,p);
        h.assertTrue(!level.getBlockState(trap).is(Blocks.COBWEB),"Retreating hand telegraphs placement");
        MirrorMelee.tick(m,p);
        h.assertTrue(level.getBlockState(trap).is(Blocks.COBWEB)&&s.getInt("Webs")==7,"One web placed, limited stock");
        for(int i=0;i<6;i++)MirrorMelee.tick(m,p);
        h.assertTrue(s.getString("Move").equals("flee")&&m.isSprinting(),"Turns and sprints after sealing route");
        h.assertTrue(MirrorTerrain.get(level).breakTool(level,m.runId(),trap,new ItemStack(Items.IRON_SWORD)),"Attackers can cut the actual web");
        h.succeed();
    }
    @GameTest(template="building_test_empty",batch="echo_duel_mechanics",timeoutTicks=60)
    public static void retreatBuildsFourStepsAndShootsFromAbove(GameTestHelper h){
        var p=AdventureGameTests.player(h);var m=duel(h,p);var level=h.getLevel();
        var base=m.blockPosition();
        for(int z=0;z<=3;z++)for(int y=0;y<=6;y++)level.setBlockAndUpdate(base.offset(0,y,z),Blocks.AIR.defaultBlockState());
        level.setBlockAndUpdate(base.below(),Blocks.STONE.defaultBlockState());
        var s=commit(m,p,"flee",38);
        s.putInt("Age",7);s.putString("After","pillar");
        s.putDouble("RouteX",m.getX());s.putDouble("RouteY",m.getY());s.putDouble("RouteZ",m.getZ());
        m.setOnGround(true);MirrorMelee.tick(m,p);
        h.assertTrue(s.getString("Move").equals("pillar"),"A completed retreat starts the building move");
        for(int i=0;i<4;i++){
            m.setPos(Vec3.atBottomCenterOf(base.above(i+1)).add(0,.15,0));
            m.setOnGround(false);m.setDeltaMovement(0,.11,0);
            MirrorMelee.tick(m,p);
            h.assertTrue(level.getBlockState(base.above(i)).is(i==0?Blocks.CHISELED_DEEPSLATE:Blocks.COBBLESTONE),
                    "Each jump places a real temporary block");
        }
        m.setPos(Vec3.atBottomCenterOf(base.above(4)));m.setOnGround(true);m.setDeltaMovement(Vec3.ZERO);
        MirrorMelee.tick(m,p);
        h.assertTrue(s.getString("Move").equals("bow"),"Reaching the top equips a bow");
        for(int i=0;i<68;i++)MirrorMelee.tick(m,p);
        h.assertTrue(s.getInt("Arrows")==61,"Sustained line of sight yields exactly three shots");
        MirrorMelee.cleanup(level,m.runId());h.succeed();
    }

    @GameTest(template="building_test_empty",batch="echo_duel_mechanics",timeoutTicks=60)
    public static void bucketNeedsLegalLandingAndDryHandsAfterScoop(GameTestHelper h){
        var p=AdventureGameTests.player(h);var m=duel(h,p);var l=h.getLevel();var at=m.blockPosition();
        l.setBlockAndUpdate(at.below(),Blocks.STONE.defaultBlockState());l.setBlockAndUpdate(at,Blocks.AIR.defaultBlockState());
        var s=commit(m,p,"recover",1);m.setPos(Vec3.atBottomCenterOf(at).add(0,1.7,0));
        m.setOnGround(false);m.fallDistance=7;m.setDeltaMovement(0,-1.2,0);
        MirrorMelee.tick(m,p);
        h.assertTrue(s.getString("Move").equals("water")&&m.getMainHandItem().is(Items.WATER_BUCKET),"Sees fall and equips real bucket");
        MirrorMelee.tick(m,p);
        h.assertTrue(l.getBlockState(at).is(Blocks.WATER)&&m.getMainHandItem().is(Items.BUCKET),"Source placed at actual collision ray landing");
        h.assertTrue(m.fallDistance>=5,"Knockback before landing is still dangerous");
        m.setPos(Vec3.atBottomCenterOf(at));m.fallDistance=0;
        for(int i=0;i<14;i++)MirrorMelee.tick(m,p);
        h.assertTrue(l.isEmptyBlock(at),"Actual landing water gets scooped after the punish window");
        h.succeed();
    }
}
