package com.tnc.tnc.network;

import com.tnc.tnc.magic.MagicStone;
import com.tnc.tnc.magic.MagicStoneData;
import com.tnc.tnc.magic.MagicStoneLearning;
import com.tnc.tnc.magic.SpellCatalog;
import com.tnc.tnc.magic.compat.SpellEngineBridge;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * 客户端 → 服务端：魔法石界面的动作。
 *
 * <p>现在只有两件事：拉一次最新数据、解锁一个法术。
 * 以后加"遗忘/重置/切页"也走这个包（加一个枚举值即可）。
 */
public class MagicStoneActionPacket {

    public enum Action {
        /** 打开界面时拉一次最新数据（服务端权威，客户端只是镜像）。 */
        REQUEST_SYNC,
        /** 花魔法点数解锁一个法术。 */
        UNLOCK
    }

    private Action action;
    private String spellId;

    public MagicStoneActionPacket() {
    }

    public MagicStoneActionPacket(Action action, String spellId) {
        this.action = action;
        this.spellId = spellId == null ? "" : spellId;
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeEnum(action);
        buf.writeUtf(spellId);
    }

    public static MagicStoneActionPacket decode(FriendlyByteBuf buf) {
        return new MagicStoneActionPacket(buf.readEnum(Action.class), buf.readUtf());
    }

    public void handle(Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player == null) {
                return;
            }
            switch (action) {
                case REQUEST_SYNC -> MagicStoneNetwork.syncTo(player);
                case UNLOCK -> handleUnlock(player);
            }
        });
        context.setPacketHandled(true);
    }

    private void handleUnlock(ServerPlayer player) {
        ResourceLocation id = ResourceLocation.tryParse(spellId);
        if (id == null) {
            return;
        }
        SpellCatalog.Entry entry = SpellCatalog.byId(id);
        if (entry == null) {
            player.sendSystemMessage(Component.literal("§c[TN-C] 未知法术：" + id));
            return;
        }
        MagicStoneData data = MagicStone.getOrNull(player);
        if (data == null) {
            return;
        }
        MagicStoneLearning.Result result = MagicStoneLearning.unlock(data, entry);
        player.sendSystemMessage(MagicStoneLearning.describe(result, entry, data));
        if (result == MagicStoneLearning.Result.OK || result == MagicStoneLearning.Result.ALREADY_LEARNED) {
            // 解锁成功 = 把"已解锁集合"同步进法杖（玩家看不到卷轴/法术书/注册台）。
            // 魔法石是权威数据，法杖只是它的一个投影。
            // ⚠️ 用 effectiveIds：每条链只挂最高级（学了高阶就把低阶顶下去）
            java.util.List<ResourceLocation> effective =
                    com.tnc.tnc.magic.SpellCatalog.effectiveIds(data);
            SpellEngineBridge.WandResult synced =
                    SpellEngineBridge.ensureWand(player, effective);
            player.sendSystemMessage(Component.literal("§7[TN-C] 法杖："
                    + SpellEngineBridge.describeWand(synced, effective.size())));
        }
        // 无论成功失败都同步一次：成功要刷新点数/已学，失败也能让界面显示最新状态
        MagicStoneNetwork.syncTo(player);
    }
}
