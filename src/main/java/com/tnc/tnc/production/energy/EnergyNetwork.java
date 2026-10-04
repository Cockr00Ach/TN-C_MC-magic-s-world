package com.tnc.tnc.production.energy;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.server.level.ServerLevel;
import org.joml.Vector3f;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Short, loaded-only owner-scoped cable graph; paths and loops never become energy stores. */
final class EnergyNetwork {
    static final int MAX_CABLES = 64;
    private static final DustParticleOptions FE_FLOW =
            new DustParticleOptions(new Vector3f(.13f, .84f, .65f), .65f);

    record Result(int moved, boolean truncated) {}

    private EnergyNetwork() {}

    static Result distribute(ServerLevel level, EnergyBlockEntity source, int maxOutput) {
        if (source.owner() == null || !EnergyPermissions.mayOperate(level, source.getBlockPos(), source.owner()))
            return new Result(0, false);
        var cables = new HashSet<BlockPos>();
        var devices = new HashSet<BlockPos>();
        var queue = new ArrayDeque<BlockPos>();
        var lamps = new ArrayList<EnergyBlockEntity>();
        var machines = new ArrayList<EnergyBlockEntity>();
        var batteries = new ArrayList<EnergyBlockEntity>();
        queue.add(source.getBlockPos());
        boolean truncated = false;
        while (!queue.isEmpty()) {
            BlockPos current = queue.removeFirst();
            for (Direction direction : Direction.values()) {
                BlockPos next = current.relative(direction);
                if (!level.hasChunkAt(next) || next.equals(source.getBlockPos())) continue;
                if (!(level.getBlockEntity(next) instanceof EnergyBlockEntity node)
                        || node.owner() == null || !node.owner().equals(source.owner())
                        || !EnergyPermissions.mayOperate(level, next, source.owner())) continue;
                if (node.kind() == EnergyBlockEntity.Kind.CABLE) {
                    if (cables.contains(next)) continue;
                    if (cables.size() >= MAX_CABLES) { truncated = true; continue; }
                    cables.add(next);
                    queue.addLast(next);
                } else if (devices.add(next)) {
                    if (node.kind() == EnergyBlockEntity.Kind.LAMP) lamps.add(node);
                    else if (node.kind() == EnergyBlockEntity.Kind.PRESS) machines.add(node);
                    else if (node.kind() == EnergyBlockEntity.Kind.BATTERY
                            && source.kind() == EnergyBlockEntity.Kind.GENERATOR) batteries.add(node);
                }
            }
        }
        lamps.sort(java.util.Comparator.comparingLong(node -> node.getBlockPos().asLong()));
        machines.sort(java.util.Comparator.comparingLong(node -> node.getBlockPos().asLong()));
        batteries.sort(java.util.Comparator.comparingLong(node -> node.getBlockPos().asLong()));
        int remaining = Math.min(maxOutput, source.energy());
        int moved = 0;
        for (EnergyBlockEntity target : concat(lamps, machines, batteries)) {
            if (remaining == 0) break;
            int amount = Math.min(remaining, target.capacity() - target.energy());
            if (amount <= 0) continue;
            int sent = source.extractOutput(amount, false);
            target.addEnergy(sent);
            moved += sent;
            remaining -= sent;
        }
        if (moved > 0 && level.getGameTime() % 5 == 0) {
            for (BlockPos wire : cables)
                level.sendParticles(FE_FLOW, wire.getX() + .5, wire.getY() + .55,
                        wire.getZ() + .5, 1, .13, .13, .13, 0);
        }
        return new Result(moved, truncated);
    }

    private static List<EnergyBlockEntity> concat(List<EnergyBlockEntity> lamps,
                                                   List<EnergyBlockEntity> machines,
                                                   List<EnergyBlockEntity> batteries) {
        var all = new ArrayList<EnergyBlockEntity>(lamps.size() + machines.size() + batteries.size());
        all.addAll(lamps);
        all.addAll(machines);
        all.addAll(batteries);
        return all;
    }
}
