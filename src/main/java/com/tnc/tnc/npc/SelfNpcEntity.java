package com.tnc.tnc.npc;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

/**
 * A. Self —— 酒馆老板（TN-C 第一个自己的 NPC）。
 *
 * <p>他是<b>对话 NPC</b>：站着不动、不会死、右键说话。剧情里第一场戏就在他的酒馆
 * （见 {@code 剧情/开场_分离之后.md} 第一场「第二杯酒」）。
 *
 * <p>基类选 {@link PathfinderMob}（原版村民/怪物也是这条线）而不是普通 {@code Mob}：
 * 它是"会走路的生物"的标准基类，之后要给 Self 加"在酒馆里转悠""看着客人"这类行为时，
 * 直接加 {@code goal} 就行，不用换基类。
 *
 * <p>三个刻意的设计点（都是"NPC 不该有的行为"）：
 * <ol>
 *   <li>{@link #removeWhenFarAway} 返回 false —— <b>玩家走远也不会消失</b>；
 *   <li>{@link #isPersistenceRequired} 返回 true —— 不参与原版的"清怪"逻辑；
 *   <li>{@link #hurt} 除了创造模式玩家之外<b>不掉血</b> —— 剧情 NPC 不该被误杀。
 * </ol>
 */
public class SelfNpcEntity extends PathfinderMob {

    public SelfNpcEntity(EntityType<? extends SelfNpcEntity> type, Level level) {
        super(type, level);
    }

    /** 属性：满血 20、不主动攻击、移速偏低（老板不跑）。 */
    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 20.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.25D)
                .add(Attributes.FOLLOW_RANGE, 16.0D);
    }

    @Override
    public boolean removeWhenFarAway(double distanceToClosestPlayer) {
        return false;
    }

    @Override
    public boolean isPersistenceRequired() {
        return true;
    }

    @Override
    public boolean hurt(net.minecraft.world.damagesource.DamageSource source, float amount) {
        // 创造模式的玩家可以打掉他（放错了要能清掉），其他伤害一律免疫。
        if (source.getEntity() instanceof Player player && player.isCreative()) {
            return super.hurt(source, amount);
        }
        return false;
    }

    @Override
    protected InteractionResult mobInteract(Player player, InteractionHand hand) {
        // 对话在下一步接（先说一句话验证"右键能叫到他"）。
        if (!this.level().isClientSide) {
            player.displayClientMessage(
                    net.minecraft.network.chat.Component.literal(
                            "\u00a77[Self] \u00a7f\u4f60\u597d\u554a\uff0c\u8fdc\u9053\u800c\u6765\u7684\u5ba2\u4eba\u3002"),
                    false);
        }
        return InteractionResult.sidedSuccess(this.level().isClientSide);
    }
}
