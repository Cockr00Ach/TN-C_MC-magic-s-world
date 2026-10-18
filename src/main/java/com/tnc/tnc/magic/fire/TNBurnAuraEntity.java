package com.tnc.tnc.magic.fire;

import com.tnc.tnc.magic.SpellCatalog;
import com.tnc.tnc.magic.TNOrbEntities;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;

/**
 * <b>燃烧链</b>的施法起手表现：一圈<b>把自己围起来的火</b>。
 *
 * <p>作者 2026-10-18：「燃烧链的特效改用粒子效果，不用真实建模了」——
 * 于是这个实体从"画几何体"退回成<b>纯粒子发射器</b>：它自己不画任何东西
 * （客户端挂的是 {@code TNParticleOnlyRenderer} 空渲染器），
 * 每 tick 在服务端往施法者周围撒真实粒子，档位越高圈越大、火越旺 ✓
 *
 * <h2>为什么还要一个实体</h2>
 * 几何体没了，但"跟着施法者走、持续 1.2 秒、按档位变规模"这三件事还是要有人做：
 * 一次 RELEASE 只回一次事件，光靠事件只能撒一下 ✗ 所以留一个轻量实体专门打粒子 ✓
 *
 * <h2>生长规则（档位越高规模越大）</h2>
 * 圈半径 / 火苗高度 / 粒子数量都按 {@link #tier()} 递增，见 {@link #burn(ServerLevel)}。
 *
 * <p>⚠️ 纯表现实体：不存档（{@link #shouldBeSaved()} false）、不可选中、无碰撞。
 */
public final class TNBurnAuraEntity extends Entity {

    /**
     * 一团火活多久（tick）。
     *
     * <p>2026-10-18 起这团火是<b>放出瞬间</b>点起来的（SPELL_CAST 对燃烧链只以 RELEASE 到达，
     * 起手那 0.6 秒里引擎不回事件），所以时长按"放出后还能看多久"给：
     * 24 tick = 1.2 秒，够看清一圈火绕上来又不至于挡视线 ✓
     */
    public static final int AURA_LIFE_TICKS = 24;

    private static final EntityDataAccessor<Integer> DATA_TIER =
            SynchedEntityData.defineId(TNBurnAuraEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_LIFE =
            SynchedEntityData.defineId(TNBurnAuraEntity.class, EntityDataSerializers.INT);

    private LivingEntity caster;

    public TNBurnAuraEntity(EntityType<? extends TNBurnAuraEntity> type, Level level) {
        super(type, level);
        noPhysics = true;
        setNoGravity(true);
    }

    /** 由起手钩子调用（只在服务端）。 */
    public static TNBurnAuraEntity cast(ServerLevel level, LivingEntity owner, int tier, int lifeTicks) {
        TNBurnAuraEntity aura = new TNBurnAuraEntity(TNOrbEntities.BURN_AURA.get(), level);
        aura.caster = owner;
        aura.entityData.set(DATA_TIER, Math.max(1, Math.min(5, tier)));
        aura.entityData.set(DATA_LIFE, Math.max(1, lifeTicks));
        aura.setPos(owner.getX(), owner.getY(), owner.getZ());
        level.addFreshEntity(aura);
        return aura;
    }

    /**
     * 施法时点一团火：如果这位施法者身上还没有火，就点一团。
     *
     * <p>为什么先查一遍：不管引擎那边一次施法回几次事件（RELEASE 现在只回一次，
     * 但 CHANNEL 之类的路径可能每 tick 回一次），这里都能兜住 ——
     * 不查就会叠出好几团火（肉眼是白到看不清的一团，还白烧几份渲染开销）✗
     */
    public static boolean ensureAura(ServerPlayer player, SpellCatalog.Entry entry) {
        if (player == null || entry == null) {
            return false;
        }
        if (entry.chain() != SpellCatalog.Chain.BURN) {
            return false;
        }
        ServerLevel level = player.serverLevel();
        boolean already = !level.getEntitiesOfClass(TNBurnAuraEntity.class,
                player.getBoundingBox().inflate(3.0D), a -> a.caster == player).isEmpty();
        if (already) {
            return false;
        }
        cast(level, player, entry.tier(), AURA_LIFE_TICKS);
        return true;
    }

    /** 1~5 档，渲染器据此决定火焰规模。 */
    public int tier() {
        return entityData.get(DATA_TIER);
    }

    public int life() {
        return entityData.get(DATA_LIFE);
    }

    @Override
    protected void defineSynchedData() {
        entityData.define(DATA_TIER, 1);
        entityData.define(DATA_LIFE, AURA_LIFE_TICKS);
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide()) {
            return;
        }
        // 施法者没了（下线/死亡/换维度）就跟着消失，别留一团野火 ✗
        if (caster == null || !caster.isAlive() || caster.level() != level()
                || tickCount > life()) {
            discard();
            return;
        }
        // 每 tick 贴到施法者脚下：起手时人是能走的，火得跟着走 ✓
        setPos(caster.getX(), caster.getY(), caster.getZ());
        if (level() instanceof ServerLevel server) {
            burn(server);
        }
    }

