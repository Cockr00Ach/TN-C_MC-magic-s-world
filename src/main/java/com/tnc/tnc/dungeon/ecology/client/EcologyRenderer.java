package com.tnc.tnc.dungeon.ecology.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.tnc.tnc.dungeon.ecology.*;
import com.tnc.tnc.mana.sight.client.ManaSightClient;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.entity.*;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.resources.ResourceLocation;

/** Registration shares this renderer, while all four skeletons and animations are separate. */
public final class EcologyRenderer extends MobRenderer<EcologyMob,EcologyModel> {
    public static ModelLayerLocation layer(String kind){return new ModelLayerLocation(EcologyContent.id("ecology_"+kind),"main");}
    public EcologyRenderer(EntityRendererProvider.Context context,String kind){
        super(context,switch(kind){case "siphon"->new SiphonJellyModel(context.bakeLayer(layer(kind)));case "flash"->new GlareGargoyleModel(context.bakeLayer(layer(kind)));case "worm"->new SourceGrubModel(context.bakeLayer(layer(kind)));default->new CrystalCrabModel(context.bakeLayer(layer(kind)));},kind.equals("siphon")?0:kind.equals("worm")?.55f:.4f);
        addLayer(new Core(this));
    }
    @Override public ResourceLocation getTextureLocation(EcologyMob mob){return EcologyContent.id("textures/entity/dungeon_ecology/"+mob.ecologyKind()+".png");}
    @Override public void render(EcologyMob mob,float yaw,float partial,PoseStack pose,MultiBufferSource buffers,int light){if(mob instanceof ManaSiphonJelly jelly&&!EcologyClient.visibleSiphon(jelly))return;super.render(mob,yaw,partial,pose,buffers,light);}
    @Override protected void scale(EcologyMob mob,PoseStack pose,float partial){float scale=mob.captureWindow()?.3f:1;if(mob.guardian())scale*=1.8f;pose.scale(scale,scale,scale);}
    @Override protected RenderType getRenderType(EcologyMob mob,boolean body,boolean invisible,boolean glowing){return body&&(mob instanceof ManaSiphonJelly||mob instanceof SourceCrystalGrub)?RenderType.entityTranslucent(getTextureLocation(mob)):super.getRenderType(mob,body,invisible,glowing);}
    private static final class Core extends RenderLayer<EcologyMob,EcologyModel> {
        private final EcologyRenderer renderer;
        Core(EcologyRenderer renderer){super(renderer);this.renderer=renderer;}
        @Override public void render(PoseStack pose,MultiBufferSource buffers,int light,EcologyMob mob,float walk,float amount,float partial,float age,float yaw,float pitch){
            if(!ManaSightClient.enabled()&&!(mob instanceof GlareGargoyle&&mob.action()>0))return;
            int color=switch(mob.supplyState()){case CUT->0x586170;case TAINTED->0xbb62e5;case OVERLOAD->0xff2348;default->mob.species().accent();};
            float strength=mob.supplyState()==SupplyState.CUT?.35f:.85f+(float)Math.sin(age*.17)*.15f;
            getParentModel().renderCore(pose,buffers.getBuffer(EcologyRenderTypes.core(renderer.getTextureLocation(mob))),(color>>16&255)/255f,(color>>8&255)/255f,(color&255)/255f,strength);
        }
    }
}
