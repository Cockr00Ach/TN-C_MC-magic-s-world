package com.tnc.tnc.client;
import com.tnc.tnc.magic.EarthAttackEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.entity.*;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
public final class EarthAttackRenderer extends EntityRenderer<EarthAttackEntity> {
    public EarthAttackRenderer(EntityRendererProvider.Context c){super(c);}
    public ResourceLocation getTextureLocation(EarthAttackEntity e){return ResourceLocation.fromNamespaceAndPath("minecraft","textures/block/stone.png");}
    public void render(EarthAttackEntity e,float yaw,float partial,PoseStack pose,MultiBufferSource buffers,int light) {
        double age=e.age(partial)-e.waitTicks();if(e.done()){renderBurst(e,partial,pose,buffers,light);if(e.mode()!=7&&e.mode()!=3)return;}if(age<0&&e.mode()!=0&&e.mode()!=3)return;
        if(e.mode()==3){renderSkySpear(e,partial,age,pose,buffers);return;}
        float growth=e.mode()==2?(float)Math.max(.04,Math.min(1,age/45)):e.mode()==1?(float)Math.max(.03,Math.min(1,age/8)):1;
        pose.pushPose();if(e.mode()==0||e.mode()==3||e.mode()==5){pose.mulPose(Axis.YP.rotationDegrees(e.getYRot()));pose.mulPose(Axis.XP.rotationDegrees(e.getXRot()));}
        if(e.mode()==0||e.mode()==3||e.mode()==5)pose.scale(e.scale(),e.scale(),e.height());else pose.scale(e.scale()*growth,e.height()*growth,e.scale()*growth);
        if(e.model().startsWith("stone_lancer_")&&(e.mode()==0||e.mode()==5))
            pose.translate(0,0,-1.5); // Centered model tip Z=1.5 stays on the swept impact point.
        else pose.translate(0,e.mode()==2?0:.5,0);
        var emission=e.mode()==3?net.spell_engine.api.render.LightEmission.RADIATE:net.spell_engine.api.render.LightEmission.NONE;
        draw(e.model(),pose,buffers,light,emission);
        pose.popPose();
        if((e.mode()==0||e.mode()==3||e.mode()==5)&&age>=0)for(int i=0;i<4;i++) {
            double a=age*.18+i*Math.PI/2,r=Math.max(.7,e.scale()*.18);
            pose.pushPose();pose.translate(Math.cos(a)*r,Math.sin(a*1.7)*.45,Math.sin(a)*r);pose.scale(.22F,.22F,.22F);
            draw("earth_clod",pose,buffers,light,net.spell_engine.api.render.LightEmission.NONE);pose.popPose();
        }
        if(e.mode()==2&&age>=0&&age<45)for(int i=0;i<8;i++) {
            double a=i*Math.PI/4+age*.09,r=((1-growth)*8+.6)*(e.gravityCore()?15:e.model().startsWith("stone_lancer_")?5:1);
            pose.pushPose();pose.translate(Math.cos(a)*r,-e.height()*(1-growth)*.7,Math.sin(a)*r);
            pose.mulPose(Axis.YP.rotationDegrees((float)(a*180/Math.PI)));pose.scale(.55F,.55F,.55F);
            net.spell_engine.api.render.CustomModels.render(net.spell_engine.client.render.SpellModelHelper.LAYERS.get(net.spell_engine.api.render.LightEmission.NONE),
                    Minecraft.getInstance().getItemRenderer(),ResourceLocation.fromNamespaceAndPath("tnc","projectile/earth_clod"),pose,buffers,light,OverlayTexture.NO_OVERLAY);
            pose.popPose();
        }
    }
    private static void renderSkySpear(EarthAttackEntity e,float partial,double age,PoseStack pose,MultiBufferSource buffers) {
        if(e.done()&&e.age(partial)-e.burstAge()>8)return;
        pose.pushPose();
        // The network entity stays near the target; the visible point is suspended above it.
        pose.translate(0,!e.done()&&age<=0?e.dropHeight():0,0);
        EarthSpearLayout.apply(pose,e.scale(),e.height());
        var mc=Minecraft.getInstance();
        var model=mc.getModelManager().getModel(ResourceLocation.fromNamespaceAndPath("tnc","projectile/"+(e.model().startsWith("stone_lancer_")?e.model():"earth_spear_sharp")));
        mc.getItemRenderer().renderModelLists(model,net.minecraft.world.item.ItemStack.EMPTY,LightTexture.FULL_BRIGHT,
                OverlayTexture.NO_OVERLAY,pose,buffers.getBuffer(RenderType.entityCutoutNoCull(net.minecraft.client.renderer.texture.TextureAtlas.LOCATION_BLOCKS)));
        pose.popPose();
    }
    private static void renderBurst(EarthAttackEntity e,float partial,PoseStack pose,MultiBufferSource buffers,int light) {
        double elapsed=e.age(partial)-e.burstAge();if(elapsed<0||elapsed>e.burstLife())return;
        pose.pushPose();if(e.model().startsWith("stone_lancer_")){float size=e.gravityCore()?15:5;pose.scale(size,size,size);}
        boolean giant=e.mode()==2;
        float fade=(float)Math.max(0,1-elapsed/e.burstLife()),size=giant?.6F:.28F;
        if(giant) {
            var out=buffers.getBuffer(WaterRenderTypes.geometry());
            for(int ring=0;ring<2;ring++) {
                double radius=Math.max(.2,(elapsed-ring*2)*.95);
                WaterGeometry.ringColor(out,pose.last().pose(),new net.minecraft.world.phys.Vec3(0,.25+ring*.2,0),new net.minecraft.world.phys.Vec3(0,1,0),radius,1.2-ring*.35,.72F,.42F,.12F,fade*.8F,48);
            }
        }
        int pieces=giant?42:18;
        for(int i=0;i<pieces;i++) {
            double a=i*Math.PI*2/pieces+i*.71,r=elapsed*((giant?.6:.32)+(i%5)*.055);
            double y=.2+elapsed*((giant?.30:.12)+(i%3)*.065)-elapsed*elapsed*.012;
            pose.pushPose();pose.translate(Math.cos(a)*r,y,Math.sin(a)*r);
            pose.mulPose(Axis.YP.rotationDegrees((float)(i*37+elapsed*13)));pose.scale(size*fade,size*fade,size*fade);
            draw((e.model().startsWith("stone_lancer_")?"stone_lancer_":"")+(i%3==0?"earth_shell":"earth_clod"),pose,buffers,light,net.spell_engine.api.render.LightEmission.NONE);pose.popPose();
        }
        pose.popPose();
    }
    private static void draw(String model,PoseStack pose,MultiBufferSource buffers,int light,net.spell_engine.api.render.LightEmission emission) {
        if(model.startsWith("stone_lancer_")){
            var mc=Minecraft.getInstance();
            // Match CustomModels' centered model origin for flight, spikes and debris.
            pose.translate(-.5,-.5,-.5);
            mc.getItemRenderer().renderModelLists(mc.getModelManager().getModel(ResourceLocation.fromNamespaceAndPath("tnc","projectile/"+model)),
                    net.minecraft.world.item.ItemStack.EMPTY,light,OverlayTexture.NO_OVERLAY,pose,
                    buffers.getBuffer(RenderType.entityCutoutNoCull(net.minecraft.client.renderer.texture.TextureAtlas.LOCATION_BLOCKS)));
        }else {
        net.spell_engine.api.render.CustomModels.render(net.spell_engine.client.render.SpellModelHelper.LAYERS.get(emission),
                Minecraft.getInstance().getItemRenderer(),ResourceLocation.fromNamespaceAndPath("tnc","projectile/"+model),pose,buffers,light,OverlayTexture.NO_OVERLAY);
        }
    }
}
