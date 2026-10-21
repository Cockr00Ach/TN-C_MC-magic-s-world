package com.tnc.tnc.world.wind;

import com.tnc.tnc.TNMod;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.Item;
import net.minecraftforge.common.ForgeSpawnEggItem;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.RegisterEvent;

/** Forge registry bridge for the wind-realm duelist boss. */
@Mod.EventBusSubscriber(modid = TNMod.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class WindWardenRegistration {
    public static EntityType<WindWardenEntity> WIND_WARDEN;
    public static Item EGG;

    private WindWardenRegistration() {}

    @SubscribeEvent
    public static void register(RegisterEvent event) {
        event.register(Registries.ENTITY_TYPE, helper -> {
            WIND_WARDEN = EntityType.Builder
                    .<WindWardenEntity>of(WindWardenEntity::new, MobCategory.MONSTER)
                    .sized(WindWardenAttackRules.HITBOX_WIDTH, WindWardenAttackRules.HITBOX_HEIGHT)
                    .clientTrackingRange(16)
                    .updateInterval(2)
                    .build("tnc:wind_warden");
            helper.register(ResourceLocation.fromNamespaceAndPath(TNMod.MODID, "wind_warden"),
                    WIND_WARDEN);
        });
        event.register(Registries.ITEM, helper -> {
            EGG = new ForgeSpawnEggItem(() -> WIND_WARDEN, 0x1A8A60, 0xE2C25C,
                    new Item.Properties());
            helper.register(ResourceLocation.fromNamespaceAndPath(TNMod.MODID, "wind_warden_spawn_egg"),
                    EGG);
        });
    }

    @SubscribeEvent
    public static void attributes(EntityAttributeCreationEvent event) {
        event.put(WIND_WARDEN, WindWardenEntity.createAttributes().build());
    }

    @SubscribeEvent
    public static void creative(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey().equals(TNMod.TNC_TAB.getKey())) {
            event.accept(EGG);
        }
    }
}
