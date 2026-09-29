package com.tnc.tnc.life;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;
import java.util.function.Supplier;
public record OpenTravelBagPacket() {
    public void encode(FriendlyByteBuf b){}
    public static OpenTravelBagPacket decode(FriendlyByteBuf b){return new OpenTravelBagPacket();}
    public void handle(Supplier<NetworkEvent.Context> supplier){var ctx=supplier.get();ctx.enqueueWork(()->{if(ctx.getSender()!=null)LifeEvents.open(ctx.getSender());});ctx.setPacketHandled(true);}
}
