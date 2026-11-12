package com.tnc.tnc.boss;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.BossEvent;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobType;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.SmoothSwimmingMoveControl;
import net.minecraft.world.entity.ai.control.FlyingMoveControl;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.ai.navigation.FlyingPathNavigation;
import net.minecraft.world.entity.ai.navigation.WaterBoundPathNavigation;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.HitResult;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.core.object.PlayState;
import software.bernie.geckolib.util.GeckoLibUtil;

/**
 * 蓝金水渊兽：深海兽形水系首领。深蓝鳞甲和外露獠牙负责“凶兽”，蓝金圣甲、
 * 发光额冠和脊柱符文负责“神圣”。水中游泳，离水后飞行，始终不用地面步行寻路。
 */
public final class TNWaterLeviathanEntity extends Monster implements GeoEntity {
    public static final float MAX_HP = 4200.0F;
    public static final float SIZE_SCALE = 4.0F;
    public static final float HITBOX_WIDTH = 4.8F*SIZE_SCALE;
    public static final float HITBOX_HEIGHT = 4.1F*SIZE_SCALE;

    private static final int PHASE_ONE = 1;
    private static final int PHASE_TWO = 2;
    private static final int ACTION_NONE = 0;
    private static final int ACTION_SPELL = 1;
    private static final String ACTION_CONTROLLER = "action";

    private static final EntityDataAccessor<Integer> DATA_PHASE =
            SynchedEntityData.defineId(TNWaterLeviathanEntity.class, EntityDataSerializers.INT);

