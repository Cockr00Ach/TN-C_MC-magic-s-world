package com.tnc.tnc.casino.machine;

import com.tnc.tnc.TNMod;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.network.*;
import net.minecraftforge.network.simple.SimpleChannel;
import java.util.function.Supplier;

/** Casino-only channel: server validates table hits; celebrations are visual effects. */
@Mod.EventBusSubscriber(modid=TNMod.MODID,bus=Mod.EventBusSubscriber.Bus.MOD)
public final class CasinoPackets {
    public static final SimpleChannel CHANNEL=NetworkRegistry.newSimpleChannel(
            ResourceLocation.fromNamespaceAndPath(TNMod.MODID,"casino"),()->"1","1"::equals,"1"::equals);
    @SubscribeEvent public static void setup(FMLCommonSetupEvent event) {
        CHANNEL.messageBuilder(TableClick.class,0,NetworkDirection.PLAY_TO_SERVER)
                .encoder((m,b)->b.writeBlockPos(m.pos)).decoder(b->new TableClick(b.readBlockPos()))
                .consumerMainThread(TableClick::handle).add();
        CHANNEL.messageBuilder(Celebration.class,1,NetworkDirection.PLAY_TO_CLIENT)
                .encoder((m,b)->{b.writeDouble(m.x);b.writeDouble(m.y);b.writeDouble(m.z);})
                .decoder(b->new Celebration(b.readDouble(),b.readDouble(),b.readDouble()))
                .consumerMainThread(Celebration::handle).add();
    }
    public record TableClick(BlockPos pos) {
        static void handle(TableClick message,Supplier<NetworkEvent.Context> supplier) {
            var context=supplier.get();var player=context.getSender();
            if(player!=null&&!player.isSpectator()&&player.serverLevel().hasChunkAt(message.pos)
                    &&player.getEyePosition().distanceToSqr(Vec3.atCenterOf(message.pos))<400
                    &&player.serverLevel().getBlockEntity(message.pos) instanceof TNMachineEntity table) {
                Vec3 hit=MachineInteraction.hit(player,table);
                if(hit!=null) {
                    Vec3 local=RouletteControls.local(hit,message.pos,table.getBlockState().getValue(TNCasinoMachineBlock.FACING));
                    int button=local.y>=.9?RouletteControls.button(local.x,local.z):-1;
                    if(table instanceof TNRouletteTableEntity roulette&&button>=0) roulette.chooseColour(player,RouletteControls.BETS[button]);
                    else if(table instanceof TNRouletteTableEntity roulette) roulette.interact(player);
                    else if(table instanceof TNSlotMachineEntity slot) slot.interact(player);
                    else if(table instanceof TNGambleTableEntity dice) dice.interact(player);
                    else if(table instanceof TNCasinoGameEntity game) game.interact(player);
                }
            }
            context.setPacketHandled(true);
        }
    }
    public record Celebration(double x,double y,double z) {
        static void handle(Celebration message,Supplier<NetworkEvent.Context> supplier) {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT,()->()->com.tnc.tnc.casino.client.CasinoEffects.burst(message));
            supplier.get().setPacketHandled(true);
        }
    }
    public static void celebrate(ServerLevel level,BlockPos pos,net.minecraft.world.level.block.state.BlockState state) {
        var bounds=state.getShape(level,pos).bounds();
        double x=pos.getX()+(bounds.minX+bounds.maxX)/2,y=pos.getY()+bounds.maxY+0.85,z=pos.getZ()+(bounds.minZ+bounds.maxZ)/2;
        CHANNEL.send(PacketDistributor.NEAR.with(()->new PacketDistributor.TargetPoint(x,y,z,48,level.dimension())),new Celebration(x,y,z));
    }
}
