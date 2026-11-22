package com.tnc.tnc.skyacademy.client;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.logging.LogUtils;
import com.tnc.tnc.world.skyhaven.SkyhavenContent;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;

/** The private 1.20.1 client can import the authored region files into its own LOD cache. */
@Mod.EventBusSubscriber(modid = "tnc", value = Dist.CLIENT)
public final class SkyhavenVoxyWarmup {
    private static final String TEMPLATE_MARKER = ".tnc-highlands-lod-v1";
    private static final String CACHE_MARKER = "tnc-highlands-v1.ready";
    private static final boolean SUPPORTED = ModList.get().getModContainerById("voxy").stream()
            .anyMatch(mod -> "0.2.15-Forge-1.20.1".equalsIgnoreCase(mod.getModInfo().getVersion().toString()));

    private static ClientLevel checkedLevel;
    private static Path region;
    private static Path receipt;
    private static boolean checked;
    private static Method renderer;
    private static boolean dispatching;
    private static int nextAttempt;

    @SubscribeEvent
    public static void tick(TickEvent.ClientTickEvent event) {
        if (!SUPPORTED || event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != checkedLevel) {
            reset();
            checkedLevel = mc.level;
        }
        if (checked || mc.level == null || mc.player == null || mc.getOverlay() != null
                || mc.player.tickCount < nextAttempt || mc.player.tickCount % 20 != 0 || !mc.level.dimension().equals(SkyhavenContent.KEY)) return;
        var server = mc.getSingleplayerServer();
        if (server == null || server.isPublished()) return;

        try {
            if (region == null) {
                Path save = server.getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize();
                Path island = save.resolve("dimensions/tnc/skyhaven");
                receipt = save.resolve("voxy").resolve(CACHE_MARKER);
                if (!Files.isRegularFile(island.resolve(TEMPLATE_MARKER)) || Files.exists(receipt)) {
                    checked = true;
                    return;
                }
                region = island.resolve("region");
            }
            if (renderer == null) {
                renderer = Class.forName("me.cortex.voxy.client.core.IGetVoxyRenderSystem").getMethod("getNullable");
            }
            if (renderer.invoke(null) == null) return;
            Object config = Class.forName("me.cortex.voxy.client.config.VoxyConfig").getField("CONFIG").get(null);
            if (!config.getClass().getField("ingestEnabled").getBoolean(config)) return;
            nextAttempt = mc.player.tickCount + 200;
            dispatching = true;
            try {
                mc.player.connection.sendCommand("voxy import raw " + StringArgumentType.escapeIfRequired(region.toString()));
            } finally {
                dispatching = false;
            }
        } catch (ReflectiveOperationException | RuntimeException | LinkageError failure) {
            checked = true;
            LogUtils.getLogger().warn("[Skyhaven] Optional Voxy import unavailable", failure);
        }
    }

    /** Called synchronously by the native importer with the directory it actually opened. */
    public static Path receiptFor(File directory) {
        if (!dispatching || !region.equals(directory.toPath().toAbsolutePath().normalize())) return null;
        checked = true;
        LogUtils.getLogger().info("[Skyhaven] Started Voxy import for the new island");
        return receipt;
    }

    /** Runs on the importer's worker after all regions and queued chunks have finished. */
    public static void completed(Path path) {
        try {
            Files.writeString(path, "1");
            LogUtils.getLogger().info("[Skyhaven] Voxy island cache ready");
        } catch (IOException failure) {
            LogUtils.getLogger().warn("[Skyhaven] Could not mark the Voxy cache ready", failure);
        }
    }

    @SubscribeEvent
    public static void logout(ClientPlayerNetworkEvent.LoggingOut event) {
        reset();
        checkedLevel = null;
    }

    private static void reset() {
        region = null;
        receipt = null;
        checked = false;
        nextAttempt = 0;
    }

    private SkyhavenVoxyWarmup() {}
}
