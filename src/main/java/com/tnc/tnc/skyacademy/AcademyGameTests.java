package com.tnc.tnc.skyacademy;

import com.tnc.tnc.adventure.AdventureGameTests;
import com.tnc.tnc.life.routes.*;
import com.tnc.tnc.world.SkyIslandSavedData;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.gametest.*;

/** Exercises the same machines, ownership checks and dialogue actions used by players. */
@GameTestHolder("tncacademy") @PrefixGameTestTemplate(false)
public final class AcademyGameTests {
    @GameTest(template="building_test_empty",timeoutTicks=100)
    public static void freshIslandEnrollmentDoesNotDependOnWorldTickZero(GameTestHelper h){
        var s=h.getLevel().getServer();var state=SkyAcademyWorld.State.get(s.overworld());
        boolean enabled=state.enabled,done=state.done;int tile=state.tile,cursor=state.cursor;String error=state.error;
        try{
            state.enabled=false;state.done=false;
            h.assertTrue(s.overworld().getGameTime()>0,"fixture starts after world warm-up");
            SkyAcademyWorld.beginFreshIsland(s);
            h.assertTrue(state.enabled&&!state.done&&!SkyAcademyWorld.ready(s),"new island waits for the authored scenery even after warm-up");
            h.succeed();
        }finally{state.enabled=enabled;state.done=done;state.tile=tile;state.cursor=cursor;state.error=error;state.setDirty();}
    }
    private interface Check { void run(ServerPlayer p,BlockPos base)throws Exception; }
    private record Fixture(ServerPlayer player, BlockPos base, Runnable restore) {}
    private static Fixture beginFixture(GameTestHelper h,String site)throws Exception{
        var l=h.getLevel();var s=l.getServer();var store=l.getDataStorage();
        var previous=SkyIslandSavedData.get(l);var academy=SkyAcademyWorld.State.get(l);
        boolean enabled=academy.enabled,done=academy.done;
        var load=SkyIslandSavedData.class.getDeclaredMethod("load",CompoundTag.class);load.setAccessible(true);
        var base=h.absolutePos(new BlockPos(3,3,3));
        var local=SkyAcademyWorld.SITES.get(site).offset(site.equals("farm")?-3:-5,0,site.equals("farm")?-10:-4);
        var origin=base.subtract(local);var tag=previous.save(new CompoundTag());
        tag.putString("Phase","COMPLETE");tag.putBoolean("LayoutReady",true);
        tag.putInt("Version",5);tag.putInt("PortalRevision",4);
        tag.putInt("OriginX",origin.getX());tag.putInt("OriginY",origin.getY());tag.putInt("OriginZ",origin.getZ());
        var island=(SkyIslandSavedData)load.invoke(null,tag);
        store.set("tnc_sky_island_v5",island);academy.enabled=academy.done=true;
        var p=AdventureGameTests.player(h);p.setPos(base.getX()+.5,base.getY(),base.getZ()+.5);
        return new Fixture(p,base,()->{
            store.set("tnc_sky_island_v5",previous);academy.enabled=enabled;academy.done=done;
            AcademyLessons.stop(new net.minecraftforge.event.server.ServerStoppedEvent(s));
        });
    }

    private static void fixture(GameTestHelper h,String site,Check check)throws Exception{
        var fixture=beginFixture(h,site);
        try {check.run(fixture.player,fixture.base);} finally {fixture.restore.run();}
        h.succeed();
    }

    @GameTest(template="building_test_empty",batch="academy_workshop_real_ticks",timeoutTicks=180)
    public static void protectedWorkshopAcceptsSurvivalAndAdventureWire(GameTestHelper h)throws Exception{
        // Forge also discovers annotated methods during unrelated projects' bare-mod suites.
        // This test uses the shipped island's full lifecycle and needs its KubeJS resource.
        if (h.getLevel().getServer().getResourceManager().getResource(com.tnc.tnc.world.SkyIslandManifest.LOCATION).isEmpty()
                && !java.util.List.of(System.getProperty("forge.enabledGameTestNamespaces","").split(",")).contains("tncacademy")) {
            h.succeed();
            return;
        }
        var fixture=beginFixture(h,"workshop");
        try {
            placePracticeWire(h,fixture,net.minecraft.world.level.GameType.SURVIVAL);
        } catch(RuntimeException failure){fixture.restore.run();throw failure;}
        awaitPowered(h,fixture,"survival",0,()->{
            placePracticeWire(h,fixture,net.minecraft.world.level.GameType.ADVENTURE);
            awaitPowered(h,fixture,"adventure",0,()->{fixture.restore.run();h.succeed();});
        });
    }

