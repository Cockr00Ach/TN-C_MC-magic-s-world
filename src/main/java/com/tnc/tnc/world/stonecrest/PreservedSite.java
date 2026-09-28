package com.tnc.tnc.world.stonecrest;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.levelgen.structure.templatesystem.*;
import java.util.HashSet;
import java.util.Set;

/** Small immutable exclusion islands around pre-existing containers/devices, not whole columns. */
final class PreservedSite {
    final Set<Long> blocks=new HashSet<>();

    void reserve(BlockPos center) {
        for (int y=-2;y<=4;y++) for (int z=-2;z<=2;z++) for (int x=-2;x<=2;x++)
            blocks.add(center.offset(x,y,z).asLong());
    }
    boolean contains(BlockPos pos) { return blocks.contains(pos.asLong()); }
    long[] save() { return blocks.stream().mapToLong(Long::longValue).sorted().toArray(); }
    void load(long[] saved) { blocks.clear(); for (long p:saved) blocks.add(p); }

    final StructureProcessor processor=new StructureProcessor() {
        @Override public StructureTemplate.StructureBlockInfo processBlock(LevelReader level, BlockPos origin,
                BlockPos pivot, StructureTemplate.StructureBlockInfo local, StructureTemplate.StructureBlockInfo world,
                StructurePlaceSettings settings) {
            return contains(world.pos()) ? null : world;
        }
        // This per-job processor is never serialized or used by a datapack.
        @Override protected StructureProcessorType<?> getType() { return StructureProcessorType.BLOCK_IGNORE; }
    };
}
