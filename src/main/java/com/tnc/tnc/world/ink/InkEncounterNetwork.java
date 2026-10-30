package com.tnc.tnc.world.ink;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.network.*;
import java.util.Optional;

@Mod.EventBusSubscriber(modid="tnc",bus=Mod.EventBusSubscriber.Bus.MOD)
public final class InkEncounterNetwork {
    private static final net.minecraftforge.network.simple.SimpleChannel CHANNEL=NetworkRegistry.newSimpleChannel(
            ResourceLocation.fromNamespaceAndPath("tnc","ink_encounter"),()->"3","3"::equals,"3"::equals);
    public record Intro(ResourceLocation dimension,int entity,int phase,Vec3 position,float yaw){}
    public record Music(ResourceLocation dimension,int phase){}
    public record Shake(ResourceLocation dimension,int ticks,float strength){}
    public record Hold(ResourceLocation dimension,int ticks){}
    @SubscribeEvent public static void setup(FMLCommonSetupEvent event){
        CHANNEL.registerMessage(0,Intro.class,InkEncounterNetwork::encode,InkEncounterNetwork::decode,(p,c)->{
            var ctx=c.get();ctx.enqueueWork(()->DistExecutor.unsafeRunWhenOn(Dist.CLIENT,()->()->com.tnc.tnc.world.ink.client.InkEncounterCamera.start(p)));
            ctx.setPacketHandled(true);
        },Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(1,Music.class,(p,b)->{b.writeResourceLocation(p.dimension());b.writeVarInt(p.phase());},
                b->new Music(b.readResourceLocation(),b.readVarInt()),(p,c)->{var ctx=c.get();
                    ctx.enqueueWork(()->DistExecutor.unsafeRunWhenOn(Dist.CLIENT,()->()->com.tnc.tnc.world.ink.client.InkBossMusic.accept(p.dimension(),p.phase())));
                    ctx.setPacketHandled(true);
                },Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(2,Shake.class,(p,b)->{b.writeResourceLocation(p.dimension());b.writeVarInt(p.ticks());b.writeFloat(p.strength());},
                b->new Shake(b.readResourceLocation(),b.readVarInt(),b.readFloat()),(p,c)->{var ctx=c.get();
                    ctx.enqueueWork(()->DistExecutor.unsafeRunWhenOn(Dist.CLIENT,()->()->com.tnc.tnc.world.ink.client.InkCombatFeedback.shake(p.dimension(),p.ticks(),p.strength())));ctx.setPacketHandled(true);
                },Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(3,Hold.class,(p,b)->{b.writeResourceLocation(p.dimension());b.writeVarInt(p.ticks());},
                b->new Hold(b.readResourceLocation(),b.readVarInt()),(p,c)->{var ctx=c.get();
                    ctx.enqueueWork(()->DistExecutor.unsafeRunWhenOn(Dist.CLIENT,()->()->com.tnc.tnc.world.ink.client.InkCombatFeedback.hold(p.dimension(),p.ticks())));ctx.setPacketHandled(true);
                },Optional.of(NetworkDirection.PLAY_TO_CLIENT));
    }
    public static void music(ServerPlayer player,int phase){CHANNEL.send(PacketDistributor.PLAYER.with(()->player),new Music(player.level().dimension().location(),phase));}
    public static void shake(ServerPlayer player,int ticks,float strength){CHANNEL.send(PacketDistributor.PLAYER.with(()->player),new Shake(player.level().dimension().location(),ticks,strength));}
    public static void hold(ServerPlayer player,int ticks){CHANNEL.send(PacketDistributor.PLAYER.with(()->player),new Hold(player.level().dimension().location(),ticks));}
    public static void send(ServerPlayer player,Mob boss,int phase){CHANNEL.send(PacketDistributor.PLAYER.with(()->player),new Intro(player.level().dimension().location(),boss.getId(),phase,boss.position(),boss.getYRot()));}
    private static void encode(Intro p,FriendlyByteBuf b){b.writeResourceLocation(p.dimension);b.writeInt(p.entity);b.writeInt(p.phase);b.writeDouble(p.position.x);b.writeDouble(p.position.y);b.writeDouble(p.position.z);b.writeFloat(p.yaw);}
    private static Intro decode(FriendlyByteBuf b){return new Intro(b.readResourceLocation(),b.readInt(),b.readInt(),new Vec3(b.readDouble(),b.readDouble(),b.readDouble()),b.readFloat());}
}
