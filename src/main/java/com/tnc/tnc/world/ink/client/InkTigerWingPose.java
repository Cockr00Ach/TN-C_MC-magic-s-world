package com.tnc.tnc.world.ink.client;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.cache.object.GeoBone;
/** Two rigged silhouettes: flank-folded on the ground, full-sized for spells. */
final class InkTigerWingPose {
    private InkTigerWingPose(){}
    static void apply(GeoModel<?> model,boolean folded){
        for(String side:new String[]{"l","r"}){
            hide((GeoBone)model.getAnimationProcessor().getBone("wing_"+side),folded);
            hide((GeoBone)model.getAnimationProcessor().getBone("folded_wing_"+side),!folded);
        }
    }
    private static void hide(GeoBone bone,boolean hidden){if(bone!=null){bone.setHidden(hidden);bone.setChildrenHidden(hidden);}}
}
