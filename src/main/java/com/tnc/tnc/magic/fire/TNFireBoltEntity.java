package com.tnc.tnc.magic.fire;

import com.tnc.tnc.TNMod;
import com.tnc.tnc.combat.DownedCombat;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.Optional;

/**
 * 火球链的<b>自有投射物</b>（照 {@code TNWaterBoltEntity} 的结构写）。
 *
 * <h2>为什么不用引擎的 PROJECTILE</h2>
 * 灼烧（{@link TNScorch 焚身}）的每秒伤害 = <b>那一下的实际命中伤害 × 10%</b>，
 * 所以命中时必须知道「是哪个法术、打了多少」。引擎的投射物在 {@code LivingHurtEvent}
 * 里只是一个无名的魔法伤害，拿不到法术 id —— 于是这条链改成自有实体：
 * <b>伤害自己算、命中自己判定、焚身自己挂</b>，归因零误差。
 *
 * <h2>判定方式（照抄水系，别自己发明）</h2>
 * 每 tick 用「线段 × 包围盒」扫掠：先 {@code clip} 撞墙，再对线上所有敌人做盒体裁剪，
 * 取<b>离出发点最近</b>的那个 —— 高速飞行也不会穿人。
 */
public final class TNFireBoltEntity extends Projectile {

    /** 服务端定形、同步给客户端（客户端要靠它画大小）。 */
    private static final EntityDataAccessor<String> SPELL =
            SynchedEntityData.defineId(TNFireBoltEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<Float> RADIUS =
            SynchedEntityData.defineId(TNFireBoltEntity.class, EntityDataSerializers.FLOAT);

    /** 这一发的伤害（服务端算好；客户端不用）。 */
    private float damage;
    /** 还能飞多远（格）。 */
    private double remaining = 40.0D;
    /** 命中挂的焚身是不是 II 级。 */
    private boolean heavyScorch;
    /** 命中后是否在落点留下熔岩地（t3 熔岩火球起才有）。 */
    private boolean lavaField;
    /** 命中后的爆炸半径（0 = 不爆炸；t4 熔岳天倾的那些火球才有）。 */
    private double blastRadius;

    public TNFireBoltEntity(EntityType<? extends TNFireBoltEntity> type, Level level) {
        super(type, level);
        setNoGravity(true);
    }

    /** 由法术入口调用：定好法术、伤害、初速。 */
    public void configure(LivingEntity owner, FireSpellRules.Bolt bolt, String spellPath,
                          Vec3 at, Vec3 velocity) {
        setOwner(owner);
        entityData.set(SPELL, spellPath);
        entityData.set(RADIUS, bolt.radius());
        setPos(at);
        setDeltaMovement(velocity);
        damage = FireSpellRules.damage(bolt, FireSpellRules.power(owner));
        heavyScorch = bolt.heavyScorch();
        lavaField = bolt.lavaField();
        blastRadius = bolt.blastRadius();
        remaining = bolt.range();
    }

    public String spellPath() {
        return entityData.get(SPELL);
    }

    public float radius() {
        return entityData.get(RADIUS);
    }

    /**
     * 由 {@code TnSpellMechanics.onSpellCast} 转调：这一发是火球链的就放出去。
     *
     * <p>和 {@code TNWaterBoltEntity} / {@code TNWaterFieldEntity} 的 {@code cast} 同一个约定：
     * <b>不是本链的法术就返回 false 让别的链去处理</b>，所以派发处可以无脑全调一遍。
     *
     * @return 真的放出去了才 true
     */
    public static boolean cast(ServerPlayer player, ResourceLocation spellId) {
        if (player == null || spellId == null || !spellId.getNamespace().equals(TNMod.MODID)) {
            return false;
        }
        FireSpellRules.Bolt bolt = FireSpellRules.bolt(spellId.getPath());
        if (bolt == null) {
            return false;
        }
        Vec3 look = player.getLookAngle();
        Vec3 muzzle = FireSpellRules.muzzle(player, 0.6D);
        Vec3 right = FireSpellRules.right(look);
        for (int i = 0; i < bolt.launches(); i++) {
            // 连珠：横向错开一点，免得三发叠成一根。单发时 spread = 0，行为不变
            double spread = bolt.launches() > 1
                    ? (i - (bolt.launches() - 1) / 2.0D) * 0.055D
                    : 0.0D;
            Vec3 velocity = look.scale(1.3D).add(right.scale(spread));
            TNFireBoltEntity boltEntity = new TNFireBoltEntity(
                    com.tnc.tnc.magic.TNOrbEntities.FIRE_BOLT.get(), player.level());
            boltEntity.configure(player, bolt, spellId.getPath(), muzzle, velocity);
            player.level().addFreshEntity(boltEntity);
        }
        return true;
    }

    @Override
    protected void defineSynchedData() {
        entityData.define(SPELL, "");
        entityData.define(RADIUS, 0.35F);
    }

    @Override
    public void tick() {
        super.tick();

        // ---------------- 客户端：只负责好看 ----------------
        if (level().isClientSide) {
            if (tickCount % 2 == 0) {
                Vec3 dir = getDeltaMovement().normalize();
                double r = radius();
                for (int i = 0; i < 4; i++) {
                    double angle = tickCount * 0.6D + i * Math.PI / 2.0D;
                    level().addParticle(i % 2 == 0 ? ParticleTypes.FLAME : ParticleTypes.SMALL_FLAME,
                            getX() + Math.cos(angle) * r * 0.7D,
                            getY() + Math.sin(angle) * r * 0.7D,
                            getZ() + Math.cos(angle) * r * 0.7D,
                            -dir.x * 0.05D, 0.02D, -dir.z * 0.05D);
                }
                if (tickCount % 6 == 0) {
                    level().addParticle(ParticleTypes.LAVA, getX(), getY(), getZ(), 0, 0, 0);
                }
            }
            setPos(position().add(getDeltaMovement()));
            return;
        }

        // ---------------- 服务端：有效性 ----------------
        if (!(getOwner() instanceof LivingEntity owner) || !owner.isAlive() || tickCount > 100 || remaining <= 0
                || owner instanceof net.minecraft.world.entity.player.Player p
                        && DownedCombat.isDowned(p)
                || owner.level() != level()) {
            discard();
            return;
        }

        ServerLevel server = (ServerLevel) level();
        Vec3 from = position();
        Vec3 motion = getDeltaMovement();
        Vec3 to = from.add(motion);
        if (!server.hasChunkAt(BlockPos.containing(to))) {
            discard();
            return;
        }

        // 先看会不会撞墙
        BlockHitResult wall = server.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE, this));
        Vec3 limit = wall.getType() == HitResult.Type.MISS ? to : wall.getLocation();

