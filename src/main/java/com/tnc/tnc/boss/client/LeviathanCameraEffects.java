package com.tnc.tnc.boss.client;

import com.tnc.tnc.TNMod;
import com.tnc.tnc.magic.water.*;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Local, distance-scaled camera motion; no particles, packets or screen flashes. */
@Mod.EventBusSubscriber(modid=TNMod.MODID,value=Dist.CLIENT)
public final class LeviathanCameraEffects {
    private static float strength;
    private LeviathanCameraEffects() {}
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event) {
        if(event.phase!=TickEvent.Phase.END)return;
        var mc=Minecraft.getInstance();
        if(mc.level==null || mc.player==null) {strength=0;return;}
        if(mc.isPaused())return;
        strength*=.80F;
        var eye=mc.player.getEyePosition();
        for(var beam:mc.level.getEntitiesOfClass(TNWaterSpellEntity.class,mc.player.getBoundingBox().inflate(128))) {
            if(beam.tier()!=5 || beam.firedAt()<0 || beam.length()<=0)continue;
            int active=beam.age()-beam.firedAt();
            if(active<0 || active>=beam.duration())continue;
            double distance=Math.sqrt(WaterSpellRules.segmentDistanceSquared(eye,beam.position(),beam.direction(),beam.length()));
            float falloff=(float)Math.max(0,1-distance/48);
            strength=Math.max(strength,(active<10?3.2F: .65F)*falloff);
        }
        for(var field:mc.level.getEntitiesOfClass(TNWaterFieldEntity.class,mc.player.getBoundingBox().inflate(64))) {
            if(field.kind()!=2 || field.tier()!=5)continue;
            for(int elapsed=0;elapsed<10;elapsed++) {
                if(field.swordImpactIndex(field.age()-elapsed)<0)continue;
                float falloff=(float)Math.max(0,1-field.position().distanceTo(eye)/64);
                strength=Math.max(strength,5F*(1-elapsed/10F)*falloff);
                break;
            }
        }
        if(strength<.01F)strength=0;
    }
    @SubscribeEvent public static void camera(ViewportEvent.ComputeCameraAngles event) {
        var mc=Minecraft.getInstance();
        if(mc.level==null || mc.player==null || mc.isPaused() || strength<=0)return;
        float time=(float)(mc.level.getGameTime()+event.getPartialTick());
        event.setPitch(event.getPitch()+Mth.sin(time*2.13F)*strength*.65F);
        event.setYaw(event.getYaw()+Mth.sin(time*1.71F+.6F)*strength*.45F);
        event.setRoll(event.getRoll()+Mth.sin(time*2.57F)*strength*.8F);
    }
}
