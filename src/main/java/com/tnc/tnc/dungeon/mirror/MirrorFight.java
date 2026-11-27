package com.tnc.tnc.dungeon.mirror;

import com.tnc.tnc.combat.DownedCombat;
import com.tnc.tnc.dungeon.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.TickTask;
import net.minecraft.server.level.*;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.Vec3;
import java.util.*;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** The immutable participant list, phase, and each surviving reflection belong to the dungeon save. */
@Mod.EventBusSubscriber(modid="tnc")
public final class MirrorFight {
    private static final String KEY="EchoingMirror";
    static final int MAGIC=1, RETURNING=2, DUEL=3, OPEN=4, FINISHED=5;
    private static final Map<UUID,ServerBossEvent> BARS=new HashMap<>();
    private MirrorFight() {}

    private static CompoundTag state(DungeonData.Run r) {return r.state().events.getCompound(KEY);}
    private static DungeonGraph.Room guardian(DungeonData.Run r) {
        return r.graph().rooms.stream().filter(room->room.kind()==DungeonGraph.Kind.GUARDIAN).findFirst().orElseThrow();
    }
    private static boolean current(DungeonData.Run r) {
        return r!=null && !r.closed && r.floor==30 && r.theme==DungeonTheme.ASTRAL;
    }
    @SubscribeEvent public static void loaded(EntityJoinLevelEvent event) {
        if(!(event.getLevel() instanceof ServerLevel l) || !DungeonRealm.inside(l))return;
        var entity=event.getEntity();
        if(entity instanceof ServerPlayer player){
            var server=l.getServer();
            server.tell(new TickTask(server.getTickCount()+1,()->{
                var r=DungeonService.run(player);
                if(current(r) && !player.isRemoved())
                    MirrorArena.liftFromOldFloor(l,r,DungeonRealm.center(r,guardian(r)),player);
            }));
            return;
        }
        UUID run=entity instanceof EchoDouble m?m.runId():entity instanceof EchoCore core?core.runId():null;
        if(run==null)return;
        // The chunk's entity list may arrive after the party abandoned elsewhere. Check on the
        // next tick, after a newly spawned wave has committed its own durable entity IDs.
        var server=l.getServer();
        server.tell(new TickTask(server.getTickCount()+1,()->{
            var r=DungeonData.get(server).runs.get(run);
            if(r==null || entity.isRemoved())return;
            if(!current(r)){entity.discard();return;}
            var s=state(r);
            if(s.getInt("Phase")==FINISHED){entity.discard();return;}
            if(entity instanceof EchoDouble copy && copy.ownerId()!=null && s.contains("People")){
                var entry=s.getCompound("People").getCompound(copy.ownerId().toString());
                if(!entry.hasUUID("Entity") || !copy.getUUID().equals(entry.getUUID("Entity"))
                        || entry.getBoolean("Down") && copy.isAlive())entity.discard();
                else MirrorArena.liftFromOldFloor(l,r,copy.center(),copy);
            }
        }));
    }
    public static UUID activeRun(ServerPlayer p) {
        if(!DungeonRealm.inside(p.level()))return null;
        var r=DungeonService.run(p);
        if(!current(r))return null;
        var s=state(r);
        return s.getCompound("People").contains(p.getStringUUID()) && inRoom(p,origin(r,s)) ? r.id : null;
    }
    public static boolean silent(ServerPlayer p) {
        if(!DungeonRealm.inside(p.level()))return false;
        var r=DungeonService.run(p);
        if(!current(r))return false;
        var s=state(r);
        return s.getInt("Phase")==DUEL && s.getCompound("People").contains(p.getStringUUID()) && inRoom(p,origin(r,s));
    }