        // 再在线段上找最近的敌人（盒体裁剪，别用球心距离）
        LivingEntity hit = null;
        Vec3 impact = limit;
        double closest = from.distanceToSqr(limit) + 1e-7D;
        for (LivingEntity target : server.getEntitiesOfClass(LivingEntity.class,
                new AABB(from, to).inflate(0.4D), t -> FireSpellRules.enemy(owner, t))) {
            AABB box = target.getBoundingBox().inflate(0.15D);
            Optional<Vec3> point = box.contains(from) ? Optional.of(from) : box.clip(from, limit);
            if (point.isPresent() && point.get().distanceToSqr(from) < closest) {
                closest = point.get().distanceToSqr(from);
                hit = target;
                impact = point.get();
            }
        }

        if (hit != null || wall.getType() != HitResult.Type.MISS) {
            if (hit != null) {
                boolean hurt = hit.hurt(server.damageSources().indirectMagic(this, owner), damage);
                // 焚身：命中就挂（基数 = 这一发实际打出的伤害）。
                // 已经挂了同级或更高的目标由 TNScorch 自己判「跳过」——这里不用管"不刷新"
                if (hurt && owner instanceof ServerPlayer caster) {
                    TNScorch.apply(hit, caster, damage, heavyScorch);
                }
            }
            server.sendParticles(ParticleTypes.FLAME, impact.x, impact.y, impact.z, 14, 0.25D, 0.25D, 0.25D, 0.06D);
            server.sendParticles(ParticleTypes.LAVA, impact.x, impact.y, impact.z, 3, 0.2D, 0.2D, 0.2D, 0.0D);
            // 熔岩地：在落点**下方**留一块持续灼烧的地面（t3 熔岩火球起才有）。
            // 它的每秒伤害和焚身是两条独立结算，会同时触发 —— 这是作者明确要的 ✓
            if (lavaField) {
                TNLavaFieldEntity.spawn(server, groundBelow(server, impact),
                        owner instanceof ServerPlayer caster ? caster : null,
                        FireSpellRules.lavaFieldPerSecond(damage),
                        FireSpellRules.LAVA_FIELD_RADIUS, FireSpellRules.LAVA_FIELD_LIFE_TICKS);
            }
            // 爆炸（t4 熔岳天倾的火球才有）：对范围内敌人造成**火球伤害的一半**。
            // 直击目标也会吃到（先被直接命中、再被爆炸波及）—— 爆炸本来就该是这样
            if (blastRadius > 0.0D) {
                float blast = FireSpellRules.blastDamage(damage);
                for (LivingEntity victim : server.getEntitiesOfClass(LivingEntity.class,
                        FireSpellRules.uprightArea(impact, blastRadius, 2.0D),
                        t -> FireSpellRules.enemy(owner, t))) {
                    victim.invulnerableTime = 0;   // 直击刚把这个设成 20，不清的话爆炸会被吞
                    victim.hurt(server.damageSources().indirectMagic(this, owner), blast);
                }
                server.sendParticles(ParticleTypes.EXPLOSION, impact.x, impact.y, impact.z, 1, 0.0D, 0.0D, 0.0D, 0.0D);
                server.sendParticles(ParticleTypes.LAVA, impact.x, impact.y, impact.z, 20,
                        blastRadius * 0.4D, 0.4D, blastRadius * 0.4D, 0.2D);
            }
            discard();
            return;
        }

        setPos(to);
        remaining -= motion.length();
    }

    /** 熔岩地要贴地：从命中点往下找一层可站立的平面，找不到就退回命中点本身。 */
    private Vec3 groundBelow(ServerLevel level, Vec3 impact) {
        Vec3 down = impact.add(0.0D, -4.0D, 0.0D);
        BlockHitResult floor = level.clip(new ClipContext(impact, down, ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE, this));
        Vec3 at = floor.getType() == HitResult.Type.MISS ? impact : floor.getLocation();
        return new Vec3(at.x, at.y + 0.06D, at.z);
    }

    @Override
    public boolean shouldBeSaved() {
        return false;
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        discard();
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
    }
}