    /**
     * 往施法者周围撒一圈火粒子 —— <b>这就是燃烧链的全部表现</b>（作者 2026-10-18 改回粒子）。
     *
     * <p>三层：
     * <ol>
     *   <li><b>贴着地面的火圈</b>：{@code 5 + tier*3} 颗（8~20），半径 {@code 0.55 + tier*0.16}（0.71~1.35 格）</li>
     *   <li><b>往上窜的火舌</b>：{@code 3 + tier*2} 颗（5~13），撒在整个圆柱体内，高度到 {@code 0.85 + tier*0.28}（1.13~2.25 格）</li>
     *   <li><b>t3 起再加熔岩火星</b>：{@code tier-2} 颗（1~3），会往下掉 ⇒ 看得出"火在往上燎"</li>
     * </ol>
     *
     * <p>火圈整体按 {@code tickCount * 0.35} 的角速度慢慢转，不是死的 ✓
     */
    private void burn(ServerLevel server) {
        int tier = tier();
        double radius = 0.55D + tier * 0.16D;
        double height = 0.85D + tier * 0.28D;

        int ring = 5 + tier * 3;
        for (int i = 0; i < ring; i++) {
            double a = (i + tickCount * 0.35D) * Math.PI * 2.0D / ring;
            server.sendParticles(net.minecraft.core.particles.ParticleTypes.FLAME,
                    getX() + Math.cos(a) * radius, getY() + 0.10D + Math.random() * 0.45D,
                    getZ() + Math.sin(a) * radius,
                    1, 0.02D, 0.05D, 0.02D, 0.008D);
        }

        int rising = 3 + tier * 2;
        for (int i = 0; i < rising; i++) {
            double a = Math.random() * Math.PI * 2.0D;
            double r = radius * 0.85D * Math.sqrt(Math.random());
            server.sendParticles(i % 3 == 0
                            ? net.minecraft.core.particles.ParticleTypes.SMALL_FLAME
                            : net.minecraft.core.particles.ParticleTypes.FLAME,
                    getX() + Math.cos(a) * r, getY() + 0.15D + Math.random() * height,
                    getZ() + Math.sin(a) * r,
                    1, 0.08D, 0.18D, 0.08D, 0.010D);
        }

        if (tier >= 3) {
            server.sendParticles(net.minecraft.core.particles.ParticleTypes.LAVA,
                    getX(), getY() + 0.30D + Math.random() * height, getZ(),
                    tier - 2, radius * 0.70D, 0.30D, radius * 0.70D, 0.0D);
        }
    }

    @Override
    public boolean shouldBeSaved() {
        return false;
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        // 不能调 super（直接继承 Entity 时它是抽象的）；纯表现实体不读档
        discard();
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        // 不持久化，没有要写的东西
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    public boolean isAttackable() {
        return false;
    }

    @Override
    public net.minecraft.world.phys.AABB getBoundingBoxForCulling() {
        // 火最多烧到 2 格高、1 格多宽，包围盒给宽一点免得转头就消失
        return super.getBoundingBoxForCulling().inflate(3.0D, 2.5D, 3.0D);
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double distanceSqr) {
        return distanceSqr < 96.0D * 96.0D;
    }
}
