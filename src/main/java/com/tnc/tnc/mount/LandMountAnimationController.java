package com.tnc.tnc.mount;

import java.util.EnumMap;
import java.util.Map;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.AnimationState;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.core.object.PlayState;

final class LandMountAnimationController extends AnimationController<LandMount> {
    private final Map<MountMotion.Gait, RawAnimation> ground = new EnumMap<>(MountMotion.Gait.class);
    private final Map<MountMotion.AirPose, RawAnimation> airborne = new EnumMap<>(MountMotion.AirPose.class);
    private final MountMotion.PlaybackClock clock = new MountMotion.PlaybackClock();
    private final MountMotion.Cadence cadence;
    private final RawAnimation swim;
    private double rate = 1;

    LandMountAnimationController(LandMount mount) {
        super(mount, "movement", 2, state -> ((LandMountAnimationController) state.getController()).select(state));
        String prefix = "animation." + mount.species() + ".";
        cadence = MountMotion.Cadence.forSpecies(mount.species());
        swim = mount.snow() ? RawAnimation.begin().thenLoop(prefix + "swim") : null;
        if (mount.species().equals("rainbow_unicorn")) {
            setSoundKeyframeHandler(event -> mount.playAnimatedHoofbeat());
        }
        for (MountMotion.Gait gait : MountMotion.Gait.values()) {
            ground.put(gait, RawAnimation.begin().thenLoop(prefix + gait.clip));
        }
        for (MountMotion.AirPose pose : MountMotion.AirPose.values()) {
            if (pose != MountMotion.AirPose.GROUND) {
                airborne.put(pose, RawAnimation.begin().thenPlayAndHold(prefix + pose.clip));
            }
        }
    }

    private PlayState select(AnimationState<LandMount> state) {
        LandMount mount = state.getAnimatable();
        if (swim != null && mount.visualSwimming()) {
            transitionLength(2);
            double ceiling = .55 + 1.7 * mount.swimSpeedFactor();
            rate = Math.max(.55, Math.min(ceiling, .55 + 1.7 * mount.visualSpeed()));
            return state.setAndContinue(swim);
        }
        MountMotion.AirPose pose = mount.visualAirPose();
        if (pose != MountMotion.AirPose.GROUND) {
            transitionLength(1);
            rate = switch (pose) {
                case TAKEOFF, LAND -> 1.5;
                default -> 1;
            };
            return state.setAndContinue(airborne.get(pose));
        }
        transitionLength(getCurrentRawAnimation() == airborne.get(MountMotion.AirPose.RUN_LAND) ? 1 : 2);
        MountMotion.Gait gait = mount.visualGait();
        rate = cadence.rate(gait, mount.visualSpeed());
        return state.setAndContinue(ground.get(gait));
    }

    @Override
    protected double adjustTick(double tick) {
        // GeckoLib still controls clip resets and transition timing. Only the running
        // clock is integrated, instead of rescaling every tick since the clip started.
        if (shouldResetTick || getAnimationState() != State.RUNNING) {
            clock.reset(tick);
            return super.adjustTick(tick);
        }
        return clock.advance(tick, rate);
    }
}
