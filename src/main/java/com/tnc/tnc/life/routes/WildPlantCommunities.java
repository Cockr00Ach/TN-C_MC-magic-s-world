package com.tnc.tnc.life.routes;

import com.tnc.tnc.home.HousingService;
import com.tnc.tnc.home.TownProtection;
import com.tnc.tnc.life.UncommonMagicPlants;
import com.tnc.tnc.life.botanical.BotanicalBlock;
import com.tnc.tnc.life.botanical.BotanicalPlantEntity;
import com.tnc.tnc.life.botanical.BotanicalSpecies;
import com.tnc.tnc.life.ecology.VerdantVeinBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.QuartPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BiomeTags;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.List;

/** Local plant communities on first generation. All column and habitat samples stay in the decorating chunk. */
final class WildPlantCommunities {
    private WildPlantCommunities() {}

    enum Habitat {
        MEADOW("wild_road_bell dawn_disk dance_bell honey_cluster homeward_flower", "prism_crown daynight_sandgrain echo_bean"),
        WOOD("wild_night_gourd shadow_cut turnpage_fern rainletter_bush", "paper_tree sleep_clock dream_compost star_dew shadow_hour_orchid mana_root verdant_vein"),
        BANK("wild_tide_reed wish_puff hanging_dewgrass flight_pod", "mist_cotton rainletter_bush verdant_vein"),
        POOL("mirror_lotus", "mirror_lotus"),
        RIDGE("wind_sail stone_fern ore_sleep_moss", "star_rest storm_crown thermal_foldleaf warning_moss sky_vine"),
        FROST("frost_chime daynight_sandgrain", "star_rest storm_crown"),
        WARM("hearth_pepper ladder_vine honey_cluster", "thermal_foldleaf rainletter_bush sky_vine"),
        GROTTO("hushcap_mushroom stone_fern ore_sleep_moss", "warning_moss echo_bean dream_compost mana_root"),
        COAST("wild_tide_reed stone_fern", "salt_ink hanging_dewgrass");

        final List<String> dominant;
        final List<String> accents;
        Habitat(String dominant, String accents) {
            this.dominant = List.of(dominant.split(" "));
            this.accents = List.of(accents.split(" "));
        }
        String pick(RandomSource random, boolean accent) {
            var pool = accent ? accents : dominant;
            return pool.get(random.nextInt(pool.size()));
        }
    }

    static boolean eligible(long worldSeed, ChunkPos chunk) {
        return random(worldSeed, chunk).nextInt(3) == 0;
    }

    private static RandomSource random(long seed, ChunkPos pos) {
        return RandomSource.create(seed ^ Mth.getSeed(pos.x, 701, pos.z));
    }

    static boolean allowedTerrain(ServerLevel level, LevelChunk chunk) {
        if (level != level.getServer().overworld() || chunk.getInhabitedTime() > 1200
                || !chunk.getBlockEntitiesPos().isEmpty()
                || chunk.getAllReferences().values().stream().anyMatch(refs -> !refs.isEmpty())
                || chunk.getAllStarts().values().stream().anyMatch(start -> start.isValid())) return false;
        if (com.tnc.tnc.world.stonecrest.WildPlantLandmarkMask.occupiesTerrain(level,chunk.getPos())) return false;
        return com.tnc.tnc.world.WildPlantIslandBounds.allows(level,chunk.getPos());
    }

    static int populate(ServerLevel level, LevelChunk chunk) {
        if (!allowedTerrain(level, chunk)) return 0;
        int underwater = com.tnc.tnc.life.seagarden.SeaGardenShoals.populate(level,chunk);
        if (underwater > 0) return underwater;
        return populateAtSeed(level, chunk, level.getSeed());
    }

