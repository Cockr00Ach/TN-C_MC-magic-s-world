package com.tnc.tnc.sanctum;

import com.tnc.tnc.bosshud.BossHudNetwork;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.network.*;
import net.minecraftforge.network.simple.SimpleChannel;
import java.util.Optional;
import java.util.UUID;

/** Appearance follows the combat carrier on spawn, re-track and phase transitions. */
@Mod.EventBusSubscriber(modid="tnc")
public final class SanctumAppearance {
    public static final String HUD = "tnc:sanctum_false_god";
    public static final int TRUE_NAME_RED = 0xED303E;
    private static final String KEY = "TncSanctumAppearance";
    static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            ResourceLocation.fromNamespaceAndPath("tnc","sanctum_appearance"), () -> "3",
            "3"::equals, "3"::equals);

    public record State(ResourceLocation dimension, int id, UUID uuid, String model,
                        String action, long started, int duration) {
        void write(FriendlyByteBuf b) {
            b.writeResourceLocation(dimension); b.writeVarInt(id); b.writeUUID(uuid);
            b.writeUtf(model,32); b.writeUtf(action,32); b.writeLong(started); b.writeVarInt(duration);
        }
        static State read(FriendlyByteBuf b) {
            return new State(b.readResourceLocation(), b.readVarInt(), b.readUUID(),
                    b.readUtf(32), b.readUtf(32), b.readLong(), b.readVarInt());
        }
    }

    @Mod.EventBusSubscriber(modid="tnc", bus=Mod.EventBusSubscriber.Bus.MOD)
    public static final class Setup {
        @SubscribeEvent public static void register(FMLCommonSetupEvent event) {
            SanctumGaze.register(CHANNEL);
            CHANNEL.registerMessage(0, State.class, State::write, State::read, (p,c) -> {
                var ctx=c.get();
                ctx.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                        () -> () -> com.tnc.tnc.sanctum.client.SanctumFigures.accept(p)));
                ctx.setPacketHandled(true);
            }, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        }
    }
    private SanctumAppearance() {}
    public static void mark(LivingEntity entity,String model) {
        if (entity.level().isClientSide) return;
        var tag = entity.getPersistentData().getCompound(KEY);
        tag.putString("Model",model);
        tag.remove("Action"); tag.remove("Duration");
        entity.getPersistentData().put(KEY,tag);
        broadcast(entity);
    }
    public static void markAndAct(LivingEntity entity,String model,String action,int durationTicks){
        if(entity.level().isClientSide)return;
        var tag=entity.getPersistentData().getCompound(KEY);
        tag.putString("Model",model);tag.putString("Action",action);
        tag.putLong("Start",entity.level().getGameTime());tag.putInt("Duration",Math.max(1,durationTicks));
        entity.getPersistentData().put(KEY,tag);broadcast(entity);
    }
    public static void act(LivingEntity entity,String action,int durationTicks) {
        if (entity.level().isClientSide || !entity.getPersistentData().contains(KEY)) return;
        var tag=entity.getPersistentData().getCompound(KEY);
        tag.putString("Action",action);
        tag.putLong("Start",entity.level().getGameTime());
        tag.putInt("Duration",Math.max(1,durationTicks));
        broadcast(entity);
    }
    private static State state(LivingEntity entity) {
        var tag=entity.getPersistentData().getCompound(KEY);
        return new State(entity.level().dimension().location(),entity.getId(),entity.getUUID(),
                tag.getString("Model"),tag.getString("Action"),tag.getLong("Start"),tag.getInt("Duration"));
    }
    private static void broadcast(LivingEntity entity) {
        if (entity.isAddedToWorld()) CHANNEL.send(PacketDistributor.TRACKING_ENTITY.with(() -> entity),state(entity));
    }
    @SubscribeEvent public static void tracking(PlayerEvent.StartTracking event) {
        if (event.getEntity() instanceof ServerPlayer p && event.getTarget() instanceof LivingEntity e
                && e.getPersistentData().contains(KEY) && p.connection!=null) {
            CHANNEL.send(PacketDistributor.PLAYER.with(() -> p),state(e));
        }
    }
    public static void bar(ServerBossEvent bar,LivingEntity carrier,int phase,float hp,float max) {
        var frame=new BossHudNetwork.Snapshot(bar.getId(),carrier.getUUID(),HUD,hp,max,phase,3);
        for (var p:bar.getPlayers()) BossHudNetwork.send(p,frame);
    }
}
