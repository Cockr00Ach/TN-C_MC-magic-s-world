package com.tnc.tnc.world;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.levelgen.structure.templatesystem.*;

/** Replace only the defective BerryBush decoration present in the imported palace.
 * Persistent vanilla foliage retains the garden silhouette without crop support rules.
 * Architecture, containers and unrelated Conquest blocks are deliberately untouched. */
public final class ConquestPlantProcessor extends StructureProcessor {
    public static final ConquestPlantProcessor INSTANCE = new ConquestPlantProcessor();
    private ConquestPlantProcessor() {}

    public static BlockState replacement(ResourceLocation id) {
        if (!id.getNamespace().equals("conquest")) return null;
        return switch (id.getPath()) {
            case "beautyberry_bush" -> Blocks.AZALEA_LEAVES.defaultBlockState()
                    .setValue(BlockStateProperties.PERSISTENT, true);
            default -> null;
        };
    }

    @Override public StructureTemplate.StructureBlockInfo processBlock(LevelReader level, BlockPos origin,
            BlockPos pivot, StructureTemplate.StructureBlockInfo local, StructureTemplate.StructureBlockInfo world,
            StructurePlaceSettings settings) {
        var safe = replacement(BuiltInRegistries.BLOCK.getKey(world.state().getBlock()));
        return safe == null ? world : new StructureTemplate.StructureBlockInfo(world.pos(), safe, null);
    }

    // Runtime-only processor, never written into a datapack or structure NBT.
    @Override protected StructureProcessorType<?> getType() { return StructureProcessorType.BLOCK_IGNORE; }
}
