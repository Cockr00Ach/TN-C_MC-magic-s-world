package com.tnc.tnc.client;
import com.tnc.tnc.magic.*;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.entity.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
public final class DarkSiphonRenderer extends EntityRenderer<TNDarkDrainEntity> {
    public DarkSiphonRenderer(EntityRendererProvider.Context c){super(c);}
    public ResourceLocation getTextureLocation(TNDarkDrainEntity e){return ResourceLocation.fromNamespaceAndPath("minecraft","textures/block/redstone_block.png");}
    public void render(TNDarkDrainEntity e,float yaw,float partial,PoseStack pose,MultiBufferSource buffers,int light) {
        var a=e.level().getEntity(e.victimId());var b=e.level().getEntity(e.casterId());
        if(!(a instanceof LivingEntity victim)||!(b instanceof LivingEntity caster)||!a.isAlive()||!b.isAlive())return;
        Vec3 origin=e.getPosition(partial),start=victim.getPosition(partial).add(0,victim.getBbHeight()*.55,0).subtract(origin),
                end=caster.getPosition(partial).add(0,caster.getBbHeight()*.62,0).subtract(origin);
        for(int i=0;i<DarkSiphonRules.strands(e.tier());i++)NightHandGeometry.tube(buffers.getBuffer(NightHandRenderTypes.geometry()),
                pose.last().pose(),start,end,.035+e.tier()*.006,i,true,e.tickCount+partial);
    }
}
