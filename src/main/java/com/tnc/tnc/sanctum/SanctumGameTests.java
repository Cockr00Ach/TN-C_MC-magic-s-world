package com.tnc.tnc.sanctum;

import com.tnc.tnc.adventure.AdventureGameTests;
import com.tnc.tnc.combat.DownedCombat;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.HashSet;
import java.util.Set;

@GameTestHolder("tncsanctum")
@PrefixGameTestTemplate(false)
public final class SanctumGameTests {
    private static final net.minecraft.server.level.TicketType<net.minecraft.world.level.ChunkPos> VIEW =
            net.minecraft.server.level.TicketType.create("tnc_sanctum_test_view",
                    java.util.Comparator.comparingLong(net.minecraft.world.level.ChunkPos::toLong), 120);

    // A packet-sink player never acknowledges the client view distance. Keep the nearby entity
    // sections ticking as a connected client's view normally would, including the opposite wrist.
    static void trackView(ServerLevel level,BlockPos feet) {
        var chunk=new net.minecraft.world.level.ChunkPos(feet);
        for(int x=chunk.x-2;x<=chunk.x+2;x++) for(int z=chunk.z-2;z<=chunk.z+2;z++) {
            var at=new net.minecraft.world.level.ChunkPos(x,z);
            level.getChunkSource().addRegionTicket(VIEW,at,2,at);
        }
    }
    static ServerPlayer participant(GameTestHelper h) {
        var p = AdventureGameTests.player(h);
        var channel = new io.netty.channel.embedded.EmbeddedChannel();
        var connection = new net.minecraft.network.Connection(net.minecraft.network.protocol.PacketFlow.SERVERBOUND) {
            @Override public io.netty.channel.Channel channel() { return channel; }
        };
        p.connection = new net.minecraft.server.network.ServerGamePacketListenerImpl(p.server, connection, p) {
            @Override public void send(net.minecraft.network.protocol.Packet<?> packet) {
                if(packet instanceof net.minecraft.network.protocol.game.ClientboundSystemChatPacket chat && !chat.overlay())
                    com.mojang.logging.LogUtils.getLogger().info("Sanctum participant: {}",chat.content().getString());
            }
            @Override public void send(net.minecraft.network.protocol.Packet<?> packet, net.minecraft.network.PacketSendListener listener) {}
        };
        return p;
    }
    @GameTest(template="empty",timeoutTicks=60)
    public static void independentPartyAndReturnAddressesSurviveReload(GameTestHelper h) {
        var a=participant(h);
        var b=participant(h);
        a.setPos(14.125,80,33.75);
        b.setPos(-20.5,77,-70.25);
        h.assertTrue(UnlitSanctum.enter(java.util.List.of(a,b)),"party enters through story API");
        String group=UnlitSanctum.rescueGroup(a);
        h.assertTrue(group!=null && group.equals(UnlitSanctum.rescueGroup(b)) && !UnlitSanctum.solo(a),"shared rescue group");
        h.assertTrue(!UnlitSanctum.enter(a),"no nested expeditions");
        var data=SanctumLedger.get(a.server);
        var restored=SanctumLedger.load(data.save(new CompoundTag()));
        var entry=restored.expeditions.values().stream().filter(r->r.members.containsKey(a.getUUID())).findFirst().orElseThrow();
        h.assertTrue(entry.members.size()==2 && entry.members.get(b.getUUID()).z()==-70.25,"personal exit survives save");
        UnlitSanctum.leave(a);
        UnlitSanctum.leave(b);
        h.assertTrue(a.level()==h.getLevel() && a.getX()==14.125 && b.getZ()==-70.25,"exact return to both origins");
        h.succeed();
    }

