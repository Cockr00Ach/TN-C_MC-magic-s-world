package com.tnc.tnc.dialogue;

import com.tnc.tnc.TNMod;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import java.util.HashSet;
import java.util.Set;

/**
 * "这条剧本这个玩家看过了没" —— 存在玩家自己的持久化数据里（{@code PlayerPersisted}）。
 *
 * <p>为什么用 {@code PlayerPersisted} 而不是 Capability：这点数据太小，不值得为它写一套
 * Capability + Provider；而 {@code PlayerPersisted} 是原版给的"跟着玩家走"的口袋
 * （死亡不掉、换维度不丢），且**自动跨存档/跨模组共存**。
 *
 * <p>以后如果对话需要更复杂的状态（做到第几句、选了哪个分支），
 * 就把这里升级成 Capability —— 接口（{@code hasSeen} / {@code markSeen}）不用变。
 */
public final class DialogueProgress {

    private static final String ROOT = "tnc_dialogues_seen";

    private DialogueProgress() {
    }

    public static boolean hasSeen(ServerPlayer player, ResourceLocation script) {
        CompoundTag tag = player.getPersistentData().getCompound(ROOT);
        return tag.getBoolean(script.toString());
    }

    public static void markSeen(ServerPlayer player, ResourceLocation script) {
        CompoundTag data = player.getPersistentData();
        CompoundTag tag = data.getCompound(ROOT);
        tag.putBoolean(script.toString(), true);
        data.put(ROOT, tag);
    }

    /** 调试用：把看过的剧本名列出来（{@code /tnc dialogue list}）。 */
    public static Set<String> seen(ServerPlayer player) {
        CompoundTag tag = player.getPersistentData().getCompound(ROOT);
        Set<String> out = new HashSet<>();
        for (String key : tag.getAllKeys()) {
            if (tag.getBoolean(key)) {
                out.add(key);
            }
        }
        return out;
    }
}
