package com.tnc.tnc.magic;

import com.tnc.tnc.TNMod;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.registries.RegistryObject;

import java.util.List;
import java.util.UUID;

/**
 * <b>环绕雷球</b>的真身 —— 一颗绕着施法者转的<b>实体</b>雷球（作者 2026-09-22：
 * "环绕雷球的技能，变为我们的实体雷球啊"）。
 *
 * <h2>为什么改成实体</h2>
 * 之前"环绕"是用 {@code ELECTRIC_SPARK} 粒子在玩家周围画圈 ✗ —— 看着像一圈亮点，
 * 不是"我们的雷球"。现在是真的实体：用和投射物<b>同一个模型</b>
 * （{@code assets/tnc/models/projectile/lightingball.json} 的 7 个方块，
 * 见 {@code client/TNThunderOrbModel}）+ 同一张贴图，所以看起来就是那颗球 ✓。
 *
 * <h2>行为</h2>
 * <ul>
 *   <li><b>位置</b>：每 tick 由自己算（绕着主人转），不用 AI、不受重力与方块碰撞影响 ✓；</li>
 *   <li><b>归属</b>：{@link #bind} 记主人 UUID 与槽位（0..COUNT-1），只同步给客户端位置，
 *       归属信息**只在服务端** ✓（客户端渲染不需要它）；</li>
 *   <li><b>电击</b>：每 {@link #ZAP_INTERVAL} tick 电一次附近的敌人（数值与原机制一致）；</li>
 *   <li><b>生命周期</b>：主人没了 / 死了 / 换维度 / <b>buff 掉了</b> → 自己 discard ✓
 *       （外面 {@code TnSpellMechanics} 也会在 buff 消失时清一遍，双保险）。</li>
 * </ul>
 *
 * <p>这个实体也是**将来"暗系召唤线"的模板**：一个纯服务端操控、客户端只负责画外观的召唤物 ✓
 * （那时要加的只有 AI / 攻击目标，位置控制这套已经跑通）。
 */
public class TNThunderOrbEntity extends Entity {

    /** 同时存在几颗（与机制层的常量一致）。 */
    public static final int COUNT = 4;
    /** 环绕半径（格）。 */
    public static final double RADIUS = 1.4D;
    /** 环绕高度（相对主人脚底，格）。 */
    public static final double HEIGHT = 1.0D;

    /** 绕一圈要多久（tick）。 */
    private static final int ORBIT_PERIOD = 80;
    /** 几 tick 电一次。 */
    private static final int ZAP_INTERVAL = 10;
    /** 电击判定半径 / 伤害。 */
    private static final double HIT_RADIUS = 3.5D;
    private static final float DAMAGE = 5.0F;

    private UUID ownerId;
    private int slot;

    public TNThunderOrbEntity(EntityType<? extends TNThunderOrbEntity> type, Level level) {
        super(type, level);
        // 不受重力、不参与方块碰撞：位置完全由我们每 tick 设定 ✓
        this.setNoGravity(true);
        this.noPhysics = true;
    }

    /** 由机制层在生成时调用：绑定主人与槽位。 */
    public void bind(UUID owner, int slot) {
        this.ownerId = owner;
        this.slot = slot;
    }

    public UUID ownerId() {
        return this.ownerId;
    }

    public int slot() {
        return this.slot;
    }

    @Override
    protected void defineSynchedData() {
        // 没有需要同步的字段：位置由原版实体同步机制处理 ✓
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        if (tag.hasUUID("Owner")) {
            this.ownerId = tag.getUUID("Owner");
        }
        this.slot = tag.getInt("Slot");
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        if (this.ownerId != null) {
            tag.putUUID("Owner", this.ownerId);
        }
        tag.putInt("Slot", this.slot);
    }

    @Override
    public void tick() {
        super.tick();       // 客户端靠它做插值，位置看起来才顺 ✓
        if (this.level().isClientSide()) {
            return;
        }
        ServerLevel level = (ServerLevel) this.level();
        Player owner = this.ownerId == null ? null : level.getPlayerByUUID(this.ownerId);
        if (!(owner instanceof ServerPlayer player)
                || !player.isAlive()
                || player.level() != this.level()
                || !hasEffect(player, TNEffects.ORBITING_THUNDER_ORB)) {
            this.discard();
            return;
        }

        long time = level.getGameTime();
        double phase = (time % ORBIT_PERIOD) / (double) ORBIT_PERIOD * Math.PI * 2.0D;
        double a = phase + this.slot * (Math.PI * 2.0D / COUNT);
        double x = player.getX() + Math.cos(a) * RADIUS;
        double z = player.getZ() + Math.sin(a) * RADIUS;
        double y = player.getY() + HEIGHT + Math.sin(a * 2.0D) * 0.25D;
        this.setPos(x, y, z);

        // 球体本身的电弧（让它在飞的时候一直"噼啪"）
        if (time % 2 == 0) {
            TnSpellMechanics.arcBall(level, this.position(), 2, 0.22D);
        }

        // 轮到这颗球电人（错开各颗球，避免四颗同时闪）
        if (time % ZAP_INTERVAL == this.slot % ZAP_INTERVAL) {
            zapNearby(level, player);
        }
    }

    private void zapNearby(ServerLevel level, ServerPlayer player) {
        AABB box = player.getBoundingBox().inflate(HIT_RADIUS);
        List<LivingEntity> targets = level.getEntitiesOfClass(LivingEntity.class, box);
        for (LivingEntity target : targets) {
            if (!isEnemy(player, target) || target.distanceTo(player) > HIT_RADIUS) {
                continue;
            }
            target.hurt(level.damageSources().indirectMagic(player, player), DAMAGE);
            TnSpellMechanics.arcLine(level, this.position(),
                    target.position().add(0.0D, target.getBbHeight() * 0.5D, 0.0D), 6, 0.15D);
        }
    }

    private static boolean hasEffect(ServerPlayer player, RegistryObject<MobEffect> effect) {
        return effect.isPresent() && player.hasEffect(effect.get());
    }

    /** 敌人 = 不是自己、不是队友（与机制层同一套判据）。 */
    private static boolean isEnemy(Player player, LivingEntity target) {
        if (target == player || !target.isAlive()) {
            return false;
        }
        if (target instanceof Player other && !player.canHarmPlayer(other)) {
            return false;
        }
        if (target instanceof Mob mob && mob.getTarget() == player) {
            return true;
        }
        return !(target instanceof Player);
    }

    @Override
    public boolean isPickable() {
        return false;       // 别挡住玩家点东西
    }

    @Override
    public boolean isAttackable() {
        return false;
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double distanceSqr) {
        return distanceSqr < 4096.0D;
    }
}
