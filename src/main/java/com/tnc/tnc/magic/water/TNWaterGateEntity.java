package com.tnc.tnc.magic.water;

import com.tnc.tnc.magic.TNOrbEntities;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
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
import net.minecraftforge.network.NetworkHooks;

import java.util.UUID;

/**
 * <b>水球链 t4/t5 的"金闪闪法阵群"</b>（作者 2026-11-09 指定 ✓）。
 *
 * <p>作者原话：
 * <blockquote>
 * 直接射一个太单调了……在释放 t4 之后，玩家身边会有两个小法阵围绕，然后当玩家攻击了某个敌人之后，
 * 玩家身边就会出现几个法阵并且像光线那样快速射出我们的这个新水波，记得要先出现法阵，
 * 法阵全部出现完之后再一起发射水波；t5 就是法阵和水波变大，然后攻击结束之后几个法阵汇聚到一起
 * 射出一个巨大的水波。这个水波可以就不用原来那样持久，就跟光线一样就行了。
 * </blockquote>
 *
 * <p>时间线全在 {@link WaterGateRules} 里（服务端判定 / 客户端表现共用 ✓）：
 * <pre>
 *   IDLE ──(玩家打中敌人 / 2 秒冷却)──▶ OPEN ──(全部亮完 + 憋 6 tick)──▶ FIRE
 *     ▲                                                                    │
 *     └──────────────── t4 直接回来 ◀───────────────────────────────────────┤
 *                                                                          ▼ (仅 t5)
 *     ▲──────────── GIANT（一道巨大水波）◀── CONVERGE（法阵汇到身前一点）
 * </pre>
 *
 * <p>这个实体<b>只做三件事</b>：跟着主人走 ✓、到点把 {@link TNWaterLanceEntity} 生成出来 ✓、
 * 让客户端知道"现在第几阶段、目标是谁"✓ —— 画面全在 {@code client/TNWaterGateRenderer} 里 ✓。
 *
 * <p>{@code updateInterval(1)}：它每 tick 贴着玩家走，同步慢了法阵会拖在身后 ✗
 * （黑雾 / 黑夜之手 / 环绕雷球都是同一个理由 ✓）。
 */
public final class TNWaterGateEntity extends Entity {

    /** 阶段（服务端推进，客户端只读 ✓）。 */
    public static final int IDLE = 0;
    public static final int OPEN = 1;
    public static final int FIRE = 2;
    /**
     * 齐射之后的<b>残留</b>（作者 2026-11-10：「射完激光之后法阵还要残留半秒」✓）。
     *
     * <p>这半秒里什么都不结算 ✗：水波已经飞出去了，只剩法阵在原地淡出 ✓ ——
     * 不留这一段的后果是六~八座阵在齐射结束那一 tick"啪"地同时消失 ✗。
     *
     * <p>⚠️ 枚举值是 {@code 5}，<b>不是</b> 3 ✗ —— 3 / 4 早就被 CONVERGE / GIANT 占了，
     * 而客户端渲染器是拿这些整数分支的 ✗（重排会让老存档/老客户端把"残留"当成"汇聚"✗）。
     */
    public static final int LINGER = 5;
    public static final int CONVERGE = 3;
    public static final int GIANT = 4;

