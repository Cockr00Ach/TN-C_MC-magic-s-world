package com.tnc.tnc.warrior;

import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;
import com.tnc.tnc.network.MagicStoneNetwork;

/** The client chooses an action; qualification, progress and rewards remain server-owned. */
public record TemperingActionPacket(Action action) {
    public enum Action { VIEW, START, CANCEL }
    public static void send(Action action) { MagicStoneNetwork.CHANNEL.sendToServer(new TemperingActionPacket(action)); }
    public void encode(FriendlyByteBuf buffer) { buffer.writeEnum(action); }
    public static TemperingActionPacket decode(FriendlyByteBuf buffer) { return new TemperingActionPacket(buffer.readEnum(Action.class)); }
    public void handle(Supplier<NetworkEvent.Context> supplier) {
        var ctx=supplier.get();var player=ctx.getSender();
        if(player!=null)ctx.enqueueWork(()->{
            switch(action) {
                case VIEW -> TemperingEvents.sync(player,true);
                case START -> {
                    if(!TemperingTraining.start(player))player.sendSystemMessage(net.minecraft.network.chat.Component.literal("当前无法开始训练：需要战士天赋、生存模式且未倒地。"));
                    TemperingEvents.sync(player);
                }
                case CANCEL -> TemperingTraining.cancel(player);
            }
        });
        ctx.setPacketHandled(true);
    }
}
