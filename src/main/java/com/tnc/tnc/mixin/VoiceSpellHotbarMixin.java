package com.tnc.tnc.mixin;

import com.tnc.tnc.magic.free.client.EquippedSpellVoiceClient;
import net.spell_engine.config.ClientConfig;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Spoken casts own their windup; physical hotkeys retain the user's normal hold settings. */
@Pseudo
@Mixin(targets="net.spell_engine.client.input.SpellHotbar",remap=false)
public class VoiceSpellHotbarMixin {
    @Redirect(method="handle(Lnet/minecraft/client/player/LocalPlayer;Ljava/util/List;Lnet/minecraft/client/Options;)Lnet/spell_engine/client/input/SpellHotbar$Handle;",
            at=@At(value="FIELD",target="Lnet/spell_engine/config/ClientConfig;holdToCastCharged:Z"),remap=false,require=1)
    private boolean tnc$voiceCharged(ClientConfig config){return config.holdToCastCharged&&!EquippedSpellVoiceClient.ownsCurrentProcess();}
    @Redirect(method="handle(Lnet/minecraft/client/player/LocalPlayer;Ljava/util/List;Lnet/minecraft/client/Options;)Lnet/spell_engine/client/input/SpellHotbar$Handle;",
            at=@At(value="FIELD",target="Lnet/spell_engine/config/ClientConfig;holdToCastChannelled:Z"),remap=false,require=1)
    private boolean tnc$voiceChannelled(ClientConfig config){return config.holdToCastChannelled&&!EquippedSpellVoiceClient.ownsCurrentProcess();}
}