    private static void awaitPowered(GameTestHelper h,Fixture fixture,String mode,int elapsed,Runnable next){
        // Network transfer runs at END; the lamp consumes the newly arrived mana on
        // a subsequent block-entity tick. Observe both real machines until it lights.
        h.runAfterDelay(5,()->{
            try {
                if (AcademyStations.complete(fixture.player,"workshop")) {
                    assertPowered(h,fixture,mode);
                    next.run();
                } else if (elapsed+5>=75) {
                    assertPowered(h,fixture,mode);
                } else {
                    awaitPowered(h,fixture,mode,elapsed+5,next);
                }
            } catch(RuntimeException failure){fixture.restore.run();throw failure;}
        });
    }

    private static void placePracticeWire(GameTestHelper h,Fixture fixture,net.minecraft.world.level.GameType mode){
        var player=fixture.player;
        var level=player.serverLevel();
        var base=fixture.base;
        var slot=base.east(2);
        var hit=new net.minecraft.world.phys.BlockHitResult(
                net.minecraft.world.phys.Vec3.atBottomCenterOf(slot),net.minecraft.core.Direction.UP,slot.below(),false);
        var hand=net.minecraft.world.InteractionHand.MAIN_HAND;
        var stranger=AdventureGameTests.player(h);
        stranger.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
        var data=AcademyLessons.data(player);
        player.setGameMode(mode);
        player.setPos(slot.getX()+.5,slot.getY(),slot.getZ()+2.5);
        data.putString("Active","workshop");data.putBoolean("Practice",true);
        h.assertTrue(AcademyLessons.claim(player,"workshop"),mode+": owns lesson");
        h.assertTrue(com.tnc.tnc.home.TownProtection.town(level,slot),mode+": town protection applies");
        AcademyStations.setup(player,"workshop",false);
        h.assertTrue(!RouteAccess.allowed(level,slot,stranger.getUUID()),mode+": bystander cannot operate circuit");
        var wires=new ItemStack(RouteContent.item("mana_thread"),2);
        player.setItemInHand(hand,wires);
        h.assertTrue(!AcademyPracticeAccess.placement(player,slot.east(),wires),mode+": permission confined to the gap");
        var result=player.gameMode.useItemOn(player,level,wires,hand,hit);
        h.assertTrue(result.consumesAction() && wires.getCount()==1 && !wires.hasTag(),mode+": one wire spent with no lasting CanPlaceOn");
        h.assertTrue(data.getBoolean("WirePlaced") && node(h,slot).getPersistentData().getUUID("AcademyPlacedBy").equals(player.getUUID()),
                mode+": real Forge placement recorded");
        for(int i=0;i<5;i++)h.assertTrue(RouteAccess.allowed(level,base.east(i),player.getUUID()),mode+": five cells accessible");
    }

    private static void assertPowered(GameTestHelper h,Fixture fixture,String mode){
        var lamp=node(h,fixture.base.east(4));
        var source=node(h,fixture.base);
        boolean connected=true;
        for(int i=0;i<4;i++)connected &= RouteConnections.connects(h.getLevel(),fixture.base.east(i),fixture.base.east(i+1));
        var level=fixture.player.serverLevel();
        String actual="mode="+mode+", station="+AcademyStations.base(fixture.player.server,"workshop")+", fixture="+fixture.base
                +", placed="+AcademyLessons.data(fixture.player).getBoolean("WirePlaced")+", lampOwner="+lamp.owner+", player="+fixture.player.getUUID()
                +", world="+level.dimension().location()+", lit="+level.getBlockState(lamp.getBlockPos()).getValue(RouteNodeBlock.LIT)
                +", status="+lamp.status+", pool="+source.mana+", lamp="+lamp.mana+", input="+lamp.inRate+", connected="+connected
                +", loaded="+RouteNetwork.positions(level).contains(lamp.getBlockPos())
                +", jammed="+com.tnc.tnc.arcana.ArcanaDevices.jammed(level,lamp.owner,lamp.getBlockPos());
        com.mojang.logging.LogUtils.getLogger().info("[Academy workshop] {}",actual);
        h.assertTrue(AcademyStations.complete(fixture.player,"workshop"),actual);
    }
    private static void operate(RouteNodeEntity node)throws Exception{
        var method=RouteNodeEntity.class.getDeclaredMethod("operate",net.minecraft.server.level.ServerLevel.class,RouteNodeEntity.class);
        method.setAccessible(true);method.invoke(null,node.getLevel(),node);
    }
    private static RouteNodeEntity node(GameTestHelper h,BlockPos pos){return (RouteNodeEntity)h.getLevel().getBlockEntity(pos);}

