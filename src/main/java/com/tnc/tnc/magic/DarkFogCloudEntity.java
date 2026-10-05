package com.tnc.tnc.magic;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.spell_engine.entity.SpellCloud;

/**
 * <b>黑雾</b>的真身 —— 暗系第四条链（黑雾 / 领域）用的云实体。
 *
 * <h2>为什么要有自己的云实体</h2>
 * 引擎的 {@code SpellCloud} 只是一团"生成后就不动"的粒子发射器：
 * <ul>
 *   <li>它不会跟着施法者走 —— 而"黑雾"是<b>领域</b>，应该能裹着自己推过去 ✗；</li>
 *   <li>它没有边界提示 —— 半径 9 格的雾，玩家看不出自己站在里面还是外面 ✗。</li>
 * </ul>
 * 引擎自己留了两个缺口让我们补，不用重写它 ✓：
 * <ol>
 *   <li>{@code release.target.cloud.entity_type_id}（{@code SpellHelper.placeCloud} 会用它
 *       {@code create(level)} 出实体，再强转成 {@code SpellCloud}）⇒ 指向本类即可 ✓；</li>
 *   <li>{@code client_data.model}（{@code SpellCloudRenderer} 会把它喂给
 *       {@code CustomModels.render}）⇒ 3D 穹顶走的是投射物那条**已验证**的模型通道 ✓。</li>
 * </ol>
 *
 * <h2>行为（作者 2026-10-09："你全做吧" ⇒ 推荐的方案全上）</h2>
 * <ul>
 *   <li><b>t3 起跟着施法者走</b>：t1/t2 原地铺开（当控制/封路），t3 开始整片雾跟着走 ✓；</li>
 *   <li><b>t5 还会慢慢绕着施法者转</b>（"吞光领域"要像一圈压过来的黑幕 ✓）；</li>
 *   <li><b>边界法阵</b>：跟着雾走的一张暗色魔法阵（{@link TNMagicCircleEntity#STYLE_DARK}）⇒
 *       玩家一眼看得出范围 ✓（由 {@code TnSpellMechanics} 在施法那一刻生成并交给我们托管）。</li>
 * </ul>
 *
 * <h2>为什么"跟不跟"是从法术本体算出来的</h2>
 * 云的 JSON 里没有"自定义字段"这种口子（引擎是 Gson 反序列化，未识别的键会被**静默丢掉** ✗）。
 * 所以"是哪条链、第几档"只能从法术本体读：{@code spell.group == "dark_fog"}
 * ＋ {@code spell.learn.tier}（1..5）✓ —— 数据驱动，以后往链里加第六档也不用改这里 ✓。
 */
public class DarkFogCloudEntity extends SpellCloud {

    /** 与 {@code TnSpellMechanics.FOG_GROUP} 一致（那边也用它挑法术）。 */
    public static final String FOG_GROUP = "dark_fog";

    /**
     * 从第几档开始整片雾跟着施法者走（1..5）。
     *
     * <p>★★ <b>2026-10-09 改成"永不跟随"（{@code 6}）</b> —— 作者实机反馈：
     * 「**黑雾的 t4t5 中间生成的模型怎么会黏在人物身上一起走了**」✗。
     *
     * <p>那正是我当初加的"t3 起跟随"设计（本文档 §2.3 写的就是它）✓，逻辑上没错，
     * 但实机观感是"领域"变成了**挂在身上的一个球** ✗ —— 作者不要这个。
     * AOE 领域本来就该**钉在释放点**：丢出去、敌人走进去吃伤害，这才是"领域" ✓。
     *
     * <p>⇒ 现在标记与穹顶都**原地不动** ✓。想恢复跟随只要把这里改成 3（或 4/5）✓；
     * 想只让"标记"跟随、穹顶留在原地，那是另一套改法（穹顶位置与实体位置解耦），
     * 要做再说 ✓。
     */
    private static final int FOLLOW_FROM_TIER = 6;

    /**
     * 生成后先"定住"几 tick 再开始跟随。
     *
     * <p>为什么：{@code placeCloud} 先做 {@code applyEntityPlacement}（可能带
     * {@code force_onto_ground} 把雾按到地面上）再挂进世界 ✓；我们立刻改位置会把这步修正吃掉 ✗。
     * 而且施法者那一刻就站在雾中心，先定住一小会儿也让"铺开"这个动作看得出来 ✓。
     */
    private static final int FOLLOW_GRACE_TICKS = 10;

    /** t5：绕着施法者转的角速度（度/tick）与额外半径（格）。 */
    private static final double T5_ORBIT_DEGREES_PER_TICK = 0.35D;
    private static final double T5_ORBIT_RADIUS = 1.2D;

