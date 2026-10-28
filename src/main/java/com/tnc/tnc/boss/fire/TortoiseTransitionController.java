package com.tnc.tnc.boss.fire;

import java.util.Map;
import software.bernie.geckolib.core.animatable.model.CoreGeoBone;
import software.bernie.geckolib.core.animatable.model.CoreGeoModel;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.AnimationState;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.core.object.PlayState;
import software.bernie.geckolib.core.state.BoneSnapshot;

/** Seeks the existing transition clip from synced game time, including late tracking/model swaps. */
final class TortoiseTransitionController extends AnimationController<VolcanicTortoiseEntity> {
    private static final RawAnimation CLIP = RawAnimation.begin().thenPlay("transition");
    private float partialTick;

    TortoiseTransitionController(VolcanicTortoiseEntity boss) {
        super(boss, "phase_transition", 0,
                state -> boss.isTransitioning() ? state.setAndContinue(CLIP) : PlayState.STOP);
    }

    @Override
    public void process(CoreGeoModel<VolcanicTortoiseEntity> model,
                        AnimationState<VolcanicTortoiseEntity> state,
                        Map<String, CoreGeoBone> bones, Map<String, BoneSnapshot> snapshots,
                        double seekTime, boolean crashIfBoneMissing) {
        this.partialTick = state.getPartialTick();
        super.process(model, state, bones, snapshots, seekTime, crashIfBoneMissing);
    }

    @Override
    protected double adjustTick(double tick) {
        double ordinary = super.adjustTick(tick);
        return this.animatable.isTransitioning()
                ? Math.min(TortoiseTransition.DURATION - 0.001D, this.animatable.transitionAge(this.partialTick))
                : ordinary;
    }
}
