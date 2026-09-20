package com.tnc.tnc.world.stonecrest;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.TemplateStructurePiece;
import net.minecraft.world.level.levelgen.structure.templatesystem.BlockIgnoreProcessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;

final class StonecrestTemplatePiece extends TemplateStructurePiece {
    StonecrestTemplatePiece(StructureTemplateManager manager, ResourceLocation template, BlockPos position) {
        super(TNStructures.STONECREST_TEMPLATE.get(), 0, manager, template, template.toString(), settings(), position);
    }

    StonecrestTemplatePiece(StructureTemplateManager manager, CompoundTag tag) {
        super(TNStructures.STONECREST_TEMPLATE.get(), tag, manager, ignored -> settings());
    }

    private static StructurePlaceSettings settings() {
        return new StructurePlaceSettings()
                .setMirror(Mirror.NONE)
                .setRotation(Rotation.NONE)
                .setIgnoreEntities(false)
                .setKeepLiquids(false)
                .addProcessor(BlockIgnoreProcessor.STRUCTURE_BLOCK);
    }

    @Override
    protected void handleDataMarker(String marker, BlockPos pos, ServerLevelAccessor level,
                                    RandomSource random, BoundingBox box) {
        // The first export contains no structure-block markers.  This hook is
        // intentionally reserved for later boss spawners and loot markers.
    }
}
