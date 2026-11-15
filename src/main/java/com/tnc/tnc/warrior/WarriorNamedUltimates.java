package com.tnc.tnc.warrior;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
/** Named weapon signatures supplement the family choreography, never replace it. */
public final class WarriorNamedUltimates {
 private WarriorNamedUltimates(){}
 public static void swordFinale(ServerPlayer player,LivingEntity target){
  var id=net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(player.getMainHandItem().getItem());if(id==null)return;
  switch(id.getPath()){
   case "dragon_sword" -> target.setSecondsOnFire(4);
   case "frost_katana" -> target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN,80,2));
   case "star_rapier" -> target.addEffect(new MobEffectInstance(MobEffects.GLOWING,100));
   default -> {}
  }
 }
}
