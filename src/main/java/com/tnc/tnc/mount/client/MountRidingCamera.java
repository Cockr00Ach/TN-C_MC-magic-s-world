package com.tnc.tnc.mount.client;

import com.tnc.tnc.mount.LandMount;
import com.tnc.tnc.mount.LandMounts;
import com.tnc.tnc.mixin.MountCameraInvoker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = "tnc", value = Dist.CLIENT)
public final class MountRidingCamera {
    private static final double MODEL_UNIT = .65f * LandMounts.SIZE_MULTIPLIER / 16;
    private static final double WALL_PADDING = .09;

    private MountRidingCamera() {}

    static boolean isActiveFor(LandMount mount) {
        var minecraft = Minecraft.getInstance();
        var player = minecraft.player;
        return player != null && player.isAlive() && player.getVehicle() == mount && mount.isAlive()
                && mount.getControllingPassenger() == player && minecraft.getCameraEntity() == player
                && minecraft.options.getCameraType().isFirstPerson();
    }

    @SubscribeEvent
    public static void attachToHead(ViewportEvent.ComputeCameraAngles event) {
        var camera = event.getCamera();
        if (camera.isDetached() || !(camera.getEntity() instanceof LocalPlayer rider)
                || !(rider.getVehicle() instanceof LandMount mount) || !isActiveFor(mount)
                || !((Object) camera instanceof MountCameraInvoker mover)) return;
        float partial = (float) event.getPartialTick();
        float yaw = Mth.rotLerp(partial, mount.yBodyRotO, mount.yBodyRot) * Mth.DEG_TO_RAD;
        Vec3 eye = switch (mount.species()) {
            case "rainbow_unicorn" -> new Vec3(0, 48.5, 22.4);
            case "mountain_goat" -> new Vec3(0, 40.4, 22);
            default -> new Vec3(0, 34.2, 23.1);
        };
        // A fixed point on the model avoids shaking from each keyframed head movement.
        Vec3 desired = mount.getPosition(partial).add(eye.scale(MODEL_UNIT).yRot(-yaw));
        Vec3 origin = camera.getPosition();
        Vec3 travel = desired.subtract(origin);
        double length = travel.length();
        if (length < .001) return;
        double safeDistance = length;
        for (int corner = 0; corner < 8; ++corner) {
            Vec3 offset = new Vec3((corner & 1) == 0 ? -WALL_PADDING : WALL_PADDING,
                    (corner & 2) == 0 ? -WALL_PADDING : WALL_PADDING,
                    (corner & 4) == 0 ? -WALL_PADDING : WALL_PADDING);
            Vec3 start = origin.add(offset);
            var hit = mount.level().clip(new ClipContext(start, desired.add(offset),
                    ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, camera.getEntity()));
            if (hit.getType() == HitResult.Type.BLOCK) {
                double distance = hit.getLocation().distanceTo(start);
                safeDistance = Math.min(safeDistance, Math.max(0, distance - WALL_PADDING));
            }
        }
        mover.tnc$setMountedPosition(origin.add(travel.scale(safeDistance / length)));
    }
}
