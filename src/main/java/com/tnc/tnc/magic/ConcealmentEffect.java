package com.tnc.tnc.magic;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeMap;

/**
 * 隐匿：一分钟隐身，且敌对生物不会把施法者选作目标。
 *
 * <p>隐身本身沿用 {@link LivingEntity#setInvisible(boolean)}（与原版隐身同一条渲染路径），
 * 但“不被索敌”是原版隐身做不到的：原版只降低探测距离，怪仍然会锁定贴脸的玩家。
 * 所以这里每秒清一次附近敌对生物的目标，并在 {@link TNConcealmentEvents} 里拦截新的索敌。
 *
 * <p>结束条件不在这里判断——造成伤害或受到伤害时由事件直接 {@code removeEffect}，
 * 这样“提前结束”与自然到期走同一条清理路径（{@link #removeAttributeModifiers} 负责恢复可见）。
 */
public class ConcealmentEffect extends MobEffect {

    /** 清目标与补隐身的间隔：1 秒一次足够，且不会给服务端添负担。 */
    public static final int PULSE_TICKS = 20;

    /** 清目标的半径（格）。 */
    public static final double SUPPRESS_RADIUS = 32.0D;

    public ConcealmentEffect() {
        super(MobEffectCategory.BENEFICIAL, 0x6B5AA6);
    }

    @Override
    public boolean isDurationEffectTick(int duration, int amplifier) {
        return duration % PULSE_TICKS == 0;
    }

    @Override
    public void applyEffectTick(LivingEntity entity, int amplifier) {
        if (entity.level().isClientSide()) {
            return;
        }
        if (!entity.isInvisible()) {
            entity.setInvisible(true);
        }
        for (Mob mob : entity.level().getEntitiesOfClass(Mob.class,
                entity.getBoundingBox().inflate(SUPPRESS_RADIUS))) {
            if (mob.getTarget() == entity) {
                mob.setTarget(null);
            }
        }
    }

    @Override
    public void removeAttributeModifiers(LivingEntity entity, AttributeMap attributes, int amplifier) {
        super.removeAttributeModifiers(entity, attributes, amplifier);
        // 仍然带着原版隐身（药水/其他法术）时不要抢着把模型显出来。
        if (!entity.level().isClientSide() && !entity.hasEffect(MobEffects.INVISIBILITY)) {
            entity.setInvisible(false);
        }
    }

    @Override
    public boolean isInstantenous() {
        return false;
    }
}
