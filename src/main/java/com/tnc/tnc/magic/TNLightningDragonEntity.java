package com.tnc.tnc.magic;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;

/**
 * <b>雷龙</b> —— 一条"用雷电勾线出来"的龙（纯表现实体）。
 *
 * <h2>作者 2026-11-05 的要求</h2>
 * <blockquote>"再做一条雷龙，尽管堆砌模型块数，但是我希望他是实体建模并且他没有身体，
 * 怎么说他是一条用雷电勾线出来的龙，我要你展现龙的威风，什么龙须什么头都得会动，
 * 然后在 t5 砸下雷电之后，砸中的地面有龙直冲云霄，对了，这些特效在游戏里的体积
 * 一定要和现在的大小差不多，甚至更大，只要你有足够细节"</blockquote>
 *
 * <p>所以它是：
 * <ul>
 *   <li><b>实体建模</b> ✓ —— 有自己的实体类型、自己的渲染器，模型由<b>几百个方块</b>拼出来
 *       （头骨、吻、下颚、牙、眼、三对龙角、两根长龙须、鬃刺、脊椎、肋骨、四爪、尾鳍），
 *       不是几颗粒子 ✗；</li>
 *   <li><b>没有身体</b> ✓ —— 躯干中空：只有一条闪电脊线 ＋ 两侧肋骨弧 ＋ 一节节悬浮的脊椎骨块，
 *       从肋骨之间能直接看穿到另一边 ✓；</li>
 *   <li><b>雷电勾线</b> ✓ —— 脊线、龙须、龙角、肋骨边缘都额外描了锯齿电弧
 *       （{@code LightningDragonGeometry}），远看是一团电，近看才是龙；</li>
 *   <li><b>会动</b> ✓ —— 头会左右摆/俯仰、下颚会张合、龙须按正弦波飘、龙角会颤、
 *       鬃刺和四爪都会随飞行摆动（全在渲染器里按 {@code age} 算，不需要动画文件）；</li>
 *   <li><b>大</b> ✓ —— 默认从头到尾 45 格以上、龙角到龙爪 8 格宽；t5 用 1.15 倍会更长，
 *       和"神在投篮"那 29 格高的神是一个量级甚至更大 ✓。</li>
 * </ul>
 *
 * <h2>它从哪来</h2>
 * t5 的雷砸到地面之后：{@code TnSpellMechanics}（天雷）或 {@link TNLightningStrikeEntity}
 * （神在投篮那颗大雷球落地）在落点生成它 ✓；{@code DATA_DELAY} 是"先蓄 14 tick 雷，
 * 再破土冲天"的延迟（见渲染器）✓。飞行轨迹（螺旋直冲云霄）由渲染器按 {@code age} 算，
 * 实体本身只钉在落点、负责生命周期和声音 —— 这样客户端不需要每 tick 收位置包 ✓。
 */
public class TNLightningDragonEntity extends Entity {

    /** 尺寸倍数 ×100（1.0 = 设计尺寸）。 */
    private static final EntityDataAccessor<Integer> DATA_SCALE =
            SynchedEntityData.defineId(TNLightningDragonEntity.class, EntityDataSerializers.INT);
    /** 颜色主题（{@code LightningTheme}）。 */
    private static final EntityDataAccessor<Integer> DATA_THEME =
            SynchedEntityData.defineId(TNLightningDragonEntity.class, EntityDataSerializers.INT);
    /** 总共活多少 tick。 */
    private static final EntityDataAccessor<Integer> DATA_LIFE =
            SynchedEntityData.defineId(TNLightningDragonEntity.class, EntityDataSerializers.INT);
    /** 形状随机种子（同步 ⇒ 客户端每帧画的是同一条龙 ✓）。 */
    private static final EntityDataAccessor<Integer> DATA_SEED =
            SynchedEntityData.defineId(TNLightningDragonEntity.class, EntityDataSerializers.INT);
    /** 出生后先"蓄力"多少 tick 再破土（这段时间只画地面的雷光 ✓）。 */
    private static final EntityDataAccessor<Integer> DATA_DELAY =
            SynchedEntityData.defineId(TNLightningDragonEntity.class, EntityDataSerializers.INT);

