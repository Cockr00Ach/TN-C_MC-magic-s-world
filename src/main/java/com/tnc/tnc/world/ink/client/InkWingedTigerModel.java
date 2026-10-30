package com.tnc.tnc.world.ink.client;
import com.tnc.tnc.world.ink.InkWingedTigerEntity;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.core.animation.AnimationState;
import software.bernie.geckolib.constant.DataTickets;
import software.bernie.geckolib.cache.object.GeoBone;
public final class InkWingedTigerModel extends GeoModel<InkWingedTigerEntity>{
    private static ResourceLocation asset(String s){return ResourceLocation.fromNamespaceAndPath("tnc",s);}
    @Override public ResourceLocation getModelResource(InkWingedTigerEntity e){return asset("geo/entity/ink_winged_tiger.geo.json");}
    @Override public ResourceLocation getTextureResource(InkWingedTigerEntity e){return asset("textures/entity/ink_winged_tiger.png");}
    @Override public ResourceLocation getAnimationResource(InkWingedTigerEntity e){return asset("animations/entity/ink_winged_tiger.animation.json");}
    @Override public void setCustomAnimations(InkWingedTigerEntity e,long id,AnimationState<InkWingedTigerEntity> state){
        super.setCustomAnimations(e,id,state);
        InkTigerWingPose.apply(this,e.inkMove()==0||e.inkMove()==4);
        if(e.inkMove()!=0)return;
        var data=state.getData(DataTickets.ENTITY_MODEL_DATA);GeoBone head=(GeoBone)getAnimationProcessor().getBone("head");
        if(head!=null&&data!=null){head.setRotY(Math.max(-1.35F,Math.min(1.35F,data.netHeadYaw()*.0174533F)));
            head.setRotX(Math.max(-.35F,Math.min(.35F,data.headPitch()*.0174533F)));}
    }
}
