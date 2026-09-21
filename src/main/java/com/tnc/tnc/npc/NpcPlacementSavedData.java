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
 * <h2>它解决什么问题</h2>
 * 剧情 NPC（Self、cava、riggen…）的位置是**设定**，不是玩家刷出来的：
 * 他应该<b>永远在那儿</b>。所以：登记一次坐标 → 每次世界加载/周期性检查时，
 * 那个坐标上要是没有他，就<b>补一个</b>。
 *
 * <p>这样即使因为任何原因（被创造模式误杀、区块异常、旧的卸载 bug）他没了，
 * 也会自己回来，不需要人工再刷一次。
 *
 * <p>数据落在 {@code <存档>/data/tnc_npc_placements.dat}，跟着存档走。
 * 换存档就是另一份（每个世界的 NPC 位置独立），这符合"世界状态"的语义。
 */
public class NpcPlacementSavedData extends net.minecraft.world.level.saveddata.SavedData {

    private static final Logger LOGGER = LogUtils.getLogger();
    public static final String DATA_NAME = "tnc_npc_placements";

    /** npcId（如 {@code self}） -> 位置。 */
    private final Map<String, BlockPos> placements = new HashMap<>();

    public static NpcPlacementSavedData get(ServerLevel level) {
        return level.getServer().overworld().getDataStorage().computeIfAbsent(
                NpcPlacementSavedData::load, NpcPlacementSavedData::new, DATA_NAME);
    }

    public static NpcPlacementSavedData load(CompoundTag tag) {
        NpcPlacementSavedData data = new NpcPlacementSavedData();
        ListTag list = tag.getList("Npcs", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag entry = list.getCompound(i);
            String id = entry.getString("Id");
            if (id.isEmpty()) {
                continue;
            }
            data.placements.put(id, new BlockPos(entry.getInt("X"), entry.getInt("Y"), entry.getInt("Z")));
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag list = new ListTag();
        placements.forEach((id, pos) -> {
            CompoundTag entry = new CompoundTag();
            entry.putString("Id", id);
            entry.putInt("X", pos.getX());
            entry.putInt("Y", pos.getY());
            entry.putInt("Z", pos.getZ());
            list.add(entry);
        });
        tag.put("Npcs", list);
        return tag;
    }

    // ------------------------------------------------------------------ API

    public void put(String npcId, BlockPos pos) {
        placements.put(npcId, pos);
        this.setDirty();
    }

    public BlockPos get(String npcId) {
        return placements.get(npcId);
    }

    public Map<String, BlockPos> all() {
        return java.util.Collections.unmodifiableMap(placements);
    }

    public boolean remove(String npcId) {
        boolean removed = placements.remove(npcId) != null;
        if (removed) {
            this.setDirty();
        }
        return removed;
    }

    // ------------------------------------------------------------------ 补位

    /**
     * 检查所有登记的 NPC：位置在，但**实体不在** → 补一个。
     *
     * @return 本次补出来的数量（便于日志/命令回报）
     */
    public int ensureAll(ServerLevel overworld) {
        int spawned = 0;
        for (Map.Entry<String, BlockPos> e : placements.entrySet()) {
            if (ensureOne(overworld, e.getKey(), e.getValue())) {
                spawned++;
            }
        }
        return spawned;
    }

    /** 单个 NPC 的补位。返回 true = 这次真的补了一个。 */
    public boolean ensureOne(ServerLevel level, String npcId, BlockPos pos) {
        EntityType<?> type = ForgeRegistries.ENTITY_TYPES.getValue(
                ResourceLocation.fromNamespaceAndPath(com.tnc.tnc.TNMod.MODID, npcId));
        if (type == null) {
            LOGGER.error("TN-C npc: placement references unknown entity type tnc:{}", npcId);
            return false;
        }
        // 已经有一个活着的，就什么都不做
        boolean present = !level.getEntitiesOfClass(Entity.class,
                new net.minecraft.world.phys.AABB(pos).inflate(24.0D),
                entity -> entity.getType() == type).isEmpty();
        if (present) {
            return false;
        }
        Entity entity = type.create(level);
        if (entity == null) {
            LOGGER.error("TN-C npc: failed to create tnc:{}", npcId);
            return false;
        }
        // 放在方块正上方一点，避免卡进地里
        entity.moveTo(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D,
                level.random.nextFloat() * 360.0F, 0.0F);
        level.addFreshEntity(entity);
        LOGGER.info("TN-C npc: restored tnc:{} at {}", npcId, pos);
        return true;
    }

    /** 强制重新放置（先清掉附近同类的，再放一个）—— 给"我改坐标了"用。 */
    public int respawn(ServerLevel level, String npcId) {
        BlockPos pos = placements.get(npcId);
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
        return ensureOne(level, npcId, pos) ? 1 : 0;
    }

    /** 打印一遍登记表（诊断用）。 */
    public String describe() {
        if (placements.isEmpty()) {
            return "(none)";
        }
        StringBuilder sb = new StringBuilder();
        placements.forEach((id, pos) ->
                sb.append(id).append('@').append(pos.getX()).append('/')
                        .append(pos.getY()).append('/').append(pos.getZ()).append(' '));
        return sb.toString().trim();
    }

    /** 主世界（NPC 都放主世界；天空岛也在主世界）。 */
    public static ServerLevel overworldOf(net.minecraft.server.MinecraftServer server) {
        return server.getLevel(Level.OVERWORLD);
    }
}