    /**
     * 形态（2026-11-05 作者："登神链我希望施法之后持续时间内会有我们的雷龙缠绕着身上一直游走"）。
     * <ul>
     *   <li>{@link #MODE_ERUPT} 破土冲天（天雷/神在投篮 t5）✓</li>
     *   <li>{@link #MODE_COIL} 盘在施法者身上游走（登神链 t5）✓</li>
     * </ul>
     */
    private static final EntityDataAccessor<Integer> DATA_MODE =
            SynchedEntityData.defineId(TNLightningDragonEntity.class, EntityDataSerializers.INT);
    /** 缠绕形态要跟着谁（UUID；只在这条实体自己身上用，不用每 tick 同步位置包 ✓）。 */
    private static final EntityDataAccessor<java.util.Optional<java.util.UUID>> DATA_OWNER =
            SynchedEntityData.defineId(TNLightningDragonEntity.class, EntityDataSerializers.OPTIONAL_UUID);

    public static final int MODE_ERUPT = 0;
    public static final int MODE_COIL = 1;

    /** 已经吼过没有（服务端字段就够 ✓）。 */
    private boolean roared = false;

    public TNLightningDragonEntity(EntityType<? extends TNLightningDragonEntity> type, Level level) {
        super(type, level);
        this.setNoGravity(true);
        this.noPhysics = true;
    }

    /**
     * 生成后调用一次。
     *
     * @param scale 尺寸倍数（1.0 = 45 格长；t5 建议 1.1~1.25）
     * @param life  总共活多少 tick（建议 150）
     * @param theme {@code LightningTheme} 主题
     * @param delay 破土前的蓄力 tick（天雷用 14，落地即出用 0）
     */
    public void configure(double scale, int life, int theme, int delay) {
        this.entityData.set(DATA_SCALE, (int) Math.round(scale * 100.0D));
        this.entityData.set(DATA_LIFE, Math.max(20, life));
        this.entityData.set(DATA_THEME, Math.max(0, theme));
        this.entityData.set(DATA_DELAY, Math.max(0, delay));
        this.entityData.set(DATA_SEED, this.random.nextInt());
    }

    public double scale() {
        return this.entityData.get(DATA_SCALE) / 100.0D;
    }

    public int theme() {
        return this.entityData.get(DATA_THEME);
    }

    public int life() {
        return this.entityData.get(DATA_LIFE);
    }

    public int seed() {
        return this.entityData.get(DATA_SEED);
    }

    public int delay() {
        return this.entityData.get(DATA_DELAY);
    }

    public int mode() {
        return this.entityData.get(DATA_MODE);
    }

    public boolean coil() {
        return this.mode() == MODE_COIL;
    }

    public java.util.UUID ownerId() {
        return this.entityData.get(DATA_OWNER).orElse(null);
    }

    /**
     * 变成"盘在 {@code wearer} 身上游走的雷龙"（登神链 t5）✓。
     *
     * <p>渲染器把脊线换成螺旋（{@code LightningDragonGeometry.coil}），
     * 实体每 tick 只是<b>贴到那个人脚下</b> —— 位置同步照旧，客户端不需要额外数据 ✓。
     */
    public void configureCoil(Entity wearer, double scale, int life, int theme) {
        this.entityData.set(DATA_MODE, MODE_COIL);
        this.entityData.set(DATA_OWNER, java.util.Optional.of(wearer.getUUID()));
        this.entityData.set(DATA_SCALE, (int) Math.round(scale * 100.0D));
        this.entityData.set(DATA_LIFE, Math.max(20, life));
        this.entityData.set(DATA_THEME, Math.max(0, theme));
        this.entityData.set(DATA_DELAY, 0);
        this.entityData.set(DATA_SEED, this.random.nextInt());
        this.setPos(wearer.getX(), wearer.getY(), wearer.getZ());
    }

    @Override
    protected void defineSynchedData() {
        this.entityData.define(DATA_SCALE, 100);
        this.entityData.define(DATA_THEME, 2);
        this.entityData.define(DATA_LIFE, 150);
        this.entityData.define(DATA_SEED, 0);
        this.entityData.define(DATA_DELAY, 0);
        this.entityData.define(DATA_MODE, MODE_ERUPT);
        this.entityData.define(DATA_OWNER, java.util.Optional.empty());
    }

