package com.tnc.tnc.mount;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = "tnc")
public final class MountRiderDamage {
    private MountRiderDamage() {}

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void absorbRiderDamage(LivingHurtEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer rider)
                || !(rider.getVehicle() instanceof LandMount mount)
                || event.getSource().is(DamageTypes.GENERIC_KILL)
                || !Float.isFinite(event.getAmount()) || event.getAmount() <= 0) return;
        event.setAmount(mount.absorbRiderDamage(rider, event.getSource(), event.getAmount()));
    }
}
