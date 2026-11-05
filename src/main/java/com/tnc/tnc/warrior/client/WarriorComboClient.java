package com.tnc.tnc.warrior.client;

import com.tnc.tnc.TNMod;
import com.tnc.tnc.warrior.ComboTracker;
import com.tnc.tnc.warrior.WarriorComboSyncPacket;
import com.tnc.tnc.warrior.WarriorCombos;
import com.tnc.tnc.warrior.WarriorInput;
import com.tnc.tnc.warrior.WarriorMove;
import com.tnc.tnc.warrior.WarriorSkill;
import com.tnc.tnc.warrior.WarriorWeaponFamily;
import com.tnc.tnc.warrior.WarriorWeapons;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 连招镜像（纯客户端）：只读服务端发来的权威快照。
 *
 * <p>HUD 不自己判定连招 —— 客户端本地算出来的进度一旦和服务端不一致，
 * 玩家就会遇到"界面显示连上了、实际没放出来"。这里只做两件事：把服务端给的
 * 缓冲区翻译成"正在接哪招、下一步按什么"，以及把剩余冷却按 tick 递减。
 */
@Mod.EventBusSubscriber(modid = TNMod.MODID, value = Dist.CLIENT)
public final class WarriorComboClient {

    private static byte[] buffer = new byte[0];
    private static int[] cooldowns = new int[0];
    private static int flashed = -1;
    private static long receivedAt;
    private static long flashAt = -10_000;
    private static long ticks;

    private WarriorComboClient() {
    }

    @SubscribeEvent
    public static void tick(TickEvent.ClientTickEvent event) {
        var minecraft = Minecraft.getInstance();
        if (event.phase == TickEvent.Phase.END && minecraft.level != null && !minecraft.isPaused()) {
            ticks++;
        }
    }

    public static void accept(WarriorComboSyncPacket packet) {
        buffer = packet.buffer() == null ? new byte[0] : packet.buffer().clone();
        cooldowns = packet.cooldowns() == null ? new int[0] : packet.cooldowns().clone();
        flashed = packet.flashed();
        receivedAt = ticks;
        if (flashed >= 0 && flashed < WarriorCombos.all().size()) {
            flashAt = ticks;
        }
    }

    @SubscribeEvent
    public static void logout(ClientPlayerNetworkEvent.LoggingOut event) {
        reset();
    }

    @SubscribeEvent
    public static void clone(ClientPlayerNetworkEvent.Clone event) {
        // 重生/换维度时客户端会换一个 LocalPlayer，旧缓冲不能跟着过去
        reset();
    }

    public static void reset() {
        buffer = new byte[0];
        cooldowns = new int[0];
        flashed = -1;
        receivedAt = 0;
        flashAt = -10_000;
        ticks = 0;
    }

    /** 缓冲区里的输入（旧 → 新，含长按标记）。 */
    public static List<WarriorInput> inputs() {
        List<WarriorInput> out = new ArrayList<>(buffer.length);
        for (byte value : buffer) {
            WarriorMove move = WarriorMove.byOrdinal(value & 0x7F);
            if (move != null) {
                out.add(new WarriorInput(move, (value & 0x80) != 0));
            }
        }
        return out;
    }

    /** 只要方向的兼容视图。 */
    public static List<WarriorMove> moves() {
        List<WarriorMove> out = new ArrayList<>(buffer.length);
        for (WarriorInput input : inputs()) {
            out.add(input.move());
        }
        return out;
    }

    /** 正在接的那一招；没有输入或已经超窗口返回 {@code null}。 */
    public static ComboTracker.Progress progress() {
        if (!active()) {
            return null;
        }
        WarriorWeaponFamily family = heldFamily();
        return ComboTracker.progressOfInputs(inputs(),
                family == null ? WarriorCombos.all() : WarriorCombos.forFamily(family).stream().map(WarriorComboClient::heldVariant).toList());
    }
    public static WarriorSkill heldVariant(WarriorSkill skill) {
        var player=Minecraft.getInstance().player;
        return player==null || skill==null?skill:com.tnc.tnc.warrior.WarriorPerformance.forWeapon(skill,player.getMainHandItem());
    }

    /** 服务端快照还"新鲜"吗（超过输入窗口就当作连招已断）。 */
    public static boolean active() {
        return buffer.length > 0 && ticks - receivedAt <= ComboTracker.WINDOW_TICKS;
    }

    /** 该连招剩余冷却（tick）。 */
    public static int cooldown(WarriorSkill skill) {
        int index = WarriorCombos.indexOf(skill);
        if (index < 0 || index >= cooldowns.length) {
            return 0;
        }
        int elapsed = (int) Math.min(Integer.MAX_VALUE, Math.max(0, ticks - receivedAt));
        return Math.max(0, cooldowns[index] - elapsed);
    }

    /** 刚放出来的一招，HUD 用它闪一下。 */
    public static boolean flashing(WarriorSkill skill) {
        int index = WarriorCombos.indexOf(skill);
        return index >= 0 && index == flashed && ticks - flashAt <= 10;
    }

    /** 手上武器的家族；空手/非武器返回 {@code null}。 */
    public static WarriorWeaponFamily heldFamily() {
        var player = Minecraft.getInstance().player;
        return player == null ? null : WarriorWeapons.family(player.getMainHandItem());
    }

    /** 手上是不是长剑（和"拿法杖才能施法"同一条规矩，保留给旧调用）。 */
    public static boolean holdingLongsword() {
        return heldFamily() == WarriorWeaponFamily.LONGSWORD;
    }
}
