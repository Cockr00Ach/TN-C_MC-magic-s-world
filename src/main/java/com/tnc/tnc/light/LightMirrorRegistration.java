package com.tnc.tnc.light;

import com.tnc.tnc.TNMod;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.*;
import net.minecraft.world.item.*;
import net.minecraftforge.common.ForgeSpawnEggItem;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.RegisterEvent;

@Mod.EventBusSubscriber(modid="tnc", bus=Mod.EventBusSubscriber.Bus.MOD)
public final class LightMirrorRegistration {
    public static EntityType<TNLightMirrorEntity> MIRROR;
    public static EntityType<ApolloAcolyteEntity> ACOLYTE;
    public static final Item[] ITEMS = new Item[5];
    public static Item EGG;
    @SubscribeEvent public static void register(RegisterEvent event) {
        event.register(Registries.ENTITY_TYPE, helper -> {
            MIRROR = EntityType.Builder.<TNLightMirrorEntity>of(TNLightMirrorEntity::new, MobCategory.MISC)
                    .sized(1.4F, 2.6F).clientTrackingRange(16).updateInterval(1).build("tnc:light_mirror");
            ACOLYTE = EntityType.Builder.<ApolloAcolyteEntity>of(ApolloAcolyteEntity::new, MobCategory.MONSTER)
                    .sized(.65F, 2.15F).clientTrackingRange(12).build("tnc:apollo_acolyte");
            helper.register(id("light_mirror"), MIRROR); helper.register(id("apollo_acolyte"), ACOLYTE);
        });
        event.register(Registries.ITEM, helper -> {
            for (int i=0; i<5; i++) { ITEMS[i] = new LightMirrorItem(i+1); helper.register(id("light_mirror_t"+(i+1)), ITEMS[i]); }
            EGG = new ForgeSpawnEggItem(() -> ACOLYTE, 0xeee6cf, 0xb28737, new Item.Properties());
            helper.register(id("apollo_acolyte_spawn_egg"), EGG);
        });
    }
    private static ResourceLocation id(String path) { return ResourceLocation.fromNamespaceAndPath("tnc", path); }
    @SubscribeEvent public static void attributes(EntityAttributeCreationEvent event) { event.put(ACOLYTE, ApolloAcolyteEntity.attributes().build()); }
    @SubscribeEvent public static void creative(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey().equals(TNMod.TNC_TAB.getKey())) { for (Item item : ITEMS) event.accept(item); event.accept(EGG); }
    }
}
