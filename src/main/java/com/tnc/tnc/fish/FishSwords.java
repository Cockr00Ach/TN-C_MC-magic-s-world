package com.tnc.tnc.fish;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.RegisterEvent;

@Mod.EventBusSubscriber(modid = "tnc", bus = Mod.EventBusSubscriber.Bus.MOD)
public final class FishSwords {
    public static final ResourceLocation LIVING_ID = ResourceLocation.fromNamespaceAndPath("tnc", "living_fish_sword");
    public static final ResourceLocation METAL_ID = ResourceLocation.fromNamespaceAndPath("tnc", "metal_fish_sword");
    private static FishSwordItem living, metal;
    private static EntityType<FishSchoolEntity> school;
    private FishSwords() {}
    public static EntityType<FishSchoolEntity> school() { return school; }
    public static FishSwordItem living() { return living; }
    public static FishSwordItem metal() { return metal; }
    @SubscribeEvent public static void register(RegisterEvent event) {
        event.register(Registries.ITEM, helper -> {
            living = new FishSwordItem(false);
            metal = new FishSwordItem(true);
            helper.register(LIVING_ID, living);
            helper.register(METAL_ID, metal);
        });
        event.register(Registries.ENTITY_TYPE, helper -> {
            school=EntityType.Builder.<FishSchoolEntity>of(FishSchoolEntity::new,MobCategory.MISC)
                    .sized(.5f,.5f).clientTrackingRange(8).updateInterval(1).noSave().noSummon().build("tnc:fish_school");
            helper.register(ResourceLocation.fromNamespaceAndPath("tnc","fish_school"),school);
        });
    }
    @SubscribeEvent public static void creative(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.COMBAT || event.getTabKey().location().equals(ResourceLocation.fromNamespaceAndPath("tnc", "main"))) {
            event.accept(living());
            event.accept(metal());
        }
    }
}
