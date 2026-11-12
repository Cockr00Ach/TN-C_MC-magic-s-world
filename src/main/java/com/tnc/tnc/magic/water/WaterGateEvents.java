package com.tnc.tnc.magic.water;

import com.tnc.tnc.TNMod;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 「玩家攻击了某个敌人之后，法阵群就齐射」的那个**扳机** ✓（作者 2026-11-09）。
 *
 * <p>为什么用 {@link LivingHurtEvent} 而不是"右键/左键"：作者说的是"**攻击了某个敌人**"✓ ——
 * 判定标准应该是"真的打出了伤害"（近战、弓箭、别的法术都算 ✓），而不是"按了攻击键"✗
 * （空挥、打空气、打队友都不该齐射 ✗）。
 *
 * <p>两道闸在 {@link TNWaterGateEntity#onOwnerDamaged} 里（阶段必须是 IDLE + 2 秒冷却 ✓）；
 * 这里只做三件过滤：
 * <ol>
 *   <li><b>只在服务端</b>（{@code LivingHurtEvent} 两侧都会发 ✓）；</li>
 *   <li><b>伤害来自玩家</b>（{@code getEntity()} = 造成伤害的人 ✓）；</li>
 *   <li><b>不是我们自己的水波打的</b> ✗ —— 否则齐射的每一道都会再触发一轮，直接无限循环 ✗
 *       （水波的 {@code getDirectEntity()} 是 {@link TNWaterLanceEntity} ✓）。</li>
 * </ol>
 */
@Mod.EventBusSubscriber(modid = TNMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class WaterGateEvents {

    private WaterGateEvents() {
    }

    @SubscribeEvent
    public static void onHurt(LivingHurtEvent event) {
        if (event.getAmount() <= 0.0F || event.getEntity().level().isClientSide()) {
            return;
        }
        if (event.getSource().getDirectEntity() instanceof TNWaterLanceEntity) {
            return;         // 自己的水波不算"玩家攻击"（不然一轮齐射会滚成无限轮 ✗）
        }
        if (!(event.getSource().getEntity() instanceof ServerPlayer player)) {
            return;
        }
        LivingEntity victim = event.getEntity();
        if (victim == player || !WaterSpellRules.enemy(player, victim)) {
            return;
        }
        TNWaterGateEntity.onOwnerDamaged(player, victim);
    }
}
