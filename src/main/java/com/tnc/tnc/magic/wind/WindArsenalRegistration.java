package com.tnc.tnc.magic.wind;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.*;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.RegisterEvent;

@Mod.EventBusSubscriber(modid="tnc",bus=Mod.EventBusSubscriber.Bus.MOD)
public final class WindArsenalRegistration {
    public static EntityType<WindWeaponEntity> WEAPON;
    @SubscribeEvent public static void register(RegisterEvent e){
        e.register(Registries.ENTITY_TYPE,h->{
            WEAPON=EntityType.Builder.<WindWeaponEntity>of(WindWeaponEntity::new,MobCategory.MISC)
                    .sized(.25F,.25F).clientTrackingRange(12).updateInterval(1).noSave().build("tnc:wind_weapon");
            h.register(ResourceLocation.fromNamespaceAndPath("tnc","wind_weapon"),WEAPON);
        });
    }
}
