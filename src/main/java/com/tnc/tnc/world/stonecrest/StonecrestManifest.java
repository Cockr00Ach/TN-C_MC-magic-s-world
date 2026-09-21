package com.tnc.tnc.world.stonecrest;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.resources.ResourceLocation;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;

/** Immutable metadata generated alongside the Stonecrest structure templates. */
public final class StonecrestManifest {
    private static final String CLASSPATH = "/data/tnc/stonecrest/manifest.json";
    private static final Gson GSON = new Gson();
    private static final int MAX_BLEND_DISTANCE = 24;
    private static volatile StonecrestManifest instance;

    public record Piece(ResourceLocation resource, BlockPos offset, Vec3i size, int blocks) {
    }

    private final Vec3i dimensions;
    private final BlockPos anchorLocal;
    private final List<Piece> pieces;
    private final BitSet[] buildingRows;
    private final BitSet[] terrainRows;
    private final byte[] distances;

    private StonecrestManifest(Vec3i dimensions, BlockPos anchorLocal, List<Piece> pieces,
                               BitSet[] buildingRows, BitSet[] terrainRows) {
        this.dimensions = dimensions;
        this.anchorLocal = anchorLocal;
        this.pieces = List.copyOf(pieces);
        this.buildingRows = buildingRows;
        this.terrainRows = terrainRows;
        this.distances = buildDistances(dimensions.getX(), dimensions.getZ(), buildingRows, terrainRows);
    }

    public static StonecrestManifest get() {
        StonecrestManifest cached = instance;
        if (cached != null) {
            return cached;
        }
        synchronized (StonecrestManifest.class) {
            if (instance == null) {
                try {
                    instance = load();
                } catch (IOException error) {
                    throw new IllegalStateException("Could not load Stonecrest manifest", error);
                }
            }
            return instance;
        }
    }

    private static StonecrestManifest load() throws IOException {
        try (InputStream stream = StonecrestManifest.class.getResourceAsStream(CLASSPATH)) {
            if (stream == null) {
                throw new IOException("missing bundled resource " + CLASSPATH);
            }
            try (Reader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
                JsonObject root = GSON.fromJson(reader, JsonObject.class);
                Vec3i dimensions = vec3(root.getAsJsonArray("dimensions"), "dimensions");
                if (dimensions.getX() < 1 || dimensions.getX() > 272
                        || dimensions.getZ() < 1 || dimensions.getZ() > 272
                        || dimensions.getY() < 1 || dimensions.getY() > 256) {
                    throw new IOException("unsupported Stonecrest dimensions " + dimensions);
                }
                BlockPos anchor = pos(root.getAsJsonArray("anchor_local"), "anchor_local");
                BitSet[] buildingRows = rows(root.getAsJsonArray("mask_rows"), dimensions.getX(), dimensions.getZ());
                BitSet[] terrainRows = rows(root.getAsJsonArray("terrain_mask_rows"), dimensions.getX(), dimensions.getZ());

                List<Piece> pieces = new ArrayList<>();
                for (JsonElement element : root.getAsJsonArray("pieces")) {
                    JsonObject piece = element.getAsJsonObject();
                    ResourceLocation resource = ResourceLocation.tryParse(piece.get("resource").getAsString());
                    if (resource == null) {
                        throw new IOException("invalid Stonecrest template resource");
                    }
                    Vec3i size = vec3(piece.getAsJsonArray("size"), "piece.size");
                    if (size.getX() > 48 || size.getY() > 48 || size.getZ() > 48) {
                        throw new IOException("oversized Stonecrest template " + resource + ": " + size);
                    }
                    pieces.add(new Piece(resource, pos(piece.getAsJsonArray("offset"), "piece.offset"),
                            size, piece.get("blocks").getAsInt()));
                }
                if (pieces.isEmpty() || pieces.size() != root.get("piece_count").getAsInt()) {
                    throw new IOException("Stonecrest piece count mismatch");
                }
                return new StonecrestManifest(dimensions, anchor, pieces, buildingRows, terrainRows);
            }
        }
    }

    private static BitSet[] rows(JsonArray source, int width, int depth) throws IOException {
        if (source == null || source.size() != depth) {
            throw new IOException("Stonecrest mask row count mismatch");
        }
        BitSet[] result = new BitSet[depth];
        for (int z = 0; z < depth; z++) {
            BitSet row = new BitSet(width);
            for (JsonElement runElement : source.get(z).getAsJsonArray()) {
                JsonArray run = runElement.getAsJsonArray();
                int start = run.get(0).getAsInt();
                int end = run.get(1).getAsInt();
                if (start < 0 || end < start || end >= width) {
                    throw new IOException("invalid Stonecrest mask run " + start + ".." + end);
                }
                row.set(start, end + 1);
            }
            result[z] = row;
        }
        return result;
    }

    private static byte[] buildDistances(int width, int depth, BitSet[] building, BitSet[] terrain) {
        byte[] result = new byte[width * depth];
        java.util.Arrays.fill(result, (byte) 127);
        ArrayDeque<Integer> queue = new ArrayDeque<>();
        for (int z = 0; z < depth; z++) {
            for (int x = building[z].nextSetBit(0); x >= 0; x = building[z].nextSetBit(x + 1)) {
                int index = z * width + x;
                result[index] = 0;
                queue.add(index);
            }
        }
        while (!queue.isEmpty()) {
            int index = queue.removeFirst();
            int x = index % width;
            int z = index / width;
            int nextDistance = Byte.toUnsignedInt(result[index]) + 1;
            if (nextDistance > MAX_BLEND_DISTANCE) {
                continue;
            }
            if (x > 0) visit(index - 1, x - 1, z, width, terrain, result, nextDistance, queue);
            if (x + 1 < width) visit(index + 1, x + 1, z, width, terrain, result, nextDistance, queue);
            if (z > 0) visit(index - width, x, z - 1, width, terrain, result, nextDistance, queue);
            if (z + 1 < depth) visit(index + width, x, z + 1, width, terrain, result, nextDistance, queue);
        }
        return result;
    }

    private static void visit(int index, int x, int z, int width, BitSet[] terrain, byte[] distances,
                              int distance, ArrayDeque<Integer> queue) {
        if (terrain[z].get(x) && Byte.toUnsignedInt(distances[index]) > distance) {
            distances[index] = (byte) distance;
            queue.addLast(index);
        }
    }

    private static Vec3i vec3(JsonArray array, String name) throws IOException {
        BlockPos pos = pos(array, name);
        return new Vec3i(pos.getX(), pos.getY(), pos.getZ());
    }

    private static BlockPos pos(JsonArray array, String name) throws IOException {
        if (array == null || array.size() != 3) {
            throw new IOException(name + " must contain three integers");
        }
        return new BlockPos(array.get(0).getAsInt(), array.get(1).getAsInt(), array.get(2).getAsInt());
    }

    public Vec3i dimensions() { return dimensions; }
    public BlockPos anchorLocal() { return anchorLocal; }
    public List<Piece> pieces() { return pieces; }
    public int maxBlendDistance() { return MAX_BLEND_DISTANCE; }

    public boolean terrainAt(int x, int z) {
        return x >= 0 && z >= 0 && x < dimensions.getX() && z < dimensions.getZ() && terrainRows[z].get(x);
    }

    public boolean buildingAt(int x, int z) {
        return x >= 0 && z >= 0 && x < dimensions.getX() && z < dimensions.getZ() && buildingRows[z].get(x);
    }

    public int distanceAt(int x, int z) {
        if (!terrainAt(x, z)) return 127;
        return Byte.toUnsignedInt(distances[z * dimensions.getX() + x]);
    }
}
