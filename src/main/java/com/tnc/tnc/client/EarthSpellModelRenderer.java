package com.tnc.tnc.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.tnc.tnc.magic.EarthModelLayout;
import com.tnc.tnc.magic.EarthRuptureLayout;
import com.tnc.tnc.magic.WindEarthVisualEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import java.util.List;
import java.util.WeakHashMap;

/**
 * The same baked, textured model path as thunder orbs; normal terrain lighting.
 * Columns punch out with an overshoot and an outward lean, and their base
 * splinters stay in the air for a beat as real block models.
 */
final class EarthSpellModelRenderer {
    private final WeakHashMap<WindEarthVisualEntity,List<EarthModelLayout.Piece>> layouts=new WeakHashMap<>();
    private final WeakHashMap<WindEarthVisualEntity,List<EarthRuptureLayout.Shard>> shards=new WeakHashMap<>();
    void render(WindEarthVisualEntity entity,double age,float scale,PoseStack poses,MultiBufferSource buffers,
                int light,WindEarthGeometry.Ground ground) {
        var pieces=layouts.computeIfAbsent(entity,e->EarthModelLayout.create(e.kind(),e.tier(),e.radius()));
        boolean punched=entity.kind()==4||entity.kind()==6;
        poses.pushPose();if(entity.kind()==6)poses.mulPose(Axis.YP.rotationDegrees((float)(age*1.8)));
        for(int i=0;i<pieces.size();i++) {
            var piece=pieces.get(i);
            // Staggered columns break surface one after another; the rest of the
            // field (mud plates, projectiles) appears at full size.
            double punch=punched?Math.max(0,Math.min(1,(age-(i%5)*2.0)/EarthRuptureLayout.PUNCH_TICKS)):1;
            double rise=punched?EarthRuptureLayout.rise(punch):1;
            double impact=punched?EarthRuptureLayout.impact(punch):0;
            poses.pushPose();
            double y=piece.y()+(entity.kind()==3?0:ground.y(piece.x(),piece.z()));
            if(entity.kind()==5)y+=Math.sin(age*.24+i*.8)*entity.tier()*.045;
            if(entity.kind()==6&&piece.model().equals("earth_root"))y+=.12+Math.sin(age*.08+i)*.08;
            if(entity.kind()==3)y+=Math.sin(age*.06+i)*.06;
            if(entity.kind()==5&&piece.model().equals("earth_orb"))y+=Math.sin(age*.04)*.12;
            poses.translate(piece.x(),y,piece.z());
            double radial=Math.hypot(piece.x(),piece.z());
            if(punched&&radial>.001) {
                // Lean the fresh column outward, then unwind as it settles.
                poses.mulPose(Axis.YP.rotationDegrees((float)-Math.toDegrees(Math.atan2(piece.z(),piece.x()))));
                poses.mulPose(Axis.ZP.rotationDegrees((float)(-7.5*impact)));
            }
            poses.mulPose(Axis.YP.rotationDegrees((float)(piece.yaw()+(entity.kind()==3?age*1.7:0))));
            float girth=(float)(piece.width()*scale*(1+.08*impact));
            poses.scale(girth,(float)(piece.height()*rise*scale),girth);
            // Models use x/z=8, y=0 as their anchor; CustomModels subtracts half a block.
            poses.translate(0,.5,0);
            model(piece.model(),poses,buffers,light);
            poses.popPose();
        }
        poses.popPose();
        if(entity.kind()==4||entity.kind()==6)drawEjecta(entity,age,scale,poses,buffers,light,ground);
    }
    private void drawEjecta(WindEarthVisualEntity entity,double age,float scale,PoseStack poses,
                            MultiBufferSource buffers,int light,WindEarthGeometry.Ground ground) {
        int columns=entity.kind()==6?10+entity.tier()*2:5+entity.tier()*2;
        var chips=shards.computeIfAbsent(entity,e->EarthRuptureLayout.ejecta(e.tier(),columns,e.radius(),e.kind()==6,2.0));
        for(var chip:chips) {
            double local=age-chip.delay();
            if(local<0||local>EarthRuptureLayout.CHIP_TICKS)continue;
            double fade=1-local/EarthRuptureLayout.CHIP_TICKS;
            double r=chip.radius()+local*.055;
            double x=Math.cos(chip.angle())*r,z=Math.sin(chip.angle())*r;
            double y=ground.y(x,z)+.16+chip.lift()*local*.05-.012*local*local;
            double size=chip.size()*(.35+.65*fade)*scale;
            if(size<=.02)continue;
            poses.pushPose();
            poses.translate(x,y,z);
            poses.mulPose(Axis.YP.rotationDegrees((float)(chip.spin()+local*24)));
            poses.mulPose(Axis.XP.rotationDegrees((float)(local*33)));
            poses.scale((float)size,(float)size,(float)size);
            model("earth_clod",poses,buffers,light);
            poses.popPose();
        }
    }
    private static void model(String name,PoseStack poses,MultiBufferSource buffers,int light) {
        var layer=net.spell_engine.client.render.SpellModelHelper.LAYERS.get(net.spell_engine.api.render.LightEmission.NONE);
        net.spell_engine.api.render.CustomModels.render(layer,Minecraft.getInstance().getItemRenderer(),
                ResourceLocation.fromNamespaceAndPath("tnc","projectile/"+name),poses,buffers,light,OverlayTexture.NO_OVERLAY);
    }
}
