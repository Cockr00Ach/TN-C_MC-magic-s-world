package com.tnc.tnc.life.botanical;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.level.ClipContext;
/** Lily-style placement puts the plant above shallow water, rather than replacing water. */
public final class LotusSeedItem extends BlockItem {
    public LotusSeedItem(Block b){super(b,new Item.Properties());}
    @Override public net.minecraft.world.InteractionResult useOn(net.minecraft.world.item.context.UseOnContext c){return net.minecraft.world.InteractionResult.PASS;}
    @Override public InteractionResultHolder<ItemStack> use(Level l,Player p,InteractionHand hand){var hit=getPlayerPOVHitResult(l,p,ClipContext.Fluid.SOURCE_ONLY);if(hit.getType()!=HitResult.Type.BLOCK)return InteractionResultHolder.pass(p.getItemInHand(hand));BlockPos target=hit.getBlockPos().above();if(p instanceof net.minecraft.server.level.ServerPlayer sp&&com.tnc.tnc.home.TownProtection.denied(sp,target))return InteractionResultHolder.fail(p.getItemInHand(hand));var result=super.useOn(new net.minecraft.world.item.context.UseOnContext(p,hand,hit.withPosition(target)));return new InteractionResultHolder<>(result,p.getItemInHand(hand));}
}
