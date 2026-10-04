package com.tnc.tnc.life.botanical.client;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.tnc.tnc.life.botanical.*;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.*;
import net.minecraft.resources.ResourceLocation;
public final class BotanicalSpriteRenderer extends EntityRenderer<BotanicalSprite> {
    public BotanicalSpriteRenderer(EntityRendererProvider.Context context){super(context);shadowRadius=.08F;}
    @Override public void render(BotanicalSprite e,float yaw,float partial,PoseStack pose,MultiBufferSource buffers,int light){pose.pushPose();pose.scale(.24F,.24F,.24F);pose.translate(-.5,0,-.5);BotanicalPlantRenderer.model("botanical_sprite_body",pose,buffers,0xF000F0-(e.variant()*0x10000));for(int side:new int[]{-1,1}){pose.pushPose();pose.translate(.5,.45,.5);pose.mulPose(Axis.YP.rotationDegrees(side*(25+(float)Math.sin((e.tickCount+partial)*.65)*24)));pose.scale(side,1,1);pose.translate(-.5,-.45,-.5);BotanicalPlantRenderer.model("botanical_sprite_wing",pose,buffers,0xF000F0);pose.popPose();}pose.popPose();super.render(e,yaw,partial,pose,buffers,light);}
    @Override public ResourceLocation getTextureLocation(BotanicalSprite e){return BotanicalContent.id("textures/block/star_rest_petal.png");}
}
