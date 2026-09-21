package com.tnc.tnc.npc;

import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import org.slf4j.Logger;

/**
 * 天空岛坐标的只读查询 —— 让固定 NPC 能"跟着天空岛走"。
 *
 * <h2>为什么需要它</h2>
 * 天空岛是**按存档生成的**：每个存档里它的位置都不一样（生成时先在出生点附近选点）。
 * 所以剧情 NPC 的位置<b>不能写死世界坐标</b>，否则换个存档就跑到虚空里去了。
 * 正确做法是存"相对天空岛某个锚点的偏移"，放置时按该存档的真实坐标算出来。
 *
 * <h2>怎么读到的（改过一次，这里记录教训）</h2>
 * 第一版我绕路去读原始 NBT（{@code DimensionDataStorage.readTagFromDisk("tnc_sky_island_v5")}），
 * 想避开"字段是包级可见"的问题。**实测不可靠**：运行时既没成功、也没走进我的 catch
 * （连日志都没有），代价是用户白测一轮。
 * ⇒ 改成**请天空岛那边开一个公开只读方法**（{@code SkyIslandSavedData.anchorPos(String)} +
 * {@code isSkyIslandComplete()}）。那是类型安全的，而且一眼能看懂。
 *
 * <p><b>教训</b>：跨模块取数据时，优先加一个公开访问器；绕底层格式（NBT/反射）看着"不改别人代码"，
 * 实际上更脆、更难查。
 */
public final class SkyIslandAnchors {

    private static final Logger LOGGER = LogUtils.getLogger();

    private SkyIslandAnchors() {
    }

    /** 可用的锚点。名字会写进存档，**不要随便改名**（改了老存档的登记会失效）。 */
    public enum Anchor {
        /** 南门落点（玩家传送上岛的位置）—— 剧情 NPC 的默认落脚点。 */
        ARRIVAL,
        /** 岛中心。 */
        CENTER,
        /** 岛的原点（最低根尖）。 */
        ORIGIN,
        /** 地面传送阵（主世界进岛的入口）。 */
        GROUND_PORTAL
    }

    /**
     * 取该存档里某个锚点的坐标。
     *
     * @return 坐标；天空岛尚未生成完成时返回 {@code null} —— 调用方必须处理
     *         （我们的做法是：这次不补位，等下次 5 秒自检）
     */
    public static BlockPos resolve(ServerLevel level, Anchor anchor) {
        return com.tnc.tnc.world.SkyIslandSavedData.get(level)
                .anchorPos(anchor.name());
    }

    /** 天空岛是否已生成完成 —— 没完成就别放 NPC，免得放到半成品上（会掉虚空）。 */
    public static boolean isComplete(ServerLevel level) {
        return com.tnc.tnc.world.SkyIslandSavedData.get(level).isSkyIslandComplete();
    }
}
