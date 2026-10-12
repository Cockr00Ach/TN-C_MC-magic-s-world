package com.tnc.tnc.client;

import com.mojang.blaze3d.vertex.PoseStack;
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
    public static final List<String> MODELS=List.of("seal","raindrop","rain_crown");
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
        var mc=Minecraft.getInstance();var model=mc.getModelManager().getModel(id(name));
        poses.pushPose();poses.translate(-.5,0,-.5);
        mc.getItemRenderer().renderModelLists(model,ItemStack.EMPTY,LightTexture.FULL_BRIGHT,
                OverlayTexture.NO_OVERLAY,poses,buffers.getBuffer(RenderType.eyes(TextureAtlas.LOCATION_BLOCKS)));
        poses.popPose();
    }
    static void seal(PoseStack poses,MultiBufferSource buffers,Vec3 at,Vec3 normal,double radius,double age,float envelope) {
        if(envelope<=0)return;
        poses.pushPose();poses.translate(at.x,at.y,at.z);
        poses.mulPose(new Quaternionf().rotationTo(0,1,0,(float)normal.x,(float)normal.y,(float)normal.z));
        poses.mulPose(Axis.YP.rotationDegrees((float)(age*.55)));
        float size=(float)(radius*2*Math.min(1,envelope*3));
        poses.scale(size,(float)(.8+Math.min(radius,8)*.15),size);
        draw("seal",poses,buffers);poses.popPose();
    }
}
