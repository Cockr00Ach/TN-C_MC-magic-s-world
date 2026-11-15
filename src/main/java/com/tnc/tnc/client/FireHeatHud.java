package com.tnc.tnc.client;

import com.tnc.tnc.magic.fire.FireHeatFeedback;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** A restrained target thermometer below the reticle, never a full-screen fire overlay. */
@Mod.EventBusSubscriber(modid="tnc",value=Dist.CLIENT)
public final class FireHeatHud {
    private static FireHeatFeedback.Target target;
    private static int age;
    private static float displayed;
    public static void accept(FireHeatFeedback.Target value){
        if(target==null||!target.target().equals(value.target()))displayed=value.heat();
        target=value;age=0;
    }
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent e){
        if(e.phase!=TickEvent.Phase.END)return;
        var mc=Minecraft.getInstance();
        if(mc.player==null||mc.level==null||target!=null&&!mc.level.dimension().location().equals(target.dimension())){target=null;displayed=0;return;}
        age++;if(target!=null)displayed+=(target.heat()-displayed)*.35F;
    }
    private static boolean aimedAtTarget(Minecraft mc){
        var camera=mc.gameRenderer.getMainCamera().getPosition();
        var end=camera.add(mc.gameRenderer.getMainCamera().getLookVector().x()*64,mc.gameRenderer.getMainCamera().getLookVector().y()*64,mc.gameRenderer.getMainCamera().getLookVector().z()*64);
        var wall=mc.level.clip(new net.minecraft.world.level.ClipContext(camera,end,net.minecraft.world.level.ClipContext.Block.COLLIDER,net.minecraft.world.level.ClipContext.Fluid.NONE,mc.player));
        double limit=wall.getLocation().distanceToSqr(camera);
        for(var e:mc.level.entitiesForRendering())if(e.isAlive()&&e.getUUID().equals(target.target())){
            var hit=e.getBoundingBox().inflate(.4).clip(camera,end);return hit.isPresent()&&hit.get().distanceToSqr(camera)<=limit;
        }
        return false;
    }
    @SubscribeEvent public static void draw(RenderGuiEvent.Post e){
        var mc=Minecraft.getInstance();
        if(target==null||age>20||mc.player==null||mc.options.hideGui||mc.screen!=null||!aimedAtTarget(mc))return;
        var g=e.getGuiGraphics();int cx=mc.getWindow().getGuiScaledWidth()/2,y=mc.getWindow().getGuiScaledHeight()/2+23;
        int color=displayed>=60?0xFFFFDDA1:0xFFF39254;
        String label=(target.heat()==0?"余烬 · ":displayed>=99.5F?"满热 · ":displayed>=60?"燃点 · ":"热量 · ")+Math.round(displayed)+" / 100";
        g.drawCenteredString(mc.font,label,cx,y,color);
        for(int i=0;i<20;i++)g.fill(cx-50+i*5,y+12,cx-46+i*5,y+15,i*5<displayed?color:0x80362A28);
        g.fill(cx+9,y+11,cx+10,y+17,0xFFFAE9C6);
    }
}
