package com.tnc.tnc.mixin;

import com.tnc.tnc.skyacademy.client.SkyhavenVoxyWarmup;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.io.File;
import java.nio.file.Path;

@Pseudo
@Mixin(targets = "me.cortex.voxy.commonImpl.importers.WorldImporter", remap = false)
public abstract class SkyhavenVoxyImportMixin {
    @Unique private Path tnc$receipt;
    @Unique private volatile boolean tnc$cancelled;

    @Inject(method = "importRegionDirectoryAsync(Ljava/io/File;)V", at = @At("HEAD"), require = 0)
    private void tnc$bindImport(File directory, CallbackInfo ci) {
        tnc$receipt = SkyhavenVoxyWarmup.receiptFor(directory);
    }

    @Inject(method = "shutdown()V", at = @At("HEAD"), require = 0)
    private void tnc$cancel(CallbackInfo ci) {
        tnc$cancelled = true;
    }

    // Both a cancellation and a successful import call Voxy's normal completion callback.
    @Inject(method = "lambda$importRegionsAsync$6", at = @At("RETURN"), require = 0)
    private void tnc$finish(CallbackInfo ci) {
        if (tnc$receipt != null && !tnc$cancelled) SkyhavenVoxyWarmup.completed(tnc$receipt);
    }
}
