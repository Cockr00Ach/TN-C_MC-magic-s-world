package com.tnc.tnc.life.skywild;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.MoverType;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("tncroute") @PrefixGameTestTemplate(false)
public final class SkyWildGameTests {
    @GameTest(template="empty",batch="skywild_ecology",timeoutTicks=40)
    public static void flocksPersistAndFlyUnderOwnPower(GameTestHelper h) {
        var level=h.getLevel();int rank=0;
        for (var kind : SkySpecies.values()) {
            var anchor=h.absolutePos(new BlockPos(4,3,4));
            int roof=SkyHabitats.roof(level,anchor.getX(),anchor.getZ());
            int y=kind.altitude(roof,.5,level.getMaxBuildHeight());
            h.assertTrue(y>=roof+kind.low && y<=roof+kind.high,"Relative flight band: "+kind.id);
            h.assertTrue(kind.altitude(level.getMaxBuildHeight()-3,0,level.getMaxBuildHeight())==Integer.MIN_VALUE,"World ceiling");
            var bird=SkyWildRegistry.TYPES.get(kind).create(level);
            h.assertTrue(bird!=null && bird.isNoGravity(),"Airborne on creation");
            bird.joinFlock(97,rank,anchor.atY(roof),.8);
            var tag=new CompoundTag();bird.addAdditionalSaveData(tag);
            var copy=SkyWildRegistry.TYPES.get(kind).create(level);copy.readAdditionalSaveData(tag);
            h.assertTrue(copy.flockKey()==97 && copy.flockSlot()==rank && copy.isNoGravity(),"Flock save/load");
            copy.moveTo(anchor.getX()+.5,y,anchor.getZ()+.5,0,0);
            var start=copy.position();
            for(int i=0;i<24;i++) {
                copy.tickCount=i+1;
                copy.customServerAiStep();
                copy.move(MoverType.SELF,copy.getDeltaMovement());
            }
            h.assertTrue(copy.position().subtract(start).horizontalDistance()>1 && copy.getY()>roof+1,
                    "Controlled forward flight at altitude: "+kind.id);
            rank++;
        }
        h.assertTrue(SkySpecies.EAGLE.maxGroup==1 && SkySpecies.STARLING.minGroup>=5,"Flock and solitary ranks");
        h.assertTrue(SkySpecies.BAT.active(16000) && !SkySpecies.BAT.active(3000),"Dusk habitat window");
        h.assertTrue(new SkyHabitats.Habitat(false,true,false,false).supports(SkySpecies.SWALLOW),"Shore for swallows");
        h.assertTrue(!new SkyHabitats.Habitat(false,true,false,false).supports(SkySpecies.EAGLE),"Eagles need highland");
        h.succeed();
    }
    @GameTest(template="empty",batch="skywild_ecology",timeoutTicks=40)
    public static void starlingGroupAdmissionUsesLoadedAirAndBudget(GameTestHelper h) {
        var level=h.getLevel();var anchor=h.absolutePos(new BlockPos(1,3,1));
        var kind=SkySpecies.STARLING;int roof=SkyHabitats.roof(level,anchor.getX(),anchor.getZ());
        anchor=anchor.atY(roof);
        h.assertTrue(SkyHabitats.place(level,kind,anchor,kind.minGroup-1)==0,"Never admit half a murmuration to fill spare capacity");
        int actual=SkyHabitats.place(level,kind,anchor,kind.minGroup);
        h.assertTrue(actual==kind.minGroup,"Flock size admitted together");
        var air=level.getEntitiesOfClass(SkyBird.class,new net.minecraft.world.phys.AABB(anchor).inflate(22,32,22),
                b->b.species==kind);
        var leader=air.stream().filter(b->b.flockSlot()==0).findFirst().orElseThrow();
        var mine=air.stream().filter(b->b.flockKey()==leader.flockKey()).toList();
        h.assertTrue(mine.size()==actual && mine.stream().map(SkyBird::flockSlot).distinct().count()==actual,
                "One leader and distinct lateral slots");
        for(var bird:mine) {
            double height=bird.getY()-SkyHabitats.roof(level,bird.getBlockX(),bird.getBlockZ());
            h.assertTrue(height>=kind.low && height<kind.high+1 && level.canSeeSky(bird.blockPosition()),
                    "Each slot enters clear air over its own column");
            bird.discard();
        }
        h.succeed();
    }
    private SkyWildGameTests() {}
}

