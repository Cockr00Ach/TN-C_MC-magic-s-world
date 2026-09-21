package com.tnc.tnc.npc;

import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import org.slf4j.Logger;

import java.io.IOException;

/**
 * 天空岛坐标的只读查询 —— 让固定 NPC 能"跟着天空岛走"。
 *
 * <h2>为什么需要它</h2>
 * 天空岛是**按存档生成的**：每个存档里它的位置都不一样（见
 * {@code SkyIslandSavedData}，生成前先在出生点附近选点）。所以剧情 NPC 的位置
 * <b>不能写死世界坐标</b>，否则换个存档就跑到虚空里去了。
 * 正确做法是存"相对天空岛某个锚点的偏移"，放置时按该存档的真实坐标算出来。
 *
 * <h2>怎么读到的（不改别人的代码）</h2>
 * {@code SkyIslandSavedData} 的字段是**包级可见**（不在我们包里），所以走
 * {@link net.minecraft.world.level.storage.DimensionDataStorage#readTagFromDisk}
 * 直接读原始 NBT —— 那是<b>公开 API</b>，不用反射、也不用为了读两个坐标去改
 * 魔法专题维护的文件。键名与 SkyIslandSavedData.save() 里写的完全一致：
 * {@code CenterX/Y/Z}、{@code ArrivalX/Y/Z}、{@code OriginX/Y/Z}、{@code GroundPortalX/Y/Z}。
 */
public final class SkyIslandAnchors {

    private static final Logger LOGGER = LogUtils.getLogger();
    /** 与 SkyIslandSavedData.FILE_ID 一致（那个常量是包级的，只能在这里写一遍）。 */
    private static final String FILE_ID = "tnc_sky_island_v5";

    /**
     * 缓存：{@code COMPLETE} 之后这些坐标就不再变了，没必要每 5 秒读一次磁盘。
     * 用 volatile 因为读发生在服务端线程、也有可能在别的线程被清。
     */
    private static volatile CompoundTag cachedCompleteTag;

    private SkyIslandAnchors() {
    }

    /** 让缓存失效（服务器启动/资源重载时调用，保证换存档/重生成能读到新值）。 */
    public static void clearCache() {
        cachedCompleteTag = null;
    }

    /** 可用的锚点。名字会写进存档，**不要随便改名**（改了老存档的登记会失效）。 */
    public enum Anchor {
        /** 南门落点（玩家传送上岛的位置）—— 剧情 NPC 的默认落脚点。 */
        ARRIVAL("Arrival"),
        /** 岛中心。 */
        CENTER("Center"),
        /** 岛的原点（最低根尖）。 */
        ORIGIN("Origin"),
        /** 地面传送阵（主世界进岛的入口）。 */
        GROUND_PORTAL("GroundPortal");

        final String keyPrefix;

        Anchor(String keyPrefix) {
            this.keyPrefix = keyPrefix;
        }
    }

    /**
     * 取该存档里某个锚点的坐标。
     *
     * @return 坐标；天空岛还没生成（或数据缺失）时返回 {@code null} —— 
     *         调用方必须处理这一情况（我们的做法是：这次不补位，等下次检查）
     */
    public static BlockPos resolve(ServerLevel level, Anchor anchor) {
        CompoundTag tag = readTag(level);
        if (tag == null) {
            return null;
        }
        String p = anchor.keyPrefix;
        if (!tag.contains(p + "X")) {
            return null;
        }
        return new BlockPos(tag.getInt(p + "X"), tag.getInt(p + "Y"), tag.getInt(p + "Z"));
    }

    /** 天空岛是否已生成完成（COMPLETE）—— 没完成就别放 NPC，免得放到半成品上。 */
    public static boolean isComplete(ServerLevel level) {
        CompoundTag tag = readTag(level);
        return tag != null && "COMPLETE".equals(tag.getString("Phase"));
    }

    private static CompoundTag readTag(ServerLevel level) {
        CompoundTag done = cachedCompleteTag;
        if (done != null) {
            return done; // 岛已完成，坐标固定 —— 直接用缓存，不再碰磁盘
        }
        CompoundTag tag = readFromDisk(level);
        if (tag != null && "COMPLETE".equals(tag.getString("Phase"))) {
            cachedCompleteTag = tag;
            LOGGER.info("TN-C npc: sky island anchors cached (Center={}/{}/{}, Arrival={}/{}/{})",
                    tag.getInt("CenterX"), tag.getInt("CenterY"), tag.getInt("CenterZ"),
                    tag.getInt("ArrivalX"), tag.getInt("ArrivalY"), tag.getInt("ArrivalZ"));
        }
        return tag;
    }

    private static CompoundTag readFromDisk(ServerLevel level) {
        try {
            return level.getDataStorage().readTagFromDisk(FILE_ID,
                    net.minecraft.SharedConstants.getCurrentVersion().getDataVersion().getVersion());
        } catch (IOException e) {
            // 天空岛还没生成 → 这个文件不存在，是**正常情况**，不打 ERROR 免得误导
            LOGGER.debug("TN-C npc: sky island data not readable yet ({})", e.toString());
            return null;
        } catch (Exception e) {
            LOGGER.warn("TN-C npc: failed to read sky island data: {}", e.toString());
            return null;
        }
    }
}
