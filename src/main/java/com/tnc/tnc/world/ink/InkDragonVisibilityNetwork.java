package com.tnc.tnc.world.ink;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.network.*;
import java.util.*;

@Mod.EventBusSubscriber(modid="tnc",bus=Mod.EventBusSubscriber.Bus.MOD)
public final class InkDragonVisibilityNetwork {
    public static final net.minecraftforge.network.simple.SimpleChannel CHANNEL=NetworkRegistry.newSimpleChannel(
            ResourceLocation.fromNamespaceAndPath("tnc","ink_dragon_visibility"),()->"2","2"::equals,"2"::equals);
    public record SnapshotPacket(ResourceLocation dimension,List<InkDragonVisibility.Snapshot> dragons){}
    @SubscribeEvent public static void setup(FMLCommonSetupEvent event){
        CHANNEL.registerMessage(0,SnapshotPacket.class,InkDragonVisibilityNetwork::encode,InkDragonVisibilityNetwork::decode,
                (packet,context)->{var ctx=context.get();ctx.enqueueWork(()->DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                        ()->()->com.tnc.tnc.world.ink.client.InkDragonDistantView.accept(packet)));ctx.setPacketHandled(true);},
                Optional.of(NetworkDirection.PLAY_TO_CLIENT));
    }
    private static void encode(SnapshotPacket packet,FriendlyByteBuf b){
        b.writeResourceLocation(packet.dimension());b.writeVarInt(packet.dragons().size());
        for(var s:packet.dragons()){b.writeUUID(s.id());b.writeDouble(s.position().x);b.writeDouble(s.position().y);b.writeDouble(s.position().z);b.writeFloat(s.yaw());b.writeNbt(s.state());}
    }
    private static SnapshotPacket decode(FriendlyByteBuf b){
        var dim=b.readResourceLocation();int count=b.readVarInt();
        if(count<0||count>InkDragonVisibility.MAX_SENT)throw new IllegalArgumentException("Invalid dragon snapshot count");
        var list=new ArrayList<InkDragonVisibility.Snapshot>();
        for(int i=0;i<count;i++){
            var id=b.readUUID();var at=new net.minecraft.world.phys.Vec3(b.readDouble(),b.readDouble(),b.readDouble());float yaw=b.readFloat();
            if(!Double.isFinite(at.x)||!Double.isFinite(at.y)||!Double.isFinite(at.z)||!Float.isFinite(yaw))throw new IllegalArgumentException("Invalid dragon position");
            var state=b.readNbt();if(state==null)throw new IllegalArgumentException("Missing dragon state");
            list.add(new InkDragonVisibility.Snapshot(id,at,yaw,state));
        }
        return new SnapshotPacket(dim,List.copyOf(list));
    }
}