    public static ServerPlayer target(EchoDouble m) {
        if(!(m.level() instanceof ServerLevel level) || m.runId()==null)return null;
        var r=DungeonData.get(level.getServer()).runs.get(m.runId());
        if(!current(r))return null;
        var s=state(r);
        int phase=s.getInt("Phase");
        if(phase!=(m.melee()?DUEL:MAGIC) || s.getInt("Grace")>0 || m.ownerId()==null)return null;
        var records=s.getCompound("People");
        var own=records.getCompound(m.ownerId().toString());
        if(own.getBoolean("Down") || !own.hasUUID("Entity") || !own.getUUID("Entity").equals(m.getUUID()))return null;
        var owner=level.getServer().getPlayerList().getPlayer(m.ownerId());
        if(eligible(owner,level,records,m.center()))return owner;
        return level.players().stream().filter(p->eligible(p,level,records,m.center()))
                .min(Comparator.comparingDouble(m::distanceToSqr)).orElse(null);
    }
    private static boolean eligible(ServerPlayer p,ServerLevel level,CompoundTag people,BlockPos c) {
        return p!=null && p.level()==level && p.isAlive() && !p.isSpectator() && !p.isCreative()
                && people.contains(p.getStringUUID()) && inRoom(p,c)
                && !DownedCombat.isDowned(p) && !DungeonService.recovering(p);
    }
    private static BlockPos origin(DungeonData.Run r,CompoundTag state){
        return state.contains("Origin") ? BlockPos.of(state.getLong("Origin")) : DungeonRealm.center(r,guardian(r));
    }
    private static boolean inRoom(ServerPlayer p,BlockPos c){
        return Math.abs(p.getX()-c.getX()-.5)<23.6 && Math.abs(p.getZ()-c.getZ()-.5)<23.6
                && p.getY()>=c.getY()-1 && p.getY()<c.getY()+20;
    }

    public static boolean begin(ServerLevel l,DungeonData.Run r,DungeonGraph.Room room,List<ServerPlayer> party) {
        var c=DungeonRealm.center(r,room);
        if(!MirrorArena.ensureFloor(l,r,room,party))return false;
        var f=r.state();
        if(!f.events.contains(KEY))f.events.put(KEY,new CompoundTag());
        var s=state(r);
        if(s.getInt("Phase")!=0)return true;
        var core=ensureCore(l,r,room,s);
        if(core==null)return false;
        // Stepping onto the central inlay wakes a settled reflection.
        if(party.isEmpty() || party.size()!=r.members.size()
                || party.stream().anyMatch(p->p.isSpectator() || DownedCombat.isDowned(p) || DungeonService.recovering(p))
                || party.stream().noneMatch(p->MirrorArena.onSeal(p,l,c)))return false;
        var people=new CompoundTag();
        var ids=new ListTag();
        List<EchoDouble> pending=new ArrayList<>();
        for(var p:party){
            var m=MirrorEntities.DOUBLE.get().create(l);
            if(m==null)return false;
            m.bind(p,r.id,c);
            place(m,c,p.position(),people.size(),party.size());
            var record=new CompoundTag();
            record.put("Original",m.saveWithoutId(new CompoundTag()));
            record.putUUID("Entity",m.getUUID());
            people.put(p.getStringUUID(),record);
            ids.add(StringTag.valueOf(p.getStringUUID()));
            pending.add(m);
        }
        s.put("People",people);
        s.putInt("Room",room.id());
        s.putLong("Origin",c.asLong());
        s.putInt("Phase",MAGIC);
        s.putInt("Grace",30);
        for(var m:pending){
            if(!l.addFreshEntity(m)){
                pending.forEach(Entity::discard);
                s.remove("People");s.remove("Phase");
                dirty(l);
                return false;
            }
            m.action("rise",30);
            snapshot(m,people.getCompound(m.ownerId().toString()));
        }
        f.started|=1<<room.id();
        f.participants.put(Integer.toString(room.id()),ids);
        DungeonRealm.doors(l,r,room,true);
        signal(l,c,SoundEvents.AMETHYST_BLOCK_RESONATE,0.6f);
        party.forEach(p->DungeonService.message(p,"银纹掠过脚下，石镜里映出了你的名字。"));
        dirty(l);
        return true;
    }

