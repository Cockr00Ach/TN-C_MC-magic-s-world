package com.tnc.tnc.world.eartharena.client;

import com.tnc.tnc.world.eartharena.EarthArena;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.components.Button;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.GameType;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Isolated dev save only: enter with the real key, photograph the realm and return. */
@Mod.EventBusSubscriber(modid = "tnc", value = Dist.CLIENT)
public final class EarthArenaClientAudit {
    private static boolean requested, positioned, saving, returning;
    private static volatile boolean saved;
    private static int ticks, arenaFrames;
    private static String origin;
    private static net.minecraft.world.phys.Vec3 originalPosition;

    @Mod.EventBusSubscriber(modid = "tnc", bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static final class Setup {
        @SubscribeEvent public static void setup(net.minecraftforge.fml.event.lifecycle.FMLLoadCompleteEvent event) {
            if (Boolean.getBoolean("tnc.earthArenaAudit")) event.enqueueWork(() -> {
                net.minecraftforge.common.MinecraftForge.EVENT_BUS.unregister(com.tnc.tnc.magic.TNDarkAimMechanics.class);
                net.minecraftforge.common.MinecraftForge.EVENT_BUS.unregister(com.tnc.tnc.magic.TNDarkHandCord.class);
                net.minecraftforge.common.MinecraftForge.EVENT_BUS.unregister(com.tnc.tnc.client.TNSpellClientVisuals.class);
                net.minecraftforge.common.MinecraftForge.EVENT_BUS.unregister(com.tnc.tnc.client.DarkGiantSiphonInput.class);
            });
        }
    }

    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event) throws Exception {
        if (!Boolean.getBoolean("tnc.earthArenaAudit") || event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        org.lwjgl.glfw.GLFW.glfwHideWindow(mc.getWindow().getWindow());
        if (mc.getOverlay() != null) return;
        if (++ticks > 2400) {
            Files.writeString(Path.of(System.getProperty("tnc.earthArenaAuditReport")), "{\"failures\":[\"Audit timed out\"],\"screen\":\"" + (mc.screen == null ? "none" : mc.screen.getClass().getSimpleName()) + "\"}");
            mc.stop(); return;
        }
        if (mc.level == null || mc.player == null) {
            // A disposable copied test save may present the vanilla experimental-world prompt.
            if (mc.screen != null && mc.screen.getClass().getSimpleName().matches("BackupConfirmScreen|ConfirmScreen"))
                for (var child : mc.screen.children()) if (child instanceof Button button && button.active &&
                        List.of("Proceed", "I know what I'm doing!", "Yes").contains(button.getMessage().getString())) { button.onPress(); break; }
            return;
        }
        var server = mc.getSingleplayerServer();
        if (server == null) return;
        if (!requested) {
            requested = true;
            origin = mc.level.dimension().location().toString(); originalPosition = mc.player.position();
            mc.options.pauseOnLostFocus = false; mc.options.hideGui = true;
            mc.options.renderDistance().set(16); mc.options.framerateLimit().set(60); mc.options.gamma().set(1.0);
            server.execute(() -> {
                var player = server.getPlayerList().getPlayer(mc.player.getUUID());
                EarthArena.KEYSTONE.use(player.serverLevel(), player, InteractionHand.MAIN_HAND);
            });
            return;
        }
        if (mc.level.dimension().equals(EarthArena.KEY)) {
            if (!positioned) {
                positioned = true;
                server.execute(() -> {
                    var player = server.getPlayerList().getPlayer(mc.player.getUUID());
                    player.setGameMode(GameType.CREATIVE);player.getAbilities().flying=true;player.onUpdateAbilities();
                    player.teleportTo(player.serverLevel(), -25.5, 340, 80.5, -117, 46);
                });
            }
            if (++arenaFrames < 400 || mc.level.getChunkSource().getLoadedChunksCount() < 300) return;
            if (!saving) {
                saving = true;
                Screenshot.grab(mc.gameDirectory, "earth_arena.png", mc.getMainRenderTarget(), message -> saved = true);
                return;
            }
            if (saved && !returning) {
                returning = true;
                server.execute(() -> {
                    var player = server.getPlayerList().getPlayer(mc.player.getUUID());
                    EarthArena.KEYSTONE.use(player.serverLevel(), player, InteractionHand.MAIN_HAND);
                });
            }
        } else if (returning && mc.level.dimension().location().toString().equals(origin) && mc.player.position().distanceToSqr(originalPosition) < 1) {
            Files.writeString(Path.of(System.getProperty("tnc.earthArenaAuditReport")), new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(Map.of(
                    "failures", List.of(), "dimension", EarthArena.KEY.location().toString(), "keyRoundTrip", true,
                    "arenaFrames", arenaFrames, "scope", "Real isolated client terrain/sky render and actual keystone entry/return")));
            mc.stop();
        }
    }
}
