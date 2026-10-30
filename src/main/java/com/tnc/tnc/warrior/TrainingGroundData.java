package com.tnc.tnc.warrior;

import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * 演武场台账：哪些台子建好了、每座台的四个演武位谁在占、每档练习的纪录。
 *
 * <p>正常存档里只有一座台；这里记成列表是为了让 GameTest 能在同一世界里同时
 * 砌好几座台互不干扰，也方便以后真的多修几座。
 *
 * <p>它只记「长期」的东西 —— 教官、训练进度这些临时状态在内存里，重登即清。
 */
public final class TrainingGroundData extends SavedData {
    /** 一座演武场：维度 + 台心（Y 是台面高度）。 */
    public record Arena(String dimension, int x, int y, int z) {
        public String key() {
            return dimension + "@" + x + "," + y + "," + z;
        }

        public BlockPos center() {
            return new BlockPos(x, y, z);
        }
    }

    public record Record(int mode, int completions, int score, int reaction) {
    }

    private final List<Arena> arenas = new ArrayList<>();
    private final Map<String, Map<UUID, Integer>> pads = new LinkedHashMap<>();
    private final Map<UUID, CompoundTag> records = new HashMap<>();

    public Record record(UUID id) {
        var t = records.getOrDefault(id, new CompoundTag());
        int mode = PracticeDifficulty.clamp(t.getInt("Mode"));
        var r = t.getCompound("Tier" + mode);
        int count = Math.max(0, r.getInt("Completions"));
        return new Record(mode, count, Math.max(0, Math.min(100, r.getInt("Score"))),
            count == 0 ? 0 : Math.max(0, Math.min(80, r.getInt("Reaction"))));
    }

    public void cycle(UUID id) {
        var t = records.computeIfAbsent(id, k -> new CompoundTag());
        t.putInt("Mode", (record(id).mode() + 1) % 3);
        setDirty();
    }

    public void complete(UUID id, int mode, int score, int reaction) {
        var t = records.computeIfAbsent(id, k -> new CompoundTag());
        String key = "Tier" + PracticeDifficulty.clamp(mode);
        var r = t.getCompound(key);
        int count = Math.max(0, r.getInt("Completions"));
        r.putInt("Completions", count == Integer.MAX_VALUE ? count : count + 1);
        r.putInt("Score", Math.max(count == 0 ? 0 : Math.max(0, Math.min(100, r.getInt("Score"))), Math.max(0, Math.min(100, score))));
        r.putInt("Reaction", Math.min(count == 0 ? 80 : Math.max(0, Math.min(80, r.getInt("Reaction"))), Math.max(0, Math.min(80, reaction))));
        t.put(key, r);
        setDirty();
    }

    public static TrainingGroundData get(MinecraftServer server) {
        return server.overworld().getDataStorage()
            .computeIfAbsent(TrainingGroundData::load, TrainingGroundData::new, "tnc_training_ground_v1");
    }

    // ------------------------------------------------------------------
    // 演武位

    private static final String DEFAULT_ARENA = "default";

    private String defaultKey() {
        return arenas.isEmpty() ? DEFAULT_ARENA : arenas.get(arenas.size() - 1).key();
    }

    private Map<UUID, Integer> padMap(String arena) {
        return pads.computeIfAbsent(arena == null ? DEFAULT_ARENA : arena, k -> new HashMap<>());
    }

    /** 已占用的演武位；-1 表示还没占。 */
    public int slot(UUID id) {
        return slot(id, defaultKey());
    }

    public int slot(UUID id, String arena) {
        return pads.getOrDefault(arena == null ? DEFAULT_ARENA : arena, Map.of()).getOrDefault(id, -1);
    }

    /** 占一个空演武位；满员返回 -1。 */
    public int claim(UUID id) {
        return claim(id, defaultKey());
    }

    public int claim(UUID id, String arena) {
        var map = padMap(arena);
        Integer existing = map.get(id);
        if (existing != null) return existing;
        for (int pad = 0; pad < TrainingGroundRules.PAD_COUNT; pad++) {
            if (!map.containsValue(pad)) {
                map.put(id, pad);
                setDirty();
                return pad;
            }
        }
        return -1;
    }

    /** 收起某人的演武位（所有台子一起收）。 */
    public void release(UUID id) {
        boolean changed = false;
        for (var map : pads.values()) changed |= map.remove(id) != null;
        if (changed) setDirty();
    }

