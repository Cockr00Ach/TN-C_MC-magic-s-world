package com.tnc.tnc.mount;

import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/** Distances are blocks per game tick; periods are game ticks. */
public final class MountMotion {
    static final double MAX_CONTIGUOUS_TRAVEL = 3;
    static final double MAX_CONTIGUOUS_TRAVEL_SQR = MAX_CONTIGUOUS_TRAVEL * MAX_CONTIGUOUS_TRAVEL;
    static final float NORMAL_FRICTION = .6f;
    static final double SWIM_SPEED_FRACTION = .40;

    static float steeredYaw(float yaw, float desired, float runProgress) {
        float limit = Mth.lerp(runProgress, 12f, 6f);
        return yaw + Mth.clamp(Mth.wrapDegrees(desired - yaw), -limit, limit);
    }

    static float iceInputCompensation(float friction) {
        float ratio = friction / NORMAL_FRICTION;
        return ratio * ratio * ratio;
    }

    static Vec3 iceSteer(Vec3 velocity, Vec3 intent) {
        Vec3 direction = new Vec3(intent.x, 0, intent.z).normalize();
        double forward = direction.dot(velocity);
        double sideCarry = .36;
        double along = forward * (forward < 0 ? .55 : 1);
        return new Vec3(velocity.x * sideCarry + direction.x * (along - sideCarry * forward),
                velocity.y, velocity.z * sideCarry + direction.z * (along - sideCarry * forward));
    }

    static boolean swimming(double depth, boolean alreadySwimming) {
        return depth >= (alreadySwimming ? .36 : .54);
    }

    static Vec3 swimVelocity(Vec3 velocity, Vec3 intent, float riddenSpeed, double depth, boolean assistHorizontal) {
        double rate = SWIM_SPEED_FRACTION * riddenSpeed / (1 - .91 * NORMAL_FRICTION);
        Vec3 target = intent.scale(rate / Math.max(1, intent.length()));
        double y = Mth.clamp((depth - .72) * .22, -.06, .105);
        return new Vec3(
                assistHorizontal ? Mth.lerp(.68, velocity.x, target.x) : velocity.x,
                Mth.lerp(.58, velocity.y, y),
                assistHorizontal ? Mth.lerp(.68, velocity.z, target.z) : velocity.z);
    }

    enum Gait {
        IDLE("idle"), WALK("walk"), TROT("trot"), GALLOP("gallop");

        final String clip;

        Gait(String clip) {
            this.clip = clip;
        }
    }

    /** Ground-contact distance measured from the exported foot tracks in world units. */
    record Timing(double distancePerCycle, int clipTicks, int minimumCycleTicks) {
        double rate(double groundSpeed) {
            double matchedRate = groundSpeed * clipTicks / distancePerCycle;
            return Math.max(.35, Math.min((double) clipTicks / minimumCycleTicks, matchedRate));
        }

        double maxReadableSpeed() {
            return distancePerCycle / minimumCycleTicks;
        }
    }

    record Cadence(Timing walk, Timing trot, Timing gallop) {
        private static final Cadence UNICORN = new Cadence(
                new Timing(0.51409, 28, 12),
                new Timing(0.92609, 16, 8),
                new Timing(2.08398, 10, 7));
        private static final Cadence GOAT = new Cadence(
                new Timing(0.42344, 28, 12),
                new Timing(0.74602, 16, 8),
                new Timing(1.51321, 8, 6));
        private static final Cadence WOLF = new Cadence(
                new Timing(0.48553, 28, 12),
                new Timing(0.78889, 16, 8),
                new Timing(1.67453, 8, 6));

        static Cadence forSpecies(String species) {
            return switch (species) {
                case "mountain_goat" -> GOAT;
                case "snow_wolf" -> WOLF;
                default -> UNICORN;
            };
        }

        double rate(Gait gait, double groundSpeed) {
            return switch (gait) {
                case WALK -> walk.rate(groundSpeed);
                case TROT -> trot.rate(groundSpeed);
                case GALLOP -> gallop.rate(groundSpeed);
                case IDLE -> 1;
            };
        }
    }

    static final class GroundMotion {
        private Gait gait = Gait.IDLE;
        private Gait pending = Gait.IDLE;
        private double speed;
        private int samples;

