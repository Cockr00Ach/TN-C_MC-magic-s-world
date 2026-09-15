package com.tnc.tnc.network;

import com.tnc.tnc.magic.MagicStone;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.nbt.CompoundTag;

/**
 * 只在客户端加载：把服务端推来的魔法石数据写进本地玩家的 capability。
 *
 * <p>单独一个类是为了不让 {@link net.minecraft.client.Minecraft} 在专用服务器上被加载。
 */
public final class MagicStoneClientSync {

    private MagicStoneClientSync() {
    }

    public static void accept(CompoundTag tag) {
        if (tag == null) {
            return;
        }
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) {
            return;
        }
        MagicStone.get(player).ifPresent(data -> {
            data.deserializeNBT(tag);
            // 界面开着就让它按新数据重排（解锁成功后按钮要消失）
            if (Minecraft.getInstance().screen instanceof com.tnc.tnc.client.MagicStoneScreen screen) {
                screen.refreshFromServer();
            }
        });
    }
}
