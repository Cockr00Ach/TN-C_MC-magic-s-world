package com.tnc.tnc.production.energy;

import com.tnc.tnc.home.HousingService;
import com.tnc.tnc.home.TownProtection;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.UUID;

/** Ownership checks also run for automatic transfers, where there is no clicking player. */
final class EnergyPermissions {
    private EnergyPermissions() {}

    static UUID ownerForPlacement(ServerPlayer player, BlockPos pos) {
        var plot = HousingService.ownedAt(player.serverLevel(), pos);
        if (plot != null) {
            var saved = HousingService.home(player.server, plot.id());
            if (saved.hasUUID("Owner")) return saved.getUUID("Owner");
        }
        return player.getUUID();
    }

    static boolean mayOperate(ServerLevel level, BlockPos pos, UUID owner) {
        if (owner == null) return false;
        var plot = HousingService.ownedAt(level, pos);
        if (plot != null) {
            var saved = HousingService.home(level.getServer(), plot.id());
            return saved.hasUUID("Owner") && !saved.getBoolean("Preparing")
                    && saved.getUUID("Owner").equals(owner);
        }
        // A pending plot and all other parts of the protected town are off limits.
        return HousingService.plotAt(level, pos) == null && !TownProtection.town(level, pos);
    }

    static boolean mayDrawPlant(ServerLevel level, BlockPos pos, UUID machineOwner) {
        if (!mayOperate(level, pos, machineOwner)) return false;
        var source = level.getBlockEntity(pos);
        return source instanceof OwnedManaPlant owned && machineOwner.equals(owned.manaOwner());
    }

    static boolean mayExposeTo(ServerLevel level, BlockPos source, Direction side, UUID owner) {
        BlockPos neighbor = source.relative(side);
        if (!level.hasChunkAt(neighbor) || !mayOperate(level, source, owner)
                || !mayOperate(level, neighbor, owner)) return false;
        var sourcePlot = HousingService.ownedAt(level, source);
        var neighborPlot = HousingService.ownedAt(level, neighbor);
        if ((sourcePlot == null) != (neighborPlot == null)) return false;
        if (sourcePlot != null && !sourcePlot.id().equals(neighborPlot.id())) return false;
        var adjacent = level.getBlockEntity(neighbor);
        return !(adjacent instanceof EnergyBlockEntity node)
                || owner.equals(node.owner());
    }
}
