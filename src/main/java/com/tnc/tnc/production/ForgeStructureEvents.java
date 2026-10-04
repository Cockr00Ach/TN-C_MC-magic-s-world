package com.tnc.tnc.production;

import com.tnc.tnc.TNMod;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Mark nearby cores dirty when a player fills the cavity or changes any structural part. */
@Mod.EventBusSubscriber(modid = TNMod.MODID)
public final class ForgeStructureEvents {
    private ForgeStructureEvents() {}

    @SubscribeEvent public static void placed(BlockEvent.EntityPlaceEvent event) {
        if (event.getLevel() instanceof Level level && !level.isClientSide) invalidateNear(level, event.getPos());
    }

    @SubscribeEvent public static void broken(BlockEvent.BreakEvent event) {
        if (event.getLevel() instanceof Level level && !level.isClientSide) invalidateNear(level, event.getPos());
    }

    static void invalidateNear(Level level, BlockPos changed) {
        for (BlockPos probe : BlockPos.betweenClosed(changed.offset(-2, -1, -2), changed.offset(2, 1, 2))) {
            if (level.hasChunkAt(probe) && level.getBlockEntity(probe) instanceof MagicForgeBlockEntity forge)
                forge.invalidateStructure();
        }
    }
}
