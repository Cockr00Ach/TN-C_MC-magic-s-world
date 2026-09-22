package com.tnc.tnc.npc;

import java.util.List;

/**
 * 固定 NPC 的**默认登记** —— 每个新存档自动采用，玩家不需要手敲命令。
 *
 * <h2>为什么要有它</h2>
 * 登记数据是**按存档**存的（{@code <存档>/data/tnc_npc_placements.dat}），
 * 所以第一版的行为是"每开一个新档都要手动 {@code /tnc npc here self} 一次" ——
 * 那显然不对：剧情 NPC 的位置是**设定**，不该由玩家来登记。
 * 现在每个存档在第一次加载时会把下面这份默认值**补进去**；改了默认值后把
 * {@link #VERSION} +1，已经玩过的存档也会在下次启动时跟着刷新。
 *
 * <h2>怎么加一个 NPC</h2>
 * 往 {@link #DEFAULTS} 里加一行即可。偏移是**相对天空岛锚点**的，所以换存档也正确。
 * 想知道某个位置该填什么：在游戏里站到位置跑 {@code /tnc npc here <id>}，
 * 然后 {@code /tnc npc list} 会打出锚点与偏移，抄进这里。
 *
 * <h2>当前值怎么来的</h2>
 * {@code self} 的 {@code CENTER (-1, -1, 44)} 是用户在存档
 * 「新的世界 (3)」实测**看得见**的位置（世界坐标 -5/179/-772），已核实没有被落点修正挪动。
 */
public final class NpcPlacementDefaults {

    /**
     * 默认登记表。
     *
     * <p><b>坐标是相对天空岛 CENTER 的偏移</b>（天空岛每存档位置不同，所以不能写死世界坐标）。
     *
     * <p>{@code self} —— 用户在存档「新的世界 (3)」实测**看得见**的位置。
     * <br>{@code cava} / {@code huai} —— 用户 2026-09-22 给出的精确位置，换算自存档「新的世界 (4)」：
     * <pre>
     *   该存档 CENTER = (-20, 180, -768)
     *   cava 要站 (-5, 180, -783)  ->  偏移 (+15,  0, -15)
     *   槐   要站 (-8, 180, -781)  ->  偏移 (+12,  0, -13)
     * </pre>
     * 三者都在 CENTER 同一层（dy=0），只差水平位置。
     */
    public static final List<NpcPlacementSavedData.Placement> DEFAULTS = List.of(
            new NpcPlacementSavedData.Placement("self", "CENTER", -1, -1, 44),
            new NpcPlacementSavedData.Placement("cava", "CENTER", 15, 0, -15),
            new NpcPlacementSavedData.Placement("huai", "CENTER", 12, 0, -13),
            // 庄鹊让（卷五《代》）—— 作者 2026-09-22：「放在 self 旁边先」。
            // self 在 CENTER +5/-1/+44；她放**同一层、往岛内退 2 格**（+5/-1/+42），
            // 与 self 相距约 2.2 格 —— 横向错开是不想两个人叠在一起。
            // 位置仍是**暂定** ✗：定位置跑 /tnc npc here zhuangquerang，再把 /tnc npc list 那行抄回来。
            new NpcPlacementSavedData.Placement("zhuangquerang", "CENTER", 5, -1, 42)
    );

    /**
     * 默认表的**版本号** —— 每次改动 {@link #DEFAULTS} 都要 +1。
     *
     * <h2>为什么需要它</h2>
     * {@code seedDefaults()} 原本是"缺什么补什么、已存在的不覆盖"。那设计是为了保护
     * GM 用 {@code /tnc npc here} 调过的位置，但有个副作用：
     * <b>我改了默认值之后，已经玩过的存档不会跟着更新</b> —— 每次都得手敲命令。
     *
     * <p>所以存档里记下"上次采用过哪一版默认值"；版本对不上时，
     * <b>用新默认值覆盖同名条目</b>（"以代码里的默认表为准"——它才是设定）。
     *
     * <p><b>因此在存档里手动调位置是临时的</b>：只要之后 VERSION +1，就会被顶回去。
     * 想让手动结果留下来，就把 {@code /tnc npc list} 打出的那行抄回 {@link #DEFAULTS} 再 +1。
     *
     * <h2>版本沿革</h2>
     * <ul>
     *   <li>1 —— 只有 self；</li>
     *   <li>2 —— cava / 槐 按作者给的精确坐标（CENTER +15,0,-15 / +12,0,-13）；</li>
     *   <li>3 —— 加庄鹊让；</li>
     *   <li>4 —— 庄鹊让从"贴着 huai"挪到"贴着 self"（作者要求）。</li>
     * </ul>
     */
    public static final int VERSION = 4;

    private NpcPlacementDefaults() {
    }

    /** 调试/诊断用：把默认值打成一行行文字，方便和存档里的登记对照。 */
    public static String describe() {
        StringBuilder sb = new StringBuilder();
        for (NpcPlacementSavedData.Placement p : DEFAULTS) {
            sb.append(p.npcId()).append('@').append(p.anchor()).append(' ')
                    .append(p.dx()).append('/').append(p.dy()).append('/').append(p.dz()).append("  ");
        }
        return sb.toString().trim();
    }
}
