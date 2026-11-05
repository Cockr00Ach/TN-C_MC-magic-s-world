package com.tnc.tnc.bronze.client;

import com.tnc.tnc.bronze.*;
import net.minecraft.client.Minecraft;
import net.minecraft.world.InteractionHand;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.*;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Dagger visibility/charge cue only; melee uses the existing warrior client. */
@Mod.EventBusSubscriber(modid="tnc",value=Dist.CLIENT)
public final class BronzeDaggerClient {
    @SubscribeEvent public static void hand(RenderHandEvent e){
        var mc=Minecraft.getInstance();if(mc.level!=null&&e.getItemStack().getItem() instanceof BronzeWeaponItem&&BronzeWeaponItem.inFlight(e.getItemStack(),mc.level))e.setCanceled(true);
    }
    @SubscribeEvent public static void attack(InputEvent.InteractionKeyMappingTriggered e){
        var mc=Minecraft.getInstance();if(!e.isAttack()||mc.player==null)return;
        var stack=mc.player.getMainHandItem();
        if(stack.getItem() instanceof BronzeWeaponItem&&BronzeWeaponItem.inFlight(stack,mc.level)){e.setCanceled(true);e.setSwingHand(false);}
    }
    @SubscribeEvent public static void charge(TickEvent.ClientTickEvent e){
        var mc=Minecraft.getInstance();if(e.phase!=TickEvent.Phase.END||mc.player==null||mc.isPaused())return;
        if(mc.player.isUsingItem()&&mc.player.getUsedItemHand()==InteractionHand.MAIN_HAND
            &&mc.player.getUseItem().getItem() instanceof BronzeWeaponItem item&&(item.profile==BronzeProfile.DAGGER||item.profile==BronzeProfile.HALBERD)&&mc.player.getTicksUsingItem()==30)
            mc.player.playSound(net.minecraft.sounds.SoundEvents.AMETHYST_BLOCK_CHIME,.6f,.7f);
    }
}
