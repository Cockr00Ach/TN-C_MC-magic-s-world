package com.tnc.tnc.magic;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * <b>爆炸冲击波</b> —— 由法术 JSON 的 {@code SPAWN} 冲击动作在**爆炸点**生成 ✓
 * （作者 2026-09-22："爆炸的效果很差，我没有感受到爆炸波冲击出去的那种感觉"）。
 *
 * <h2>它做什么</h2>
 * <ul>
 *   <li><b>推人</b>：前 {@link #PUSH_TICKS} tick 把周围实体沿着"从爆心向外"的方向推出去
 *       （越近越强、略微上掀）✓ —— 这就是"被冲击波撞到"的手感；</li>
 *   <li><b>画环</b>：渲染器把魔法阵那张贴图当作**向外扩张的光环**画出来（见
 *       {@code TNMagicCircleRenderer}）✓；</li>
 *   <li><b>震屏</b>：客户端在附近侦测到它就给镜头加抖动
 *       （{@code client\TNSpellClientVisuals}）✓。</li>
 * </ul>
 *
 * <p>为什么继承 {@link TNMagicCircleEntity}：两者都是"贴地的一张面片"，共用同步字段与渲染器 ✓；
 * 差别只有"扩张 + 推人"这一段逻辑，所以放在子类里，注册成另一个实体类型
 * （引擎的 SPAWN 动作只能指定<b>实体类型</b>，没法带参数 ✗ —— 所以用类型区分形态）。
 */
public class TNShockwaveEntity extends TNMagicCircleEntity {

    /** 存在时长（tick）：1.5 秒，够看清一圈扩出去。 */
    private static final int LIFE = 30;
    /** 最终半径（格）——渲染器按年龄把它从 30% 扩到 100% ✓。 */
    private static final double MAX_RADIUS = 13.0D;
    /** 前多少 tick 出力推人。 */
    private static final int PUSH_TICKS = 10;
    /** 推开的最大速度（格/tick）。 */
    private static final double PUSH_STRENGTH = 1.6D;
    /** 上掀分量 —— 贴着地面被"掀"一下的感觉。 */
    private static final double PUSH_UP = 0.45D;

    public TNShockwaveEntity(EntityType<? extends TNShockwaveEntity> type, Level level) {
        super(type, level);
        this.configure(MAX_RADIUS, LIFE);
    }

    @Override
    public void tick() {
        super.tick();
        if (this.level().isClientSide() || this.tickCount > PUSH_TICKS) {
            return;
        }
        ServerLevel level = (ServerLevel) this.level();
        // 冲击波前沿随时间向外走：3 格 -> 约 19 格
        double front = 3.0D + this.tickCount * 1.6D;
        Vec3 center = this.position();
        for (Entity entity : level.getEntities(this, this.getBoundingBox().inflate(front))) {
            if (!(entity instanceof LivingEntity target)) {
                continue;       // 只管活物：这个实体本身不是 LivingEntity，所以不用排除自己 ✓
            }
            Vec3 away = target.position().subtract(center);
            double distance = away.length();
            if (distance > front) {
                continue;       // 还没扫到它
            }
            Vec3 push = distance < 0.05D ? new Vec3(1.0D, 0.0D, 0.0D) : away.scale(1.0D / distance);
            double falloff = 1.0D - (distance / (front + 1.0D));      // 越近越强
            target.push(push.x * PUSH_STRENGTH * falloff, PUSH_UP * falloff, push.z * PUSH_STRENGTH * falloff);
            target.hurtMarked = true;                                  // 让客户端也动起来 ✓
        }
    }
}
