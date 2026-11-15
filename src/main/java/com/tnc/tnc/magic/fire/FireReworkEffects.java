package com.tnc.tnc.magic.fire;

import com.tnc.tnc.TNMod;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.WeakHashMap;

/** Short cosmetic events only. Fire damage and heat remain entirely server-authoritative. */
@Mod.EventBusSubscriber(modid = TNMod.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class FireReworkEffects {
    public enum Kind {
        SPARK, SCAR, WALL, INFERNO, SKYFIRE, DETONATE, CHAIN, SEED, MOLTEN, MARTYR,
        HEAT_IGNITE, HEAT_BURST, HAND_CRUSH, FORM, INFERNO_PULSE, SKY_IMPACT, MOLTEN_IMPACT,
        MARTYR_DRAW, FOOTPRINT, FORM_STRIKE, GHOST_BURST, SCORCHED, FIRE_WAVE
    }

    public record Cue(ResourceLocation dimension, Kind kind, Vec3 at, Vec3 direction,
                      float radius, float intensity, int lifetime, UUID caster, int targetId) {
        public Cue(ResourceLocation dimension, Kind kind, Vec3 at, Vec3 direction,
                   float radius, float intensity, int lifetime, UUID caster) {
            this(dimension, kind, at, direction, radius, intensity, lifetime, caster, -1);
        }

        public Cue {
            if (dimension == null || kind == null || !finite(at) || !finite(direction)
                    || !Float.isFinite(radius) || !Float.isFinite(intensity)
                    || Math.abs(at.x) > 30_000_000 || Math.abs(at.z) > 30_000_000 || Math.abs(at.y) > 20_000_000
                    || radius < .05F || radius > 32 || intensity < 0 || intensity > 1
                    || (kind == Kind.SCORCHED ? targetId < 0 : targetId != -1)
                    || lifetime < 2 || lifetime > 100 || direction.lengthSqr() > 256 * 256) {
                throw new IllegalArgumentException("Invalid fire presentation cue");
            }
        }
    }

    private record Burst(long tick, int count) {}
    private static final Map<ServerLevel, Burst> BUDGET = new WeakHashMap<>();
    private static final class Channel {
        private static final net.minecraftforge.network.simple.SimpleChannel VALUE =
                NetworkRegistry.newSimpleChannel(ResourceLocation.fromNamespaceAndPath("tnc", "fire_rework_fx"),
                        () -> "3", "3"::equals, "3"::equals);
    }

    private FireReworkEffects() {}

    @SubscribeEvent public static void setup(FMLCommonSetupEvent event) {
        Channel.VALUE.registerMessage(0, Cue.class, FireReworkEffects::encode, FireReworkEffects::decode,
                (cue, context) -> {
                    var ctx = context.get();
                    ctx.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                            () -> () -> com.tnc.tnc.client.FireReworkVisuals.accept(cue)));
                    ctx.setPacketHandled(true);
                }, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
    }

    /** At most 96 cosmetic emissions per level/tick, sent only to nearby clients. */
    public static void emit(ServerLevel level, Kind kind, Vec3 at, Vec3 direction,
                            float radius, float intensity, int lifetime, ServerPlayer caster) {
        send(level, kind, at, direction, radius, intensity, lifetime, caster, -1);
    }

    static void emitAttached(ServerPlayer caster, LivingEntity target, Kind kind, float radius, float intensity, int lifetime) {
        send(caster.serverLevel(), kind, target.position(), Vec3.ZERO, radius, intensity, lifetime, caster, target.getId());
    }

    private static void send(ServerLevel level, Kind kind, Vec3 at, Vec3 direction,
                             float radius, float intensity, int lifetime, ServerPlayer caster, int targetId) {
        if (level == null || kind == null || !finite(at) || !finite(direction)
                || !Float.isFinite(radius) || !Float.isFinite(intensity)) return;
        long tick = level.getGameTime();
        Burst previous = BUDGET.get(level);
        int count = previous != null && previous.tick == tick ? previous.count : 0;
        if (count >= 96) return;
        BUDGET.put(level, new Burst(tick, count + 1));
        Cue cue = new Cue(level.dimension().location(), kind, at, direction.lengthSqr() > 256 * 256
                ? direction.normalize().scale(256) : direction,
                Math.max(.05F, Math.min(32, radius)), Math.max(0, Math.min(1, intensity)),
                Math.max(2, Math.min(100, lifetime)), caster == null ? null : caster.getUUID(), targetId);
        double range = Math.min(128, 64 + cue.radius * 2);
        Channel.VALUE.send(PacketDistributor.NEAR.with(() -> new PacketDistributor.TargetPoint(
                at.x, at.y, at.z, range, level.dimension())), cue);
    }

    private static boolean finite(Vec3 v) {
        return v != null && Double.isFinite(v.x) && Double.isFinite(v.y) && Double.isFinite(v.z);
    }
    private static void encode(Cue cue, FriendlyByteBuf b) {
        b.writeResourceLocation(cue.dimension); b.writeEnum(cue.kind);
        vector(b, cue.at); vector(b, cue.direction);
        b.writeFloat(cue.radius); b.writeFloat(cue.intensity); b.writeVarInt(cue.lifetime);
        b.writeBoolean(cue.caster != null); if (cue.caster != null) b.writeUUID(cue.caster);
        if (cue.kind == Kind.SCORCHED) b.writeVarInt(cue.targetId);
    }
    private static Cue decode(FriendlyByteBuf b) {
        var dimension = b.readResourceLocation();
        var kind = b.readEnum(Kind.class);
        Vec3 at = vector(b), direction = vector(b);
        float radius = b.readFloat(), intensity = b.readFloat();
        int lifetime = b.readVarInt();
        UUID owner = b.readBoolean() ? b.readUUID() : null;
        int targetId = kind == Kind.SCORCHED ? b.readVarInt() : -1;
        return new Cue(dimension, kind, at, direction, radius, intensity, lifetime, owner, targetId);
    }
    private static void vector(FriendlyByteBuf b, Vec3 v) { b.writeDouble(v.x); b.writeDouble(v.y); b.writeDouble(v.z); }
    private static Vec3 vector(FriendlyByteBuf b) { return new Vec3(b.readDouble(), b.readDouble(), b.readDouble()); }
}
