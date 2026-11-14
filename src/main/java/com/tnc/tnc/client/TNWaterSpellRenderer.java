package com.tnc.tnc.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import com.tnc.tnc.magic.water.*;

public final class TNWaterSpellRenderer extends EntityRenderer<TNWaterSpellEntity> {
    public TNWaterSpellRenderer(EntityRendererProvider.Context context){super(context);shadowRadius=0;}
    @Override public ResourceLocation getTextureLocation(TNWaterSpellEntity e){return ResourceLocation.fromNamespaceAndPath("minecraft","textures/block/water_still.png");}
    @Override public boolean shouldRender(TNWaterSpellEntity e,Frustum f,double x,double y,double z){return e.tier()==3?e.distanceToSqr(x,y,z)<256*256:e.distanceToSqr(x,y,z)<144*144 && f.isVisible(
            new net.minecraft.world.phys.AABB(e.position(),e.position().add(e.direction().scale(e.length())))
                    .inflate(e.circleRadius()*1.3));}
    @Override public void render(TNWaterSpellEntity e,float yaw,float partial,PoseStack stack,MultiBufferSource buffers,int light) {
        buffers=WaterTransparencyPass.route(buffers);
        // ★ 2026-11-10 作者："把 t3 改回不是蓝金水渊兽的版本" ⇒ 水渊兽虚影 + 巨剑那套渲染整块删掉 ✗。
        //   t3 现在和其它档一样：只画**阵口**（richCircle），水炮弹本身走 WaterBolt 的弹道 ✓
        //   （t3 的 length 恒为 0 ⇒ 下面那根光束自己就 return 了 ✓）。
        double age=e.age()+partial,charge=WaterSpellRules.charge(e.tier());
        float fade=e.firedAt()<0?1:(float)Math.min(1,(e.firedAt()+e.duration()-age)/10);
        if(fade<=0)return;
        Vec3 dir=e.direction();
        double animation=e.visualAge(partial);
        double circleRadius=e.circleRadius()*Math.min(1,.45+animation/Math.max(1,charge));
        int satellites=circleRadius>20?2:e.tier()>=3?3:2;
        boolean avoid=FirstPersonSpellView.own(e.visualOwner());
        if(!avoid)WaterGeometry.richCircle(buffers,stack,Vec3.ZERO,dir,circleRadius,animation,fade,satellites,e.tier());
        Vec3 cam=net.minecraft.client.Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
        double distance=cam.distanceTo(e.position());
        double skip=avoid?Math.min(e.length(),FirstPersonSpellView.clearance(WaterSpellRules.radius(e.tier()))):0;
        // 闪电是"正对镜头的加色光带"✗ ⇒ 必须把相机换算到光束自己的坐标系里（见 WaterBeamVisuals ✓）
        Vec3 camLocal=cam.subtract(e.position()).subtract(dir.scale(skip));
        stack.pushPose();stack.translate(dir.x*skip,dir.y*skip,dir.z*skip);
        WaterVisualBuffers.draw(buffers,(body,glow) -> WaterBeamVisuals.render(body,glow,
                stack.last().pose(),e.tier(),dir,e.length()-skip,age,fade,distance<64,camLocal));
        stack.popPose();
    }
}
