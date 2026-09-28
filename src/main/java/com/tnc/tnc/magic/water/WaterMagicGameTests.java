package com.tnc.tnc.magic.water;

import com.mojang.authlib.GameProfile;
import com.tnc.tnc.magic.TNOrbEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.gametest.*;
import java.util.UUID;
import java.util.function.Consumer;

@GameTestHolder("tnc")
@PrefixGameTestTemplate(false)
public final class WaterMagicGameTests {
    @GameTest(template="building_test_empty",timeoutTicks=30)
    public static void independentChaosLearnsWithoutAffinityAndSurvivesReload(GameTestHelper h) {
        var id=net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("tnc","chaos_magic");
        var entry=com.tnc.tnc.magic.SpellCatalog.byId(id);
        var data=new com.tnc.tnc.magic.MagicStoneData();data.addBonusPoints(10);
        h.assertTrue(entry!=null&&entry.independent()&&entry.element()==null,"Chaos is not an eighth element");
        h.assertTrue(com.tnc.tnc.magic.Element.values().length==7,"Seven-element storage unchanged");
        h.assertTrue(com.tnc.tnc.magic.MagicStoneLearning.unlock(data,entry)==com.tnc.tnc.magic.MagicStoneLearning.Result.OK,"Zero affinity can learn chaos");
        int spent=data.getPointsSpent();
        h.assertTrue(com.tnc.tnc.magic.MagicStoneLearning.unlock(data,entry)==com.tnc.tnc.magic.MagicStoneLearning.Result.ALREADY_LEARNED&&data.getPointsSpent()==spent,"Duplicate unlock cannot charge twice");
        var loaded=new com.tnc.tnc.magic.MagicStoneData();loaded.deserializeNBT(data.serializeNBT());
        h.assertTrue(loaded.hasLearned(id)&&com.tnc.tnc.magic.SpellCatalog.effectiveIds(loaded).contains(id),"Independent spell survives NBT and reaches wand");
        for(var element:com.tnc.tnc.magic.Element.values())h.assertTrue(loaded.getProgressMax(element)==0,"Chaos cannot advance elemental progression");
        loaded.forget(id);h.assertTrue(!com.tnc.tnc.magic.SpellCatalog.effectiveIds(loaded).contains(id),"Forget removes it from wand");h.succeed();
    }
    @GameTest(template="building_test_empty",timeoutTicks=30)
    public static void slashSpawnsAllTwentySixThreeDimensionalDirections(GameTestHelper h) {
        var p=caster(h);p.setYRot(17);
        TNWaterFieldEntity.cast(p,net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("tnc","wave_slash"));
        var fields=h.getLevel().getEntitiesOfClass(TNWaterFieldEntity.class,p.getBoundingBox().inflate(3));
        h.assertTrue(fields.size()==26,"Expected 26 slash fronts, got "+fields.size());
        h.assertTrue(fields.stream().anyMatch(e->e.direction().y>.99)&&fields.stream().anyMatch(e->e.direction().y<-.99),"Both vertical poles synchronized");
        var directions=WaterSpellRules.slashDirections(17);
        h.assertTrue(directions.size()==26,"26 planned directions");
        for(int i=0;i<directions.size();i++)for(int j=i+1;j<directions.size();j++)h.assertTrue(directions.get(i).distanceToSqr(directions.get(j))>.01,"No duplicate directions");
        fields.forEach(Entity::discard);h.succeed();
    }
    @GameTest(template="water_test_large_empty",timeoutTicks=30)
    public static void largerBeamReachesBeyondOldRadiusWithinBudget(GameTestHelper h) {
        h.assertTrue(WaterSpellRules.radius(5)==15&&WaterSpellRules.range(5)==96&&WaterSpellRules.radius(5)==WaterSpellRules.circleRadius(5),"Beam covers the whole main sigil");
        h.assertTrue(WaterSpellRules.radius(4)==.8,"Other water lasers remain unchanged");
        var p=caster(h);Vec3 start=Vec3.atCenterOf(h.absolutePos(new BlockPos(24,24,24)));
        BlockPos outer=BlockPos.containing(start).offset(14,0,0);h.getLevel().setBlockAndUpdate(outer,Blocks.STONE.defaultBlockState());
        var bore=new WaterTerrainBore(start,new Vec3(0,0,1),WaterSpellRules.radius(5),96);int edits=bore.tick(h.getLevel(),p);
        h.assertTrue(edits<=WaterTerrainBore.BLOCK_BUDGET,"Expanded bore keeps edit budget");
        h.assertTrue(h.getLevel().getBlockState(outer).isAir(),"Radius fourteen is no longer clamped to seven: "+bore.stopReason());h.succeed();
    }
    @GameTest(template="water_test_large_empty",timeoutTicks=30)
    public static void fullApertureSliceRespectsBudgetAcrossTicks(GameTestHelper h) {
        var p=caster(h);Vec3 start=Vec3.atCenterOf(h.absolutePos(new BlockPos(24,24,24)));
        int blocks=0;
        for(int x=-14;x<=14;x++)for(int y=-14;y<=14;y++)if(x*x+y*y<=14*14) {
            h.getLevel().setBlockAndUpdate(BlockPos.containing(start).offset(x,y,0),Blocks.STONE.defaultBlockState());blocks++;
        }
        var bore=new WaterTerrainBore(start,new Vec3(0,0,1),WaterSpellRules.radius(5),1);
        int removed=bore.tick(h.getLevel(),p);
        h.assertTrue(removed==WaterTerrainBore.BLOCK_BUDGET&&bore.length()==0,"Full aperture must not advance before its first slice is committed");
        for(int i=0;i<10&&bore.length()==0;i++) {
            int edits=bore.tick(h.getLevel(),p);
            h.assertTrue(edits<=WaterTerrainBore.BLOCK_BUDGET,"Full aperture retains per-tick edit budget");removed+=edits;
        }
        h.assertTrue(removed==blocks&&bore.length()==1&&!bore.stopped(),"Pending full-aperture edits complete without loss: "+bore.stopReason());h.succeed();
    }
    @GameTest(template="water_test_large_empty",timeoutTicks=30)
    public static void fullApertureContainerAtEdgeStopsBeforeAnyEdits(GameTestHelper h) {
        var p=caster(h);Vec3 start=Vec3.atCenterOf(h.absolutePos(new BlockPos(24,24,24)));
        BlockPos center=BlockPos.containing(start),container=center.offset(14,0,0);
        h.getLevel().setBlockAndUpdate(center,Blocks.STONE.defaultBlockState());
        h.getLevel().setBlockAndUpdate(container,Blocks.CHEST.defaultBlockState());
        var bore=new WaterTerrainBore(start,new Vec3(0,0,1),WaterSpellRules.radius(5),1);
        h.assertTrue(bore.tick(h.getLevel(),p)==0&&bore.stopped()&&bore.length()==0,"Protection covers the new outer aperture before editing");
        h.assertTrue(h.getLevel().getBlockState(center).is(Blocks.STONE)&&h.getLevel().getBlockState(container).is(Blocks.CHEST),"Whole blocked slice and container remain intact");h.succeed();
    }
    private static ServerPlayer caster(GameTestHelper h) {
        var player=FakePlayerFactory.get(h.getLevel(),new GameProfile(UUID.randomUUID(),"water-test"));
        player.setGameMode(GameType.CREATIVE);player.setPos(Vec3.atCenterOf(h.absolutePos(new BlockPos(1,3,1))));return player;
    }
    private static TNWaterFieldEntity crypt(GameTestHelper h,ServerPlayer p) {
        p.setPos(Vec3.atCenterOf(h.absolutePos(new BlockPos(32,8,20))));p.setYRot(0);p.setXRot(0);
        TNWaterFieldEntity.cast(p,net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("tnc","sea_god_crypt"));
        return h.getLevel().getEntitiesOfClass(TNWaterFieldEntity.class,p.getBoundingBox().inflate(48))
                .stream().filter(e->e.kind()==2&&e.tier()==5).findFirst().orElseThrow();
    }
    private static Zombie toughZombie(GameTestHelper h,Vec3 at) {
        var z=zombie(h,at);z.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH).setBaseValue(200);
        z.setHealth(200);return z;
    }
    @GameTest(template="water_test_crypt_empty",timeoutTicks=30)
    public static void seaGodSwordHitsOnceAtSevenSecondsAndLeavesFieldActive(GameTestHelper h) {
        var p=caster(h);var field=crypt(h,p);var enemy=toughZombie(h,field.position().add(25,0,0));
        float full=enemy.getHealth();
        field.tickCount=139;field.seaGodSwordImpact(h.getLevel(),p);
        h.assertTrue(enemy.getHealth()==full,"Sword cannot damage before seven seconds");
        field.tickCount=140;field.affect(h.getLevel(),p);
        float remaining=enemy.getHealth();
        // Zombies have innate armor: verify a large real hit rather than ignoring vanilla defenses.
        h.assertTrue(full-remaining>40&&full-remaining<=SeaGodSwordRules.DAMAGE*WaterSpellRules.power(p),"Large real sword hit reaches the field edge");
        enemy.invulnerableTime=0;field.seaGodSwordImpact(h.getLevel(),p);
        field.tickCount=141;field.seaGodSwordImpact(h.getLevel(),p);
        h.assertTrue(enemy.getHealth()==remaining,"Impact is once per cast even without target invulnerability");
        h.assertTrue(!field.isRemoved()&&field.life()==600,"Original thirty-second field persists after sword impact");
        enemy.discard();field.discard();h.succeed();
    }
    @GameTest(template="water_test_crypt_empty",timeoutTicks=30)
    public static void seaGodSwordRespectsWallsVerticalBoundsAndFriendlyTargets(GameTestHelper h) {
        var p=caster(h);var field=crypt(h,p);Vec3 center=field.position();
        Zombie exposed=toughZombie(h,center.add(4,0,0)),blocked=toughZombie(h,center.add(0,0,6)),outside=toughZombie(h,center.add(27,0,0));
        Zombie below=toughZombie(h,center.add(4,-4,0)),above=toughZombie(h,center.add(4,26,0)),ally=toughZombie(h,center.add(-4,0,0));
        var pet=EntityType.WOLF.create(h.getLevel());pet.setTame(true);pet.setOwnerUUID(p.getUUID());pet.setPos(center.add(-6,0,0));pet.setNoAi(true);h.getLevel().addFreshEntity(pet);
        float petHealth=pet.getHealth(),playerHealth=p.getHealth();
        var scoreboard=h.getLevel().getScoreboard();var team=scoreboard.addPlayerTeam("sword-"+UUID.randomUUID().toString().substring(0,8));
        scoreboard.addPlayerToTeam(p.getScoreboardName(),team);scoreboard.addPlayerToTeam(ally.getScoreboardName(),team);
        BlockPos wall=BlockPos.containing(center.add(0,0,3));
        h.getLevel().setBlockAndUpdate(wall,Blocks.STONE.defaultBlockState());h.getLevel().setBlockAndUpdate(wall.above(),Blocks.STONE.defaultBlockState());
        try {
            field.tickCount=140;field.seaGodSwordImpact(h.getLevel(),p);
            h.assertTrue(exposed.getHealth()<160,"Unobstructed enemy receives giant sword damage");
            for(var z:new Zombie[]{blocked,outside,below,above,ally})h.assertTrue(z.getHealth()==200,"Wall/radius/height/team safety retained: "+z.position());
            h.assertTrue(p.getHealth()==playerHealth&&pet.getHealth()==petHealth,"Caster and tame pet remain safe");
            h.assertTrue(h.getLevel().getBlockState(wall).is(Blocks.STONE),"Giant sword does not destroy terrain");
        } finally {
            scoreboard.removePlayerTeam(team);field.discard();pet.discard();
            for(var z:new Zombie[]{exposed,blocked,outside,below,above,ally})z.discard();
        }
        h.succeed();
    }
    @GameTest(template="water_test_large_empty",timeoutTicks=30)
    public static void controlAffectsNearbyEnemiesAndHealingUsesExpandedRadius(GameTestHelper h) {
        var p=caster(h);p.setPos(Vec3.atCenterOf(h.absolutePos(new BlockPos(6,8,2))));p.setYRot(0);p.setXRot(0);
        Zombie anchor=zombie(h,p.position().add(0,0,4)),near=zombie(h,p.position().add(4,0,4)),outside=zombie(h,p.position().add(9,0,4)),below=zombie(h,p.position().add(0,-4,4));
        TNWaterFieldEntity.cast(p,net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("tnc","water_bind"));
        var fields=h.getLevel().getEntitiesOfClass(TNWaterFieldEntity.class,p.getBoundingBox().inflate(12));
        var control=fields.stream().filter(e->e.kind()==2).findFirst().orElseThrow();control.tickCount=20;control.affect(h.getLevel(),p);
        h.assertTrue(anchor.hasEffect(net.minecraft.world.effect.MobEffects.MOVEMENT_SLOWDOWN)&&near.hasEffect(net.minecraft.world.effect.MobEffects.MOVEMENT_SLOWDOWN),"Low control tier covers nearby enemies, not just anchor");
        h.assertTrue(!outside.hasEffect(net.minecraft.world.effect.MobEffects.MOVEMENT_SLOWDOWN),"Outside control radius unaffected");
        h.assertTrue(!below.hasEffect(net.minecraft.world.effect.MobEffects.MOVEMENT_SLOWDOWN),"Below-floor enemy unaffected");control.discard();anchor.discard();near.discard();outside.discard();below.discard();
        TNWaterFieldEntity.cast(p,net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("tnc","raindrop"));
        var rain=h.getLevel().getEntitiesOfClass(TNWaterFieldEntity.class,p.getBoundingBox().inflate(40)).stream().filter(e->e.kind()==3).findFirst().orElseThrow();
        Vec3 center=rain.position();p.setPos(center.add(7,0,0));p.setHealth(10);rain.tickCount=20;rain.rain(h.getLevel(),p);
        h.assertTrue(p.getHealth()==11,"Rain heals at seven blocks, beyond old radius");p.setPos(center.add(9,0,0));rain.rain(h.getLevel(),p);
        h.assertTrue(p.getHealth()==11,"Outside rain radius not healed");rain.discard();h.succeed();
    }
    @GameTest(template="building_test_empty",timeoutTicks=30)
    public static void largerFieldsShareRadiusLifeAndHeight(GameTestHelper h) {
        double[] controls={5,7,12,18,26},rains={8,12,16,22,32};int[] seconds={8,12,18,24,30};
        for(int t=1;t<=5;t++) {
            h.assertTrue(WaterSpellRules.fieldRadius(2,t)==controls[t-1]&&WaterSpellRules.fieldLife(2,t)==seconds[t-1]*20,"Control scale and lifetime tier "+t);
            h.assertTrue(WaterSpellRules.fieldRadius(3,t)==rains[t-1],"Healing radius tier "+t);
            h.assertTrue(WaterSpellRules.fieldLife(3,t)==200+t*100,"Healing duration unchanged");
            h.assertTrue(WaterSpellRules.fieldHeight(2,t)>=7,"Cage vertical coverage");
        }
        h.assertTrue(WaterSpellRules.fieldRadius(1,5)==40&&WaterSpellRules.fieldHeight(1,5)==28&&WaterSpellRules.fieldLife(1,5)==160,"Apocalyptic sea scale");
        var sea=WaterSpellRules.uprightArea(Vec3.ZERO,40,28);
        h.assertTrue(sea.minY==-.25&&sea.maxY==28,"Sea grows above its floor, not equally underground");h.succeed();
    }
    @GameTest(template="water_test_large_empty",timeoutTicks=30)
    public static void dragonLaunchClearsFlatGrassWithoutRemovingProtection(GameTestHelper h) {
        var p=caster(h);p.setPos(Vec3.atCenterOf(h.absolutePos(new BlockPos(24,2,24))).subtract(0,.5,0));p.setYRot(0);p.setXRot(0);
        for(int x=0;x<48;x++)for(int z=0;z<48;z++) {
            h.getLevel().setBlockAndUpdate(h.absolutePos(new BlockPos(x,1,z)),Blocks.GRASS_BLOCK.defaultBlockState());
            h.getLevel().setBlockAndUpdate(h.absolutePos(new BlockPos(x,2,z)),Blocks.GRASS.defaultBlockState());
        }
        var old=new WaterTerrainBore(p.getEyePosition().add(0,0,2),new Vec3(0,0,1),7,1);old.tick(h.getLevel(),p);
        h.assertTrue(old.stopped()&&old.length()==0,"Reproduce original eye-height grass obstruction");
        TNWaterSpellEntity.cast(p,net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("tnc","dragon_ruin"));
        var cast=h.getLevel().getEntitiesOfClass(TNWaterSpellEntity.class,p.getBoundingBox().inflate(20)).get(0);
        var bore=new WaterTerrainBore(cast.position(),cast.direction(),WaterSpellRules.radius(5),1);bore.tick(h.getLevel(),p);
        Vec3 desired=p.getEyePosition().add(p.getLookAngle().scale(2)).add(0,WaterSpellRules.radius(5)-p.getEyeHeight()+.8,0);
        var obstruction=h.getLevel().clip(new net.minecraft.world.level.ClipContext(p.getEyePosition(),desired,net.minecraft.world.level.ClipContext.Block.COLLIDER,net.minecraft.world.level.ClipContext.Fluid.NONE,p));
        h.assertTrue(bore.length()>0,"Dragon muzzle must launch above its own ground footprint: "+cast.position()+" eye="+p.getEyePosition()+" dir="+cast.direction()+" look="+p.getLookAngle()+" obstruction="+obstruction+" block="+h.getLevel().getBlockState(obstruction.getBlockPos())+" stop="+bore.stopReason());
        cast.discard();h.succeed();
    }
    @GameTest(template="building_test_empty",timeoutTicks=30)
    public static void slashThinSweepIncludesVerticalEdgesButNotFutureTargets(GameTestHelper h) {
        Vec3 start=Vec3.ZERO,end=new Vec3(0,0,1.35),dir=new Vec3(0,0,1);
        var future=new net.minecraft.world.phys.AABB(-.2,-.2,3,.2,.2,3.4);
        h.assertTrue(!WaterSpellRules.slashIntersects(future,start,end,dir,4),"No early hit ahead of visible blade");
        for(double y:new double[]{-3.8,3.8}) {
            var edge=new net.minecraft.world.phys.AABB(-.1,y-.1,.5,.1,y+.1,.7);
            h.assertTrue(WaterSpellRules.slashIntersects(edge,start,end,dir,4),"Small flying target at vertical edge included");
            h.assertTrue(new net.minecraft.world.phys.AABB(start,end).inflate(4).intersects(edge),"Candidate bounds include edge");
        }
        var pole=new net.minecraft.world.phys.AABB(3.6,.5,-.1,3.8,.7,.1);
        h.assertTrue(WaterSpellRules.slashIntersects(pole,start,new Vec3(0,1.35,0),new Vec3(0,1,0),4),"Vertical slash has horizontal cross-section");h.succeed();
    }
    private static Zombie zombie(GameTestHelper h,Vec3 pos){var z=EntityType.ZOMBIE.create(h.getLevel());z.setPos(pos);z.setNoAi(true);z.setNoGravity(true);h.getLevel().addFreshEntity(z);return z;}
    @GameTest(template="building_test_empty",timeoutTicks=30)
    public static void projectileHitsOnlyFirstEnemy(GameTestHelper h) {
        var p=caster(h);Vec3 start=Vec3.atCenterOf(h.absolutePos(new BlockPos(1,4,1)));
        Zombie a=zombie(h,start.add(0,-.6,1)),b=zombie(h,start.add(0,-.6,2));
        var bolt=TNOrbEntities.WATER_BOLT.get().create(h.getLevel());bolt.configure(p,1,start,new Vec3(0,0,3));bolt.tick();
        h.assertTrue(a.getHealth()<a.getMaxHealth(),"First target must be hurt");
        h.assertTrue(b.getHealth()==b.getMaxHealth(),"Second target must not take splash/piercing damage");
        h.assertTrue(bolt.isRemoved(),"Water projectile must end on first impact");a.discard();b.discard();h.succeed();
    }
    @GameTest(template="building_test_empty",timeoutTicks=30)
    public static void waterCannonIsStrongerAndWallBlocksBolt(GameTestHelper h) {
        h.assertTrue(WaterSpellRules.damage(2)>WaterSpellRules.damage(1),"Cannon must be stronger");
        var p=caster(h);Vec3 start=Vec3.atCenterOf(h.absolutePos(new BlockPos(1,4,1)));
        var z=zombie(h,start.add(0,-.6,3));h.getLevel().setBlockAndUpdate(BlockPos.containing(start.add(0,0,1)),Blocks.STONE.defaultBlockState());
        var bolt=TNOrbEntities.WATER_BOLT.get().create(h.getLevel());bolt.configure(p,2,start,new Vec3(0,0,4));bolt.tick();
        h.assertTrue(z.getHealth()==z.getMaxHealth(),"Wall must block projectile");h.assertTrue(bolt.isRemoved(),"Wall consumes projectile");z.discard();h.succeed();
    }
    @GameTest(template="building_test_empty",timeoutTicks=30)
    public static void boreCutsOnlyFiniteCylinder(GameTestHelper h) {
        var p=caster(h);Vec3 start=Vec3.atCenterOf(h.absolutePos(new BlockPos(2,5,1)));
        BlockPos inside=BlockPos.containing(start.add(0,0,1)),outside=BlockPos.containing(start.add(3,0,1));
        h.getLevel().setBlockAndUpdate(inside,Blocks.STONE.defaultBlockState());h.getLevel().setBlockAndUpdate(outside,Blocks.STONE.defaultBlockState());
        var bore=new WaterTerrainBore(start,new Vec3(0,0,1),1,3);
        for(int i=0;i<8;i++)h.assertTrue(bore.tick(h.getLevel(),p)<=WaterTerrainBore.BLOCK_BUDGET,"Exceeded block budget");
        h.assertTrue(h.getLevel().getBlockState(inside).isAir(),"Stone in cylinder must be broken");
        h.assertTrue(h.getLevel().getBlockState(outside).is(Blocks.STONE),"Outside cylinder must remain");
        h.assertTrue(bore.length()<=3,"Finite range required");h.succeed();
    }
    @GameTest(template="building_test_empty",timeoutTicks=30)
    public static void containersAndBedrockStopBore(GameTestHelper h) {
        var p=caster(h);Vec3 start=Vec3.atCenterOf(h.absolutePos(new BlockPos(2,5,1)));
        BlockPos obstacle=BlockPos.containing(start.add(0,0,1));
        for(var block:new net.minecraft.world.level.block.Block[]{Blocks.CHEST,Blocks.BEDROCK}) {
            h.getLevel().setBlockAndUpdate(obstacle,block.defaultBlockState());
            var bore=new WaterTerrainBore(start,new Vec3(0,0,1),1,4);for(int i=0;i<8;i++)bore.tick(h.getLevel(),p);
            h.assertTrue(bore.stopped(),"Protected block must stop beam");
            h.assertTrue(h.getLevel().getBlockState(obstacle).is(block),"Protected block must survive");
            h.assertTrue(bore.length()<=1,"Visible beam must stop before protected slice");
        }h.succeed();
    }
    @GameTest(template="building_test_empty",timeoutTicks=30)
    public static void claimCancellationStopsDestruction(GameTestHelper h) {
        var p=caster(h);Vec3 start=Vec3.atCenterOf(h.absolutePos(new BlockPos(2,5,1)));BlockPos pos=BlockPos.containing(start);
        h.getLevel().setBlockAndUpdate(pos,Blocks.STONE.defaultBlockState());
        Consumer<BlockEvent.BreakEvent> listener=e->{if(e.getPlayer()==p)e.setCanceled(true);};
        MinecraftForge.EVENT_BUS.addListener(listener);
        try {var bore=new WaterTerrainBore(start,new Vec3(0,0,1),1,3);bore.tick(h.getLevel(),p);
            h.assertTrue(bore.stopped()&&bore.length()==0,"Claim rejection must stop beam");h.assertTrue(h.getLevel().getBlockState(pos).is(Blocks.STONE),"Claimed stone preserved");
        }finally{MinecraftForge.EVENT_BUS.unregister(listener);}h.succeed();
    }
    @GameTest(template="building_test_empty",timeoutTicks=100)
    public static void silenceBlocksNewProjectilesThenExpires(GameTestHelper h) {
        var z=zombie(h,Vec3.atCenterOf(h.absolutePos(new BlockPos(2,4,2))));z.setNoAi(false);
        h.assertTrue(ChaosSilence.apply(z),"Silence should apply to hostile mob");
        var arrow=new Arrow(h.getLevel(),z);h.assertTrue(!h.getLevel().addFreshEntity(arrow),"Silenced caster cannot create projectile");
        h.assertTrue(!z.isNoAi(),"Silence must not disable all AI");
        h.runAfterDelay(62,()->{h.assertTrue(!ChaosSilence.silenced(z),"Silence expires after 60 ticks");var after=new Arrow(h.getLevel(),z);
            h.assertTrue(h.getLevel().addFreshEntity(after),"Projectile allowed again after expiry");after.discard();z.discard();h.succeed();});
    }
    @GameTest(template="building_test_empty",timeoutTicks=30)
    public static void verticalAimHasStableCircleBasis(GameTestHelper h) {
        for(Vec3 dir:new Vec3[]{new Vec3(0,1,0),new Vec3(0,-1,0),new Vec3(0,0,1)}) {
            var r=WaterSpellRules.right(dir);h.assertTrue(Math.abs(r.length()-1)<.0001,"Basis unit length");h.assertTrue(Math.abs(r.dot(dir))<.0001,"Basis perpendicular");
        }h.succeed();
    }
    @GameTest(template="building_test_empty",timeoutTicks=30)
    public static void protectedAirStopsVisualFront(GameTestHelper h) {
        var p=caster(h);Vec3 start=Vec3.atCenterOf(h.absolutePos(new BlockPos(2,5,1)));
        Consumer<BlockEvent.BreakEvent> listener=e->{if(e.getPlayer()==p)e.setCanceled(true);};MinecraftForge.EVENT_BUS.addListener(listener);
        try {var bore=new WaterTerrainBore(start,new Vec3(0,0,1),1,3);bore.tick(h.getLevel(),p);h.assertTrue(bore.stopped()&&bore.length()==0,"Protected air must block beam");}
        finally{MinecraftForge.EVENT_BUS.unregister(listener);}h.succeed();
    }
    @GameTest(template="building_test_empty",timeoutTicks=30)
    public static void wetSolidIsNotFluidAndContainerSupportIsProtected(GameTestHelper h) {
        var wet=Blocks.OAK_STAIRS.defaultBlockState().setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.WATERLOGGED,true);
        h.assertTrue(!WaterTerrainBore.fluidOnly(wet),"Waterlogged stairs must not count as water");
        var p=caster(h);Vec3 start=Vec3.atCenterOf(h.absolutePos(new BlockPos(2,5,1)));BlockPos pos=BlockPos.containing(start);
        h.getLevel().setBlockAndUpdate(pos,Blocks.STONE.defaultBlockState());h.getLevel().setBlockAndUpdate(pos.above(),Blocks.CHEST.defaultBlockState());
        var bore=new WaterTerrainBore(start,new Vec3(0,0,1),.4,3);bore.tick(h.getLevel(),p);
        h.assertTrue(bore.stopped()&&h.getLevel().getBlockState(pos).is(Blocks.STONE),"Container support must not be removed");h.succeed();
    }
    @GameTest(template="building_test_empty",timeoutTicks=30)
    public static void weatherLeaseDetectsAdminChangesWithFrozenCycle(GameTestHelper h) {
        h.assertTrue(WaterWeather.ownsTimer(620,620,false),"Frozen timer unchanged");
        h.assertTrue(!WaterWeather.ownsTimer(620,12000,false),"Admin storm must override lease even with frozen cycle");
        h.assertTrue(WaterWeather.ownsTimer(620,619,true),"Normal timer decrement");
        h.assertTrue(!WaterWeather.ownsTimer(620,610,true),"External timer change");h.succeed();
    }
    @GameTest(template="building_test_empty",timeoutTicks=100)
    public static void ignitedCreeperSurvivesSilenceWithoutExploding(GameTestHelper h) {
        var creeper=EntityType.CREEPER.create(h.getLevel());creeper.setPos(Vec3.atCenterOf(h.absolutePos(new BlockPos(2,4,2))));
        creeper.setNoGravity(true);h.getLevel().addFreshEntity(creeper);creeper.ignite();ChaosSilence.apply(creeper);
        h.runAfterDelay(50,()->{h.assertTrue(creeper.isAlive(),"Ignited creeper must not explode or suicide during silence");
            h.assertTrue(!creeper.isNoAi(),"Creeper AI still active");creeper.discard();h.succeed();});
    }
    @GameTest(template="building_test_empty",timeoutTicks=30)
    public static void beamStopsBeforeProtectedCellFaceNotItsCenter(GameTestHelper h) {
        var p=caster(h);Vec3 start=Vec3.atCenterOf(h.absolutePos(new BlockPos(2,5,1)));
        int protectedZ=BlockPos.containing(start).getZ()+2;
        Consumer<BlockEvent.BreakEvent> listener=e->{if(e.getPlayer()==p && e.getPos().getZ()>=protectedZ)e.setCanceled(true);};MinecraftForge.EVENT_BUS.addListener(listener);
        try {var bore=new WaterTerrainBore(start,new Vec3(0,0,1),1,4);for(int i=0;i<8;i++)bore.tick(h.getLevel(),p);
            h.assertTrue(bore.stopped(),"Expected protection boundary");h.assertTrue(start.z+bore.length()<=protectedZ,"Beam endpoint must never enter protected cell by half a block");
        }finally{MinecraftForge.EVENT_BUS.unregister(listener);}h.succeed();
    }
    @GameTest(template="building_test_empty",timeoutTicks=30)
    public static void existingExplosiveProjectileIsNotErasedBySilencingOwner(GameTestHelper h) {
        var z=zombie(h,Vec3.atCenterOf(h.absolutePos(new BlockPos(2,4,2))));
        var projectile=new net.minecraft.world.entity.projectile.LargeFireball(h.getLevel(),z,0,0,1,1);
        ChaosSilence.apply(z);
        var explosion=new net.minecraft.world.level.Explosion(h.getLevel(),projectile,z.getX(),z.getY(),z.getZ(),1,false,net.minecraft.world.level.Explosion.BlockInteraction.KEEP);
        var event=new net.minecraftforge.event.level.ExplosionEvent.Start(h.getLevel(),explosion);ChaosSilence.explosion(event);
        h.assertTrue(!event.isCanceled(),"Already-fired projectile must retain explosion");z.discard();h.succeed();
    }
}
