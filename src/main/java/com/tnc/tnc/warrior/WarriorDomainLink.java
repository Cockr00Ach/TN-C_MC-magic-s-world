package com.tnc.tnc.warrior;
/** Optional bridge: works when the domain module is present without linking its unpublished classes. */
public final class WarriorDomainLink {
 public static String activeElementId(net.minecraft.world.entity.LivingEntity player){
  for(String element:new String[]{"lightning","dark","fire","wind","water","earth","light"}){
   var effect=net.minecraftforge.registries.ForgeRegistries.MOB_EFFECTS.getValue(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("tnc","domain_"+element));
   if(effect!=null&&player.hasEffect(effect))return element;
  }return null;
 }
}
