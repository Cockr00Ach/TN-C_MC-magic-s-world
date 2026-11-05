package com.tnc.tnc.warrior.forge;

import java.util.UUID;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.network.*;
import net.minecraftforge.network.simple.SimpleChannel;

@Mod.EventBusSubscriber(modid="tnc",bus=Mod.EventBusSubscriber.Bus.MOD)
public final class ForgeNamingNetwork {
    public static final SimpleChannel CHANNEL=NetworkRegistry.newSimpleChannel(WarriorForgeContent.id("warrior_forge"),()->"1","1"::equals,"1"::equals);
    @SubscribeEvent public static void setup(FMLCommonSetupEvent event){
        CHANNEL.messageBuilder(Open.class,0,NetworkDirection.PLAY_TO_CLIENT).encoder(Open::encode).decoder(Open::decode).consumerMainThread(Open::handle).add();
        CHANNEL.messageBuilder(Name.class,1,NetworkDirection.PLAY_TO_SERVER).encoder(Name::encode).decoder(Name::decode).consumerMainThread(Name::handle).add();
    }
    public static void open(ServerPlayer p,ItemStack stack){
        if(!ForgeWeaponData.needsName(stack))return;
        var t=stack.getTag().getCompound(ForgeWeaponData.KEY);
        if(t.hasUUID("Maker")&&t.getUUID("Maker").equals(p.getUUID()))CHANNEL.send(PacketDistributor.PLAYER.with(()->p),new Open(t.getUUID("WeaponId")));
    }
    public record Open(UUID id){
        void encode(FriendlyByteBuf b){b.writeUUID(id);} static Open decode(FriendlyByteBuf b){return new Open(b.readUUID());}
        static void handle(Open p,Supplier<NetworkEvent.Context> c){
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT,()->()->com.tnc.tnc.warrior.forge.client.ForgeNamingScreen.open(p.id));c.get().setPacketHandled(true);
        }
    }
    public record Name(UUID id,String name){
        void encode(FriendlyByteBuf b){b.writeUUID(id);b.writeUtf(name,64);} static Name decode(FriendlyByteBuf b){return new Name(b.readUUID(),b.readUtf(64));}
        static void handle(Name packet,Supplier<NetworkEvent.Context> c){
            var p=c.get().getSender();if(p!=null&&p.isAlive()&&!p.isSpectator()){
                boolean named=ForgeWeaponData.name(p,p.getMainHandItem(),packet.id,packet.name);
                if(!named)p.displayClientMessage(net.minecraft.network.chat.Component.literal("命名未保存：请主手持有本人的未命名神铸兵器，名字为2～16字"),false);
            }c.get().setPacketHandled(true);
        }
    }
}
