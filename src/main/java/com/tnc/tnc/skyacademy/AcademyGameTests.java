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
    private static void fixture(GameTestHelper h,String site,Check check)throws Exception{
        var l=h.getLevel();var s=l.getServer();var store=l.getDataStorage();
        var previous=SkyIslandSavedData.get(l);var academy=SkyAcademyWorld.State.get(l);
        boolean enabled=academy.enabled,done=academy.done;
        var load=SkyIslandSavedData.class.getDeclaredMethod("load",CompoundTag.class);load.setAccessible(true);
        var base=h.absolutePos(new BlockPos(3,3,3));
        var local=SkyAcademyWorld.SITES.get(site).offset(site.equals("farm")?-3:-5,0,site.equals("farm")?-10:-4);
        var origin=base.subtract(local);var tag=previous.save(new CompoundTag());
        tag.putString("Phase","COMPLETE");tag.putBoolean("LayoutReady",true);
        tag.putInt("OriginX",origin.getX());tag.putInt("OriginY",origin.getY());tag.putInt("OriginZ",origin.getZ());
        store.set("tnc_sky_island_v5",(SkyIslandSavedData)load.invoke(null,tag));academy.enabled=academy.done=true;
        var p=AdventureGameTests.player(h);p.setPos(base.getX()+.5,base.getY(),base.getZ()+.5);
        try {check.run(p,base);} finally {
            store.set("tnc_sky_island_v5",previous);academy.enabled=enabled;academy.done=done;
            AcademyLessons.stop(new net.minecraftforge.event.server.ServerStoppedEvent(s));
        }
        h.succeed();
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
