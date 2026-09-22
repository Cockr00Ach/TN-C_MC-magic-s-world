package com.tnc.tnc.npc;

import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraftforge.registries.ForgeRegistries;
import org.slf4j.Logger;

import java.util.HashMap;
import java.util.Map;

/**
 * "固定 NPC" 的登记表 —— 存在存档里的世界级数据（{@code SavedData}）。
 *
 * <h2>位置怎么存：锚点 + 偏移，而不是绝对坐标</h2>
 * 天空岛是**按存档生成的**，每个存档位置都不同。所以这里存的是
 * <b>「相对天空岛某个锚点的偏移」</b>（见 {@link SkyIslandAnchors}），
 * 放置时按该存档的真实坐标算出来 —— 这样换存档 NPC 会跟着岛走。
 *
 * <p>也支持绝对坐标锚点（{@link SkyIslandAnchors.Anchor} 之外用 {@code ABSOLUTE}），
 * 留给"不在岛上的 NPC"以及调试用。
 *
 * <p>数据文件：{@code <存档>/data/tnc_npc_placements.dat}。
 */
public class NpcPlacementSavedData extends net.minecraft.world.level.saveddata.SavedData {

    private static final Logger LOGGER = LogUtils.getLogger();
    public static final String DATA_NAME = "tnc_npc_placements";

    /** 绝对坐标锚点（不在天空岛上的 NPC / 调试）。 */
    public static final String ABSOLUTE = "ABSOLUTE";

    /**
     * 一条登记。
     *
     * @param npcId  实体 id（如 {@code self}，对应 {@code tnc:self}）
     * @param anchor 锚点名（{@link SkyIslandAnchors.Anchor} 的名字，或 {@code ABSOLUTE}）
     * @param dx/dy/dz 相对锚点的偏移；{@code ABSOLUTE} 时它们就是世界坐标
     */
    public record Placement(String npcId, String anchor, int dx, int dy, int dz) {
    }

    private final Map<String, Placement> placements = new HashMap<>();

    /**
     * 本存档已经采用过的 {@link NpcPlacementDefaults} 版本。
     * {@code 0} = 这个存档早在版本机制存在之前就存过盘（或还没播种过）。
     */
    private int defaultsVersion = 0;

    public static NpcPlacementSavedData get(ServerLevel level) {
        return level.getServer().overworld().getDataStorage().computeIfAbsent(
                NpcPlacementSavedData::load, NpcPlacementSavedData::new, DATA_NAME);
    }

