package com.tnc.tnc.sanctum;

import com.tnc.tnc.combat.DownedCombat;
import com.tnc.tnc.dungeon.DungeonRealm;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.living.LivingHurtEvent;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Entry and completion hooks for the future final story chapter. Call on the logical server. */
public final class UnlitSanctum {
    public static final ResourceKey<Level> LEVEL = ResourceKey.create(Registries.DIMENSION,
            ResourceLocation.fromNamespaceAndPath("tnc", "unlit_sanctum"));
    private static final Map<UUID, Active> active = new HashMap<>();
    private UnlitSanctum() {}

    private static final class Active {
        final SanctumLedger.Expedition run;
        final SanctumRoute route;
        final java.util.Set<UUID> atVestibule = new java.util.HashSet<>();
        FalseGodFight fight;
        int epilogue, rehearsalReady;
        Active(ServerLevel level, SanctumLedger.Expedition run) {
            this.run = run;
            this.route = new SanctumRoute(level, run.origin());
            if (!run.route.isEmpty()) route.restore(run.route.copy());
        }
        void close() {
            if (fight != null) fight.close();
            route.close();
        }
    }

    public static boolean enter(ServerPlayer player) { return enter(List.of(player)); }

    public static boolean enter(Collection<ServerPlayer> party) {
        var members = new LinkedHashSet<>(party);
        if (members.isEmpty() || members.size() > 4) return false;
        var server = members.iterator().next().server;
        var level = server.getLevel(LEVEL);
        if (level == null) return false;
        var ledger = SanctumLedger.get(server);
        for (var p : members) {
            if (com.tnc.tnc.world.yan.YanPrologue.active(p) || p.level().dimension().equals(com.tnc.tnc.world.yan.YanPrologue.WORLD)) {
                say(p, "先结束序幕再进入无昼圣堂。开幕战中可蹲下右击祭坛上的雷纹石进入回溯。");
                return false;
            }
            if (p.server != server || !p.isAlive() || p.isSpectator() || DownedCombat.isDowned(p)
                    || inside(p) || expedition(p) != null) return false;
        }
        var run = new SanctumLedger.Expedition(UUID.randomUUID(), ledger.vacantSlot());
        for (var p : members) run.members.put(p.getUUID(), SanctumLedger.ReturnPoint.capture(p));
        ledger.expeditions.put(run.id, run);
        ledger.setDirty();
        DungeonRealm.write(() -> active.put(run.id, new Active(level, run)));
        for (var p : members) {
            move(p, level, run.origin());
            if (!inside(p)) {
                for (var member : members) {
                    if (inside(member)) leave(member);
                    else detach(member,run);
                }
                return false;
            }
        }
        members.forEach(p -> say(p, "无昼圣堂　·　前方的烛火依次灭了。"));
        return true;
    }

    public static boolean hasDefeated(MinecraftServer server, UUID player) {
        return SanctumLedger.get(server).victors.contains(player);
    }

    /** The caller is the permission-checked command's player. Only their own saved expedition is altered. */
    static boolean bossRehearsal(ServerPlayer p) {
        if (!inside(p) && !enter(p)) return refusal(p,"无法进入演练。请先结束当前冒险，并以存活的非旁观身份重试。");
        var run=expedition(p);
        var session=run==null?null:active.get(run.id);
        if(session==null || run.won)return refusal(p,"当前没有可操作的圣堂挑战。");
        if(!p.isAlive() || p.isSpectator() || DownedCombat.isDowned(p))
            return refusal(p,"请先恢复行动再开启演练。");
        markRehearsal(p,session);
        if(session.fight==null && run.requestedPhase==0) queue(p,session,1);
        if(session.fight!=null) rally(session,present(p.server,run),p.serverLevel(),true);
        say(p,session.fight!=null?"本队已在首领演练中。可用 /sanctum phase next 或 /sanctum phase 1、2、3。"
                :"已预约本队首领演练。大殿准备好后，全队将直接抵达首领面前。");
        return true;
    }

