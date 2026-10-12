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
        double age=e.age(partial)-e.waitTicks();if(e.done()||age<0&&e.mode()!=0&&e.mode()!=3)return;
        float growth=e.mode()==2?(float)Math.max(.04,Math.min(1,age/45)):e.mode()==1?(float)Math.max(.03,Math.min(1,age/8)):1;
        pose.pushPose();if(e.mode()==0||e.mode()==3||e.mode()==5){pose.mulPose(Axis.YP.rotationDegrees(e.getYRot()));pose.mulPose(Axis.XP.rotationDegrees(e.getXRot()));}
        if(e.mode()==0||e.mode()==3||e.mode()==5)pose.scale(e.scale(),e.scale(),e.height());else pose.scale(e.scale()*growth,e.height()*growth,e.scale()*growth);
        pose.translate(0,e.mode()==2?0:.5,0);
        net.spell_engine.api.render.CustomModels.render(net.spell_engine.client.render.SpellModelHelper.LAYERS.get(net.spell_engine.api.render.LightEmission.NONE),
                Minecraft.getInstance().getItemRenderer(),ResourceLocation.fromNamespaceAndPath("tnc","projectile/"+e.model()),pose,buffers,light,OverlayTexture.NO_OVERLAY);
        pose.popPose();
        if(e.mode()==2&&age>=0&&age<45)for(int i=0;i<8;i++) {
            double a=i*Math.PI/4+age*.09,r=(1-growth)*8+.6;
            pose.pushPose();pose.translate(Math.cos(a)*r,-e.height()*(1-growth)*.7,Math.sin(a)*r);
            pose.mulPose(Axis.YP.rotationDegrees((float)(a*180/Math.PI)));pose.scale(.55F,.55F,.55F);
            net.spell_engine.api.render.CustomModels.render(net.spell_engine.client.render.SpellModelHelper.LAYERS.get(net.spell_engine.api.render.LightEmission.NONE),
                    Minecraft.getInstance().getItemRenderer(),ResourceLocation.fromNamespaceAndPath("tnc","projectile/earth_clod"),pose,buffers,light,OverlayTexture.NO_OVERLAY);
            pose.popPose();
        }
    }
}
