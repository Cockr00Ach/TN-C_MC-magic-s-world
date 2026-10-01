package com.tnc.tnc.mixin;
import net.minecraft.world.entity.player.Player;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Enforce on the server packet, including survival operators and direct packet requests. */
@Pseudo @Mixin(targets="net.conczin.immersive_furniture.network.c2s.CraftRequest",remap=false)
public abstract class TownFurnitureCraftMixin {
    @Inject(method="handle",at=@At("HEAD"),cancellable=true,remap=false,require=1)
    private void tnc$shopFurniture(Player player,CallbackInfo ci){if(!player.isCreative()){player.displayClientMessage(Component.literal("家具请到32号朝夕商行购买；创造模式供内饰制作。"),true);ci.cancel();}}
}
