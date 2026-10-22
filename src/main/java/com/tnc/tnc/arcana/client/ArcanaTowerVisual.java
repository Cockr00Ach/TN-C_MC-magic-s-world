package com.tnc.tnc.arcana.client;

import com.tnc.tnc.life.routes.RouteNodeEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.model.geom.builders.*;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.*;
import net.minecraft.resources.ResourceLocation;

/** Shell blocks unlock and unfold into a copper containment instrument. */
public final class ArcanaTowerVisual {
    private static final ModelPart CUBE=cube();
    private static ModelPart cube(){var mesh=new MeshDefinition();mesh.getRoot().addOrReplaceChild("part",CubeListBuilder.create().texOffs(0,0).addBox(-8,-8,-8,16,16,16),net.minecraft.client.model.geom.PartPose.ZERO);return LayerDefinition.create(mesh,64,32).bakeRoot();}
    public static void render(RouteNodeEntity n,float partial,PoseStack p,MultiBufferSource buffers,int light,int overlay){
        if(!n.getPersistentData().getBoolean("ShellFormed"))return;
        double age=n.getLevel().getGameTime()+partial,time=age-n.getPersistentData().getLong("TowerFormedAt");
        float unfold=(float)Math.max(0,Math.min(1,time/36));unfold=unfold*unfold*(3-2*unfold);
        var bronze=buffers.getBuffer(RenderType.entityCutout(ResourceLocation.parse("tnc:textures/block/alchemy_bronze.png")));
        var ceramic=buffers.getBuffer(RenderType.entityCutout(ResourceLocation.parse("tnc:textures/block/alchemy_ceramic.png")));
        part(p,ceramic,.5,.16,.5,2.9,.32,2.9,light,overlay);part(p,bronze,.5,.34,.5,2.75,.08,2.75,light,overlay);
        for(int x:new int[]{-1,1})for(int z:new int[]{-1,1}){
            double dx=.5+x*(.72+.42*unfold),dz=.5+z*(.72+.42*unfold);
            part(p,ceramic,dx,1.5,dz,.3,2.4,.3,light,overlay);
            for(double y:new double[]{.55,1.5,2.55})part(p,bronze,dx,y,dz,.39,.13,.39,light,overlay);
        }
        part(p,bronze,.5,2.85,.5,2.7,.16,2.7,light,overlay);
        for(int r=0;r<3;r++){
            p.pushPose();p.translate(.5,1.1+r*.5,.5);p.mulPose(Axis.YP.rotation((float)(age*.008*(r%2==0?1:-1)*unfold)));p.mulPose(Axis.XP.rotationDegrees((r-1)*18*unfold));
            for(int i=0;i<12;i++){p.pushPose();p.mulPose(Axis.YP.rotationDegrees(i*30));part(p,bronze,0,0,.73+.12*unfold,.41,.07,.10,light,overlay);p.popPose();}
            p.popPose();
        }
        p.pushPose();p.translate(.5,1.55,.5);p.mulPose(Axis.YP.rotation((float)(age*.018)));p.mulPose(Axis.ZP.rotationDegrees(45));
        part(p,ceramic,0,0,0,.52,.52,.52,light,overlay);p.popPose();
        int color=n.getPersistentData().getInt("OverloadStage")>=2?0xff5560:n.getPersistentData().getInt("OverloadStage")==1?0xffb85b:0x66e6ce;
        com.tnc.tnc.alchemy.client.AlchemyRenderer.ring(p,buffers,age,0xc0000000|color,.5f,.8f);
        com.tnc.tnc.alchemy.client.AlchemyRenderer.ring(p,buffers,-age,0xb0000000|color,2.65f,.8f);
    }
    private static void part(PoseStack p,com.mojang.blaze3d.vertex.VertexConsumer out,double x,double y,double z,double sx,double sy,double sz,int light,int overlay){p.pushPose();p.translate(x,y,z);p.scale((float)sx,(float)sy,(float)sz);CUBE.render(p,out,light,overlay);p.popPose();}
    private ArcanaTowerVisual(){}
}
