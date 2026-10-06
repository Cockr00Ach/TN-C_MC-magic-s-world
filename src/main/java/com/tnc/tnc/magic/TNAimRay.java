package com.tnc.tnc.magic;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * <b>准星指到哪一个敌人</b> —— 暗系那两条链"瞄准释放"共用的射线 ✓。
 *
 * <h2>★ 为什么必须单独有这一个类（这是一个真 bug 的修复）</h2>
 * 之前这条链（以及 {@link TNDarkAimMechanics}）用的是
 * <pre>HitResult hit = player.pick(32, 0, false);  if (hit instanceof EntityHitResult …)</pre>
 * —— 而 {@code Entity.pick} 的字节码是这样的（javap 实证 ✗）：
 * <pre>
 *   getEyePosition(partial) → getViewVector(partial) → end = eye + view × range
 *   → new ClipContext(eye, end, Block.OUTLINE, Fluid.NONE, this)
 *   → level.clip(...) → areturn
 * </pre>
 * 它<b>只做方块射线</b>，返回的永远是 {@code BlockHitResult}（或 MISS）✗ ——
 * <b>那个 {@code instanceof EntityHitResult} 恒为 false</b>，所以"瞄准锁定"从来没生效过 ✓。
 * 作者 2026-10-10 反馈「**现在黑夜之手怎么无法瞄准敌人了**」正是它 ✗
 * （重做前看起来"有索敌"，其实是<b>引擎投射物自己去找最近的敌人</b>，跟瞄准无关 ✗）。
 *
 * <h2>现在怎么做（照抄原版准星那一套）</h2>
 * 原版 {@code GameRenderer.pick} 就是两步：**方块先把射线截短**，再用
 * {@code ProjectileUtil.getEntityHitResult} 做实体射线 ⇒ 所以"隔着墙锁不到人"✓。
 * 这里完全是同一套逻辑 ✓：
 * <ol>
 *   <li>{@code player.pick(range, 0, false)} 只用来拿"墙在哪"⇒ 射线长度被它截短 ✓；</li>
 *   <li>{@code ProjectileUtil.getEntityHitResult(...)} 在截短后的线段上找**最近的活体** ✓；</li>
 *   <li>过滤：排除自己、旁观者、友军、以及<b>不可拾取</b>的实体 ✓
 *       （最后这条同时保证"我们自己的黑手不会挡住瞄准"—— 法术实体都覆写了
 *       {@code isPickable() → false} ✓）。</li>
 * </ol>
 */
public final class TNAimRay {

    /** 备用：别人想用别的长度时传自己的（默认见 {@link #AIM_RANGE}）✓。 */
    public static final double AIM_RANGE = 32.0D;

    private TNAimRay() {
    }

    /** 准星指到的那个敌人；指到方块/空气/友军/墙后 ⇒ null ✓。 */
    public static LivingEntity aimedEnemy(ServerPlayer player) {
        return aimedEnemy(player, AIM_RANGE);
    }

    public static LivingEntity aimedEnemy(ServerPlayer player, double range) {
        if (player == null || player.level() == null) {
            return null;
        }
        Vec3 eye = player.getEyePosition(1.0F);
        Vec3 view = player.getViewVector(1.0F);

        // ① 方块把射线截短（隔着墙不能锁人 ✓）
        HitResult blockHit = player.pick(range, 0.0F, false);
        double limit = blockHit.getType() == HitResult.Type.MISS
                ? range
                : Math.max(0.0D, blockHit.getLocation().distanceTo(eye));
        Vec3 end = eye.add(view.scale(limit));

        // ② 实体射线（这才是"准星上的敌人"✓）
        AABB box = player.getBoundingBox().expandTowards(view.scale(limit)).inflate(1.0D);
        EntityHitResult entityHit = net.minecraft.world.entity.projectile.ProjectileUtil
                .getEntityHitResult(player.level(), player, eye, end, box,
                        e -> e instanceof LivingEntity living
                                && living.isAlive()
                                && !living.isSpectator()
                                && e.isPickable()
                                && e != player
                                && !living.isAlliedTo(player));
        if (entityHit != null && entityHit.getEntity() instanceof LivingEntity living) {
            return living;
        }
        return null;
    }

    /** 准星指到的那个人是不是敌人（给判断"该不该锁定"用）✓。 */
    public static boolean isEnemy(ServerPlayer player, Entity entity) {
        return entity instanceof LivingEntity living
                && living.isAlive()
                && !living.isSpectator()
                && living != player
                && !living.isAlliedTo(player);
    }
}
