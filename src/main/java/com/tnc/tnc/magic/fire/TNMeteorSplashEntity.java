package com.tnc.tnc.magic.fire;

import com.tnc.tnc.magic.TNOrbEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraft.util.RandomSource;

/**
 * 陨星坠（火球链 t5）落地那一下<b>甩出去的岩浆</b>。
 *
 * <p>作者 2026-10-18：「火球链中 t5 的法术要求在火球爆炸后再爆炸范围内出现黑曜石和岩浆，
 * 并且爆炸时也会有岩浆飞出，<b>这里的岩浆是真实的不是用来修饰的</b>」。
 *
 * <h2>为什么是一个实体</h2>
 * 「岩浆飞出去」必须是<b>真的岩浆方块在移动</b>（会烧人、会发光、落地就留一滩），
 * 不是粒子 ✗ 也不能是一次性摆一排悬空的方块 ✗。
 * 所以这里每一 tick 干三件事：
 * <ol>
 *   <li><b>擦掉上一 tick 放下的那块</b>（岩浆是"飞过去"的，不是拖一条带子 ✗）；</li>
 *   <li>按抛物线推进，<b>在新的位置真放一块岩浆</b>；</li>
 *   <li>撞到实体方块就<b>落定</b>：在它上面留一滩岩浆，这一颗不再动。</li>
 * </ol>
 *
 * <p>放方块一律用 {@code flag = 2}（不触发邻居更新）：岩浆块原地待着、不流动，
 * 下一 tick 才被擦掉 —— 否则它会像水一样顺着地形淌开，一眼就看出是"泄漏"而不是"溅射" ✗。
 *
 * <p>纯服务端：客户端只看得见它放出来的岩浆，所以渲染器什么都不画（见
 * {@code client/TNMeteorSplashRenderer}）。
 */
public final class TNMeteorSplashEntity extends Entity {

    /** 这团溅射活多久（tick）。够它飞出去、落下来。 */
    public static final int MAX_TICKS = 18;

    /** 往外甩几块岩浆。 */
    private static final int DROPS = 12;

    /** 重力（格/tick²）。 */
    private static final double GRAVITY = 0.085D;

    private final double[] px = new double[DROPS];
    private final double[] py = new double[DROPS];
    private final double[] pz = new double[DROPS];
    private final double[] vx = new double[DROPS];
    private final double[] vy = new double[DROPS];
    private final double[] vz = new double[DROPS];
    private final boolean[] alive = new boolean[DROPS];
    /** 上一 tick 放下的那块岩浆 —— 这一 tick 要擦掉它。 */
    private final BlockPos[] trail = new BlockPos[DROPS];

    public TNMeteorSplashEntity(EntityType<? extends TNMeteorSplashEntity> type, Level level) {
        super(type, level);
        noPhysics = true;
        setNoGravity(true);
    }

    /** 由陨石命中时调用（只在服务端）。{@code at} 是命中点。 */
    public static TNMeteorSplashEntity cast(ServerLevel level, Vec3 at, RandomSource random) {
        TNMeteorSplashEntity splash = new TNMeteorSplashEntity(TNOrbEntities.METEOR_SPLASH.get(), level);
        splash.setPos(at.x, at.y, at.z);
        for (int i = 0; i < DROPS; i++) {
            // 从中心外一圈起飞（全从一个点起会变成泉水 ✗），方向散开
            double a = random.nextDouble() * Math.PI * 2.0D;
            double out = 0.6D + random.nextDouble() * 1.4D;
            double speed = 0.30D + random.nextDouble() * 0.80D;
            splash.px[i] = at.x + Math.cos(a) * out;
            splash.py[i] = at.y + 0.5D + random.nextDouble() * 0.7D;
            splash.pz[i] = at.z + Math.sin(a) * out;
            splash.vx[i] = Math.cos(a) * speed;
            splash.vz[i] = Math.sin(a) * speed;
            splash.vy[i] = 0.42D + random.nextDouble() * 0.62D;
            splash.alive[i] = true;
        }
        level.addFreshEntity(splash);
        return splash;
    }

    @Override
    protected void defineSynchedData() {
        // 纯服务端的表现：客户端只看得见它放出来的岩浆方块，本身没有要同步的状态
    }

    @Override
    public void tick() {
        super.tick();
        if (!(level() instanceof ServerLevel server)) {
            return;
        }
        if (tickCount > MAX_TICKS) {
            clearTrails(server);
            discard();
            return;
        }

        boolean anyAlive = false;
        for (int i = 0; i < DROPS; i++) {
            if (!alive[i]) {
                continue;
            }
            // ① 擦掉上一 tick 那块（只在它还是我们自己放的岩浆时才擦）
            BlockPos last = trail[i];
            if (last != null && server.getBlockState(last).is(Blocks.LAVA)) {
                server.setBlock(last, Blocks.AIR.defaultBlockState(), 2);
            }
            trail[i] = null;

            // ② 抛物线推进
            vy[i] -= GRAVITY;
            px[i] += vx[i];
            py[i] += vy[i];
            pz[i] += vz[i];

            BlockPos at = BlockPos.containing(px[i], py[i], pz[i]);
            if (!server.isLoaded(at)) {
                alive[i] = false;
                continue;
            }
            BlockState state = server.getBlockState(at);

            // ③ 撞到实体方块 ⇒ 落定：在它上面留一滩**真实的岩浆**
            if (!state.isAir() && state.getFluidState().isEmpty()) {
                BlockPos land = at.above();
                if (server.getBlockState(land).isAir()) {
                    server.setBlock(land, Blocks.LAVA.defaultBlockState(), 2);
                }
                alive[i] = false;
                continue;
            }

            // ④ 还在空中：真放一块岩浆（会烧人、会发光），下一 tick 擦掉
            if (state.isAir()) {
                server.setBlock(at, Blocks.LAVA.defaultBlockState(), 2);
                trail[i] = at;
            }
            anyAlive = true;
        }

        if (!anyAlive) {
            discard();
        }
    }

    /** 收尾：把自己放过还没擦掉的岩浆全部擦干净（否则空中会留下悬空的岩浆 ✗）。 */
    private void clearTrails(ServerLevel server) {
        for (int i = 0; i < DROPS; i++) {
            BlockPos last = trail[i];
            if (last != null && server.getBlockState(last).is(Blocks.LAVA)) {
                server.setBlock(last, Blocks.AIR.defaultBlockState(), 2);
            }
            trail[i] = null;
            alive[i] = false;
        }
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
    protected void readAdditionalSaveData(CompoundTag tag) {
        // ⚠️ 不能调 super：直接继承 Entity 时这两个方法是抽象的
        discard();
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        // 不持久化
    }
}
