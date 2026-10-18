package com.tnc.tnc.life.routes.mech;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.*;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import java.util.Optional;
public final class MechNetwork {
    public enum Action{DASH,SHIELD,OVERDRIVE,NOVA}
    private static final net.minecraftforge.network.simple.SimpleChannel CHANNEL=NetworkRegistry.newSimpleChannel(ResourceLocation.fromNamespaceAndPath("tnc","skyward_mech"),()->"1","1"::equals,"1"::equals);
    public record Fx(Vec3 from,Vec3 to,int element,int type){}
    public static void register(){
        CHANNEL.registerMessage(0,Action.class,(m,b)->b.writeEnum(m),b->b.readEnum(Action.class),(m,c)->{var ctx=c.get();ctx.enqueueWork(()->{if(ctx.getSender()!=null)MechCombat.activate(ctx.getSender(),m);});ctx.setPacketHandled(true);},Optional.of(NetworkDirection.PLAY_TO_SERVER));
        CHANNEL.registerMessage(1,Fx.class,(m,b)->{vec(b,m.from);vec(b,m.to);b.writeByte(m.element);b.writeByte(m.type);},b->new Fx(vec(b),vec(b),b.readUnsignedByte(),b.readUnsignedByte()),(m,c)->{var ctx=c.get();ctx.enqueueWork(()->DistExecutor.unsafeRunWhenOn(Dist.CLIENT,()->()->MechClient.accept(m)));ctx.setPacketHandled(true);},Optional.of(NetworkDirection.PLAY_TO_CLIENT));
    }
    private static void vec(FriendlyByteBuf b,Vec3 v){b.writeDouble(v.x);b.writeDouble(v.y);b.writeDouble(v.z);}private static Vec3 vec(FriendlyByteBuf b){return new Vec3(b.readDouble(),b.readDouble(),b.readDouble());}
    public static void send(Action action){CHANNEL.sendToServer(action);}
    public static void fx(ServerPlayer p,Vec3 from,Vec3 to,int element,int type){CHANNEL.send(PacketDistributor.NEAR.with(()->new PacketDistributor.TargetPoint(from.x,from.y,from.z,80,p.level().dimension())),new Fx(from,to,element,type));}
}
