package com.tnc.tnc.network;

import com.tnc.tnc.TNMod;
import com.tnc.tnc.magic.MagicStone;
import com.tnc.tnc.magic.MagicStoneData;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.function.Supplier;

/**
 * 魔法石数据的网络同步。
 *
 * <p>数据本身是服务端权威的；客户端那一份只是给 GUI / HUD 读的镜像，
 * 所以现在只有一个方向：服务端 → 客户端（整包同步，数据量很小，不做增量）。
 */
public class MagicStoneNetwork {

    // v3 adds downed/rescue packets. Reject stale clients instead of silently losing combat state.
    private static final String PROTOCOL_VERSION = "6";

    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            ResourceLocation.fromNamespaceAndPath(TNMod.MODID, "main"),
            () -> PROTOCOL_VERSION,
            PROTOCOL_VERSION::equals,
            PROTOCOL_VERSION::equals);

    public static void register() {
        CHANNEL.messageBuilder(com.tnc.tnc.tavern.TavernRoomPacket.class,203,NetworkDirection.PLAY_TO_CLIENT)
                .encoder(com.tnc.tnc.tavern.TavernRoomPacket::encode).decoder(com.tnc.tnc.tavern.TavernRoomPacket::decode)
                .consumerMainThread(com.tnc.tnc.tavern.TavernRoomPacket::handle).add();
        int id = 0;
        CHANNEL.messageBuilder(SyncMagicStone.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(SyncMagicStone::encode)
                .decoder(SyncMagicStone::decode)
                .consumerMainThread(SyncMagicStone::handle)
                .add();

        CHANNEL.messageBuilder(MagicStoneActionPacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(MagicStoneActionPacket::encode)
                .decoder(MagicStoneActionPacket::decode)
                .consumerMainThread(MagicStoneActionPacket::handle)
                .add();

        // 对话系统的两个包挂在**同一条通道**上（包 id 从 100 起，避开上面的 0/1）。
        // ⚠️ 绝不能另建一条同名通道：Forge 的 newSimpleChannel 对同名会抛
        //    "NetworkDirection Channel {tnc:main} already registered"，
        //    而那个异常发生在 mod 构造期 → 整个 mod 变 broken → 启动失败。
        //    详见 com.tnc.tnc.dialogue.DialogueNetwork 的类注释。
        com.tnc.tnc.dialogue.DialogueNetwork.registerPackets(CHANNEL, 100);
        com.tnc.tnc.adventure.AdventurePackets.register(CHANNEL);
        CHANNEL.messageBuilder(com.tnc.tnc.life.OpenTravelBagPacket.class,302,NetworkDirection.PLAY_TO_SERVER)
                .encoder(com.tnc.tnc.life.OpenTravelBagPacket::encode).decoder(com.tnc.tnc.life.OpenTravelBagPacket::decode)
                .consumerMainThread(com.tnc.tnc.life.OpenTravelBagPacket::handle).add();
        CHANNEL.messageBuilder(PortalRitualPacket.class, 200, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(PortalRitualPacket::encode)
                .decoder(PortalRitualPacket::decode)
                .consumerMainThread(PortalRitualPacket::handle)
                .add();
        CHANNEL.messageBuilder(CombatStatePacket.class,201,NetworkDirection.PLAY_TO_CLIENT)
                .encoder(CombatStatePacket::encode).decoder(CombatStatePacket::decode)
                .consumerMainThread(CombatStatePacket::handle).add();
        CHANNEL.messageBuilder(RescueInputPacket.class,202,NetworkDirection.PLAY_TO_SERVER)
                .encoder(RescueInputPacket::encode).decoder(RescueInputPacket::decode)
                .consumerMainThread(RescueInputPacket::handle).add();
    }

    // ---------------- 客户端调用的小工具 ----------------

    /** 请求一次最新数据（打开魔法石界面时用）。 */
    public static void requestSync() {
        CHANNEL.sendToServer(new MagicStoneActionPacket(MagicStoneActionPacket.Action.REQUEST_SYNC, ""));
    }

    /** 请求解锁一个法术。 */
    public static void requestUnlock(ResourceLocation spell) {
        CHANNEL.sendToServer(new MagicStoneActionPacket(MagicStoneActionPacket.Action.UNLOCK, spell.toString()));
    }

    /** 切到下一页热键（页号由服务端算：它有权威数据）。 */
    public static void requestNextPage() {
        CHANNEL.sendToServer(new MagicStoneActionPacket(MagicStoneActionPacket.Action.SET_PAGE, ""));
    }

    /** 把一个法术配到第 slot 个槽（0..17）。 */
    public static void requestSetSlot(int slot, ResourceLocation spell) {
        CHANNEL.sendToServer(new MagicStoneActionPacket(
                MagicStoneActionPacket.Action.SET_SLOT, spell == null ? "" : spell.toString(), slot));
    }

    /** 清空第 slot 个槽。 */
    public static void requestClearSlot(int slot) {
        CHANNEL.sendToServer(new MagicStoneActionPacket(
                MagicStoneActionPacket.Action.CLEAR_SLOT, "", slot));
    }

    /** 把某个玩家的魔法石数据推给他自己。 */
    public static void syncTo(ServerPlayer player) {
        MagicStone.get(player).ifPresent(data ->
                CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new SyncMagicStone(data)));
    }

    /** 服务端 → 客户端的整包数据。 */
    public static class SyncMagicStone {

        private CompoundTag tag;

        public SyncMagicStone() {
        }

        public SyncMagicStone(MagicStoneData data) {
            this.tag = data.serializeNBT();
        }

        public void encode(net.minecraft.network.FriendlyByteBuf buf) {
            buf.writeNbt(tag);
        }

        public static SyncMagicStone decode(net.minecraft.network.FriendlyByteBuf buf) {
            SyncMagicStone packet = new SyncMagicStone();
            packet.tag = buf.readNbt();
            return packet;
        }

        public void handle(Supplier<NetworkEvent.Context> contextSupplier) {
            NetworkEvent.Context context = contextSupplier.get();
            CompoundTag payload = this.tag;
            // 这个包只会发往客户端，所以这里引用客户端类名是安全的（类只在客户端被加载）
            context.enqueueWork(() -> MagicStoneClientSync.accept(payload));
            context.setPacketHandled(true);
        }
    }
}