    private static void place(EchoDouble m,BlockPos c,Vec3 player,int index,int count){
        // At the six-block entry mark this puts the solo reflection 33° beside the core on screen.
        double angle=count==1 ? Math.atan2(c.getZ()+.5-player.z,c.getX()+.5-player.x)+Math.toRadians(66)
                : 2*Math.PI*index/count;
        double x=c.getX()+.5+6*Math.cos(angle),z=c.getZ()+.5+6*Math.sin(angle);
        m.moveTo(x,c.getY()+1,z,(float)Math.toDegrees(Math.atan2(-(player.x-x),player.z-z)),0);
    }
    private static EchoCore ensureCore(ServerLevel l,DungeonData.Run r,DungeonGraph.Room room,CompoundTag s){
        var c=DungeonRealm.center(r,room);
        if(!l.areEntitiesLoaded(new ChunkPos(c).toLong()))return null;
        if(s.hasUUID("Core") && l.getEntity(s.getUUID("Core")) instanceof EchoCore core)return core;
        var core=MirrorEntities.CORE.get().create(l);
        if(core==null)return null;
        if(s.hasUUID("Core"))core.setUUID(s.getUUID("Core"));
        core.bind(r.id,c);
        core.setYRot(MirrorArena.entrance(r,room).toYRot());
        if(!l.addFreshEntity(core))return null;
        s.putUUID("Core",core.getUUID());
        if(s.getInt("Phase")==OPEN)core.expose();
        dirty(l);
        return core;
    }
    private static void snapshot(EchoDouble m,CompoundTag record){
        record.put("Live",m.saveWithoutId(new CompoundTag()));
        record.putLong("At",m.blockPosition().asLong());
    }
    private static EchoDouble reflection(ServerLevel l,DungeonData.Run r,CompoundTag record){
        if(record.getBoolean("Down") || !record.hasUUID("Entity"))return null;
        var uuid=record.getUUID("Entity");
        if(l.getEntity(uuid) instanceof EchoDouble m)return m;
        if(!record.contains("Live") || !l.areEntitiesLoaded(new ChunkPos(BlockPos.of(record.getLong("At"))).toLong()))return null;
        // Loaded entities are authoritative. Use the saved image only after the chunk's entity list is ready.
        var m=MirrorEntities.DOUBLE.get().create(l);
        if(m==null)return null;
        m.load(record.getCompound("Live"));
        MirrorArena.liftFromOldFloor(l,r,m.center(),m);
        return l.addFreshEntity(m)?m:null;
    }

    public static void serverTick(ServerLevel l) {
        for(var r:DungeonData.get(l.getServer()).runs.values()){
            if(!current(r) || r.building || !r.state().built)continue;
            var party=DungeonService.online(l.getServer(),r);
            if(party.isEmpty())continue;
            var room=guardian(r);
            if(!MirrorArena.built(r,room) || !MirrorArena.ensureFloor(l,r,room,party))continue;
            var s=state(r);
            if(s.getInt("Phase")!=0){
                if(s.getInt("Phase")!=FINISHED)tick(l,r,r.graph().rooms.get(s.getInt("Room")),party);
            }else if(l.getGameTime()%20==0){
                var c=DungeonRealm.center(r,room);
                if(r.state().clear(room.id()) || party.stream().noneMatch(p->c.closerToCenterThan(p.position(),34)))continue;
                if(!r.state().events.contains(KEY))r.state().events.put(KEY,new CompoundTag());
                ensureCore(l,r,room,state(r));
            }
        }
    }

    public static void tick(ServerLevel l,DungeonData.Run r,DungeonGraph.Room room,List<ServerPlayer> party) {
        var s=state(r);
        int phase=s.getInt("Phase");
        if(phase==0 || phase==FINISHED)return;
        if(s.contains("Tide") || s.contains("Link")){
            // Active fights from the original encounter can resume with the same actors and health.
            s.remove("Tide");
            s.remove("Link");
            dirty(l);
        }
        if(ensureCore(l,r,room,s)==null)return;
        var c=DungeonRealm.center(r,room);
        var people=s.getCompound("People");
        if(phase==RETURNING){
            int left=s.getInt("Wait")-1;
            s.putInt("Wait",left);
            if(left<=0)startDuel(l,r,c,s,party);
        }else if(phase==MAGIC || phase==DUEL){
            for(var key:people.getAllKeys()){
                var record=people.getCompound(key);
                var m=reflection(l,r,record);
                if(m!=null && m.isAlive() && l.getGameTime()%20==0)snapshot(m,record);
            }
            if(s.getInt("Grace")>0)s.putInt("Grace",s.getInt("Grace")-1);
        }
        if(l.getGameTime()%5==0)bar(r,s,party,l);
        if(l.getGameTime()%20==0)dirty(l);
    }

