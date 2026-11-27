package com.tnc.tnc.sanctum;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("tncsanctum")
@PrefixGameTestTemplate(false)
public final class SanctumAdminGameTests {
    private static int command(ServerPlayer p,int permission,String tail){
        return p.server.getCommands().performPrefixedCommand(p.createCommandSourceStack().withPermission(permission),"sanctum "+tail);
    }
    private static SanctumLedger.Expedition expedition(ServerPlayer p){
        return SanctumLedger.get(p.server).expeditions.values().stream().filter(r->r.members.containsKey(p.getUUID())).findFirst().orElseThrow();
    }

    @GameTest(template="empty",timeoutTicks=60)
    public static void adminPracticeIsScopedAndSurvivesReload(GameTestHelper h){
        var p=SanctumGameTests.participant(h);
        var other=SanctumGameTests.participant(h);
        h.assertTrue(command(p,0,"boss")==0 && command(p,0,"phase 3")==0 && command(p,0,"phase frenzy")==0,
                "ordinary players cannot dispatch administrative commands");
        h.assertTrue(command(p,2,"phase next")==0 && command(p,2,"phase 4")==0,"outside or invalid phase is refused");
        h.assertTrue(command(p,2,"boss")==1 && UnlitSanctum.enter(other),"directly create practice and a separate story party");
        var run=expedition(p);var independent=expedition(other);
        h.assertTrue(command(p,2,"phase 3")==1 && command(p,2,"phase 3")==1 && command(p,2,"boss")==1,"queue explicit phase while building");
        var saved=SanctumLedger.load(SanctumLedger.get(p.server).save(new CompoundTag()));
        h.assertTrue(saved.expeditions.get(run.id).rehearsal && saved.expeditions.get(run.id).requestedPhase==3
                && !saved.expeditions.get(independent.id).rehearsal && independent.requestedPhase==0,"scope and rehearsal classification survive save/load");
        h.assertTrue(command(p,2,"phase next")==0 && command(p,2,"phase 2")==0 && command(p,2,"phase frenzy")==0,
                "a conflicting phase cannot replace the pending choice");
        h.assertTrue(UnlitSanctum.retry(p) && run.rehearsal && run.requestedPhase==0,
                "a wipe cancels deferred commands while preserving rehearsal status");
        UnlitSanctum.leave(other);UnlitSanctum.leave(p);h.succeed();
    }

    @GameTest(template="empty",timeoutTicks=6500)
    public static void fullPartyBossPracticeReplaysBothTransitionsAndFrenzies(GameTestHelper h){
        var p=SanctumGameTests.participant(h);var mate=SanctumGameTests.participant(h);
        h.assertTrue(UnlitSanctum.enter(List.of(p,mate)) && command(p,2,"boss")==1,"convert this route to a team rehearsal");
        var run=expedition(p);var level=p.serverLevel();var center=run.origin().offset(0,0,SanctumArchitecture.FINAL_Z);
        AtomicInteger wins=new AtomicInteger();
        Consumer<FalseGodDefeatedEvent> witness=e->{if(e.expedition.equals(run.id))wins.incrementAndGet();};
        MinecraftForge.EVENT_BUS.addListener(EventPriority.NORMAL,false,FalseGodDefeatedEvent.class,witness);
        p.setGameMode(GameType.CREATIVE);mate.setGameMode(GameType.CREATIVE);
        class Trial implements Runnable {
            int step,age;long eyes,shell;UUID first,restarted;
            void ask(String command){h.assertTrue(SanctumAdminGameTests.command(p,2,command)==1,"dispatch "+command+" at checkpoint "+step);}
            void reject(String command){h.assertTrue(SanctumAdminGameTests.command(p,2,command)==0,"reject "+command+" at checkpoint "+step);}
            void strike(IronGolem body){body.invulnerableTime=0;body.hurt(level.damageSources().playerAttack(p),9999);}
            @Override public void run(){
                age++;
                if(age%60==1)SanctumGameTests.trackView(level,p.blockPosition());
                if(run.won){
                    h.assertTrue(step==11 && wins.get()==0 && !UnlitSanctum.hasDefeated(p.server,p.getUUID())
                            && !UnlitSanctum.hasDefeated(p.server,mate.getUUID()),"practice victory never awards the chapter or posts its event");
                    MinecraftForge.EVENT_BUS.unregister(witness);
                    UnlitSanctum.leave(p);UnlitSanctum.leave(mate);h.succeed();return;
                }
                var actors=level.getEntitiesOfClass(IronGolem.class,new AABB(center).inflate(85),b->b.isAlive()&&b.getTags().contains("tnc_false_god"));
                h.assertTrue(actors.size()<=1,"one real carrier per encounter, including resets");
                if(actors.isEmpty())return;
                var boss=actors.get(0);var data=boss.getPersistentData().getCompound("TncSanctumAppearance");
                String model=data.getString("Model"),action=data.getString("Action");
                long start=data.getLong("Start"),now=level.getGameTime();
                switch(step){
                    case 0 -> {
                        if(run.requestedPhase!=0)return;
                        h.assertTrue(FalseGodFight.inHall(p.position(),center)&&FalseGodFight.inHall(mate.position(),center),"both creative party players arrived at the actual hall");
                        first=boss.getUUID();reject("phase frenzy");ask("phase next");reject("phase next");step++;
                    }
                    case 1 -> {if(action.equals("awaken")){eyes=start;reject("phase 1");step++;}}
                    case 2 -> {
                        if(run.requestedPhase!=0)return;
                        h.assertTrue(model.equals("bleeding") && now-eyes>=SanctumTimings.AWAKEN,"unabridged eyes, grin and tears");
                        ask("phase frenzy");reject("phase frenzy");
                        h.assertTrue(Math.abs(boss.getHealth()/boss.getMaxHealth()-.3F)<.001,"normal thirty percent boundary");step++;
                    }
                    case 3 -> {if(action.equals("combo")){ask("phase 2");step++;}}
                    case 4 -> {
                        if(!action.equals("awaken"))return;
                        restarted=boss.getUUID();
                        h.assertTrue(!restarted.equals(first),"selecting the same phase creates a clean fight");step++;
                    }
                    case 5 -> {if(run.requestedPhase==0){ask("phase next");step++;}}
                    case 6 -> {
                        if(!action.equals("rupture"))return;
                        shell=start;
                        if(run.requestedPhase==0){h.assertTrue(now-shell>=SanctumTimings.RUPTURE,"complete breakout");
                            reject("phase next");ask("phase frenzy");reject("phase frenzy");step++;}
                    }
                    case 7 -> {if(action.equals("dance")){ask("phase 1");step++;}}
                    case 8 -> {if(model.equals("angel")&&run.requestedPhase==0){
                        h.assertTrue(!boss.getUUID().equals(restarted),"third form can restart as a fresh statue");
                        ask("phase 3");ask("phase 3");step++;}}
                    case 9 -> {if(action.equals("awaken")){eyes=start;step++;}}
                    case 10 -> {if(action.equals("rupture")){
                        h.assertTrue(start-eyes>=SanctumTimings.AWAKEN,"a direct choice of phase three plays both transformations in order");
                        shell=start;step++;}}
                    case 11 -> {if(run.requestedPhase==0 && now-shell>=SanctumTimings.RUPTURE && age%6==0)strike(boss);}
                    default -> throw new IllegalStateException();
                }
            }
        }
        h.onEachTick(new Trial());
    }
}
