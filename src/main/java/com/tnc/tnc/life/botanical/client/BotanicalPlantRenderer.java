package com.tnc.tnc.life.botanical.client;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.tnc.tnc.life.botanical.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.blockentity.*;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.Mth;
/** Real articulated geometry; mature petals are rendered separately from the rooted base. */
public final class BotanicalPlantRenderer implements BlockEntityRenderer<BotanicalPlantEntity> {
    public BotanicalPlantRenderer(BlockEntityRendererProvider.Context ignored){}
    public static void model(String name,PoseStack pose,MultiBufferSource buffers,int light){var mc=Minecraft.getInstance();var model=mc.getModelManager().getModel(BotanicalContent.id("block/"+name));mc.getBlockRenderer().getModelRenderer().renderModel(pose.last(),buffers.getBuffer(RenderType.cutout()),null,model,1,1,1,light,OverlayTexture.NO_OVERLAY);}
    @Override public void render(BotanicalPlantEntity be,float partial,PoseStack pose,MultiBufferSource buffers,int light,int overlay){if(be.getLevel()==null||be.getBlockState().getValue(BotanicalBlock.AGE)<3)return;var player=Minecraft.getInstance().player;if(player==null)return;boolean animate=player.distanceToSqr(be.getBlockPos().getX()+.5,be.getBlockPos().getY()+.5,be.getBlockPos().getZ()+.5)<=1024;float time=animate?be.getLevel().getGameTime()+partial:0;
        if(be.species()==BotanicalSpecies.DANCE_BELL){float strength=Mth.lerp(Mth.clamp((time-be.danceUpdated)/20F,0,1),be.danceBefore,be.dance)/60F;if(be.owner==null)strength=animate&&player.distanceToSqr(be.getBlockPos().getX()+.5,be.getBlockPos().getY()+.5,be.getBlockPos().getZ()+.5)<64?.15F:0;if(!animate)strength=0;float sway=Mth.sin(time*.12F)*strength*18;pose.pushPose();pose.translate(.5,.25,.5);pose.mulPose(Axis.ZP.rotationDegrees(sway*.55F));pose.translate(-.5,0,-.5);model("dance_bell_joint",pose,buffers,light);pose.translate(.5,.22,.5);pose.mulPose(Axis.XP.rotationDegrees(Mth.cos(time*.1F)*strength*9));pose.mulPose(Axis.ZP.rotationDegrees(sway*.45F));pose.translate(-.5,0,-.5);model("dance_bell_joint",pose,buffers,light);pose.translate(.5,.2,.5);pose.mulPose(Axis.ZP.rotationDegrees(-sway*.4F));pose.translate(-.5,0,-.5);model("dance_bell_head",pose,buffers,light);pose.popPose();}
        else if(be.species()==BotanicalSpecies.DAWN_DISK){pose.pushPose();pose.translate(.5,.625,.5);float clock=Math.floorMod(be.getLevel().getDayTime(),24000)+partial;float turn=clock<60?Mth.clamp(clock/60F,0,1)*90:90;pose.mulPose(Axis.YP.rotationDegrees(turn));pose.translate(-.5,-.625,-.5);model("dawn_disk_head",pose,buffers,light);pose.popPose();}
        else if(animate&&be.species()==BotanicalSpecies.MIST_COTTON&&be.ready){float clock=Math.floorMod(be.getLevel().getDayTime(),24000);if(clock<120||be.getLevel().isRaining()){pose.pushPose();pose.translate(.5,.5,.5);pose.mulPose(Axis.YP.rotationDegrees(time*3));pose.translate(-.5,-.5,-.5);model("mist_cotton_ring",pose,buffers,light);pose.popPose();}}
    }
}