    private static void startDuel(ServerLevel l,DungeonData.Run r,BlockPos c,CompoundTag s,List<ServerPlayer> party) {
        var people=s.getCompound("People");
        List<EchoDouble> actors=new ArrayList<>();
        for(String id:people.getAllKeys()){
            var record=people.getCompound(id);
            var m=MirrorEntities.DOUBLE.get().create(l);
            if(m==null)return;
            m.load(record.getCompound("Original"));
            m.setUUID(UUID.randomUUID());
            m.enterMelee();
            MirrorArena.liftFromOldFloor(l,r,c,m);
            actors.add(m);
        }
        for(var m:actors){
            if(!l.addFreshEntity(m)){
                actors.forEach(Entity::discard);
                return;
            }
        }
        for(var m:actors){
            var record=people.getCompound(m.ownerId().toString());
            record.putUUID("Entity",m.getUUID());record.remove("Down");snapshot(m,record);
            m.action("rise",14);
        }
        s.putInt("Phase",DUEL);
        s.putInt("Grace",20);
        for(var p:party)if(people.contains(p.getStringUUID())){
            MirrorKit.equip(p,actors.get(0).runId());
            DungeonService.message(p,"镜域静默 · 铁甲、剑与盾已就位");
        }
        signal(l,c,SoundEvents.GLASS_BREAK,0.65f);
        l.sendParticles(ParticleTypes.REVERSE_PORTAL,c.getX()+.5,c.getY()+1,c.getZ()+.5,160,8,.35,8,.08);
        dirty(l);
    }

    public static void fallen(EchoDouble m){
        if(!(m.level() instanceof ServerLevel l) || m.runId()==null || m.ownerId()==null)return;
        var r=DungeonData.get(l.getServer()).runs.get(m.runId());
        if(!current(r))return;
        var s=state(r);int phase=s.getInt("Phase");
        if(phase!=(m.melee()?DUEL:MAGIC))return;
        var people=s.getCompound("People");var record=people.getCompound(m.ownerId().toString());
        if(record.getBoolean("Down") || !record.hasUUID("Entity") || !record.getUUID("Entity").equals(m.getUUID()))return;
        record.putBoolean("Down",true);
        MirrorSpells.clear(m);
        record.remove("Live");
        var c=m.center();
        mercuryReturn(l,m.position().add(0,.3,0),Vec3.atCenterOf(c).add(0,.55,0));
        if(people.getAllKeys().stream().allMatch(k->people.getCompound(k).getBoolean("Down"))){
            if(phase==MAGIC){
                s.putInt("Phase",RETURNING);s.putInt("Wait",46);
            }else{
                s.putInt("Phase",OPEN);s.putLong("Opened",l.getGameTime()+36);
                MirrorMelee.cleanup(l,r.id);
                DungeonService.online(l.getServer(),r).forEach(MirrorKit::restore);
                if(l.getEntity(s.getUUID("Core")) instanceof EchoCore core)core.expose();
                signal(l,c,SoundEvents.AMETHYST_CLUSTER_BREAK,.55f);
            }
        }
        dirty(l);
    }

