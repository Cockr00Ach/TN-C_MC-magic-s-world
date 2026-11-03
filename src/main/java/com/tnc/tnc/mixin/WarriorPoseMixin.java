package com.tnc.tnc.mixin;

import com.tnc.tnc.warrior.client.WarriorPose;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 玩家全身动作的注入点：原版把姿势算完之后，再让连招系统在骨骼上叠一层。
 *
 * <p><b>为什么挂在 {@code PlayerModel#setupAnim} 而不是 {@code HumanoidModel#setupAnim}：</b>
 * {@code PlayerModel#setupAnim} 调完 {@code super.setupAnim(...)} 之后还要跑
 * {@code setupAttackAnimation(...)}（挥砍/使用物品的手臂动作）和潜行/骑乘修正。
 * 只挂在父类的 TAIL，我们摆好的手臂会被这些"后处理"整个覆盖掉 —— 连招一放就会
 * {@code swing()}，{@code attackTime > 0}，表现就是"按了连招，人还是原来那样"，
 * 而单元测试和 GameTest 全绿（它们不渲染）。
 *
 * <p>挂在 {@code PlayerModel} 的 TAIL 上，是这一帧最后一个动骨骼的人，才不会被覆盖。
 * 只在 {@code entity instanceof Player} 时生效，其他原版人形生物完全不受影响。
 *
 * <p><b>参数类型必须是 {@code LivingEntity} 而不是 {@code Player}</b>：
 * {@code PlayerModel<T extends LivingEntity>}，{@code setupAnim(T, ...)} 擦除后的描述符是
 * {@code (Lnet/minecraft/world/entity/LivingEntity;FFFFF)V}。写成 {@code Player} 会
 * {@code InvalidInjectionException: Invalid descriptor}（踩过）。
 * refmap {@code src/main/resources/tnc.warrior.refmap.json} 里的签名也要一致，
 * 否则启动直接 {@code InvalidInjectionException}。
 */
@Mixin(PlayerModel.class)
public abstract class WarriorPoseMixin {

    @Inject(method = "setupAnim", at = @At("TAIL"))
    private void tnc$warriorPose(LivingEntity entity, float limbSwing, float limbSwingAmount,
                                 float ageInTicks, float netHeadYaw, float headPitch, CallbackInfo callback) {
        if (entity instanceof Player player) {
            WarriorPose.apply(player, (HumanoidModel<?>) (Object) this);
        }
    }
}
