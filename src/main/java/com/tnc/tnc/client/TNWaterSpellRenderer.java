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
    @Override public boolean shouldRender(TNWaterSpellEntity e,Frustum f,double x,double y,double z){return e.distanceToSqr(x,y,z)<144*144 && f.isVisible(
            new net.minecraft.world.phys.AABB(e.position(),e.position().add(e.direction().scale(e.length())))
                    .inflate(WaterSpellRules.circleRadius(e.tier())*1.3));}
    @Override public void render(TNWaterSpellEntity e,float yaw,float partial,PoseStack stack,MultiBufferSource buffers,int light) {
        double age=e.age()+partial,charge=WaterSpellRules.charge(e.tier());
        float fade=e.firedAt()<0?1:(float)Math.min(1,(e.firedAt()+WaterSpellRules.duration(e.tier())-age)/10);
        if(fade<=0)return;
        Vec3 dir=e.direction();
        double animation=e.visualAge(partial);
        double circleRadius=WaterSpellRules.circleRadius(e.tier())*Math.min(1,.45+animation/Math.max(1,charge));
        WaterGeometry.richCircle(buffers,stack,Vec3.ZERO,dir,circleRadius,animation,fade,3);
        double distance=net.minecraft.client.Minecraft.getInstance().gameRenderer.getMainCamera().getPosition().distanceTo(e.position());
        WaterVisualBuffers.draw(buffers,(body,glow) -> WaterBeamVisuals.render(body,glow,
                stack.last().pose(),e.tier(),dir,e.length(),age,fade,distance<64));
    }
}
