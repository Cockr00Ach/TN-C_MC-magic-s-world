package com.tnc.tnc.sanctum;

import java.util.Optional;
import java.util.UUID;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

public final class SanctumGaze {
    private SanctumGaze() {}

    public record Shot(ResourceLocation level, UUID boss, Vec3 anchor, float yaw) {
        void write(FriendlyByteBuf b) {
            b.writeResourceLocation(level); b.writeUUID(boss);
            b.writeDouble(anchor.x); b.writeDouble(anchor.y); b.writeDouble(anchor.z); b.writeFloat(yaw);
        }
        static Shot read(FriendlyByteBuf b) {
            return new Shot(b.readResourceLocation(), b.readUUID(),
                    new Vec3(b.readDouble(), b.readDouble(), b.readDouble()), b.readFloat());
        }
    }

    static void register(SimpleChannel channel) {
        channel.registerMessage(1, Shot.class, Shot::write, Shot::read, (shot, context) -> {
            var ctx = context.get();
            ctx.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                    () -> () -> com.tnc.tnc.sanctum.client.SanctumGazeView.start(shot)));
            ctx.setPacketHandled(true);
        }, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
    }

    public static void send(ServerPlayer victim, LivingEntity boss) {
        if (victim.level() != boss.level() || !victim.isAlive() || victim.isSpectator() || victim.connection == null) return;
        SanctumAppearance.CHANNEL.send(PacketDistributor.PLAYER.with(() -> victim),
                new Shot(boss.level().dimension().location(), boss.getUUID(), boss.position(), boss.getYRot()));
    }
}
