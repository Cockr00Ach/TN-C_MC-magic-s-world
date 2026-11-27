package com.tnc.tnc.mount;

import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/** Distances are blocks per game tick; periods are game ticks. */
public final class MountMotion {
    static final double MAX_CONTIGUOUS_TRAVEL = 3;
    static final double MAX_CONTIGUOUS_TRAVEL_SQR = MAX_CONTIGUOUS_TRAVEL * MAX_CONTIGUOUS_TRAVEL;
    static final float NORMAL_FRICTION = .6f;
    static final double WOLF_MUZZLE_HEIGHT = 31.1 * .754 / 16;
    public static final int SWIM_CAPACITY = 360;
    public static final int SWIM_SLOW_THRESHOLD = 126;
    public static final int SWIM_WARNING_THRESHOLD = 54;
    static final int SWIM_RECOVERY_DELAY = 10;
    static final int SWIM_RECOVERY_PER_TICK = 2;

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

    static boolean swimming(double depth, boolean alreadySwimming, boolean hasFooting) {
        if (hasFooting) {
            return depth >= .65;
        }
        return depth >= (alreadySwimming ? .36 : .54);
    }

    static double swimSpeedFactor(int stamina) {
        if (stamina >= SWIM_SLOW_THRESHOLD) return .44;
        if (stamina >= SWIM_WARNING_THRESHOLD) {
            return .44 * Mth.lerp((stamina - SWIM_WARNING_THRESHOLD)
                    / (double) (SWIM_SLOW_THRESHOLD - SWIM_WARNING_THRESHOLD), .75, 1);
        }
        return stamina > 0 ? .44 * Mth.lerp(stamina / (double) SWIM_WARNING_THRESHOLD, .45, .75) : 0;
    }

    static boolean activelyPaddling(Vec3 movement, Vec3 current, Vec3 intent) {
        Vec3 direction = intent.normalize();
        double forwardTravel = movement.dot(direction);
        return forwardTravel > .012 && forwardTravel - current.dot(direction) > .012;
    }

    static int migrateSwimDistance(float usedDistance) {
        return Float.isFinite(usedDistance) ? Math.round(SWIM_CAPACITY * (1 - Mth.clamp(usedDistance / 48, 0, 1)))
                : SWIM_CAPACITY;
    }

    static Vec3 swimVelocity(Vec3 velocity, Vec3 intent, float riddenSpeed, double surfaceError,
                             int stamina, boolean clear, boolean climbing, boolean entering) {
        double speed = swimSpeedFactor(stamina) * riddenSpeed / (1 - .91 * NORMAL_FRICTION);
        // At a reachable ledge the wolf can pull itself out even with its swim reserve spent.
        if (climbing) speed = Math.max(speed, .14);
        if (surfaceError > .34 && !climbing) speed = 0;
        Vec3 target = intent.scale(speed / Math.max(1, intent.length()));
        double y = Mth.clamp(surfaceError * .28 + .01, -.045, .12);
        if (climbing) y = Math.max(.16, y);
        else if (!clear) y = Math.max(.08, y);
        double steering = entering ? .34 : .65;
        return new Vec3(Mth.lerp(steering, velocity.x, target.x),
                Mth.lerp(.58, velocity.y, y), Mth.lerp(steering, velocity.z, target.z));
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
        GROUND(""), TAKEOFF("takeoff"), RISE("rise"), FALL("fall"), LAND("land"), RUN_LAND("run_land");

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

        AirPose update(boolean grounded, boolean immersed, double verticalTravel, double horizontalTravel,
                       boolean riddenJump, boolean goat) {
            if (immersed) {
                unsupportedTicks = remainingTicks = 0;
                return pose = AirPose.GROUND;
            }
            if (riddenJump) {
                unsupportedTicks = 1;
                remainingTicks = goat ? 3 : 2;
                return pose = AirPose.TAKEOFF;
            }
            if (grounded) {
                unsupportedTicks = 0;
                if (pose == AirPose.TAKEOFF || pose == AirPose.RISE || pose == AirPose.FALL) {
                    boolean fast = horizontalTravel >= .24;
                    remainingTicks = fast ? (goat ? 4 : 3) : (goat ? 6 : 5);
                    return pose = fast ? AirPose.RUN_LAND : AirPose.LAND;
                }
                if ((pose == AirPose.LAND || pose == AirPose.RUN_LAND) && --remainingTicks > 0) {
                    return pose;
                }
                return pose = AirPose.GROUND;
            }

            unsupportedTicks++;
            if (pose == AirPose.TAKEOFF && --remainingTicks > 0 && verticalTravel >= -.04) {
                return pose;
            }
            if (unsupportedTicks == 1
                    && (pose == AirPose.GROUND || pose == AirPose.LAND || pose == AirPose.RUN_LAND)) {
                if (verticalTravel > .12) {
                    remainingTicks = goat ? 3 : 2;
                    return pose = AirPose.TAKEOFF;
                }
                // A brief gap at a slab edge is not a jump.
                if (verticalTravel > -.09) {
                    return pose;
                }
            }
            // Hold the approach to the apex and avoid alternating when packet displacements are near zero.
            if (pose == AirPose.RISE && verticalTravel >= -.025) {
                return pose;
            }
            return pose = verticalTravel > .045 ? AirPose.RISE : AirPose.FALL;
        }

        AirPose pose() {
            return pose;
        }
    }

