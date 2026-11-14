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

    /**
     * Moves the pose onto a bone's own <b>pivot</b> — the frame a bone-attached model
     * has to be drawn in.
     *
     * <p>Why it is needed: GeckoLib's {@code renderRecursively} pushes the pose, applies the
     * bone transform, draws that bone's cubes and children, then pops it again. A renderer
     * hook that runs <i>after</i> {@code super.renderRecursively(bone, ...)} therefore holds
     * the <b>parent's</b> frame, not the bone's ✗: the anchor's pivot offset and — far worse —
     * its animation (every wrist key of every clip) would be ignored, so a held weapon would
     * stay bolted to the hand bone and never swing.
     *
     * <p>This re-applies exactly what GeckoLib applied, in its order
     * ({@code T(pos) · T(pivot) · Rz · Ry · Rx · scale}), and stops at the pivot so the
     * caller's own offsets start from the bone.
     */
    public static void atBone(PoseStack pose, software.bernie.geckolib.cache.object.GeoBone bone){
        pose.translate(bone.getPosX()/16.0F,bone.getPosY()/16.0F,bone.getPosZ()/16.0F);
        pose.translate(bone.getPivotX()/16.0F,bone.getPivotY()/16.0F,bone.getPivotZ()/16.0F);
        if(bone.getRotZ()!=0.0F)pose.mulPose(Axis.ZP.rotation(bone.getRotZ()));
        if(bone.getRotY()!=0.0F)pose.mulPose(Axis.YP.rotation(bone.getRotY()));
        if(bone.getRotX()!=0.0F)pose.mulPose(Axis.XP.rotation(bone.getRotX()));
        if(bone.getScaleX()!=1.0F||bone.getScaleY()!=1.0F||bone.getScaleZ()!=1.0F){
            pose.scale(bone.getScaleX(),bone.getScaleY(),bone.getScaleZ());
        }
    }
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
    /**
     * 风球（t1/t2 的风灵形态、风球链的飞行体）。
     *
     * <p>2026-11-07 重排：以前的球是「内核 + 3 层随机倾角的 cage + 2 道收缩环 + 一圈平躺的月牙刃」✗
     * —— 三个 cage 的倾角是 35/85/135 度，谁也不跟谁垂直，叠起来读成"一团乱铁丝" ✗；
     * 月牙刃全在同一个水平面（{@code cos/sin} 只走 XZ），远看是一条扁扁的旋转木马 ✗。
     *
     * <p>现在：
     * <ol>
     *   <li><b>内核</b>两层反向自转（一层 .90、一层 .34 透明度、缩到 .82）⇒ 球心自己在搅动 ✓；</li>
     *   <li><b>三条大圆经线</b>互成 60 度、都立起来（X 90 度）⇒ 才是"球"而不是乱线 ✓，交替正反转；</li>
     *   <li><b>赤道环</b>反向转，和经线十字交叉 ⇒ 有经纬球的读感 ✓；</li>
     *   <li><b>两道收缩碎环</b>一上一下对称收进来（以前是同一个 75 度）✓；</li>
     *   <li><b>月牙刃</b>分到 3 个倾角（0/38/76 度）的轨道面上 ⇒ 包住整颗球 ✓；</li>
     *   <li>整体加一点点 <b>呼吸缩放</b>（±2%）⇒ 静止时也不会像一块死模型 ✓。</li>
     * </ol>
     * 绘制次数：2 内核 + 4 cage + 2 碎环 + N 月牙（tier 决定）＝和旧版基本一致 ✓，不会多烧 GPU。
     */
    public static void orb(int tier,double age,PoseStack p,MultiBufferSource b){
        p.pushPose();p.translate(0,.2,0);
        float pulse=1F+.02F*(float)Math.sin(age*.11);
        float s=(.72F+tier*.08F)*pulse;p.scale(s,s,s);
        p.pushPose();p.mulPose(Axis.YP.rotationDegrees((float)(age*2%360)));draw("core",p,b,.90F);p.popPose();
        p.pushPose();p.mulPose(Axis.XP.rotationDegrees(90));p.mulPose(Axis.ZP.rotationDegrees((float)(-age*3.1%360)));
        p.scale(.82F,.82F,.82F);draw("core",p,b,.34F);p.popPose();
        // Great-circle meridians: mutually 60 deg apart and all upright -> reads as a sphere.
        for(int i=0;i<3;i++){
            p.pushPose();
            p.mulPose(Axis.YP.rotationDegrees(i*60));
            p.mulPose(Axis.XP.rotationDegrees(90));
            p.mulPose(Axis.ZP.rotationDegrees((float)((age*(2.6+i*.5)+(i%2==0?1:-1)*40)%360)));
            draw("cage",p,b,.34F);
            p.popPose();
        }
        p.pushPose();p.mulPose(Axis.ZP.rotationDegrees((float)((-age*2.2)%360)));draw("cage",p,b,.26F);p.popPose();
        // Converging broken rings give the core a visible inward flow, at a fixed draw budget.
        for(int i=0;i<2;i++){
            double phase=(age/30+i*.5)%1;float size=(float)(1.9-phase*1.55);
            p.pushPose();p.mulPose(Axis.XP.rotationDegrees(i==0?82:-82));
            p.mulPose(Axis.ZP.rotationDegrees((float)((age*5*(i==0?1:-1)+i*180)%360)));
            p.scale(size,size,size);draw("cage",p,b,(float)(Math.sin(phase*Math.PI)*.26));p.popPose();
        }
        int blades=WindArsenalRules.blades(tier);
        for(int i=0;i<blades;i++){
            double a=age*.055+i*Math.PI*2/blades;
            double tilt=(i%3)*38.0;
            p.pushPose();
            p.mulPose(Axis.YP.rotation((float)a));
            p.mulPose(Axis.ZP.rotationDegrees((float)tilt));
            p.translate(.92,0,0);
            p.mulPose(Axis.YP.rotation((float)-a));
            p.mulPose(Axis.XP.rotationDegrees(75));p.scale(.34F,.34F,.34F);
            draw("crescent",p,b,.74F);p.popPose();
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
