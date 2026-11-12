package com.tnc.tnc.travel;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * 客户端 → 服务端：元素附身的意图。
 *
 * <p>包里只有“我想做什么”，没有坐标、没有形态阶位、没有伤害。服务端用主手物品、
 * 魔法石数据、位置与冷却自己算；客户端说“我进了附身”不等于真的进了。
 */
public record TravelInputPacket(int action) {

    /** 没有动作。短按 Q 不再触发任何位移。 */
    public static final int NONE = 0;
    public static final int ENTER = 1;
    public static final int SKILL = 2;
    public static final int KEEPALIVE = 3;
    public static final int EXIT = 4;

    public static void send(int action) {
        TravelNetwork.CHANNEL.sendToServer(new TravelInputPacket(clamp(action)));
    }

    public static int clamp(int action) {
        return action < ENTER || action > EXIT ? NONE : action;
    }

    public void encode(FriendlyByteBuf buffer) {
        buffer.writeByte(action & 0xFF);
    }

    public static TravelInputPacket decode(FriendlyByteBuf buffer) {
        return new TravelInputPacket(clamp(buffer.readUnsignedByte()));
    }

    public void handle(Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        var player = context.getSender();
        if (player != null) {
            context.enqueueWork(() -> TravelFormService.input(player, action));
        }
        context.setPacketHandled(true);
    }
}
