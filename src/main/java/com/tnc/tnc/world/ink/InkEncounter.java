package com.tnc.tnc.world.ink;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.*;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import java.util.UUID;

/** One persistent three-form encounter per ink dimension. Ordinary test summons remain independent. */
@Mod.EventBusSubscriber(modid="tnc")
public final class InkEncounter extends SavedData {
    private static final String TAG="TncInkEncounter";
    private static final TicketType<String> TICKET=TicketType.create("tnc_ink_encounter",String::compareTo,40);
    private UUID boss;
    private Vec3 anchor=Vec3.ZERO,lastBossPosition=Vec3.ZERO;
    private int phase, intro, pending, deserted, challengers=1;
    private ChunkPos ticket;
    public int phase(){return phase;}
    public int introTicks(){return intro;}
    public UUID bossId(){return boss;}
    public boolean musicContains(Vec3 player){return player.distanceToSqr(anchor)<256*256;}
    public int musicPhase(){return pending>0?Math.max(0,phase-1):phase;}
    public static InkEncounter get(ServerLevel level){return level.getDataStorage().computeIfAbsent(InkEncounter::load,InkEncounter::new,"tnc_ink_encounter");}
    private static InkEncounter load(CompoundTag n){
        var e=new InkEncounter();e.phase=n.getInt("phase");e.intro=n.getInt("intro");e.pending=n.getInt("pending");
        e.challengers=Math.max(1,n.getInt("challengers"));
        if(n.hasUUID("boss"))e.boss=n.getUUID("boss");
        e.anchor=new Vec3(n.getDouble("x"),n.getDouble("y"),n.getDouble("z"));
        e.lastBossPosition=n.contains("bossX")?new Vec3(n.getDouble("bossX"),n.getDouble("bossY"),n.getDouble("bossZ")):e.anchor;return e;
    }
    @Override public CompoundTag save(CompoundTag n){
        n.putInt("phase",phase);n.putInt("intro",intro);n.putInt("pending",pending);n.putInt("challengers",challengers);if(boss!=null)n.putUUID("boss",boss);
        n.putDouble("x",anchor.x);n.putDouble("y",anchor.y);n.putDouble("z",anchor.z);
        n.putDouble("bossX",lastBossPosition.x);n.putDouble("bossY",lastBossPosition.y);n.putDouble("bossZ",lastBossPosition.z);return n;
    }
    public static boolean start(ServerPlayer player){
        var level=player.serverLevel();if(!level.dimension().equals(InkRealmTravel.INK_REALM))return false;
        var e=get(level);if(e.phase!=0)return false;
        Vec3 ahead=player.getLookAngle().multiply(1,0,1).normalize().scale(26);
        e.anchor=player.position().add(ahead);
        if(!level.getWorldBorder().isWithinBounds(new net.minecraft.world.phys.AABB(e.anchor,e.anchor).inflate(32)))return false;
        e.lastBossPosition=e.anchor;e.phase=1;e.pending=1;
        e.challengers=(int)Math.max(1,level.players().stream().filter(p->p.isAlive()&&!p.isSpectator()&&p.distanceToSqr(e.anchor)<192*192).count());
        e.setDirty();return true;
    }
    public static boolean introducing(Entity entity){return entity.getPersistentData().getInt("TncInkIntro")>0;}
    private void spawn(ServerLevel level){
        EntityType<? extends Mob> type=phase==1?InkBossRegistration.INK_CRANE_BOSS:phase==2?InkBossRegistration.INK_WINGED_TIGER:InkBossRegistration.INK_CELESTIAL_DRAGON;
        Mob mob=type.create(level);if(mob==null){pending=20;return;}
        int floor=level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,(int)Math.floor(anchor.x),(int)Math.floor(anchor.z));
        mob.moveTo(anchor.x,floor+(phase==3?InkDragonFlightRules.HOVER_CLEARANCE:0),anchor.z,180,0);
        mob.yBodyRot=mob.yBodyRotO=mob.getYRot();mob.yHeadRot=mob.yHeadRotO=mob.getYRot();
        mob.setPersistenceRequired();mob.getPersistentData().putBoolean(TAG,true);
        mob.setCustomName(Component.literal(name(phase)));
        var health=mob.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH);
        if(health!=null){health.setBaseValue(health.getBaseValue()*(1+.6*Math.min(5,challengers-1)));mob.setHealth(mob.getMaxHealth());}
        mob.getPersistentData().putInt("TncInkIntro",80);mob.setNoAi(true);mob.setInvulnerable(true);
        boss=mob.getUUID();
        if(!level.addFreshEntity(mob)){boss=null;pending=20;return;}
        lastBossPosition=mob.position();intro=80;pending=0;
        InkBossAudio.cast(mob,phase);
        level.sendParticles(net.minecraft.core.particles.ParticleTypes.SQUID_INK,mob.getX(),mob.getY()+3,mob.getZ(),36,3,2,3,.04);
        for(ServerPlayer p:level.players())if(p.isAlive()&&!p.isSpectator()&&p.distanceToSqr(anchor)<192*192){
            InkEncounterNetwork.send(p,mob,phase);
            InkEncounterNetwork.music(p,phase);
            p.displayClientMessage(Component.literal("第"+phase+"相 · "+name(phase)+" 〔潜行键跳过特写〕"),false);
        }
        if(mob instanceof InkCraneBossEntity crane)crane.triggerAnim("action","ink_roar");
        if(mob instanceof InkWingedTigerEntity tiger)tiger.triggerAnim("action","ink_roar");
        setDirty();
    }
    public static String name(int phase){return switch(phase){case 1->"玄翎墨鹤 · 起笔";case 2->"玄墨翼虎 · 破卷";default->"玄墨天龙 · 归墟";};}
    @SubscribeEvent public static void tick(TickEvent.LevelTickEvent event){
        if(event.phase!=TickEvent.Phase.END||!(event.level instanceof ServerLevel level)||!level.dimension().equals(InkRealmTravel.INK_REALM))return;
        var e=get(level);if(e.phase==0)return;
        boolean present=level.players().stream().anyMatch(p->p.isAlive()&&!p.isSpectator()&&p.distanceToSqr(e.anchor)<256*256);
        if(!present){if(++e.deserted>=600)e.reset(level);return;}e.deserted=0;
        Entity loaded=e.boss==null?null:level.getEntity(e.boss);
        if(loaded!=null&&level.getGameTime()%20==0){e.lastBossPosition=loaded.position();e.setDirty();}
        ChunkPos at=new ChunkPos(net.minecraft.core.BlockPos.containing(e.pending>0?e.anchor:e.lastBossPosition));
        if(e.ticket==null||!e.ticket.equals(at)){
            if(e.ticket!=null)level.getChunkSource().removeRegionTicket(TICKET,e.ticket,4,"encounter");
            e.ticket=at;level.getChunkSource().addRegionTicket(TICKET,e.ticket,4,"encounter");
        }
        if(level.getGameTime()%10==0||e.pending==1)level.getChunkSource().addRegionTicket(TICKET,e.ticket,4,"encounter");
        if(e.pending>0){if(--e.pending==0)e.spawn(level);e.setDirty();return;}
        Entity current=e.boss==null?null:level.getEntity(e.boss);
        // Missing UUID may simply be loading after restart. Never create a duplicate.
        if(current==null)return;
        if(e.intro>0&&current instanceof Mob mob){
            e.intro--;mob.getPersistentData().putInt("TncInkIntro",e.intro);mob.setDeltaMovement(Vec3.ZERO);
            if(e.intro==0){mob.setNoAi(false);mob.setInvulnerable(false);}e.setDirty();
        }
    }
    @SubscribeEvent(priority=net.minecraftforge.eventbus.api.EventPriority.LOWEST) public static void death(LivingDeathEvent event){
        if(!(event.getEntity().level() instanceof ServerLevel level)||!event.getEntity().getPersistentData().getBoolean(TAG))return;
        var e=get(level);if(!event.getEntity().getUUID().equals(e.boss))return;
        level.sendParticles(net.minecraft.core.particles.ParticleTypes.SQUID_INK,event.getEntity().getX(),event.getEntity().getY()+3,event.getEntity().getZ(),64,4,3,4,.08);
        e.boss=null;e.intro=0;
        if(e.phase<3){e.phase++;e.pending=60;
            for(var p:level.players())if(p.distanceToSqr(e.anchor)<256*256)p.displayClientMessage(Component.literal("墨迹重聚……"+name(e.phase)),false);
        }else{
            for(var p:level.players())if(p.distanceToSqr(e.anchor)<256*256)p.displayClientMessage(Component.literal("三相归墨，画境重开。"),false);
            e.reset(level);
        }e.setDirty();
    }
    @SubscribeEvent public static void protect(LivingAttackEvent event){
        if(!(event.getEntity().level() instanceof ServerLevel level)||!level.dimension().equals(InkRealmTravel.INK_REALM))return;
        var e=get(level);
        if((e.intro>0||e.pending>0)&&event.getEntity().distanceToSqr(e.anchor)<192*192
                &&(event.getEntity() instanceof ServerPlayer||event.getEntity().getPersistentData().getBoolean(TAG)))event.setCanceled(true);
    }
    public void reset(ServerLevel level){
        for(var player:level.players())if(musicContains(player.position()))InkEncounterNetwork.music(player,0);
        if(boss!=null&&level.getEntity(boss) instanceof Mob mob)mob.discard();
        if(ticket!=null)level.getChunkSource().removeRegionTicket(TICKET,ticket,4,"encounter");
        boss=null;ticket=null;phase=0;intro=0;pending=0;deserted=0;setDirty();
    }
    @SubscribeEvent public static void preserveReturn(net.minecraftforge.event.entity.player.PlayerEvent.Clone event){
        var old=event.getOriginal().getPersistentData();if(old.contains("TncInkReturn"))event.getEntity().getPersistentData().put("TncInkReturn",old.getCompound("TncInkReturn").copy());
    }
    @SubscribeEvent public static void removeAbandoned(net.minecraftforge.event.entity.EntityJoinLevelEvent event){
        if(!(event.getLevel() instanceof ServerLevel level)||!event.getEntity().getPersistentData().getBoolean(TAG))return;
        var state=get(level);
        if(state.phase==0||!event.getEntity().getUUID().equals(state.boss))event.setCanceled(true);
    }
}