    @GameTest(template="empty",timeoutTicks=9000)
    public static void lonePlayerWalksEveryLockAndDefeatsThreeForms(GameTestHelper h) {
        var p=participant(h);
        p.setPos(31.25,76,11.25);
        h.assertTrue(UnlitSanctum.enter(p),"enter solo");
        var run=SanctumLedger.get(p.server).expeditions.values().stream().filter(r->r.members.containsKey(p.getUUID())).findFirst().orElseThrow();
        var level=p.serverLevel();
        var o=run.origin();
        class Walk implements Runnable {
            String last="";
            int age, ticks, attempts, wristHits, spaceChecks;
            final Set<String> models=new HashSet<>();
            final Set<String> actions=new HashSet<>();
            final Set<String> raging=new HashSet<>();
            void at(int x,int z){
                var to=o.offset(x,0,z);
                if(!p.blockPosition().equals(to)) {
                    trackView(level,to);
                    p.teleportTo(to.getX()+.5,to.getY(),to.getZ()+.5);
                }
            }
            boolean press(int x,int z){at(x+1,z);return UnlitSanctum.interact(p,o.offset(x,1,z));}
            void strike(Mob m){m.invulnerableTime=0;m.hurt(level.damageSources().playerAttack(p),2000);}
            public void run(){
                if(++ticks>8900){h.fail("Timed out at "+last+" with forms "+models);return;}
                if(ticks%60==1)trackView(level,p.blockPosition());
                if(run.won){
                    h.assertTrue(UnlitSanctum.hasDefeated(p.server,p.getUUID()) && models.containsAll(Set.of("angel","bleeding","demon")),"victory recorded only after all three actual carriers");
                    h.assertTrue(actions.containsAll(Set.of("pillars","prayer","wings","gaze","tears","scream","combo",
                            "wingcut_l","hook_l","fire","grab","funeral","dance","awaken","rupture","stagger")), "real skill rotation and both reveals: "+actions);
                    if(!UnlitSanctum.inside(p)){
                        h.assertTrue(p.getX()==31.25 && p.getZ()==11.25,"victory returns to origin");
                        h.succeed();
                    }
                    return;
                }
                var stage=run.route.getString("Stage");
                if(!stage.equals(last)){
                    age=0;last=stage;
                    com.mojang.logging.LogUtils.getLogger().info("Sanctum walkthrough {} at tick {}", stage,ticks);
                }
                age++;
                if (stage.isEmpty()) return;
                p.setHealth(p.getMaxHealth());
                switch(stage){
                    case "FOYER" -> {
                        // The service holds the player at the vestibule until the actual building job completes.
                        if (run.route.getInt("Lamp")<3) press(-7,8);
                        else at(5,8);
                    }
                    case "GALLERY" -> {
                        int wave=run.route.getInt("Choir");
                        at(0,25+wave*12);
                        for(var m : level.getEntitiesOfClass(Mob.class, new AABB(o.offset(-12,-2,17),o.offset(13,10,53)),
                                m->m.getTags().contains("tnc_sanctum_route_"+o.asLong()))) {
                            if(m.isAlive())strike(m);
                        }
                        if(wave==3) {
                            if(!run.route.getBoolean("Chandelier"))press(5,49);else press(-5,50);
                        }
                    }
                    case "CHIMES" -> {
                        int bell=Math.min(2,run.route.getInt("Bells"));
                        press(new int[]{0,-6,6}[bell],65);
                    }
                    case "WEDGE" -> press(4,71);
                    case "GATE" -> {
                        at((wristHits%2==0)?-8:8,100);
                        for(var m:level.getEntitiesOfClass(Mob.class,new AABB(o.offset(-12,-1,96),o.offset(13,7,106)),
                                m->m.isAlive() && m.getName().getString().equals("落地腕锁"))) {
                            if ((m.getX()-o.getX()<0)==(wristHits%2==0)) {strike(m);wristHits++;}
                        }
                    }
                    case "CRANK" -> press(0,126);
                    case "PAGE" -> press(5,144);
                    case "CANDLE" -> press(0,148);
                    case "ARMED" -> {
                        h.assertTrue(level.getBlockState(o.offset(0,-1,224)).isSolid(),"real arena constructed");
                        h.assertTrue(level.getBlockState(o.offset(0,72,224)).is(Blocks.AIR),"final wings have headroom");
                        press(0,197);
                    }
                    case "BOSS" -> {
                        if(attempts==0){
                            attempts++;
                            h.assertTrue(UnlitSanctum.retry(p) && !run.won && p.blockPosition().getZ()==o.getZ()+150,"failed attempt restores inner candle");
                            return;
                        }
                        if(spaceChecks<12) {
                            if(spaceChecks==0)at(48,224);
                            if(spaceChecks>0 && spaceChecks<=4)h.assertTrue(p.blockPosition().equals(o.offset(48,0,224)),
                                    "survival player can stand beyond the former 34-block combat leash");
                            if(spaceChecks==4) {
                                p.setGameMode(net.minecraft.world.level.GameType.CREATIVE);
                                p.getAbilities().flying=true;
                                p.teleportTo(o.getX()+48.5,o.getY()+60,o.getZ()+224.5);
                            }
                            if(spaceChecks>=5 && spaceChecks<=10)h.assertTrue(p.blockPosition().equals(o.offset(48,60,224)),
                                    "creative flight retains altitude and horizontal position across server ticks");
                            if(spaceChecks==11){p.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);at(0,201);}
                            spaceChecks++;return;
                        }
                        var hall=new AABB(o.offset(-55,-3,170),o.offset(56,93,280));
                        for(var m:level.getEntitiesOfClass(Mob.class,hall,m->m.getTags().contains("tnc_false_god"))){
                            var look=m.getPersistentData().getCompound("TncSanctumAppearance");
                            var model=look.getString("Model");
                            if(model.equals("bleeding_feral")) model="bleeding";
                            String action=look.getString("Action");
                            models.add(model); actions.add(action);
                            if(action.equals("dance") && level.getGameTime()-look.getLong("Start")==91) {
                                at(p.getX()<o.getX()+.5?14:-14,224);
                            }
                            boolean basic=switch(model){
                                case "angel" -> actions.containsAll(Set.of("pillars","prayer","wings"));
                                case "bleeding" -> actions.containsAll(Set.of("tears","scream","gaze"));
                                case "demon" -> actions.containsAll(Set.of("wingcut_l","hook_l","fire","grab","funeral"));
                                default -> false;
                            };
                            if(!basic) continue;
                            if(model.equals("angel") || model.equals("bleeding") && action.equals("combo")
                                    && level.getGameTime()-look.getLong("Start")>SanctumTimings.HEAVY_IMPACT
                                    || model.equals("demon") && actions.contains("dance") && action.equals("stagger")) strike(m);
                            else if(!model.equals("angel") && raging.add(model)) strike(m);
                        }
                    }
                }
            }
        }
        h.onEachTick(new Walk());
    }

    @GameTest(template="empty",timeoutTicks=60)
    public static void offlineTeammateRejoinsTheNewAttemptAlive(GameTestHelper h) {
        var leader=participant(h);
        var returning=participant(h);
        h.assertTrue(UnlitSanctum.enter(leader),"open an encounter");
        var data=SanctumLedger.get(leader.server);
        var run=data.expeditions.values().stream().filter(r->r.members.containsKey(leader.getUUID())).findFirst().orElseThrow();
        run.members.put(returning.getUUID(),SanctumLedger.ReturnPoint.capture(returning));
        h.assertTrue(DownedCombat.enter(returning),"saved character was down when disconnected");
        h.assertTrue(UnlitSanctum.retry(leader),"remaining party returns to the checkpoint");
        var loaded=SanctumLedger.load(data.save(new CompoundTag())).expeditions.get(run.id);
        h.assertTrue(loaded.pendingRevival.equals(Set.of(returning.getUUID())),"only the absent member carries a durable revival");
        var origin=run.origin();
        returning.teleportTo(leader.serverLevel(),origin.getX()+.5,origin.getY(),origin.getZ()+.5,0,0);
        UnlitSanctum.login(returning);
        h.assertTrue(!DownedCombat.isDowned(returning) && returning.getHealth()==returning.getMaxHealth()
                && run.pendingRevival.isEmpty(),"revive once on reconnection");
        UnlitSanctum.leave(leader);
        UnlitSanctum.leave(returning);
        h.succeed();
    }
}