    public void release(UUID id, String arena) {
        var map = pads.get(arena == null ? DEFAULT_ARENA : arena);
        if (map != null && map.remove(id) != null) setDirty();
    }

    // ------------------------------------------------------------------
    // 台子

    public List<Arena> arenas() {
        return List.copyOf(arenas);
    }

    public boolean arenaBuilt() {
        return !arenas.isEmpty();
    }

    /** 玩家脚下这座台（维度要一致，坐标落在台面范围里）；不在台上返回 null。 */
    public Arena arenaAt(String dimension, double x, double y, double z) {
        for (var arena : arenas) {
            if (!arena.dimension().equals(dimension)) continue;
            if (TrainingGroundRules.contains(arena.x(), arena.y(), arena.z(), x, y, z)) return arena;
        }
        return null;
    }

    /** 最后建的那座台；没有台子时返回 null。 */
    public BlockPos arenaCenter() {
        return arenas.isEmpty() ? null : arenas.get(arenas.size() - 1).center();
    }

    public void markArena(String dimension, int x, int y, int z) {
        String dim = dimension == null || dimension.isEmpty() ? "minecraft:overworld" : dimension;
        var arena = new Arena(dim, x, y, z);
        if (!arenas.contains(arena)) {
            if (arenas.size() >= 16) arenas.remove(0);
            arenas.add(arena);
            padMap(arena.key());
            setDirty();
        }
    }

    // ------------------------------------------------------------------
    // 存读

    public static TrainingGroundData load(CompoundTag root) {
        var data = new TrainingGroundData();
        String legacyKey = DEFAULT_ARENA;
        if (root.contains("Arena")) {
            var a = root.getCompound("Arena");
            String dimension = a.contains("Dimension") ? a.getString("Dimension") : "minecraft:overworld";
            data.markArena(dimension, a.getInt("X"), a.getInt("Y"), a.getInt("Z"));
            legacyKey = data.arenas.get(0).key();
        }
        for (Tag raw : root.getList("Arenas", Tag.TAG_COMPOUND)) {
            var a = (CompoundTag) raw;
            data.markArena(a.getString("Dimension"), a.getInt("X"), a.getInt("Y"), a.getInt("Z"));
        }
        for (Tag raw : root.getList("Players", Tag.TAG_COMPOUND)) {
            var t = (CompoundTag) raw;
            if (!t.hasUUID("Id")) continue;
            UUID id = t.getUUID("Id");
            String arena = t.contains("Arena") ? t.getString("Arena") : legacyKey;
            int slot = t.contains("Slot") ? t.getInt("Slot") : t.getInt("Plot");
            var map = data.padMap(arena);
            if (slot >= 0 && slot < TrainingGroundRules.PAD_COUNT && !map.containsValue(slot)) {
                map.put(id, slot);
            }
            if (t.contains("Practice")) data.records.put(id, t.getCompound("Practice").copy());
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag root) {
        var list = new ListTag();
        var seen = new HashSet<UUID>();
        for (var entry : pads.entrySet()) {
            for (var pad : entry.getValue().entrySet()) {
                seen.add(pad.getKey());
                var t = new CompoundTag();
                t.putUUID("Id", pad.getKey());
                t.putString("Arena", entry.getKey());
                t.putInt("Slot", pad.getValue());
                if (records.containsKey(pad.getKey())) t.put("Practice", records.get(pad.getKey()).copy());
                list.add(t);
            }
        }
        for (var entry : records.entrySet()) {
            if (!seen.add(entry.getKey())) continue;
            var t = new CompoundTag();
            t.putUUID("Id", entry.getKey());
            t.putInt("Slot", -1);
            t.put("Practice", entry.getValue().copy());
            list.add(t);
        }
        root.put("Players", list);
        var arenaList = new ListTag();
        for (var arena : arenas) {
            var a = new CompoundTag();
            a.putString("Dimension", arena.dimension());
            a.putInt("X", arena.x());
            a.putInt("Y", arena.y());
            a.putInt("Z", arena.z());
            arenaList.add(a);
        }
        root.put("Arenas", arenaList);
        // 旧字段留着，方便回退到上一版时还能读出演武场位置。
        if (!arenas.isEmpty()) {
            var last = arenas.get(arenas.size() - 1);
            var a = new CompoundTag();
            a.putString("Dimension", last.dimension());
            a.putInt("X", last.x());
            a.putInt("Y", last.y());
            a.putInt("Z", last.z());
            root.put("Arena", a);
        }
        return root;
    }
}
