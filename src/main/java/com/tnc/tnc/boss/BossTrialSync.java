package com.tnc.tnc.boss;

import com.tnc.tnc.TNMod;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

/**
 * Boss 交互的<b>屏幕特效同步</b>（2026-11-15 作者："加上更多特别的特效，让人感觉紧迫感"）。
 *
 * <p>只发一个很小的包：剩余 tick / 总 tick / 颜色 / 是否存在。
 * 客户端拿到之后画氛围边框 + 倒计时 + 最后 3 秒的心跳闪红（见 {@code BossTrialOverlay}）。
 *
 * <p>为什么要新开通道：原版 title/subtitle 只能给文字，做不出"屏幕边缘在收缩"这种感觉 ✗
 */
@Mod.EventBusSubscriber(modid = TNMod.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class BossTrialSync {

    private static final String VERSION = "1";
    private static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            ResourceLocation.fromNamespaceAndPath(TNMod.MODID, "boss_trial"),
            () -> VERSION, VERSION::equals, VERSION::equals);

    /** ticksLeft <= 0 表示"这一轮结束了"，客户端会把特效淡出。 */
    public record State(int ticksLeft, int total, float r, float g, float b) {
    }

    private BossTrialSync() {
    }

    @SubscribeEvent
    public static void setup(net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            CHANNEL.messageBuilder(State.class, 0, NetworkDirection.PLAY_TO_CLIENT)
                    .encoder((m, buf) -> {
                        buf.writeInt(m.ticksLeft());
                        buf.writeInt(m.total());
                        buf.writeFloat(m.r());
                        buf.writeFloat(m.g());
                        buf.writeFloat(m.b());
                    })
                    .decoder(buf -> new State(buf.readInt(), buf.readInt(),
                            buf.readFloat(), buf.readFloat(), buf.readFloat()))
                    .consumerMainThread((m, ctx) -> {
                        com.tnc.tnc.client.BossTrialOverlay.accept(m);
                        ctx.get().setPacketHandled(true);
                    })
                    .add();
        });
    }

    /** 每轮交互里调一次（内部自己节流，10 tick 一发就够）。 */
    public static void push(ServerLevel level, Vec3 centre, int ticksLeft, int total,
                            float r, float g, float b) {
        for (ServerPlayer player : level.getEntitiesOfClass(ServerPlayer.class,
                new AABB(centre, centre).inflate(96.0D), p -> p.isAlive() && !p.isSpectator())) {
            CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                    new State(ticksLeft, total, r, g, b));
        }
    }

    /** 交互结束：让客户端把特效收掉。 */
    public static void clear(ServerLevel level, Vec3 centre) {
        push(level, centre, 0, 1, 0.0F, 0.0F, 0.0F);
    }
}