    @Override
    public void tick() {
        super.tick();
        if (this.level().isClientSide()) {
            return;
        }
        ServerLevel level = (ServerLevel) this.level();
        // ★ 缠绕形态：跟着人走 + 时间到了/人没了就散 ✓（不需要破土/咆哮那一套）
        if (this.coil()) {
            Entity wearer = this.ownerId() == null ? null : level.getPlayerByUUID(this.ownerId());
            if (wearer instanceof net.minecraft.world.entity.LivingEntity living) {
                wearer = living.isAlive() ? living : null;
            }
            if (wearer == null || this.tickCount > this.life()) {
                this.discard();
                return;
            }
            this.setPos(wearer.getX(), wearer.getY(), wearer.getZ());
            // 时不时在龙经过的地方甩两颗电花（跟着螺旋走，别乱撒 ✗）
            if (this.tickCount % 5 == 0) {
                double ang = this.tickCount * 0.10D + this.random.nextDouble() * 6.28D;
                double r = 1.5D + this.random.nextDouble() * 0.9D;
                level.sendParticles(TnSpellMechanics.arc(),
                        this.getX() + Math.cos(ang) * r,
                        this.getY() + 0.5D + this.random.nextDouble() * 3.6D,
                        this.getZ() + Math.sin(ang) * r,
                        1, 0.05D, 0.05D, 0.05D, 0.0D);
            }
            return;
        }
        if (!this.roared && this.tickCount == 1) {
            this.roared = true;
            // ★ 龙啸：低频、很响、从落点传出去 ✓（t5 的排面全靠它）
            level.playSound(null, this.getX(), this.getY() + 2.0D, this.getZ(),
                    SoundEvents.ENDER_DRAGON_GROWL, SoundSource.HOSTILE, 2.4F, 0.72F);
            level.playSound(null, this.getX(), this.getY() + 2.0D, this.getZ(),
                    SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.HOSTILE, 1.6F, 0.55F);
        }
        // 破土的那一下：地上一圈电花 ＋ 白闪（渲染器画大光效，这里补粒子 ✓）
        if (this.tickCount == this.delay() + 1) {
            level.sendParticles(ParticleTypes.FLASH, this.getX(), this.getY() + 0.6D, this.getZ(),
                    3, 0.2D, 0.2D, 0.2D, 0.0D);
            level.sendParticles(ParticleTypes.ELECTRIC_SPARK, this.getX(), this.getY() + 0.4D, this.getZ(),
                    120, 0.8D, 0.5D, 0.8D, 1.6D);
            level.playSound(null, this.getX(), this.getY(), this.getZ(),
                    SoundEvents.LIGHTNING_BOLT_IMPACT, SoundSource.HOSTILE, 1.4F, 0.85F);
        }
        // 上升途中每 4 tick 沿龙的路径撒一点电花（和渲染器共用同一条曲线 ✓）
        int age = this.tickCount - this.delay();
        if (age > 0 && age % 4 == 0 && age < this.life()) {
            double[] head = LightningDragonPath.point(age, this.scale());
            double x = this.getX() + head[0];
            double z = this.getZ() + head[2];
            double y = this.getY() + head[1];
            level.sendParticles(TnSpellMechanics.arc(), x, y, z, 1, 0.1D, 0.1D, 0.1D, 0.0D);
            level.sendParticles(ParticleTypes.ELECTRIC_SPARK, x, y, z, 1, 0.1D, 0.1D, 0.1D, 0.0D);
        }
        if (this.tickCount > this.life()) {
            this.discard();
        }
    }

    /**
     * 剔除盒：实体只有 0.1 格，但龙有 50 多格高、10 格宽 ✓ —— 不覆盖的话，
     * 平视时整条龙会被视锥裁掉 ✗（落雷/雷笼都踩过这个坑）。
     */
    @Override
    public net.minecraft.world.phys.AABB getBoundingBoxForCulling() {
        if (this.coil()) {
            // 缠绕形态只有 3 格半径、5 格高 ✓（别按 50 格去撑，白算）
            return super.getBoundingBoxForCulling().inflate(6.0D, 8.0D, 6.0D);
        }
        double s = Math.max(0.5D, this.scale());
        return super.getBoundingBoxForCulling().inflate(12.0D * s, LightningDragonPath.sky(s) + 8.0D, 12.0D * s);
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
    public boolean isPickable() {
        return false;
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double distance) {
        return distance < 131072.0D;
    }
}
