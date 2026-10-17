package com.tnc.tnc.boss.earth.client;

import com.tnc.tnc.boss.earth.StoneGravityPull;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Reconstruct the synchronized force before movement, beyond vanilla's 3.9 packet clamp. */
@Mod.EventBusSubscriber(modid="tnc",value=Dist.CLIENT)
public final class StoneLancerGravityClient {
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event){
        if(event.phase!=TickEvent.Phase.START)return;
        var mc=Minecraft.getInstance();var field=StoneLancerTremor.gravityField;
        if(mc.level==null||mc.player==null||mc.isPaused()||!mc.player.isAlive()||mc.player.isSpectator()
                ||field==null||field.level()!=mc.level||!field.isAlive()||field.done())return;
        double age=field.age(0)-field.waitTicks();
        if(age<0||age>=80||mc.level.getGameTime()%2!=0)return;
        var delta=field.position().subtract(mc.player.getBoundingBox().getCenter());
        if(delta.lengthSqr()>420*420)return;
        mc.player.setDeltaMovement(StoneGravityPull.motion(mc.player.getDeltaMovement(),delta,mc.player.isNoGravity(),age));
    }
}
