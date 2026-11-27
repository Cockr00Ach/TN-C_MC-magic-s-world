package com.tnc.tnc.sanctum;

import com.tnc.tnc.combat.DownedCombat;
import com.tnc.tnc.warrior.WarriorComboService;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.BossEvent;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static com.tnc.tnc.sanctum.FalseGodRules.Move;

public final class FalseGodFight {
    static final DustParticleOptions GOLD = dust(1f,.72f,.19f,1.35f), BLOOD = dust(.92f,.035f,.065f,1.55f),
            EMBER = dust(.91f,.10f,.32f,1.45f), PALE = dust(.98f,.84f,.76f,1.35f);
    private static final String TAG = "tnc_false_god";
    private static final String[] NAMES = {"天使","天使（？）","厄瑞玻斯·萨拉托斯·维尔米翁"};
    private static final String[] MODELS = {"angel","bleeding","demon"};
    final ServerLevel level;
    final Vec3 center;
    final BlockPos centerBlock;
    final ServerBossEvent bar = new ServerBossEvent(Component.literal(NAMES[0]), BossEvent.BossBarColor.YELLOW,BossEvent.BossBarOverlay.PROGRESS);
    final FalseGodFightMoves skills;
    final List<Vec3> pools = new ArrayList<>();
    private final Map<UUID,Integer> frenzy = new HashMap<>();
    private final Map<UUID,Integer> gazeBleeding = new HashMap<>();
    private static final net.minecraft.resources.ResourceKey<net.minecraft.world.damagesource.DamageType> FAST_CUT =
            net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.DAMAGE_TYPE,
                    net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("tnc","sanctum_fast_cut"));
    private final Set<UUID> shieldRaised = new HashSet<>();
    private final Map<UUID,Integer> shieldStart = new HashMap<>();
    IronGolem body;
    FalseGodWound wound;
    final FalseGodHitboxes contacts=new FalseGodHitboxes(this);
    private static final class Body extends IronGolem implements net.minecraft.world.entity.monster.Enemy {
        Body(ServerLevel level){super(EntityType.IRON_GOLEM,level);}
    }
    private Vec3 pinnedWound;
    private int woundUntil;
    Vec3 feet;
    List<ServerPlayer> players = List.of();
    ServerPlayer target;
    Move move;
    int phase=1, age, clock, cycle, recovery, transformation;
    private int partySize, participantCursor, floorPattern, enragePhase;
    private float max, lastHp;
    private boolean won, closed, frenzyQueued, openingFrenzy;
    private boolean roomEmpty;

    public FalseGodFight(ServerLevel level, BlockPos center) {
        this.level=level; this.centerBlock=center.immutable(); this.center=Vec3.atBottomCenterOf(center);
        this.feet=this.center;
        for (var old:level.getEntitiesOfClass(IronGolem.class,new AABB(center).inflate(65), e->e.getTags().contains(TAG))) old.discard();
        this.skills = new FalseGodFightMoves(this);
        max=lastHp=FalseGodRules.health(1,1);
        spawn();
        recovery=45;
    }
    private void spawn() {
        body=new Body(level);
        body.addTag(TAG); body.setPersistenceRequired(); body.setSilent(true); body.setNoAi(true);
        body.setNoGravity(true); body.setCustomName(Component.literal(NAMES[phase-1]));
        body.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(1);
        body.getAttribute(Attributes.ARMOR).setBaseValue(0);
        body.getAttribute(Attributes.MAX_HEALTH).setBaseValue(max); body.setHealth(lastHp);
        body.moveTo(feet.x,feet.y,feet.z,180,0);
        SanctumAppearance.mark(body,phase==2 && enragePhase==2?"bleeding_feral":MODELS[phase-1]); level.addFreshEntity(body);
    }
    public boolean owns(UUID id) { return !closed && body!=null && body.getUUID().equals(id); }
    public boolean defeated() { return won; }
    public void close() {
        closed=true;
        contacts.clear();
        if(body!=null)SanctumFloorMarks.clear(players,body.getUUID());
        bar.removeAllPlayers();
        if(body!=null)body.discard();
        dropWound();
        skills.clear(); pools.clear(); frenzy.clear(); gazeBleeding.clear(); shieldStart.clear(); shieldRaised.clear();
    }
    public void hurt(LivingHurtEvent e) {
        if(!owns(e.getEntity().getUUID())) return;
        var source=e.getSource().getEntity();
        if(source instanceof OwnableEntity pet && pet.getOwner()!=null) source=pet.getOwner();
        if(!(source instanceof ServerPlayer p) || !players.contains(p) || transformation>0 || won) {
            e.setCanceled(true);
            return;
        }
        e.setAmount((float)(e.getAmount()*FalseGodRules.incoming(phase,skills.exhausted)));
    }
    /** Forge fires this after armour, resistance and absorption. A raid burst cannot skip the reveal. */
    public void damaged(LivingDamageEvent e) {
        if(!owns(e.getEntity().getUUID()) || won || e.getAmount()<=0)return;
        float health=body.getHealth();
        if(queueFrenzyAt(health-e.getAmount())) {
            e.setAmount(Math.max(0,health-max*.30f));
            return;
        }
        if(e.getAmount()<health)return;
        if(frenzyQueued || openingFrenzy) {
            e.setAmount(Math.max(0,health-1));
            return;
        }
        e.setCanceled(true);
        if(phase==3) {
            won=true; bar.setProgress(0);
            SanctumAppearance.bar(bar,body,3,0,max);
            sound(SoundEvents.WITHER_DEATH,.8f,.58f);
        } else switchPhase();
    }
    private boolean queueFrenzyAt(float nextHealth) {
        if(phase<2 || enragePhase==phase || frenzyQueued || nextHealth>max*.30f)return false;
        frenzyQueued=true;
        return true;
    }
    boolean rehearseFrenzy() {
        if(closed || won || transformation>0 || players.isEmpty() || !queueFrenzyAt(max*.30f))return false;
        lastHp=Math.min(body.getHealth(),max*.30f);
        body.setHealth(lastHp);
        bar.setProgress(lastHp/max);
        SanctumAppearance.bar(bar,body,phase,lastHp,max);
        return true;
    }
    boolean advanceRehearsal() {
        if(closed || won || phase>=3 || transformation!=0 || players.isEmpty())return false;
        switchPhase();
        return true;
    }
    private void switchPhase() {
        contacts.clear();
        SanctumFloorMarks.clear(players,body.getUUID());
        dropWound();
        phase++; cycle=0; move=null; skills.clear(); pools.clear(); frenzy.clear();gazeBleeding.clear();
        frenzyQueued=false;openingFrenzy=false;
        transformation=phase==2?SanctumTimings.AWAKEN:SanctumTimings.RUPTURE; recovery=22;
        place(center); face(center.add(0,0,-1));
        max=lastHp=FalseGodRules.health(phase,Math.max(1,partySize));
        body.getAttribute(Attributes.MAX_HEALTH).setBaseValue(max); body.setHealth(max);
        body.removeAllEffects(); body.setCustomName(Component.literal(NAMES[phase-1]));
        var title = Component.literal(NAMES[phase-1]);
        if (phase==3) title.withStyle(style -> style.withBold(true).withColor(SanctumAppearance.TRUE_NAME_RED));
        bar.setName(title);
        bar.setColor(phase==2?BossEvent.BossBarColor.RED:BossEvent.BossBarColor.PURPLE);
        bar.setProgress(1);
        SanctumAppearance.markAndAct(body,MODELS[phase-1],phase==2?"awaken":"rupture",transformation);
        SanctumAppearance.bar(bar,body,phase,max,max);
        message(phase==2?"它睁开眼了。":"石壳里的第二只手，抓住了裂口。");
        sound(phase==2?SoundEvents.WARDEN_HEARTBEAT:SoundEvents.WARDEN_ROAR,1,.55f);
    }
    public void tick(List<ServerPlayer> standing) {
        advanceFight(standing);
        if(!closed && !won && !players.isEmpty() && transformation==0)contacts.update();
        else contacts.clear();
    }
    private void advanceFight(List<ServerPlayer> standing) {
        if(closed||won) return;
        players=standing.stream().filter(p->inHall(p.position(),centerBlock)).toList();
        for(var p:List.copyOf(bar.getPlayers())) if(!players.contains(p))bar.removePlayer(p);
        if(players.isEmpty()) {
            roomEmpty=true;
            gazeBleeding.clear();
            if(transformation>0)transformation--;
            return;
        }
        if(!body.isAlive() || body.isRemoved()){body.discard();spawn();}
        for(var p:players) if(p.connection!=null&&!bar.getPlayers().contains(p))bar.addPlayer(p);
        if(partySize==0) {
            partySize=players.size();max=lastHp=FalseGodRules.health(phase,partySize);
            body.getAttribute(Attributes.MAX_HEALTH).setBaseValue(max);body.setHealth(max);
            message("天使垂下双手　·　脚下的金色裂纹可以击中。");
        }
        clock++;
        recordShields();
        // All locomotion, including temporary altitude, belongs to the encounter.
        place(feet); body.clearFire(); lastHp=body.getHealth();
        bar.setProgress(net.minecraft.util.Mth.clamp(lastHp/max,0,1));
        if(clock%5==0)SanctumAppearance.bar(bar,body,phase,lastHp,max);
        if(roomEmpty) {
            roomEmpty=false;
            if(transformation==0) {
                // Re-entering the room starts a fresh warning, never an expired hit or cutscene.
                SanctumFloorMarks.clear(players,body.getUUID());
                skills.clear();move=null;
                place(phase<=2?center:actorFloor(feet));recovery=32;
                if(openingFrenzy){openingFrenzy=false;frenzyQueued=true;enragePhase=0;}
            }
        }
        if(transformation>0) {
            // Preserve the shell's position until it has split. The editorial blackout hides a
            // single backstep when someone finished phase two standing directly beneath the torso.
            if(phase==3 && transformation==SanctumTimings.RUPTURE-(SanctumTimings.SHELL_SPLIT+5)) {
                var observer=nearest();
                if(flat(feet,observer.position())<SanctumTimings.DEMON_REACH-2) {
                    Vec3 aim=floor(observer.position());
                    Vec3 best=stance(aim,feet);
                    double space=nearestDistance(best);
                    for(int i=0;i<8;i++) {
                        double angle=i*Math.PI/4;
                        Vec3 offset=new Vec3(Math.cos(angle),0,Math.sin(angle)).scale(SanctumTimings.DEMON_REACH);
                        Vec3 point=actorFloor(aim.add(offset));
                        double distance=nearestDistance(point);
                        if(distance>space+.01){best=point;space=distance;}
                    }
                    place(best);face(aim);
                }
            }
            transformation--; return;
        }
        updateWound();
        if(clock%9==0) ring(feet,.97,14,phase==1?GOLD:phase==2?BLOOD:EMBER);
        if(phase==2){updatePools();tickBleeding();}
        boolean enraged=phase>=2 && (frenzyQueued || body.getHealth()<=max*.30f);
        boolean landingFirst=phase==3 && (move==Move.WINGCUT && age>=SanctumTimings.WING_LOCK && age<SanctumTimings.WING_LAND
                || move==Move.FUNERAL && age>0 && age<130);
        if(enraged && enragePhase!=phase && recovery==0)skills.exhausted=false;
        if(enraged && enragePhase!=phase && !skills.exhausted && !landingFirst) {
            enragePhase=phase;
            frenzyQueued=false;openingFrenzy=true;
            skills.resetMove(); place(floor(feet));
            if(phase==2){place(center);SanctumAppearance.mark(body,"bleeding_feral");}
            begin(phase==2?Move.COMBO:Move.DANCE);
            return;
        } else if(move==null) {
            if(recovery>0) {
                recovery--;
                if(phase==3&&!skills.exhausted&&recovery>0) advance(nearest(),.33);
                return;
            }
            skills.exhausted=false;
            begin(FalseGodRules.choose(phase,cycle++,enragePhase==phase));
            return;
        }
        if(move==null)return;
        if(!players.contains(target))target=nearest();
        int t=++age;
        skills.run(move,t);
        if(move!=null && t>=move.duration) finish(phase==1?28:phase==2?22:17);
    }
    void begin(Move next) {
        move=next; age=0; target=players.get(participantCursor++%players.size());
        skills.begin(next); message(next.cue);
        SanctumAppearance.act(body,skills.action(next),next.duration);
        sound(switch(next) {
            case PILLARS,PRAYER,WINGS -> SoundEvents.AMETHYST_BLOCK_CHIME;
            case GAZE -> SoundEvents.ELDER_GUARDIAN_CURSE;
            case TEARS -> SoundEvents.WITCH_CELEBRATE;
            case SCREAM -> SoundEvents.WARDEN_SONIC_CHARGE;
            case COMBO,DANCE,WINGCUT -> SoundEvents.PHANTOM_SWOOP;
            case HOOK -> SoundEvents.WARDEN_ATTACK_IMPACT;
            case FIRE -> SoundEvents.BLAZE_SHOOT;
            case GRAB -> SoundEvents.RAVAGER_ROAR;
            case FUNERAL -> SoundEvents.WITHER_SPAWN;
        },.85f,phase==1?.75f:.65f);
    }
    void finish(int ticks) { move=null; recovery=ticks; skills.resetMove(); }
    void frenzyImpact() {openingFrenzy=false;}
    void stagger(int ticks) {
        place(floor(feet)); finish(ticks);skills.exhausted=true;
        SanctumAppearance.act(body,"stagger",ticks);
        message("伪神力竭　·　攻击地面的裂痕！");
        sound(SoundEvents.ANVIL_LAND,.65f,.65f);
    }
    boolean counter(ServerPlayer p) {
        return counter(p,feet);
    }
    boolean counter(ServerPlayer p,Vec3 impact) {
        Vec3 delta=impact.subtract(p.position());
        // A vertical impact directly overhead has no horizontal facing to test.
        boolean facing=delta.horizontalDistanceSqr()<.16 ||
                p.getLookAngle().multiply(1,0,1).normalize().dot(delta.multiply(1,0,1).normalize())>.25;
        int shield=shieldStart.getOrDefault(p.getUUID(),-1000);
        if(facing && p.isBlocking() && clock-shield<=8) {
            shieldStart.remove(p.getUUID()); return true;
        }
        return WarriorComboService.consumePrecisionCounter(p,facing);
    }
    private void recordShields() {
        Set<UUID> present=new HashSet<>();
        for(var p:players) {
            UUID id=p.getUUID();present.add(id);
            if(p.isUsingItem() && p.getUseItem().getUseAnimation()==net.minecraft.world.item.UseAnim.BLOCK) {
                if(shieldRaised.add(id))shieldStart.put(id,clock);
            } else {shieldRaised.remove(id);shieldStart.remove(id);}
        }
        shieldRaised.retainAll(present); shieldStart.keySet().retainAll(present);
    }
    private void updatePools() {
        if(clock%8==0)for(var at:pools)ring(at,FalseGodRules.POOL_RADIUS,15,BLOOD);
        if(clock%20==0)for(int i=0;i<pools.size();i++)
            mark("pool_"+i,pools.get(i),FalseGodRules.POOL_RADIUS,0,0x8c071e,23);
        if(clock%10!=0)return;
        for(var p:players) {
            int blood=frenzy.getOrDefault(p.getUUID(),0);
            boolean stained=p.getY()-center.y<.65 && pools.stream().anyMatch(a->flat(a,p.position())<=FalseGodRules.POOL_RADIUS);
            int next=Math.max(0,Math.min(10,blood+(stained?2:-1)));
            if((stained || blood>0) && (move==null || age>35))p.displayClientMessage(Component.literal("狂乱　"+"▰".repeat(next)+"▱".repeat(10-next)),true);
            if(next>0)frenzy.put(p.getUUID(),next);else frenzy.remove(p.getUUID());
            if(stained && next>=6)hit(p,next==10?4:2,false);
        }
    }
    int pattern() { return floorPattern++%3; }
    double nearestDistance(Vec3 point) {
        return players.stream().mapToDouble(p->flat(point,p.position())).min().orElse(Double.POSITIVE_INFINITY);
    }
    static boolean inHall(Vec3 p,BlockPos center) {
        return Math.abs(p.x-center.getX()-.5)<=72 && Math.abs(p.z-center.getZ()-.5)<=72
                && p.y>=center.getY()-4 && p.y<=center.getY()+94;
    }
    private void dropWound() {
        if(wound!=null)wound.discard();
        wound=null;pinnedWound=null;woundUntil=0;
    }
    void expose(Vec3 tile,int ticks) {
        pinnedWound=floor(tile);woundUntil=clock+ticks;
        updateWound();
    }
    private void updateWound() {
        double yaw=Math.toRadians(body.getYRot());
        double reach=phase==1?11:phase==2?SanctumTimings.SEATED_REACH:SanctumTimings.DEMON_REST_CLAW;
        Vec3 at=pinnedWound!=null && clock<woundUntil ? pinnedWound
                : floor(feet.add(-Math.sin(yaw)*reach,0,Math.cos(yaw)*reach));
        if(wound==null || wound.isRemoved()) {
            wound=new FalseGodWound(this,at,body.getYRot());
            level.addFreshEntity(wound);
        }
        wound.setPos(at.x,at.y,at.z);wound.setDeltaMovement(Vec3.ZERO);
        wound.setYRot(body.getYRot());wound.setYBodyRot(body.getYRot());
        if(clock%18==0)ring(at,1.05,16,phase==1?GOLD:phase==2?BLOOD:EMBER);
    }
    ServerPlayer nearest() { return players.stream().min(java.util.Comparator.comparingDouble(p->flat(feet,p.position()))).orElseThrow(); }
    void advance(ServerPlayer p,double stride) {
        Vec3 aim=floor(p.position());
        // Keep the current flank while it has clearance; scan alternatives only when the wall or party blocks it.
        Vec3 goal=stance(aim,feet),diff=goal.subtract(feet);
        if(diff.lengthSqr()>.09)place(feet.add(diff.normalize().scale(Math.min(stride,diff.length()))));
        face(aim);
    }
    Vec3 floor(Vec3 p) {
        return new Vec3(net.minecraft.util.Mth.clamp(p.x,center.x-71,center.x+71),center.y,
                net.minecraft.util.Mth.clamp(p.z,center.z-71,center.z+71));
    }
    Vec3 clawReach(Vec3 target,double length) {
        Vec3 ground=floor(feet),delta=floor(target).subtract(ground);
        return delta.length()<=length?ground.add(delta):ground.add(delta.normalize().scale(length));
    }
    Vec3 stance(Vec3 aim,Vec3 approach) {
        return stance(aim,approach,0);
    }
    Vec3 stance(Vec3 aim,Vec3 approach,double halfTravel) {
        Vec3 offset=approach.subtract(aim).multiply(1,0,1);
        if(offset.lengthSqr()<.01)offset=center.subtract(aim).multiply(1,0,1);
        if(offset.lengthSqr()<.01)offset=new Vec3(0,0,1);
        double preferred=Math.atan2(offset.z,offset.x);
        double bestCost=Double.POSITIVE_INFINITY;
        Vec3 best=actorFloor(aim);
        for(int i=0;i<32;i++) {
            int step=(i+1)/2*(i%2==0?-1:1);
            double angle=preferred+step*Math.PI/16;
            Vec3 radial=new Vec3(Math.cos(angle),0,Math.sin(angle));
            Vec3 raw=aim.add(radial.scale(SanctumTimings.DEMON_REACH)), point=actorFloor(raw);
            Vec3 side=new Vec3(-radial.z,0,radial.x).scale(halfTravel);
            Vec3 a=raw.subtract(side),b=raw.add(side);
            double wall=flat(a,actorFloor(a))+flat(b,actorFloor(b));
            double clearance=24;
            for(var p:players)clearance=Math.min(clearance,FalseGodRules.segment(p.getX(),p.getZ(),a.x,a.z,b.x,b.z));
            double cost=wall*5+(24-clearance)*2+Math.abs(step)*.09;
            if(cost<bestCost){best=point;bestCost=cost;}
            if(cost<.001)break;
        }
        return best;
    }
    Vec3 actorFloor(Vec3 p) {
        // Room piers start at X=61. Keep the body inside the open nave, including beyond the old circular limit.
        return new Vec3(net.minecraft.util.Mth.clamp(p.x,center.x-57,center.x+57),center.y,
                net.minecraft.util.Mth.clamp(p.z,center.z-65,center.z+65));
    }
    void place(Vec3 p) {
        feet=p; body.setPos(p.x,p.y,p.z);body.setDeltaMovement(Vec3.ZERO);body.fallDistance=0;
    }
    void face(Vec3 point) {
        face(point,180);
    }
    void face(Vec3 point,float maxTurn) {
        if(flat(feet,point)<.01)return;
        float desired=(float)(Math.atan2(-(point.x-feet.x),point.z-feet.z)*180/Math.PI);
        float yaw=net.minecraft.util.Mth.approachDegrees(body.getYRot(),desired,maxTurn);
        body.setYRot(yaw); body.setYHeadRot(yaw);body.setYBodyRot(yaw);
    }
    static double flat(Vec3 a,Vec3 b){ return Math.hypot(a.x-b.x,a.z-b.z); }
    boolean hit(ServerPlayer p,float damage,boolean physical) {
        if(!p.isAlive()||DownedCombat.isDowned(p)||p.isSpectator())return false;
        return p.hurt(physical?level.damageSources().mobAttack(body):level.damageSources().magic(),damage);
    }
    void lockedCut(ServerPlayer p) {
        if(!p.isAlive() || p.isSpectator() || p.isCreative() || DownedCombat.isDowned(p))return;
        var types=level.registryAccess().registryOrThrow(net.minecraft.core.registries.Registries.DAMAGE_TYPE);
        // Ignore only hit/roll cooldown. Normal facing shield, resistance, armour and death/revival rules still apply.
        p.hurt(new net.minecraft.world.damagesource.DamageSource(types.getHolderOrThrow(FAST_CUT),body),4);
    }
    void failedGaze(ServerPlayer p) {
        if(!p.isAlive() || p.isSpectator() || DownedCombat.isDowned(p))return;
        boolean faceToFace=directEyeContact(p);
        if(faceToFace)SanctumGaze.send(p,body);
        if(p.isCreative())return;
        hit(p,8,false);
        if(!p.isAlive() || DownedCombat.isDowned(p))return;
        p.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.MOVEMENT_SLOWDOWN,52,5));
        if(!faceToFace)p.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.DARKNESS,64,0));
        p.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.WEAKNESS,56,1));
        gazeBleeding.put(p.getUUID(),clock);
    }
    boolean directEyeContact(ServerPlayer p) {
        double yaw=Math.toRadians(body.getYRot());
        var front=new Vec3(-Math.sin(yaw),0,Math.cos(yaw));
        var eyes=feet.add(0,52,0).add(front.scale(4));
        var sight=eyes.subtract(p.getEyePosition());
        var horizontal=p.position().subtract(feet).multiply(1,0,1).normalize();
        if(front.dot(horizontal)<=.12 || p.getLookAngle().dot(sight.normalize())<Math.cos(Math.toRadians(15)))return false;
        return level.clip(new net.minecraft.world.level.ClipContext(p.getEyePosition(),eyes,
                net.minecraft.world.level.ClipContext.Block.COLLIDER,net.minecraft.world.level.ClipContext.Fluid.NONE,p))
                .getType()==net.minecraft.world.phys.HitResult.Type.MISS;
    }
    private void tickBleeding() {
        for(var it=gazeBleeding.entrySet().iterator();it.hasNext();) {
            var effect=it.next();
            var p=players.stream().filter(player->player.getUUID().equals(effect.getKey())).findFirst().orElse(null);
            if(p==null || !p.isAlive() || p.isCreative() || DownedCombat.isDowned(p)){it.remove();continue;}
            int elapsed=clock-effect.getValue();
            if(elapsed>0 && elapsed%SanctumTimings.BLEED_INTERVAL==0) {
                hit(p,1.5f,false);
                level.sendParticles(BLOOD,p.getX(),p.getY()+.8,p.getZ(),5,.2,.25,.2,0);
            }
            if(elapsed>=SanctumTimings.BLEED_PULSES*SanctumTimings.BLEED_INTERVAL)it.remove();
        }
    }
    void knock(ServerPlayer p,Vec3 origin,double amount) {
        Vec3 direction=p.position().subtract(origin).multiply(1,0,1).normalize();
        p.push(direction.x*amount,.18,direction.z*amount);p.hurtMarked=true;
    }
    void message(String text) {for(var p:players)p.displayClientMessage(Component.literal(text),true);}
    void sound(SoundEvent event,float volume,float pitch) {
        for(var p:players)p.playNotifySound(event,SoundSource.HOSTILE,volume,pitch);
    }
    static DustParticleOptions dust(float r,float g,float b,float scale) {return new DustParticleOptions(new Vector3f(r,g,b),scale);}
    void mark(String key,Vec3 where,double outer,double inner,int rgb,int duration) {
        SanctumFloorMarks.mark(players,body.getUUID(),key,where,outer,inner,rgb,duration,0);
    }
    void waveMark(String key,Vec3 where,double speed,double reach,int rgb) {
        SanctumFloorMarks.mark(players,body.getUUID(),key,where,reach,reach-.45,rgb,(int)Math.ceil(reach/speed)+1,speed);
    }
    void dot(Vec3 p,DustParticleOptions color) {
        level.sendParticles(color,p.x,p.y+.13,p.z,1,0,0,0,0);
    }
    void ring(Vec3 at,double radius,int count,DustParticleOptions color) {
        for(int i=0;i<count;i++){
            double a=Math.PI*2*i/count;dot(at.add(radius*Math.cos(a),0,radius*Math.sin(a)),color);
        }
    }
    void line(Vec3 a,Vec3 b,double spacing,DustParticleOptions color) {
        int n=Math.max(1,(int)Math.ceil(a.distanceTo(b)/spacing));
        for(int i=0;i<=n;i++)dot(a.lerp(b,(double)i/n),color);
    }
    void burst(Vec3 p,boolean red) {
        level.sendParticles(red?ParticleTypes.CRIMSON_SPORE:ParticleTypes.END_ROD,p.x,p.y+1,p.z,15,.8,1,.8,.025);
    }
}
