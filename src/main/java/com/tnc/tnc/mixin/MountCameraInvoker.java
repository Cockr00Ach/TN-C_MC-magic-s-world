package com.tnc.tnc.mixin;

import net.minecraft.client.Camera;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(Camera.class)
public interface MountCameraInvoker {
    @Invoker("setPosition")
    void tnc$setMountedPosition(Vec3 position);
}
