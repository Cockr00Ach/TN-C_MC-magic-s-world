package com.tnc.tnc.world;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.packs.resources.Resource;

import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/** Parsed and validated build manifest for the v5 starter sky island. */
public final class SkyIslandManifest {
    public static final ResourceLocation LOCATION =
            ResourceLocation.fromNamespaceAndPath("tnc", "sky_island/manifest.json");

    private static final Gson GSON = new Gson();

    public record Piece(String layer, ResourceLocation resource, BlockPos offset, Vec3i size, int blocks) {
    }

    private final int version;
    private final long seed;
    private final Vec3i dimensions;
    private final int worldOriginY;
    private final BlockPos townCenterLocal;
    private final BlockPos arrivalLocal;
    private final List<BlockPos> rootTips;
    private final ResourceLocation groundPortal;
    private final ResourceLocation islandPortal;
    private final List<Piece> pieces;
    private final long nonAirBlocks;

    private SkyIslandManifest(
            int version,
            long seed,
            Vec3i dimensions,
            int worldOriginY,
            BlockPos townCenterLocal,
            BlockPos arrivalLocal,
            List<BlockPos> rootTips,
            ResourceLocation groundPortal,
            ResourceLocation islandPortal,
            List<Piece> pieces,
            long nonAirBlocks
    ) {
        this.version = version;
        this.seed = seed;
        this.dimensions = dimensions;
        this.worldOriginY = worldOriginY;
        this.townCenterLocal = townCenterLocal;
        this.arrivalLocal = arrivalLocal;
        this.rootTips = List.copyOf(rootTips);
        this.groundPortal = groundPortal;
        this.islandPortal = islandPortal;
        this.pieces = List.copyOf(pieces);
        this.nonAirBlocks = nonAirBlocks;
    }

    public static SkyIslandManifest load(MinecraftServer server) throws IOException {
        Optional<Resource> found = server.getResourceManager().getResource(LOCATION);
        if (found.isEmpty()) {
            throw new IOException("missing data resource " + LOCATION);
        }

        try (Reader reader = found.get().openAsReader()) {
            JsonObject root = GSON.fromJson(reader, JsonObject.class);
            if (root == null) {
                throw new IOException("empty manifest " + LOCATION);
            }
            return parse(root);
        } catch (RuntimeException error) {
            throw new IOException("invalid manifest " + LOCATION + ": " + error.getMessage(), error);
        }
    }

    static SkyIslandManifest parse(JsonObject root) {
        int version = requiredInt(root, "version");
        long seed = requiredLong(root, "seed");
        Vec3i dimensions = vec3i(root, "dimensions");
        int worldOriginY = requiredInt(root, "world_origin_y");
        BlockPos center = blockPos(root, "town_center_local");
        BlockPos arrival = blockPos(root, "arrival_local");
        long nonAirBlocks = requiredLong(root, "non_air_blocks");

        if (version < 5) {
            throw new IllegalArgumentException("expected v5 or newer, got v" + version);
        }
        if (dimensions.getX() <= 0 || dimensions.getY() <= 0 || dimensions.getZ() <= 0) {
            throw new IllegalArgumentException("dimensions must be positive");
        }

        List<BlockPos> rootTips = new ArrayList<>();
        JsonArray rootTipArray = requiredArray(root, "root_tips");
        for (JsonElement element : rootTipArray) {
            rootTips.add(blockPos(element.getAsJsonArray(), "root_tips[]"));
        }
        if (rootTips.isEmpty()) {
            throw new IllegalArgumentException("root_tips must not be empty");
        }

        JsonObject portals = root.getAsJsonObject("portal_templates");
        if (portals == null) {
            throw new IllegalArgumentException("missing portal_templates");
        }
        ResourceLocation groundPortal = resource(portals, "ground_portal");
        ResourceLocation islandPortal = resource(portals, "island_portal");

        List<Piece> pieces = new ArrayList<>();
        Set<ResourceLocation> seenResources = new HashSet<>();
        JsonArray pieceArray = requiredArray(root, "pieces");
        for (JsonElement element : pieceArray) {
            JsonObject piece = element.getAsJsonObject();
            ResourceLocation resource = resource(piece, "resource");
            if (!seenResources.add(resource)) {
                throw new IllegalArgumentException("duplicate structure resource " + resource);
            }
            BlockPos offset = blockPos(piece, "offset");
            Vec3i size = vec3i(piece, "size");
            if (size.getX() < 1 || size.getY() < 1 || size.getZ() < 1
                    || size.getX() > 48 || size.getY() > 48 || size.getZ() > 48) {
                throw new IllegalArgumentException("invalid piece size for " + resource + ": " + size);
            }
            pieces.add(new Piece(
                    piece.has("layer") ? piece.get("layer").getAsString() : "unknown",
                    resource,
                    offset,
                    size,
                    requiredInt(piece, "blocks")
            ));
        }

        int declaredCount = requiredInt(root, "piece_count");
        if (pieces.isEmpty() || pieces.size() != declaredCount) {
            throw new IllegalArgumentException("piece_count=" + declaredCount + " but parsed " + pieces.size());
        }

        return new SkyIslandManifest(
                version, seed, dimensions, worldOriginY, center, arrival,
                rootTips, groundPortal, islandPortal, pieces, nonAirBlocks
        );
    }

    private static JsonArray requiredArray(JsonObject object, String name) {
        JsonArray value = object.getAsJsonArray(name);
        if (value == null) {
            throw new IllegalArgumentException("missing array " + name);
        }
        return value;
    }

    private static int requiredInt(JsonObject object, String name) {
        if (!object.has(name)) {
            throw new IllegalArgumentException("missing integer " + name);
        }
        return object.get(name).getAsInt();
    }

    private static long requiredLong(JsonObject object, String name) {
        if (!object.has(name)) {
            throw new IllegalArgumentException("missing long " + name);
        }
        return object.get(name).getAsLong();
    }

    private static ResourceLocation resource(JsonObject object, String name) {
        if (!object.has(name)) {
            throw new IllegalArgumentException("missing resource " + name);
        }
        ResourceLocation result = ResourceLocation.tryParse(object.get(name).getAsString());
        if (result == null) {
            throw new IllegalArgumentException("invalid resource " + name);
        }
        return result;
    }

    private static BlockPos blockPos(JsonObject object, String name) {
        return blockPos(requiredArray(object, name), name);
    }

    private static BlockPos blockPos(JsonArray array, String name) {
        if (array.size() != 3) {
            throw new IllegalArgumentException(name + " must contain exactly three integers");
        }
        return new BlockPos(array.get(0).getAsInt(), array.get(1).getAsInt(), array.get(2).getAsInt());
    }

    private static Vec3i vec3i(JsonObject object, String name) {
        BlockPos pos = blockPos(object, name);
        return new Vec3i(pos.getX(), pos.getY(), pos.getZ());
    }

    public int version() {
        return version;
    }

    public long seed() {
        return seed;
    }

    public Vec3i dimensions() {
        return dimensions;
    }

    public int worldOriginY() {
        return worldOriginY;
    }

    public BlockPos townCenterLocal() {
        return townCenterLocal;
    }

    public BlockPos arrivalLocal() {
        return arrivalLocal;
    }

    public List<BlockPos> rootTips() {
        return rootTips;
    }

    public ResourceLocation groundPortal() {
        return groundPortal;
    }

    public ResourceLocation islandPortal() {
        return islandPortal;
    }

    public List<Piece> pieces() {
        return pieces;
    }

    public long nonAirBlocks() {
        return nonAirBlocks;
    }
}
