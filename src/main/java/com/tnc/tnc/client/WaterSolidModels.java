package com.tnc.tnc.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.tnc.tnc.TNMod;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.texture.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ModelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Quaternionf;
import java.util.List;

/** Normal textured BakedModels on water entities, independent of Spell Engine. */
@Mod.EventBusSubscriber(modid=TNMod.MODID,value=Dist.CLIENT,bus=Mod.EventBusSubscriber.Bus.MOD)
public final class WaterSolidModels {
    public static final List<String> MODELS=List.of("seal","raindrop","rain_crown","sea_god_sword","water_prison_panel");
    @SubscribeEvent public static void register(ModelEvent.RegisterAdditional e) {
        for(String name:MODELS)e.register(id(name));
    }
    @SubscribeEvent public static void validate(ModelEvent.ModifyBakingResult e) {
        for(String name:MODELS) {
            var model=e.getModels().get(id(name));
            if(model==null||model.getParticleIcon().contents().name().getPath().equals("missingno"))
                org.slf4j.LoggerFactory.getLogger("TN-C/water-models").error("Missing baked water model: {}",name);
        }
    }
    private static ResourceLocation id(String name){return ResourceLocation.fromNamespaceAndPath("tnc","water/"+name);}
    static void draw(String name,PoseStack poses,MultiBufferSource buffers) {
        draw(name,poses,buffers,1);
    }
    static void draw(String name,PoseStack poses,MultiBufferSource buffers,float opacity) {
        var mc=Minecraft.getInstance();var model=mc.getModelManager().getModel(id(name));
        poses.pushPose();poses.translate(-.5,0,-.5);
        boolean solidRelic=name.equals("sea_god_sword")||name.equals("raindrop");
        var consumer=buffers.getBuffer(opacity<1?WaterModelRenderTypes.alpha():solidRelic?RenderType.entityCutoutNoCull(TextureAtlas.LOCATION_BLOCKS):RenderType.eyes(TextureAtlas.LOCATION_BLOCKS));
        if(opacity<1)consumer=new Alpha(consumer,opacity);
        mc.getItemRenderer().renderModelLists(model,ItemStack.EMPTY,LightTexture.FULL_BRIGHT,
                OverlayTexture.NO_OVERLAY,poses,consumer);
        if(name.equals("sea_god_sword")||name.equals("seal")) {
            // Full light-map brightness alone does not create the additive glow.
            var emission=new Alpha(buffers.getBuffer(RenderType.eyes(TextureAtlas.LOCATION_BLOCKS)),
                    name.equals("sea_god_sword")?.45F*opacity:Math.max(.42F,opacity*.7F));
            mc.getItemRenderer().renderModelLists(model,ItemStack.EMPTY,LightTexture.FULL_BRIGHT,
                    OverlayTexture.NO_OVERLAY,poses,emission);
        }
        poses.popPose();
    }
    static void seal(PoseStack poses,MultiBufferSource buffers,Vec3 at,Vec3 normal,double radius,double age,float envelope) {
        seal(poses,buffers,at,normal,radius,age,envelope,1);
    }
    static void seal(PoseStack poses,MultiBufferSource buffers,Vec3 at,Vec3 normal,double radius,double age,float envelope,float opacity) {
        if(envelope<=0)return;
        poses.pushPose();poses.translate(at.x,at.y,at.z);
        poses.mulPose(new Quaternionf().rotationTo(0,1,0,(float)normal.x,(float)normal.y,(float)normal.z));
        poses.mulPose(Axis.YP.rotationDegrees((float)(age*.55)));
        float size=(float)(radius*2*Math.min(1,envelope*3));
        poses.scale(size,(float)(.8+Math.min(radius,8)*.15),size);
        draw("seal",poses,buffers,opacity);poses.popPose();
    }
    static void prisonPanels(PoseStack poses,MultiBufferSource buffers,double radius,double height,int panels,double age,float opacity) {
        double circumference=Math.PI*2*radius, width=circumference/panels;
        for(int i=0;i<panels;i++) {
            double angle=i*Math.PI*2/panels+age*.003;
            poses.pushPose();
            poses.translate(Math.cos(angle)*radius*1.018,.08,Math.sin(angle)*radius*1.018);
            poses.mulPose(Axis.YP.rotationDegrees((float)(90-angle*180/Math.PI)));
            poses.scale((float)(width/.76),(float)height,.72F);
            draw("water_prison_panel",poses,buffers,opacity);
            poses.popPose();
        }
    }
    private record Alpha(VertexConsumer target,float opacity) implements VertexConsumer {
        public VertexConsumer vertex(double x,double y,double z){target.vertex(x,y,z);return this;}
        public VertexConsumer color(int r,int g,int b,int a){target.color(r,g,b,Math.round(a*opacity));return this;}
        public VertexConsumer uv(float u,float v){target.uv(u,v);return this;}
        public VertexConsumer overlayCoords(int u,int v){target.overlayCoords(u,v);return this;}
        public VertexConsumer uv2(int u,int v){target.uv2(u,v);return this;}
        public VertexConsumer normal(float x,float y,float z){target.normal(x,y,z);return this;}
        public void endVertex(){target.endVertex();}
        public void defaultColor(int r,int g,int b,int a){target.defaultColor(r,g,b,Math.round(a*opacity));}
        public void unsetDefaultColor(){target.unsetDefaultColor();}
    }
}
