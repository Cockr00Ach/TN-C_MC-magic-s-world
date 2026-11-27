package com.tnc.tnc.sanctum;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.LinkedHashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

final class SanctumLedger extends SavedData {
    static final String KEY = "tnc_unlit_sanctum";
    final Map<UUID, Expedition> expeditions = new LinkedHashMap<>();
    final Set<UUID> victors = new HashSet<>();

    static SanctumLedger get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(SanctumLedger::load, SanctumLedger::new, KEY);
    }

    // A reused hall is rebuilt from its authored layout. Slots with offline members stay reserved.
    int vacantSlot() {
        Set<Integer> used = new HashSet<>();
        expeditions.values().forEach(run -> used.add(run.slot));
        int slot = 0;
        while (used.contains(slot)) slot++;
        return slot;
    }

    static final class Expedition {
        final UUID id;
        final int slot;
        final Map<UUID, ReturnPoint> members = new LinkedHashMap<>();
        final Set<UUID> spentRescues = new HashSet<>();
        final Set<UUID> pendingRevival = new HashSet<>();
        CompoundTag route = new CompoundTag();
        boolean won, rehearsal;
        int requestedPhase;

        Expedition(UUID id, int slot) {
            this.id = id;
            this.slot = slot;
        }

        BlockPos origin() {
            return new BlockPos((slot % 512) * 512, 64, (slot / 512) * 512);
        }
    }

    record ReturnPoint(ResourceLocation dimension, double x, double y, double z, float yaw, float pitch) {
        static ReturnPoint capture(ServerPlayer player) {
            return new ReturnPoint(player.level().dimension().location(), player.getX(), player.getY(),
                    player.getZ(), player.getYRot(), player.getXRot());
        }
    }

    static SanctumLedger load(CompoundTag root) {
        var data = new SanctumLedger();
        for (Tag element : root.getList("Runs", Tag.TAG_COMPOUND)) {
            var tag = (CompoundTag) element;
            var run = new Expedition(tag.getUUID("Id"), tag.getInt("Slot"));
            run.won = tag.getBoolean("Victory");
            run.rehearsal = tag.getBoolean("Rehearsal");
            if (run.rehearsal) run.requestedPhase = Math.max(0,Math.min(3,tag.getInt("RequestedPhase")));
            run.route = tag.getCompound("Route").copy();
            for (Tag entry : tag.getList("Members", Tag.TAG_COMPOUND)) {
                var pos = (CompoundTag) entry;
                UUID id = pos.getUUID("Id");
                var dimension = ResourceLocation.tryParse(pos.getString("Dim"));
                if (dimension == null) dimension = ResourceLocation.fromNamespaceAndPath("minecraft", "overworld");
                run.members.put(id, new ReturnPoint(dimension, pos.getDouble("X"), pos.getDouble("Y"),
                        pos.getDouble("Z"), pos.getFloat("Yaw"), pos.getFloat("Pitch")));
                if (pos.getBoolean("RescueUsed")) run.spentRescues.add(id);
                if (pos.getBoolean("RetryOnReturn")) run.pendingRevival.add(id);
            }
            if (!run.members.isEmpty()) data.expeditions.put(run.id, run);
        }
        for (Tag entry : root.getList("Victors", Tag.TAG_COMPOUND)) data.victors.add(((CompoundTag)entry).getUUID("Id"));
        return data;
    }

    @Override public CompoundTag save(CompoundTag root) {
        var runs = new ListTag();
        for (var run : expeditions.values()) {
            var tag = new CompoundTag();
            tag.putUUID("Id", run.id);
            tag.putInt("Slot", run.slot);
            tag.putBoolean("Victory", run.won);
            if (run.rehearsal) {
                tag.putBoolean("Rehearsal", true);
                tag.putInt("RequestedPhase", run.requestedPhase);
            }
            tag.put("Route", run.route.copy());
            var members = new ListTag();
            run.members.forEach((id, address) -> {
                var pos = new CompoundTag();
                pos.putUUID("Id", id);
                pos.putString("Dim", address.dimension.toString());
                pos.putDouble("X", address.x);
                pos.putDouble("Y", address.y);
                pos.putDouble("Z", address.z);
                pos.putFloat("Yaw", address.yaw);
                pos.putFloat("Pitch", address.pitch);
                pos.putBoolean("RescueUsed", run.spentRescues.contains(id));
                pos.putBoolean("RetryOnReturn", run.pendingRevival.contains(id));
                members.add(pos);
            });
            tag.put("Members", members);
            runs.add(tag);
        }
        root.put("Runs", runs);
        var cleared = new ListTag();
        for (UUID id : victors) {
            var tag = new CompoundTag();
            tag.putUUID("Id", id);
            cleared.add(tag);
        }
        root.put("Victors", cleared);
        return root;
    }
}
