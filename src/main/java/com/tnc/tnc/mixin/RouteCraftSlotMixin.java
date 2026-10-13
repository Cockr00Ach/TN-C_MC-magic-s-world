package com.tnc.tnc.mixin;
import net.minecraft.world.inventory.ResultSlot;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;
import com.tnc.tnc.life.routes.RouteCraftRecipe;
@Mixin(value=ResultSlot.class,remap=false)
public abstract class RouteCraftSlotMixin {
    @org.spongepowered.asm.mixin.Shadow(aliases={"f_40162_"}) @org.spongepowered.asm.mixin.Final private net.minecraft.world.inventory.CraftingContainer craftSlots;
    @org.spongepowered.asm.mixin.Unique private java.util.List<ItemStack> tnc$paidContainers=java.util.List.of();
    @Inject(method={"onTake","m_142406_"},at=@At("HEAD"),remap=false)
    private void tnc$remember(Player player,ItemStack result,CallbackInfo ci){tnc$paidContainers=RouteCraftRecipe.inputPayment(craftSlots);}
    @Inject(method={"onTake","m_142406_"},at=@At("TAIL"),remap=false)
    private void tnc$debit(Player player,ItemStack result,CallbackInfo ci){if(tnc$paidContainers.isEmpty())return;if(player instanceof net.minecraft.server.level.ServerPlayer p){for(var bottle:tnc$paidContainers){int got=com.tnc.tnc.life.pasture.PastureBottleLedger.get(p.serverLevel()).withdraw(bottle,100);if(got!=100)throw new IllegalStateException("Unverified mana crafting transaction");}tnc$paidContainers=java.util.List.of();tnc$clearMarker(result);tnc$clearMarker(p.containerMenu.getCarried());for(var stack:p.getInventory().items)tnc$clearMarker(stack);player.containerMenu.slotsChanged(craftSlots);}}
    @org.spongepowered.asm.mixin.Unique private static void tnc$clearMarker(ItemStack stack){if(stack.hasTag()){stack.getTag().remove("ManaCraftAccounts");if(stack.getTag().isEmpty())stack.setTag(null);}}
}
