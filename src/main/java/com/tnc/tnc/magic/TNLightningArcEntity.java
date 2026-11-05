package com.tnc.tnc.magic;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkHooks;

/**
 * 一段<b>横着走的雷电</b>：从实体位置拉到 {@code position + (dx,dy,dz)}，活几 tick 就散 ✓。
 *
 * <h2>为什么单独做一个实体</h2>
 * 2026-11-05 作者："闪电链的 t1 和 t3 怎么没特效一样" ——
 * 这两档原来只有引擎给的几颗 {@code electric_arc_a} 粒子（t1 甚至连投射物都没有，
 * 是 {@code CURSOR} 瞬间结算）✗。要"看得见的一道电"就得有几何，
 * 而几何必须有个地方挂（tickCount / 种子 / 存活时间），所以开一个极轻的实体 ✓。
 *
 * <p>它能同时服务好几处，不用每处各写一套：
 * <ul>
 *   <li>t1 电花：手 → 准星落点，短、细、亮 ✓</li>
 *   <li>t3 雷击：手 → 落点，粗一点，把"雷从哪来"接上 ✓</li>
 *   <li>雷锲链：封印节点 ↔ 节点之间的电弧 ✓</li>
 * </ul>
 *
 * <p>只同步三件事：端点偏移、粗细、主题色/寿命 —— 都在 {@link SynchedEntityData} 里，
 * 不需要每 tick 发包（它自己也<b>不动</b>）✓。纯表现，不存档、不可选、无碰撞 ✓。
 */
public final class TNLightningArcEntity extends Entity {

    private static final EntityDataAccessor<Float> DATA_DX =
            SynchedEntityData.defineId(TNLightningArcEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_DY =
            SynchedEntityData.defineId(TNLightningArcEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_DZ =
            SynchedEntityData.defineId(TNLightningArcEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_WIDTH =
            SynchedEntityData.defineId(TNLightningArcEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Integer> DATA_THEME =
            SynchedEntityData.defineId(TNLightningArcEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_LIFE =
            SynchedEntityData.defineId(TNLightningArcEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> DATA_IMPACT =
            SynchedEntityData.defineId(TNLightningArcEntity.class, EntityDataSerializers.BOOLEAN);

    public TNLightningArcEntity(EntityType<? extends TNLightningArcEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
        this.setNoGravity(true);
    }

    @Override
    protected void defineSynchedData() {
        this.entityData.define(DATA_DX, 0.0F);
        this.entityData.define(DATA_DY, 0.0F);
        this.entityData.define(DATA_DZ, 0.0F);
        this.entityData.define(DATA_WIDTH, 0.08F);
        this.entityData.define(DATA_THEME, 2);
        this.entityData.define(DATA_LIFE, 8);
        this.entityData.define(DATA_IMPACT, true);
    }

    /**
     * @param from   起点（手/法杖位置）
     * @param to     终点（准星落点）
     * @param theme  {@code LightningTheme} 的 0/1/2/3
     * @param life   存活 tick（6~12 比较像"啪"的一下 ✓）
     * @param width  主干粗细（格）
     * @param impact 终点要不要炸一圈地光/雷网 ✓
     */
    public void configure(Vec3 from, Vec3 to, int theme, int life, float width, boolean impact) {
        this.setPos(from.x, from.y, from.z);
        this.entityData.set(DATA_DX, (float) (to.x - from.x));
        this.entityData.set(DATA_DY, (float) (to.y - from.y));
        this.entityData.set(DATA_DZ, (float) (to.z - from.z));
        this.entityData.set(DATA_THEME, theme);
        this.entityData.set(DATA_LIFE, life);
        this.entityData.set(DATA_WIDTH, width);
        this.entityData.set(DATA_IMPACT, impact);
    }

    public double dx() {
        return this.entityData.get(DATA_DX);
    }

    public double dy() {
        return this.entityData.get(DATA_DY);
    }

    public double dz() {
        return this.entityData.get(DATA_DZ);
    }

    public float width() {
        return this.entityData.get(DATA_WIDTH);
    }

    public int theme() {
        return this.entityData.get(DATA_THEME);
    }

    public int life() {
        return Math.max(1, this.entityData.get(DATA_LIFE));
    }

    public boolean impact() {
        return this.entityData.get(DATA_IMPACT);
    }

    /** 终点（世界坐标）✓。 */
    public Vec3 end() {
        return this.position().add(dx(), dy(), dz());
    }

    @Override
    public void tick() {
        super.tick();
        if (!this.level().isClientSide() && this.tickCount > life()) {
            this.discard();
        }
    }

    /** 端点常常在实体盒子外 20 格 ⇒ 不撑开剔除盒的话，平视会被整个裁掉 ✗。 */
    @Override
    public AABB getBoundingBoxForCulling() {
        double len = Math.sqrt(dx() * dx() + dy() * dy() + dz() * dz());
        return this.getBoundingBox().inflate(len + 2.0D, len + 2.0D, len + 2.0D);
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double distance) {
        return distance < 65536.0D;
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        // 纯表现，不存档 ✓
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        // 同上
    }

    @Override
    public Packet<ClientGamePacketListener> getAddEntityPacket() {
        return NetworkHooks.getEntitySpawningPacket(this);
    }
}
