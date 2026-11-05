package com.tnc.tnc.warrior;

import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkEvent;

/** One launch snapshot; clients draw a continuous mesh, the server retains damage authority. */
public record WarriorWavePacket(ResourceLocation dimension, Vec3 origin, Vec3 direction,
        boolean ground, double speed, double range, double halfWidth, int core, int edge, long startedAt) {
    public void encode(FriendlyByteBuf out) {
        out.writeResourceLocation(dimension);
        out.writeDouble(origin.x); out.writeDouble(origin.y); out.writeDouble(origin.z);
        out.writeDouble(direction.x); out.writeDouble(direction.y); out.writeDouble(direction.z);
        out.writeBoolean(ground); out.writeDouble(speed); out.writeDouble(range); out.writeDouble(halfWidth);
        out.writeInt(core); out.writeInt(edge); out.writeLong(startedAt);
    }
    public static WarriorWavePacket decode(FriendlyByteBuf in) {
        var dimension = in.readResourceLocation();
        var origin = new Vec3(in.readDouble(), in.readDouble(), in.readDouble());
        var direction = new Vec3(in.readDouble(), in.readDouble(), in.readDouble());
        boolean ground = in.readBoolean();
        double speed = in.readDouble(), range = in.readDouble(), width = in.readDouble();
        int core = in.readInt(), edge = in.readInt(); long started = in.readLong();
        if (!Double.isFinite(origin.lengthSqr()) || !Double.isFinite(direction.lengthSqr())
                || direction.lengthSqr() < .5 || direction.lengthSqr() > 1.5
                || !Double.isFinite(speed) || speed <= 0 || speed > 10
                || !Double.isFinite(range) || range <= 0 || range > 128
                || !Double.isFinite(width) || width <= 0 || width > 16)
            throw new io.netty.handler.codec.DecoderException("invalid warrior wave geometry");
        return new WarriorWavePacket(dimension, origin, direction.normalize(), ground, speed, range, width, core, edge, started);
    }
    public void handle(Supplier<NetworkEvent.Context> supplier) {
        var context = supplier.get();
        context.enqueueWork(() -> ClientOnly.accept(this));
        context.setPacketHandled(true);
    }
    private static final class ClientOnly {
        static void accept(WarriorWavePacket packet) {
            com.tnc.tnc.warrior.client.WarriorWaveRenderer.accept(packet);
        }
    }
}