    @GameTest(template="building_test_empty",timeoutTicks=100)
    public static void workshopNeedsRealConnectedCircuitAndReplayPreservesKit(GameTestHelper h)throws Exception{
        fixture(h,"workshop",(p,b)->{
            h.assertTrue(AcademyLessons.claim(p,"workshop"),"learner receives practice lease");
            AcademyStations.setup(p,"workshop",true);var lamp=node(h,b.east(4));
            RouteNetwork.tickNetwork(h.getLevel());operate(lamp);
            h.assertTrue(lamp.mana>0&&lamp.inRate>0&&lamp.getBlockState().getValue(RouteNodeBlock.LIT),"demonstration uses actual mana transfer and lamp upkeep");
            AcademyStations.setup(p,"workshop",false);lamp=node(h,b.east(4));
            h.assertTrue(lamp.mana==0&&lamp.inRate==0&&!AcademyStations.complete(p,"workshop"),"demo cannot complete missing-wire practice");
            int kit=p.getInventory().countItem(RouteContent.item("mana_thread"));
            AcademyStations.setup(p,"workshop",false);
            h.assertTrue(p.getInventory().countItem(RouteContent.item("mana_thread"))==kit&&kit==1,"replay does not duplicate starter wire");
            h.getLevel().setBlock(b.east(2),RouteContent.NODES.get(RouteKind.WIRE.id).get().defaultBlockState(),3);
            var wire=node(h,b.east(2));wire.owner=p.getUUID();
            for(int x=1;x<4;x++)((RouteWireBlock)h.getLevel().getBlockState(b.east(x)).getBlock()).refresh(h.getLevel(),b.east(x));
            AcademyLessons.data(p).putBoolean("WirePlaced",true);
            // A new real network second gives each wire its next throughput budget.
            long time=h.getLevel().getGameTime();h.getLevel().getServer().getWorldData().overworldData().setGameTime(time+20);
            try {RouteNetwork.tickNetwork(h.getLevel());operate(lamp);}finally{h.getLevel().getServer().getWorldData().overworldData().setGameTime(time);}
            h.assertTrue(AcademyStations.complete(p,"workshop"),"actual wired lamp completes the lesson");
        });
    }

    @GameTest(template="building_test_empty",timeoutTicks=100)
    public static void practiceReturnsPlayerPlacedWireAndContentsExactlyOnce(GameTestHelper h)throws Exception{
        fixture(h,"workshop",(p,b)->{
            AcademyLessons.claim(p,"workshop");AcademyStations.setup(p,"workshop",true);
            var wire=node(h,b.east(2));wire.getPersistentData().putUUID("AcademyPlacedBy",p.getUUID());
            wire.inventory.setItem(0,new ItemStack(net.minecraft.world.item.Items.DIAMOND,3));
            AcademyStations.setup(p,"workshop",true);AcademyStations.setup(p,"workshop",true);
            var refunds=com.tnc.tnc.journey.JourneyData.get(p.server).player(p.getUUID()).getList("AcademyRefunds",10);
            h.assertTrue(refunds.size()==2,"offline refund contains one placed block plus one inventory stack, once");
            h.assertTrue(ItemStack.of(refunds.getCompound(0)).is(RouteContent.item("mana_thread"))&&ItemStack.of(refunds.getCompound(1)).getCount()==3,"exact input items are preserved");
            var other=AdventureGameTests.player(h);
            h.assertTrue(AcademyLessons.trainingProtected(other,b)&&!AcademyLessons.trainingProtected(p,b),"only lease holder can edit teaching area");
            h.assertTrue(AcademyLessons.trainingEditDenied(p,b.east(2)),"demonstration wire is not a repeatable free item");
            AcademyLessons.data(p).putString("Active","workshop");AcademyLessons.data(p).putBoolean("Practice",true);
            h.assertTrue(!AcademyLessons.trainingEditDenied(p,b.east(2))&&AcademyLessons.trainingEditDenied(p,b),"learner may edit the gap but cannot harvest the public mana pool");
            h.assertTrue(RouteAccess.allowed(h.getLevel(),b,p.getUUID()),"fixed public devices still conduct mana for their current learner");
        });
    }

    @GameTest(template="building_test_empty",timeoutTicks=100)
    public static void farmBindsAdultWithoutFoodAndDoesNotCountDemoAsPractice(GameTestHelper h)throws Exception{
        fixture(h,"farm",(p,b)->{
            AcademyLessons.claim(p,"farm");AcademyStations.setup(p,"farm",false);
            var animals=h.getLevel().getEntitiesOfClass(com.tnc.tnc.life.pasture.PastureAnimal.class,new net.minecraft.world.phys.AABB(b).inflate(12),a->a.getPersistentData().getBoolean("AcademyAnimal"));
            h.assertTrue(animals.size()==1,"one adult teaching animal");var animal=animals.get(0);
            h.assertTrue(!animal.isBaby()&&animal.bindManaWork(p,b.east(4)),"no taming or feeding is required to bind");
            h.assertTrue(node(h,b.east(4)).mana==0&&!AcademyStations.complete(p,"farm"),"binding alone cannot fake real collection");
            h.assertTrue(h.getLevel().getBlockEntity(b) instanceof com.tnc.tnc.life.botanical.BotanicalPlantEntity,"real mature botanical plant, not a display prop");
            for(var a:animals)a.discard();
        });
    }
}
