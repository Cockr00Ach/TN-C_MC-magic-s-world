package com.tnc.tnc.magic.fire;

import com.tnc.tnc.magic.TNOrbEntities;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.UUID;

/**
 * 火手链：一只<b>燃烧的巨人手掌</b>从目标脚下破土而出，抓住并捏爆它。
 *
 * <h2>为什么是一个实体，不是一次性特效</h2>
 * 抓取要有"环绕/固定/连续伤害"的过程（玩家要看到手张→合→捏三下），
 * 而且目标可能被打飞或自己乱跑 —— 只有服务端实体能每 tick 把目标按在手里 ✓。
 *
 * <h2>阶段（{@link #PHASE}，0~1）</h2>
 * <ol>
 *   <li>0.00~0.18 破土：手掌从地里顶出来，指头半握</li>
 *   <li>0.18~0.32 张爪：四指张开，拇指对扣（这时候才真正抓住目标）</li>
 *   <li>0.32~0.46 合拢：手指依次收拢（食指→中指→无名指→小指→拇指）</li>
 *   <li>0.46~0.80 捏爆：三下收紧，每下都结算一次伤害 + 热量</li>
 *   <li>0.80~1.00 炸开：手掌摊开，按目标身上的热量放大爆炸</li>
 * </ol>
 *
 * <h2>空中目标</h2>
 * 索敌到飞行怪时，手仍从<b>地面</b>升起，但手臂会一直往天上烧（渲染层画火柱），
 * 手本身出现在目标脚下 —— 见 {@link #aerial()}。
 */
public class TNFlameHandEntity extends Entity {

    /** 总时长（tick）。 */
    public static final int LIFE = 76;

