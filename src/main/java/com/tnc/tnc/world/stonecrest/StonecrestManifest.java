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
    private static final java.util.concurrent.ConcurrentMap<String, StonecrestManifest> CACHE =
            new java.util.concurrent.ConcurrentHashMap<>();

    public record Piece(ResourceLocation resource, BlockPos offset, Vec3i size, int blocks) {
    }

    private final Vec3i dimensions;
    private final BlockPos anchorLocal;
    private final List<Piece> pieces;
    private final BitSet[] buildingRows;
    private final BitSet[] terrainRows;
    private final byte[] distances;
    private final short[] groundHeights;
    private final float[] smoothHeights;
    private boolean floating;
    private boolean sunken;
    private final int blendDistance;
    private String fingerprint;

    private StonecrestManifest(Vec3i dimensions, BlockPos anchorLocal, List<Piece> pieces,
                               BitSet[] buildingRows, BitSet[] terrainRows, int blendDistance, short[] groundHeights) {
        this.dimensions = dimensions;
        this.anchorLocal = anchorLocal;
        this.pieces = List.copyOf(pieces);
        this.buildingRows = buildingRows;
        this.terrainRows = terrainRows;
        this.blendDistance=blendDistance;
        this.groundHeights = groundHeights;
        this.distances = buildDistances(dimensions.getX(), dimensions.getZ(), buildingRows, terrainRows,blendDistance,groundHeights);
        this.smoothHeights=groundHeights==null?null:smoothEdgeHeights(dimensions.getX(),dimensions.getZ(),buildingRows,terrainRows,groundHeights,blendDistance);
    }

    public static StonecrestManifest get() {
        return get("stonecrest");
    }

    public static StonecrestManifest get(String asset) {
        if (!asset.matches("[a-z0-9_]+")) throw new IllegalArgumentException("Invalid building id " + asset);
        return CACHE.computeIfAbsent(asset, key -> {
            try { return load(key); }
            catch (IOException error) { throw new IllegalStateException("Could not load building " + key, error); }
        });
    }

    private static StonecrestManifest load(String asset) throws IOException {
        String path = asset.equals("stonecrest") ? CLASSPATH : "/data/tnc/buildings/" + asset + ".json";
        try (InputStream stream = StonecrestManifest.class.getResourceAsStream(path)) {
            if (stream == null) {
                throw new IOException("missing bundled resource " + path);
            }
            try (Reader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
                JsonObject root = GSON.fromJson(reader, JsonObject.class);
                Vec3i dimensions = vec3(root.getAsJsonArray("dimensions"), "dimensions");
                if (dimensions.getX() < 1 || dimensions.getX() > 2048
                        || dimensions.getZ() < 1 || dimensions.getZ() > 2048
                        || dimensions.getY() < 1 || dimensions.getY() > 384) {
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
                int blend=root.has("blend_distance")?root.get("blend_distance").getAsInt():MAX_BLEND_DISTANCE;
                if (blend<1 || blend>96) throw new IOException("Invalid blend distance");
                short[] heights = root.has("ground_height_rows")
                        ? groundHeights(root.getAsJsonArray("ground_height_rows"), dimensions, buildingRows) : null;
                var result = new StonecrestManifest(dimensions, anchor, pieces, buildingRows, terrainRows,blend,heights);
                result.floating = root.has("floating") && root.get("floating").getAsBoolean();
                result.sunken = root.has("sunken") && root.get("sunken").getAsBoolean();
                try {
                    result.fingerprint = java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256")
                            .digest(root.toString().getBytes(StandardCharsets.UTF_8)));
                } catch (java.security.NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
                return result;
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

    private static short[] groundHeights(JsonArray rows, Vec3i dims, BitSet[] building) throws IOException {
        if (rows.size()!=dims.getZ()) throw new IOException("Ground profile row count mismatch");
        short[] values=new short[dims.getX()*dims.getZ()];
        for (int z=0;z<dims.getZ();z++) {
            var covered=new BitSet(dims.getX());
            for (var element:rows.get(z).getAsJsonArray()) {
                var run=element.getAsJsonArray(); int a=run.get(0).getAsInt(), b=run.get(1).getAsInt(), h=run.get(2).getAsInt();
                if (a<0 || b<a || b>=dims.getX() || h<0 || h>=dims.getY()
                        || covered.nextSetBit(a)>=0 && covered.nextSetBit(a)<=b)
                    throw new IOException("Invalid landscape ground profile");
                for (int x=a;x<=b;x++) { covered.set(x); values[z*dims.getX()+x]=(short)h; }
            }
            if (!covered.equals(building[z])) throw new IOException("Landscape profile must exactly cover core mask");
        }
        return values;
    }

    private static byte[] buildDistances(int width, int depth, BitSet[] building, BitSet[] terrain,int maxDistance,short[] heights) {
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
            if (nextDistance > maxDistance) {
                continue;
            }
            if (x > 0) visit(index, index - 1, x - 1, z, width, terrain, result, nextDistance, queue,heights);
            if (x + 1 < width) visit(index, index + 1, x + 1, z, width, terrain, result, nextDistance, queue,heights);
            if (z > 0) visit(index, index - width, x, z - 1, width, terrain, result, nextDistance, queue,heights);
            if (z + 1 < depth) visit(index, index + width, x, z + 1, width, terrain, result, nextDistance, queue,heights);
        }
        return result;
    }

    private static void visit(int from, int index, int x, int z, int width, BitSet[] terrain, byte[] distances,
                              int distance, ArrayDeque<Integer> queue,short[] heights) {
        if (terrain[z].get(x) && Byte.toUnsignedInt(distances[index]) > distance) {
            distances[index] = (byte) distance;
            if (heights!=null) heights[index]=heights[from];
            queue.addLast(index);
        }
    }

    /** Harmonic extension removes Voronoi seams between different boundary heights.
     * Source core elevations stay fixed; only the unexported transition belt is relaxed. */
    private static float[] smoothEdgeHeights(int width,int depth,BitSet[] core,BitSet[] terrain,short[] heights,int blend) {
        float[] current=new float[heights.length];
        for (int i=0;i<current.length;i++) current[i]=heights[i];
        float[] next=current.clone(); var cells=new java.util.ArrayList<Integer>();
        for (int z=0;z<depth;z++) for (int x=terrain[z].nextSetBit(0);x>=0;x=terrain[z].nextSetBit(x+1))
            if (!core[z].get(x)) cells.add(z*width+x);
        int[] indexes=cells.stream().mapToInt(Integer::intValue).toArray();
        for (int iteration=0;iteration<blend*2;iteration++) {
            for (int i:indexes) {
                int x=i%width,z=i/width,count=0; float sum=0;
                if (x>0 && terrain[z].get(x-1)) {sum+=current[i-1];count++;}
                if (x+1<width && terrain[z].get(x+1)) {sum+=current[i+1];count++;}
                if (z>0 && terrain[z-1].get(x)) {sum+=current[i-width];count++;}
                if (z+1<depth && terrain[z+1].get(x)) {sum+=current[i+width];count++;}
                next[i]=count==0?current[i]:sum/count;
            }
            var swap=current; current=next; next=swap;
        }
        return current;
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
    public int maxBlendDistance() { return blendDistance; }
    public boolean floating() { return floating; }
    public boolean sunken() { return sunken; }
    public String fingerprint() { return fingerprint; }
    public boolean hasGroundProfile() { return groundHeights!=null; }
    public int groundHeightAt(int x,int z) {
        return smoothHeights==null || !terrainAt(x,z) ? anchorLocal.getY() : Math.round(smoothHeights[z*dimensions.getX()+x]);
    }
    StonecrestTerrainPlanner.ColumnPlan terrainPlan(int currentY,int originY,int x,int z) {
        return StonecrestTerrainPlanner.plan(currentY,originY+groundHeightAt(x,z),distanceAt(x,z),blendDistance,
                false,0,0,hasGroundProfile());
    }

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
