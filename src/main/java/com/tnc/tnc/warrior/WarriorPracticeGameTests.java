package com.tnc.tnc.warrior;

import com.tnc.tnc.adventure.AdventureGameTests;
import io.netty.buffer.Unpooled;
import java.util.UUID;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.GameType;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.ShieldBlockEvent;
import net.minecraftforge.gametest.*;

@GameTestHolder("tncwarrior") @PrefixGameTestTemplate(false)
public final class WarriorPracticeGameTests {
    @GameTest(template="building_test_empty",timeoutTicks=40)
    public static void fractionalGrowthAndPracticeStateSurviveRealTransitions(GameTestHelper h) {
        var id=UUID.randomUUID();var d=new TemperingData();d.setTalent(id,1);
        h.assertTrue(d.award(id,3)==1,"First low-talent reward produces one point and a half carry");
        d=TemperingData.load(d.save(new CompoundTag()));
        h.assertTrue(d.award(id,3)==2&&d.points(id)==3,"Half carry survives save and completes next reward");
        d.set(id,199);h.assertTrue(d.award(id,Integer.MAX_VALUE)==1&&d.points(id)==200,"Oversized rewards stop exactly at personal cap");
        d.setTalent(id,0);h.assertTrue(d.award(id,120)==0&&d.points(id)==0,"No aptitude still means no progression");
        var p=AdventureGameTests.player(h);var data=TemperingData.get(p.server);data.setTalent(p.getUUID(),2);
        h.assertTrue(TemperingTraining.start(p),"Practice starts");
        var enemy=EntityType.ZOMBIE.create(h.getLevel());
        MinecraftForge.EVENT_BUS.post(new ShieldBlockEvent(p,h.getLevel().damageSources().mobAttack(enemy),4));
        h.assertTrue(TemperingTraining.blockCount(p)==1,"First block recorded");
        TemperingTraining.start(p);h.assertTrue(TemperingTraining.blockCount(p)==1,"Repeated start retains current progress");
        data.spendWindCooldown(p.getUUID(),p.server.overworld().getGameTime());
        var snapshot=TemperingEvents.snapshot(p,true);var buf=new FriendlyByteBuf(Unpooled.buffer());
        try {
            snapshot.encode(buf);var copy=TemperingPacket.decode(buf);
            h.assertTrue(snapshot.equals(copy)&&copy.training()&&copy.blocks()==1&&copy.windTicks()==160&&copy.open(),"Panel packet faithfully carries actual practice and cooldown state");
        }finally{buf.release();}
        p.setGameMode(GameType.CREATIVE);
        MinecraftForge.EVENT_BUS.post(new TickEvent.PlayerTickEvent(TickEvent.Phase.END,p));
        h.assertTrue(!TemperingTraining.active(p),"Losing eligibility cancels practice immediately");
        h.succeed();
    }
}
