package com.tnc.tnc.boss.fire.client;

import com.tnc.tnc.boss.fire.TortoiseStun;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.MovementInputUpdateEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Suppress voluntary input, while leaving knockback, gravity and the camera intact. */
@Mod.EventBusSubscriber(modid="tnc",value=Dist.CLIENT)
public final class TortoiseStunClient {
    private TortoiseStunClient() {}
    @SubscribeEvent public static void move(MovementInputUpdateEvent event) {
        if (!TortoiseStun.isStunned(event.getEntity())) return;
        var input=event.getInput();input.forwardImpulse=0;input.leftImpulse=0;
        input.jumping=false;input.shiftKeyDown=false;
        input.up=false;input.down=false;input.left=false;input.right=false;
        event.getEntity().setSprinting(false);
    }
    @SubscribeEvent public static void click(InputEvent.InteractionKeyMappingTriggered event) {
        var player=Minecraft.getInstance().player;
        if (player!=null && TortoiseStun.isStunned(player)) { event.setSwingHand(false);event.setCanceled(true); }
    }
}