    public static NpcPlacementSavedData load(CompoundTag tag) {
        NpcPlacementSavedData data = new NpcPlacementSavedData();
        data.defaultsVersion = tag.getInt("DefaultsVersion"); // 老存档没有这个键 → 0 → 下次启动刷新一次
        ListTag list = tag.getList("Npcs", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag e = list.getCompound(i);
            String id = e.getString("Id");
            if (id.isEmpty()) {
                continue;
            }
            data.placements.put(id, new Placement(id,
                    e.contains("Anchor") ? e.getString("Anchor") : ABSOLUTE,
                    e.getInt("DX"), e.getInt("DY"), e.getInt("DZ")));
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag list = new ListTag();
        placements.values().forEach(p -> {
            CompoundTag e = new CompoundTag();
            e.putString("Id", p.npcId());
            e.putString("Anchor", p.anchor());
            e.putInt("DX", p.dx());
            e.putInt("DY", p.dy());
            e.putInt("DZ", p.dz());
            list.add(e);
        });
        tag.put("Npcs", list);
        tag.putInt("DefaultsVersion", defaultsVersion);
        return tag;
    }

    // ------------------------------------------------------------------ API

    public void put(Placement placement) {
        placements.put(placement.npcId(), placement);
        this.setDirty();
    }

    public Placement get(String npcId) {
        return placements.get(npcId);
    }

    public Map<String, Placement> all() {
        return java.util.Collections.unmodifiableMap(placements);
    }

    public boolean remove(String npcId) {
        boolean removed = placements.remove(npcId) != null;
        if (removed) {
            this.setDirty();
        }
        return removed;
    }

    // ------------------------------------------------------------------ 坐标解析

    /**
     * 把登记换算成**这个世界里**的实际坐标。
     *
     * @return 坐标；天空岛锚点还没就绪时返回 {@code null}（调用方跳过这次补位）
     */
    public BlockPos resolve(ServerLevel level, Placement placement) {
        if (ABSOLUTE.equals(placement.anchor())) {
            return new BlockPos(placement.dx(), placement.dy(), placement.dz());
        }
        SkyIslandAnchors.Anchor anchor;
        try {
            anchor = SkyIslandAnchors.Anchor.valueOf(placement.anchor());
        } catch (IllegalArgumentException e) {
            LOGGER.error("TN-C npc: unknown anchor '{}' for {} —— 登记已失效，请重新 place",
                    placement.anchor(), placement.npcId());
            return null;
        }
        BlockPos base = SkyIslandAnchors.resolve(level, anchor);
        if (base == null) {
            return null;
        }
        return base.offset(placement.dx(), placement.dy(), placement.dz());
    }

    // ------------------------------------------------------------------ 补位

    /** 检查所有登记的 NPC：算得出坐标、但实体不在 → 补一个。返回本次补的数量。 */
    public int ensureAll(ServerLevel overworld) {
        int spawned = 0;
        for (Placement p : placements.values()) {
            if (ensureOne(overworld, p)) {
                spawned++;
            }
        }
        return spawned;
    }

    /**
     * 确保**默认登记**存在于本存档里，并在默认表版本升级时刷新它们。
     *
     * <p>规则（按顺序）：
     * <ol>
     *   <li>缺的补上；</li>
     *   <li>存档记的默认表版本 < {@link NpcPlacementDefaults#VERSION} 时，
     *       用新默认值覆盖**同名条目**（否则我改了默认值，已经玩过的存档不会跟着变，
     *       每次都要手敲命令 —— 用户明确要求"下一个新存档直接看到"）；</li>
     *   <li>刷新完把版本号写进存档，避免每次启动都覆盖。</li>
     * </ol>
     *
     * <p>想手动调位置：跑 {@code /tnc npc here <id>}，然后<b>把 {@code /tnc npc list}
     * 打出的那一行抄回 {@link NpcPlacementDefaults#DEFAULTS} 并给 VERSION +1</b> ——
     * 这样手动值会被固化成新默认，不会被下次刷新顶掉。
     *
     * @return 本次新增或刷新的条数
     */
    public int seedDefaults() {
        int changed = 0;
        boolean refresh = defaultsVersion < NpcPlacementDefaults.VERSION;

        for (Placement def : NpcPlacementDefaults.DEFAULTS) {
            Placement existing = placements.get(def.npcId());
            if (existing == null) {
                placements.put(def.npcId(), def);
                changed++;
            } else if (refresh && !existing.equals(def)) {
                LOGGER.info("TN-C npc: refreshing default placement of {} -> {} (was {})",
                        def.npcId(), def, existing);
                placements.put(def.npcId(), def);
                changed++;
            }
        }

        if (defaultsVersion != NpcPlacementDefaults.VERSION) {
            defaultsVersion = NpcPlacementDefaults.VERSION;
            this.setDirty();
        }
        if (changed > 0) {
            this.setDirty();
            LOGGER.info("TN-C npc: seeded/refreshed {} default placement(s) (defaults v{})",
                    changed, NpcPlacementDefaults.VERSION);
        }
        return changed;
    }

    public boolean ensureOne(ServerLevel level, Placement placement) {
        // ★ 岛屿没生成完就别放 —— 生成中途 CENTER 这类坐标已经有值了，但路面还没铺完，
        //   这时放上去 NPC 会掉进虚空（而且生成器随后还会改地形，等于白放）。
        //   跳过这次；每 5 秒一次的自检会在岛 COMPLETE 之后自动把它放出来。
        boolean islandAnchor = !ABSOLUTE.equals(placement.anchor());
        if (islandAnchor && !SkyIslandAnchors.isComplete(level)) {
            return false;
        }

        BlockPos pos = resolve(level, placement);
        if (pos == null) {
            return false; // 锚点还没就绪（天空岛没生成完）—— 安静跳过，下次再试
        }
        // ★ 修正（2026-09-22，用户实测反馈）：以前这里**无条件**用高度图贴地，
        //   结果把用户明确指定的 Y 覆盖掉了 —— 用户在 179 登记，NPC 被抬到 200（差 21 格），
        //   表现就是"命令回显对、但人看不见"。
        //   现在只做**最小必要修正**：目标格本来就站得住 → 原样用；
        //   只有被方块占住 / 脚下悬空 / 区块没加载时，才去就近找落点。
        BlockPos adjusted = adjustToStandable(level, pos);
        if (adjusted != null) {
            pos = adjusted;
        }

        EntityType<?> type = ForgeRegistries.ENTITY_TYPES.getValue(
                ResourceLocation.fromNamespaceAndPath(com.tnc.tnc.TNMod.MODID, placement.npcId()));
        if (type == null) {
            LOGGER.error("TN-C npc: placement references unknown entity type tnc:{}", placement.npcId());
            return false;
        }
        boolean present = !level.getEntitiesOfClass(Entity.class,
                new net.minecraft.world.phys.AABB(pos).inflate(24.0D),
                entity -> entity.getType() == type).isEmpty();
        if (present) {
            return false;
        }
        Entity entity = type.create(level);
        if (entity == null) {
            LOGGER.error("TN-C npc: failed to create tnc:{}", placement.npcId());
            return false;
        }
        entity.moveTo(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D,
                level.random.nextFloat() * 360.0F, 0.0F);
        level.addFreshEntity(entity);
        LOGGER.info("TN-C npc: restored tnc:{} at {} (anchor={} offset={}/{}/{})",
                placement.npcId(), pos, placement.anchor(), placement.dx(), placement.dy(), placement.dz());
        return true;
    }

    /**
     * 只做**最小必要**的落点修正 —— 尊重调用方给的 Y。
     *
     * <p>规则（按优先级）：
     * <ol>
     *   <li><b>目标格 + 上一格都能站</b>（脚下不是空气）→ 返回原坐标（<b>一字不改</b>）；</li>
     *   <li>脚下是空气 → 向下找最近的地面（最多 8 格）；</li>
     *   <li>目标格被方块占住 → 向上找最近的可站立格（最多 6 格）；</li>
     *   <li>都不行 → 返回 {@code null}，由调用方保留原坐标（宁可放得难看，也别乱挪）。</li>
     * </ol>
     *
     * <p>⚠️ 这里曾经写成"无条件用高度图取最高地面"，那会把用户明确指定的高度整个覆盖掉
     * （用户站 179 → NPC 跑到 200）。<b>修正地形只该在"站不住"时发生。</b>
     */
    private static BlockPos adjustToStandable(ServerLevel level, BlockPos pos) {
        if (!level.hasChunkAt(pos)) {
            return pos; // 区块没加载：原样返回，等下次再试（不要在这里做判断）
        }
        // ① 站得住
        if (isStandable(level, pos)) {
            return pos;
        }
        // ② 脚下悬空 → 向下找地面
        for (int dy = 1; dy <= 8; dy++) {
            BlockPos down = pos.below(dy);
            if (isStandable(level, down)) {
                return down;
            }
            if (!level.getBlockState(down).isAir()) {
                break; // 碰到实体方块就停：再往下就是钻进去了
            }
        }
        // ③ 被占住 → 向上找
        for (int dy = 1; dy <= 6; dy++) {
            BlockPos up = pos.above(dy);
            if (isStandable(level, up)) {
                return up;
            }
        }
        return null;
    }

    /** 该格能站人吗：本格与上一格可穿过，且本格下方是实体方块。 */
    private static boolean isStandable(ServerLevel level, BlockPos pos) {
        if (!level.hasChunkAt(pos)) {
            return false;
        }
        var below = level.getBlockState(pos.below());
        var feet = level.getBlockState(pos);
        var head = level.getBlockState(pos.above());
        return !below.isAir() && below.isSolidRender(level, pos.below())
                && feet.getCollisionShape(level, pos).isEmpty()
                && head.getCollisionShape(level, pos.above()).isEmpty();
    }

    /** 强制重放：先清掉附近的同类，再按登记坐标放一个。返回放置数量。 */
    public int respawn(ServerLevel level, String npcId) {
        Placement placement = placements.get(npcId);
        if (placement == null) {
            return 0;
        }
        BlockPos pos = resolve(level, placement);
        if (pos == null) {
            return 0;
        }
        EntityType<?> type = ForgeRegistries.ENTITY_TYPES.getValue(
                ResourceLocation.fromNamespaceAndPath(com.tnc.tnc.TNMod.MODID, npcId));
        if (type == null) {
            return 0;
        }
        for (Entity old : level.getEntitiesOfClass(Entity.class,
                new net.minecraft.world.phys.AABB(pos).inflate(24.0D),
                entity -> entity.getType() == type)) {
            old.discard();
        }
        return ensureOne(level, placement) ? 1 : 0;
    }

    /** 打印一遍登记表（诊断用）。 */
    public String describe() {
        if (placements.isEmpty()) {
            return "(none)";
        }
        StringBuilder sb = new StringBuilder();
        placements.values().forEach(p -> sb.append(p.npcId()).append('@').append(p.anchor())
                .append(p.dx() >= 0 ? "+" : "").append(p.dx()).append('/')
                .append(p.dy() >= 0 ? "+" : "").append(p.dy()).append('/')
                .append(p.dz() >= 0 ? "+" : "").append(p.dz()).append(' '));
        return sb.toString().trim();
    }

    /** 主世界（NPC 都放主世界；天空岛也在主世界）。 */
    public static ServerLevel overworldOf(net.minecraft.server.MinecraftServer server) {
        return server.getLevel(Level.OVERWORLD);
    }
}
