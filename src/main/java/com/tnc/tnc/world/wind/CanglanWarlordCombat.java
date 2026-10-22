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
    private LivingEntity target,testTarget,executionTarget;
    private net.minecraft.server.level.ServerPlayer cinematicPlayer;
    private boolean testing,testLoop;
    private int testMove;
    private int consecutiveSteps,footworkStage;
    private boolean meleeEntry;
    private Vec3 thrustDirection=Vec3.ZERO;
    private final Set<Integer> thrustVictims=new HashSet<>();
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
    int consecutiveSteps(){return consecutiveSteps;}
    boolean testing(){return testing;}
    void testAttack(int requested,LivingEntity victim,boolean loop){
        cleanup();testing=true;testLoop=loop;testMove=requested;testTarget=victim;
        boss.setTarget(victim);begin(requested,victim);
    }
    void pauseTest(){cleanup();testing=true;testLoop=false;testMove=0;testTarget=null;boss.getNavigation().stop();}
    void resumeTest(){cleanup();testing=false;testLoop=false;testMove=0;testTarget=null;}
    boolean beginGaleRoar(){return false;}
    boolean canSweep(LivingEntity victim){
        return boss.position().subtract(victim.position()).horizontalDistance()<=CanglanWarGodRules.sweepRange(boss.battleScale())
                &&Math.abs(boss.getY()-victim.getY())<=6*boss.battleScale();
    }
    boolean begin(int requested,LivingEntity victim){
        if(busy()||requested==0||victim==null||!victim.isAlive())return false;
        if(requested==5&&!canSweep(victim))return false;
        meleeEntry=false;move=requested;target=victim;age=0;boss.setCanglanMove(move);
        thrustVictims.clear();
        if(move==5){
            executionTarget=CanglanWarGodRules.executionHealth(victim.getHealth(),victim.getAbsorptionAmount(),victim.getMaxHealth())&&!victim.isInvulnerable()
                    &&(!(victim instanceof net.minecraft.world.entity.player.Player p)||!p.isCreative())?victim:null;
            if(victim instanceof net.minecraft.server.level.ServerPlayer p){cinematicPlayer=p;CanglanCinematicNetwork.send(p,boss,80,executionTarget!=null);}
        }
        if(move==1){
            thrustDirection=victim.position().subtract(boss.position()).multiply(1,0,1).normalize();
            float yaw=(float)Math.toDegrees(Math.atan2(-thrustDirection.x,thrustDirection.z));
            boss.setYRot(yaw);boss.yBodyRot=yaw;boss.setYHeadRot(yaw);
        }
        boss.getNavigation().stop();boss.triggerAnim("action",CanglanWarGodRules.animation(move));
        boss.level().playSound(null,boss.blockPosition(),SoundEvents.ENDER_DRAGON_FLAP,SoundSource.HOSTILE,2,.8F);
        return true;
    }
    void tick(){
        anchors.removeIf(Entity::isRemoved);spells.removeIf(Entity::isRemoved);
        if(dodgeCooldown>0)--dodgeCooldown;
        if(testing){
            if(testMove>0&&(testTarget==null||!testTarget.isAlive()||testTarget.level()!=boss.level())){pauseTest();return;}
            if(testTarget!=null)boss.setTarget(testTarget);
            if(move==0&&recallAge<0){
                boss.getNavigation().stop();boss.setDeltaMovement(0,boss.getDeltaMovement().y,0);
                if(testLoop&&testMove>0&&--cooldown<=0&&!begin(testMove,testTarget))cooldown=10;
                return;
            }
        }
        if(travelTicks>0&&travelTo!=null){
            Vec3 next=boss.position().lerp(travelTo,1D/travelTicks);
            Vec3 before=boss.position();
            boss.move(MoverType.SELF,next.subtract(before));
            if(boss.position().distanceToSqr(next)>.01)travelTicks=1;
            boss.setDeltaMovement(Vec3.ZERO);--travelTicks;if(travelTicks==0)boss.setCanglanMove(0);return;
        }
        if(recallAge>=0){tickRecall();return;}
        if(anchors.size()>=10){recallAge=0;tickRecall();return;}
        LivingEntity victim=boss.getTarget();
        if(victim==null||!victim.isAlive()) {if(!anchors.isEmpty())clearAnchors();cancel();return;}
        if(victim.level()!=boss.level()||boss.distanceToSqr(victim)>320*320){cleanup();return;}
        if(!busy()){
            if(--stepCooldown<=0){boolean stepped=windStep(victim,false);stepCooldown=10;if(stepped)return;}
            if(cooldown>0){--cooldown;return;}
            int next=meleeEntry&&canSweep(victim)?5:CanglanWarGodRules.choose(cycle,boss.position().subtract(victim.position()).horizontalDistance(),boss.battleScale());
            if(next==5&&!canSweep(victim))next=CanglanWarGodRules.WAVE;
            if(boss.hasLineOfSight(victim)&&begin(next,victim))++cycle;
            return;
        }
        if(target==null||!target.isAlive()){cancel();return;}
        boss.getNavigation().stop();boss.setDeltaMovement(0,boss.getDeltaMovement().y,0);
        if(age<10&&move!=1){
            boss.getLookControl().setLookAt(target,60,30);
            var delta=target.position().subtract(boss.position());
            float yaw=(float)Math.toDegrees(Math.atan2(-delta.x,delta.z));
            boss.setYRot(net.minecraft.util.Mth.rotLerp(.45F,boss.getYRot(),yaw));
            boss.yBodyRot=boss.getYRot();boss.setYHeadRot(boss.getYRot());
        }
        float scale=boss.battleScale();
        if(move==1 && age<20) advanceThrust(scale);
        if(move==1 && age==24) strikeThrust(boss.position(),boss.position(),scale);
        if(move==5 && age==66)executeSweep();
        if(move==5 && age==66)strike(boss.position(),8*scale,true);
        if(move==CanglanWarGodRules.TORNADO&&age==26){strike(boss.position(),9*scale,true);gale(boss.position(),false);}
        if(move==CanglanWarGodRules.WAVE&&age==15){
            Vec3 dir=target.position().subtract(boss.position()).multiply(1,0,1).normalize();
            var e=CanglanTempestEntity.spawn(boss,boss.position().add(0,4*scale,0),dir,false,scale,55);spells.add(e);
        }
        if((move==11||move==12)&&age==30&&spells.stream().filter(e->e instanceof WindVolleyEntity).count()<2){
            var volley=WindChainRegistration.VOLLEY.create(boss.level());
            if(volley!=null){volley.configure(boss,move==11?4:5,target);volley.setBossScale(scale);boss.level().addFreshEntity(volley);spells.add(volley);}
        }
        if(++age>=CanglanWarGodRules.duration(move)){consecutiveSteps=0;boolean arsenal=move>=11;cancel();cooldown=arsenal?35:18;}
    }
    private void executeSweep(){
        LivingEntity victim=executionTarget;
        if(victim==null||!victim.isAlive()||!CanglanWarGodRules.executionHealth(victim.getHealth(),victim.getAbsorptionAmount(),victim.getMaxHealth())||victim.isInvulnerable()
                ||!canSweep(victim)||!boss.hasLineOfSight(victim))return;
        if(victim instanceof net.minecraft.world.entity.player.Player p&&(p.isCreative()||p.isSpectator()))return;
        if(victim.hurt(boss.level().damageSources().mobAttack(boss),Float.MAX_VALUE)){
            thrustVictims.add(victim.getId());
            boss.level().playSound(null,victim.blockPosition(),SoundEvents.TRIDENT_THUNDER,SoundSource.HOSTILE,1.8F,.8F);
            ((ServerLevel)boss.level()).sendParticles(net.minecraft.core.particles.ParticleTypes.SWEEP_ATTACK,victim.getX(),victim.getY()+1,victim.getZ(),12,1,1,1,.05);
        }
    }
    private void advanceThrust(float scale){
        Vec3 from=boss.position();
        double distance=(CanglanWarGodRules.thrustDistance(age+1)-CanglanWarGodRules.thrustDistance(age))*scale;
        Vec3 delta=thrustDirection.scale(distance);
        AABB swept=boss.getBoundingBox().expandTowards(delta);
        if(!boss.level().hasChunksAt(net.minecraft.core.BlockPos.containing(swept.minX,swept.minY,swept.minZ),
                net.minecraft.core.BlockPos.containing(swept.maxX,swept.maxY,swept.maxZ)))return;
        boss.move(MoverType.SELF,delta);
        strikeThrust(from,boss.position(),scale);
    }
    private void strikeThrust(Vec3 from,Vec3 to,float scale){
        double reach=3.5*scale;
        Vec3 line=to.subtract(from).multiply(1,0,1);
        for(var e:boss.level().getEntitiesOfClass(LivingEntity.class,new AABB(from,to).inflate(reach,8*scale,reach),
                v->v!=boss&&v.isAlive()&&!v.isSpectator()&&!boss.isAlliedTo(v)&&!thrustVictims.contains(v.getId()))){
            Vec3 d=e.position().subtract(from).multiply(1,0,1);
            double t=line.lengthSqr()<1E-6?0:net.minecraft.util.Mth.clamp(d.dot(line)/line.lengthSqr(),0,1);
            if(d.subtract(line.scale(t)).lengthSqr()>reach*reach||!boss.hasLineOfSight(e))continue;
            if(e.hurt(boss.level().damageSources().mobAttack(boss),(float)boss.getAttributeValue(Attributes.ATTACK_DAMAGE)*(boss.phase()==2?1.25F:1))){
                thrustVictims.add(e.getId());e.push(thrustDirection.x*.5,.15,thrustDirection.z*.5);e.hurtMarked=true;
            }
        }
    }
    boolean dodge(net.minecraft.world.damagesource.DamageSource source){
        if(testing||dodgeCooldown>0||boss.shifting()||boss.rising()||recallAge>=0||source.getEntity()==boss)return false;
        if(!source.is(net.minecraft.tags.DamageTypeTags.IS_PROJECTILE)&&!source.is(net.minecraft.tags.DamageTypeTags.WITCH_RESISTANT_TO))return false;
        var victim=boss.getTarget();if(victim==null||boss.getRandom().nextFloat()>=CanglanWarGodRules.DODGE_CHANCE)return false;
        if(!windStep(victim,true))return false;
        dodgeCooldown=CanglanWarGodRules.DODGE_COOLDOWN;return true;
    }
    boolean windStep(LivingEntity victim,boolean dodge){
        if(consecutiveSteps>=CanglanWarGodRules.MAX_CONSECUTIVE_STEPS)return false;
        if(meleeEntry&&!dodge)return false;
        Vec3 away=boss.position().subtract(victim.position()).multiply(1,0,1);
        boolean close=footworkStage==2&&!dodge;
        double[] offset=CanglanWarGodRules.stepOffset(away.x,away.z,boss.battleScale(),footworkStage,dodge,footworkStage%2==0?1:-1);
        footworkStage=(footworkStage+1)%3;
        Vec3 planned=new Vec3(offset[0],0,offset[1]);
        if(planned.lengthSqr()<9){
            if(close&&canSweep(victim)){meleeEntry=true;cooldown=0;}
            return false;
        }
        Vec3 dir=planned.normalize();double length=planned.length();
        Vec3 from=boss.position(),to=from.add(planned);
        // Stay inside loaded chunks before ray clipping; a long dash must not load terrain.
        while(length>=3){
            AABB area=boss.getBoundingBox().expandTowards(to.subtract(from));
            if(boss.level().hasChunksAt(net.minecraft.core.BlockPos.containing(area.minX,area.minY,area.minZ),
                    net.minecraft.core.BlockPos.containing(area.maxX,area.maxY,area.maxZ)))break;
            length-=2;to=from.add(dir.scale(length));
        }
        if(length<3)return false;
        var hit=boss.level().clip(new ClipContext(from.add(0,1,0),to.add(0,1,0),ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,boss));
        if(hit.getType()!=HitResult.Type.MISS)to=hit.getLocation().subtract(dir.scale(1.6)).subtract(0,1,0);
        if(from.distanceToSqr(to)<9||!boss.level().hasChunkAt(net.minecraft.core.BlockPos.containing(to)))return false;
        // The full body must fit; never dash into a wall or across an unloaded chunk.
        if(!boss.level().noCollision(boss,boss.getBoundingBox().move(to.subtract(from))))return false;
        int attackWait=cooldown;cancel();cooldown=attackWait;
        if(++consecutiveSteps>=CanglanWarGodRules.MAX_CONSECUTIVE_STEPS)cooldown=0;
        addAnchor(from);
        boss.setCanglanMove(14);boss.triggerAnim("action","wind_step");
        meleeEntry=close;if(close)cooldown=0;
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
        if(++recallAge>34){consecutiveSteps=0;clearAnchors();recallAge=-1;cancel();cooldown=30;}
    }
    void gale(Vec3 at,boolean small){spells.add(CanglanTempestEntity.spawn(boss,at,Vec3.ZERO,true,boss.battleScale()*(small?.45F:1.25F),small?45:95));}
    private void strike(Vec3 at,double reach,boolean circle){
        Vec3 facing=Vec3.directionFromRotation(0,boss.getYRot());
        for(var e:boss.level().getEntitiesOfClass(LivingEntity.class,new AABB(at,at).inflate(reach,8*boss.battleScale(),reach),v->v!=boss&&v.isAlive()&&!v.isSpectator()&&!boss.isAlliedTo(v))){
            if(move==5&&thrustVictims.contains(e.getId()))continue;
            Vec3 d=e.position().subtract(at);if(d.horizontalDistance()>reach)continue;
            if(!circle&&d.normalize().dot(facing)<-.1)continue;
            if(!boss.hasLineOfSight(e))continue;
            float damage=(float)boss.getAttributeValue(Attributes.ATTACK_DAMAGE)*(boss.phase()==2?1.25F:1);
            if(move==5)damage=Math.min(damage,e.getMaxHealth()*.45F);
            e.hurt(boss.level().damageSources().mobAttack(boss),damage);
            Vec3 push=d.normalize().scale(.5);e.push(push.x,.2,push.z);e.hurtMarked=true;
        }
        ((ServerLevel)boss.level()).sendParticles(net.minecraft.core.particles.ParticleTypes.SWEEP_ATTACK,at.x,at.y+1,at.z,8,reach*.35,.5,reach*.35,.02);
    }
    void cancel(){if(cinematicPlayer!=null)CanglanCinematicNetwork.send(cinematicPlayer,boss,0,false);cinematicPlayer=null;executionTarget=null;thrustVictims.clear();move=0;age=0;target=null;boss.setCanglanMove(0);cooldown=18;}
    void clearAnchors(){for(var e:anchors)e.discard();anchors.clear();recallAge=-1;}
    void cleanup(){meleeEntry=false;travelTicks=0;travelTo=null;cancel();clearAnchors();for(var e:spells)e.discard();spells.clear();}
}