    private static final EntityDataAccessor<Float> PHASE =
            SynchedEntityData.defineId(TNFlameHandEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Integer> TIER =
            SynchedEntityData.defineId(TNFlameHandEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> HEAT =
            SynchedEntityData.defineId(TNFlameHandEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Boolean> AERIAL =
            SynchedEntityData.defineId(TNFlameHandEntity.class, EntityDataSerializers.BOOLEAN);

    private static final EntityDataAccessor<java.util.Optional<UUID>> VISUAL_CASTER=SynchedEntityData.defineId(TNFlameHandEntity.class,EntityDataSerializers.OPTIONAL_UUID);
    public UUID visualCaster(){return entityData.get(VISUAL_CASTER).orElse(null);}
    /** Only the hostile cast target can be held; never the caster, allies or a tame companion. */
    public static boolean canDamage(ServerPlayer caster,LivingEntity target){
        if(caster==null||!FireSpellRules.hittable(caster,target)||target.isAlliedTo(caster)||caster.isAlliedTo(target))return false;
        if(target instanceof net.minecraft.world.entity.player.Player p&&(p.isCreative()||p.isSpectator()))return false;
        return !(target instanceof net.minecraft.world.entity.TamableAnimal pet&&pet.isTame());
    }
    /** 被抓的目标（只在服务端有意义；客户端靠位置同步）。 */
    @Nullable
    private UUID targetId;
    @Nullable
    private UUID casterId;
    /** 已经结算过第几下捏。 */
    private int crushes;
    private float crushDamage;
    private int burstAt = -1;
    private static final int GIANT_BURST_HEAT = 40;

    public TNFlameHandEntity(EntityType<? extends TNFlameHandEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
        this.noCulling = true;
    }

    @Override
    protected void defineSynchedData() {
        entityData.define(VISUAL_CASTER,java.util.Optional.empty());
        entityData.define(PHASE, 0.0F);
        entityData.define(TIER, 1);
        entityData.define(HEAT, 0.0F);
        entityData.define(AERIAL, false);
    }

    public float phase() { return entityData.get(PHASE); }
    public int tier() { return entityData.get(TIER); }
    public float heat() { return entityData.get(HEAT); }
    public boolean aerial() { return entityData.get(AERIAL); }

    @Nullable
    public LivingEntity target() {
        if (targetId == null || !(level() instanceof ServerLevel server)) {
            return null;
        }
        Entity e = server.getEntity(targetId);
        return e instanceof LivingEntity living && living.isAlive() ? living : null;
    }

    @Nullable
    public ServerPlayer caster() {
        if (casterId == null || !(level() instanceof ServerLevel server)) {
            return null;
        }
        return com.tnc.tnc.dungeon.mirror.MirrorSpellHand.owner(server,casterId);
    }

    /**
     * 生成一只火手。
     *
     * @param under 手出现的位置（目标脚下或目标脚下方的地面）
     */
    public static TNFlameHandEntity spawn(ServerLevel level, ServerPlayer caster, LivingEntity target,
                                          Vec3 under, int tier, float heat, boolean aerial) {
        TNFlameHandEntity hand = new TNFlameHandEntity(TNOrbEntities.FLAME_HAND.get(), level);
        hand.setPos(under.x, under.y, under.z);
        hand.entityData.set(TIER, Math.max(1, Math.min(5, tier)));
        hand.entityData.set(HEAT, Math.max(0.0F, heat));
        hand.entityData.set(AERIAL, aerial);
        hand.targetId = target==null?null:target.getUUID();
        hand.casterId = caster.getUUID();
        hand.entityData.set(VISUAL_CASTER,java.util.Optional.of(caster.getUUID()));
        // 每一下捏的伤害：档位线性 + 目标热量的一部分（热越高压得越狠）
        hand.crushDamage = (5.0F + 3.2F * hand.tier()) + heat * 0.06F;
        level.addFreshEntity(hand);
        level.playSound(null, under.x, under.y, under.z, SoundEvents.BLAZE_SHOOT,
                SoundSource.PLAYERS, 1.1F, 0.55F);
        return hand;
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide()) {
            return;
        }
        var caster=caster();if(caster==null||!caster.isAlive()||caster.isSpectator()){discard();return;}
        entityData.set(VISUAL_CASTER,java.util.Optional.of(caster.getUUID()));
        float age = burstAt >= 0 ? tickCount + 62 - burstAt : tickCount;
        float p = Math.min(1.0F, age / LIFE);
        entityData.set(PHASE, p);

        LivingEntity target = target();
        if (target != null&&!canDamage(caster,target))target=null;
        if(target!=null)entityData.set(HEAT,Math.max(heat(),Math.min(FireHeat.MAX,FireHeat.of(target))));
        if (tier() == 5 && burstAt < 0 && tickCount >= 14 && tickCount <= 61) attract(caster);
        if (target != null) {
            // 抓住：掌心位置固定在手的上方，目标被按在原地
            Vec3 grip = new Vec3(getX(), getY() + gripHeight(), getZ());
            var feet=grip.add(0,-target.getBbHeight()*.5,0);
            if (burstAt < 0 && p > 0.32F && p < 0.82F && target.getBbHeight() < (tier() == 5 ? 9 : 4.5F)
                    && target.getBbWidth() < (tier() == 5 ? 7 : 3.5F)
                    &&level().noCollision(target,target.getBoundingBox().move(feet.subtract(target.position())))) {
                target.setDeltaMovement(Vec3.ZERO);
                target.teleportTo(grip.x, grip.y - target.getBbHeight() * 0.5, grip.z);
                target.fallDistance=0;target.hurtMarked = true;
            }
        }

        // 三下收紧：分别在第 36 / 46 / 56 tick 结算
        int[] beats = {36, 46, 56};
        for (int i = 0; i < beats.length; i++) {
            if (burstAt < 0 && tickCount >= beats[i] && crushes <= i) {
                crushes = i + 1;
                crush(target);
            }
        }
        if (target != null) entityData.set(HEAT, Math.max(heat(), FireHeat.of(target)));

        // ★ 持续燃烧：每 2 tick 甩一把火星，档位越高越密（"疯狂"感靠这个堆出来）
        if (tickCount % 2 == 0 && level() instanceof ServerLevel sl) {
            int n = 3 + tier();
            double h = gripHeight() * (0.35 + 0.5 * (tickCount % 8) / 8.0);
            sl.sendParticles(net.minecraft.core.particles.ParticleTypes.FLAME,
                    getX(), getY() + h, getZ(), n, 0.45, 0.5, 0.45, 0.06);
            sl.sendParticles(net.minecraft.core.particles.ParticleTypes.SMALL_FLAME,
                    getX(), getY() + h * 0.5, getZ(), n, 0.5, 0.2, 0.5, 0.02);
            if (tier() >= 4 && tickCount % 6 == 0) {
                sl.sendParticles(net.minecraft.core.particles.ParticleTypes.LAVA,
                        getX(), getY() + h, getZ(), 4, 0.4, 0.4, 0.4, 0.1);
            }
        }

        if (burstAt < 0 && (tickCount >= 62 || tier() == 5 && tickCount >= 36 && heat() >= GIANT_BURST_HEAT)) {
            burst(target);
            burstAt = tickCount;
            entityData.set(PHASE, 62F / LIFE);
        }
        if (age >= LIFE) {
            discard();
        }
    }

    /** 掌心离地面的高度（档位越高手越大）。 */
    public double gripHeight() {
        return gripHeight(tier());
    }

    public static double gripHeight(int tier) { return tier >= 5 ? 5.4 : .9 + .42 * tier; }

    private void attract(ServerPlayer caster) {
        Vec3 center = position().add(0, Math.min(2.2, gripHeight() * .5), 0);
        LivingEntity held = target();
        for (var enemy : level().getEntitiesOfClass(LivingEntity.class, new AABB(center, center).inflate(12),
                living -> living != held && canDamage(caster, living))) {
            Vec3 inward = center.subtract(enemy.getBoundingBox().getCenter());
            double distance = inward.length();
            if (distance <= 1.6 || distance > 12 || !caster.hasLineOfSight(enemy)) continue;
            Vec3 force = inward.scale(.085 / distance);
            Vec3 velocity = enemy.getDeltaMovement().scale(.82).add(force);
            if (velocity.lengthSqr() > .45 * .45) velocity = velocity.normalize().scale(.45);
            enemy.setDeltaMovement(velocity);
            enemy.hurtMarked = true;
            enemy.fallDistance = 0;
        }
    }

    private void crush(@Nullable LivingEntity target) {
        if (!(level() instanceof ServerLevel server)) {
            return;
        }
        Vec3 at = new Vec3(getX(), getY() + gripHeight() * 0.6, getZ());
        if (target != null && target.isAlive()) {
            TNFireRework.detonationDamage(caster(), target, crushDamage);
            target.setSecondsOnFire(4);
            FireHeat.add(target, caster(), 14.0F + 3.0F * tier());
        }
        FireReworkEffects.emit(server,FireReworkEffects.Kind.HAND_CRUSH,at,Vec3.ZERO,1.3F+.35F*tier(),Math.min(1,.25F+heat()/100F),18,caster());
        // 只伤"被捏的那一个"以外的？不：捏的时候会挤压周围，小范围擦伤
        for (LivingEntity other : server.getEntitiesOfClass(LivingEntity.class,
                new AABB(at, at).inflate(tier() == 5 ? 5.8 : 1.4 + 0.3 * tier()), e -> e != target && canDamage(caster(),e))) {
            TNFireRework.detonationDamage(caster(), other, crushDamage * 0.25F);
        }
        server.sendParticles(net.minecraft.core.particles.ParticleTypes.LAVA,
                at.x, at.y, at.z, 18, 0.35, 0.5, 0.35, 0.25);
        // ★ 力量感：每一下捏都抖屏（档位越高抖得越狠），并在掌心炸出一圈冲击
        com.tnc.tnc.magic.TNShockwaveEntity.blast(server, at, 1.3D + 0.35D * tier(),
                0F, caster(), 0F).setInvisible(true);
        // 第三下（最后一下）额外在地上泼一片岩浆：捏碎之后脚下就是火海
        if (crushes >= 3 && tier() >= 2) {
            TNLavaFieldEntity.spawn(server, new Vec3(getX(), getY() + 0.2, getZ()), caster(),
                    crushDamage * 0.35F, 1.6D + 0.25D * tier(), 60);
        }
        server.playSound(null, at.x, at.y, at.z, SoundEvents.BLAZE_HURT, SoundSource.PLAYERS,
                1.4F, 0.62F + 0.06F * crushes);
        server.playSound(null, at.x, at.y, at.z, SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS,
                0.55F, 1.45F);
    }

    private void burst(@Nullable LivingEntity target) {
        if (!(level() instanceof ServerLevel server)) {
            return;
        }
        Vec3 at = new Vec3(getX(), getY() + gripHeight() * 0.6, getZ());
        float heat = this.heat();
        double radius = (tier() == 5 ? 8.5 : 2.4 + 0.55 * tier()) + heat * .006;
        float damage = (8.0F + 5.0F * tier()) * (1.0F + heat / 110.0F);
        ServerPlayer caster = caster();
        for (LivingEntity victim : server.getEntitiesOfClass(LivingEntity.class,
                new AABB(at, at).inflate(radius + 1.0), e -> canDamage(caster,e))) {
            double dist = victim.position().distanceTo(at);
            float falloff = (float) Math.max(0.25, 1.0 - dist / (radius + 1.0));
            TNFireRework.detonationDamage(caster, victim, damage * falloff);
            victim.setSecondsOnFire(6);
            FireHeat.add(victim, caster(), 10.0F);
        }
        com.tnc.tnc.magic.TNShockwaveEntity.blast(server, at, radius * 1.15D,
                0F, caster(), 0F).setInvisible(true);
        TNLavaFieldEntity.spawn(server, new Vec3(getX(), getY() + 0.2, getZ()), caster(),
                damage * 0.30F, 2.0D + 0.3D * tier(), 100);
        FireReworkEffects.emit(server,FireReworkEffects.Kind.HEAT_BURST,at,Vec3.ZERO,(float)radius,Math.min(1,.35F+heat/100F),32,caster);
        server.sendParticles(net.minecraft.core.particles.ParticleTypes.EXPLOSION_EMITTER,
                at.x, at.y, at.z, 2, 0.4, 0.4, 0.4, 0.0);
        server.sendParticles(net.minecraft.core.particles.ParticleTypes.FLAME,
                at.x, at.y, at.z, 60, radius * 0.4, radius * 0.3, radius * 0.4, 0.35);
        server.playSound(null, at.x, at.y, at.z, SoundEvents.GENERIC_EXPLODE,
                SoundSource.PLAYERS, 2.0F, 0.72F);
        server.playSound(null, at.x, at.y, at.z, SoundEvents.LIGHTNING_BOLT_THUNDER,
                SoundSource.PLAYERS, 0.9F, 1.6F);
    }

    @Override public boolean shouldBeSaved(){return false;}

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        if (tag.hasUUID("Target")) {
            targetId = tag.getUUID("Target");
        }
        crushes = tag.getInt("Crushes");
        crushDamage = tag.getFloat("CrushDamage");
        entityData.set(TIER, Math.max(1, tag.getInt("Tier")));
        entityData.set(HEAT, tag.getFloat("Heat"));
        entityData.set(AERIAL, tag.getBoolean("Aerial"));
        if (tag.hasUUID("Caster")) {
            casterId = tag.getUUID("Caster");
        }
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        if (targetId != null) {
            tag.putUUID("Target", targetId);
        }
        if (casterId != null) {
            tag.putUUID("Caster", casterId);
        }
        tag.putInt("Crushes", crushes);
        tag.putFloat("CrushDamage", crushDamage);
        tag.putInt("Tier", tier());
        tag.putFloat("Heat", heat());
        tag.putBoolean("Aerial", aerial());
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double distance) {
        return distance < 96.0 * 96.0;
    }
}
