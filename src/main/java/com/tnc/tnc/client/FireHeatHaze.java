package com.tnc.tnc.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.tnc.tnc.magic.fire.TNFlameHandEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.PostChain;
import net.minecraft.client.renderer.PostPass;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterClientReloadListenersEvent;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;
import org.joml.Vector4f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL14;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

/**
 * A separate, local refraction pass on the composed world, before HUD rendering.
 * Shader packs own their own post pipeline: this pass never runs while one is active.
 */
@Mod.EventBusSubscriber(modid="tnc", value=Dist.CLIENT)
public final class FireHeatHaze {
    private static final ResourceLocation EFFECT = ResourceLocation.fromNamespaceAndPath("tnc", "shaders/post/fire_heat_haze.json");
    private static final double[][] CANDIDATES = new double[FireHeatHazeRules.CANDIDATES][5];
    private static final float[][] MASKS = new float[FireHeatHazeRules.MASKS][5];
    private static final double[][] MASK_WORLD = new double[FireHeatHazeRules.MASKS][4];
    private static final float[] SCORES = new float[FireHeatHazeRules.MASKS];
    private static final Matrix4f VIEW = new Matrix4f(), PROJECTION = new Matrix4f();
    private static final Vector4f POINT = new Vector4f();
    private static final int[] VIEWPORT = new int[4];
    private static final String[][] UNIFORMS = {
        {"CenterX0", "CenterY0", "RadiusX0", "RadiusY0", "Strength0"},
        {"CenterX1", "CenterY1", "RadiusX1", "RadiusY1", "Strength1"},
        {"CenterX2", "CenterY2", "RadiusX2", "RadiusY2", "Strength2"},
        {"CenterX3", "CenterY3", "RadiusX3", "RadiusY3", "Strength3"}
    };
    private static final MethodHandle SHADERS = shaderCheck();
    private static final boolean ENABLED = !Boolean.getBoolean("tnc.disableFireHeatHaze");
    private static ClientLevel world;
    private static PostChain chain;
    private static PostPass pass;
    private static Vec3 camera = Vec3.ZERO;
    private static int count, width, height;
    private static long readyAt;
    private static boolean failed, ready;

    private FireHeatHaze() {}

    /** Call from world VFX rendering, after AFTER_SKY and before AFTER_LEVEL. */
    public static void offer(Vec3 center, double radius, float strength) {
        var mc = Minecraft.getInstance();
        if (!ENABLED || mc.level == null || world != mc.level || failed || center == null || count >= CANDIDATES.length) return;
        float power = FireHeatHazeRules.strength(strength);
        radius = FireHeatHazeRules.radius(radius);
        if (power <= .01F || radius <= .05 || !Double.isFinite(center.x) || !Double.isFinite(center.y) || !Double.isFinite(center.z)) return;
        // Any effect surrounding the eye is unsuitable for local refraction, regardless of owner.
        if (center.distanceToSqr(mc.gameRenderer.getMainCamera().getPosition()) < 16) return;
        var slot = CANDIDATES[count++];
        slot[0] = center.x; slot[1] = center.y; slot[2] = center.z; slot[3] = radius; slot[4] = power;
    }

