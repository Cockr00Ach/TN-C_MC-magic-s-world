package com.tnc.tnc.client;
import com.tnc.tnc.magic.EarthAttackEntity;
import com.tnc.tnc.magic.EarthLaunchLayout;
import com.tnc.tnc.magic.EarthRuptureLayout;
import com.tnc.tnc.magic.EarthWeaponModels;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.entity.*;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import java.util.List;
import java.util.WeakHashMap;
public final class EarthAttackRenderer extends EntityRenderer<EarthAttackEntity> {
    private final WeakHashMap<EarthAttackEntity,List<EarthRuptureLayout.Shard>> burstShards=new WeakHashMap<>();
    private final WeakHashMap<EarthAttackEntity,List<EarthRuptureLayout.Shard>> spikeChips=new WeakHashMap<>();
    public EarthAttackRenderer(EntityRendererProvider.Context c){super(c);}
    public ResourceLocation getTextureLocation(EarthAttackEntity e){return ResourceLocation.fromNamespaceAndPath("minecraft","textures/block/stone.png");}
    public void render(EarthAttackEntity e,float yaw,float partial,PoseStack pose,MultiBufferSource buffers,int light) {
        double age=e.age(partial)-e.waitTicks();if(e.done()){renderBurst(e,partial,pose,buffers,light);if(e.mode()!=7&&e.mode()!=3)return;}if(age<0&&e.mode()!=0&&e.mode()!=3)return;
        if(e.mode()==8){renderLaunch(e,partial,pose,buffers,light);return;}
        if(e.mode()==3){renderSkySpear(e,partial,age,pose,buffers);return;}
        if(e.mode()==1&&(EarthWeaponModels.isWeapon(e.model())||EarthWeaponModels.isBossWeapon(e.model()))){renderWeaponSpike(e,partial,pose,buffers,light);return;}
        float growth=e.mode()==2?(float)Math.max(.04,Math.min(1,age/45)):e.mode()==1?(float)EarthRuptureLayout.rise(Math.max(0,Math.min(1,age/8))):1;
        pose.pushPose();if(e.mode()==0||e.mode()==3||e.mode()==5){pose.mulPose(Axis.YP.rotationDegrees(e.getYRot()));pose.mulPose(Axis.XP.rotationDegrees(e.getXRot()));}
        // earth_pillar is authored upright; stand it onto the +Z flight axis the entity renderer expects.
        if(e.mode()==0&&e.model().equals("earth_pillar"))pose.mulPose(Axis.XP.rotationDegrees(90));
        if(e.mode()==0&&e.model().equals("earth_pillar"))pose.scale(e.scale(),e.height(),e.scale());
        else if(e.mode()==0||e.mode()==3||e.mode()==5)pose.scale(e.scale(),e.scale(),e.height());else pose.scale(e.scale()*growth,e.height()*growth,e.scale()*growth);
        if(e.model().startsWith("stone_lancer_")&&(e.mode()==0||e.mode()==5))
            pose.translate(0,0,-1.5); // Centered model tip Z=1.5 stays on the swept impact point.
        else pose.translate(0,e.mode()==2?0:.5,0);
        var emission=e.mode()==3?net.spell_engine.api.render.LightEmission.RADIATE:net.spell_engine.api.render.LightEmission.NONE;
        draw(e.model(),pose,buffers,light,emission);
        pose.popPose();
        if(e.mode()==1&&age>=0)drawChips(e,age,pose,buffers,light);
        if((e.mode()==0||e.mode()==3||e.mode()==5)&&age>=0)for(int i=0;i<4;i++) {
            double a=age*.18+i*Math.PI/2,r=Math.max(.7,e.scale()*.18);
            pose.pushPose();pose.translate(Math.cos(a)*r,Math.sin(a*1.7)*.45,Math.sin(a)*r);pose.scale(.22F,.22F,.22F);
            draw("earth_clod",pose,buffers,light,net.spell_engine.api.render.LightEmission.NONE);pose.popPose();
        }
        if(e.mode()==2&&age>=0&&age<45)for(int i=0;i<8;i++) {
            double a=i*Math.PI/4+age*.09,r=((1-growth)*8+.6)*(e.gravityCore()?15:e.model().startsWith("stone_lancer_")?5:1);
            pose.pushPose();pose.translate(Math.cos(a)*r,-e.height()*(1-growth)*.7,Math.sin(a)*r);
            pose.mulPose(Axis.YP.rotationDegrees((float)(a*180/Math.PI)));pose.scale(.55F,.55F,.55F);
            net.spell_engine.api.render.CustomModels.render(net.spell_engine.client.render.SpellModelHelper.LAYERS.get(net.spell_engine.api.render.LightEmission.NONE),
                    Minecraft.getInstance().getItemRenderer(),ResourceLocation.fromNamespaceAndPath("tnc","projectile/earth_clod"),pose,buffers,light,OverlayTexture.NO_OVERLAY);
            pose.popPose();
        }
    }
    /** Giant column that erupts under a target and carries it skyward. */
    private void renderLaunch(EarthAttackEntity e,float partial,PoseStack pose,MultiBufferSource buffers,int light) {
        double age=e.age(partial);
        double grow=EarthLaunchLayout.grow(age/EarthAttackEntity.LAUNCH_RISE);
        double top=grow*e.height();
        float w=e.scale();
        int seg=0;
        for(double y=0;y<top;y+=3.2,seg++) {
            pose.pushPose();
            pose.translate(Math.sin(seg*1.7)*.08,y,Math.cos(seg*2.3)*.08);
            pose.mulPose(Axis.YP.rotationDegrees((float)(seg*29+age*1.1)));
            pose.scale(w,3.25F,w);
            pose.translate(0,.5,0);
            draw("earth_pillar",pose,buffers,light,net.spell_engine.api.render.LightEmission.NONE);
            pose.popPose();
        }
        // Broken crown around the top so the carried target looks lifted on a rock, not on a tube.
        for(int i=0;i<5;i++) {
            double a=i*Math.PI*2/5;
            pose.pushPose();
            pose.translate(Math.cos(a)*w*.9,top,Math.sin(a)*w*.9);
            pose.mulPose(Axis.YP.rotationDegrees((float)(a*180/Math.PI)));
            pose.mulPose(Axis.XP.rotationDegrees(-38));
            pose.scale(w*.55F,2.6F,w*.55F);
            pose.translate(0,.5,0);
            draw("earth_spike_sharp",pose,buffers,light,net.spell_engine.api.render.LightEmission.NONE);
            pose.popPose();
        }
        for(int i=0;i<6;i++) {
            double a=i*Math.PI/3;
            pose.pushPose();
            pose.translate(Math.cos(a)*w*.95,.15,Math.sin(a)*w*.95);
            pose.mulPose(Axis.YP.rotationDegrees((float)(a*180/Math.PI)));
            pose.scale(.7F,.7F,.7F);
            draw("earth_clod",pose,buffers,light,net.spell_engine.api.render.LightEmission.NONE);
            pose.popPose();
        }
        // Stone weapons jutting out of the rising column: the whole arsenal takes part in the lift.
        int weapons=Math.min(6,EarthWeaponModels.MODELS.size());
        for(int i=0;i<weapons;i++) {
            double a=i*Math.PI*2/weapons+.42;
            double y=Math.max(.7,top*(.22+.11*(i%3)));
            pose.pushPose();
            pose.translate(Math.cos(a)*w*1.05,y,Math.sin(a)*w*1.05);
            pose.mulPose(Axis.YP.rotationDegrees((float)(a*180/Math.PI)));
            pose.mulPose(Axis.XP.rotationDegrees(-58));
            pose.scale(w*1.5F,w*1.5F,w*1.5F);
            draw(EarthWeaponModels.MODELS.get(i%EarthWeaponModels.MODELS.size()),pose,buffers,light,net.spell_engine.api.render.LightEmission.NONE);
            pose.popPose();
        }
    }
    /** A giant stone weapon punching out of the ground where a rock spike would have been. */
    private void renderWeaponSpike(EarthAttackEntity e,float partial,PoseStack pose,MultiBufferSource buffers,int light) {
        double age=e.age(partial)-e.waitTicks();
        if(age<0)return;
        float grow=(float)EarthRuptureLayout.rise(Math.max(0,Math.min(1,age/8)));
        float w=e.scale(),h=(float)(e.height()*grow);
        pose.pushPose();
        pose.mulPose(Axis.YP.rotationDegrees(e.getYRot()));
        pose.mulPose(Axis.ZP.rotationDegrees(-12));
        // Weapon models are authored on +Z; -90 about X stands them up, then lift by half the
        // scaled length so the base sits on the ground instead of the middle being buried.
        pose.mulPose(Axis.XP.rotationDegrees(-90));
        pose.translate(0,0,h*.5F);
        pose.scale(w,w,h);
        draw(e.model(),pose,buffers,light,net.spell_engine.api.render.LightEmission.NONE);
        pose.popPose();
        if(age<16) {
            var out=buffers.getBuffer(WaterRenderTypes.geometry());
            double radius=Math.max(.3,age*.5);
            WaterGeometry.ringColor(out,pose.last().pose(),new net.minecraft.world.phys.Vec3(0,.06,0),
                    new net.minecraft.world.phys.Vec3(0,1,0),radius,.45,.58F,.42F,.24F,(float)((1-age/16)*.75),36);
        }
        for(int i=0;i<10;i++) {
            double a=i*Math.PI*2/10+.5;
            double t=Math.max(0,Math.min(1,age/14));
            double r=w*.9+t*2.6;
            double y=.15+t*(1.6+(i%3)*.5)-t*t*1.2;
            if(y<-.2)continue;
            pose.pushPose();
            pose.translate(Math.cos(a)*r,y,Math.sin(a)*r);
            pose.mulPose(Axis.YP.rotationDegrees((float)(a*180/Math.PI)));
            pose.mulPose(Axis.XP.rotationDegrees((float)(t*40)));
            pose.scale(.3F,.3F,.3F);
            draw("earth_clod",pose,buffers,light,net.spell_engine.api.render.LightEmission.NONE);
            pose.popPose();
        }
        drawChips(e,Math.max(0,age),pose,buffers,light);
    }
    /** Splinters thrown from the base of a spike as it punches out of the ground. */
    private void drawChips(EarthAttackEntity e,double age,PoseStack pose,MultiBufferSource buffers,int light) {
        var chips=spikeChips.computeIfAbsent(e,x->EarthRuptureLayout.ejecta(3,6,Math.max(.7,e.scale()*.55),false,.9));
        for(var chip:chips) {
            double local=age-chip.delay();
            if(local<0||local>EarthRuptureLayout.CHIP_TICKS)continue;
            double fade=1-local/EarthRuptureLayout.CHIP_TICKS;
            double r=chip.radius()+local*.06;
            double x=Math.cos(chip.angle())*r,z=Math.sin(chip.angle())*r;
            double y=.14+chip.lift()*local*.055-.012*local*local;
            double size=chip.size()*(.35+.65*fade);
            if(size<=.02)continue;
            pose.pushPose();pose.translate(x,y,z);
            pose.mulPose(Axis.YP.rotationDegrees((float)(chip.spin()+local*24)));
            pose.mulPose(Axis.XP.rotationDegrees((float)(local*33)));
            pose.scale((float)size,(float)size,(float)size);
            draw(e.model().startsWith("stone_lancer_")?"stone_lancer_earth_clod":"earth_clod",pose,buffers,light,
                    net.spell_engine.api.render.LightEmission.NONE);
            pose.popPose();
        }
    }
    private void renderSkySpear(EarthAttackEntity e,float partial,double age,PoseStack pose,MultiBufferSource buffers) {
        if(e.done()&&e.age(partial)-e.burstAge()>8)return;
        pose.pushPose();
        // The network entity stays near the target; the visible point is suspended above it.
        pose.translate(0,!e.done()&&age<=0?e.dropHeight():0,0);
        EarthSpearLayout.apply(pose,e.scale(),e.height());
        var mc=Minecraft.getInstance();
        var model=mc.getModelManager().getModel(ResourceLocation.fromNamespaceAndPath("tnc","projectile/"+(e.model().startsWith("stone_lancer_")?e.model():"earth_spear_sharp")));
        mc.getItemRenderer().renderModelLists(model,net.minecraft.world.item.ItemStack.EMPTY,LightTexture.FULL_BRIGHT,
                OverlayTexture.NO_OVERLAY,pose,buffers.getBuffer(RenderType.entityCutoutNoCull(net.minecraft.client.renderer.texture.TextureAtlas.LOCATION_BLOCKS)));
        pose.popPose();
    }
    private void renderBurst(EarthAttackEntity e,float partial,PoseStack pose,MultiBufferSource buffers,int light) {
        double elapsed=e.age(partial)-e.burstAge();if(elapsed<0||elapsed>e.burstLife())return;
        pose.pushPose();if(e.model().startsWith("stone_lancer_")){float size=e.gravityCore()?15:5;pose.scale(size,size,size);}
        boolean giant=e.mode()==2;
        float fade=(float)Math.max(0,1-elapsed/e.burstLife()),size=giant?.6F:.28F;
        if(giant) {
            var out=buffers.getBuffer(WaterRenderTypes.geometry());
            for(int ring=0;ring<2;ring++) {
                double radius=Math.max(.2,(elapsed-ring*2)*.95);
                WaterGeometry.ringColor(out,pose.last().pose(),new net.minecraft.world.phys.Vec3(0,.25+ring*.2,0),new net.minecraft.world.phys.Vec3(0,1,0),radius,1.2-ring*.35,.72F,.42F,.12F,fade*.8F,48);
            }
        } else if(elapsed<10) {
            // Every earth blast gets a ground shockwave ring, not only the giant one.
            var out=buffers.getBuffer(WaterRenderTypes.geometry());
            double radius=Math.max(.2,elapsed*.85);
            WaterGeometry.ringColor(out,pose.last().pose(),new net.minecraft.world.phys.Vec3(0,.15,0),new net.minecraft.world.phys.Vec3(0,1,0),radius,.3,.62F,.45F,.26F,fade*.65F,32);
        }
        drawBurstColumns(e,elapsed,giant,pose,buffers,light);
        int pieces=giant?42:18;
        for(int i=0;i<pieces;i++) {
            double a=i*Math.PI*2/pieces+i*.71,r=elapsed*((giant?.6:.32)+(i%5)*.055);
            double y=.2+elapsed*((giant?.30:.12)+(i%3)*.065)-elapsed*elapsed*.012;
            pose.pushPose();pose.translate(Math.cos(a)*r,y,Math.sin(a)*r);
            pose.mulPose(Axis.YP.rotationDegrees((float)(i*37+elapsed*13)));pose.scale(size*fade,size*fade,size*fade);
            draw((e.model().startsWith("stone_lancer_")?"stone_lancer_":"")+(i%3==0?"earth_shell":"earth_clod"),pose,buffers,light,net.spell_engine.api.render.LightEmission.NONE);pose.popPose();
        }
        pose.popPose();
    }
    /** A ring of small columns erupting around the detonation, then crumbling as it ends. */
    private void drawBurstColumns(EarthAttackEntity e,double elapsed,boolean giant,PoseStack pose,MultiBufferSource buffers,int light) {
        var shards=burstShards.computeIfAbsent(e,x->EarthRuptureLayout.burstColumns(giant?9.0:5.0,giant));
        double settle=Math.max(0,Math.min(1,(e.burstLife()-elapsed)/4.0));
        if(settle<=0)return;
        for(int i=0;i<shards.size();i++) {
            var shard=shards.get(i);
            double local=elapsed-shard.delay();
            if(local<0)continue;
            double t=Math.min(1,local/5.0);
            double rise=EarthRuptureLayout.rise(t);
            boolean weapon=!e.model().startsWith("stone_lancer_")&&i%3==1;
            double height=(weapon?(giant?3.4:2.6):(giant?1.7:.95))+shard.lift()*(giant?1.5:.7);
            double width=shard.size()*(weapon?(giant?2.1:1.7):(giant?1.35:1))*(.75+.25*settle);
            double lean=7+9*EarthRuptureLayout.impact(t);
            pose.pushPose();
            pose.translate(Math.cos(shard.angle())*shard.radius(),0,Math.sin(shard.angle())*shard.radius());
            pose.mulPose(Axis.YP.rotationDegrees((float)-Math.toDegrees(shard.angle())));
            pose.mulPose(Axis.ZP.rotationDegrees((float)-lean));
            pose.mulPose(Axis.YP.rotationDegrees((float)shard.spin()));
            String model=e.model().startsWith("stone_lancer_")?"stone_lancer_earth_spike_sharp"
                    :(weapon?EarthWeaponModels.MODELS.get(i%EarthWeaponModels.MODELS.size()):i%3==0?"earth_pillar":"earth_spike_sharp");
            if(weapon)pose.mulPose(Axis.XP.rotationDegrees(-90)); // weapons are authored on +Z; stand them up
            pose.scale((float)(width*1.0),(float)(height*rise*settle),(float)(width*1.0));
            pose.translate(0,.5,0);
            draw(model,pose,buffers,light,net.spell_engine.api.render.LightEmission.NONE);
            pose.popPose();
        }
    }
    private static void draw(String model,PoseStack pose,MultiBufferSource buffers,int light,net.spell_engine.api.render.LightEmission emission) {
        if(model.startsWith("stone_lancer_")){
            var mc=Minecraft.getInstance();
            // Match CustomModels' centered model origin for flight, spikes and debris.
            pose.translate(-.5,-.5,-.5);
            mc.getItemRenderer().renderModelLists(mc.getModelManager().getModel(ResourceLocation.fromNamespaceAndPath("tnc","projectile/"+model)),
                    net.minecraft.world.item.ItemStack.EMPTY,light,OverlayTexture.NO_OVERLAY,pose,
                    buffers.getBuffer(RenderType.entityCutoutNoCull(net.minecraft.client.renderer.texture.TextureAtlas.LOCATION_BLOCKS)));
        }else {
        net.spell_engine.api.render.CustomModels.render(net.spell_engine.client.render.SpellModelHelper.LAYERS.get(emission),
                Minecraft.getInstance().getItemRenderer(),ResourceLocation.fromNamespaceAndPath("tnc","projectile/"+model),pose,buffers,light,OverlayTexture.NO_OVERLAY);
        }
    }
}
