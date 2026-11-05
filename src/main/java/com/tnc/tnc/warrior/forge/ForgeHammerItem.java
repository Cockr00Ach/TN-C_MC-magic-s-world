package com.tnc.tnc.warrior.forge;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.*;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

/** Vanilla held-use packets supply server-measured pressure duration, never client scores. */
public final class ForgeHammerItem extends Item {
    private static final String HOLD = "TncForgeHold";
    public ForgeHammerItem() { super(new Properties().stacksTo(1)); }
    @Override public int getUseDuration(ItemStack stack) { return 72000; }
    @Override public UseAnim getUseAnimation(ItemStack stack) { return UseAnim.NONE; }
    @Override public InteractionResult useOn(UseOnContext c) {
        if (!(c.getLevel().getBlockEntity(c.getClickedPos()) instanceof WarriorForgeEntity be)) return InteractionResult.PASS;
        if (c.getHand() != InteractionHand.MAIN_HAND) return InteractionResult.FAIL;
        if(c.getPlayer() instanceof ServerPlayer p && p.isShiftKeyDown()) {
            // Sneaking bypasses block.use in vanilla; keep pose/correction accessible here too.
            be.interact(p,new net.minecraft.world.phys.BlockHitResult(c.getClickLocation(),c.getClickedFace(),c.getClickedPos(),false));
            return InteractionResult.CONSUME;
        }
        if (c.getLevel().isClientSide && c.getPlayer() != null && !c.getPlayer().isShiftKeyDown()
                && be.session != null && be.kind() != WarriorForgeBlock.Kind.HEARTH) c.getPlayer().startUsingItem(c.getHand());
        if (c.getPlayer() instanceof ServerPlayer p && be.beginHold(p)) {
            var t = new net.minecraft.nbt.CompoundTag();
            t.putLong("Pos", c.getClickedPos().asLong()); t.putString("Dimension", c.getLevel().dimension().location().toString());
            p.getPersistentData().put(HOLD, t); p.startUsingItem(c.getHand());
        }
        return InteractionResult.sidedSuccess(c.getLevel().isClientSide);
    }
    @Override public void onUseTick(Level l, LivingEntity entity, ItemStack stack, int remaining) {
        if (!(entity instanceof ServerPlayer p)) return;
        var be = target(p);
        if (be == null || !be.mayUse(p) || p.distanceToSqr(net.minecraft.world.phys.Vec3.atCenterOf(be.getBlockPos())) > 36
                || !(p.pick(5,1,false) instanceof net.minecraft.world.phys.BlockHitResult hit) || !hit.getBlockPos().equals(be.getBlockPos())) {
            p.getPersistentData().remove(HOLD); p.stopUsingItem();
        }
    }
    @Override public void releaseUsing(ItemStack stack, Level l, LivingEntity entity, int remaining) {
        if (!(entity instanceof ServerPlayer p)) return;
        var be = target(p); p.getPersistentData().remove(HOLD);
        if (be != null) be.release(p, Math.min(72000, getUseDuration(stack) - remaining));
    }
    private static WarriorForgeEntity target(ServerPlayer p) {
        var t = p.getPersistentData().getCompound(HOLD);
        if (!t.contains("Pos") || !t.getString("Dimension").equals(p.level().dimension().location().toString())) return null;
        var pos = BlockPos.of(t.getLong("Pos"));
        return p.serverLevel().hasChunkAt(pos) && p.serverLevel().getBlockEntity(pos) instanceof WarriorForgeEntity be ? be : null;
    }
}
