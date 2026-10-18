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
 * <b>燃烧链</b>的施法起手表现：一团<b>把自己围起来的火</b>（作者 2026-10-11 要求）。
 *
 * <p>作者原话：「修改燃烧链的释放动画，动画从火焰粒子变为出现火焰围绕，
 * 高等级法术的火焰规模大于低等级的，<b>采用真实建模</b>」。
 *
 * <h2>为什么另开一个实体，而不是继续用 JSON 里的 {@code cast.particles}</h2>
 * 那条 {@code flame CIRCLE FEET} 是 spell_engine 在起手时撒的<b>原版粒子</b>，
 * 只能撒点、不能画模型 ⇒ 要"真实建模"就只能在客户端用几何体画。
 * 而几何体要么挂在实体上（{@code EntityRenderer}）、要么挂在玩家身上（{@code RenderPlayerEvent}）——
 * 这里选实体：位置跟着施法者走，档位/存活期走 {@code SynchedEntityData} 同步，
 * 和 {@link TNSkyfallEntity} / {@code TNMagicCircleEntity} 是同一套做法 ✓。
 *
 * <h2>生长规则（档位越高规模越大）</h2>
 * 渲染器按 {@link #tier()} 决定火焰圈的<b>数量 / 半径 / 高度 / 火苗粗细</b>，
 * 见 {@code TNBurnAuraRenderer} 顶部那张表；本类只管"跟着谁、活多久、第几档"。
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
            // 画由 TNBurnAuraRenderer 负责，服务端没有窗口期要算 ✓
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