    private static final EntityDataAccessor<Integer> TIER =
            SynchedEntityData.defineId(TNWaterGateEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> PHASE =
            SynchedEntityData.defineId(TNWaterGateEntity.class, EntityDataSerializers.INT);
    /** 当前阶段是从第几 tick 开始的（客户端拿它算"这个法阵亮了几分"✓）。 */
    private static final EntityDataAccessor<Integer> PHASE_START =
            SynchedEntityData.defineId(TNWaterGateEntity.class, EntityDataSerializers.INT);
    /** 这一轮齐射打的是谁（-1 = 没有目标 ⇒ 顺着准星射 ✓）。 */
    private static final EntityDataAccessor<Integer> TARGET_ID =
            SynchedEntityData.defineId(TNWaterGateEntity.class, EntityDataSerializers.INT);
    /** 主人的朝向（t5 汇聚点要用 ✓；普通实体不会自动同步 yaw ✗）。 */
    private static final EntityDataAccessor<Float> LOOK_YAW =
            SynchedEntityData.defineId(TNWaterGateEntity.class, EntityDataSerializers.FLOAT);
    /**
     * 「从敌人指向玩家」的水平朝向 ✓ —— 齐射的扇面以它为中心（{@link WaterGateRules#portalPos} ✓）。
     *
     * <p>★ 2026-11-10 作者："法阵出现的位置可以再拉开一点，并且离敌人远点"✗ ⇒
     * 法阵只排背对敌人的 240° 扇面，这个朝向**在触发那一刻定死** ✓（目标跑动也不会让阵跟着转 ✗）。
     */
    private static final EntityDataAccessor<Float> AWAY_YAW =
            SynchedEntityData.defineId(TNWaterGateEntity.class, EntityDataSerializers.FLOAT);

    private UUID owner;
    private long cooldownUntil;

    public TNWaterGateEntity(EntityType<? extends TNWaterGateEntity> type, Level level) {
        super(type, level);
        noPhysics = true;
        setNoGravity(true);
        setInvulnerable(true);
    }

    // ------------------------------------------------------------------
    //  对外：施法 / 触发
    // ------------------------------------------------------------------

    /** 施法入口（t4/t5 ✓）：同一个玩家身上只会有一组法阵（旧的直接换掉 ✓）。 */
    public static TNWaterGateEntity cast(ServerPlayer player, int tier) {
        if (!(player.level() instanceof ServerLevel level)) {
            return null;
        }
        for (TNWaterGateEntity old : level.getEntitiesOfClass(TNWaterGateEntity.class,
                player.getBoundingBox().inflate(8.0D))) {
            if (player.getUUID().equals(old.owner)) {
                old.discard();
            }
        }
        TNWaterGateEntity gate = TNOrbEntities.WATER_GATE.get().create(level);
        if (gate == null) {
            return null;
        }
        gate.owner = player.getUUID();
        gate.entityData.set(TIER, Math.max(4, tier));
        gate.entityData.set(PHASE, IDLE);
        gate.entityData.set(PHASE_START, 0);
        gate.entityData.set(TARGET_ID, -1);
        gate.setPos(player.position());
        level.addFreshEntity(gate);
        return gate;
    }

    /**
     * 玩家打中了一个敌人 ⇒ 有法阵的话就开一轮齐射 ✓。
     *
     * <p>由 {@link WaterGateEvents} 在 {@code LivingHurtEvent} 里调 ✓
     * （每次攻击都触发 ✗ —— 内置冷却与"动画没放完不许再来"两道闸，见 {@link #tryTrigger(LivingEntity)} ✓）。
     */
    public static void onOwnerDamaged(ServerPlayer attacker, LivingEntity victim) {
        if (!(attacker.level() instanceof ServerLevel level)) {
            return;
        }
        for (TNWaterGateEntity gate : level.getEntitiesOfClass(TNWaterGateEntity.class,
                attacker.getBoundingBox().inflate(6.0D))) {
            if (attacker.getUUID().equals(gate.owner) && gate.tryTrigger(victim)) {
                return;
            }
        }
    }

    private boolean tryTrigger(LivingEntity victim) {
        if (phase() != IDLE || tickCount < cooldownUntil) {
            return false;
        }
        LivingEntity ownerEntity = ownerEntity();
        if (ownerEntity == null || !WaterSpellRules.enemy(ownerEntity, victim)) {
            return false;
        }
        entityData.set(TARGET_ID, victim.getId());
        // 扇面朝向：从敌人指向玩家 ✓（决定"阵在哪半边、离敌人多远"✗）
        Vec3 away = ownerEntity.position().subtract(victim.position());
        if (away.lengthSqr() > 1.0E-4D) {
            away = away.normalize();
            entityData.set(AWAY_YAW, (float)Math.toDegrees(Math.atan2(-away.x, away.z)));
        } else {
            entityData.set(AWAY_YAW, ownerEntity.getYRot());
        }
        enter(OPEN);
        // 冷却至少要盖住一整个动作（t5 的"汇聚 + 巨大水波"比 2 秒长 ✓）
        cooldownUntil = tickCount + Math.max(WaterGateRules.VOLLEY_COOLDOWN, WaterGateRules.cycleTicks(tier()));
        return true;
    }

    public int tier() { return entityData.get(TIER); }

    public int phase() { return entityData.get(PHASE); }

    public int phaseStart() { return entityData.get(PHASE_START); }

    public int targetId() { return entityData.get(TARGET_ID); }

    public float lookYaw() { return entityData.get(LOOK_YAW); }

    /** 「从敌人指向玩家」的朝向 ✓（齐射扇面的中心、t5 汇聚点的方向都靠它）。 */
    public float awayYaw() { return entityData.get(AWAY_YAW); }

    public boolean isOwner(Entity entity) {
        return entity != null && entity.getUUID().equals(owner);
    }

    public static boolean isActive(TNWaterGateEntity gate) {
        return gate != null && !gate.isRemoved() && gate.tickCount <= WaterGateRules.GATE_TICKS;
    }

    // ------------------------------------------------------------------
    //  每 tick
    // ------------------------------------------------------------------

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) {
            return;
        }
        ServerLevel level = (ServerLevel)level();
        LivingEntity ownerEntity = ownerEntity();
        if (ownerEntity == null || tickCount > WaterGateRules.GATE_TICKS) {
            discard();
            return;
        }
        // 法阵是"挂在主人身上"的 ⇒ 平时每 tick 跟过去 ✓。
        // ★ 2026-11-10 作者："几座法阵出现就在原地，不要跟着玩家，跟着玩家的只有那两个法阵"✗
        //   ⇒ **只有 IDLE（两个环绕阵）跟着人走** ✓；一旦开阵，实体就钉在原地 = 齐射的锚点 ✓
        //   （玩家打出这一下之后跑开，阵还留在原处 ✓）。
        if (phase() == IDLE) {
            setPos(ownerEntity.position());
        }
        entityData.set(LOOK_YAW, ownerEntity.getYRot());

