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

/** Forge registry bridge for 苍岚战将 / Canglan Warlord. */
@Mod.EventBusSubscriber(modid = TNMod.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class CanglanWarlordRegistration {
    public static EntityType<CanglanWarlordEntity> CANGLAN_WARLORD;
    public static Item EGG;
    public static EntityType<CanglanEchoEntity> ECHO;
    public static EntityType<CanglanTempestEntity> TEMPEST;

    private CanglanWarlordRegistration() {}

    @SubscribeEvent
    public static void register(RegisterEvent event) {
        event.register(Registries.ENTITY_TYPE, helper -> {
            ECHO = EntityType.Builder.<CanglanEchoEntity>of(CanglanEchoEntity::new, MobCategory.MISC)
                    .sized(.1F,.1F).clientTrackingRange(24).updateInterval(2).noSave().build("tnc:canglan_echo");
            TEMPEST = EntityType.Builder.<CanglanTempestEntity>of(CanglanTempestEntity::new, MobCategory.MISC)
                    .sized(.5F,.5F).clientTrackingRange(24).updateInterval(1).noSave().build("tnc:canglan_tempest");
            helper.register(ResourceLocation.fromNamespaceAndPath("tnc","canglan_echo"),ECHO);
            helper.register(ResourceLocation.fromNamespaceAndPath("tnc","canglan_tempest"),TEMPEST);
            CANGLAN_WARLORD = EntityType.Builder
                    .<CanglanWarlordEntity>of(CanglanWarlordEntity::new, MobCategory.MONSTER)
                    .sized(CanglanWarlordAttackRules.HITBOX_WIDTH, CanglanWarlordAttackRules.HITBOX_HEIGHT)
                    .fireImmune()
                    .clientTrackingRange(16)
                    .updateInterval(2)
                    .build("tnc:canglan_warlord");
            helper.register(ResourceLocation.fromNamespaceAndPath(TNMod.MODID, "canglan_warlord"), CANGLAN_WARLORD);
        });
        event.register(Registries.ITEM, helper -> {
            // Wind-slate body, pale gale motes: the relic-metal palette of the arsenal blade.
            EGG = new ForgeSpawnEggItem(() -> CANGLAN_WARLORD, 0x2B4350, 0x9BE8E8,
                    new Item.Properties());
            helper.register(ResourceLocation.fromNamespaceAndPath(TNMod.MODID,
                    "canglan_warlord_spawn_egg"), EGG);
        });
    }

    @SubscribeEvent
    public static void attributes(EntityAttributeCreationEvent event) {
        event.put(CANGLAN_WARLORD, CanglanWarlordEntity.createAttributes().build());
    }

    @SubscribeEvent
    public static void creative(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey().equals(TNMod.TNC_TAB.getKey())) {
            event.accept(EGG);
        }
    }
}
