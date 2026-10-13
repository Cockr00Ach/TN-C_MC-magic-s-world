package com.tnc.tnc.life.routes;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.*;
import java.util.function.Supplier;
/** A held-use heartbeat grants only a short, validated server-side injection lease. */
public final class RouteInputNetwork {
 private static final net.minecraftforge.network.simple.SimpleChannel CHANNEL=NetworkRegistry.newSimpleChannel(ResourceLocation.fromNamespaceAndPath("tnc","route_input"),()->"1","1"::equals,"1"::equals);
 public static void register(){CHANNEL.registerMessage(0,Held.class,(m,b)->b.writeBlockPos(m.pos),b->new Held(b.readBlockPos()),RouteInputNetwork::handle,java.util.Optional.of(NetworkDirection.PLAY_TO_SERVER));}
 public static void held(BlockPos p){CHANNEL.sendToServer(new Held(p));}
 private record Held(BlockPos pos){}
 private static void handle(Held m,Supplier<NetworkEvent.Context> context){var c=context.get();c.enqueueWork(()->{var p=c.getSender();if(p==null||!p.getMainHandItem().isEmpty()||p.isShiftKeyDown()||!p.serverLevel().hasChunkAt(m.pos)||p.distanceToSqr(m.pos.getX()+.5,m.pos.getY()+.5,m.pos.getZ()+.5)>16)return;if(!(p.serverLevel().getBlockEntity(m.pos) instanceof RouteNodeEntity be)||be.kind()!=RouteKind.INFUSER||!be.mayUse(p))return;var sight=p.serverLevel().clip(new net.minecraft.world.level.ClipContext(p.getEyePosition(),net.minecraft.world.phys.Vec3.atCenterOf(m.pos),net.minecraft.world.level.ClipContext.Block.COLLIDER,net.minecraft.world.level.ClipContext.Fluid.NONE,p));if(!sight.getBlockPos().equals(m.pos))return;be.manualPlayer=p.getUUID();be.manualUntil=p.level().getGameTime()+10;});c.setPacketHandled(true);}
 @net.minecraftforge.fml.common.Mod.EventBusSubscriber(modid="tnc",value=net.minecraftforge.api.distmarker.Dist.CLIENT)
 public static final class Client {
  @net.minecraftforge.eventbus.api.SubscribeEvent public static void tick(net.minecraftforge.event.TickEvent.ClientTickEvent e){if(e.phase!=net.minecraftforge.event.TickEvent.Phase.END)return;var mc=net.minecraft.client.Minecraft.getInstance();if(mc.player==null||mc.level==null||mc.screen!=null||!mc.options.keyUse.isDown()||mc.player.isShiftKeyDown()||!mc.player.getMainHandItem().isEmpty()||mc.player.tickCount%5!=0||!(mc.hitResult instanceof net.minecraft.world.phys.BlockHitResult hit))return;if(mc.level.getBlockState(hit.getBlockPos()).getBlock() instanceof RouteNodeBlock b&&b.kind==RouteKind.INFUSER)held(hit.getBlockPos());}
 }
}