    static final class RunningJump {
        private final Vec3[] steps = {Vec3.ZERO, Vec3.ZERO, Vec3.ZERO};
        private int nextStep;
        private Vec3 direction = Vec3.ZERO;
        private Vec3 previousVelocity;
        private int remaining;
        private boolean launching;
        private double target;
        private double retention;

        void recordGround(Vec3 displacement) {
            if (displacement.horizontalDistanceSqr() < .0256) {
                java.util.Arrays.fill(steps, Vec3.ZERO);
                return;
            }
            steps[nextStep] = new Vec3(displacement.x, 0, displacement.z);
            nextStep = (nextStep + 1) % steps.length;
        }

        boolean hasRunUp() {
            return steps[(nextStep + 2) % steps.length].horizontalDistanceSqr() >= .0256;
        }

        void start(String species, float progress, Vec3 intent) {
            stop();
            Vec3 heading = steps[(nextStep + 2) % steps.length].normalize();
            if (intent.normalize().dot(heading) < .70) {
                return;
            }
            double a = steps[0].dot(heading);
            double b = steps[1].dot(heading);
            double c = steps[2].dot(heading);
            double median = a + b + c - Math.min(a, Math.min(b, c)) - Math.max(a, Math.max(b, c));
            if (median < .16 || median > MAX_CONTIGUOUS_TRAVEL) {
                return;
            }
            direction = heading;
            launching = true;
            if (species.equals("rainbow_unicorn")) {
                target = median * Mth.lerp(progress, .92, .98);
                retention = Mth.lerp(progress, .965, .985);
                remaining = 8 + Math.round(6 * progress);
            } else if (species.equals("mountain_goat")) {
                target = median * .93;
                retention = .965;
                remaining = 8;
            } else {
                target = median * .94;
                retention = .970;
                remaining = 9;
            }
        }

        boolean active() {
            return remaining > 0;
        }

        Vec3 beforeMove(Vec3 velocity, Vec3 vanillaInput) {
            double deficit = target - velocity.add(vanillaInput).dot(direction);
            // On the launch tick vanilla can add its jump impulse and ground input together.
            // Limit their combined forward contribution without braking a pre-existing impulse.
            if (deficit > 0 || (launching && velocity.dot(direction) <= target)) {
                return velocity.add(direction.scale(deficit));
            }
            return velocity;
        }

        void afterMove(Vec3 displacement, boolean collision) {
            launching = false;
            double moved = displacement.dot(direction);
            if (collision || moved < .07 || moved > MAX_CONTIGUOUS_TRAVEL) {
                stop();
            } else {
                target = Math.min(target, moved) * retention;
                remaining--;
            }
        }

        boolean interruptedBy(Vec3 velocity) {
            return previousVelocity != null && velocity.subtract(previousVelocity).lengthSqr() > .0025;
        }

        void rememberVelocity(Vec3 velocity) {
            previousVelocity = active() ? velocity : null;
        }

        void stop() {
            remaining = 0;
            launching = false;
            previousVelocity = null;
        }

        void reset() {
            stop();
            recordGround(Vec3.ZERO);
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