    /**
     * 雾自己的"存在感"粒子（每 {@link #IDLE_PARTICLE_INTERVAL} tick 一次）✓。
     *
     * <p>为什么要它：法术 JSON 的 `release.target.cloud.client_data.particles` 由**引擎**
     * 每 tick 播放 ✓；但那条路是引擎自己读 JSON 的，我们插不进手 ✗。
     * 这里补的是"雾在哪、有多浓"最直观的那几颗（贴地墨点 ＋ 魂火 ＋ 烟），
     * 走的是**服务端撒粒子**（`ServerLevel.sendParticles`）⇒ 不用网络通道、
     * 也不依赖引擎的粒子指令封装 ✓。
     *
     * <p>★ <b>只用已确认注册过的粒子 id</b> ✗：`minecraft:squid_ink` / `soul_fire_flame` /
     * `smoke` 都是原版 ✓；`block_factorys_bosses` 那几个（ink_fog 等）**不是**
     * ParticleType（那个 mod 注册成了 Bedrock 粒子）⇒ 引擎用它们会
     * ClassCastException 把客户端打崩 ✗（见 `tools/gen_dark_fog_spells.py` 的
     * PARTICLE SAFETY 说明 ✓）。
     */
    private static final int IDLE_PARTICLE_INTERVAL = 4;
    private static final String[] IDLE_PARTICLES = {
            "minecraft:squid_ink", "minecraft:smoke", "minecraft:soul_fire_flame"
    };

    /** 边界法阵（纯表现，不进存档 ✓）。 */
    private TNMagicCircleEntity circle;

    /** 建阵时告诉我们的参数 —— 只在"雾的尺寸/时长变了"时才去改法阵 ✓。 */
    private double circleRadius = -1.0D;
    private int circleLife = -1;

    private float orbitAngle;

    /** 只在第一次 tick 时打一行日志（验收：grep `TN-C: fog cloud` ✓）。 */
    private boolean announced;

    public DarkFogCloudEntity(EntityType<? extends DarkFogCloudEntity> type, Level level) {
        super(type, level);
    }

    /** 这个是"黑雾"吗（按法术链判断 ✓）。 */
    public boolean isDarkFog() {
        net.spell_engine.api.spell.Spell spell = getSpell();
        return spell != null && FOG_GROUP.equals(spell.group);
    }

    /** 档位（读不到就是 0）。 */
    public int tier() {
        net.spell_engine.api.spell.Spell spell = getSpell();
        if (spell == null || spell.learn == null) {
            return 0;
        }
        return spell.learn.tier;
    }

    // ------------------------------------------------------------------
    //  ★ 同步给客户端的"我有多黑"（作者 2026-10-09："敌人包括其他玩家触碰到这个雾就会
    //    受到致盲效果，屏幕得给我黑了，t 级越高屏幕越黑"）
    //
    //  为什么要同步：**屏幕变黑只能客户端自己做** ✗ ——
    //    * 原版 `darkness` 效果只有**一档**、而且它是给"站在里面的玩家"暗视野用的，
    //      没法"t 级越高越黑" ✗；
    //    * 引擎的 `Impact$Action` 也没有"压暗屏幕"这种动作 ✗。
    //  所以：服务端把"档位 + 半径"同步下来，客户端在 `TNSpellClientVisuals` 里
    //  按"离雾心多远 / 半径"算一个 0..1 的深度，再乘以档位对应的最大压暗值 ✓。
    // ------------------------------------------------------------------

    /** 同步字段：档位（1..5，0 = 未知）✓ */
    private static final net.minecraft.network.syncher.EntityDataAccessor<Integer> DATA_FOG_TIER =
            net.minecraft.network.syncher.SynchedEntityData.defineId(
                    DarkFogCloudEntity.class, net.minecraft.network.syncher.EntityDataSerializers.INT);

    /** 同步字段：雾的真实半径（×100 存整数，和魔法阵那套一个办法 ✓） */
    private static final net.minecraft.network.syncher.EntityDataAccessor<Integer> DATA_FOG_RADIUS =
            net.minecraft.network.syncher.SynchedEntityData.defineId(
                    DarkFogCloudEntity.class, net.minecraft.network.syncher.EntityDataSerializers.INT);

    @Override
    protected void defineSynchedData() {
        // ⚠ 1.20.1 的 Entity.defineSynchedData() 是**抽象**的、没有 super 可调 ✗ ——
        //   引擎的 SpellCloud 也只是自己 define 自己的那几个字段，它读的是私有 accessor，
        //   不需要我们的字段参与 ✓；我们只加自己的两个 ✓。
        this.entityData.define(DATA_FOG_TIER, 0);
        this.entityData.define(DATA_FOG_RADIUS, 0);
    }

