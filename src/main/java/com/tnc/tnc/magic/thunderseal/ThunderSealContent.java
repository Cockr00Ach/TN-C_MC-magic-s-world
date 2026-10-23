package com.tnc.tnc.magic.thunderseal;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.*;
import net.minecraft.world.effect.*;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
@Mod.EventBusSubscriber(modid="tnc",bus=Mod.EventBusSubscriber.Bus.MOD)
public final class ThunderSealContent {
 public static EntityType<ThunderSealEntity> SEAL;
 @SubscribeEvent public static void register(net.minecraftforge.registries.RegisterEvent e){
  e.register(Registries.ENTITY_TYPE,h->{SEAL=EntityType.Builder.<ThunderSealEntity>of(ThunderSealEntity::new,MobCategory.MISC).sized(1,1).clientTrackingRange(12).updateInterval(2).noSave().build("tnc:thunder_seal");h.register(id("thunder_seal"),SEAL);});
  e.register(Registries.MOB_EFFECT,h->{for(int t=1;t<=5;t++)h.register(id("thunder_seal_cast_"+t),new Marker(t));});
 }
 public static ResourceLocation id(String s){return ResourceLocation.fromNamespaceAndPath("tnc",s);}
 public static final class Marker extends MobEffect {public final int tier;Marker(int t){super(MobEffectCategory.BENEFICIAL,0x9ADFFF);tier=t;}}
}
