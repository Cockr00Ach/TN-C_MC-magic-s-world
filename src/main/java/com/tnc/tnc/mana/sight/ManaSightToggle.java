package com.tnc.tnc.mana.sight;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;
public record ManaSightToggle(boolean enabled){
 public void encode(FriendlyByteBuf b){b.writeBoolean(enabled);}public static ManaSightToggle decode(FriendlyByteBuf b){return new ManaSightToggle(b.readBoolean());}
 public static void handle(ManaSightToggle p,java.util.function.Supplier<NetworkEvent.Context> c){var sender=c.get().getSender();if(sender!=null)c.get().enqueueWork(()->ManaSightServer.subscribe(sender,p.enabled));c.get().setPacketHandled(true);}
}
