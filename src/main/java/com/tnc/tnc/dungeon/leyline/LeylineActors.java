package com.tnc.tnc.dungeon.leyline;

import com.tnc.tnc.dungeon.*;
import com.tnc.tnc.dungeon.DungeonData.Run;
import com.tnc.tnc.dungeon.DungeonGraph.Room;
import com.tnc.tnc.life.pasture.*;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import java.util.*;

/** Physical actors share the dungeon's saved enemy ledger, including restart recovery. */
@net.minecraftforge.fml.common.Mod.EventBusSubscriber(modid="tnc")
public final class LeylineActors {
    private static List<DungeonMob> actors(ServerLevel l,Run r,Room room) {
        return l.getEntitiesOfClass(DungeonMob.class,new net.minecraft.world.phys.AABB(DungeonRealm.center(r,room)).inflate(17,32,17),m->m.isAlive()&&m.getPersistentData().hasUUID("DungeonRun")&&r.id.equals(m.getPersistentData().getUUID("DungeonRun"))&&m.getPersistentData().getInt("DungeonRoom")==room.id()&&!m.getPersistentData().getString("PuzzleRole").isEmpty());
    }
    private static DungeonMob spawn(ServerLevel l,Run r,Room room,String species,String role,String name,BlockPos at,float hp) {
        var mob=DungeonContent.MOBS.get(species).create(l); if(mob==null) throw new IllegalStateException("Cannot create puzzle actor "+species);
        mob.configure(r.floor,1,false); mob.setCustomName(Component.literal(name)); mob.setCustomNameVisible(false); mob.setPos(at.getX()+.5,at.getY(),at.getZ()+.5);
        mob.getAttribute(Attributes.MAX_HEALTH).setBaseValue(hp); mob.setHealth(hp); mob.setNoAi(true); mob.setPersistenceRequired(); mob.getPersistentData().putString("PuzzleRole",role);
        if(!l.addFreshEntity(mob)) throw new IllegalStateException("Puzzle actor spawn rejected: "+species);
        DungeonService.trackSpawn(l,r,room,mob); return mob;
    }
    private static boolean ledgerAlive(Run r,Room room,String role) {
        var enemies=r.state().enemies.getCompound(Integer.toString(room.id()));
        for(String id:enemies.getAllKeys()) if(enemies.getCompound(id).getCompound("ForgeData").getString("PuzzleRole").equals(role)) return true;
        return false;
    }
    public static void tick(ServerLevel l,Run r,Room room,CompoundTag s,LeylineKind kind,List<ServerPlayer> players,long now) {
        var o=DungeonRealm.center(r,room);
        if(!s.getBoolean("ActorsSpawned")) {
            switch(kind) {
                case FACTIONS -> { for(int i=0;i<3;i++) { spawn(l,r,room,"lizard_ember","fire","熔岩守卫",o.offset(-6,1,i*3-3),80); spawn(l,r,room,"skeleton_frost","ice","寒霜守卫",o.offset(6,1,i*3-3),70); } }
                case STOMACH -> { for(int i=0;i<3;i++) spawn(l,r,room,"tentacle_crawler","sleeper","沉睡噬魔巨虫",o.offset(i*5-5,1,5),100); }
                case PUPPETS -> { for(int i=0;i<3;i++) spawn(l,r,room,"skeleton_warden","puppet","悬丝黄金魔偶",o.offset(i*4-4,1,3),120); var nodes=LeylineService.nodes(s,"puppeteer"); spawn(l,r,room,"goblin_mage","puppeteer","提线地精",nodes.isEmpty()?o.offset(-7,6,7):BlockPos.of(nodes.get(0).getLong("Pos")).above(),10); }
                case CHESS -> spawn(l,r,room,"slime_prism","reflection","水银魔力镜像",o.offset(4,1,-4),80);
                default -> { }
            }
            s.putBoolean("ActorsSpawned",true); LeylineService.markDirty(l.getServer());
        }
        var mobs=actors(l,r,room);
        switch(kind) {
            case FACTIONS -> factions(l,r,room,s,mobs,players,now);
            case STOMACH -> {
                for(var mob:mobs) if(mob.getPersistentData().getString("PuzzleRole").equals("sleeper")) { mob.setNoAi(!s.getBoolean("Awake")||s.getBoolean("Sedated")); if(!mob.isNoAi()) mob.setTarget(nearest(mob,players)); }
                if(s.getBoolean("Awake")&&!s.getBoolean("Sedated")&&now%40==0) for(var p:players) { p.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.POISON,80)); p.hurt(p.damageSources().magic(),1); }
            }
            case PUPPETS -> {
                boolean master=mobs.stream().anyMatch(m->m.getPersistentData().getString("PuzzleRole").equals("puppeteer"));
                if(!master&&!ledgerAlive(r,room,"puppeteer")) { collapse(l,r,room,s); LeylineService.solve(l,r,room,players.get(0)); }
                else for(var m:mobs) if(m.getPersistentData().getString("PuzzleRole").equals("puppet")) { m.setNoAi(false); m.setTarget(nearest(m,players)); }
            }
            case CHESS -> chess(l,r,room,s,mobs,players,now);
            default -> { }
        }
    }
    private static ServerPlayer nearest(Mob mob,List<ServerPlayer> players) { return players.stream().min(Comparator.comparingDouble(mob::distanceToSqr)).orElse(null); }
    private static void factions(ServerLevel l,Run r,Room room,CompoundTag s,List<DungeonMob> mobs,List<ServerPlayer> players,long now) {
        var fighters=mobs.stream().filter(m->Set.of("fire","ice").contains(m.getPersistentData().getString("PuzzleRole"))).toList();
        if(!s.getBoolean("BarrierBroken")) {
            for(var m:fighters) { var p=nearest(m,players); if(p!=null&&p.distanceToSqr(m)<9&&now%20==0) p.hurt(p.damageSources().mobAttack(m),8); }
            return;
        }
        if(fighters.size()==1) { var survivor=fighters.get(0); if(!s.getBoolean("SurvivorWeakened")) { s.putBoolean("SurvivorWeakened",true); survivor.setHealth(Math.min(survivor.getHealth(),survivor.getMaxHealth()*.2f)); survivor.getAttribute(Attributes.ARMOR).setBaseValue(0); players.forEach(p->p.displayClientMessage(Component.literal("最后的胜者已重伤。缩水捕捉魔药与空拘束笼能将它带回家。"),true)); } return; }
        if(fighters.isEmpty()&&!ledgerAlive(r,room,"fire")&&!ledgerAlive(r,room,"ice")) { LeylineService.solve(l,r,room,players.get(0)); return; }
        for(var m:fighters) { var enemy=fighters.stream().filter(x->x!=m&&!x.getPersistentData().getString("PuzzleRole").equals(m.getPersistentData().getString("PuzzleRole"))).min(Comparator.comparingDouble(m::distanceToSqr)).orElse(null);
            if(enemy==null) { // A same-side group still competes for the exhausted supply until one winner remains.
                enemy=fighters.stream().filter(x->x!=m).min(Comparator.comparingDouble(m::distanceToSqr)).orElse(null);
            }
            if(enemy==null) continue; var direction=enemy.position().subtract(m.position()); m.setYRot((float)(Math.atan2(direction.z,direction.x)*180/Math.PI)-90); m.setDeltaMovement(direction.normalize().scale(.13).multiply(1,0,1).add(0,m.getDeltaMovement().y,0));
            if(now%20==0&&direction.lengthSqr()<6) { enemy.hurt(m.damageSources().mobAttack(m),7); m.swing(net.minecraft.world.InteractionHand.MAIN_HAND); }
        }
    }
    private static void chess(ServerLevel l,Run r,Room room,CompoundTag s,List<DungeonMob> mobs,List<ServerPlayer> players,long now) {
        var mirror=mobs.stream().filter(m->m.getPersistentData().getString("PuzzleRole").equals("reflection")).findFirst().orElse(null); if(mirror==null) return;
        var p=players.get(0); if(s.hasUUID("ChessPlayer")) p=players.stream().filter(q->q.getUUID().equals(s.getUUID("ChessPlayer"))).findFirst().orElse(players.get(0));
        var cells=LeylineService.nodes(s,"chess_cell"); if(cells.size()!=25) return;
        var position=p.position();
        var cell=cells.stream().min(Comparator.comparingDouble(n->BlockPos.of(n.getLong("Pos")).distToCenterSqr(position))).orElseThrow();
        int current=cell.getInt("Index"); if(!s.hasUUID("ChessPlayer")) { s.putUUID("ChessPlayer",p.getUUID()); s.putInt("ChessPlayerCell",current); }
        int previous=s.getInt("ChessPlayerCell"); if(previous==current) return; s.putInt("ChessPlayerCell",current);
        if(Math.abs(current%5-previous%5)+Math.abs(current/5-previous/5)!=1) return;
        int target=LeylinePuzzleRules.moveMirror(s.getInt("MirrorCell"),current%5-previous%5,current/5-previous/5);
        var destination=cells.stream().filter(n->n.getInt("Index")==target).findFirst().orElseThrow(); var at=BlockPos.of(destination.getLong("Pos")).above();
        boolean pillar=LeylineService.nodes(s,"pillar").stream().anyMatch(n->BlockPos.of(n.getLong("Pos")).distSqr(at)<2);
        if(!pillar&&l.noCollision(mirror,mirror.getBoundingBox().move(Vec3.atBottomCenterOf(at).subtract(mirror.position())))) { s.putInt("MirrorCell",target); mirror.teleportTo(at.getX()+.5,at.getY(),at.getZ()+.5); }
        for(var drain:LeylineService.nodes(s,"drain")) if(BlockPos.of(drain.getLong("Pos")).equals(at.below())&&s.getInt("MirrorCell")==target) { DungeonService.killed(mirror); mirror.discard(); s.putBoolean("MirrorDrained",true); LeylineService.solve(l,r,room,p); break; }
        LeylineService.markDirty(l.getServer());
    }
    public static void wake(ServerLevel l,Run r,Room room,CompoundTag s) { if(s.getBoolean("Sedated")) return; s.putBoolean("Awake",true); for(var m:actors(l,r,room)) if(m.getPersistentData().getString("PuzzleRole").equals("sleeper")) m.setNoAi(false); LeylineService.markDirty(l.getServer()); }
    public static void sedate(ServerLevel l,Run r,Room room,CompoundTag s) { for(var m:actors(l,r,room)) if(m.getPersistentData().getString("PuzzleRole").equals("sleeper")) { m.setTarget(null); m.setNoAi(true); m.getNavigation().stop(); } }
    public static void collapse(ServerLevel l,Run r,Room room,CompoundTag s) { for(var m:actors(l,r,room)) if(Set.of("puppet","puppeteer").contains(m.getPersistentData().getString("PuzzleRole"))) { DungeonService.killed(m); m.discard(); } s.putBoolean("PuppetsCollapsed",true); }
    public static void exhibit(ServerLevel l,Run r,Room room,CompoundTag s,BlockPos at,boolean friendly,ServerPlayer p) {
        if(!friendly) { var knight=spawn(l,r,room,"skeleton_warden","exhibit_knight","腐化琥珀骑士",at,180); knight.setNoAi(false); knight.setTarget(p); return; }
        var animal=PastureRegistry.TYPES.get("ringstone_tortoise").create(l); if(animal==null) throw new IllegalStateException("Missing prehistoric pasture animal");
        animal.setPos(at.getX()+.5,at.getY(),at.getZ()+.5); animal.setCustomName(Component.literal("史前星髓环石龟")); animal.setPersistenceRequired(); var saved=new CompoundTag(); animal.saveWithoutId(saved); saved.putUUID("PastureOwner",p.getUUID()); saved.putInt("CareCount",3); animal.load(saved); animal.calmFor(200); animal.getPersistentData().putBoolean("AncientExhibit",true);
        if(l.addFreshEntity(animal)) { s.putUUID("AncientAnimal",animal.getUUID()); p.displayClientMessage(Component.literal("古兽已认定你为伴生者；用二阶或更高牧场笼长按收走。"),true); }
    }
    @SubscribeEvent public static void damage(net.minecraftforge.event.entity.living.LivingAttackEvent e) {
        if(!(e.getEntity() instanceof DungeonMob mob)||!(mob.level() instanceof ServerLevel l)) return;
        var t=mob.getPersistentData(); if(!t.hasUUID("DungeonRun")) return; var r=DungeonData.get(l.getServer()).runs.get(t.getUUID("DungeonRun")); if(r==null||r.closed||r.floor!=t.getInt("DungeonFloor")) return;
        int index=t.getInt("DungeonRoom"); if(index<0||index>=r.graph().rooms.size()) return; var room=r.graph().rooms.get(index); var s=LeylineService.state(r,room);
        switch(t.getString("PuzzleRole")) {
            case "puppet" -> { if(!s.getBoolean("PuppetsCollapsed")) e.setCanceled(true); }
            case "reflection" -> { e.setCanceled(true); if(e.getSource().getEntity() instanceof ServerPlayer p) { if(t.getLong("LastReflected")!=l.getGameTime()) { t.putLong("LastReflected",l.getGameTime()); p.hurt(p.damageSources().magic(),e.getAmount()); p.displayClientMessage(Component.literal("水银镜像反射了全部伤害，地漏才是出口。"),true); } } }
            case "sleeper" -> { if(!s.getBoolean("Sedated")) wake(l,r,room,s); }
            default -> { }
        }
    }
    private LeylineActors() {}
}