    static boolean phaseRehearsal(ServerPlayer p,int requested) {
        var run=inside(p)?expedition(p):null;
        var session=run==null?null:active.get(run.id);
        if(session==null || run.won)return refusal(p,"请先输入 /sanctum boss 开启首领演练。");
        if(!p.isAlive() || p.isSpectator() || DownedCombat.isDowned(p))
            return refusal(p,"请先恢复行动再切换阶段。");
        var fight=session.fight;
        int current=fight==null?0:fight.phase;
        boolean transitioning=fight!=null && fight.transformation>0;
        if(requested==0 && (run.requestedPhase>0 || transitioning))
            return refusal(p,"本队已有阶段请求或正在转场，请等演出结束后再前进。");
        int goal=requested==0?current+1:requested;
        if(goal<1 || goal>3)return refusal(p,"第三阶段已经是最终形态。输入 /sanctum phase 1 可重开。");
        if(run.requestedPhase==goal){say(p,"第 "+goal+" 阶段已在准备中。");return true;}
        // While the hall is forming, an explicit choice can replace the initial phase-one request.
        if((run.requestedPhase>0&&(fight!=null || run.requestedPhase!=1)) || transitioning) {
            if(transitioning && goal==current){say(p,"第 "+goal+" 阶段的转场正在播放。");return true;}
            return refusal(p,"正在按顺序播放转场。请等当前请求完成后再切换。");
        }
        markRehearsal(p,session);
        if(fight!=null && goal<=current) {
            fight.close();
            session.fight=null;
        }
        if(fight!=null)rally(session,present(p.server,run),p.serverLevel(),true);
        queue(p,session,goal);
        say(p,"已选择第 "+goal+" 阶段。将依序播放到达该形态的转场。");
        return true;
    }

    static boolean frenzyRehearsal(ServerPlayer p) {
        var run=inside(p)?expedition(p):null;
        var session=run==null?null:active.get(run.id);
        if(session==null || !run.rehearsal || run.won || session.fight==null)
            return refusal(p,"请先输入 /sanctum boss 开启本队演练，再进入第二或第三阶段。");
        if(!p.isAlive() || p.isSpectator() || DownedCombat.isDowned(p))
            return refusal(p,"请先恢复行动再触发狂躁。");
        if(run.requestedPhase>0 || session.fight.transformation>0)
            return refusal(p,"请等阶段转场结束后再触发狂躁。");
        if(session.fight.phase<2)return refusal(p,"第一阶段没有狂躁。输入 /sanctum phase next 可进入第二阶段。");
        if(!session.fight.rehearseFrenzy())return refusal(p,"本阶段狂躁已经触发，或队员尚未进入战场。重选本阶段可再试。");
        say(p,"本相已触及 30% 血量。即将发动"+(session.fight.phase==2?"错乱连斩":"终末狂舞")+"。");
        return true;
    }
    private static void markRehearsal(ServerPlayer p,Active session) {
        if(session.run.rehearsal)return;
        session.run.rehearsal=true;
        SanctumLedger.get(p.server).setDirty();
        for(var member:present(p.server,session.run))say(member,"管理员已将本次挑战切换为演练。通关仅结算本次演练。");
    }
    private static boolean refusal(ServerPlayer p,String reason){say(p,reason);return false;}
    private static void queue(ServerPlayer p,Active session,int phase) {
        session.run.requestedPhase=phase;
        session.rehearsalReady=0;
        SanctumLedger.get(p.server).setDirty();
    }
    private static void rally(Active session,List<ServerPlayer> party,ServerLevel level,boolean rehearsal) {
        var center=session.route.bossCenter();
        var doorstep=center.offset(0,0,-23);
        for(var p:party) {
            if(FalseGodFight.inHall(p.position(),center) || (!rehearsal && (p.isCreative() || p.isSpectator())))continue;
            if(DownedCombat.isDowned(p))DownedCombat.relocateAnchor(p,level,doorstep);
            move(p,level,doorstep);
        }
    }

    static boolean inside(ServerPlayer p) { return p.level().dimension().equals(LEVEL); }
    private static SanctumLedger.Expedition expedition(ServerPlayer p) {
        for (var run : SanctumLedger.get(p.server).expeditions.values()) {
            if (run.members.containsKey(p.getUUID())) return run;
        }
        return null;
    }
    public static String rescueGroup(ServerPlayer p) {
        if (!inside(p)) return null;
        var run = expedition(p);
        return run == null ? null : "sanctum:" + run.id;
    }

    public static boolean solo(ServerPlayer p) {
        var run = expedition(p);
        return run != null && run.members.size() == 1;
    }

    public static boolean useSoloRescue(ServerPlayer p) {
        var run = expedition(p);
        if (run == null || !run.spentRescues.add(p.getUUID())) return false;
        SanctumLedger.get(p.server).setDirty();
        return true;
    }

