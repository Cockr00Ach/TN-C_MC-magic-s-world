package com.tnc.tnc.world.ink;

import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.*;
import net.minecraftforge.fml.common.Mod;
import java.nio.file.*;
import java.util.*;

/** Opt-in isolated client check. Never enabled in an ordinary game. */
@Mod.EventBusSubscriber(modid="tnc", value=Dist.CLIENT)
public final class InkClearAtmosphereAudit {
    private static boolean entered, saving;
    private static volatile boolean saved;
    private static int ticks, fogCalls;
    private static final List<String> failures = new ArrayList<>();

    @SubscribeEvent(priority=EventPriority.MONITOR, receiveCanceled=true)
    public static void fog(ViewportEvent.RenderFog e) {
        if (!Boolean.getBoolean("tnc.inkClearAtmosphereAudit")) return;
        var level=Minecraft.getInstance().level;
        if (level==null || !level.dimension().equals(InkRealmTravel.INK_REALM)) return;
        fogCalls++;
        if (!e.isCanceled() || e.getNearPlaneDistance()!=InkRealmClientEffects.CLEAR_FOG_START
                || e.getFarPlaneDistance()!=InkRealmClientEffects.CLEAR_FOG_END) {
            if (failures.isEmpty()) failures.add("Fog event was not cleared");
        }
    }

    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent e) throws Exception {
        if (!Boolean.getBoolean("tnc.inkClearAtmosphereAudit") || e.phase!=TickEvent.Phase.END) return;
        var mc=Minecraft.getInstance();
        org.lwjgl.glfw.GLFW.glfwHideWindow(mc.getWindow().getWindow());
        if (mc.level==null || mc.player==null || mc.getOverlay()!=null) return;
        if (!entered) {
            entered=true;
            if (!mc.gameDirectory.toPath().toAbsolutePath().normalize().startsWith(Path.of("D:/ModTest/work")))
                throw new IllegalStateException("Audit must use a disposable workspace world");
            mc.options.pauseOnLostFocus=false;
            var server=mc.getSingleplayerServer();var id=mc.player.getUUID();
            server.execute(()->{
                var player=server.getPlayerList().getPlayer(id);
                player.setGameMode(net.minecraft.world.level.GameType.SPECTATOR);
                player.teleportTo(server.getLevel(InkRealmTravel.INK_REALM),192,-34,192,180,12);
            });
        }
        if (!mc.level.dimension().equals(InkRealmTravel.INK_REALM)) return;
        mc.setScreen(null);
        if (++ticks<160) return;
        if (!saving) {
            saving=true;
            net.minecraft.client.Screenshot.grab(mc.gameDirectory,"ink-clear-atmosphere.png",mc.getMainRenderTarget(),m->saved=true);
            return;
        }
        if (!saved) return;
        if (!Float.isNaN(mc.level.effects().getCloudHeight())) failures.add("Cloud height still enabled");
        if (!mc.level.effects().renderClouds(mc.level,0,0,null,0,0,0,null)) failures.add("Cloud draw hook not consumed");
        if (fogCalls==0) failures.add("No real fog events observed");
        var iris=Class.forName("net.irisshaders.iris.api.v0.IrisApi");
        var api=iris.getMethod("getInstance").invoke(null);
        boolean shaders=(boolean)iris.getMethod("isShaderPackInUse").invoke(api);
        if (!shaders) failures.add("Complementary shader pack not active");
        int glError=org.lwjgl.opengl.GL11.glGetError();
        if (glError!=0) failures.add("OpenGL error: "+glError);
        Files.writeString(Path.of("D:/ModTest/work/ink-clear-atmosphere-native-report.json"),
                new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(Map.of(
                    "failures",failures,"fogEvents",fogCalls,"dimension",mc.level.dimension().location().toString(),
                    "cloudHeightDisabled",Float.isNaN(mc.level.effects().getCloudHeight()),
                    "frames",ticks,"shadersActive",shaders,"scope","Isolated client with dimension-scoped Complementary variant; not a full-pack FPS benchmark")));
        mc.stop();
    }
}