        void update(double displacement, Cadence cadence) {
            if (displacement > MAX_CONTIGUOUS_TRAVEL) {
                gait = pending = Gait.IDLE;
                speed = 0;
                samples = 0;
                return;
            }
            speed += (displacement - speed) * (displacement < speed ? .6 : .45);
            double walk = gait == Gait.IDLE ? .014 : .009;
            double trot = cadence.walk.maxReadableSpeed() * (gait.ordinal() >= Gait.TROT.ordinal() ? .76 : 1);
            double gallop = cadence.trot.maxReadableSpeed() * (gait == Gait.GALLOP ? .76 : 1);
            Gait next = speed >= gallop ? Gait.GALLOP : speed >= trot ? Gait.TROT
                    : speed >= walk ? Gait.WALK : Gait.IDLE;
            if (next == gait) {
                pending = next;
                samples = 0;
                return;
            }
            samples = next == pending ? samples + 1 : 1;
            pending = next;
            if (samples >= 3) {
                gait = pending;
                samples = 0;
            }
        }

        Gait gait() {
            return gait;
        }

        double speed() {
            return speed;
        }
    }

    enum AirPose {
        GROUND(""), TAKEOFF("takeoff"), RISE("rise"), FALL("fall"), LAND("land");

        private static final AirPose[] WIRE_VALUES = values();
        final String clip;

        AirPose(String clip) {
            this.clip = clip;
        }

        static AirPose fromWire(int id) {
            return id >= 0 && id < WIRE_VALUES.length ? WIRE_VALUES[id] : GROUND;
        }
    }

    static final class AirMotion {
        private AirPose pose = AirPose.GROUND;
        private int remainingTicks;
        private int unsupportedTicks;

        AirPose update(boolean grounded, boolean immersed, double verticalTravel,
                       boolean riddenJump, boolean goat) {
            if (immersed) {
                unsupportedTicks = remainingTicks = 0;
                return pose = AirPose.GROUND;
            }
            if (riddenJump) {
                unsupportedTicks = 1;
                remainingTicks = goat ? 4 : 3;
                return pose = AirPose.TAKEOFF;
            }
            if (grounded) {
                unsupportedTicks = 0;
                if (pose == AirPose.TAKEOFF || pose == AirPose.RISE || pose == AirPose.FALL) {
                    remainingTicks = goat ? 7 : 6; // Includes the one-tick blend into landing.
                    return pose = AirPose.LAND;
                }
                if (pose == AirPose.LAND && --remainingTicks > 0) {
                    return pose;
                }
                return pose = AirPose.GROUND;
            }

            unsupportedTicks++;
            if (pose == AirPose.TAKEOFF && --remainingTicks > 0 && verticalTravel >= -.04) {
                return pose;
            }
            if (unsupportedTicks == 1 && (pose == AirPose.GROUND || pose == AirPose.LAND)) {
                if (verticalTravel > .12) {
                    remainingTicks = goat ? 4 : 3;
                    return pose = AirPose.TAKEOFF;
                }
                // A brief gap at a slab edge is not a jump.
                if (verticalTravel > -.09) {
                    return pose;
                }
            }
            return pose = verticalTravel > .02 ? AirPose.RISE : AirPose.FALL;
        }

        AirPose pose() {
            return pose;
        }
    }

    /** Integrate successive frame deltas so rate changes cannot rewind the current stride. */
    static final class PlaybackClock {
        private double previousTick;
        private double elapsed;

        void reset(double tick) {
            previousTick = tick;
            elapsed = 0;
        }

        double advance(double tick, double rate) {
            if (tick < previousTick) {
                reset(tick);
                return 0;
            }
            elapsed += (tick - previousTick) * rate;
            previousTick = tick;
            return elapsed;
        }

    }
    private static final int UNICORN_ACCELERATION_TICKS = 60;

    static int advanceUnicornRun(int ticks, boolean forwardInput, boolean movedForward) {
        return forwardInput && movedForward ? Math.min(ticks + 1, UNICORN_ACCELERATION_TICKS) : 0;
    }

    static float unicornSpeedMultiplier(int ticks) {
        return 1.5f + unicornRunProgress(ticks);
    }

    static float unicornRunProgress(int ticks) {
        return Math.min(ticks, UNICORN_ACCELERATION_TICKS) / (float) UNICORN_ACCELERATION_TICKS;
    }

    static boolean isUnicornRunAtCap(int ticks) {
        return ticks == UNICORN_ACCELERATION_TICKS;
    }

    static int reconcileUnicornRun(int prediction, int serverTicks) {
        if (prediction == 0 || serverTicks == 0) {
            return 0;
        }
        int difference = serverTicks - prediction;
        // A packet covers several ticks, so small client leads belong to normal prediction.
        return Math.abs(difference) > 6 ? prediction + Integer.signum(difference) * 3 : prediction;
    }

    private MountMotion(){}
}