    static int populateAtSeed(ServerLevel level, LevelChunk chunk, long worldSeed) {
        ChunkPos cp = chunk.getPos();
        var random = random(worldSeed, cp);
        var regional = random(worldSeed ^ 0x726f7365L, new ChunkPos(Math.floorDiv(cp.x,4), Math.floorDiv(cp.z,4)));
        if (random.nextInt(3) != 0) return 0;
        boolean seekCave = random.nextInt(4) == 0;
        // Try a few anchor columns. Once one takes, nearby plants repeat its silhouette with an occasional accent.
        for (int trial = 0; trial < 8; trial++) {
            int x = cp.getMinBlockX() + 3 + random.nextInt(10);
            int z = cp.getMinBlockZ() + 3 + random.nextInt(10);
            var center = surface(level, chunk, x, z);
            if (seekCave && trial < 5) {
                var cave = caveFloor(level, chunk, center);
                if (cave == null) continue;
                center = cave;
            }
            Habitat home = habitat(level, chunk, center);
            if (home == null) continue;
            boolean accent = random.nextInt(5) == 0;
            String dominant = home.pick(random.nextInt(3) == 0 || accent ? random : regional, accent);
            if (!plant(level, center, dominant)) continue;
            int planted = 1, target = 2 + random.nextInt(4);
            for (int attempt = 0; attempt < 14 && planted < target; attempt++) {
                int nx = x + random.nextInt(7) - 3, nz = z + random.nextInt(7) - 3;
                if (!interior(cp, nx, nz)) continue;
                var p = surface(level, chunk, nx, nz);
                if (home == Habitat.GROTTO) {
                    p = caveFloor(level, chunk, p);
                    if (p == null) continue;
                }
                if (Math.abs(p.getY() - center.getY()) > 3) continue;
                Habitat adjacent = habitat(level, chunk, p);
                if (adjacent == null) continue;
                boolean same = adjacent == home && random.nextInt(3) != 0;
                String type = same ? dominant : adjacent.pick(random, adjacent == home);
                // A single canopy or tree is a landmark; its understory remains walkable.
                if (type.equals("sky_vine") || type.equals("paper_tree")) continue;
                if (plant(level, p, type)) planted++;
            }
            return planted;
        }
        return 0;
    }

    static boolean interior(ChunkPos cp, int x, int z) {
        return x >= cp.getMinBlockX()+2 && x <= cp.getMaxBlockX()-2
                && z >= cp.getMinBlockZ()+2 && z <= cp.getMaxBlockZ()-2;
    }

