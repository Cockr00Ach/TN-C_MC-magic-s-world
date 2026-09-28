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
        var fields=h.getLevel().getEntitiesOfClass(TNWaterFieldEntity.class,p.getBoundingBox().inflate(3),e->!e.isEmitter());
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
    public static void fullApertureBedrockAtEdgeStopsBeforeAnyEdits(GameTestHelper h) {
        var p=caster(h);Vec3 start=Vec3.atCenterOf(h.absolutePos(new BlockPos(24,24,24)));
        BlockPos center=BlockPos.containing(start),container=center.offset(14,0,0);
        h.getLevel().setBlockAndUpdate(center,Blocks.STONE.defaultBlockState());
        h.getLevel().setBlockAndUpdate(container,Blocks.BEDROCK.defaultBlockState());
        var bore=new WaterTerrainBore(start,new Vec3(0,0,1),WaterSpellRules.radius(5),1);
        h.assertTrue(bore.tick(h.getLevel(),p)==0&&bore.stopped()&&bore.length()==0,"Protection covers the new outer aperture before editing");
        h.assertTrue(h.getLevel().getBlockState(center).is(Blocks.STONE)&&h.getLevel().getBlockState(container).is(Blocks.BEDROCK),"Whole blocked slice and bedrock remain intact");h.succeed();
    }
    private static ServerPlayer caster(GameTestHelper h) {
        var player=FakePlayerFactory.get(h.getLevel(),new GameProfile(UUID.randomUUID(),"water-test"));
        player.setGameMode(GameType.CREATIVE);player.setPos(Vec3.atCenterOf(h.absolutePos(new BlockPos(1,3,1))));return player;
    }
    @GameTest(template="water_test_large_empty",timeoutTicks=30)
    public static void cannonIsLargerAndDragonRoarSustainsTenSeconds(GameTestHelper h) {
        h.assertTrue(WaterSpellRules.boltRadius(2)>WaterSpellRules.boltRadius(1)*2,"Cannon is clearly larger than a water ball");
        h.assertTrue(WaterSpellRules.duration(3)==200,"Dragon Roar emits for ten seconds");h.succeed();
    }
    @GameTest(template="water_test_large_empty",timeoutTicks=30)
    public static void slashEmitterReleasesLaterVolleyWithoutInfiniteFrontLifetime(GameTestHelper h) {
        var p=caster(h);var owners=testOwnerMap(h);owners.put(p.getUUID(),p);
        try {
            TNWaterFieldEntity.cast(p,net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("tnc","wave_slash"));
            var fields=h.getLevel().getEntitiesOfClass(TNWaterFieldEntity.class,p.getBoundingBox().inflate(4));
            var emitter=fields.stream().filter(TNWaterFieldEntity::isEmitter).findFirst().orElseThrow();
            h.assertTrue(emitter.life()==200,"Controller lasts ten seconds");
            fields.stream().filter(e->!e.isEmitter()).forEach(Entity::discard);
            // ServerLevel advances Entity.tickCount before tick(); direct fixture calls do not.
            emitter.tickCount=20;emitter.tick();
            var fronts=h.getLevel().getEntitiesOfClass(TNWaterFieldEntity.class,p.getBoundingBox().inflate(4),e->!e.isEmitter());
            h.assertTrue(fronts.size()==26&&fronts.stream().allMatch(e->e.life()==28),"Later volley has all directions and bounded per-front range; count="+fronts.size()+" tick="+emitter.tickCount);
            h.getLevel().getEntitiesOfClass(TNWaterFieldEntity.class,p.getBoundingBox().inflate(4)).forEach(Entity::discard);
        }finally{owners.remove(p.getUUID());}h.succeed();
    }
    @GameTest(template="water_test_large_empty",timeoutTicks=30)
    public static void boreDestroysContainersUnbreakablesAndLeaves(GameTestHelper h) {
        var p=caster(h);Vec3 start=Vec3.atCenterOf(h.absolutePos(new BlockPos(24,24,24)));
        BlockPos pos=BlockPos.containing(start);var level=h.getLevel();
        level.setBlockAndUpdate(pos,Blocks.CHEST.defaultBlockState());
        var chest=(net.minecraft.world.level.block.entity.ChestBlockEntity)level.getBlockEntity(pos);
        chest.setItem(0,new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.DIAMOND,3));
        level.setBlockAndUpdate(pos.offset(0,0,1),Blocks.BARRIER.defaultBlockState());
        level.setBlockAndUpdate(pos.offset(0,0,2),Blocks.OAK_LEAVES.defaultBlockState());
        var bore=new WaterTerrainBore(start,new Vec3(0,0,1),.4,3);
        for(int i=0;i<4;i++)bore.tick(level,p);
        h.assertTrue(!bore.stopped()&&level.getBlockState(pos).isAir()&&level.getBlockState(pos.offset(0,0,1)).isAir()
                &&level.getBlockState(pos.offset(0,0,2)).isAir(),"Only bedrock is material-protected: "+bore.stopReason());
        var loot=level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,new net.minecraft.world.phys.AABB(pos).inflate(2));
        h.assertTrue(loot.stream().filter(e->e.getItem().is(net.minecraft.world.item.Items.DIAMOND)).mapToInt(e->e.getItem().getCount()).sum()==3,"Container contents retain vanilla drops");
        loot.forEach(Entity::discard);h.succeed();
    }
    @GameTest(template="water_test_large_empty",timeoutTicks=30)
    public static void borePreservesFilledShulkerInventory(GameTestHelper h) {
        var level=h.getLevel();var p=caster(h);BlockPos pos=h.absolutePos(new BlockPos(24,24,24));
        level.setBlockAndUpdate(pos,Blocks.BLUE_SHULKER_BOX.defaultBlockState());
        var box=(net.minecraft.world.level.block.entity.ShulkerBoxBlockEntity)level.getBlockEntity(pos);
        box.setItem(0,new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.DIAMOND,7));
        var bore=new WaterTerrainBore(Vec3.atCenterOf(pos),new Vec3(0,0,1),.4,1);bore.tick(level,p);
        var loot=level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,new net.minecraft.world.phys.AABB(pos).inflate(2));
        int diamonds=0,boxes=0;
        for(var item:loot) {
            var stack=item.getItem();
            if(stack.is(net.minecraft.world.item.Items.BLUE_SHULKER_BOX)) {
                boxes++;var inventory=net.minecraft.core.NonNullList.withSize(27,net.minecraft.world.item.ItemStack.EMPTY);
                var tag=net.minecraft.world.item.BlockItem.getBlockEntityData(stack);
                if(tag!=null)net.minecraft.world.ContainerHelper.loadAllItems(tag,inventory);
                diamonds+=inventory.stream().filter(s->s.is(net.minecraft.world.item.Items.DIAMOND)).mapToInt(net.minecraft.world.item.ItemStack::getCount).sum();
            }
            if(stack.is(net.minecraft.world.item.Items.DIAMOND))diamonds+=stack.getCount();
        }
        h.assertTrue(level.getBlockState(pos).isAir()&&boxes==1&&diamonds==7,"Filled shulker must retain exactly its seven diamonds; boxes="+boxes+" diamonds="+diamonds);
        loot.forEach(Entity::discard);h.succeed();
    }
    @GameTest(template="water_test_large_empty",timeoutTicks=30)
    public static void boreCallbacksCannotRemoveUnapprovedOutsideRedstone(GameTestHelper h) {
        var level=h.getLevel();var p=caster(h);BlockPos pos=h.absolutePos(new BlockPos(24,24,24));
        level.setBlock(pos.below(),Blocks.STONE.defaultBlockState(),2|16);
        level.setBlock(pos.east().below(),Blocks.STONE.defaultBlockState(),2|16);
        level.setBlock(pos,Blocks.REDSTONE_WIRE.defaultBlockState(),2|16);
        level.setBlock(pos.east(),Blocks.REDSTONE_WIRE.defaultBlockState(),2|16);
        // Unsupported outside wire remains until notified: removal callbacks used to notify it despite flags 2|16.
        level.setBlock(pos.east().below(),Blocks.AIR.defaultBlockState(),2|16);
        h.assertTrue(level.getBlockState(pos.east()).is(Blocks.REDSTONE_WIRE),"Fixture starts with outside wire intact");
        var bore=new WaterTerrainBore(Vec3.atCenterOf(pos),new Vec3(0,0,1),.4,1);bore.tick(level,p);
        h.assertTrue(level.getBlockState(pos).isAir()&&level.getBlockState(pos.east()).is(Blocks.REDSTONE_WIRE),"Callback must not break wire outside authorized cylinder");
        // The isolation must not disable ordinary neighbor updates after the bore returns.
        level.neighborChanged(pos.east(),Blocks.STONE,pos.east().below());
        h.assertTrue(level.getBlockState(pos.east()).isAir(),"Ordinary updates resume after scoped bore edit");h.succeed();
    }
    @GameTest(template="water_test_large_empty",timeoutTicks=30)
    public static void grassInsideInBoundsBoreDoesNotStopDragon(GameTestHelper h) {
        var level=h.getLevel();var p=caster(h);BlockPos pos=h.absolutePos(new BlockPos(24,24,24));
        level.setBlockAndUpdate(pos.below(),Blocks.GRASS_BLOCK.defaultBlockState());
        level.setBlockAndUpdate(pos,Blocks.GRASS.defaultBlockState());
        var bore=new WaterTerrainBore(Vec3.atCenterOf(pos),new Vec3(0,0,1),.4,1);bore.tick(level,p);
        h.assertTrue(!bore.stopped()&&bore.length()==1&&level.getBlockState(pos).isAir(),"Grass is not bedrock and cannot stop an in-bounds bore: "+bore.stopReason());h.succeed();
    }
    @GameTest(template="water_test_large_empty",timeoutTicks=30)
    public static void boreInsideNeighborChainCannotQueueOutsideComparatorDestruction(GameTestHelper h) {
        var level=h.getLevel();var p=caster(h);BlockPos pos=h.absolutePos(new BlockPos(24,24,24));
        BlockPos outside=pos.east(2);
        level.setBlock(pos,Blocks.CHEST.defaultBlockState(),2|16);
        // A conductor forces the state-bearing ServerLevel enqueue route, not Forge's direct adjacent notification.
        level.setBlock(pos.east(),Blocks.STONE.defaultBlockState(),2|16);
        level.setBlock(outside.below(),Blocks.STONE.defaultBlockState(),2|16);
        level.setBlock(outside,Blocks.COMPARATOR.defaultBlockState(),2|16);
        level.setBlock(outside.below(),Blocks.AIR.defaultBlockState(),2|16);
        insideNeighborRemoval(h,pos.offset(0,3,0),()->level.setBlock(pos,Blocks.AIR.defaultBlockState(),2|16));
        h.assertTrue(level.getBlockState(outside).isAir(),"Control removal must reproduce actual queued comparator destruction");
        level.setBlock(pos,Blocks.CHEST.defaultBlockState(),2|16);
        level.setBlock(outside.below(),Blocks.STONE.defaultBlockState(),2|16);
        level.setBlock(outside,Blocks.COMPARATOR.defaultBlockState(),2|16);
        level.setBlock(outside.below(),Blocks.AIR.defaultBlockState(),2|16);
        var bore=new WaterTerrainBore(Vec3.atCenterOf(pos),new Vec3(0,0,1),.4,1);
        insideNeighborRemoval(h,pos.offset(0,3,0),()->bore.tick(level,p));
        h.assertTrue(level.getBlockState(pos).isAir()&&level.getBlockState(outside).is(Blocks.COMPARATOR),"Comparator notification cannot escape the guard through an existing neighbor queue");
        level.neighborChanged(outside,Blocks.STONE,outside.below());
        h.assertTrue(level.getBlockState(outside).isAir(),"Queued update isolation clears after removal");
        level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,new net.minecraft.world.phys.AABB(pos).inflate(3)).forEach(Entity::discard);h.succeed();
    }
    private static void insideNeighborRemoval(GameTestHelper h,BlockPos trigger,Runnable operation) {
        var level=h.getLevel();
        level.setBlock(trigger.below(),Blocks.STONE.defaultBlockState(),2|16);
        level.setBlock(trigger,Blocks.REDSTONE_WIRE.defaultBlockState(),2|16);
        level.setBlock(trigger.below(),Blocks.AIR.defaultBlockState(),2|16);
        var fired=new java.util.concurrent.atomic.AtomicBoolean();
        Consumer<BlockEvent.NeighborNotifyEvent> listener=event->{
            if(event.getLevel()==level&&event.getPos().equals(trigger)&&fired.compareAndSet(false,true))operation.run();
        };
        MinecraftForge.EVENT_BUS.addListener(net.minecraftforge.eventbus.api.EventPriority.NORMAL,false,BlockEvent.NeighborNotifyEvent.class,listener);
        try { level.neighborChanged(trigger,Blocks.STONE,trigger.below()); }
        finally { MinecraftForge.EVENT_BUS.unregister(listener); }
        h.assertTrue(fired.get(),"Real wire removal must enter its Forge neighbor callback inside the collecting update chain");
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
    private static java.util.Map<UUID,ServerPlayer> testOwnerMap(GameTestHelper h) {
        // Register only a test UUID lookup, not a network client or a production permission bypass.
        // Verified PlayerList.playersByUUID SRG name; always remove this entry after the test.
        return net.minecraftforge.fml.util.ObfuscationReflectionHelper.getPrivateValue(
                net.minecraft.server.players.PlayerList.class,h.getLevel().getServer().getPlayerList(),"f_11197_");
    }
    @GameTest(template="water_test_large_empty",timeoutTicks=30)
    public static void oceanProductionTickCancelsMissingOrDeadOwnerBeforeWeather(GameTestHelper h) {
        var owners=testOwnerMap(h);var p=caster(h);long before=WaterWeather.get(h.getLevel()).save(new net.minecraft.nbt.CompoundTag()).getLong("until");
        try {
            for(boolean dead:new boolean[]{false,true}) {
                p.setHealth(20);if(dead)owners.put(p.getUUID(),p);else owners.remove(p.getUUID());
                TNWaterFieldEntity.cast(p,net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("tnc","world_ending_sea"));
                var field=h.getLevel().getEntitiesOfClass(TNWaterFieldEntity.class,p.getBoundingBox().inflate(4)).stream().filter(e->e.kind()==1&&e.tier()==5&&!e.isRemoved()).findFirst().orElseThrow();
                field.tickCount=600;if(dead)p.setHealth(0);field.tick();
                h.assertTrue(field.isRemoved(),"Production tick must cancel an invalid owner before completing sea");
                h.assertTrue(WaterWeather.get(h.getLevel()).save(new net.minecraft.nbt.CompoundTag()).getLong("until")==before,"Canceled owner cannot start trailing storm");
            }
        } finally {owners.remove(p.getUUID());p.setHealth(20);}
        h.succeed();
    }
    @GameTest(template="water_test_ocean_empty",timeoutTicks=1350)
    public static void oceanNaturallyCompletesThenWeatherLeaseExpiresAndRestores(GameTestHelper h) {
        var owners=testOwnerMap(h);var p=caster(h);p.setPos(Vec3.atCenterOf(h.absolutePos(new BlockPos(72,8,72))));owners.put(p.getUUID(),p);
        var chunk=new net.minecraft.world.level.ChunkPos(p.blockPosition());
        boolean wasForced=h.getLevel().getForcedChunks().contains(chunk.toLong());
        h.getLevel().setChunkForced(chunk.x,chunk.z,true);
        Runnable cleanup=()->{owners.remove(p.getUUID());if(!wasForced)h.getLevel().setChunkForced(chunk.x,chunk.z,false);};
        final TNWaterFieldEntity field;
        try {
            TNWaterFieldEntity.cast(p,net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("tnc","world_ending_sea"));
            field=h.getLevel().getEntitiesOfClass(TNWaterFieldEntity.class,p.getBoundingBox().inflate(4)).stream().filter(e->e.kind()==1&&e.tier()==5&&!e.isRemoved()).findFirst().orElseThrow();
        } catch(RuntimeException failure) {cleanup.run();throw failure;}
        // Clean up before the framework's hard timeout as well as on success or an assertion failure.
        h.runAfterDelay(1300,()->{cleanup.run();field.discard();h.fail("Natural ocean/weather lifecycle did not complete: age="+field.age()+" tick="+field.tickCount+" gameTime="+h.getLevel().getGameTime()+" lease="+WaterWeather.get(h.getLevel()).save(new net.minecraft.nbt.CompoundTag()));});
        var captured=new java.util.concurrent.atomic.AtomicReference<net.minecraft.nbt.CompoundTag>();
        h.onEachTick(()->{
            try {
                if(field.age()<600)h.assertTrue(!field.isRemoved(),"Valid test owner keeps production field alive until natural completion");
                var state=WaterWeather.get(h.getLevel()).save(new net.minecraft.nbt.CompoundTag());long now=h.getLevel().getGameTime();
                if(field.age()==598)h.assertTrue(state.getLong("until")==0,"No trailing storm before natural sea completion");
                if(field.age()>=600&&captured.get()==null) {
                    long until=state.getLong("until");
                    h.assertTrue(until>=now+598&&until<=now+600,"Natural tick path grants the full trailing thirty-second weather lease");
                    captured.set(state);
                }
                var snapshot=captured.get();
                if(snapshot!=null&&now>snapshot.getLong("until")) {
                    var data=h.getLevel().getServer().getWorldData().overworldData();
                    h.assertTrue(field.isRemoved()&&state.getLong("until")==0,"Field and weather lease naturally terminate");
                    h.assertTrue(data.isRaining()==snapshot.getBoolean("raining")&&data.isThundering()==snapshot.getBoolean("thundering"),"Weather handler restores original weather flags");
                    h.assertTrue(Math.abs(data.getRainTime()-snapshot.getInt("rain"))<=1&&Math.abs(data.getThunderTime()-snapshot.getInt("thunder"))<=1,"Weather handler restores original timers, allowing one vanilla tick");
                    cleanup.run();h.succeed();
                }
            } catch(RuntimeException failure) {cleanup.run();field.discard();throw failure;}
        });
    }
    @GameTest(template="water_test_ocean_empty",timeoutTicks=30)
    public static void oceanBreakersHitAllDirectionsAndStormOnlyAfterCompletion(GameTestHelper h) {
        var p=caster(h);p.setPos(Vec3.atCenterOf(h.absolutePos(new BlockPos(72,8,72))));
        var id=net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("tnc","world_ending_sea");
        long before=WaterWeather.get(h.getLevel()).save(new net.minecraft.nbt.CompoundTag()).getLong("until");
        TNWaterFieldEntity.cast(p,id);
        var old=h.getLevel().getEntitiesOfClass(TNWaterFieldEntity.class,p.getBoundingBox().inflate(4)).stream().filter(e->e.kind()==1&&e.tier()==5).findFirst().orElseThrow();
        TNWaterFieldEntity.cast(p,id);
        var field=h.getLevel().getEntitiesOfClass(TNWaterFieldEntity.class,p.getBoundingBox().inflate(4)).stream().filter(e->e.kind()==1&&e.tier()==5&&!e.isRemoved()).findFirst().orElseThrow();
        h.assertTrue(old.isRemoved(),"Recasting replaces the previous ocean instead of stacking thirty-second attacks");
        old.tickCount=600;old.finishSea(h.getLevel());
        h.assertTrue(WaterWeather.get(h.getLevel()).save(new net.minecraft.nbt.CompoundTag()).getLong("until")==before,"Canceled sea cannot schedule trailing weather");
        var enemies=new java.util.ArrayList<Zombie>();Vec3 center=field.position();
        for(int i=0;i<8;i++){double a=i*Math.PI/4;enemies.add(toughZombie(h,center.add(Math.cos(a)*56,0,Math.sin(a)*56)));}
        Zombie outside=toughZombie(h,center.add(65,0,0)),below=toughZombie(h,center.add(0,-4,56)),blocked=toughZombie(h,center.add(-12,0,34));
        BlockPos wall=BlockPos.containing(center.add(-6,0,17));h.getLevel().setBlockAndUpdate(wall,Blocks.STONE.defaultBlockState());h.getLevel().setBlockAndUpdate(wall.above(),Blocks.STONE.defaultBlockState());
        try {
            field.tickCount=20;field.affect(h.getLevel(),p);
            for(var z:enemies)h.assertTrue(z.getHealth()==200,"No invisible damage before a wave front reaches the target");
            field.tickCount=40;field.affect(h.getLevel(),p);
            for(var z:enemies)h.assertTrue(z.getHealth()<200,"Moving sea reaches 56 blocks in every direction");
            for(var z:new Zombie[]{outside,below,blocked})h.assertTrue(z.getHealth()==200,"Ocean radius, below-floor and LOS boundaries retained");
            h.assertTrue(h.getLevel().getBlockState(wall).is(Blocks.STONE),"Ocean is visual water, not destructive terrain edits");
            field.tickCount=599;field.finishSea(h.getLevel());
            h.assertTrue(WaterWeather.get(h.getLevel()).save(new net.minecraft.nbt.CompoundTag()).getLong("until")==before,"No weather lease before thirty-second ocean completes");
            field.tickCount=600;field.finishSea(h.getLevel());
            long until=WaterWeather.get(h.getLevel()).save(new net.minecraft.nbt.CompoundTag()).getLong("until");
            var data=h.getLevel().getServer().getWorldData().overworldData();
            h.assertTrue(until==h.getLevel().getGameTime()+600&&data.isRaining()&&data.isThundering(),"Completion starts another thirty-second thunderstorm");
            data.setRainTime(500);field.finishSea(h.getLevel());
            h.assertTrue(data.getRainTime()==500,"Same completion cannot reset weather after an external change");
        } finally {
            field.discard();for(var z:enemies)z.discard();outside.discard();below.discard();blocked.discard();
        }
        h.succeed();
    }
    @GameTest(template="water_test_crypt_empty",timeoutTicks=30)
    public static void seaGodSwordHitsOncePerStrikeThroughoutFieldLife(GameTestHelper h) {
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
        for(int strike=1;strike<SeaGodSwordRules.COUNT;strike++) {
            enemy.setHealth(full);enemy.invulnerableTime=0;
            field.tickCount=140+strike*60-1;field.seaGodSwordImpact(h.getLevel(),p);
            h.assertTrue(enemy.getHealth()==full,"No early repeated sword impact");
            field.tickCount++;field.seaGodSwordImpact(h.getLevel(),p);float after=enemy.getHealth();
            h.assertTrue(full-after>40,"Each scheduled sword causes a real large impact");
            enemy.invulnerableTime=0;field.seaGodSwordImpact(h.getLevel(),p);
            h.assertTrue(enemy.getHealth()==after,"Same scheduled sword cannot settle twice");
        }
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
        h.assertTrue(WaterSpellRules.fieldRadius(1,5)==64&&WaterSpellRules.fieldHeight(1,5)==40&&WaterSpellRules.fieldLife(1,5)==600,"Thirty-second giant ocean scale");
        var sea=WaterSpellRules.uprightArea(Vec3.ZERO,64,40);
        h.assertTrue(sea.minY==-.25&&sea.maxY==40,"Sea grows above its floor, not equally underground");h.succeed();
    }
    @GameTest(template="water_test_large_empty",timeoutTicks=30)
    public static void dragonLaunchClearsFlatGrassWithoutRemovingProtection(GameTestHelper h) {
        var p=caster(h);p.setPos(Vec3.atCenterOf(h.absolutePos(new BlockPos(24,2,24))).subtract(0,.5,0));p.setYRot(0);p.setXRot(0);
        for(int x=0;x<48;x++)for(int z=0;z<48;z++) {
            h.getLevel().setBlockAndUpdate(h.absolutePos(new BlockPos(x,1,z)),Blocks.GRASS_BLOCK.defaultBlockState());
            h.getLevel().setBlockAndUpdate(h.absolutePos(new BlockPos(x,2,z)),Blocks.GRASS.defaultBlockState());
        }
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
    public static void onlyBedrockStopsBore(GameTestHelper h) {
        var p=caster(h);Vec3 start=Vec3.atCenterOf(h.absolutePos(new BlockPos(2,5,1)));
        BlockPos obstacle=BlockPos.containing(start.add(0,0,1));
        for(var block:new net.minecraft.world.level.block.Block[]{Blocks.BEDROCK}) {
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
    public static void wetSolidsAndLeafNeighborsDoNotStopBore(GameTestHelper h) {
        var wet=Blocks.OAK_STAIRS.defaultBlockState().setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.WATERLOGGED,true);
        h.assertTrue(!WaterTerrainBore.fluidOnly(wet),"Waterlogged stairs must not count as water");
        var p=caster(h);Vec3 start=Vec3.atCenterOf(h.absolutePos(new BlockPos(2,5,1)));BlockPos pos=BlockPos.containing(start);
        h.getLevel().setBlockAndUpdate(pos,wet);h.getLevel().setBlockAndUpdate(pos.above(),Blocks.OAK_LEAVES.defaultBlockState());
        var bore=new WaterTerrainBore(start,new Vec3(0,0,1),.4,3);bore.tick(h.getLevel(),p);
        h.assertTrue(!bore.stopped()&&h.getLevel().getBlockState(pos).isAir(),"Leaf neighbors and wet solids must not block beam: "+bore.stopReason());h.succeed();
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
