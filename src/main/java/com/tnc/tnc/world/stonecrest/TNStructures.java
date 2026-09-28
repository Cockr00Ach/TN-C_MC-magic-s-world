package com.tnc.tnc.world.stonecrest;

import com.tnc.tnc.TNMod;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceType;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

public final class TNStructures {
    private static final DeferredRegister<StructureType<?>> STRUCTURES =
            DeferredRegister.create(Registries.STRUCTURE_TYPE, TNMod.MODID);
    private static final DeferredRegister<StructurePieceType> PIECES =
            DeferredRegister.create(Registries.STRUCTURE_PIECE, TNMod.MODID);

    public static final RegistryObject<StructureType<StonecrestStructure>> STONECREST = STRUCTURES.register(
            "stonecrest_fortress", () -> () -> StonecrestStructure.CODEC);
    public static final RegistryObject<StructureType<ImportedBuildingStructure>> IMPORTED_BUILDING = STRUCTURES.register(
            "imported_building", () -> () -> ImportedBuildingStructure.CODEC);
    public static final RegistryObject<StructureType<AbyssCitadelStructure>> ABYSS_CITADEL = STRUCTURES.register(
            "abyss_citadel", () -> () -> AbyssCitadelStructure.CODEC);
    public static final RegistryObject<StructurePieceType> ABYSS_MARKER = PIECES.register(
            "abyss_marker", () -> (StructurePieceType.ContextlessType) AbyssMarkerPiece::new);
    public static final RegistryObject<StructureType<LargeLandmarkStructure>> LARGE_LANDMARK = STRUCTURES.register(
            "large_landmark", () -> () -> LargeLandmarkStructure.CODEC);
    public static final RegistryObject<StructurePieceType> LANDMARK_MARKER = PIECES.register(
            "landmark_marker", () -> (StructurePieceType.ContextlessType) LargeLandmarkMarker::new);
    public static final RegistryObject<StructurePieceType> STONECREST_TEMPLATE = PIECES.register(
            "stonecrest_template", () -> (StructurePieceType.StructureTemplateType) StonecrestTemplatePiece::new);
    public static final RegistryObject<StructurePieceType> STONECREST_TERRAIN = PIECES.register(
            "stonecrest_terrain", () -> (StructurePieceType.ContextlessType) StonecrestTerrainPiece::new);

    private TNStructures() {
    }

    public static void register(IEventBus bus) {
        STRUCTURES.register(bus);
        PIECES.register(bus);
    }
}
