package com.tnc.tnc.world.wind;

import com.tnc.tnc.magic.wind.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.*;
import java.util.*;

/** Server-owned attack windows, collision-aware wind steps and one shared anchor pool. */
final class CanglanWarlordCombat {
    private final CanglanWarlordEntity boss;
    private int move, age, cycle, cooldown=30, stepCooldown=8, dodgeCooldown, recallAge=-1;
    private LivingEntity target;
    private Vec3 travelTo;
    private int travelTicks;
    private final List<CanglanEchoEntity> anchors=new ArrayList<>();
    private final List<Entity> spells=new ArrayList<>();
    CanglanWarlordCombat(CanglanWarlordEntity boss){this.boss=boss;}
    boolean busy(){return move!=0||recallAge>=0;}
    int cooldown(){return cooldown;}
    int cycle(){return cycle;}
    int move(){return move;}
    int age(){return age;}
    int anchorCount(){return anchors.size();}
    boolean beginGaleRoar(){return false;}
    boolean begin(int requested,LivingEntity victim){
        if(busy()||requested==0||victim==null||!victim.isAlive())return false;
        move=requested;target=victim;age=0;boss.setCanglanMove(move);
        boss.getNavigation().stop();boss.triggerAnim("action",CanglanWarGodRules.animation(move));
        boss.level().playSound(null,boss.blockPosition(),SoundEvents.ENDER_DRAGON_FLAP,SoundSource.HOSTILE,2,.8F);
        return true;
    }
    void tick(){
        anchors.removeIf(Entity::isRemoved);spells.removeIf(Entity::isRemoved);
        if(dodgeCooldown>0)--dodgeCooldown;
        if(travelTicks>0&&travelTo!=null){
            Vec3 next=boss.position().lerp(travelTo,1D/travelTicks);
            if(boss.level().noCollision(boss,boss.getBoundingBox().move(next.subtract(boss.position()))))boss.setPos(next.x,next.y,next.z);
            else travelTicks=1;
            boss.setDeltaMovement(Vec3.ZERO);--travelTicks;if(travelTicks==0)boss.setCanglanMove(0);return;
        }
        if(recallAge>=0){tickRecall();return;}
        if(anchors.size()>=10){recallAge=0;tickRecall();return;}
        LivingEntity victim=boss.getTarget();
        if(victim==null||!victim.isAlive()) {if(!anchors.isEmpty())clearAnchors();cancel();return;}
        if(victim.level()!=boss.level()||boss.distanceToSqr(victim)>240*240){cleanup();return;}
        if(!busy()){
            if(--stepCooldown<=0){windStep(victim,false);stepCooldown=10;}
            if(cooldown>0){--cooldown;return;}
            if(boss.hasLineOfSight(victim)&&begin(CanglanWarGodRules.choose(cycle,boss.distanceTo(victim),boss.battleScale()),victim))++cycle;
            return;
        }
        if(target==null||!target.isAlive()){cancel();return;}
        boss.getNavigation().stop();boss.setDeltaMovement(0,boss.getDeltaMovement().y,0);
        if(age<10){
            boss.getLookControl().setLookAt(target,60,30);
            var delta=target.position().subtract(boss.position());
            float yaw=(float)Math.toDegrees(Math.atan2(-delta.x,delta.z));
            boss.setYRot(net.minecraft.util.Mth.rotLerp(.45F,boss.getYRot(),yaw));
            boss.yBodyRot=boss.getYRot();boss.setYHeadRot(boss.getYRot());
        }
        float scale=boss.battleScale();
        if((move==1&&age==10)||(move==5&&(age==12||age==22)))strike(boss.position(),move==5?8*scale:7*scale,move==5);
        if(move==CanglanWarGodRules.TORNADO&&age==26){strike(boss.position(),9*scale,true);gale(boss.position(),false);}
        if(move==CanglanWarGodRules.WAVE&&age==15){
            Vec3 dir=target.position().subtract(boss.position()).multiply(1,0,1).normalize();
            var e=CanglanTempestEntity.spawn(boss,boss.position().add(0,.25,0),dir,false,scale,55);spells.add(e);
        }
        if((move==11||move==12)&&age==32&&spells.stream().filter(e->e instanceof WindVolleyEntity).count()<2){
            var volley=WindChainRegistration.VOLLEY.create(boss.level());
            if(volley!=null){volley.configure(boss,move==11?4:5,target);volley.setBossScale(scale);boss.level().addFreshEntity(volley);spells.add(volley);}
        }
        if(++age>=CanglanWarGodRules.duration(move)){boolean arsenal=move>=11;cancel();cooldown=arsenal?35:18;}
    }
    boolean dodge(net.minecraft.world.damagesource.DamageSource source){
        if(dodgeCooldown>0||boss.shifting()||boss.rising()||recallAge>=0||source.getEntity()==boss)return false;
        if(!source.is(net.minecraft.tags.DamageTypeTags.IS_PROJECTILE)&&!source.is(net.minecraft.tags.DamageTypeTags.WITCH_RESISTANT_TO))return false;
        var victim=boss.getTarget();if(victim==null||boss.getRandom().nextFloat()>=CanglanWarGodRules.DODGE_CHANCE)return false;
        if(!windStep(victim,true))return false;
        dodgeCooldown=CanglanWarGodRules.DODGE_COOLDOWN;return true;
    }
    boolean windStep(LivingEntity victim,boolean dodge){
        Vec3 delta=victim.position().subtract(boss.position()).multiply(1,0,1);
        if(delta.lengthSqr()<.01)return false;
        Vec3 dir=delta.normalize();
        if(dodge)dir=new Vec3(-dir.z,0,dir.x).scale(boss.getRandom().nextBoolean()?1:-1);
        else if(delta.length()<8*boss.battleScale())dir=new Vec3(-dir.z,0,dir.x);
        double length=Math.min(dodge?10:15,Math.max(6,delta.length()-4));
        Vec3 from=boss.position(),to=from.add(dir.scale(length));
        var hit=boss.level().clip(new ClipContext(from.add(0,1,0),to.add(0,1,0),ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,boss));
        if(hit.getType()!=HitResult.Type.MISS)to=hit.getLocation().subtract(dir.scale(1.6)).subtract(0,1,0);
        if(from.distanceToSqr(to)<9||!boss.level().hasChunkAt(net.minecraft.core.BlockPos.containing(to)))return false;
        // The full body must fit; never dash into a wall or across an unloaded chunk.
        if(!boss.level().noCollision(boss,boss.getBoundingBox().move(to.subtract(from))))return false;
        cancel();addAnchor(from);
        boss.setCanglanMove(14);boss.triggerAnim("action","wind_step");
        travelTo=to;travelTicks=6;boss.setDeltaMovement(Vec3.ZERO);
        return true;
    }
    void addAnchor(Vec3 at){
        if(anchors.size()>=10)return;
        var echo=CanglanWarlordRegistration.ECHO.create(boss.level());if(echo==null)return;
        echo.configure(boss,at,boss.getYRot(),boss.battleScale());
        if(!anchors.isEmpty())anchors.get(anchors.size()-1).link(at);
        boss.level().addFreshEntity(echo);anchors.add(echo);
    }
    private void tickRecall(){
        if(anchors.isEmpty()){recallAge=-1;return;}
        if(recallAge==0){
            boss.setCanglanMove(5);
            Vec3 at=anchors.get(0).position();
            if(boss.level().noCollision(boss,boss.getBoundingBox().move(at.subtract(boss.position()))))boss.setPos(at.x,at.y,at.z);
            boss.triggerAnim("action","cyclone");
            for(var echo:anchors)echo.activate();
        }
        if(recallAge==12)for(var echo:anchors){strike(echo.position(),6*echo.visualScale(),true);gale(echo.position(),true);}
        if(++recallAge>34){clearAnchors();recallAge=-1;cancel();cooldown=30;}
    }
    void gale(Vec3 at,boolean small){spells.add(CanglanTempestEntity.spawn(boss,at,Vec3.ZERO,true,boss.battleScale()*(small?.45F:1.25F),small?45:95));}
    private void strike(Vec3 at,double reach,boolean circle){
        Vec3 facing=Vec3.directionFromRotation(0,boss.getYRot());
        for(var e:boss.level().getEntitiesOfClass(LivingEntity.class,new AABB(at,at).inflate(reach,8*boss.battleScale(),reach),v->v!=boss&&v.isAlive()&&!v.isSpectator()&&!boss.isAlliedTo(v))){
            Vec3 d=e.position().subtract(at);if(d.horizontalDistance()>reach)continue;
            if(!circle&&d.normalize().dot(facing)<-.1)continue;
            if(!boss.hasLineOfSight(e))continue;
            e.hurt(boss.level().damageSources().mobAttack(boss),(float)boss.getAttributeValue(Attributes.ATTACK_DAMAGE)*(boss.phase()==2?1.25F:1));
            Vec3 push=d.normalize().scale(.5);e.push(push.x,.2,push.z);e.hurtMarked=true;
        }
        ((ServerLevel)boss.level()).sendParticles(net.minecraft.core.particles.ParticleTypes.SWEEP_ATTACK,at.x,at.y+1,at.z,8,reach*.35,.5,reach*.35,.02);
    }
    void cancel(){move=0;age=0;target=null;boss.setCanglanMove(0);cooldown=18;}
    void clearAnchors(){for(var e:anchors)e.discard();anchors.clear();recallAge=-1;}
    void cleanup(){travelTicks=0;travelTo=null;cancel();clearAnchors();for(var e:spells)e.discard();spells.clear();}
}
