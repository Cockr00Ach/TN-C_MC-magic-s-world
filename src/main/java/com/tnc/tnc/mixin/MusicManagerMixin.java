package com.tnc.tnc.mixin;

import com.tnc.tnc.client.TravelMusicController;
import net.minecraft.client.sounds.MusicManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Prevents vanilla situational music while the local travel playlist is available. */
@Mixin(MusicManager.class)
public abstract class MusicManagerMixin {
    @Inject(method = "tick", at = @At("HEAD"), cancellable = true)
    private void tnc$suppressVanillaMusic(CallbackInfo callback) {
        if (TravelMusicController.shouldSuppressVanillaMusic()) {
            callback.cancel();
        }
    }
}