    @SubscribeEvent(priority=EventPriority.HIGHEST)
    public static void begin(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_SKY) return;
        var mc = Minecraft.getInstance();
        if (world != mc.level) { close(); world = mc.level; }
        count = 0; ready = false;
    }

    @SubscribeEvent(priority=EventPriority.LOWEST)
    public static void world(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_LEVEL) return;
        var mc = Minecraft.getInstance();
        if (!ENABLED || mc.level == null || mc.screen != null || mc.isPaused() || shaders()) { count = 0; ready = false; return; }
        if (world != mc.level) { close(); world = mc.level; count = 0; }
        int hands = 0;
        for (var entity : mc.level.entitiesForRendering()) {
            if (!(entity instanceof TNFlameHandEntity hand) || !hand.isAlive()) continue;
            if (++hands > FireHeatHazeRules.MASKS) break;
            float phase = hand.phase();
            if (phase < .18F || phase >= .86F) continue;
            float envelope = Math.min(1F, Math.min((phase - .18F) / .15F, (.86F - phase) / .12F));
            float heat = Math.max(0F, Math.min(1F, hand.heat() / 100F));
            offer(hand.getPosition(event.getPartialTick()).add(0, hand.gripHeight(), 0),
                    1.5 + .28 * hand.tier(), envelope * (.2F + .65F * heat));
        }
        VIEW.set(event.getPoseStack().last().pose());
        PROJECTION.set(event.getProjectionMatrix());
        camera = event.getCamera().getPosition();
        readyAt = System.nanoTime(); ready = count > 0;
    }

    /** RenderGuiEvent.Pre precedes the hotbar, inventory, text and other HUD layers. */
    @SubscribeEvent(priority=EventPriority.HIGHEST)
    public static void beforeHud(RenderGuiEvent.Pre event) {
        var mc = Minecraft.getInstance();
        if (world != mc.level || mc.level == null || mc.screen != null) {
            close(); world = mc.level; count = 0; ready = false; return;
        }
        if (!ENABLED || shaders()) { close(); ready = false; count = 0; return; }
        if (failed || !ready || System.nanoTime() - readyAt > 500_000_000L) { ready = false; count = 0; return; }
        ready = false;
        for (int i = 0; i < MASKS.length; i++) { java.util.Arrays.fill(MASKS[i], 0); SCORES[i] = 0; }
        for (int i = 0; i < count; i++) project(CANDIDATES[i]);
        count = 0;
        // Four bounded visibility rays keep a fire behind an opaque wall from bending the foreground.
        for (int i = 0; i < MASKS.length; i++) if (SCORES[i] > 0) {
            var source = MASK_WORLD[i];
            var center = new Vec3(source[0], source[1], source[2]);
            var hit = mc.level.clip(new net.minecraft.world.level.ClipContext(camera, center,
                    net.minecraft.world.level.ClipContext.Block.COLLIDER,
                    net.minecraft.world.level.ClipContext.Fluid.NONE, mc.getCameraEntity()));
            if (hit.getType() == net.minecraft.world.phys.HitResult.Type.BLOCK
                    && hit.getLocation().distanceToSqr(center) > source[3] * source[3]) {
                MASKS[i][4] = 0; SCORES[i] = 0;
            }
        }
        if (SCORES[0] == 0 && SCORES[1] == 0 && SCORES[2] == 0 && SCORES[3] == 0) return;
        process(event.getPartialTick());
    }

    private static void project(double[] candidate) {
        POINT.set((float)(candidate[0] - camera.x), (float)(candidate[1] - camera.y), (float)(candidate[2] - camera.z), 1);
        POINT.mul(VIEW).mul(PROJECTION);
        if (!Float.isFinite(POINT.w) || POINT.w < .1F) return;
        float x = POINT.x / POINT.w * .5F + .5F, y = POINT.y / POINT.w * .5F + .5F;
        float z = POINT.z / POINT.w;
        if (z < -1 || z > 1) return;
        float rx = Math.min(.20F, (float)(Math.abs(PROJECTION.m00()) * candidate[3] / POINT.w * .5));
        float ry = Math.min(.28F, (float)(Math.abs(PROJECTION.m11()) * candidate[3] / POINT.w * .5));
        if (rx < .002F || ry < .002F || x + rx < 0 || x - rx > 1 || y + ry < 0 || y - ry > 1) return;
        float score = rx * ry * (float)candidate[4];
        int slot = 0;
        for (int i = 1; i < SCORES.length; i++) if (SCORES[i] < SCORES[slot]) slot = i;
        if (score <= SCORES[slot]) return;
        SCORES[slot] = score;
        var mask = MASKS[slot]; mask[0] = x; mask[1] = y; mask[2] = rx; mask[3] = ry; mask[4] = (float)candidate[4];
        System.arraycopy(candidate, 0, MASK_WORLD[slot], 0, 4);
    }

    private static void create() throws java.io.IOException {
        var mc = Minecraft.getInstance();
        chain = new PostChain(mc.getTextureManager(), mc.getResourceManager(), mc.getMainRenderTarget(), EFFECT);
        // addPass is public and returns our own pass; no reflection into PostChain's private pass list.
        var swap = chain.getTempTarget("fire_haze_swap");
        pass = chain.addPass("tnc:fire_heat_haze", mc.getMainRenderTarget(), swap);
        chain.addPass("blit", swap, mc.getMainRenderTarget());
        for (var names : UNIFORMS) for (var name : names)
            if (pass.getEffect().getUniform(name) == null) throw new java.io.IOException("Missing fire haze uniform: " + name);
        if (pass.getEffect().getUniform("HazeTime") == null) throw new java.io.IOException("Missing fire haze time uniform");
    }

    private static void process(float partial) {
        var mc = Minecraft.getInstance();
        boolean blend = GL11.glIsEnabled(GL11.GL_BLEND), depth = GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
        boolean depthWrite = GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK);
        int depthFunc = GL11.glGetInteger(GL11.GL_DEPTH_FUNC);
        int src = GL11.glGetInteger(GL14.GL_BLEND_SRC_RGB), dst = GL11.glGetInteger(GL14.GL_BLEND_DST_RGB);
        int srcAlpha = GL11.glGetInteger(GL14.GL_BLEND_SRC_ALPHA), dstAlpha = GL11.glGetInteger(GL14.GL_BLEND_DST_ALPHA);
        GL11.glGetIntegerv(GL11.GL_VIEWPORT, VIEWPORT);
        var color = RenderSystem.getShaderColor();
        float red = color[0], green = color[1], blue = color[2], alpha = color[3];
        try {
            if (chain == null) create();
            int w = mc.getMainRenderTarget().width, h = mc.getMainRenderTarget().height;
            if (w != width || h != height) { chain.resize(w, h); width = w; height = h; }
            for (int i = 0; i < MASKS.length; i++) for (int u = 0; u < MASKS[i].length; u++)
                pass.getEffect().getUniform(UNIFORMS[i][u]).set(MASKS[i][u]);
            pass.getEffect().getUniform("HazeTime").set((mc.level.getGameTime() % 24000 + partial) / 20F);
            RenderSystem.disableBlend(); RenderSystem.disableDepthTest(); RenderSystem.depthMask(false);
            chain.process(partial);
        } catch (Exception failure) {
            failed = true; close();
            org.slf4j.LoggerFactory.getLogger(FireHeatHaze.class).error("Local fire heat refraction disabled until resource reload", failure);
        } finally {
            mc.getMainRenderTarget().bindWrite(false);
            RenderSystem.viewport(VIEWPORT[0], VIEWPORT[1], VIEWPORT[2], VIEWPORT[3]);
            RenderSystem.blendFuncSeparate(src, dst, srcAlpha, dstAlpha);
            if (blend) RenderSystem.enableBlend(); else RenderSystem.disableBlend();
            RenderSystem.depthFunc(depthFunc); RenderSystem.depthMask(depthWrite);
            if (depth) RenderSystem.enableDepthTest(); else RenderSystem.disableDepthTest();
            RenderSystem.setShaderColor(red, green, blue, alpha);
        }
    }

    private static MethodHandle shaderCheck() {
        try {
            Class<?> api = Class.forName("net.irisshaders.iris.api.v0.IrisApi");
            Object instance = api.getMethod("getInstance").invoke(null);
            return MethodHandles.publicLookup().findVirtual(api, "isShaderPackInUse", MethodType.methodType(boolean.class)).bindTo(instance);
        } catch (ClassNotFoundException absent) { return null; }
        catch (ReflectiveOperationException | LinkageError unavailable) { return MethodHandles.constant(boolean.class, true); }
    }

    private static boolean shaders() {
        if (SHADERS == null) return false;
        try { return (boolean) SHADERS.invokeExact(); }
        catch (Throwable unavailable) { return true; }
    }

    private static void close() {
        PostChain previous = chain;
        chain = null; pass = null; width = height = 0;
        if (previous != null) try { previous.close(); }
        catch (RuntimeException cleanupFailure) {
            failed = true;
            org.slf4j.LoggerFactory.getLogger(FireHeatHaze.class).warn("Fire refraction cleanup failed; disabled until resource reload", cleanupFailure);
        }
    }

    @Mod.EventBusSubscriber(modid="tnc", value=Dist.CLIENT, bus=Mod.EventBusSubscriber.Bus.MOD)
    public static final class Reload {
        @SubscribeEvent public static void register(RegisterClientReloadListenersEvent event) {
            event.registerReloadListener((ResourceManagerReloadListener) resource -> Minecraft.getInstance().execute(() -> {
                close(); failed = false; ready = false; count = 0; world = null;
            }));
        }
    }
}
