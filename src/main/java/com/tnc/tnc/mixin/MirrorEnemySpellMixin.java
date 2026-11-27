package com.tnc.tnc.mixin;

import com.tnc.tnc.dungeon.mirror.MirrorSpellHand;
import com.tnc.tnc.dungeon.mirror.MirrorSpells;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** The detached hand is mob spell damage, including on a server that has PvP disabled. */
@Mixin(ServerPlayer.class)
abstract class MirrorEnemySpellMixin {
    @Inject(method={"canHarmPlayer(Lnet/minecraft/world/entity/player/Player;)Z","m_7099_(Lnet/minecraft/world/entity/player/Player;)Z"},
            at=@At("HEAD"),cancellable=true,remap=false,require=1)
    private void mirrorHostileCast(Player other, CallbackInfoReturnable<Boolean> result) {
        if (other instanceof MirrorSpellHand hand)
            result.setReturnValue(MirrorSpells.enemy(hand, (ServerPlayer)(Object)this));
    }
}