    /** 客户端用：这片雾是第几档（0 = 还不知道）✓。 */
    public int syncedTier() {
        return this.entityData.get(DATA_FOG_TIER);
    }

    /** 客户端用：这片雾的半径（格；0 = 还不知道）✓。 */
    public double syncedRadius() {
        return this.entityData.get(DATA_FOG_RADIUS) / 100.0D;
    }

    /**
     * 服务端把"档位 + 半径"写进同步字段（每 tick 调一次，客户端因此不会晚半拍 ✓）。
     *
     * <p>半径取自 JSON 的 `volume.radius`（不是 combinedRadius(null)，那条对没有
     * `extra_radius` 的法术合法但没必要在这儿重复算 ✓）。
     */
    private void syncAppearance() {
        if (level().isClientSide()) {
            return;
        }
        int t = tier();
        if (this.entityData.get(DATA_FOG_TIER) != t) {
            this.entityData.set(DATA_FOG_TIER, t);
        }
        double radius = 0.0D;
        net.spell_engine.api.spell.Spell spell = getSpell();
        if (spell != null && spell.release != null && spell.release.target != null
                && spell.release.target.cloud != null && spell.release.target.cloud.volume != null) {
            radius = spell.release.target.cloud.volume.radius;
        }
        int stored = (int) Math.round(radius * 100.0D);
        if (this.entityData.get(DATA_FOG_RADIUS) != stored) {
            this.entityData.set(DATA_FOG_RADIUS, stored);
        }
    }

    /**
     * 把一张边界法阵交给这片雾托管（重复调用是幂等的 ✓）。
     *
     * <p>放在这里而不是只放在施法事件里，是因为只有实体这边知道"雾活了多久"——
     * 法阵的时长要跟雾对齐，雾被重放时也不该多铺一张 ✓。
     */
    public void claimBoundaryCircle(TNMagicCircleEntity candidate, double radius, int lifeTicks) {
        if (candidate == null) {
            return;
        }
        if (circle != null && circle.isAlive() && circle != candidate) {
            candidate.discard();                 // 多出来的直接扔掉 ✓
            return;
        }
        circle = candidate;
        if (Math.abs(circleRadius - radius) > 0.05D || circleLife != lifeTicks) {
            circleRadius = radius;
            circleLife = lifeTicks;
            circle.configure(radius, lifeTicks, TNMagicCircleEntity.STYLE_DARK);
        }
        placeCircle();
    }

    public TNMagicCircleEntity boundaryCircle() {
        return circle;
    }

    /** 法阵只跟 X/Z（高度由法阵自己"往下找一层地面"的逻辑决定 ✓）。 */
    private void placeCircle() {
        if (circle == null || !circle.isAlive()) {
            circle = null;
            return;
        }
        circle.setPos(getX(), circle.getY(), getZ());
    }

    @Override
    public void tick() {
        super.tick();

        // 客户端不需要这些（位置与法阵都是服务端权威的 ✓）
        if (level().isClientSide()) {
            return;
        }

        if (!announced) {
            announced = true;
            org.apache.logging.log4j.LogManager.getLogger("TN-C/fog").info(
                    "TN-C: fog cloud alive isDarkFog={} tier={} r={} followFromTier={} pos={}",
                    isDarkFog(), tier(), circleRadius, FOLLOW_FROM_TIER, position());
        }

        placeCircle();
        purgeExtraCircles();
        idleParticles();
        syncAppearance();

        if (!isDarkFog() || tickCount < FOLLOW_GRACE_TICKS) {
            return;
        }
        int tier = tier();
        if (tier < FOLLOW_FROM_TIER) {
            return;
        }

        LivingEntity owner = resolveOwner();
        if (owner == null || !owner.isAlive() || owner.level() != level()) {
            return;
        }

        double targetY = owner.getY();
        if (tier >= 5) {
            // t5：绕着施法者转，像一圈慢慢压过来的黑幕 ✓
            orbitAngle += (float) T5_ORBIT_DEGREES_PER_TICK;
            double rad = Math.toRadians(orbitAngle);
            setPos(owner.getX() + Math.cos(rad) * T5_ORBIT_RADIUS, targetY,
                    owner.getZ() + Math.sin(rad) * T5_ORBIT_RADIUS);
        } else {
            setPos(owner.getX(), targetY, owner.getZ());
        }
        placeCircle();
    }

