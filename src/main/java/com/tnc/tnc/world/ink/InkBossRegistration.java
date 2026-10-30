package com.tnc.tnc.world.ink;

import com.tnc.tnc.TNMod;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.RegisterEvent;

/** Forge registry bridge for the ink-realm boss. */
@Mod.EventBusSubscriber(modid = TNMod.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class InkBossRegistration {
    public static EntityType<InkRitualEntity> INK_RITUAL;
    public static EntityType<InkPortalEntity> INK_PORTAL;
    public static EntityType<InkAbyssDrakeEntity> INK_ABYSS_DRAKE;
    public static EntityType<InkCraneBossEntity> INK_CRANE_BOSS;
    public static EntityType<InkCranePhantomEntity> INK_CRANE_PHANTOM;
    public static EntityType<InkWingedTigerEntity> INK_WINGED_TIGER;
    public static EntityType<InkTigerEffectEntity> INK_TIGER_EFFECT;
    public static EntityType<InkCelestialDragonEntity> INK_CELESTIAL_DRAGON;

    private InkBossRegistration() {}

    @SubscribeEvent
    public static void register(RegisterEvent event) {
        event.register(Registries.ITEM,helper->helper.register(ResourceLocation.fromNamespaceAndPath("tnc","ink_portal"),new InkPortalItem()));
        event.register(Registries.ENTITY_TYPE, helper -> {
            INK_RITUAL=EntityType.Builder.<InkRitualEntity>of(InkRitualEntity::new,MobCategory.MISC).sized(1,1).clientTrackingRange(32).updateInterval(1).build("tnc:ink_ritual");
            helper.register(ResourceLocation.fromNamespaceAndPath("tnc","ink_ritual"),INK_RITUAL);
            INK_PORTAL=EntityType.Builder.<InkPortalEntity>of(InkPortalEntity::new,MobCategory.MISC).sized(4.8F,5.2F).clientTrackingRange(12).updateInterval(20).build("tnc:ink_portal");
            helper.register(ResourceLocation.fromNamespaceAndPath("tnc","ink_portal"),INK_PORTAL);
            INK_CELESTIAL_DRAGON=EntityType.Builder.<InkCelestialDragonEntity>of(InkCelestialDragonEntity::new,MobCategory.MISC)
                    .sized(8*InkDragonCombatRules.BODY_SCALE,10*InkDragonCombatRules.BODY_SCALE).clientTrackingRange(32).updateInterval(2).build("tnc:ink_celestial_dragon");
            helper.register(ResourceLocation.fromNamespaceAndPath(TNMod.MODID,"ink_celestial_dragon"),INK_CELESTIAL_DRAGON);
            INK_TIGER_EFFECT=EntityType.Builder.<InkTigerEffectEntity>of(InkTigerEffectEntity::new,MobCategory.MISC)
                    .sized(1,1).clientTrackingRange(24).updateInterval(1).build("tnc:ink_tiger_effect");
            helper.register(ResourceLocation.fromNamespaceAndPath(TNMod.MODID,"ink_tiger_effect"),INK_TIGER_EFFECT);
            INK_WINGED_TIGER=EntityType.Builder.<InkWingedTigerEntity>of(InkWingedTigerEntity::new,MobCategory.MONSTER)
                    .sized(3.6F*InkTigerAttackRules.BODY_MULTIPLIER,3.9F*InkTigerAttackRules.BODY_MULTIPLIER).clientTrackingRange(20).updateInterval(2).build("tnc:ink_winged_tiger");
            helper.register(ResourceLocation.fromNamespaceAndPath(TNMod.MODID,"ink_winged_tiger"),INK_WINGED_TIGER);
            INK_ABYSS_DRAKE = EntityType.Builder
                    .<InkAbyssDrakeEntity>of(InkAbyssDrakeEntity::new, MobCategory.MONSTER)
                    .sized(3.25F, 3.0F)
                    .clientTrackingRange(16)
                    .updateInterval(2)
                    .build("tnc:ink_abyss_drake");
            helper.register(ResourceLocation.fromNamespaceAndPath(TNMod.MODID, "ink_abyss_drake"),
                    INK_ABYSS_DRAKE);
            INK_CRANE_BOSS = EntityType.Builder
                    .<InkCraneBossEntity>of(InkCraneBossEntity::new, MobCategory.MONSTER)
                    .sized(4.8F, 19.2F)
                    .clientTrackingRange(16)
                    .updateInterval(2)
                    .build("tnc:ink_crane_boss");
            helper.register(ResourceLocation.fromNamespaceAndPath(TNMod.MODID, "ink_crane_boss"),
                    INK_CRANE_BOSS);
            INK_CRANE_PHANTOM = EntityType.Builder
                    .<InkCranePhantomEntity>of(InkCranePhantomEntity::new, MobCategory.MISC)
                    .sized(1.0F, 1.0F).clientTrackingRange(24).updateInterval(1)
                    .build("tnc:ink_crane_phantom");
            helper.register(ResourceLocation.fromNamespaceAndPath(TNMod.MODID, "ink_crane_phantom"), INK_CRANE_PHANTOM);
        });
    }

    @SubscribeEvent
    public static void attributes(EntityAttributeCreationEvent event) {
        event.put(INK_ABYSS_DRAKE, InkAbyssDrakeEntity.createAttributes().build());
        event.put(INK_CRANE_BOSS, InkCraneBossEntity.createAttributes().build());
        event.put(INK_WINGED_TIGER,InkWingedTigerEntity.createAttributes().build());
        event.put(INK_CELESTIAL_DRAGON,InkCelestialDragonEntity.createAttributes().build());
    }
}
