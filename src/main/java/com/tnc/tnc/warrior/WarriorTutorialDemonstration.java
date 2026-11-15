package com.tnc.tnc.warrior;

import com.tnc.tnc.network.MagicStoneNetwork;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.PacketDistributor;

/** A temporary instructor performs the choreography without invoking combat or progression. */
public final class WarriorTutorialDemonstration {
    private static final String ACTOR_ROLE = "tutorial_actor";
    private static final String DIRECTION_X = "TutorialDemoDirectionX";
    private static final String DIRECTION_Z = "TutorialDemoDirectionZ";

    private WarriorTutorialDemonstration() {}

    public static void tick(ServerPlayer learner, InstructorEntity tutor, Vec3 home, int age) {
        if (!valid(learner, tutor, home) || age < 0 || age > 130) return;
        tutor.setTutorialActor(true);
        tutor.setHolding(false);
        tutor.setDeltaMovement(Vec3.ZERO);
        tutor.fallDistance = 0;
        CompoundTag data = tutor.getPersistentData();
        if (age == 0 || !data.contains(DIRECTION_X)) {
            Vec3 away = home.subtract(learner.position()).multiply(1, 0, 1).normalize();
            if (away.lengthSqr() < .01) away = Vec3.directionFromRotation(0, tutor.getYRot()).scale(-1);
            data.putDouble(DIRECTION_X, away.x);
            data.putDouble(DIRECTION_Z, away.z);
        }
        Vec3 forward = new Vec3(data.getDouble(DIRECTION_X), 0, data.getDouble(DIRECTION_Z)).normalize();
        Vec3 side = new Vec3(-forward.z, 0, forward.x);
        Vec3 center = home.add(0,0,-3);

        if (age == 0) {
            tutor.playAnimation("tutorial_draw");
            learner.playNotifySound(SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, .55f, .8f);
        }
        // Nine crossings, spaced six ticks apart, stay within three blocks of the home mount.
        if (age >= 18 && age <= 66 && (age - 18) % 6 == 0) {
            int pass = (age - 18) / 6;
            double angle = pass * Math.PI + pass * Math.toRadians(40);
            Vec3 destination = center.add(forward.scale(Math.cos(angle) * 1.5))
                    .add(side.scale(Math.sin(angle) * 1.5));
            Vec3 from = tutor.position();
            if (clearPath(learner, tutor, from, destination)) {
                move(tutor, destination, destination.subtract(from));
                effect(learner, tutor, from, destination, 0);
                learner.playNotifySound(SoundEvents.TRIDENT_THROW, SoundSource.PLAYERS, .68f, 1.6f);
            }
            tutor.playAnimation("tutorial_thrust");
        }
        if (age == 90) {
            tutor.playAnimation("tutorial_sheath");
            learner.playNotifySound(SoundEvents.TRIDENT_RETURN, SoundSource.PLAYERS, .65f, 1.1f);
        }
        if (age == 108 && safe(learner, tutor, center)) {
            effect(learner, tutor, center, center, 1);
            learner.playNotifySound(SoundEvents.WARDEN_SONIC_BOOM, SoundSource.PLAYERS, .75f, 1.15f);
            learner.playNotifySound(SoundEvents.TRIDENT_THUNDER, SoundSource.PLAYERS, .45f, .95f);
        }
        if (age >= 120 && clearPath(learner, tutor, tutor.position(), home)) {
            move(tutor, home, learner.position().subtract(home));
        }
    }

    public static void combo(InstructorEntity tutor, int slot) {
        if (tutor == null || !tutor.isAlive() || tutor.level().isClientSide
                || !ACTOR_ROLE.equals(tutor.getPersistentData().getString(InstructorEntity.ROLE))
                || slot < 0 || slot > 2) return;
        tutor.setTutorialActor(true);
        tutor.setHolding(slot == 1);
        tutor.playAnimation(slot == 1 ? "block" : "attack");
    }

    private static boolean valid(ServerPlayer learner, InstructorEntity tutor, Vec3 home) {
        if (learner == null || tutor == null || home == null || !learner.isAlive() || !tutor.isAlive()
                || learner.level() != tutor.level() || !Double.isFinite(home.lengthSqr())) return false;
        CompoundTag data = tutor.getPersistentData();
        return ACTOR_ROLE.equals(data.getString(InstructorEntity.ROLE))
                && data.hasUUID(InstructorEntity.OWNER)
                && data.getUUID(InstructorEntity.OWNER).equals(learner.getUUID());
    }

    private static boolean clearPath(ServerPlayer learner, InstructorEntity tutor, Vec3 from, Vec3 to) {
        Vec3 delta = to.subtract(from);
        int steps = Math.max(1, (int) Math.ceil(delta.length() / .15));
        for (int step = 0; step <= steps; step++) {
            if (!safe(learner, tutor, from.lerp(to, step / (double) steps))) return false;
        }
        return true;
    }

    private static boolean safe(ServerPlayer learner, InstructorEntity tutor, Vec3 position) {
        var level = tutor.level();
        var bounds = tutor.getBoundingBox().move(position.subtract(tutor.position()));
        if (!level.getWorldBorder().isWithinBounds(bounds)
                || level.getBlockCollisions(tutor, bounds).iterator().hasNext()
                || bounds.inflate(.15).intersects(learner.getBoundingBox())
                || !level.getEntitiesOfClass(Player.class, bounds.inflate(.15), Player::isAlive).isEmpty()) return false;
        return level.clip(new ClipContext(position.add(0, .1, 0), position.add(0, -1.25, 0),
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, tutor)).getType() != HitResult.Type.MISS;
    }

    private static void move(InstructorEntity tutor, Vec3 to, Vec3 direction) {
        tutor.teleportTo(to.x, to.y, to.z);
        if (direction.horizontalDistanceSqr() < .0001) return;
        float yaw = (float) Math.toDegrees(Math.atan2(-direction.x, direction.z));
        tutor.setYRot(yaw);
        tutor.yBodyRot = yaw;
        tutor.setYHeadRot(yaw);
        tutor.setXRot(0);
    }

    private static void effect(ServerPlayer learner, InstructorEntity tutor, Vec3 from, Vec3 to, int kind) {
        MagicStoneNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> learner),
                new WarriorSwordFx(tutor.getId(), from, to, kind));
    }
}
