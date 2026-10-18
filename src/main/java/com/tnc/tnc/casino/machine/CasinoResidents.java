package com.tnc.tnc.casino.machine;

import com.tnc.tnc.TNMod;
import net.minecraft.world.entity.*;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.RegisterEvent;

@Mod.EventBusSubscriber(modid=TNMod.MODID,bus=Mod.EventBusSubscriber.Bus.MOD)
public final class CasinoResidents {
    private static EntityType<CasinoGuestEntity> type;
    public static final java.util.function.Supplier<EntityType<CasinoGuestEntity>> TYPE=()->type;
    @SubscribeEvent public static void register(RegisterEvent event) {
        event.register(net.minecraft.core.registries.Registries.ENTITY_TYPE,h->{
            type=EntityType.Builder.of(CasinoGuestEntity::new,MobCategory.MISC).sized(.6F,1.95F).clientTrackingRange(8).build("tnc:casino_guest");
            h.register(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("tnc","casino_guest"),type);
        });
    }
    @SubscribeEvent public static void attributes(EntityAttributeCreationEvent event){event.put(type,net.minecraft.world.entity.npc.Villager.createAttributes().build());}
}
