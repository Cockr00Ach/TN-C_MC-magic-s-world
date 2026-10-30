package com.tnc.tnc.world.ink;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.*;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.registries.RegisterEvent;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;

@Mod.EventBusSubscriber(modid="tnc",bus=Mod.EventBusSubscriber.Bus.MOD)
public final class InkBrushRegistration {
    public static EntityType<InkBrushBossEntity> BRUSH;
    @SubscribeEvent public static void register(RegisterEvent event){event.register(Registries.ENTITY_TYPE,h->{BRUSH=EntityType.Builder.<InkBrushBossEntity>of(InkBrushBossEntity::new,MobCategory.MONSTER).sized(1.4F,8).clientTrackingRange(16).updateInterval(2).fireImmune().build("tnc:ink_brush_boss");h.register(ResourceLocation.fromNamespaceAndPath("tnc","ink_brush_boss"),BRUSH);});}
    @SubscribeEvent public static void attributes(EntityAttributeCreationEvent event){event.put(BRUSH,InkBrushBossEntity.attributes().build());}
}
