package com.tnc.tnc.life.seawild.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.tnc.tnc.TNMod;
import com.tnc.tnc.life.pasture.client.PastureSkin;
import com.tnc.tnc.life.seawild.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.entity.*;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import java.util.*;

@Mod.EventBusSubscriber(modid=TNMod.MODID,bus=Mod.EventBusSubscriber.Bus.MOD,value=Dist.CLIENT)
public final class SeaWildRenderer extends EntityRenderer<SeaCreature> {
    private final SeaKind kind;
    private final ResourceLocation texture;
    private final PastureSkin skin;
    private final Map<String,ModelPart> bones=new HashMap<>();
    private final WeakHashMap<SeaCreature,SeaSchool> schools=new WeakHashMap<>();

    private SeaWildRenderer(EntityRendererProvider.Context context,SeaKind kind) {
        super(context);
        this.kind=kind;
        texture=ResourceLocation.fromNamespaceAndPath(TNMod.MODID,"textures/entity/seawild/"+kind.id+".png");
        var root=new ModelPart(List.of(),Map.of());
        skin=new PastureSkin("sea_"+kind.id,root,new String[0][]);
        skin.bindArticulatedParts(bones);
    }

    @SubscribeEvent public static void register(EntityRenderersEvent.RegisterRenderers e) {
        SeaWildRegistry.TYPES.forEach((k,t)->e.registerEntityRenderer(t,ctx->new SeaWildRenderer(ctx,k)));
    }

    @Override public ResourceLocation getTextureLocation(SeaCreature e) { return texture; }

    @Override public void render(SeaCreature e,float yaw,float partial,PoseStack stack,MultiBufferSource buffers,int light) {
        var camera=Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
        double ds=camera.distanceToSqr(e.position());
        float time=e.tickCount+partial;
        if (kind.school()) {
            var school=schools.computeIfAbsent(e,SeaSchool::new);
            int stride=ds>48*48 ? 3 : ds>30*30 ? 2 : 1;
            school.step(e,stride);
            for(int i=0;i<school.current.length;i+=stride) {
                var at=school.previous[i].lerp(school.current[i],partial);
                stack.pushPose();
                stack.translate(at.x,at.y,at.z);
                stack.mulPose(Axis.YP.rotationDegrees(-Mth.rotLerp(partial,school.previousTurn[i],school.turn[i])));
                float scale=school.bodyScale[i]*(kind==SeaKind.REEF?1.02f:1.09f);
                stack.scale(scale,scale,scale);
                float wave=time*(.23f+.026f*Mth.sin(school.phase[i]))+school.panicPhase(partial)+school.phase[i];
                stack.mulPose(Axis.YP.rotation(.055f*Mth.sin(wave)));
                bones.get("tail").yRot=.47f*Mth.sin(wave-.55f);
                bones.get("fin-1").yRot=.20f+.23f*Mth.sin(wave*.52f);
                bones.get("fin1").yRot=-bones.get("fin-1").yRot;
                draw(stack,buffers,light,false);
                stack.popPose();
            }
        } else {
            stack.pushPose();
            stack.mulPose(Axis.YP.rotationDegrees(-yaw));
            float t=time+e.getId()*1.97f;
            if (kind==SeaKind.JELLY) {
                float pump=(float)Math.pow(Math.max(0,Mth.sin(t*.13f)),1.8);
                stack.translate(0,.53+.035*Mth.sin(t*.13f),0);
                var body=bones.get("body");body.xScale=body.zScale=1-.13f*pump;body.yScale=.97f+.16f*pump;
                for(int j=0;j<8;j++) {
                    float a=j*Mth.TWO_PI/8;
                    for(int k=0;k<3;k++) {
                        var tentacle=bones.get("thread"+j+(k==0?"":"_"+k));
                        float curl=.13f*Mth.sin(t*.09f-.58f*k+j*.70f)+.085f*Mth.sin(t*.15f-.6f*k);
                        tentacle.xRot=curl*Mth.cos(a);tentacle.zRot=curl*Mth.sin(a);
                    }
                    if(j%2==0)bones.get("veil"+j).zRot=.15f*Mth.sin(t*.10f+j);
                }
                draw(stack,buffers,light,true);
            } else {
                for (int side : new int[]{-1,1}) {
                    bones.get("skirt"+side).zRot=side*.10f*Mth.sin(t*.14f+side*.85f);
                    bones.get("horn"+side).xRot=.14f*Mth.sin(t*.065f+side);
                }
                for(int i=0;i<5;i++)bones.get("gill"+i).zRot=.15f*Mth.sin(t*.10f+i*.71f);
                draw(stack,buffers,light,e.level().getSkyDarken()>=6);
            }
            stack.popPose();
        }
        super.render(e,yaw,partial,stack,buffers,light);
    }

    private void draw(PoseStack stack,MultiBufferSource buffers,int light,boolean glow) {
        var type=kind==SeaKind.JELLY ? RenderType.entityTranslucent(texture) : RenderType.entityCutoutNoCull(texture);
        skin.draw(stack,buffers.getBuffer(type),light,OverlayTexture.NO_OVERLAY,1,1,1,1,false);
        if (glow) skin.draw(stack,buffers.getBuffer(RenderType.eyes(texture)),LightTexture.FULL_BRIGHT,
                OverlayTexture.NO_OVERLAY,1,1,1,1,true);
    }
}
