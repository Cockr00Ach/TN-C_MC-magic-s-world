package com.tnc.tnc.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.tnc.tnc.magic.EarthModelLayout;
import com.tnc.tnc.magic.WindEarthVisualEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import java.util.List;
import java.util.WeakHashMap;

/** The same baked, textured model path as thunder orbs; normal terrain lighting. */
final class EarthSpellModelRenderer {
    private final WeakHashMap<WindEarthVisualEntity,List<EarthModelLayout.Piece>> layouts=new WeakHashMap<>();
    void render(WindEarthVisualEntity entity,double age,float scale,PoseStack poses,MultiBufferSource buffers,
                int light,WindEarthGeometry.Ground ground) {
        var pieces=layouts.computeIfAbsent(entity,e->EarthModelLayout.create(e.kind(),e.tier(),e.radius()));
        poses.pushPose();if(entity.kind()==6)poses.mulPose(Axis.YP.rotationDegrees((float)(age*1.8)));
        for(int i=0;i<pieces.size();i++) {
            var piece=pieces.get(i);
            double rise=entity.kind()==4?Math.min(1,Math.max(0,(age-i%5)/10)):1;
            poses.pushPose();
            double y=piece.y()+(entity.kind()==3?0:ground.y(piece.x(),piece.z()));
            if(entity.kind()==5)y+=Math.sin(age*.24+i*.8)*entity.tier()*.045;
            if(entity.kind()==6&&piece.model().equals("earth_root"))y+=.12+Math.sin(age*.08+i)*.08;
            if(entity.kind()==3)y+=Math.sin(age*.06+i)*.06;
            if(entity.kind()==5&&piece.model().equals("earth_orb"))y+=Math.sin(age*.04)*.12;
            poses.translate(piece.x(),y,piece.z());
            poses.mulPose(Axis.YP.rotationDegrees((float)(piece.yaw()+(entity.kind()==3?age*1.7:0))));
            poses.scale((float)piece.width()*scale,(float)(piece.height()*rise)*scale,(float)piece.width()*scale);
            // Models use x/z=8, y=0 as their anchor; CustomModels subtracts half a block.
            poses.translate(0,.5,0);
            var id=ResourceLocation.fromNamespaceAndPath("tnc","projectile/"+piece.model());
            var layer=net.spell_engine.client.render.SpellModelHelper.LAYERS.get(net.spell_engine.api.render.LightEmission.NONE);
            net.spell_engine.api.render.CustomModels.render(layer,Minecraft.getInstance().getItemRenderer(),id,
                    poses,buffers,light,OverlayTexture.NO_OVERLAY);
            poses.popPose();
        }
        poses.popPose();
    }
}
