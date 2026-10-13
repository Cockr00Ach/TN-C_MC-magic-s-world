package com.tnc.tnc.mixin;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;
import com.tnc.tnc.life.routes.RouteCraftRecipe;
@Mixin(value=Slot.class,remap=false)
public abstract class RouteCraftPickupMixin {
    @Inject(method={"mayPickup","m_8010_"},at=@At("HEAD"),cancellable=true,remap=false)
    private void tnc$verify(Player player,CallbackInfoReturnable<Boolean> ci){if(player instanceof net.minecraft.server.level.ServerPlayer p&&!RouteCraftRecipe.mayTake(p,((Slot)(Object)this).getItem()))ci.setReturnValue(false);}
}