    static void tick(MinecraftServer server) {
        var level = server.getLevel(LEVEL);
        if (level == null || active.isEmpty()) return;
        for (var session : List.copyOf(active.values())) {
            List<ServerPlayer> present = present(server, session.run);
            if (present.isEmpty()) {
                suspend(server, session);
                continue;
            }
            if (session.run.won) {
                if (++session.epilogue >= 120) for (var p : present) leave(p);
                continue;
            }
            var route = session.route;
            var standing = present.stream().filter(p -> p.isAlive() && !p.isSpectator() && !DownedCombat.isDowned(p)).toList();
            DungeonRealm.write(() -> route.tick(standing));
            for (var p : standing) {
                var origin = session.run.origin();
                if (route.built() && session.atVestibule.remove(p.getUUID()) && p.blockPosition().distSqr(origin)<9)
                    move(p,level,route.checkpoint());
                // The nave, gallery and airspace are traversable. Rescue only an actual fall below the masonry.
                if (!p.isCreative() && p.getY()<origin.getY()-12) {
                    move(p, level, route.built() ? route.checkpoint() : origin);
                }
            }
            if (!route.built() || standing.isEmpty()) continue;
            if(session.run.rehearsal && session.run.requestedPhase>0 && !route.readyForBoss())
                DungeonRealm.write(route::openBossRehearsal);
            if (session.fight == null && route.readyForBoss()) {
                rally(session,present,level,session.run.rehearsal);
                session.fight = new FalseGodFight(level, route.bossCenter());
            }
            if (session.fight != null) {
                session.fight.tick(standing);
                if (session.fight.defeated()) win(server, session, present);
                else if(session.run.rehearsal && session.run.requestedPhase>0) {
                    var fight=session.fight;
                    if(fight.transformation==0 && !fight.players.isEmpty()) {
                        if(fight.phase>=session.run.requestedPhase){
                            session.run.requestedPhase=0;
                            SanctumLedger.get(server).setDirty();
                        }else if(++session.rehearsalReady>=12 && fight.advanceRehearsal())
                            session.rehearsalReady=0;
                    }
                }
            }
            if (server.getTickCount() % 20 == 0) snapshot(server, session);
        }
    }

    private static void win(MinecraftServer server, Active session, List<ServerPlayer> present) {
        session.run.won = true;
        var ledger = SanctumLedger.get(server);
        if(!session.run.rehearsal)ledger.victors.addAll(session.run.members.keySet());
        ledger.setDirty();
        session.fight.close();
        session.fight = null;
        for (var p : present) {
            if (DownedCombat.isDowned(p)) DownedCombat.revive(p);
            say(p, session.run.rehearsal?"首领演练结束　·　六秒后返回来处。":"伪神已陨　·　殿中最后一声心跳停了。六秒后返回来处。");
            p.playNotifySound(net.minecraft.sounds.SoundEvents.BEACON_DEACTIVATE,
                    net.minecraft.sounds.SoundSource.AMBIENT, .8f, .6f);
        }
        if(!session.run.rehearsal)MinecraftForge.EVENT_BUS.post(new FalseGodDefeatedEvent(server, session.run.id, session.run.members.keySet()));
    }

    private static List<ServerPlayer> present(MinecraftServer server, SanctumLedger.Expedition run) {
        var result = new ArrayList<ServerPlayer>();
        var level = server.getLevel(LEVEL);
        if (level != null) for (var p : level.players()) if (run.members.containsKey(p.getUUID())) result.add(p);
        return result;
    }
    private static void snapshot(MinecraftServer server, Active session) {
        var saved = session.route.save();
        if (!saved.equals(session.run.route)) {
            session.run.route = saved.copy();
            SanctumLedger.get(server).setDirty();
        }
    }
    private static void suspend(MinecraftServer server, Active session) {
        snapshot(server, session);
        DungeonRealm.write(session::close);
        active.remove(session.run.id);
    }

    static boolean interact(ServerPlayer p, BlockPos target) {
        if (!inside(p) || DownedCombat.isDowned(p) || p.isSpectator() || !p.isAlive()
                || p.getEyePosition().distanceToSqr(net.minecraft.world.phys.Vec3.atCenterOf(target)) > 49) return false;
        var run = expedition(p);
        var session = run == null ? null : active.get(run.id);
        if (session == null || run.won || session.fight != null || !session.route.built()) return false;
        boolean[] used = {false};
        DungeonRealm.write(() -> used[0] = session.route.interact(p, target));
        if (used[0]) snapshot(p.server, session);
        return used[0];
    }

    static void hurt(LivingHurtEvent event) {
        if (!event.getEntity().level().dimension().equals(LEVEL)) return;
        var id = event.getEntity().getUUID();
        for (var session : active.values()) {
            boolean boss = session.fight != null && session.fight.owns(id);
            if (!boss && !session.route.owns(id)) continue;
            if (event.getSource().getEntity() instanceof ServerPlayer p && !session.run.members.containsKey(p.getUUID())) {
                event.setCanceled(true);
                return;
            }
            if (boss) session.fight.hurt(event);
            else session.route.hurt(event);
            return;
        }
    }

    static boolean canRequestRetry(ServerPlayer p) {
        var run = expedition(p);
        return run != null && present(p.server, run).stream().noneMatch(q ->
                q.isAlive() && !q.isSpectator() && !DownedCombat.isDowned(q));
    }

    static void damaged(net.minecraftforge.event.entity.living.LivingDamageEvent event) {
        if (!event.getEntity().level().dimension().equals(LEVEL)) return;
        for (var session : active.values()) {
            if (session.fight != null && session.fight.owns(event.getEntity().getUUID())) {
                session.fight.damaged(event);
                return;
            }
        }
    }

