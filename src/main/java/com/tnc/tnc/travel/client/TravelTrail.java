package com.tnc.tnc.travel.client;

import com.tnc.tnc.TNMod;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 附身形态的<b>运动残影</b>：每个 tick 记一个身体位置，渲染时沿这条历史画拖尾。
 *
 * <p>为什么不在渲染里现算：拖尾要的是"我<b>刚刚</b>在哪"，而不是"我现在朝哪"。
 * 只按速度画一条线，站着不动就没有、急转弯会瞬间折断 ✗；沿历史点画出来的
 * 才是一道跟着人甩出去的尾巴 ✓。
 *
 * <p>采样在 {@link TickEvent.ClientTickEvent} 的 END（每 tick 一次，20/s），
 * 每个实体最多 {@value #SAMPLES} 个点 ≈ 1.1 秒的记忆。退出附身后残影不立刻消失，
 * 而是再淡出 {@value #FADE_TICKS} tick —— 不然尾巴会"啪"一下被剪掉 ✗。
 *
 * <p>入场爆发也在这里判定：元素从"没有"变成"有"的那一 tick 记下来，
 * {@link TravelFormRenderer} 用它画一圈扩散光环。
 */
@Mod.EventBusSubscriber(modid = TNMod.MODID, value = Dist.CLIENT)
public final class TravelTrail {

    /** 每个实体保留的历史点数（≈ 1.1 秒 @20tps）。 */
    public static final int SAMPLES = 22;

    /** 入场爆发的时长（tick）。 */
    public static final int BURST_TICKS = 12;

    /** 退出附身后残影继续淡出的 tick 数。 */
    public static final int FADE_TICKS = 8;

    /** 一条残影：历史点 ＋ 它属于哪个元素 ＋ 退出后过了多久。 */
    public static final class Tail {
        public final List<Vec3> points = new ArrayList<>(SAMPLES);
        public int element = -1;
        /** 距离"最后一次还在附身"过了多少 tick（退出后用来淡出）。 */
        public int age;
        public boolean active;
    }

    private static final Map<Integer, Tail> TAILS = new HashMap<>();
    private static final Map<Integer, Integer> BURST_START = new HashMap<>();

    private TravelTrail() {
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        if (level == null) {
            clear();
            return;
        }
        int now = minecraft.player == null ? 0 : minecraft.player.tickCount;
        Set<Integer> alive = new HashSet<>();
        for (Player player : level.players()) {
            int element = TravelAppearance.elementOf(player);
            int id = player.getId();
            Tail tail = TAILS.get(id);
            if (element < 0) {
                if (tail == null) {
                    continue;
                }
                alive.add(id);
                tail.active = false;
                if (++tail.age > FADE_TICKS) {
                    TAILS.remove(id);
                    BURST_START.remove(id);
                }
                continue;
            }
            alive.add(id);
            if (tail == null) {
                tail = new Tail();
                TAILS.put(id, tail);
            }
            if (tail.element != element) {
                tail.points.clear();     // 换形态：旧尾巴不能拉过来
                BURST_START.put(id, now);
            }
            tail.element = element;
            tail.active = true;
            tail.age = 0;
            Vec3 at = player.position().add(0.0D, 1.0D, 0.0D);
            if (tail.points.isEmpty()
                    || tail.points.get(tail.points.size() - 1).distanceToSqr(at) > 1.0E-4D) {
                tail.points.add(at);
                while (tail.points.size() > SAMPLES) {
                    tail.points.remove(0);
                }
            }
        }
        TAILS.keySet().retainAll(alive);
        BURST_START.keySet().retainAll(alive);
    }

    /** 某个实体的残影（可能正在淡出）；没有就返回 {@code null}。 */
    public static Tail tail(int entityId) {
        return TAILS.get(entityId);
    }

    /** 入场爆发已经过去多少 tick；没在爆发期返回 {@code Integer.MAX_VALUE}。 */
    public static int burstAge(int entityId, int now) {
        Integer start = BURST_START.get(entityId);
        return start == null ? Integer.MAX_VALUE : now - start;
    }

    public static void clear() {
        TAILS.clear();
        BURST_START.clear();
    }
}
