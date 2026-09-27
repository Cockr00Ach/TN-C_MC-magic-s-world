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
    static final int HEIGHT = 28;
    static final int WIDTH = 35;
    static final int CENTER = 17;
    static BlockPos footprintOrigin(BlockPos legacyOrigin) { return legacyOrigin.offset(7-CENTER,0,7-CENTER); }
    record Plan(Map<BlockPos, BlockState> writes) {
        void apply(ServerLevel level) {
            writes.forEach((pos, state) -> level.setBlock(pos, state, 18));
        }
    }

    private PortalArchitecture() {}

    static Plan prepare(ServerLevel level, ResourceLocation resource, BlockPos origin, boolean refresh)
            throws IOException {
        return prepare(level,resource,origin,refresh,false);
    }

    static Plan prepare(ServerLevel level,ResourceLocation resource,BlockPos origin,boolean refresh,boolean firstExpansion)
            throws IOException {
        var template = level.getStructureManager().get(resource)
                .orElseThrow(() -> new IOException("缺少传送阵模板 " + resource));
        if (template.getSize().getX() != WIDTH || template.getSize().getZ() != WIDTH
                || template.getSize().getY() != HEIGHT)
            throw new IOException("传送阵模板仍是旧版，请同步 kubejs/data 后重启游戏");
        if (origin.getY() < level.getMinBuildHeight() || origin.getY() + HEIGHT > level.getMaxBuildHeight())
            throw new IOException("传送阵上方高度不足");
        BlockPos footprint = footprintOrigin(origin);
        AABB bounds = new AABB(footprint).expandTowards(WIDTH-1, HEIGHT-1, WIDTH-1);

        Map<BlockPos, BlockState> desired = decode(level, template.save(new CompoundTag()), footprint);
        Map<BlockPos, List<BlockState>> known = new HashMap<>();
        // Also used during INITIAL finalization: a crash may already have written one altar.
        {
            for (String name : List.of("legacy_v1", "legacy_v2", "legacy_v0", "legacy_v3")) {
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
        for (var iterator = writes.entrySet().iterator(); iterator.hasNext();) {
            var entry=iterator.next();
            BlockPos pos = entry.getKey();
            BlockState current = level.getBlockState(pos);
            boolean expansion = pos.getX()<origin.getX() || pos.getX()>origin.getX()+14
                    || pos.getZ()<origin.getZ() || pos.getZ()>origin.getZ()+14;
            // Expanded rim may meet the village's original path, fence or a ground ruin.
            // Leave these existing low blocks in place; never whitelist masonry as replaceable.
            if (expansion && pos.getY()<=origin.getY()+1 && !current.isAir()
                    && !naturalGround(current) && !current.canBeReplaced() && !current.is(BlockTags.FLOWERS)) {
                iterator.remove(); continue;
            }
            if (level.getBlockEntity(pos) != null)
                throw new IOException("传送阵范围内有容器或方块实体，未修改：" + pos.toShortString());
            if (current.isAir() || (current.canBeReplaced() && current.getFluidState().isEmpty())) continue;
            // Flowers (e.g. the source island's dandelions) are not canBeReplaced in 1.20.1.
            // Only initial construction may clear them; don't broaden the building/container whitelist.
            if ((!refresh || (expansion && firstExpansion)) && current.is(BlockTags.FLOWERS) && current.getFluidState().isEmpty()) continue;
            if (known.getOrDefault(pos, List.of()).contains(current)) continue;
            // Initial disk may replace natural ground, not a house floor or a container.
            if ((!refresh || (expansion && firstExpansion)) && pos.getY() <= origin.getY() + (expansion?4:1) && naturalGround(current)) continue;
            throw new IOException("传送阵被建筑或玩家改造占用，未修改：" + pos.toShortString() + " " + current);
        }
        // An NPC standing in the open courtyard must not block the upgrade forever.
        // Only actual changed blocks intersecting a body/feet require waiting.
        for (var entity:level.getEntitiesOfClass(LivingEntity.class,bounds)) {
            var occupied=entity.getBoundingBox().inflate(.05,.1,.05);
            for (var entry:writes.entrySet()) if (!level.getBlockState(entry.getKey()).equals(entry.getValue())
                    && occupied.intersects(new AABB(entry.getKey())))
                throw new IOException("请玩家及生物先离开正在改建的方块范围，再刷新外观");
        }
        return new Plan(writes);
    }

    static Plan prepareSupports(ServerLevel level,BlockPos origin) throws IOException {
        Map<BlockPos,BlockState> writes=new LinkedHashMap<>();
        for (int x=-10;x<25;x++) for (int z=-10;z<25;z++) {
            if (Math.hypot(x-7,z-7)>16.6) continue;
            for (int depth=1;depth<=5;depth++) {
                var pos=origin.offset(x,-depth,z); var state=level.getBlockState(pos);
                if (pos.getY()<level.getMinBuildHeight() || level.getBlockEntity(pos)!=null) break;
                // Waterlogging does not make a chest, stair or other solid replaceable.
                if (!state.isAir() && !(state.getBlock() instanceof net.minecraft.world.level.block.LiquidBlock)) break;
                if (!level.getEntitiesOfClass(LivingEntity.class,new AABB(pos)).isEmpty())
                    throw new IOException("请玩家及生物先离开传送阵基础施工范围");
                writes.put(pos,depth<=2?Blocks.COBBLESTONE.defaultBlockState():Blocks.STONE.defaultBlockState());
            }
        }
        return new Plan(writes);
    }

    private static boolean naturalGround(BlockState state) {
        return state.is(BlockTags.DIRT) || state.is(BlockTags.BASE_STONE_OVERWORLD)
                || state.is(BlockTags.SAND) || state.is(Blocks.GRAVEL) || state.is(Blocks.DIRT_PATH)
                || state.is(net.minecraftforge.common.Tags.Blocks.ORES);
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
