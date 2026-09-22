package com.tnc.tnc.npc;

import java.util.List;

/**
 * 固定 NPC 的**默认登记** —— 每个新存档自动采用，玩家不需要手敲命令。
 *
 * <h2>为什么要有它</h2>
 * 登记数据是**按存档**存的（{@code <存档>/data/tnc_npc_placements.dat}），
 * 所以第一版的行为是"每开一个新档都要手动 {@code /tnc npc here self} 一次" ——
 * 那显然不对：剧情 NPC 的位置是**设定**，不该由玩家来登记。
 * 现在每个存档在第一次加载时会把下面这份默认值**补进去**（已存在的不覆盖）。
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
     * <p>{@code self} = A. Self（酒馆老板），站在岛中心往南 44 格。
     * 剧情上这对应第一场「第二杯酒」——他就在玩家上岛后走几步的地方。
     */
    public static final List<NpcPlacementSavedData.Placement> DEFAULTS = List.of(
            new NpcPlacementSavedData.Placement("self", "CENTER", -1, -1, 44)
    );

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
