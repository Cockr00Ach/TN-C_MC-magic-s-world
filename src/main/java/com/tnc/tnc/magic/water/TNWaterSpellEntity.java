package com.tnc.tnc.magic.water;

import com.tnc.tnc.magic.TNOrbEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.*;
import net.minecraft.world.level.*;
import net.minecraft.world.phys.*;
import java.util.UUID;

/** Fixed cast frame. Clients render it; only the server fires, damages and drills. */
public final class TNWaterSpellEntity extends Entity {
    private static final EntityDataAccessor<Integer> TIER=SynchedEntityData.defineId(TNWaterSpellEntity.class,EntityDataSerializers.INT);
    private final WaterVisualClock visualClock=new WaterVisualClock();
    public double visualAge(float partial){return visualClock.time(tickCount,partial,age());}
    private static final EntityDataAccessor<Integer> AGE=SynchedEntityData.defineId(TNWaterSpellEntity.class,EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> LENGTH=SynchedEntityData.defineId(TNWaterSpellEntity.class,EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> YAW=SynchedEntityData.defineId(TNWaterSpellEntity.class,EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> PITCH=SynchedEntityData.defineId(TNWaterSpellEntity.class,EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Boolean> BLOCKED=SynchedEntityData.defineId(TNWaterSpellEntity.class,EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Integer> FIRED_AT=SynchedEntityData.defineId(TNWaterSpellEntity.class,EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> BOSS_CAST=SynchedEntityData.defineId(TNWaterSpellEntity.class,EntityDataSerializers.BOOLEAN);
    private UUID owner;
    private Vec3 barrageAim;
    private WaterTerrainBore bore;
    /** 真正开火的那一 tick ✓ —— 龙滅的蓄力可能因为"预扫描还没扫完"多花几 tick ✗，
        所以不能再拿 {@code tickCount-charge} 当技能计时 ✗（否则时长会被那几 tick 吃掉 ✗）。 */
    private int fireTick=-1;
    public TNWaterSpellEntity(EntityType<? extends TNWaterSpellEntity> type,Level level) { super(type,level); noPhysics=true;setNoGravity(true); }
    public static void cast(ServerPlayer player,ResourceLocation spell) {
        if(!spell.getNamespace().equals("tnc"))return;
        int tier=WaterSpellRules.tier(spell.getPath()); if(tier==0)return;
        if(tier<=2) { bolt(player, tier,player.getEyePosition().add(player.getLookAngle().scale(.5)),player.getLookAngle().scale(tier==1?1.5:2));
            WaterFx.muzzle(player.serverLevel(),player.getEyePosition().add(player.getLookAngle().scale(.5)),player.getLookAngle(),tier);return; }
        // One active large cast per player; repeated release cannot multiply drilling work.
        for(var old:player.serverLevel().getEntitiesOfClass(TNWaterSpellEntity.class,player.getBoundingBox().inflate(128)))
            if(player.getUUID().equals(old.owner))old.discard();
        var cast=TNOrbEntities.WATER_SPELL.get().create(player.serverLevel());
        if(cast==null)return;
        cast.owner=player.getUUID();cast.entityData.set(TIER,tier);
        cast.entityData.set(YAW,player.getYRot());cast.entityData.set(PITCH,player.getXRot());
        LivingEntity target=tier==3?TNWaterFieldEntity.aimEnemy(player,48):null;
        var aimWall=player.level().clip(new ClipContext(player.getEyePosition(),player.getEyePosition().add(player.getLookAngle().scale(48)),ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,player));
        cast.barrageAim=target!=null?target.getBoundingBox().getCenter():aimWall.getLocation();
        Vec3 desired=player.getEyePosition().add(player.getLookAngle().scale(tier==3?-2:2)).add(0,tier==3?2.5:0,0);
        if(tier==5) {
            // The complete aperture must not start buried in the caster's own ground.
            // 2026-10-10：法阵半径改成水柱半径的**一倍**（15 → 30 格 ✗）⇒ 这条抬高必须跟着按**法阵**
            // 算 ✗，否则 30 格的大阵会有一半埋进地里 ✗。纯摆位 ✗：不碰破坏半径/伤害/时长 ✓。
            double verticalRadius=WaterSpellRules.circleRadius(tier)*Math.sqrt(Math.max(0,1-player.getLookAngle().y*player.getLookAngle().y));
            desired=desired.add(0,Math.max(0,verticalRadius-player.getEyeHeight()+.8-2*player.getLookAngle().y),0);
        }
        var obstacle=player.level().clip(new ClipContext(player.getEyePosition(),desired,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,player));
        cast.setPos(obstacle.getType()==HitResult.Type.MISS?desired:obstacle.getLocation().subtract(desired.subtract(player.getEyePosition()).normalize().scale(.2)));
        if(tier==5) {
            Vec3 aim=aimWall.getType()==HitResult.Type.MISS?player.getEyePosition().add(player.getLookAngle().scale(96)):aimWall.getLocation();
            Vec3 direction=aim.subtract(cast.position()).normalize();
            cast.entityData.set(YAW,(float)Math.toDegrees(Math.atan2(-direction.x,direction.z)));
            cast.entityData.set(PITCH,(float)-Math.toDegrees(Math.asin(direction.y)));
        }
        player.serverLevel().addFreshEntity(cast);
    }
    public static boolean castBoss(com.tnc.tnc.boss.TNWaterLeviathanEntity caster,
                                   LivingEntity target, String spell) {
        boolean basic=com.tnc.tnc.boss.LeviathanSpellRules.BASIC.equals(spell);
        int tier=basic?1:WaterSpellRules.tier(spell);
        if (!(caster.level() instanceof ServerLevel level) || !caster.isAlive()
                || (tier!=3 && tier!=5 && !basic) || !WaterSpellRules.enemy(caster,target)) return false;
        if (!basic) for (var old : level.getEntitiesOfClass(TNWaterSpellEntity.class,caster.getBoundingBox().inflate(128)))
            if (caster.getUUID().equals(old.owner)) old.discard();
        var cast=TNOrbEntities.WATER_SPELL.get().create(level);
        if (cast==null) return false;
        Vec3 targetAim=target.getBoundingBox().getCenter().add(0,basic?WaterSpellRules.boltRadius(1)*20*.75:0,0);
        Vec3 aim=targetAim.subtract(caster.position());
        float yaw=(float)Math.toDegrees(Math.atan2(-aim.x,aim.z));
        // Account for the mouth's height in the rotated model so the beam still meets its target.
        double length=Math.max(caster.MOUTH_HEIGHT+0.01D,aim.length());
        double elevation=Math.atan2(aim.y,aim.horizontalDistance())-Math.asin(caster.MOUTH_HEIGHT/length);
        float pitch=(float)-Math.toDegrees(elevation);
        Vec3 direction=Vec3.directionFromRotation(pitch,yaw);
        Vec3 at=caster.aperturePosition(yaw,pitch);
        if (basic) {
            var shot=TNOrbEntities.WATER_BOLT.get().create(level);
            if(shot==null)return false;
            shot.configure(caster,1,at,direction.scale(1.5D));shot.setSpellScale(20F);
            boolean added=level.addFreshEntity(shot);
            if(added) caster.beginBasicShot(yaw,pitch);
            return added;
        }
        cast.entityData.set(BOSS_CAST,true);
        cast.owner=caster.getUUID(); cast.barrageAim=target.getBoundingBox().getCenter();
        cast.setPos(at); cast.entityData.set(TIER,tier);
        cast.entityData.set(YAW,(float)Math.toDegrees(Math.atan2(-direction.x,direction.z)));
        cast.entityData.set(PITCH,(float)-Math.toDegrees(Math.asin(direction.y)));
        boolean added=level.addFreshEntity(cast);
        if (added) caster.beginChannel(cast,target,caster.position(),cast.entityData.get(YAW));
        return added;
    }

    static void bolt(LivingEntity player,int tier,Vec3 at,Vec3 velocity) { bolt(player,tier,at,velocity,1F); }
    static void bolt(LivingEntity player,int tier,Vec3 at,Vec3 velocity,float scale) {
        var bolt=TNOrbEntities.WATER_BOLT.get().create(player.level());
        if(bolt!=null) { bolt.configure(player,tier,at,velocity);bolt.setSpellScale(scale);player.level().addFreshEntity(bolt); }
    }
    public float spellScale(){return entityData.get(BOSS_CAST) && tier()==3 ? 3F : 1F;}
    public double circleRadius(){return WaterSpellRules.circleRadius(tier())*spellScale();}
    public int duration(){return entityData.get(BOSS_CAST) && tier()==5 ? 200 : WaterSpellRules.duration(tier());}
    public int tier(){return entityData.get(TIER);}
    public int age(){return entityData.get(AGE);}
    public int firedAt(){return entityData.get(FIRED_AT);}
    public float length(){return entityData.get(LENGTH);}
    public boolean blocked(){return entityData.get(BLOCKED);}
    public Vec3 direction(){return Vec3.directionFromRotation(entityData.get(PITCH),entityData.get(YAW));}
    @Override protected void defineSynchedData(){entityData.define(BOSS_CAST,false);entityData.define(FIRED_AT,-1);entityData.define(TIER,3);entityData.define(AGE,0);entityData.define(LENGTH,0F);entityData.define(YAW,0F);entityData.define(PITCH,0F);entityData.define(BLOCKED,false);}
    @Override public void tick() {
        super.tick(); if(level().isClientSide)return;
        ServerLevel level=(ServerLevel)level();
        LivingEntity player=WaterSpellOwners.resolve(level,owner);
        if(player==null || player.distanceToSqr(this)>128*128) {discard();return;}
        ServerPlayer terrainCaster=player instanceof ServerPlayer p?p:null;
        entityData.set(AGE,tickCount);
        Vec3 dir=direction();
        int charge=WaterSpellRules.charge(tier());
        // 龙滅：**蓄力期跑预扫描**（作者 2026-10-10："不管是前期加载久一点"✓）——
        // 整条 96 格通道在这里一次性验证完 ✗（每格 4 片、24 tick 扫完 ✓ < 30 tick 蓄力 ✓），
        // 前端才可能"一开火就射出 96 格" ✓。纯服务端 ✗：不加法阵时长、不改魔力门槛 ✓。
        if(tier()==5 && terrainCaster!=null) {
            if(bore==null)bore=new WaterTerrainBore(position(),dir,WaterSpellRules.radius(tier()),WaterSpellRules.range(tier()));
            bore.scan(level,terrainCaster);
        }
        // 正常情况蓄力 30 tick 结束、预扫描也早在第 24 tick 扫完 ✓；
        // 万一没扫完（服务器卡 / 前方区块没加载 ✗）就**继续蓄力**✗ —— 绝不在"还没验证完"的情况下开火 ✓。
        boolean charging=tickCount<charge||(tier()==5&&terrainCaster!=null&&!bore.prescanDone());
        if(charging) {
            // 蓄力：水元素螺旋收进法阵中心 ✓（纯表现 ✗，不影响蓄力时长/魔力门槛 ✗）
            if(tickCount%2==0) WaterFx.charge(level,position(),dir,circleRadius(),tickCount,Math.max(charge,Math.max(1,tickCount)));
            return;
        }
        if(fireTick<0) {
            fireTick=tickCount;entityData.set(FIRED_AT,fireTick);
            level.playSound(null,blockPosition(),SoundEvents.CONDUIT_ACTIVATE,SoundSource.PLAYERS,1.5F,tier()==5?.5F:.85F);
            WaterFx.muzzle(level,position(),dir,tier());
            WaterFx.aperture(level,position(),dir,circleRadius());
        }
        int active=tickCount-fireTick;
        if(active>=duration()) { discard();return; }
        if(tier()==3) {
            if(active%4==0) {
                Vec3 right=WaterSpellRules.right(dir),up=right.cross(dir).normalize();
                for(int i=0;i<3;i++) {
                    double angle=(active/4*3+i)*2.399963;
                    Vec3 at=position().add(right.scale(Math.cos(angle)*2.8*spellScale())).add(up.scale(Math.sin(angle)*2.8*spellScale()));
                    Vec3 aim=barrageAim!=null?barrageAim:position().add(dir.scale(38));
                    bolt(player,3,at,aim.subtract(at).normalize().scale(1.8),spellScale());
                }
                // 阵面环绕水花 + 阵口喷发 ✓；单发弹幕本身已有拖尾 ✗，这里不逐发喷 ✓
                WaterFx.sigil(level,position(),dir,circleRadius()*.85,active);
                WaterFx.muzzle(level,position(),dir,1);
            }
            return;
        }
        double len;
        if(tier()==5 && terrainCaster!=null) {
            if(bore==null)bore=new WaterTerrainBore(position(),dir,WaterSpellRules.radius(tier()),WaterSpellRules.range(tier()));
            bore.setRemainingTicks(WaterSpellRules.duration(tier())-active);   // 自适应追挖预算 ✓
            bore.tick(level,terrainCaster);len=bore.length();
            if(bore.stopped()&&!blocked()) {
                entityData.set(BLOCKED,true);
                terrainCaster.displayClientMessage(net.minecraft.network.chat.Component.literal("§b龙滅受阻："+bore.stopReason()+"。请换到开阔处施放。"),true);
                level.playSound(null,blockPosition(),SoundEvents.ANVIL_LAND,SoundSource.PLAYERS,.7F,.55F);
            }
        } else len=tier()==5?bossBeamLength(level,dir):clearLength(level,dir);
        entityData.set(LENGTH,(float)len);
        // 光束表面流动 + 末端喷发 ✓，长度用的是服务端真实长度 ✗。
        // 2026-10-10 起：t5 的"真实长度"是**保证射出速度的前端** ✓（预扫描过的授权通道 ✗），
        // 隧道在它后面按预算追挖 ✗ —— 其余档位仍是"挖到哪画到哪" ✓。
        if(active%4==0) WaterFx.beam(level,position(),dir,len,WaterSpellRules.radius(tier()),tier(),active);
        if(len>0 && active%10==0) {
            double radius=WaterSpellRules.radius(tier());
            Vec3 end=position().add(dir.scale(len));
            for(LivingEntity target:level.getEntitiesOfClass(LivingEntity.class,new AABB(position(),end).inflate(radius),t->WaterSpellRules.enemy(player,t))) {
                Vec3 center=target.getBoundingBox().getCenter();
                double along=center.subtract(position()).dot(dir);
                double hitRadius=radius+target.getBbWidth()*.5;
                // 龙滅（t5）：整条通道在开火前就被**逐体素**验证过（没有基岩/领地/出生点/边界 ✗），
                // 而石头本来就是这发光束要挖掉的东西 ✗ ⇒ 通道内不再要求视线 ✓：
                // 躲在自己正要被轰掉的岩层后面的敌人，照样在光束扫过的那一刻挨打 ✓（作者 2026-10-10 选的"瞬间射出"✗）。
                // 保护边界之外依旧不可能被打到 ✗ —— 前端根本越不过那个切片 ✓。

                if(along>=0 && along<=len && WaterSpellRules.segmentDistanceSquared(center,position(),dir,len)<=hitRadius*hitRadius
                        && (tier()==5&&terrainCaster!=null||TNWaterFieldEntity.clearSight(level,position(),center)))
                    target.hurt(level.damageSources().indirectMagic(this,player),WaterSpellRules.damage(tier())*WaterSpellRules.power(player));
            }
        }
        if(active%20==0 && len>0)level.playSound(null,blockPosition(),SoundEvents.WATER_AMBIENT,SoundSource.PLAYERS,1.2F,.65F);
    }
    /** Mob beam shares the real T5 aperture and damage, but has no player drilling authority. */
    private double bossBeamLength(ServerLevel level,Vec3 dir) {
        double length=WaterSpellRules.range(5);
        for(int step=0;step<=length;step++) {
            if(!level.hasChunkAt(BlockPos.containing(position().add(dir.scale(step))))) {
                length=Math.max(0,step-1); break;
            }
        }
        var hit=level.clip(new ClipContext(position(),position().add(dir.scale(length)),
                ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,this));
        return hit.getType()==HitResult.Type.MISS?length:position().distanceTo(hit.getLocation());
    }

    private double clearLength(ServerLevel level,Vec3 dir) {
        double length=WaterSpellRules.range(tier());
        for(int i=0;i<=length;i++)if(!level.hasChunkAt(BlockPos.containing(position().add(dir.scale(i))))) {length=Math.max(0,i-1);break;}
        java.util.Set<BlockPos> seen=new java.util.HashSet<>();
        Vec3 end=position().add(dir.scale(length));
        // Include the full search cube plus inflated collision shapes, even along a diagonal ray.
        for(int step=0;step<=Math.min(WaterSpellRules.range(tier()),length+7);step++) {
            BlockPos center=BlockPos.containing(position().add(dir.scale(step)));
            for(BlockPos mutable:BlockPos.betweenClosed(center.offset(-2,-2,-2),center.offset(2,2,2))) {
                BlockPos pos=mutable.immutable();if(!seen.add(pos))continue;
                if(!level.hasChunkAt(pos)){length=Math.min(length,Math.max(0,step-2));continue;}
                for(AABB box:level.getBlockState(pos).getCollisionShape(level,pos).toAabbs()) {
                    AABB volume=box.move(pos).inflate(.8);
                    if(volume.contains(position())){length=0;break;}
                    var hit=volume.clip(position(),end);
                    if(hit.isPresent())length=Math.min(length,position().distanceTo(hit.get()));
                }
            }
        }
        return length;
    }
    @Override public boolean shouldBeSaved(){return false;}
    @Override protected void readAdditionalSaveData(CompoundTag tag){discard();}
    @Override protected void addAdditionalSaveData(CompoundTag tag){}
    @Override public boolean isPickable(){return false;}
    @Override public boolean isAttackable(){return false;}
}
