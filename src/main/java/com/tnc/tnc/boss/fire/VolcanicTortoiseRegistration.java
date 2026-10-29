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
    /** 喷吐火球（任务 5）：服务端权威的直线投射物。 */
    public static EntityType<VolcanicFireballEntity> FIREBALL;
    /** 火山喷发的抛射火球（任务 8）：抛物线 + 服务端落点预警。 */
    public static EntityType<VolcanicMortarEntity> MORTAR;
    /** 火球落地后的持续火焰区域。 */
    public static EntityType<VolcanicFireFieldEntity> FIRE_FIELD;
    public static EntityType<TortoiseRemainsEntity> REMAINS;
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

            REMAINS = EntityType.Builder.<TortoiseRemainsEntity>of(TortoiseRemainsEntity::new, MobCategory.MISC)
                    .sized(6.8F, 1.9F).fireImmune().clientTrackingRange(10).updateInterval(20)
                    .build("tnc:volcanic_tortoise_remains");
            helper.register(ResourceLocation.fromNamespaceAndPath("tnc", "volcanic_tortoise_remains"), REMAINS);

            FIREBALL = EntityType.Builder.<VolcanicFireballEntity>of(VolcanicFireballEntity::new, MobCategory.MISC)
                    .sized(1.2F, 1.2F)
                    .clientTrackingRange(10)
                    .updateInterval(1)
                    .build("tnc:volcanic_fireball");
            helper.register(ResourceLocation.fromNamespaceAndPath("tnc", "volcanic_fireball"), FIREBALL);

            MORTAR = EntityType.Builder.<VolcanicMortarEntity>of(VolcanicMortarEntity::new, MobCategory.MISC)
                    .sized(0.6F, 0.6F)
                    .clientTrackingRange(10)
                    .updateInterval(1)
                    .build("tnc:volcanic_mortar");
            helper.register(ResourceLocation.fromNamespaceAndPath("tnc", "volcanic_mortar"), MORTAR);

            FIRE_FIELD = EntityType.Builder.<VolcanicFireFieldEntity>of(VolcanicFireFieldEntity::new, MobCategory.MISC)
                    .sized(1.0F, 0.5F)
                    .fireImmune()
                    .clientTrackingRange(10)
                    .updateInterval(5)
                    .build("tnc:volcanic_fire_field");
            helper.register(ResourceLocation.fromNamespaceAndPath("tnc", "volcanic_fire_field"), FIRE_FIELD);
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
