package com.tnc.tnc.production;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;

/**
 * A nearby living plant may supply stored mana to a generator. The implementation
 * must debit persistent plant state before returning the actual amount delivered.
 * It may return zero for immature, shaded, dry, or exhausted plants.
 */
public interface ManaPlantSource {
    int drawMana(ServerLevel level, BlockPos pos, BlockState state, int maxMana);
}
