package com.tnc.tnc.client;

import com.mojang.blaze3d.vertex.*;
import com.mojang.math.Axis;
import com.tnc.tnc.magic.TNWindSpiritEntity;
import com.tnc.tnc.magic.wind.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.texture.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;

/** Reused baked atlas models. Orbiting blades are render instances, never extra entities. */
public final class WindConstructModels {
    public static final String[] NAMES={"core","cage","crescent","sword","spear","chakram"};
    public static ResourceLocation id(String name){return ResourceLocation.fromNamespaceAndPath("tnc","wind/arsenal/"+name);}
    public static void draw(String name,PoseStack p,MultiBufferSource b,float alpha){
        p.pushPose();p.translate(-.5,-.5,-.5);
        var mc=Minecraft.getInstance();boolean metal=name.equals("sword")||name.equals("spear");
        var layer=metal&&alpha>=.97F?RenderType.entityCutoutNoCull(TextureAtlas.LOCATION_BLOCKS):WindModelRenderTypes.alpha();
        mc.getItemRenderer().renderModelLists(mc.getModelManager().getModel(id(name)),ItemStack.EMPTY,
                LightTexture.FULL_BRIGHT,OverlayTexture.NO_OVERLAY,p,new Tint(b.getBuffer(layer),alpha,metal));
        p.popPose();
    }
    public static void weapon(int form,PoseStack p,MultiBufferSource b,float alpha,boolean tip){
        p.pushPose();if(tip&&(form==1||form==2))p.translate(0,0,-1);
        draw(NAMES[2+Math.max(0,Math.min(3,form))],p,b,alpha);p.popPose();
    }
    /** Spell instances share the held weapon mesh, with translucent wind light over the whole blade. */
    public static void phantomWeapon(int form,double age,PoseStack p,MultiBufferSource b,float envelope,boolean tip){
        p.pushPose();if(tip&&(form==1||form==2))p.translate(0,0,-1);
        String name=NAMES[2+Math.max(0,Math.min(3,form))];
        p.translate(-.5,-.5,-.5);
        var mc=Minecraft.getInstance();var model=mc.getModelManager().getModel(id(name));
        mc.getItemRenderer().renderModelLists(model,ItemStack.EMPTY,LightTexture.FULL_BRIGHT,OverlayTexture.NO_OVERLAY,
                p,new WindLight(b.getBuffer(WindModelRenderTypes.alpha()),.78F*envelope));
        float glow=(.15F+.025F*(float)Math.sin(age*.09))*envelope;
        mc.getItemRenderer().renderModelLists(model,ItemStack.EMPTY,LightTexture.FULL_BRIGHT,OverlayTexture.NO_OVERLAY,
                p,new WindLight(b.getBuffer(WindModelRenderTypes.emission()),glow));
        p.popPose();
    }
    private record WindLight(VertexConsumer out,float alpha) implements VertexConsumer {
        public VertexConsumer vertex(double x,double y,double z){out.vertex(x,y,z);return this;}
        public VertexConsumer color(int r,int g,int b,int a){out.color(Math.min(255,(int)(r*.58F+28)),Math.min(255,(int)(g*.80F+48)),Math.min(255,(int)(b*.75F+65)),(int)(a*alpha));return this;}
        public VertexConsumer uv(float u,float v){out.uv(u,v);return this;}
        public VertexConsumer overlayCoords(int u,int v){out.overlayCoords(u,v);return this;}
        public VertexConsumer uv2(int u,int v){out.uv2(u,v);return this;}
        public VertexConsumer normal(float x,float y,float z){out.normal(x,y,z);return this;}
        public void endVertex(){out.endVertex();}
        public void defaultColor(int r,int g,int b,int a){out.defaultColor(Math.min(255,(int)(r*.58F+28)),Math.min(255,(int)(g*.80F+48)),Math.min(255,(int)(b*.75F+65)),(int)(a*alpha));}
        public void unsetDefaultColor(){out.unsetDefaultColor();}
    }
    public static void orb(int tier,double age,PoseStack p,MultiBufferSource b){
        p.pushPose();p.translate(0,.2,0);float s=.72F+tier*.08F;p.scale(s,s,s);
        p.pushPose();p.mulPose(Axis.YP.rotationDegrees((float)(age*2%360)));draw("core",p,b,.88F);p.popPose();
        for(int i=0;i<3;i++){
            p.pushPose();p.mulPose(Axis.YP.rotationDegrees(i*60));p.mulPose(Axis.XP.rotationDegrees(35+i*50));
            p.mulPose(Axis.ZP.rotationDegrees((float)((age*(3.2+i*.6)+i*120)%360)));
            draw("cage",p,b,.38F);p.popPose();
        }
        // Converging broken rings give the core a visible inward flow, at a fixed draw budget.
        for(int i=0;i<2;i++){
            double phase=(age/28+i*.5)%1;float size=(float)(1.8-phase*1.5);
            p.pushPose();p.mulPose(Axis.XP.rotationDegrees(75));p.mulPose(Axis.ZP.rotationDegrees((float)((age*5+i*180)%360)));
            p.scale(size,size,size);draw("cage",p,b,(float)(Math.sin(phase*Math.PI)*.28));p.popPose();
        }
        int blades=WindArsenalRules.blades(tier);
        for(int i=0;i<blades;i++){
            double a=age*.055+i*Math.PI*2/blades;
            p.pushPose();p.translate(Math.cos(a)*.88,Math.sin(a*2)*.12,Math.sin(a)*.88);
            p.mulPose(Axis.YP.rotation((float)-a));p.mulPose(Axis.XP.rotationDegrees(75));p.scale(.36F,.36F,.36F);
            draw("crescent",p,b,.72F);p.popPose();
        }
        p.popPose();
    }
    public static void escorts(TNWindSpiritEntity e,float partial,PoseStack p,MultiBufferSource b){
        double age=e.level().getGameTime()+partial;
        for(int i=0;i<WindArsenalRules.escorts(e.tier());i++){
            int form=i+1;double a=age*.027+i*Math.PI*2/WindArsenalRules.escorts(e.tier());
            Vec3 idle=new Vec3(Math.cos(a)*.25,.94,Math.sin(a)*.25).normalize();
            double elapsed=WindArsenal.attackAge(e,partial);
            float lunge=WindArsenal.form(e)==form&&elapsed>=0&&elapsed<7?(float)Math.sin(elapsed*Math.PI/7):0;
            Vec3 aim=idle.scale(1-lunge).add(WindArsenal.direction(e).scale(lunge)).normalize();
            p.pushPose();p.translate(Math.cos(a)*.8,1+Math.sin(a*2)*.12,Math.sin(a)*.8);
            p.translate(aim.x*lunge*.55,aim.y*lunge*.55,aim.z*lunge*.55);
            p.mulPose(new Quaternionf().rotationTo(0,0,1,(float)aim.x,(float)aim.y,(float)aim.z));
            if(form==3)p.mulPose(Axis.ZP.rotationDegrees((float)(age*4%360)));
            p.scale(.48F,.48F,.48F);weapon(form,p,b,.78F,false);p.popPose();
        }
    }
    private record Tint(VertexConsumer out,float alpha,boolean metal) implements VertexConsumer {
        public VertexConsumer vertex(double x,double y,double z){out.vertex(x,y,z);return this;}
        public VertexConsumer color(int r,int g,int b,int a){out.color(metal?r:(int)(r*.72F),g,metal?b:(int)(b*.94F),(int)(a*alpha));return this;}
        public VertexConsumer uv(float u,float v){out.uv(u,v);return this;}
        public VertexConsumer overlayCoords(int u,int v){out.overlayCoords(u,v);return this;}
        public VertexConsumer uv2(int u,int v){out.uv2(u,v);return this;}
        public VertexConsumer normal(float x,float y,float z){out.normal(x,y,z);return this;}
        public void endVertex(){out.endVertex();}
        public void defaultColor(int r,int g,int b,int a){out.defaultColor(r,g,b,(int)(a*alpha));}
        public void unsetDefaultColor(){out.unsetDefaultColor();}
    }
}
