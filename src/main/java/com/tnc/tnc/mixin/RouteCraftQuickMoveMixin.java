package com.tnc.tnc.mixin;
import net.minecraft.world.inventory.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;
@Mixin(value=CraftingMenu.class,remap=false)
public abstract class RouteCraftQuickMoveMixin {
    @Inject(method={"quickMoveStack","m_7648_"},at=@At("HEAD"),cancellable=true,remap=false)
    private void tnc$verify(Player player,int slot,CallbackInfoReturnable<ItemStack> ci){if(slot==0&&player instanceof net.minecraft.server.level.ServerPlayer p&&!com.tnc.tnc.life.routes.RouteCraftRecipe.mayTake(p,((AbstractContainerMenu)(Object)this).slots.get(0).getItem()))ci.setReturnValue(ItemStack.EMPTY);}
}
