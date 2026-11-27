package com.tnc.tnc.life.skywild.client;

import com.google.gson.JsonParser;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.tnc.tnc.life.pasture.client.PastureSkin;
import com.tnc.tnc.life.skywild.SkyBird;
import com.tnc.tnc.life.skywild.SkySpecies;
import com.tnc.tnc.life.skywild.SkyWildRegistry;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid="tnc",bus=Mod.EventBusSubscriber.Bus.MOD,value=Dist.CLIENT)
public final class SkyBirdRenderer extends MobRenderer<SkyBird,SkyBirdRenderer.Sculpture> {
    private final ResourceLocation feather;
    @SubscribeEvent public static void register(EntityRenderersEvent.RegisterRenderers event) {
        SkyWildRegistry.TYPES.forEach((kind,type)->event.registerEntityRenderer(type,ctx->new SkyBirdRenderer(ctx,kind)));
    }
    SkyBirdRenderer(EntityRendererProvider.Context context,SkySpecies kind) {
        super(context,new Sculpture(kind),kind.width*.24f);
        feather=id("textures/entity/skywild/"+kind.id+".png");
        var glow=id("textures/entity/skywild/"+kind.id+"_glow.png");
        addLayer(new RenderLayer<SkyBird,Sculpture>(this) {
            @Override public void render(PoseStack stack,MultiBufferSource buffers,int light,SkyBird entity,float limb,float stride,
                                         float partial,float age,float yaw,float pitch) {
                if (!entity.isInvisible())
                    getParentModel().skin.draw(stack,buffers.getBuffer(RenderType.eyes(glow)),LightTexture.FULL_BRIGHT,
                            OverlayTexture.NO_OVERLAY,1,1,1,1,true);
            }
        });
    }
    @Override public ResourceLocation getTextureLocation(SkyBird bird) {return feather;}
    private static ResourceLocation id(String path){return ResourceLocation.fromNamespaceAndPath("tnc",path);}
    static final class Sculpture extends EntityModel<SkyBird> {
        private final Map<String,ModelPart> poses=new HashMap<>();
        private final PastureSkin skin;
        Sculpture(SkySpecies kind) {
            var mesh=new MeshDefinition();
            var defs=new HashMap<String,PartDefinition>();defs.put("root",mesh.getRoot());
            var parents=new ArrayList<String[]>();
            var file=id("skywild/binds/"+kind.id+".json");
            try(var in=Minecraft.getInstance().getResourceManager().getResourceOrThrow(file).openAsReader()){
                for (var entry:JsonParser.parseReader(in).getAsJsonArray()){
                    var joint=entry.getAsJsonObject();var pivot=joint.getAsJsonArray("pivot");
                    String name=joint.get("name").getAsString(),parent=joint.get("parent").getAsString();
                    var rest=PartPose.offset(pivot.get(0).getAsFloat(),pivot.get(1).getAsFloat(),pivot.get(2).getAsFloat());
                    defs.put(name,defs.get(parent).addOrReplaceChild(name,CubeListBuilder.create(),rest));
                    parents.add(new String[]{name,parent});
                }
            }catch(IOException error){throw new IllegalStateException("Missing sky flight rig "+file,error);}
            var root=LayerDefinition.create(mesh,128,128).bakeRoot();
            skin=new PastureSkin("skywild/"+kind.id,root,parents.toArray(String[][]::new));
            skin.bindArticulatedParts(poses);
        }
        @Override public void setupAnim(SkyBird bird,float limb,float stride,float age,float yaw,float pitch) {
            float t=age+(bird.getId()%113)*1.71f;
            float beat,amplitude,dihedral,wristLag;
            switch (bird.species) {
                case SWALLOW -> {beat=t*.76f;amplitude=.68f*(.38f+.62f*burst(t,.075f));dihedral=.02f;wristLag=.49f;}
                case STARLING -> {beat=t*.69f;amplitude=.76f*(.57f+.43f*burst(t,.11f));dihedral=.07f;wristLag=.56f;}
                case EAGLE -> {beat=t*.208f;amplitude=.08f+.68f*burst(t,.032f);dihedral=.12f;wristLag=.26f;}
                case BAT -> {beat=t*.62f+.20f*Mth.sin(t*.15f);amplitude=.82f;dihedral=-.04f;wristLag=.72f;}
                case CRANE -> {beat=t*.24f;amplitude=.19f+.54f*burst(t,.042f);dihedral=.085f;wristLag=.35f;}
                default -> throw new IllegalStateException();
            }
            float bank=Mth.clamp(Mth.wrapDegrees(bird.getYRot()-bird.yRotO)*.055f,-.38f,.38f);
            var body=poses.get("body");body.zRot=-bank;body.xRot=bird.getXRot()*Mth.DEG_TO_RAD*.73f;
            body.y=19+Mth.cos(beat)*amplitude*.32f;
            var neck=poses.get("neck");neck.xRot=.09f*Mth.sin(beat-.8f)*amplitude;
            var head=poses.get("head");head.zRot=bank*.44f;head.yRot=Mth.clamp(yaw*Mth.DEG_TO_RAD,-.32f,.32f);
            head.xRot=-body.xRot*.65f-neck.xRot*.67f;
            for (int side=0;side<2;side++) {
                int sign=side==0?1:-1;String end=side==0?"_l":"_r";
                var base=poses.get("wing"+end);var hand=poses.get("outer"+end);
                base.zRot=sign*(dihedral+amplitude*Mth.sin(beat));
                base.yRot=sign*amplitude*.16f*Mth.cos(beat);
                hand.zRot=sign*(.06f+wristLag*amplitude*Mth.sin(beat-.90f));
                hand.yRot=sign*amplitude*.24f*(1+Mth.cos(beat-.22f));
                var tail=poses.get("tail"+end);tail.yRot=sign*(Math.abs(bank)*.8f+.035f*Mth.sin(t*.12f));
                tail.xRot=-body.xRot*.32f+amplitude*.067f*Mth.sin(beat-.8f);
                poses.get("leg"+end).xRot=.06f*Mth.sin(beat-1.12f)*amplitude;
            }
        }
        private static float burst(float time,float frequency){return Mth.clamp((Mth.sin(time*frequency)-.28f)*2.8f,0,1);}
        @Override public void renderToBuffer(PoseStack stack,VertexConsumer out,int light,int overlay,float r,float g,float b,float a){
            skin.draw(stack,out,light,overlay,r,g,b,a,false);
        }
    }
}

