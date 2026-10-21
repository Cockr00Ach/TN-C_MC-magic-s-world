package com.tnc.tnc.world.ink;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.*;
import net.minecraft.server.level.*;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.*;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.*;

/** Server-authoritative flight and combat; never changes blocks or force-loads chunks. */
public final class InkCelestialDragonEntity extends PathfinderMob {
    private static final double SCALE=InkDragonCombatRules.BODY_SCALE;
    private static final EntityDataAccessor<Integer> MOVE=SynchedEntityData.defineId(InkCelestialDragonEntity.class,EntityDataSerializers.INT);
    private static final EntityDataAccessor<Long> START=SynchedEntityData.defineId(InkCelestialDragonEntity.class,EntityDataSerializers.LONG);
    private static final EntityDataAccessor<CompoundTag> EFFECT=SynchedEntityData.defineId(InkCelestialDragonEntity.class,EntityDataSerializers.COMPOUND_TAG);
    private static final EntityDataAccessor<Boolean> GROUNDED=SynchedEntityData.defineId(InkCelestialDragonEntity.class,EntityDataSerializers.BOOLEAN);
    private final ServerBossEvent bar=new ServerBossEvent(Component.translatable("entity.tnc.ink_celestial_dragon"),BossEvent.BossBarColor.WHITE,BossEvent.BossBarOverlay.NOTCHED_10);
    private Vec3 home,flightGoal,attackHome,highOrb,landing;
    private LivingEntity victim;
    private int cooldown=100,nextMove=1;
    private boolean groundPlaced;
    public InkCelestialDragonEntity(EntityType<? extends InkCelestialDragonEntity> type,Level level){super(type,level);setNoGravity(true);noPhysics=true;setPersistenceRequired();xpReward=1200;}
    public static AttributeSupplier.Builder createAttributes(){return Mob.createMobAttributes().add(Attributes.MAX_HEALTH,1000).add(Attributes.ARMOR,18).add(Attributes.MOVEMENT_SPEED,.3).add(Attributes.FOLLOW_RANGE,192).add(Attributes.KNOCKBACK_RESISTANCE,1);}
    @Override protected void registerGoals(){}
    @Override protected void defineSynchedData(){super.defineSynchedData();entityData.define(MOVE,0);entityData.define(START,0L);entityData.define(EFFECT,new CompoundTag());entityData.define(GROUNDED,false);}
    public CompoundTag visualSnapshot(){
        var tag=new CompoundTag();tag.putBoolean("ground",isGroundedForObservation());tag.putInt("move",inkMove());
        tag.putInt("age",inkMove()==0?0:moveAge());tag.put("effect",entityData.get(EFFECT).copy());return tag;
    }
    public void applyVisualSnapshot(CompoundTag tag){
        if(!level().isClientSide)return;
        entityData.set(GROUNDED,tag.getBoolean("ground"));entityData.set(MOVE,net.minecraft.util.Mth.clamp(tag.getInt("move"),0,2));
        entityData.set(START,level().getGameTime()-net.minecraft.util.Mth.clamp(tag.getInt("age"),0,200));entityData.set(EFFECT,tag.getCompound("effect").copy());
    }
    public boolean isGroundedForObservation(){return entityData.get(GROUNDED);}
    public void setGroundedForObservation(boolean value){
        if(level().isClientSide)return;
        entityData.set(GROUNDED,value);groundPlaced=false;flightGoal=null;home=null;
        if(value){stopAttack();setTarget(null);placeOnGround();}
    }
    private void placeOnGround(){
        if(groundPlaced||!level().hasChunkAt(blockPosition()))return;
        int floor=level().getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,blockPosition().getX(),blockPosition().getZ());
        if(floor<=level().getMinBuildHeight())return;
        setPos(getX(),floor,getZ());setDeltaMovement(Vec3.ZERO);groundPlaced=true;
    }
    public int inkMove(){return entityData.get(MOVE);}
    public int moveAge(){return (int)Math.max(0,level().getGameTime()-entityData.get(START));}
    public Vec3 effectVector(String key){CompoundTag t=entityData.get(EFFECT).getCompound(key);return new Vec3(t.getDouble("x"),t.getDouble("y"),t.getDouble("z"));}
    public boolean breathHit(){return entityData.get(EFFECT).getBoolean("hit");}
    public float jawOpen(float partial){if(inkMove()!=1)return 0;double age=moveAge()+partial;return (float)Math.max(0,Math.min(1,Math.min((age-16)/16,(120-age)/14)));}
    private static void vector(CompoundTag t,String key,Vec3 v){CompoundTag p=new CompoundTag();p.putDouble("x",v.x);p.putDouble("y",v.y);p.putDouble("z",v.z);t.put(key,p);}
    private void syncEffect(Vec3 point,Vec3 normal,boolean hit){CompoundTag t=new CompoundTag();vector(t,"point",point);vector(t,"normal",normal);t.putBoolean("hit",hit);if(highOrb!=null)vector(t,"high",highOrb);entityData.set(EFFECT,t);}
    public boolean startInkAttack(int move,LivingEntity target){
        if(level().isClientSide||isGroundedForObservation()||inkMove()!=0||!isAlive()||move<1||move>2||!valid(target)||distanceToSqr(target)>288*288)return false;
        victim=target;attackHome=position();highOrb=null;landing=target.position();entityData.set(MOVE,move);entityData.set(START,level().getGameTime());syncEffect(landing,new Vec3(0,1,0),false);playSound(SoundEvents.ENDER_DRAGON_GROWL,2,.65F);return true;
    }
    private boolean valid(LivingEntity e){return e!=null&&e!=this&&e.isAlive()&&e.level()==level();}
    private void stopAttack(){entityData.set(MOVE,0);entityData.set(EFFECT,new CompoundTag());victim=null;highOrb=null;cooldown=80;}
    @Override public void aiStep(){
        super.aiStep();setNoGravity(true);setDeltaMovement(Vec3.ZERO);fallDistance=0;if(level().isClientSide)return;
        bar.setName(getDisplayName());bar.setProgress(getHealth()/getMaxHealth());if(!isAlive()){if(inkMove()!=0)stopAttack();return;}
        if(isGroundedForObservation()){
            if(inkMove()!=0)stopAttack();setTarget(null);placeOnGround();return;
        }
        if(home==null)home=position();
        if(inkMove()!=0){if(!valid(victim)||distanceToSqr(victim)>384*384){stopAttack();return;}attackTick();return;}
        if(isNoAi())return;
        if(tickCount%20==0)setTarget(level().getNearestPlayer(getX(),getY(),getZ(),192,e->e instanceof Player p&&p.isAlive()&&!p.isCreative()&&!p.isSpectator()));
        if(cooldown>0)cooldown--;
        LivingEntity target=getTarget();
        if(valid(target)&&distanceToSqr(target)>135*135){
            int terrain=ground(target.getX(),target.getZ());if(terrain!=Integer.MIN_VALUE){Vec3 offset=position().subtract(target.position()).multiply(1,0,1).normalize().scale(26);
                flyToward(new Vec3(target.getX()+offset.x,Math.min(level().getMaxBuildHeight()-40,terrain+InkDragonFlightRules.HOVER_CLEARANCE),target.getZ()+offset.z),.35*SCALE);}
            return;
        }
        if(valid(target)&&cooldown==0&&hasLineOfSight(target)&&startInkAttack(nextMove,target)){nextMove=nextMove==1?2:1;return;}cruise();
    }
    private void cruise(){
        if(flightGoal==null||tickCount%100==0||position().distanceToSqr(flightGoal)<4){double a=tickCount*.004+getId(),x=home.x+Math.cos(a)*20,z=home.z+Math.sin(a)*20;int ground=ground(x,z);
            if(ground!=Integer.MIN_VALUE)flightGoal=new Vec3(x,Math.min(level().getMaxBuildHeight()-40,ground+InkDragonFlightRules.HOVER_CLEARANCE),z);}
        if(flightGoal!=null)flyToward(flightGoal,.22*SCALE);
    }
    private int ground(double x,double z){int height=level().getMinBuildHeight();
        for(int dx:new int[]{-36,0,36})for(int dz:new int[]{-36,0,36}){BlockPos at=BlockPos.containing(x+dx,0,z+dz);if(!level().hasChunkAt(at))return Integer.MIN_VALUE;height=Math.max(height,level().getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,at.getX(),at.getZ()));}return height;}
    private void flyToward(Vec3 goal,double speed){
        Vec3 d=goal.subtract(position());if(d.lengthSqr()<.001)return;Vec3 step=d.scale(Math.min(speed/d.length(),1)),next=position().add(step);
        if(!level().hasChunkAt(BlockPos.containing(next)))return;
        float desired=(float)Math.toDegrees(Math.atan2(-d.x,d.z));float nextYaw=inkMove()==0?getYRot()+net.minecraft.util.Mth.clamp(net.minecraft.util.Mth.wrapDegrees(desired-getYRot()),-3,3):getYRot();
        AABB box=InkDragonFlightRules.bounds(next,getYRot()).minmax(InkDragonFlightRules.bounds(next,nextYaw));
        if(!hasFlightRoom(box)){flightGoal=null;return;}setPos(next);if(inkMove()==0)face(next.add(step.scale(10)));
    }
    /** Fast air-chunk rejection: do not scan a giant empty volume every tick. Never requests missing chunks. */
    public boolean hasFlightRoom(AABB box){
        if(box.minY<level().getMinBuildHeight()||box.maxY>=level().getMaxBuildHeight()||!level().getWorldBorder().isWithinBounds(box))return false;
        for(int cx=net.minecraft.util.Mth.floor(box.minX)>>4;cx<=net.minecraft.util.Mth.floor(box.maxX)>>4;cx++)
            for(int cz=net.minecraft.util.Mth.floor(box.minZ)>>4;cz<=net.minecraft.util.Mth.floor(box.maxZ)>>4;cz++){
                var chunk=level().getChunkSource().getChunk(cx,cz,net.minecraft.world.level.chunk.ChunkStatus.FULL,false);
                // A long visual tail may extend beyond view distance. Do not load
                // those chunks or freeze an otherwise visible, ticking head.
                if(chunk==null)continue;
                if(chunk.getHighestSectionPosition()+16<=box.minY)continue;
                AABB part=box.intersect(new AABB(cx*16,box.minY,cz*16,(cx+1)*16,box.maxY,(cz+1)*16));
                if(level().getBlockCollisions(this,part).iterator().hasNext())return false;
            }
        return true;
    }
    private void face(Vec3 point){Vec3 d=point.subtract(position());float desired=(float)Math.toDegrees(Math.atan2(-d.x,d.z));float turn=inkMove()==0?3:8;setYRot(getYRot()+net.minecraft.util.Mth.clamp(net.minecraft.util.Mth.wrapDegrees(desired-getYRot()),-turn,turn));yBodyRot=getYRot();yHeadRot=getYRot();}
    public float breathPitch(){if(inkMove()!=1)return 0;Vec3 origin=position().add(InkDragonFlightRules.HEAD.add(0,1,0).scale(SCALE).yRot((float)Math.toRadians(90-getYRot())));Vec3 d=effectVector("point").subtract(origin);return (float)net.minecraft.util.Mth.clamp(Math.toDegrees(Math.atan2(-d.y,Math.sqrt(d.x*d.x+d.z*d.z))),-25,65);}
    public Vec3 mouthPosition(){Vec3 d=InkDragonFlightRules.MOUTH.subtract(InkDragonFlightRules.HEAD);double a=Math.toRadians(breathPitch());Vec3 p=new Vec3(d.x*Math.cos(a)-d.y*Math.sin(a),d.x*Math.sin(a)+d.y*Math.cos(a),d.z).add(InkDragonFlightRules.HEAD);return position().add(p.scale(SCALE).yRot((float)Math.toRadians(90-getYRot()))).add(0,SCALE,0);}
    private void attackTick(){
        int age=moveAge();face(victim.position());
        if(inkMove()==1){
            int terrain=ground(victim.getX(),victim.getZ());if(age<28&&terrain!=Integer.MIN_VALUE){Vec3 horizontal=position().subtract(victim.position()).multiply(1,0,1).normalize().scale(22*SCALE);flyToward(new Vec3(victim.getX()+horizontal.x,Math.max(terrain+InkDragonFlightRules.HOVER_CLEARANCE,attackHome.y),victim.getZ()+horizontal.z),.55*SCALE);}
            Vec3 aim=victim.getBoundingBox().getCenter();syncEffect(aim,new Vec3(0,1,0),false);Vec3 mouth=mouthPosition(),direction=aim.subtract(mouth).normalize();
            var ray=level().clip(new ClipContext(mouth,mouth.add(direction.scale(48*SCALE)),ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,this));boolean hit=ray.getType()==HitResult.Type.BLOCK;Vec3 end=ray.getLocation();syncEffect(end,hit?Vec3.atLowerCornerOf(ray.getDirection().getNormal()):new Vec3(0,1,0),hit);
            if(age>=34&&age<106&&age%10==0){for(LivingEntity e:level().getEntitiesOfClass(LivingEntity.class,new AABB(mouth,end).inflate(2*SCALE),this::damageable))if(InkDragonCombatRules.segmentDistanceSquared(e.getBoundingBox().getCenter(),mouth,end)<Math.pow(1.6*SCALE+e.getBbWidth()*.5,2))e.hurt(damageSources().mobAttack(this),10);if(hit)strike(end,6*SCALE,7);}
            if(age==34)playSound(SoundEvents.ENDER_DRAGON_SHOOT,3,.55F);if(age>=120)stopAttack();
        }else{
            if(age<40){int terrain=ground(getX(),getZ());double desired=Math.min(level().getMaxBuildHeight()-24*SCALE,Math.max(attackHome.y+25*SCALE,terrain==Integer.MIN_VALUE?getY():terrain+32*SCALE));flyToward(new Vec3(getX(),desired,getZ()),.8*SCALE);}
            if(age==40){highOrb=position().add(0,-12*SCALE,0);landing=victim.position();}if(age<80)landing=victim.position();
            if(highOrb!=null){var surface=level().clip(new ClipContext(landing.add(0,4,0),landing.add(0,-12,0),ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,this));if(age<80&&surface.getType()==HitResult.Type.BLOCK)landing=surface.getLocation();syncEffect(landing,new Vec3(0,1,0),true);
                if(age>=80&&age<110){Vec3 previous=InkDragonDiveRules.sample(age-1,highOrb,landing).orb(),now=InkDragonDiveRules.sample(age,highOrb,landing).orb();var block=level().clip(new ClipContext(previous,now,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,this));if(block.getType()==HitResult.Type.BLOCK&&block.getLocation().distanceTo(landing)>2){landing=block.getLocation();syncEffect(landing,new Vec3(0,1,0),true);}}
                if(age==110){strike(landing,16*SCALE,32);playSound(SoundEvents.GENERIC_EXPLODE,4,.5F);}}
            if(age>=150)stopAttack();
        }
    }
    private boolean damageable(LivingEntity e){return valid(e)&&!(e instanceof InkCelestialDragonEntity)&&!(e instanceof Player p&&(p.isCreative()||p.isSpectator()));}
    private void strike(Vec3 point,double radius,float damage){for(LivingEntity e:level().getEntitiesOfClass(LivingEntity.class,new AABB(point,point).inflate(radius),this::damageable)){
        if(e.distanceToSqr(point)>radius*radius||level().clip(new ClipContext(point.add(0,.3,0),e.getBoundingBox().getCenter(),ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,this)).getType()!=HitResult.Type.MISS)continue;
        e.hurt(damageSources().mobAttack(this),damage);Vec3 push=e.position().subtract(point).multiply(1,0,1).normalize().scale(.6);e.push(push.x,.25,push.z);e.hurtMarked=true;}}
    @Override public void addAdditionalSaveData(CompoundTag tag){super.addAdditionalSaveData(tag);tag.putInt("InkMotionVersion",2);tag.putBoolean("InkGroundObservation",isGroundedForObservation());if(home!=null)vector(tag,"InkFlightHome",home);}
    @Override public void readAdditionalSaveData(CompoundTag tag){super.readAdditionalSaveData(tag);boolean legacy=tag.getInt("InkMotionVersion")<2;entityData.set(GROUNDED,!legacy&&tag.getBoolean("InkGroundObservation"));if(legacy)setNoAi(false);groundPlaced=false;if(tag.contains("InkFlightHome")){var p=tag.getCompound("InkFlightHome");home=new Vec3(p.getDouble("x"),p.getDouble("y"),p.getDouble("z"));}setNoGravity(true);}
    @Override public boolean isPushable(){return false;}
    @Override protected void doPush(Entity e){}
    @Override public boolean removeWhenFarAway(double distance){return false;}
    @Override public void startSeenByPlayer(ServerPlayer p){super.startSeenByPlayer(p);bar.addPlayer(p);}
    @Override public void stopSeenByPlayer(ServerPlayer p){bar.removePlayer(p);super.stopSeenByPlayer(p);}
    @Override public void die(DamageSource source){stopAttack();bar.removeAllPlayers();super.die(source);}
    @Override public void remove(RemovalReason reason){bar.removeAllPlayers();super.remove(reason);}
    @Override public AABB getBoundingBoxForCulling(){AABB own=(isGroundedForObservation()?InkDragonFlightRules.restingBounds(position(),getYRot()):InkDragonFlightRules.bounds(position(),getYRot())).inflate(3);return inkMove()==0?own:own.inflate(220*SCALE);}
    @Override public boolean shouldRenderAtSqrDistance(double d){return d<512*512;}
}
