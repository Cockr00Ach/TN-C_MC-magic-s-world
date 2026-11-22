package com.tnc.tnc.boss.eclipse;

import com.tnc.tnc.TNMod;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.RegisterEvent;

@Mod.EventBusSubscriber(modid = TNMod.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class EclipseRegistration {
    public static EntityType<EclipseLeviathanEntity> ECLIPSE_LEVIATHAN;

    private EclipseRegistration() {}

    @SubscribeEvent
    public static void register(RegisterEvent event) {
        event.register(Registries.ENTITY_TYPE, helper -> {
            var id = ResourceLocation.fromNamespaceAndPath(TNMod.MODID, "eclipse_leviathan");
            ECLIPSE_LEVIATHAN = EntityType.Builder.<EclipseLeviathanEntity>of(EclipseLeviathanEntity::new, MobCategory.MISC)
                    .sized(5.5F, 4.0F).clientTrackingRange(12).updateInterval(2)
                    .noSave().build(id.toString());
            helper.register(id, ECLIPSE_LEVIATHAN);
        });
    }

    @SubscribeEvent
    public static void attributes(EntityAttributeCreationEvent event) {
        event.put(ECLIPSE_LEVIATHAN, EclipseLeviathanEntity.attributes().build());
    }
}
