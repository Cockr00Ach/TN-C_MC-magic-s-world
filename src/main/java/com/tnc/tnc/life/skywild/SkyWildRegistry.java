package com.tnc.tnc.life.skywild;

import java.util.EnumMap;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.RegisterEvent;

@Mod.EventBusSubscriber(modid="tnc",bus=Mod.EventBusSubscriber.Bus.MOD)
public final class SkyWildRegistry {
    public static final EnumMap<SkySpecies,EntityType<SkyBird>> TYPES=new EnumMap<>(SkySpecies.class);
    @SubscribeEvent public static void register(RegisterEvent event) {
        event.register(Registries.ENTITY_TYPE,registry -> {
            for (var species : SkySpecies.values()) {
                var id=ResourceLocation.fromNamespaceAndPath("tnc",species.id);
                var type=EntityType.Builder.<SkyBird>of((t,l)->new SkyBird(t,l,species),MobCategory.AMBIENT)
                        .sized(species.width,species.height).clientTrackingRange(10).updateInterval(3).build(id.toString());
                TYPES.put(species,type);registry.register(id,type);
            }
        });
    }
    @SubscribeEvent public static void attributes(EntityAttributeCreationEvent event) {
        for (var entry : TYPES.entrySet()) event.put(entry.getValue(),SkyBird.attributes(entry.getKey()).build());
    }
    private SkyWildRegistry() {}
}