    public static boolean handleWipe(ServerPlayer p) {
        if (!inside(p)) return false;
        var run = expedition(p);
        if (run == null) return false;
        if (present(p.server, run).size() < run.members.size()) {
            if (p.tickCount % 60 == 0) p.displayClientMessage(Component.literal("队友已离线。可等待重连，或输入 /sanctum retry 返回烛台。"), true);
            return true;
        }
        return retry(p);
    }

    /** Called by the shared rescue system after eligible death-prevention effects. */
    public static boolean retry(ServerPlayer p) {
        if (!inside(p)) return false;
        var run = expedition(p);
        var session = run == null ? null : active.get(run.id);
        if (session == null || run.won) return false;
        if (session.fight != null) { session.fight.close(); session.fight = null; }
        run.requestedPhase=0;
        session.rehearsalReady=0;
        DungeonRealm.write(session.route::retry);
        run.spentRescues.clear();
        run.pendingRevival.addAll(run.members.keySet());
        var level = p.serverLevel();
        for (var q : present(p.server, run)) {
            reviveForRetry(q,run);
            move(q, level, session.route.checkpoint());
            say(q, "灯芯重新燃起。已解开的机关仍然有效。");
        }
        snapshot(p.server, session);
        SanctumLedger.get(p.server).setDirty();
        return true;
    }

    private static void reviveForRetry(ServerPlayer p, SanctumLedger.Expedition run) {
        if (!run.pendingRevival.remove(p.getUUID())) return;
        if (DownedCombat.isDowned(p)) DownedCombat.revive(p);
        p.setHealth(p.getMaxHealth());
        p.clearFire();
        p.invulnerableTime = 60;
        SanctumLedger.get(p.server).setDirty();
    }

    static void login(ServerPlayer p) {
        var run = expedition(p);
        if (run == null) {
            if (inside(p)) spawnReturn(p);
            return;
        }
        if (!inside(p)) { detach(p,run); return; }
        if (run.won) { leave(p); return; }
        reviveForRetry(p,run);
        var level = p.serverLevel();
        var session = active.get(run.id);
        if (session == null) {
            DungeonRealm.write(() -> active.put(run.id, new Active(level, run)));
            session = active.get(run.id);
        }
        var dest = session.fight != null ? session.route.bossCenter().offset(0,0,-23)
                : session.route.built() ? session.route.checkpoint() : run.origin();
        if (!session.route.built()) session.atVestibule.add(p.getUUID());
        if (DownedCombat.isDowned(p)) DownedCombat.relocateAnchor(p, level, dest);
        move(p, level, dest);
        p.invulnerableTime = 60;
        say(p, "已回到无昼圣堂。输入 /sanctum leave 可返回来处。");
    }

    static boolean leave(ServerPlayer p) {
        var run = expedition(p);
        if (run == null) return false;
        var pos = run.members.get(p.getUUID());
        if (DownedCombat.isDowned(p)) DownedCombat.revive(p);
        var target = p.server.getLevel(ResourceKey.create(Registries.DIMENSION, pos.dimension()));
        if (target == null) spawnReturn(p);
        else { p.stopRiding(); p.teleportTo(target, pos.x(), pos.y(), pos.z(), pos.yaw(), pos.pitch()); }
        if (inside(p)) return false;
        p.fallDistance = 0;
        if (run.members.containsKey(p.getUUID())) detach(p,run);
        return true;
    }
    static void leftRealm(ServerPlayer p) {
        var run = expedition(p);
        if (run != null && !inside(p)) detach(p,run);
    }
    private static void detach(ServerPlayer p, SanctumLedger.Expedition run) {
        run.members.remove(p.getUUID());
        run.spentRescues.remove(p.getUUID());
        run.pendingRevival.remove(p.getUUID());
        var ledger = SanctumLedger.get(p.server);
        if (run.members.isEmpty()) {
            var session = active.remove(run.id);
            if (session != null) DungeonRealm.write(session::close);
            ledger.expeditions.remove(run.id);
        }
        ledger.setDirty();
    }

    private static void spawnReturn(ServerPlayer p) {
        move(p, p.server.overworld(), p.server.overworld().getSharedSpawnPos());
    }
    private static void move(ServerPlayer p, ServerLevel level, BlockPos at) {
        p.stopRiding();
        p.teleportTo(level, at.getX()+.5, at.getY(), at.getZ()+.5, 0, 0);
        p.setDeltaMovement(0,0,0);
        p.fallDistance = 0;
    }
    private static void say(ServerPlayer p, String message) { p.sendSystemMessage(Component.literal(message)); }

    static void stop(MinecraftServer server) {
        for (var session : List.copyOf(active.values())) suspend(server, session);
    }
}
