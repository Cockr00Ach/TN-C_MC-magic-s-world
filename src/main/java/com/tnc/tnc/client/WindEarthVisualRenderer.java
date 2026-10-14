package com.tnc.tnc.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.tnc.tnc.magic.WindEarthVisualEntity;
import com.tnc.tnc.magic.WindEarthVisualRules;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.*;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import java.util.WeakHashMap;

public final class WindEarthVisualRenderer extends EntityRenderer<WindEarthVisualEntity> {
    private final EarthSpellModelRenderer earthModels=new EarthSpellModelRenderer();
    private final WeakHashMap<WindEarthVisualEntity,WindEarthGroundSurface> surfaces=new WeakHashMap<>();
    public WindEarthVisualRenderer(EntityRendererProvider.Context context){super(context);shadowRadius=0;}
    public ResourceLocation getTextureLocation(WindEarthVisualEntity entity){return ResourceLocation.fromNamespaceAndPath("minecraft","textures/block/stone.png");}
    @Override public void render(WindEarthVisualEntity entity,float yaw,float partial,PoseStack poses,MultiBufferSource buffers,int light) {
        double age=entity.visualAge(partial);
        float envelope=WindEarthVisualRules.envelope(age,entity.life());
        if(envelope<=.005)return;
        Entity owner=entity.level().getEntity(entity.ownerId());
        Vec3 centre=entity.followsOwner()&&owner!=null?owner.getPosition(partial):entity.position();
        poses.pushPose();
        Vec3 offset=centre.subtract(entity.getPosition(partial));poses.translate(offset.x,offset.y,offset.z);
        WindEarthGeometry.Ground ground=WindEarthGeometry.FLAT;
        if(entity.kind()==4||entity.kind()==5) {
            WindEarthGroundSurface surface=surfaces.get(entity);
            if(surface==null||!surface.origin.equals(BlockPos.containing(centre))) {
                surface=new WindEarthGroundSurface(entity.level(),centre,entity.radius());surfaces.put(entity,surface);
            }
            ground=surface;
        }
        if(entity.kind()==0) {
            poses.mulPose(Axis.YP.rotationDegrees(-(owner==null?entity.getYRot():owner.getViewYRot(partial))));
            if(owner==Minecraft.getInstance().player&&Minecraft.getInstance().options.getCameraType().isFirstPerson())envelope*=.55F;
        }
        if(entity.kind()==3) {
            poses.mulPose(Axis.YP.rotationDegrees(-entity.getYRot()));
            poses.translate(0,.35,.7);
        }
        if(entity.kind()>=3) {
            earthModels.render(entity,age,envelope,poses,buffers,light,ground);
            poses.popPose();return;
        }
        if(entity.kind()==0){WindSolidModels.flight(entity.tier(),age,envelope,poses,buffers);poses.popPose();return;}
        var matrix=poses.last().pose();var terrain=ground;float alpha=envelope;
        WindEarthVisualBuffers.draw(buffers,(body,glow)->WindEarthGeometry.spell(body,glow,matrix,
                entity.kind(),entity.tier(),entity.radius(),age,alpha,terrain));
        poses.popPose();
    }
}
