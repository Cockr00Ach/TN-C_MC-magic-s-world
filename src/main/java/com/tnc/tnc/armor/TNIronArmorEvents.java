package com.tnc.tnc.armor;

import com.tnc.tnc.TNMod;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 铁甲的 99% 减伤 ✓ —— 只在这里实现（一道乘法 ✓）。
 *
 * <p>为什么用事件而不是护甲属性：原版护甲减伤有上限（约 80%）✗，写多少点护甲都到不了 99% ✓；
 * 而 {@code LivingHurtEvent} 里直接改数值就是"最终减伤" ✓（对近战/远程/魔法等都生效 ✓）。
 */
@Mod.EventBusSubscriber(modid = TNMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class TNIronArmorEvents {

    /** 只吃 1% 伤害 = 99% 减伤 ✓（想改成 90% 就写成 0.1F ✓）。 */
    private static final float DAMAGE_TAKEN = 0.01F;

    private TNIronArmorEvents() {
    }

    @SubscribeEvent
    public static void onLivingHurt(LivingHurtEvent event) {
        LivingEntity victim = event.getEntity();
        if (victim.level().isClientSide()) {
            return;
        }
        if (victim.getItemBySlot(EquipmentSlot.CHEST).getItem() instanceof TNIronArmorItem) {
            event.setAmount(event.getAmount() * DAMAGE_TAKEN);
        }
    }
}