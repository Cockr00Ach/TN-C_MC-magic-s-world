package com.tnc.tnc.light;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.UseOnContext;

/** Explicit tiered player entry point; no existing light spell is silently replaced. */
public final class LightMirrorItem extends Item {
    private final int tier;
    public LightMirrorItem(int tier) { super(new Properties().stacksTo(16).rarity(Rarity.RARE)); this.tier = tier; }
    @Override public InteractionResult useOn(UseOnContext context) {
        if (context.getLevel().isClientSide) return InteractionResult.SUCCESS;
        if (!(context.getPlayer() instanceof ServerPlayer player)) return InteractionResult.PASS;
        var pos = context.getClickedPos().relative(context.getClickedFace());
        if (!player.mayUseItemAt(pos, context.getClickedFace(), context.getItemInHand())) return InteractionResult.FAIL;
        var mirror = LightMirrorRegistration.MIRROR.create(player.serverLevel());
        if (mirror == null) return InteractionResult.FAIL;
        mirror.configure(player, tier);
        mirror.moveTo(pos.getX() + .5, pos.getY(), pos.getZ() + .5, player.getYRot(), 0);
        if (!player.level().getWorldBorder().isWithinBounds(mirror.getBoundingBox())
                || !player.level().noCollision(mirror, mirror.getBoundingBox())) return InteractionResult.FAIL;
        var owned = player.serverLevel().getEntitiesOfClass(TNLightMirrorEntity.class, player.getBoundingBox().inflate(160), m -> m.belongsTo(player));
        if (owned.size() >= 8) return InteractionResult.FAIL;
        if (!player.serverLevel().addFreshEntity(mirror)) return InteractionResult.FAIL;
        player.getCooldowns().addCooldown(this, 10);
        if (!player.isCreative()) context.getItemInHand().shrink(1);
        return InteractionResult.CONSUME;
    }
}