    private static BlockPos surface(ServerLevel level, LevelChunk chunk, int x, int z) {
        int y = chunk.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x & 15, z & 15) + 1;
        BlockPos pos = new BlockPos(x, y, z);
        return y > level.getMinBuildHeight() && chunk.getBlockState(pos.below()).is(Blocks.SNOW) ? pos.below() : pos;
    }

    private static BlockPos caveFloor(ServerLevel level, LevelChunk chunk, BlockPos top) {
        int minY = Math.max(level.getMinBuildHeight()+2, top.getY()-30);
        boolean roof = false;
        for (int y=top.getY()-1; y>=minY; y--) {
            BlockPos ground = new BlockPos(top.getX(), y, top.getZ());
            var state = chunk.getBlockState(ground);
            if (roof && state.is(BlockTags.BASE_STONE_OVERWORLD) && chunk.getBlockState(ground.above()).isAir()
                    && chunk.getBlockState(ground.above(2)).isAir()) return ground.above();
            if (state.is(BlockTags.BASE_STONE_OVERWORLD)) roof = true;
        }
        return null;
    }

    static Habitat habitat(ServerLevel level, LevelChunk chunk, BlockPos p) {
        if (!level.isInWorldBounds(p) || !interior(chunk.getPos(), p.getX(), p.getZ())) return null;
        var below = chunk.getBlockState(p.below());
        var biome = chunk.getNoiseBiome(QuartPos.fromBlock(p.getX()), QuartPos.fromBlock(p.getY()), QuartPos.fromBlock(p.getZ()));
        if (below.getFluidState().is(FluidTags.WATER)) {
            var bed = chunk.getBlockState(p.below(2));
            return below.is(Blocks.WATER) && below.getFluidState().isSource() && (bed.is(BlockTags.DIRT) || bed.is(Blocks.CLAY)) ? Habitat.POOL : null;
        }
        boolean stone = below.is(BlockTags.BASE_STONE_OVERWORLD);
        boolean soil = below.is(BlockTags.DIRT) || below.is(Blocks.MOSS_BLOCK) || below.is(Blocks.MUD);
        if (!(soil || stone || below.is(BlockTags.SAND) || below.is(Blocks.CLAY))) return null;
        boolean coastal = biome.is(BiomeTags.IS_OCEAN) || biome.is(BiomeTags.IS_BEACH);
        // Actual water within two blocks, including ponds in biomes without a river tag.
        for (int dx=-2; dx<=2; dx++) for (int dz=-2; dz<=2; dz++) {
            if (Math.abs(dx)+Math.abs(dz)>2) continue;
            if (chunk.getFluidState(p.offset(dx,-1,dz)).is(FluidTags.WATER)) return coastal ? Habitat.COAST : Habitat.BANK;
        }
        if (stone && chunk.getHeight(Heightmap.Types.OCEAN_FLOOR, p.getX() & 15, p.getZ() & 15) >= p.getY()+2) return Habitat.GROTTO;
        if (biome.value().coldEnoughToSnow(p)) return Habitat.FROST;
        if (stone || biome.is(BiomeTags.IS_MOUNTAIN) || biome.is(BiomeTags.IS_HILL) || p.getY() >= level.getSeaLevel()+32) return Habitat.RIDGE;
        if (biome.is(BiomeTags.IS_JUNGLE) || biome.is(BiomeTags.IS_SAVANNA)) return Habitat.WARM;
        if (biome.is(BiomeTags.IS_FOREST) || biome.is(BiomeTags.IS_TAIGA)
                || chunk.getBlockState(new BlockPos(p.getX(),chunk.getHeight(Heightmap.Types.WORLD_SURFACE,p.getX()&15,p.getZ()&15),p.getZ())).is(BlockTags.LEAVES)) return Habitat.WOOD;
        return soil ? Habitat.MEADOW : null;
    }

    static boolean plant(ServerLevel level, BlockPos p, String id) {
        if (!interior(new ChunkPos(p),p.getX(),p.getZ()) || !level.hasChunkAt(p)
                || TownProtection.hazard(level,p) || HousingService.plotAt(level,p) != null) return false;
        var existing = level.getBlockState(p);
        if (!existing.isAir() && !existing.is(Blocks.GRASS) && !existing.is(Blocks.SNOW)) return false;
        Block block = ForgeRegistries.BLOCKS.getValue(ResourceLocation.fromNamespaceAndPath("tnc",id));
        if (block == null || block == Blocks.AIR) return false;
        BlockState state = RouteWildSpecimens.specimenState(block,id);
        if (block instanceof BotanicalBlock botany) {
            if ((botany.species == BotanicalSpecies.PAPER_TREE || botany.species == BotanicalSpecies.LADDER_VINE)
                    && (!level.isEmptyBlock(p.above()) || !level.isEmptyBlock(p.above(2)))) return false;
            state = state.setValue(BotanicalBlock.AGE,3);
        } else if (block instanceof RoutePlantBlock) {
            if (block == RouteContent.PLANTS.get("steam_lotus").get()) return false;
            state = state.setValue(RoutePlantBlock.AGE,3);
        } else if (block instanceof UncommonMagicPlants.HushcapBlock) {
            state = state.setValue(UncommonMagicPlants.HushcapBlock.AGE,3).setValue(UncommonMagicPlants.HushcapBlock.QUIET,3);
        } else if (block instanceof UncommonMagicPlants.RainletterBlock) {
            state = state.setValue(UncommonMagicPlants.RainletterBlock.AGE,3);
        } else if (block instanceof VerdantVeinBlock) {
            state = state.setValue(VerdantVeinBlock.AGE,3);
        }
        if (id.equals("warning_moss")) return RouteWildSpecimens.placeSpecimen(level,p,id);
        if (!state.canSurvive(level,p) || !level.setBlock(p,state,Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE)) return false;
        var entity = level.getBlockEntity(p);
        if (entity instanceof BotanicalPlantEntity botany) {
            botany.setWild();
            if (botany.species() == BotanicalSpecies.PAPER_TREE) {botany.bookStudied=true;botany.setChanged();}
        } else if (entity instanceof RoutePlantEntity route) {
            route.growth=18000;
            route.setChanged();
        }
        return true;
    }
}
