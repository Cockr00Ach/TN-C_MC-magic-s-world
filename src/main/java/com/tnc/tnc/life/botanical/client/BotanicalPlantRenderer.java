package com.tnc.tnc.life.botanical.client;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.tnc.tnc.life.botanical.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.blockentity.*;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.Mth;
/** Whole rooted geometry moves as one mesh, preventing split stems and buried roots. */
public final class BotanicalPlantRenderer implements BlockEntityRenderer<BotanicalPlantEntity> {
    public BotanicalPlantRenderer(BlockEntityRendererProvider.Context ignored){}
    public static void model(String name,PoseStack pose,MultiBufferSource buffers,int light){var mc=Minecraft.getInstance();var model=mc.getModelManager().getModel(BotanicalContent.id("block/"+name));mc.getBlockRenderer().getModelRenderer().renderModel(pose.last(),buffers.getBuffer(RenderType.cutout()),null,model,1,1,1,light,OverlayTexture.NO_OVERLAY);}
    public static void drawPlant(net.minecraft.world.level.block.state.BlockState state,PoseStack pose,MultiBufferSource buffers,int light){
        var mc=Minecraft.getInstance();var model=mc.getBlockRenderer().getBlockModel(state);
        var output=buffers.getBuffer(RenderType.cutout());
        for(int side=0;side<7;side++)for(var quad:model.getQuads(state,side==6?null:net.minecraft.core.Direction.values()[side],net.minecraft.util.RandomSource.create(42))){
            int color=quad.isTinted()?PlantPalette.color(state,quad.getTintIndex()):0xffffff;
            output.putBulkData(pose.last(),quad,(color>>16&255)/255f,(color>>8&255)/255f,(color&255)/255f,quad.getTintIndex()==4?15728880:light,OverlayTexture.NO_OVERLAY);
        }
    }
    @Override public int getViewDistance(){return 32;}
    @Override public void render(BotanicalPlantEntity be,float partial,PoseStack pose,MultiBufferSource buffers,int light,int overlay){
        if(be.getLevel()==null)return;var player=Minecraft.getInstance().player;if(player==null)return;
        boolean mature=be.getBlockState().getValue(BotanicalBlock.AGE)==3;
        float time=be.getLevel().getGameTime()+partial;
        float sway=mature?Mth.sin(time*.035f+(be.getBlockPos().asLong()%91))*.65f:0;
        if(mature&&be.species()==BotanicalSpecies.DANCE_BELL){
            float strength=Mth.lerp(Mth.clamp((time-be.danceUpdated)/20F,0,1),be.danceBefore,be.dance)/60F;
            if(be.owner==null)strength=player.distanceToSqr(be.getBlockPos().getCenter())<64?.25f:0;
            sway=Mth.sin(time*.12f)*Math.min(1,strength)*8;
        }
        pose.pushPose();pose.translate(.5,.12,.5);pose.mulPose(Axis.ZP.rotationDegrees(sway));pose.translate(-.5,-.12,-.5);
        drawPlant(be.getBlockState(),pose,buffers,light);pose.popPose();
    }
}