        double elapsed = tickCount - phaseStart();
        switch (phase()) {
            case OPEN -> {
                if (elapsed >= WaterGateRules.openTicks(tier())) {
                    enter(FIRE);
                    fireAll(level, ownerEntity);
                }
            }
            case FIRE -> {
                if (elapsed >= WaterGateRules.FIRE_TICKS) {
                    // 作者 2026-11-10："射完激光之后法阵还要残留半秒" ⇒ 先挂 10 tick 再走下一步 ✓
                    enter(LINGER);
                }
            }
            case LINGER -> {
                if (elapsed >= WaterGateRules.LINGER_TICKS) {
                    if (WaterGateRules.giant(tier())) {
                        enter(CONVERGE);
                    } else {
                        enter(IDLE);
                    }
                }
            }
            case CONVERGE -> {
                if (elapsed >= WaterGateRules.CONVERGE_TICKS) {
                    enter(GIANT);
                    fireGiant(level, ownerEntity);
                }
            }
            case GIANT -> {
                if (elapsed >= WaterGateRules.GIANT_TICKS) {
                    enter(IDLE);
                }
            }
            default -> {
            }
        }
    }

    private void enter(int phase) {
        entityData.set(PHASE, phase);
        entityData.set(PHASE_START, tickCount);
    }

    /** 齐射：每个法阵各射一道水波，全部射向同一个目标 ✓（"一起发射"✗ 作者强调 ✓）。 */
    private void fireAll(ServerLevel level, LivingEntity ownerEntity) {
        int count = WaterGateRules.portals(tier());
        Vec3 aim = aimPoint(level, ownerEntity);
        for (int i = 0; i < count; i++) {
            // ★ 用**实体自己的位置**（= 开阵那一刻的锚点 ✓）而不是玩家现在的脚下 ✗
            Vec3 from = WaterGateRules.portalPos(position(), tier(), i, count, awayYaw());
            TNWaterLanceEntity.fire(level, ownerEntity, tier(), false, from, aim);
            WaterFx.muzzle(level, from, WaterGateRules.facing(from, aim), tier());
        }
    }

    /** t5 收尾：几个法阵汇到身前一点，射出一道巨大水波 ✓。 */
    private void fireGiant(ServerLevel level, LivingEntity ownerEntity) {
        Vec3 from = WaterGateRules.convergePos(position(), awayYaw());
        Vec3 aim = aimPoint(level, ownerEntity);
        TNWaterLanceEntity.fire(level, ownerEntity, tier(), true, from, aim);
        WaterFx.aperture(level, from, WaterGateRules.facing(from, aim), WaterGateRules.giantRadius(tier()));
        level.playSound(null, ownerEntity.blockPosition(), SoundEvents.CONDUIT_ACTIVATE,
                SoundSource.PLAYERS, 1.4F, 0.5F);
    }

    /** 打哪：这一轮记下的目标 ✓；目标没了就顺着主人视线打 48 格 ✓（不至于白放 ✗）。 */
    private Vec3 aimPoint(ServerLevel level, LivingEntity ownerEntity) {
        int id = targetId();
        if (id >= 0 && level.getEntity(id) instanceof LivingEntity target && target.isAlive()) {
            return target.getBoundingBox().getCenter();
        }
        return ownerEntity.getEyePosition().add(ownerEntity.getLookAngle().scale(WaterGateRules.LANCE_RANGE));
    }

    private LivingEntity ownerEntity() {
        if (!(level() instanceof ServerLevel level)) {
            return null;
        }
        return WaterSpellOwners.resolve(level, owner);
    }

    // ------------------------------------------------------------------
    //  实体样板
    // ------------------------------------------------------------------

    @Override
    protected void defineSynchedData() {
        entityData.define(TIER, 4);
        entityData.define(PHASE, IDLE);
        entityData.define(PHASE_START, 0);
        entityData.define(TARGET_ID, -1);
        entityData.define(LOOK_YAW, 0F);
        entityData.define(AWAY_YAW, 0F);
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        discard();      // 法阵跟着主人走，存档里不需要它 ✓（重登再放一次就行 ✓）
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
    }

    @Override
    public boolean shouldBeSaved() {
        return false;
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
    public AABB getBoundingBoxForCulling() {
        return new AABB(position().add(-4, -1, -4), position().add(4, 4, 4));
    }

    @Override
    public Packet<ClientGamePacketListener> getAddEntityPacket() {
        return NetworkHooks.getEntitySpawningPacket(this);
    }
}