    /**
     * 施法者。
     *
     * <p>{@code SpellCloud} 把主人存在**私有**字段里，但对外的口子是一个原版方法
     * （{@code TraceableEntity.getOwner()} 在 SRG 里叫 {@code m_19749_}）✓ ——
     * 引擎自己的 {@code SpellCloud.tick} 也是拿它去结算范围伤害的 ✓；
     * 换维度时它会自己按 UUID 重新解析 ✓，所以这里直接用就够了，不用再存一份 ✗。
     */
    private LivingEntity resolveOwner() {
        Entity owner = this.m_19749_();
        return owner instanceof LivingEntity living ? living : null;
    }

    /** 延迟到"本 tick 之后再删"的法阵（见 {@link #purgeExtraCircles} 的说明 ✓）。 */
    private final java.util.List<TNMagicCircleEntity> pendingPurge = new java.util.ArrayList<>();

    private void purgeExtraCircles() {
        // 先把上一轮攒下的删掉（那时已经不在实体遍历里了 ✓）
        if (!pendingPurge.isEmpty()) {
            for (TNMagicCircleEntity extra : pendingPurge) {
                if (extra.isAlive()) {
                    extra.discard();
                }
            }
            pendingPurge.clear();
        }
        if (tickCount % 20 != 0) {
            return;
        }
        // ⚠ 只"记账"、不在这里 discard：本方法是在 `SpellCloud.tick`（会遍历实体做范围结算）
        //   的调用栈里跑的，删实体要等这一 tick 走完再删 ✓
        //   （直接删就是 ConcurrentModificationException ✗）。
        pendingPurge.addAll(level().getEntitiesOfClass(
                TNMagicCircleEntity.class, getBoundingBox().inflate(2.0D),
                e -> e != circle && e.style() == TNMagicCircleEntity.STYLE_DARK));
    }

    /**
     * 雾自己的粒子（每 {@value #IDLE_PARTICLE_INTERVAL} tick 一次）✓。
     *
     * <p>服务端撒粒子（`sendParticles`）⇒ 不经过引擎的粒子指令通道，
     * 也不依赖任何 mod 的粒子注册表以外的假设 ✓。
     */
    private void idleParticles() {
        if (tickCount % IDLE_PARTICLE_INTERVAL != 0 || !(level() instanceof ServerLevel server)) {
            return;
        }
        double radius = Math.max(2.0D, circleRadius > 0 ? circleRadius : 4.5D);
        for (int i = 0; i < IDLE_PARTICLES.length; i++) {
            net.minecraft.core.particles.ParticleOptions options = idleType(i);
            if (options == null) {
                continue;                                   // 那个 id 没注册 ⇒ 跳过（不崩 ✓）
            }
            int count = i == 0 ? 7 : (i == 1 ? 5 : 2);      // 墨点最多、魂火其次、烟最少
            double spread = i == 2 ? 1.0D : 0.72D;          // 魂火/墨点更靠里
            // 参数顺序：粒子 / x y z / 数量 / dx dy dz（速度抖动）/ 速度
            server.sendParticles(options,
                    getX(), getY() + 0.35D, getZ(),
                    count,
                    radius * spread, 0.6D, radius * spread,
                    0.02D);
        }
    }

    /** 第 i 个"存在感"粒子的类型（**惰性解析 + 缓存** ✓：注册表构造期还没填满 ✗）。 */
    private net.minecraft.core.particles.ParticleOptions idleType(int index) {
        if (idleCache == null) {
            idleCache = new net.minecraft.core.particles.ParticleOptions[IDLE_PARTICLES.length];
        }
        if (idleCache[index] == null) {
            net.minecraft.resources.ResourceLocation id =
                    net.minecraft.resources.ResourceLocation.tryParse(IDLE_PARTICLES[index]);
            net.minecraft.core.particles.ParticleType<?> type = id == null ? null
                    : net.minecraftforge.registries.ForgeRegistries.PARTICLE_TYPES.getValue(id);
            // ⚠ 必须确认"真的注册过" ✗：Forge 的 getValue 对未注册 id 会返回默认值，
            //   而那是一个**别的粒子** ⇒ 我们会静默撒出错误的粒子 ✗
            if (type != null && net.minecraftforge.registries.ForgeRegistries.PARTICLE_TYPES.containsKey(id)
                    && type instanceof net.minecraft.core.particles.ParticleOptions options) {
                idleCache[index] = options;
            }
        }
        return idleCache[index];
    }

    private net.minecraft.core.particles.ParticleOptions[] idleCache;

    @Override
    public String toString() {
        return "DarkFogCloudEntity[tier=" + tier() + " r=" + circleRadius + " @ " + position() + "]";
    }
}
