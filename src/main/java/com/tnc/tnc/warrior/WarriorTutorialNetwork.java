package com.tnc.tnc.warrior;

import java.util.Optional;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.network.*;

@Mod.EventBusSubscriber(modid="tnc",bus=Mod.EventBusSubscriber.Bus.MOD)
public final class WarriorTutorialNetwork {
    private static final net.minecraftforge.network.simple.SimpleChannel CHANNEL=NetworkRegistry.newSimpleChannel(
            ResourceLocation.parse("tnc:warrior_tutorial"),()->"1","1"::equals,"1"::equals);
    public record View(CompoundTag data) {}
    public record Action(long token,String action) {}

    @SubscribeEvent public static void setup(net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent event) {
        event.enqueueWork(()->{
            CHANNEL.registerMessage(0,View.class,(packet,buffer)->buffer.writeNbt(packet.data()),
                    buffer->new View(buffer.readNbt()),(packet,context)->{
                        var ctx=context.get();
                        ctx.enqueueWork(()->DistExecutor.unsafeRunWhenOn(Dist.CLIENT,()->()->{
                            var data=packet.data();
                            if(data==null)return;
                            if(data.getBoolean("Waypoint")) {
                                com.tnc.tnc.client.QuestMarkerTarget.tutorialWaypoint(
                                        ResourceLocation.parse(data.getString("Dimension")),
                                        net.minecraft.world.phys.Vec3.atCenterOf(net.minecraft.core.BlockPos.of(data.getLong("Target"))),data.getString("Label"));
                            } else com.tnc.tnc.warrior.client.WarriorTutorialScene.receive(data);
                        }));
                        ctx.setPacketHandled(true);
                    },Optional.of(NetworkDirection.PLAY_TO_CLIENT));
            CHANNEL.registerMessage(1,Action.class,(packet,buffer)->{
                buffer.writeLong(packet.token());buffer.writeUtf(packet.action(),16);
            },buffer->new Action(buffer.readLong(),buffer.readUtf(16)),(packet,context)->{
                var ctx=context.get();
                ctx.enqueueWork(()->{if(ctx.getSender()!=null)WarriorTutorial.action(ctx.getSender(),packet.token(),packet.action());});
                ctx.setPacketHandled(true);
            },Optional.of(NetworkDirection.PLAY_TO_SERVER));
        });
    }
    public static void send(ServerPlayer player,CompoundTag data) {
        if(!(player instanceof net.minecraftforge.common.util.FakePlayer))
            CHANNEL.send(PacketDistributor.PLAYER.with(()->player),new View(data));
    }
    public static void action(long token,String action) { CHANNEL.sendToServer(new Action(token,action)); }
    private WarriorTutorialNetwork() {}
}