    private static final EntityDataAccessor<Integer> CAST_POSE =
            SynchedEntityData.defineId(TNWaterLeviathanEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> CAST_PITCH =
            SynchedEntityData.defineId(TNWaterLeviathanEntity.class, EntityDataSerializers.FLOAT);
    /**
     * 升空到天阵之后“化作发光虚影再消失”的进度（0 = 实体本体，{@link #GHOST_TICKS} = 完全不可见）。
     *
     * <p>作者 2026-11-07：「他要飞到很高的天空再消失……这个 boss 的虚影也可以发着光」——
     * 本体是真的飞上去的（碰撞、站位、测试都不变），只是到站后由客户端把同一副模型改画成
     * 自发光半透明残影并淡出；回程落地前再反向淡入，所以服务端只同步这一个 0..30 的计数。
     */
    private static final EntityDataAccessor<Integer> CAST_GHOST =
            SynchedEntityData.defineId(TNWaterLeviathanEntity.class, EntityDataSerializers.INT);
    /** 到站后 40 tick（2 s）淡成虚影并消失，正好在 APPEAR_TICK=100 的巨剑出现前收干净。 */
    public static final int GHOST_TICKS = 40;
    /** 起飞前先让脚下法阵亮 10 tick（0.5 s），再真的离地。 */
    private static final int ASCENT_DELAY = 10;

    // Client-visible anticipation state. The server owns the windup; the client only renders it.
    private static final EntityDataAccessor<Integer> CAST_REMAIN =
            SynchedEntityData.defineId(TNWaterLeviathanEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> CAST_TOTAL =
            SynchedEntityData.defineId(TNWaterLeviathanEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> CAST_VISUAL_TIER =
            SynchedEntityData.defineId(TNWaterLeviathanEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> CAST_VISUAL_FAMILY =
            SynchedEntityData.defineId(TNWaterLeviathanEntity.class, EntityDataSerializers.INT);
    public static final double MOUTH_FORWARD = 109.0D/16.0D*SIZE_SCALE;
    public static final double MOUTH_HEIGHT = 1.35D*SIZE_SCALE;
    public static final double APERTURE_GAP = 0.85D*SIZE_SCALE;
    public static final double COMBAT_DISTANCE = MOUTH_FORWARD+APERTURE_GAP+8.0D;
    public static final double ATTACK_RANGE = 96.0D;
    public static final double FIELD_OWNER_RANGE = 128.0D+MOUTH_FORWARD+APERTURE_GAP;
    private java.util.UUID channel, channelTarget, returnTarget;
    private Vec3 channelPosition;
    private net.minecraft.world.entity.Entity channelEntity;
    private float channelYaw;
    private int returnTicks;
    private int basicMouthTicks;
    private Vec3 ritualVelocity=Vec3.ZERO;
    /** 升空曲线：起点（起飞位置）→ 控制点（前出并微微抬高）→ 天阵站点。 */
    private Vec3 ascentStart, ascentControl, ascentEnd;
    private float ascentT, ascentStep;
    private int ascentDelay;
    private boolean returningFromSky;
    private Vec3 returnPosition;
    /** 回程起始距离，用来把“虚影→实体”的淡入铺满整段下落。 */
    private double returnStartDistance;
    private float castTilt, previousCastTilt;
    private float flightPitch, previousFlightPitch, flightBank, previousFlightBank;

    private final ServerBossEvent bossEvent = new ServerBossEvent(this.getDisplayName(),
            BossEvent.BossBarColor.BLUE, BossEvent.BossBarOverlay.NOTCHED_10);
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    private int pendingAction = ACTION_NONE;
    private int actionTicks;
    private int attackCooldown = 70;
    private String pendingSpell;
    private int basicCasts;
    private int specialCooldown;
    private int nextSpecial;
    private PathNavigation airNavigation;
    private PathNavigation waterNavigation;
    private boolean swimmingMovement;

    public TNWaterLeviathanEntity(EntityType<? extends TNWaterLeviathanEntity> type, Level level) {
        super(type, level);
        this.airNavigation = this.navigation;
        this.waterNavigation = new WaterBoundPathNavigation(this, level);
        this.moveControl = new FlyingMoveControl(this, 12, true);
        this.setNoGravity(true);
        this.xpReward = 1200;
    }

    @Override
    protected PathNavigation createNavigation(Level level) {
        FlyingPathNavigation navigation = new FlyingPathNavigation(this, level);
        navigation.setCanOpenDoors(false);
        navigation.setCanPassDoors(false);
        navigation.setCanFloat(false);
        return navigation;
    }

    @Override
    public void tick() {
        this.setNoGravity(this.isAlive());
        if (!this.level().isClientSide && this.swimmingMovement != this.isInWater()) {
            this.navigation.stop();
            this.swimmingMovement = this.isInWater();
            this.navigation = this.swimmingMovement ? this.waterNavigation : this.airNavigation;
            this.moveControl = this.swimmingMovement
                    ? new SmoothSwimmingMoveControl(this, 85, 10, 0.035F, 0.1F, false)
                    : new FlyingMoveControl(this, 12, true);
        }
        super.tick();
        this.fallDistance = 0.0F;
        if (this.level() instanceof ServerLevel server) {
            if (!this.isAlive() && this.castRemainingTicks()!=0) this.clearVisualWindup();
            if (basicMouthTicks>0 && --basicMouthTicks==0 && castPose()==4) entityData.set(CAST_POSE,0);
            tickChannel(server);
        }
        previousCastTilt = castTilt;
        // 升空段（pose 2）的俯仰由曲线切线给，不再固定“低头 90°”；只有到站低头吐剑才用 castTilt。
        castTilt += net.minecraft.util.Mth.clamp((castPose()==3 ? 1F : 0F)-castTilt,-0.20F,0.20F);
        previousFlightPitch=flightPitch;previousFlightBank=flightBank;
        Vec3 drift=position().subtract(xo,yo,zo);
        float lean=drift.lengthSqr()<0.0001 ? 0 : net.minecraft.util.Mth.clamp(
                (float)-Math.toDegrees(Math.atan2(drift.y,drift.horizontalDistance()+0.05D)),-24F,24F);
        float bank=net.minecraft.util.Mth.clamp(net.minecraft.util.Mth.wrapDegrees(yBodyRot-yBodyRotO)*0.45F,-8F,8F);
        flightPitch=net.minecraft.util.Mth.lerp(0.12F,flightPitch,lean);
        flightBank=net.minecraft.util.Mth.lerp(0.15F,flightBank,bank);
    }

    @Override
    public void travel(Vec3 input) {
        if (channel != null || returningFromSky) { this.setDeltaMovement(Vec3.ZERO); return; }
        if (this.isEffectiveAi() && this.isAlive()) {
            this.moveRelative(this.isInWater() ? 0.035F : 0.045F, input);
            this.move(MoverType.SELF, this.getDeltaMovement());
            this.setDeltaMovement(this.getDeltaMovement().scale(this.isInWater() ? 0.88D : 0.91D));
        } else {
            super.travel(input);
        }
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, MAX_HP)
                .add(Attributes.ATTACK_DAMAGE, 26.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.19D)
                .add(Attributes.FLYING_SPEED, 0.30D)
                .add(Attributes.FOLLOW_RANGE, 128.0D)
                .add(Attributes.ARMOR, 18.0D)
                .add(Attributes.ARMOR_TOUGHNESS, 10.0D)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0D);
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define(DATA_PHASE, PHASE_ONE);
        this.entityData.define(CAST_POSE, 0);
        this.entityData.define(CAST_PITCH, 0F);
        this.entityData.define(CAST_GHOST, 0);
        this.entityData.define(CAST_REMAIN, 0);
        this.entityData.define(CAST_TOTAL, 0);
        this.entityData.define(CAST_VISUAL_TIER, 0);
        this.entityData.define(CAST_VISUAL_FAMILY, 0);
    }

    public java.util.UUID channelId() { return channel; }
    public int castPose() { return entityData.get(CAST_POSE); }
    /** Windup ticks left; 0 means no anticipation is playing. */
    public int castRemainingTicks() { return entityData.get(CAST_REMAIN); }
    public int castTotalTicks() { return entityData.get(CAST_TOTAL); }
    /** 1 = water orb, 2 = water bind; 0 = no anticipation. */
    public int castVisualFamily() { return entityData.get(CAST_VISUAL_FAMILY); }
    public int castVisualTier() { return entityData.get(CAST_VISUAL_TIER); }
    public boolean mouthOpen() { return castPose()==1 || castPose()==3 || castPose()==4; }
    public float castTilt(float partial) {
        float t=net.minecraft.util.Mth.lerp(partial,previousCastTilt,castTilt);
        return t*t*(3F-2F*t);
    }

    public boolean isReturningFromSky() { return returningFromSky; }
    /** 0 = 实体本体；>0 = 已经化虚影（30 = 完全消失）。 */
    public int ghostAge() { return entityData.get(CAST_GHOST); }
    public boolean ghosting() { return ghostAge()>0; }
    /** 1 = 清楚可见，0 = 完全消失。 */
    public float ghostAlpha() { return 1F-net.minecraft.util.Mth.clamp(ghostAge()/(float)GHOST_TICKS,0F,1F); }
    public float flightPitch(float partial){return net.minecraft.util.Mth.lerp(partial,previousFlightPitch,flightPitch);}
    public float flightBank(float partial){return net.minecraft.util.Mth.lerp(partial,previousFlightBank,flightBank);}
    public float castingPitch(float partial) {
        // pose 2 = 沿曲线升空：俯仰跟着路径切线（向上爬升就是抬头），pose 3 = 到站低头吐剑。
        return castPose()==1 || castPose()==4 || castPose()==2 ? entityData.get(CAST_PITCH) : 90F*castTilt(partial);
    }
    public Vec3 mouthPosition(float yaw,float pitch) {
        return position().add(Vec3.directionFromRotation(pitch,yaw).scale(MOUTH_FORWARD))
                .add(Vec3.directionFromRotation(pitch-90F,yaw).scale(MOUTH_HEIGHT));
    }
    public Vec3 aperturePosition(float yaw,float pitch) {
        return mouthPosition(yaw,pitch).add(Vec3.directionFromRotation(pitch,yaw).scale(APERTURE_GAP));
    }

    public void beginBasicShot(float yaw,float pitch) {
        basicMouthTicks=18;entityData.set(CAST_POSE,4);entityData.set(CAST_PITCH,pitch);
        setYRot(yaw);setYHeadRot(yaw);setYBodyRot(yaw);
    }
    private void moveRitualToward(Vec3 destination) {
        Vec3 delta=destination.subtract(position());
        Vec3 wanted=delta.normalize().scale(Math.min(4.8D,delta.length()*0.24D));
        ritualVelocity=ritualVelocity.lerp(wanted,0.40D);
        move(MoverType.SELF,ritualVelocity);
    }

    /** The spell owns this state: interruption, death and its natural end all release it. */
    public void beginChannel(net.minecraft.world.entity.Entity spell, net.minecraft.world.entity.LivingEntity target,
                             Vec3 station, float yaw) {
        channel=spell.getUUID(); channelEntity=spell; channelTarget=target.getUUID(); channelPosition=station;
        channelYaw=yaw;basicMouthTicks=0;ritualVelocity=Vec3.ZERO;entityData.set(CAST_GHOST,0);returnStartDistance=0D;
        if (spell instanceof com.tnc.tnc.magic.water.TNWaterFieldEntity) returnPosition=position();
        returningFromSky=false;
        entityData.set(CAST_PITCH,spell instanceof com.tnc.tnc.magic.water.TNWaterSpellEntity beam
                ? (float)-Math.toDegrees(Math.asin(beam.direction().y)) : 0F);
        entityData.set(CAST_POSE,spell instanceof com.tnc.tnc.magic.water.TNWaterFieldEntity ? 2 : 1);
        if (spell instanceof com.tnc.tnc.magic.water.TNWaterFieldEntity) beginAscent(station,yaw);
        navigation.stop(); setDeltaMovement(Vec3.ZERO);
    }

    /**
     * 起飞航线（作者手绘 2026-11-07）：先贴着水面往前冲出去，再拐一个弯几乎垂直地窜上天空，
     * 而不是从原地直线平移上去。用二次贝塞尔，控制点放在正前方偏上，于是
     * 起点切线≈水平前冲、终点切线≈竖直向上，正好是画里那条“先平飞后拔高”的曲线。
     */
    private void beginAscent(Vec3 station,float yaw) {
        ascentStart=position(); ascentEnd=station;
        Vec3 toStation=station.subtract(ascentStart);
        Vec3 flat=new Vec3(toStation.x,0,toStation.z);
        double flatLength=flat.length();
        Vec3 dir=flatLength>1.0E-3D ? flat.normalize() : Vec3.directionFromRotation(0,yaw);
        double out=net.minecraft.util.Mth.clamp(flatLength*.45D,9D,26D);
        // 控制点只抬高一点点：起点切线接近水平（先贴着水面冲出去），终点切线才接近竖直（再拔高）。
        double rise=net.minecraft.util.Mth.clamp(Math.abs(toStation.y)*.025D,1.5D,6D);
        ascentControl=ascentStart.add(dir.scale(out)).add(0,rise,0);
        double length=ascentStart.distanceTo(ascentControl)+ascentControl.distanceTo(ascentEnd);
        // 34→40 tick 爬完 100+ 格：比旧版直线插值更快，且到站时间早于 APPEAR_TICK=100。
        ascentStep=(float)(1D/net.minecraft.util.Mth.clamp(length/3.4D,26D,40D));
        ascentT=0F; ascentDelay=ASCENT_DELAY;
        Vec3 tangent=ascentTangent(0F);
        if (tangent.lengthSqr()>1.0E-6D) {
            float pitch=pitchFor(tangent), yawToPath=yawFor(tangent);
            entityData.set(CAST_PITCH,pitch);
            // 起飞前一拍就把头抬到航向上：法阵亮着的那 10 tick 里它已经在“瞄准天空”，不是平移。
            setXRot(pitch); setYRot(yawToPath); setYHeadRot(yawToPath); setYBodyRot(yawToPath);
        }
    }

    private Vec3 ascentPoint(float t) {
        float u=1F-t;
        return ascentStart.scale(u*u).add(ascentControl.scale(2F*u*t)).add(ascentEnd.scale(t*t));
    }

    private Vec3 ascentTangent(float t) {
        return ascentControl.subtract(ascentStart).scale(2F*(1F-t))
                .add(ascentEnd.subtract(ascentControl).scale(2F*t));
    }

    /** 正数 = 低头（MC 的 xRot 约定），爬升时切线的 y 为正 ⇒ 得到负俯仰 = 抬头。 */
    private static float pitchFor(Vec3 tangent) {
        Vec3 n=tangent.normalize();
        return (float)-Math.toDegrees(Math.asin(net.minecraft.util.Mth.clamp(n.y,-1D,1D)));
    }

    private static float yawFor(Vec3 tangent) {
        return (float)Math.toDegrees(Math.atan2(-tangent.x,tangent.z));
    }

    /** 沿曲线推进并让头/身体对齐切线方向；返回 true 表示已到站。 */
    private boolean climbAscent() {
        if (ascentEnd==null) return false;
        if (ascentDelay>0) { ascentDelay--; return false; }
        ascentT=Math.min(1F,ascentT+ascentStep);
        Vec3 tangent=ascentTangent(ascentT);
        if (tangent.lengthSqr()>1.0E-6D) {
            float yaw=yawFor(tangent);
            setYRot(yaw);setYHeadRot(yaw);setYBodyRot(yaw);
            float pitch=pitchFor(tangent);
            setXRot(pitch);
            entityData.set(CAST_PITCH,pitch);
        }
        Vec3 want=ascentPoint(ascentT);
        Vec3 delta=want.subtract(position());
        // 仍然走 move(...)：巨兽的碰撞体照旧参与，只换目的地。
        if (delta.lengthSqr()>1.0E-6D) move(MoverType.SELF,delta);
        return ascentT>=1F || position().distanceToSqr(ascentEnd)<0.36D;
    }

    private void tickChannel(ServerLevel server) {
        if (channel==null) {
            if (returningFromSky) { tickSkyReturn(server); return; }
            if (returnTicks>0 && --returnTicks>=0 && returnTarget!=null && server.getEntity(returnTarget) instanceof net.minecraft.world.entity.LivingEntity target
                    && canStrike(target)) setTarget(target);
            return;
        }
        // Newly added effects may still be queued until the entity tick loop ends.
        var spell=channelEntity;
        if (!isAlive() || spell==null || spell.isRemoved()) {
            returningFromSky=isAlive() && (castPose()==2 || castPose()==3);
            channel=null; channelEntity=null; channelPosition=null; entityData.set(CAST_POSE,0);
            setXRot(0F);
            returnTarget=channelTarget; returnTicks=600; returnStartDistance=0D;
            attackCooldown=LeviathanSpellRules.recovery(isPhaseTwo());
            return;
        }
        navigation.stop(); setDeltaMovement(Vec3.ZERO);
        if (castPose()==2) {
            if (climbAscent()) {
                // 到站那一刻的俯仰直接接到 castTilt 上：模型从“仰头爬升”平滑翻成“低头吐剑”，不会瞬移。
                float endPitch=ascentEnd==null ? 0F : pitchFor(ascentTangent(1F));
                castTilt=net.minecraft.util.Mth.clamp(endPitch/90F,-1F,1F);
                previousCastTilt=castTilt;
                setPos(channelPosition);ritualVelocity=Vec3.ZERO;
                setYRot(channelYaw); setYHeadRot(channelYaw); setYBodyRot(channelYaw);
                setXRot(90F); entityData.set(CAST_PITCH,90F);
                entityData.set(CAST_POSE,3);
                entityData.set(CAST_GHOST,1);
                ascentEnd=null;
            }
            return;
        }
        setYRot(channelYaw); setYHeadRot(channelYaw); setYBodyRot(channelYaw);
        setPos(channelPosition);
        if (castPose()==3 && ghostAge()>0 && ghostAge()<GHOST_TICKS)
            entityData.set(CAST_GHOST,Math.min(GHOST_TICKS,ghostAge()+1));
        setXRot(castPose()==3 ? 90F : spell instanceof com.tnc.tnc.magic.water.TNWaterSpellEntity beam
                ? (float)-Math.toDegrees(Math.asin(beam.direction().y)) : 0F);
    }

    private void tickSkyReturn(ServerLevel server) {
        if (!isAlive()) { returningFromSky=false; return; }
        net.minecraft.world.entity.LivingEntity target=returnTarget!=null
                && server.getEntity(returnTarget) instanceof net.minecraft.world.entity.LivingEntity living
                && canStrike(living) ? living : getTarget();
        if (target==null || !canStrike(target)) {
            target=server.getEntitiesOfClass(ServerPlayer.class,getBoundingBox().inflate(160),this::canStrike)
                    .stream().min(java.util.Comparator.comparingDouble(this::distanceToSqr)).orElse(null);
        }
        Vec3 destination=returnPosition==null ? position().add(0,-8,0) : returnPosition.add(0,LeviathanMovementRules.AIR_TARGET_HEIGHT,0);
        if (target!=null && canStrike(target)) {
            setTarget(target); returnTarget=target.getUUID(); returnTicks=600;
            Vec3 away=position().subtract(target.position()).multiply(1,0,1);
            if (away.lengthSqr()<1) away=Vec3.directionFromRotation(0,getYRot()).scale(-1);
            destination=target.position().add(away.normalize().scale(COMBAT_DISTANCE)).add(0,LeviathanMovementRules.AIR_TARGET_HEIGHT,0);
        }
        navigation.stop(); setDeltaMovement(Vec3.ZERO); setXRot(0F);
        Vec3 delta=destination.subtract(position());
        // 回程按“剩余路程比例”反向淡入：从 116 格高空下来只用 20 多 tick，
        // 若只在最后 30 格才淡，落地那一刻还是全透明 ⇒ 会“啪”地凭空出现。
        if (returnStartDistance<=0D) returnStartDistance=Math.max(1D,delta.length());
        if (ghostAge()>0) {
            double left=Math.min(1D,delta.length()/returnStartDistance);
            entityData.set(CAST_GHOST,(int)Math.round(GHOST_TICKS*left));
        }
        if (delta.lengthSqr()<6.25D) {
            entityData.set(CAST_GHOST,0);
            returningFromSky=false; returnPosition=null;ritualVelocity=Vec3.ZERO;attackCooldown=10;
            returnStartDistance=0D;
            return;
        }
        // Follow the victim's current location rather than the old stationary crypt field.
        if (level().hasChunkAt(BlockPos.containing(destination)))
            moveRitualToward(destination);
        if (target!=null) getLookControl().setLookAt(target,30,30);
    }

    public int phase() {
        return this.entityData.get(DATA_PHASE);
    }

    public boolean isPhaseTwo() {
        return this.phase() >= PHASE_TWO;
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("LeviathanPhase", this.phase());
        tag.putInt("LeviathanSpecialCooldown", specialCooldown);
        tag.putInt("LeviathanNextSpecial", nextSpecial);
        if (returnPosition!=null && (returningFromSky || castPose()==2 || castPose()==3)) {
            tag.putBoolean("LeviathanSkyReturn",true);
            tag.putDouble("LeviathanReturnX",returnPosition.x);
            tag.putDouble("LeviathanReturnY",returnPosition.y);
            tag.putDouble("LeviathanReturnZ",returnPosition.z);
            java.util.UUID victim=channelTarget!=null ? channelTarget : returnTarget;
            if (victim!=null) tag.putUUID("LeviathanReturnTarget",victim);
        }
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        this.entityData.set(DATA_PHASE,
                tag.getInt("LeviathanPhase") >= PHASE_TWO ? PHASE_TWO : PHASE_ONE);
        // Attributes are already restored by LivingEntity; never multiply them again on reload.
        this.pendingAction = ACTION_NONE;
        this.pendingSpell = null;
        this.specialCooldown = Math.max(0,tag.getInt("LeviathanSpecialCooldown"));
        this.nextSpecial = Math.floorMod(tag.getInt("LeviathanNextSpecial"),3);
        this.attackCooldown = 70;
        AttributeInstance follow=this.getAttribute(Attributes.FOLLOW_RANGE);
        if (follow!=null) follow.setBaseValue(Math.max(128.0D,follow.getBaseValue()));
        if (this.hasCustomName() && ("圣渊利维坦".equals(this.getName().getString())
                || "利维坦".equals(this.getName().getString())))
            this.setCustomName(Component.translatable("entity.tnc.water_leviathan"));
        channel=null; channelEntity=null; channelPosition=null; entityData.set(CAST_POSE,0);
        returningFromSky=tag.getBoolean("LeviathanSkyReturn");
        // 存档恢复时若正从天上回来，别让巨兽突然出现在 116 格高空，继续保持虚影直到落地淡入。
        entityData.set(CAST_GHOST,returningFromSky ? GHOST_TICKS : 0);
        returnStartDistance=0D;
        returnPosition=returningFromSky ? new Vec3(tag.getDouble("LeviathanReturnX"),
                tag.getDouble("LeviathanReturnY"),tag.getDouble("LeviathanReturnZ")) : null;
        returnTarget=tag.hasUUID("LeviathanReturnTarget") ? tag.getUUID("LeviathanReturnTarget") : null;
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(2, new LeviathanMoveGoal());
        this.goalSelector.addGoal(8, new LookAtPlayerGoal(this, Player.class, 56.0F));
        this.goalSelector.addGoal(9, new RandomLookAroundGoal(this));
        this.targetSelector.addGoal(1, new HurtByTargetGoal(this));
        this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
    }

    @Override
    public boolean canBreatheUnderwater() {
        return true;
    }

    @Override
    public MobType getMobType() {
        return MobType.WATER;
    }

    @Override
    public boolean removeWhenFarAway(double distance) {
        return false;
    }

    @Override
    public boolean isPersistenceRequired() {
        return true;
    }

    @Override
    public AABB getBoundingBoxForCulling() {
        return super.getBoundingBoxForCulling().inflate(24.0D*SIZE_SCALE, 24.0D*SIZE_SCALE, 24.0D*SIZE_SCALE);
    }

    @Override
    public void startSeenByPlayer(ServerPlayer player) {
        super.startSeenByPlayer(player);
        this.bossEvent.addPlayer(player);
    }

    @Override
    public void stopSeenByPlayer(ServerPlayer player) {
        super.stopSeenByPlayer(player);
        this.bossEvent.removePlayer(player);
    }

    @Override
    public boolean doHurtTarget(net.minecraft.world.entity.Entity target) {
        boolean hit = super.doHurtTarget(target);
        if (hit && !this.level().isClientSide()) {
            this.triggerAnim(ACTION_CONTROLLER, "bite");
        }
        return hit;
    }

    @Override
    public void aiStep() {
        super.aiStep();
        if (!(this.level() instanceof ServerLevel level)) {
            return;
        }
        this.bossEvent.setName(this.getDisplayName());
        this.bossEvent.setProgress(Math.max(0.0F, Math.min(1.0F,
                this.getHealth() / Math.max(1.0F, this.getMaxHealth()))));
        this.bossEvent.setVisible(this.isAlive() && !this.isDeadOrDying());
        if (!this.isAlive() || this.isDeadOrDying() || this.isNoAi()) return;

        if (!this.isPhaseTwo() && this.getHealth() <= this.getMaxHealth() * 0.5F) {
            this.enterPhaseTwo(level);
        }
        this.sacredAura(level);
        this.tickSpecialAttack(level);
    }

    private void enterPhaseTwo(ServerLevel level) {
        this.entityData.set(DATA_PHASE, PHASE_TWO);
        this.pendingAction = ACTION_NONE;
        this.pendingSpell = null;
        this.attackCooldown = 50;
        AttributeInstance speed = this.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed != null) speed.setBaseValue(speed.getBaseValue() * 1.18D);
        AttributeInstance damage = this.getAttribute(Attributes.ATTACK_DAMAGE);
        if (damage != null) damage.setBaseValue(damage.getBaseValue() * 1.25D);
        AttributeInstance flight = this.getAttribute(Attributes.FLYING_SPEED);
        if (flight != null) flight.setBaseValue(flight.getBaseValue() * 1.18D);

        if (channel==null) this.triggerAnim(ACTION_CONTROLLER, "roar");
        level.playSound(null, this.getX(), this.getY(), this.getZ(),
                SoundEvents.ELDER_GUARDIAN_CURSE, SoundSource.HOSTILE, 3.5F, 0.72F);
        level.sendParticles(ParticleTypes.SPLASH, this.getX(), this.getY() + 1.5D, this.getZ(),
                220, 4.8D, 2.2D, 4.8D, 0.24D);
        level.sendParticles(ParticleTypes.END_ROD, this.getX(), this.getY() + 3.4D, this.getZ(),
                110, 3.2D, 1.8D, 3.2D, 0.12D);
        for (ServerPlayer player : level.getEntitiesOfClass(ServerPlayer.class,
                this.getBoundingBox().inflate(72.0D))) {
            player.displayClientMessage(Component.literal(
                    "§3[水渊] §b蓝金水渊兽的圣甲正在苏醒"), false);
        }
    }

    private void sacredAura(ServerLevel level) {
        int interval = this.isPhaseTwo() ? 3 : 7;
        if (this.tickCount % interval != 0) return;
        int count = this.isPhaseTwo() ? 7 : 3;
        for (int i = 0; i < count; i++) {
            double angle = this.random.nextDouble() * Math.PI * 2.0D;
            double radius = (1.4D + this.random.nextDouble() * 3.8D)*SIZE_SCALE;
            double x = this.getX() + Math.cos(angle) * radius;
            double z = this.getZ() + Math.sin(angle) * radius;
            double y = this.getY() + (0.4D + this.random.nextDouble() * 4.0D)*SIZE_SCALE;
            level.sendParticles(i % 3 == 0 ? ParticleTypes.END_ROD : ParticleTypes.BUBBLE_POP,
                    x, y, z, 1, 0.03D, 0.05D, 0.03D, 0.01D);
        }
    }

    private void tickSpecialAttack(ServerLevel level) {
        if (this.specialCooldown>0) --this.specialCooldown;
        if (channel!=null || returningFromSky) return;
        var target=this.getTarget();
        if (this.pendingAction != ACTION_NONE) {
            if (--this.actionTicks <= 0) {
                boolean cast=target!=null && this.canStrike(target) && this.hasLineOfSight(target)
                        && LeviathanSpells.cast(this,target,this.pendingSpell);
                if (cast) {
                    if (LeviathanSpellRules.BASIC.equals(this.pendingSpell)) {
                        this.basicCasts=Math.min(3,this.basicCasts+1);
                    } else {
                        this.specialCooldown=LeviathanSpellRules.specialCooldown(this.pendingSpell,this.isPhaseTwo());
                        this.basicCasts=0;
                        this.nextSpecial=(this.nextSpecial+1)%3;
                    }
                }
                this.pendingAction = ACTION_NONE;
                this.pendingSpell=null;
                this.attackCooldown=LeviathanSpellRules.recovery(this.isPhaseTwo());
                this.clearVisualWindup();
            } else {
                this.entityData.set(CAST_REMAIN,this.actionTicks);
            }
            return;
        }

        if (target==null || !this.canStrike(target) || this.distanceToSqr(target)>ATTACK_RANGE*ATTACK_RANGE
                || this.distanceToSqr(target)<Math.pow(MOUTH_FORWARD+APERTURE_GAP+2,2)
                || !this.hasLineOfSight(target) || --this.attackCooldown>0) return;
        this.pendingSpell=LeviathanSpellRules.next(this.basicCasts,this.specialCooldown,this.nextSpecial);
        this.pendingAction=ACTION_SPELL;
        this.actionTicks=LeviathanSpellRules.windup(this.pendingSpell,this.isPhaseTwo());
        this.beginVisualWindup(this.pendingSpell,this.actionTicks);
        this.getLookControl().setLookAt(target, 30, 30);
        if (!LeviathanSpellRules.BASIC.equals(this.pendingSpell))
            this.triggerAnim(ACTION_CONTROLLER, "breath");
        boolean basic=LeviathanSpellRules.BASIC.equals(this.pendingSpell);
        if (basic) {
            // Open during the anticipation, before the uncharged ordinary orb leaves the mouth.
            basicMouthTicks=actionTicks+18;entityData.set(CAST_POSE,4);
            entityData.set(CAST_PITCH,getXRot());
        }
        level.playSound(null,this.getX(),this.getY(),this.getZ(),SoundEvents.CONDUIT_ACTIVATE,
                SoundSource.HOSTILE,basic?1.4F:2.5F,basic?0.9F:0.58F);
        Vec3 muzzle=mouthPosition(getYRot(),0F);
        level.sendParticles(ParticleTypes.END_ROD,muzzle.x,muzzle.y,muzzle.z,
                basic?8:28,1.1D*SIZE_SCALE,0.65D*SIZE_SCALE,1.1D*SIZE_SCALE,0.04D);
    }

    /**
     * Publish the anticipation to the client. No new entity or packet: only four synched ints
     * that the existing boss renderer already receives.
     */
    private void beginVisualWindup(String spell,int ticks) {
        this.entityData.set(CAST_REMAIN,ticks);
        this.entityData.set(CAST_TOTAL,ticks);
        this.entityData.set(CAST_VISUAL_TIER,switch(spell) {
            case "dragon_ruin","sea_god_crypt" -> 5;
            case "dragon_roar" -> 3;
            default -> 3;
        });
        this.entityData.set(CAST_VISUAL_FAMILY,"sea_god_crypt".equals(spell) ? 2 : 1);
    }

    private void clearVisualWindup() {
        this.entityData.set(CAST_REMAIN,0);
        this.entityData.set(CAST_TOTAL,0);
        this.entityData.set(CAST_VISUAL_TIER,0);
        this.entityData.set(CAST_VISUAL_FAMILY,0);
    }

    private boolean canStrike(net.minecraft.world.entity.LivingEntity victim) {
        return victim != this && victim.isAlive() && !this.isAlliedTo(victim)
                && !(victim instanceof TNWaterLeviathanEntity)
                && !(victim instanceof Player player && (player.isCreative() || player.isSpectator()));
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "move", 10, state -> {
            if (mouthOpen()) {
                state.getController().setAnimationSpeed(1.0F);
                return state.setAndContinue(RawAnimation.begin().thenLoop("channel_idle"));
            }
            if (this.isInWater()) {
                state.getController().setAnimationSpeed(1.15F);
                return state.setAndContinue(RawAnimation.begin().thenLoop("swim"));
            }
            state.getController().setAnimationSpeed(state.isMoving() ? 1.0F : 0.72F);
            return state.setAndContinue(RawAnimation.begin().thenLoop("glide"));
        }));
        AnimationController<TNWaterLeviathanEntity> action = new AnimationController<>(
                this, ACTION_CONTROLLER, 2, state -> PlayState.STOP);
        action.triggerableAnim("bite", RawAnimation.begin().thenPlay("bite"));
        action.triggerableAnim("roar", RawAnimation.begin().thenPlay("roar"));
        action.triggerableAnim("breath", RawAnimation.begin().thenPlay("breath"));
        action.triggerableAnim("tail", RawAnimation.begin().thenPlay("tail_sweep"));
        controllers.add(action);
        controllers.add(new AnimationController<>(this,"mouth",10,state ->
                state.setAndContinue(RawAnimation.begin().thenLoop(mouthOpen() ? "mouth_open" : "mouth_closed"))));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return this.cache;
    }

    public static ResourceLocation modelResource() {
        return ResourceLocation.fromNamespaceAndPath("tnc", "geo/entity/water_leviathan.geo.json");
    }

    public static ResourceLocation textureResource() {
        return ResourceLocation.fromNamespaceAndPath("tnc", "textures/entity/water_leviathan_bedrock.png");
    }

    public static ResourceLocation animationResource() {
        return ResourceLocation.fromNamespaceAndPath("tnc", "animations/entity/water_leviathan.animation.json");
    }

    /** One three-dimensional movement goal; neither mode asks for a walkable ground node. */
    private final class LeviathanMoveGoal extends Goal {
        private int repath;
        private Vec3 destination;

        private LeviathanMoveGoal() {
            this.setFlags(java.util.EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        @Override public boolean canUse() { return true; }
        @Override public boolean requiresUpdateEveryTick() { return true; }
        @Override public void stop() { navigation.stop(); }

        @Override public void tick() {
            if (channel!=null || returningFromSky) { navigation.stop(); return; }
            var target = getTarget();
            boolean chasing = target != null && target.isAlive() && canStrike(target);
            if (chasing) {
                getLookControl().setLookAt(target, 12.0F, 12.0F);

            }
            if (pendingAction != ACTION_NONE) {
                navigation.stop();
                setDeltaMovement(getDeltaMovement().scale(0.8D));
                return;
            }
            if (chasing && swimmingMovement) {
                // Large water mobs can finish a path while their feet are still below
                // its final node. Keep depth control independent of that path tolerance.
                double depth = target.getY()+(target.isInWater() ? 0 : LeviathanMovementRules.AIR_TARGET_HEIGHT)-getY();
                double vertical = LeviathanMovementRules.swimVerticalAcceleration(depth);
                Vec3 velocity = getDeltaMovement();
                setDeltaMovement(velocity.x,
                        net.minecraft.util.Mth.clamp(velocity.y+vertical, -0.20D, 0.20D), velocity.z);
            }
            if (--repath > 0) return;
            repath = 12;
            if (chasing) {
                Vec3 away=position().subtract(target.position()).multiply(1,0,1);
                if (away.lengthSqr()<1) away=Vec3.directionFromRotation(0,getYRot()).scale(-1);
                destination=target.position().add(away.normalize().scale(COMBAT_DISTANCE))
                        .add(0,swimmingMovement && target.isInWater() ? 0 : LeviathanMovementRules.AIR_TARGET_HEIGHT,0);
            } else if (destination == null || position().distanceToSqr(destination) < 9.0D
                    || tickCount % 120 < 12) {
                destination = position().add((random.nextDouble()-0.5D)*16.0D,
                        (random.nextDouble()-0.5D)*3.0D, (random.nextDouble()-0.5D)*16.0D);
            }
            if (!level().hasChunkAt(BlockPos.containing(destination))) {
                navigation.stop();
                destination = null;
                return;
            }
            if (!swimmingMovement) {
                double floor = nearbyFloor();
                destination = new Vec3(destination.x,
                        LeviathanMovementRules.airDestinationY(destination.y, floor,
                                level().getMaxBuildHeight()-HITBOX_HEIGHT-1), destination.z);
            }
            double speed = chasing ? 1.0D : 0.60D;
            Vec3 mid=position().add(0,getBbHeight()*0.5D,0);
            boolean open=level().clip(new ClipContext(mid,destination.add(0,getBbHeight()*0.5D,0),
                    ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,TNWaterLeviathanEntity.this)).getType()==HitResult.Type.MISS;
            // Avoid searching thousands of volume nodes for an enormous creature in open air/water.
            if (open && !horizontalCollision) {
                navigation.stop();
                moveControl.setWantedPosition(destination.x,destination.y,destination.z,speed);
            } else if (!navigation.moveTo(destination.x, destination.y, destination.z, speed)) {
                // Direct steering also permits crossing the air/water boundary when the
                // current medium's pathfinder cannot yet accept the next medium's node.
                moveControl.setWantedPosition(destination.x, destination.y, destination.z, speed);
            }
        }

        private double nearbyFloor() {
            double floor = getY()-10.0D*SIZE_SCALE;
            for (double[] offset : new double[][]{{0, 0}, {-8, 0}, {8, 0}, {0, -8}, {0, 8}}) {
                Vec3 from = position().add(offset[0], 0.25D, offset[1]);
                HitResult hit = level().clip(new ClipContext(from, from.add(0, -10*SIZE_SCALE, 0),
                        ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, TNWaterLeviathanEntity.this));
                if (hit.getType() != HitResult.Type.MISS) floor = Math.max(floor, hit.getLocation().y);
            }
            return floor;
        }
    }
}
