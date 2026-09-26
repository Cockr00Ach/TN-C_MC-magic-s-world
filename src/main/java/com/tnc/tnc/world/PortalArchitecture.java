package com.tnc.tnc.world;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Preflight both sites before mutating either; remove only matching legacy blocks, never cuboids. */
final class PortalArchitecture {
    static final int HEIGHT = 22;
    record Plan(Map<BlockPos, BlockState> writes) {
        void apply(ServerLevel level) {
            writes.forEach((pos, state) -> level.setBlock(pos, state, 18));
        }
    }

    private PortalArchitecture() {}

    static Plan prepare(ServerLevel level, ResourceLocation resource, BlockPos origin, boolean refresh)
            throws IOException {
        var template = level.getStructureManager().get(resource)
                .orElseThrow(() -> new IOException("缺少传送阵模板 " + resource));
        if (template.getSize().getX() != 15 || template.getSize().getZ() != 15
                || template.getSize().getY() != HEIGHT)
            throw new IOException("传送阵模板仍是旧版，请同步 kubejs/data 后重启游戏");
        if (origin.getY() < level.getMinBuildHeight() || origin.getY() + HEIGHT > level.getMaxBuildHeight())
            throw new IOException("传送阵上方高度不足");
        AABB bounds = new AABB(origin).expandTowards(14, HEIGHT-1, 14);
        if (!level.getEntitiesOfClass(LivingEntity.class, bounds).isEmpty())
            throw new IOException("请玩家及生物先离开两座传送阵的建筑范围，再刷新外观");

        Map<BlockPos, BlockState> desired = decode(level, template.save(new CompoundTag()), origin);
        Map<BlockPos, List<BlockState>> known = new HashMap<>();
        // Also used during INITIAL finalization: a crash may already have written one altar.
        {
            for (String name : List.of("legacy_v1", "legacy_v2", "legacy_v0")) {
                ResourceLocation id = ResourceLocation.fromNamespaceAndPath("tnc", "sky_island/portal/" + name);
                var old = level.getStructureManager().get(id)
                        .orElseThrow(() -> new IOException("缺少旧版传送阵迁移模板 " + id));
                decode(level, old.save(new CompoundTag()), origin).forEach((pos, state) ->
                        known.computeIfAbsent(pos, unused -> new ArrayList<>()).add(state));
            }
            desired.forEach((pos, state) -> known.computeIfAbsent(pos, unused -> new ArrayList<>()).add(state));
        }
        Map<BlockPos, BlockState> writes = new LinkedHashMap<>();
        for (var entry : known.entrySet()) {
            BlockPos pos = entry.getKey();
            if (!desired.containsKey(pos) && entry.getValue().contains(level.getBlockState(pos)))
                writes.put(pos, Blocks.AIR.defaultBlockState());
        }
        writes.putAll(desired);
        // Walk-in and destination headroom must be clear; don't rely on teleport clearing a player build.
        for (int y = 2; y <= 4; y++) {
            BlockPos pos = origin.offset(7, y, 7);
            writes.put(pos, Blocks.AIR.defaultBlockState());
        }
        for (var entry : writes.entrySet()) {
            BlockPos pos = entry.getKey();
            BlockState current = level.getBlockState(pos);
            if (level.getBlockEntity(pos) != null)
                throw new IOException("传送阵范围内有容器或方块实体，未修改：" + pos.toShortString());
            if (current.isAir() || (current.canBeReplaced() && current.getFluidState().isEmpty())) continue;
            if (known.getOrDefault(pos, List.of()).contains(current)) continue;
            // Initial disk may replace natural ground, not a house floor or a container.
            if (!refresh && pos.getY() <= origin.getY() + 1 && naturalGround(current)) continue;
            throw new IOException("传送阵被建筑或玩家改造占用，未修改：" + pos.toShortString());
        }
        return new Plan(writes);
    }

    private static boolean naturalGround(BlockState state) {
        return state.is(BlockTags.DIRT) || state.is(BlockTags.BASE_STONE_OVERWORLD)
                || state.is(BlockTags.SAND) || state.is(Blocks.GRAVEL) || state.is(Blocks.DIRT_PATH);
    }

    private static Map<BlockPos, BlockState> decode(ServerLevel level, CompoundTag tag, BlockPos origin) {
        var palette = tag.getList("palette", Tag.TAG_COMPOUND);
        var blocks = tag.getList("blocks", Tag.TAG_COMPOUND);
        Map<BlockPos, BlockState> result = new LinkedHashMap<>();
        for (int i = 0; i < blocks.size(); i++) {
            CompoundTag block = blocks.getCompound(i);
            var position = block.getList("pos", Tag.TAG_INT);
            BlockState state = NbtUtils.readBlockState(level.holderLookup(Registries.BLOCK),
                    palette.getCompound(block.getInt("state")));
            if (!state.isAir()) result.put(origin.offset(position.getInt(0), position.getInt(1), position.getInt(2)), state);
        }
        return result;
    }
}
