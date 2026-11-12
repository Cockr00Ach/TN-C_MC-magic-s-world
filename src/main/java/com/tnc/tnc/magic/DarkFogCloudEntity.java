package com.tnc.tnc.magic;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
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

    /** Fog is a nonphysical spell volume; never scan its entire domain for water currents. */
    @Override
    public void updateFluidHeightAndDoFluidPushing(java.util.function.Predicate<net.minecraft.world.level.material.FluidState> predicate) {
    }

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
     * 这里补的是"雾在哪、有多浓"最直观的贴地墨点；不再混入会把黑雾洗成灰色的 smoke，
     * 走的是**服务端撒粒子**（`ServerLevel.sendParticles`）⇒ 不用网络通道、
     * 也不依赖引擎的粒子指令封装 ✓。
     *
     * <p>★ <b>只用已确认注册过的粒子 id</b>：`minecraft:squid_ink` 是原版 ✓；
     * `block_factorys_bosses` 那几个（ink_fog 等）**不是**
     * ParticleType（那个 mod 注册成了 Bedrock 粒子）⇒ 引擎用它们会
     * ClassCastException 把客户端打崩 ✗（见 `tools/gen_dark_fog_spells.py` 的
     * PARTICLE SAFETY 说明 ✓）。
     */
    private static final int IDLE_PARTICLE_INTERVAL = 4;
    private static final String[] IDLE_PARTICLES = {
            "minecraft:squid_ink"
    };

    /** 黑雾"抓紧"的判定间隔（tick）：越密越像一直有人拽，但也不用每 tick 都推 ✓。 */
    private static final int GRIP_INTERVAL = 6;

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
    /**
     * ★ 2026-11-11 作者：「黑雾的大小也要随着 t 级增长变大，你别只加宽度，高度也要加」✓
     *
     * <p>雾的半高（格 ×100 存）。以前只说半径（宽度），高度是渲染器里写死的
     * {@code 1.1 + 档位×0.1}（t1 1.2 → t5 1.6，几乎不变 ✗）。
     * 现在半高 = 半径 × {@link #HEIGHT_RATIO}（t1 4.5 格 → 半高 2.25；t5 9 格 → 半高 4.5）✓。
     */
    private static final net.minecraft.network.syncher.EntityDataAccessor<Integer> DATA_FOG_HEIGHT =
            net.minecraft.network.syncher.SynchedEntityData.defineId(
                    DarkFogCloudEntity.class, net.minecraft.network.syncher.EntityDataSerializers.INT);

    /** 同步施法者实体 id；客户端据此给自己的黑雾保留清晰视野。 */
    private static final net.minecraft.network.syncher.EntityDataAccessor<Integer> DATA_OWNER_ID =
            net.minecraft.network.syncher.SynchedEntityData.defineId(
                    DarkFogCloudEntity.class, net.minecraft.network.syncher.EntityDataSerializers.INT);
    /** Explicit owner sync: guarantees caster-only vision even if SpellCloud delays owner resolution. */
    private static final net.minecraft.network.syncher.EntityDataAccessor<java.util.Optional<java.util.UUID>> DATA_FOG_OWNER =
            net.minecraft.network.syncher.SynchedEntityData.defineId(
                    DarkFogCloudEntity.class, net.minecraft.network.syncher.EntityDataSerializers.OPTIONAL_UUID);

    @Override
    protected void defineSynchedData() {
        // SpellCloud owns spell id, data index and radius trackers used during spell creation.
        // Initialise them before extending the tracker table with our overlay fields.
        // The Connector compile-time engine jar exposes this inherited method under its SRG name.
        super.m_8097_();
        this.entityData.define(DATA_FOG_TIER, 0);
        this.entityData.define(DATA_FOG_RADIUS, 0);
        this.entityData.define(DATA_FOG_HEIGHT, 0);
        this.entityData.define(DATA_OWNER_ID, -1);
        this.entityData.define(DATA_FOG_OWNER, java.util.Optional.empty());
    }

    /** 客户端用：这片雾是第几档（0 = 还不知道）✓。 */
    public int syncedTier() {
        return this.entityData.get(DATA_FOG_TIER);
    }

    /** 客户端用：这片雾的半径（格；0 = 还不知道）✓。 */
    public double syncedRadius() {
        return this.entityData.get(DATA_FOG_RADIUS) / 100.0D;
    }

    /** 客户端用：这片雾的<b>半高</b>（格）✓ —— 没同步到就退回"按半径算"。 */
    public double syncedHeight() {
        int stored = this.entityData.get(DATA_FOG_HEIGHT);
        if (stored > 0) {
            return stored / 100.0D;
        }
        return fogHalfHeightFor(syncedRadius());
    }

    /** 半高与半径的比例 ✓（2026-11-11 作者：高度也要跟着档位长）—— 规则在 {@link DarkFogRules} 里 ✓。 */
    public static final double HEIGHT_RATIO = DarkFogRules.HEIGHT_RATIO;

    /**
     * 一片雾的<b>半高</b>（格）✓ —— t1 半径 4.5 ⇒ 半高 2.25；t5 半径 9 ⇒ 半高 4.5。
     *
     * <p>⚠️ Boss 领域雾（半径 &gt; {@link #DOMAIN_RADIUS}）保持 3.2 格：
     * 那是"整个战场都是雾"的场面，长高没有意义（而且 150×0.5 = 75 格会把整张图糊掉 ✗）✓。
     */
    public static double fogHalfHeightFor(double radius) {
        return DarkFogRules.halfHeight(radius);
    }

    /** 这片雾的半高（格）✓（服务端判定用）。 */
    public double fogHalfHeight() {
        double radius = circleRadius > 0.0D ? circleRadius : syncedRadius();
        return fogHalfHeightFor(radius);
    }

    /** 客户端用：这片雾是谁释放的（-1 = 尚未同步）。 */
    public int syncedOwnerId() {
        return this.entityData.get(DATA_OWNER_ID);
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
        // ★ 2026-11-11：半高也跟着半径同步给客户端（渲染器用它决定雾片的高度）✓
        int storedHeight = (int) Math.round(fogHalfHeightFor(radius) * 100.0D);
        if (this.entityData.get(DATA_FOG_HEIGHT) != storedHeight) {
            this.entityData.set(DATA_FOG_HEIGHT, storedHeight);
        }
        LivingEntity owner = resolveOwner();
        int ownerId = owner == null ? -1 : owner.getId();
        if (this.entityData.get(DATA_OWNER_ID) != ownerId) {
            this.entityData.set(DATA_OWNER_ID, ownerId);
        }
        java.util.Optional<java.util.UUID> ownerUuid = owner == null
                ? java.util.Optional.empty() : java.util.Optional.of(owner.getUUID());
        if (!this.entityData.get(DATA_FOG_OWNER).equals(ownerUuid)) {
            this.entityData.set(DATA_FOG_OWNER, ownerUuid);
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
        syncAppearance();
        gripIntruders();

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
     * ★ <b>把雾里的敌人"抓紧"</b> —— 作者 2026-11-05：
     * 「黑雾加一点那个就是，中心是不是会冒出手，把敌人抓紧黑雾之中」✓
     *
     * <p>视觉那边（{@code DarkMistRenderer}）已经让雾心冒出手、在敌人身上各抓一只；
     * 这里补上<b>手感</b>，让"被抓住"不只是画面：
     * <ul>
     *   <li><b>往雾心拽</b>：每 {@link #GRIP_INTERVAL} tick 给一个朝中心的小推力
     *       （{@code hurtMarked} 让服务端把速度同步给客户端 ⇒ 玩家也会被拽 ✓）；</li>
     *   <li><b>重减速</b>：按档位给 {@code MOVEMENT_SLOWDOWN}（最高 IV），持续刷新 ⇒ 走不出去 ✓；</li>
     *   <li><b>压跳</b>：向上的速度砍到三成 ⇒ 跳不出雾 ✓（不锁 AI、不 noAi，敌人还能打你）。</li>
     * </ul>
     *
     * <p>不拽施法者自己和队友（{@code isAlliedTo}）✓；不处理非暗雾（别的 cloud 法术不受影响）✓。
     *
     * <p>★ <b>Boss 领域雾要单独调</b>（作者 2026-11-05：「连着黑暗boss的也一起改了」）：
     * 巨人的领域把半径放大了 30 倍（{@code TNDarkGiantFog}）⇒ 同一套数值会变成
     * "整个屏幕都被拽" ✗。所以 domain 雾（{@code radius > 64}）改用更轻的一套：
     * 间隔 10 tick、推力 0.06、减速封顶 III —— 范围大、手感必须软 ✓。
     */
    private static final double DOMAIN_RADIUS = 64.0D;

    private void gripIntruders() {
        if (!isDarkFog()) {
            return;
        }
        double radius = circleRadius > 0.0D ? circleRadius : syncedRadius();
        if (radius <= 0.0D) {
            return;
        }
        boolean domain = radius > DOMAIN_RADIUS;
        if (tickCount % (domain ? GRIP_INTERVAL * 2 : GRIP_INTERVAL) != 0) {
            return;
        }
        LivingEntity owner = resolveOwner();
        int tier = Math.max(1, tier());
        int slowAmp = domain ? Math.min(3, tier) : Math.min(4, tier);
        // ★ 2026-11-11：雾现在是一个"圆柱"——半高也跟着档位长（见 fogHalfHeightFor）✓
        double halfHeight = fogHalfHeight();
        // 瞬移进雾里之后停在离雾心多远（雾半径的 45%，至少 0.6 格）
        double inside = DarkFogRules.insideDistance(radius);
        // ★ 2026-11-06 作者：「黑雾抓周围的敌人的距离要很远啊，起码比黑雾范围大」
        //   ⇒ 抓取半径 = 雾半径 × 1.45（原来就是雾半径本身 ⇒ 只有雾最里面的人被抓住 ✗）。
        //   Boss 领域雾（150 格）再乘 1.45 就成了两百格，太重 ⇒ 单独封顶 96 格 ✓
        double grip = DarkFogRules.gripRadius(radius);
        double r2 = grip * grip;
        for (LivingEntity victim : level().getEntitiesOfClass(LivingEntity.class,
                getBoundingBox().inflate(grip + 1.0D), LivingEntity::isAlive)) {
            if (owner != null && (victim == owner || victim.isAlliedTo(owner))) {
                continue;
            }
            // ★ Boss 领域雾：施法者是巨人 ⇒ 只抓玩家，别把 BOSS 自己召唤的小怪按在雾里 ✗
            //   （作者 2026-11-05："连着黑暗boss的也一起改了"）
            if (!(owner instanceof net.minecraft.world.entity.player.Player)
                    && victim instanceof net.minecraft.world.entity.monster.Monster) {
                continue;
            }
            // 高度也要算：雾是圆柱，头顶/脚下太远的敌人不该被拽进雾底 ✗
            double victimCentreY = victim.getY() + victim.getBbHeight() * 0.5D;
            if (Math.abs(victimCentreY - getY()) > halfHeight + victim.getBbHeight() * 0.5D) {
                continue;
            }
            double dx = getX() - victim.getX();
            double dz = getZ() - victim.getZ();
            double d2 = dx * dx + dz * dz;
            if (d2 > r2) {
                continue;
            }
            double d = Math.sqrt(d2);
            // ★ 2026-11-11 作者：「我要你瞬间给他拉进来」——
            //   以前是每 6 tick 给一个很小的速度（0.10×距离），要拽好几秒才进得去 ✗；
            //   现在够到就直接**瞬移进雾里**（离雾心 ≈ 半径 45% 的地方）✓，
            //   再补一串黑雾粒子，读作"被一把抓进去了" ✓
            //
            // ⚠️ **Boss 领域雾不瞬移** ✗：那是 150 格半径的战场雾，
            //    瞬移会把玩家直接甩到 BOSS 脸上（67 格），属于"游戏替我死" ✗ ——
            //    领域雾仍然只给一点轻推（和以前一样）✓
            if (domain) {
                if (d > 0.5D) {
                    double pull = 0.06D * Math.min(1.0D, d / radius);
                    Vec3 v = victim.getDeltaMovement();
                    victim.setDeltaMovement(v.x + dx / d * pull, v.y, v.z + dz / d * pull);
                    victim.hurtMarked = true;
                }
            } else if (d > inside) {
                double tx = getX() - dx / d * inside;
                double tz = getZ() - dz / d * inside;
                double ty = Math.max(getY() - halfHeight + 0.1D,
                        Math.min(getY() + halfHeight - 0.1D, victim.getY()));
                victim.teleportTo(tx, ty, tz);
                victim.setDeltaMovement(0.0D, -0.12D, 0.0D);
                victim.fallDistance = 0.0F;
                victim.hurtMarked = true;
                if (level() instanceof ServerLevel server) {
                    server.sendParticles(net.minecraft.core.particles.ParticleTypes.SQUID_INK,
                            tx, victimCentreY, tz, 6, 0.2D, 0.2D, 0.2D, 0.02D);
                }
            }
            victim.addEffect(new net.minecraft.world.effect.MobEffectInstance(
                    net.minecraft.world.effect.MobEffects.MOVEMENT_SLOWDOWN,
                    40, slowAmp, false, false, true));
            Vec3 v2 = victim.getDeltaMovement();
            if (v2.y > 0.08D) {
                victim.setDeltaMovement(v2.x, v2.y * 0.35D, v2.z);
            }
        }
    }

    /**
     * 施法者。
     *
     * <p>{@code SpellCloud} 把主人存在**私有**字段里，但对外的口子是一个原版方法
     * （{@code TraceableEntity.getOwner()} 在 SRG 里叫 {@code m_19749_}）✓ ——
     * 引擎自己的 {@code SpellCloud.tick} 也是拿它去结算范围伤害的 ✓；
     * 换维度时它会自己按 UUID 重新解析 ✓，所以这里直接用就够了，不用再存一份 ✗。
     */
    /** Allows recasting to replace only this caster's existing fog domain. */
    public boolean isOwnedBy(LivingEntity caster) {
        return isOwnedBy((Entity) caster);
    }

    private LivingEntity resolveOwner() {
        Entity owner = this.m_19749_();
        return owner instanceof LivingEntity living ? living : null;
    }

    /** Client-safe ownership check used for caster-only vision and outlines. */
    public boolean isOwnedBy(Entity candidate) {
        if (candidate == null) {
            return false;
        }
        java.util.Optional<java.util.UUID> synced = this.entityData.get(DATA_FOG_OWNER);
        if (synced.isPresent()) {
            return synced.get().equals(candidate.getUUID());
        }
        Entity owner = this.m_19749_();
        return owner != null && owner.getUUID().equals(candidate.getUUID());
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
            // 参数顺序：粒子 / x y z / 数量 / dx dy dz（速度抖动）/ 速度
            server.sendParticles(options,
                    getX(), getY() + 0.35D, getZ(),
                    18,
                    radius * 0.82D, 0.75D, radius * 0.82D,
                    0.012D);
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