    public static boolean strike(EchoCore core,DamageSource source){
        if(!(core.level() instanceof ServerLevel l) || !(source.getEntity() instanceof ServerPlayer p) || core.runId()==null)return false;
        var r=DungeonData.get(l.getServer()).runs.get(core.runId());
        if(!current(r))return false;
        var s=state(r);long age=l.getGameTime()-s.getLong("Opened");
        if(s.getInt("Phase")!=OPEN || age<0 || !s.hasUUID("Core") || !s.getUUID("Core").equals(core.getUUID())
                || !s.getCompound("People").contains(p.getStringUUID()) || p.level()!=l || DownedCombat.isDowned(p) || DungeonService.recovering(p))return false;
        var direct=source.getDirectEntity();
        if(direct!=p && (!(direct instanceof Projectile shot) || shot.tickCount>age+1))return false;
        s.putInt("Phase",FINISHED);
        var room=guardian(r);var c=DungeonRealm.center(r,room);
        signal(l,c,SoundEvents.GLASS_BREAK,1.7f);
        l.sendParticles(ParticleTypes.END_ROD,c.getX()+.5,c.getY()+2,c.getZ()+.5,70,1,.65,1,.1);
        for(String participant:s.getCompound("People").getAllKeys()){
            var profile=DungeonData.get(l.getServer()).profile(UUID.fromString(participant));
            if(profile.getBoolean("EchoShardEarned"))continue;
            var shard=new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.ECHO_SHARD);
            shard.setHoverName(Component.literal("无相镜片").withStyle(net.minecraft.ChatFormatting.LIGHT_PURPLE));
            var lore=new ListTag();
            lore.add(StringTag.valueOf(Component.Serializer.toJson(Component.literal("你收手以后，它就再也没有动过。"))));
            shard.getOrCreateTagElement("display").put("Lore",lore);
            var mail=profile.getList("LeylineMail",Tag.TAG_COMPOUND);
            mail.add(shard.save(new CompoundTag()));profile.put("LeylineMail",mail);
            profile.putBoolean("EchoShardEarned",true);
        }
        DungeonService.clearRoom(l,r,room);
        removeBar(r.id);
        dirty(l);
        return true;
    }
    private static void signal(ServerLevel l,BlockPos c,net.minecraft.sounds.SoundEvent sound,float pitch){
        l.playSound(null,c,sound,SoundSource.HOSTILE,1.4f,pitch);
    }
    private static void dirty(ServerLevel l){DungeonData.get(l.getServer()).setDirty();}

    private static void mercuryReturn(ServerLevel l,Vec3 a,Vec3 b){
        int n=Math.max(2,(int)(a.distanceTo(b)*1.8));
        for(int i=0;i<=n;i++){
            var p=a.lerp(b,i/(double)n);
            l.sendParticles(ParticleTypes.GLOW,p.x,p.y,p.z,1,.02,.02,.02,0);
        }
    }
    private static void bar(DungeonData.Run r,CompoundTag s,List<ServerPlayer> party,ServerLevel l){
        var b=BARS.computeIfAbsent(r.id,id->new ServerBossEvent(Component.literal("虚镜魔核"),BossEvent.BossBarColor.PURPLE,BossEvent.BossBarOverlay.PROGRESS));
        var people=s.getCompound("People");int phase=s.getInt("Phase");
        double sum=0;
        for(String id:people.getAllKeys()){
            var row=people.getCompound(id);
            if(!row.getBoolean("Down") && row.hasUUID("Entity") && l.getEntity(row.getUUID("Entity")) instanceof EchoDouble m)
                sum+=m.getHealth()/m.getMaxHealth();
        }
        b.setName(Component.literal(switch(phase){case MAGIC->"虚镜魔核 · 魔力镜像";case RETURNING->"虚镜魔核 · 水银回流";case DUEL->"虚镜魔核 · 静默决斗";default->"虚镜魔核 · 裸露的镜心";}));
        b.setProgress(phase==OPEN?1f:(float)Math.max(0,Math.min(1,sum/Math.max(1,people.size()))));
        for(var old:List.copyOf(b.getPlayers()))if(!party.contains(old))b.removePlayer(old);
        for(var p:party)if(people.contains(p.getStringUUID()))b.addPlayer(p);
    }
    public static void cleanup(MinecraftServer server,DungeonData.Run r){
        if(!current(r))return;
        var l=server.getLevel(DungeonRealm.KEY);if(l==null)return;
        var s=state(r);
        if(s.hasUUID("Core") && l.getEntity(s.getUUID("Core")) instanceof EchoCore core)core.discard();
        for(String k:s.getCompound("People").getAllKeys()){
            var rec=s.getCompound("People").getCompound(k);
            if(rec.hasUUID("Entity") && l.getEntity(rec.getUUID("Entity")) instanceof EchoDouble m)m.discard();
        }
        DungeonService.online(server,r).forEach(MirrorKit::restore);
        MirrorMelee.cleanup(l,r.id);
        removeBar(r.id);
    }
    private static void removeBar(UUID run){var b=BARS.remove(run);if(b!=null)b.removeAllPlayers();}
    public static void stop(){BARS.values().forEach(ServerBossEvent::removeAllPlayers);BARS.clear();}
}
