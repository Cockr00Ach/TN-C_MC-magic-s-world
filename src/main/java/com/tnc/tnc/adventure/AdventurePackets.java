package com.tnc.tnc.adventure;

import com.tnc.tnc.network.MagicStoneNetwork;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.*;
import net.minecraftforge.network.simple.SimpleChannel;
import java.util.function.Supplier;

public final class AdventurePackets {
    public enum Action {REQUEST,REGISTER,ACCEPT,DELIVER,ORDER,CLAIM,PROMOTE,BUY_HOME}
    public record Request(Action action,String id) {
        void encode(FriendlyByteBuf b){b.writeEnum(action);b.writeUtf(id,64);}
        static Request decode(FriendlyByteBuf b){return new Request(b.readEnum(Action.class),b.readUtf(64));}
        void handle(Supplier<NetworkEvent.Context> supplier) {
            var ctx=supplier.get();ctx.enqueueWork(()->{var player=ctx.getSender();if(player!=null)AdventureService.action(player,action,id);});ctx.setPacketHandled(true);
        }
    }
    public record Snapshot(CompoundTag tag,boolean open) {
        void encode(FriendlyByteBuf b){b.writeNbt(tag);b.writeBoolean(open);}
        static Snapshot decode(FriendlyByteBuf b){return new Snapshot(b.readNbt(),b.readBoolean());}
        void handle(Supplier<NetworkEvent.Context> supplier){var ctx=supplier.get();ctx.enqueueWork(()->com.tnc.tnc.adventure.client.AdventureScreen.accept(tag,open));ctx.setPacketHandled(true);}
    }
    public static void register(SimpleChannel channel) {
        channel.messageBuilder(Request.class,300,NetworkDirection.PLAY_TO_SERVER).encoder(Request::encode).decoder(Request::decode).consumerMainThread(Request::handle).add();
        channel.messageBuilder(Snapshot.class,301,NetworkDirection.PLAY_TO_CLIENT).encoder(Snapshot::encode).decoder(Snapshot::decode).consumerMainThread(Snapshot::handle).add();
    }
    public static void send(Action action,String id){MagicStoneNetwork.CHANNEL.sendToServer(new Request(action,id));}
}
