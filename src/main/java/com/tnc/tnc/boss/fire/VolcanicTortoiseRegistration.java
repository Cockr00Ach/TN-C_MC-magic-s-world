package com.tnc.tnc.boss.fire;

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

/** 火山巨龟的注册（实体类型 / 刷怪蛋 / 属性 / 创造模式页）。 */
@Mod.EventBusSubscriber(modid = "tnc", bus = Mod.EventBusSubscriber.Bus.MOD)
public final class VolcanicTortoiseRegistration {
    public static EntityType<VolcanicTortoiseEntity> TYPE;
    public static Item EGG;

    @SubscribeEvent
    public static void register(RegisterEvent event) {
        event.register(Registries.ENTITY_TYPE, helper -> {
            TYPE = EntityType.Builder.<VolcanicTortoiseEntity>of(VolcanicTortoiseEntity::new, MobCategory.MONSTER)
                    .sized(VolcanicTortoiseEntity.HITBOX_WIDTH, VolcanicTortoiseEntity.HITBOX_HEIGHT)
                    .fireImmune()
                    .clientTrackingRange(32)
                    .updateInterval(2)
                    .build("tnc:volcanic_tortoise");
            helper.register(ResourceLocation.fromNamespaceAndPath("tnc", "volcanic_tortoise"), TYPE);
        });
        event.register(Registries.ITEM, helper -> {
            EGG = new ForgeSpawnEggItem(() -> TYPE, 0x241C18, 0xF06A1E, new Item.Properties());
            helper.register(ResourceLocation.fromNamespaceAndPath("tnc", "volcanic_tortoise_spawn_egg"), EGG);
        });
    }

    @SubscribeEvent
    public static void attributes(EntityAttributeCreationEvent event) {
        event.put(TYPE, VolcanicTortoiseEntity.createAttributes().build());
    }

    @SubscribeEvent
    public static void creative(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey().equals(TNMod.TNC_TAB.getKey())) {
            event.accept(EGG);
        }
    }
}
