package com.tnc.tnc.warrior.client;
import com.tnc.tnc.warrior.*;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.eventbus.api.SubscribeEvent;
/** Temporary cinematic air hold; never changes the persistent no-gravity or flight flags. */
@Mod.EventBusSubscriber(modid="tnc",value=Dist.CLIENT)
public final class WarriorSwordAirControl {
 private static boolean active(){
  var mc=Minecraft.getInstance();var p=mc.player;if(p==null||mc.level==null||mc.isPaused()||!p.isAlive()||p.isSpectator())return false;
  var a=WarriorAnimationClient.active(p.getId());if(a==null||a.animation()!=WarriorAnimation.ULTIMATE||WarriorWeapons.family(p.getMainHandItem())!=WarriorWeaponFamily.LONGSWORD||!WarriorPolearmUltimates.kind(p.getMainHandItem()).isEmpty())return false;
  double age=a.progress(0)*a.durationTicks();return age>=26&&age<96;
 }
 @SubscribeEvent public static void tick(net.minecraftforge.event.TickEvent.ClientTickEvent e){
  if(!active())return;var p=Minecraft.getInstance().player;p.setDeltaMovement(0,0,0);p.fallDistance=0;
 }
 @SubscribeEvent public static void movement(net.minecraftforge.client.event.MovementInputUpdateEvent e){
  if(!active())return;var input=e.getInput();input.forwardImpulse=0;input.leftImpulse=0;input.up=false;input.down=false;input.left=false;input.right=false;input.jumping=false;e.getEntity().setSprinting(false);
 }
}
