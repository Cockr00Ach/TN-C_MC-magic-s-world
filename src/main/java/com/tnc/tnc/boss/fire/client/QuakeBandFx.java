package com.tnc.tnc.boss.fire.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexBuffer;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexSorting;
import com.tnc.tnc.boss.fire.TortoiseQuake;
import com.tnc.tnc.boss.fire.TortoiseQuakeMesh;
import com.tnc.tnc.boss.fire.VolcanicTortoiseEntity;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterClientCommandsEvent;
import net.minecraftforge.client.event.RegisterShadersEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL14;

/** One translucent, emissive annulus; no terrain copies and no block-model queries. */
@Mod.EventBusSubscriber(modid = "tnc", value = Dist.CLIENT)
public final class QuakeBandFx {
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath("tnc", "textures/entity/quake_band.png");
    private static final Map<UUID, Wave> BOSS_WAVES = new HashMap<>();
    private static final double RANGE_SQR = 96.0D * 96.0D;
    private static final int MAX_BOSS_WAVES = 3;
    private static final int HOLD_TICKS = 1200;
    private static ClientLevel currentLevel;
    private static int localTick;
    private static Wave preview;
    private static ShaderInstance shader;
    private static VertexBuffer vertexBuffer;
    private static final BufferBuilder BUILDER = new BufferBuilder(1048576);

    private QuakeBandFx() {
    }

    @Mod.EventBusSubscriber(modid = "tnc", value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static final class Shaders {
        @SubscribeEvent
        public static void register(RegisterShadersEvent event) throws java.io.IOException {
            event.registerShader(new ShaderInstance(event.getResourceProvider(),
                    ResourceLocation.fromNamespaceAndPath("tnc", "quake_band"), DefaultVertexFormat.POSITION_COLOR_TEX),
                    loaded -> shader = loaded);
        }
    }

    private static final class Wave {
        final int cast;
        final Vec3 center;
        final TortoiseQuake.Playback clock = new TortoiseQuake.Playback();
        final Map<Long, Double> heights = new HashMap<>();
        final boolean flat;
        final boolean held;
        final int born;

        Wave(int cast, Vec3 center, boolean flat, boolean held) {
            this.cast = cast;
            this.center = center;
            this.flat = flat;
            this.held = held;
            this.born = localTick;
        }

        double age(float partial) {
            return held ? 24.0D : clock.age(localTick + partial);
        }

        double height(double x, double z) {
            if (flat) return center.y;
            int bx = Mth.floor(x), bz = Mth.floor(z);
            long key = ((long) bx << 32) ^ (bz & 0xffffffffL);
            Double cached = heights.get(key);
            if (cached != null) return cached;
            double sampled = ground(currentLevel, bx, bz, center.y);
            // The 23.75-block outer edge visits fewer than 2400 columns per wave.
            if (heights.size() < 4096) heights.put(key, sampled);
            return sampled;
        }
    }

    @SubscribeEvent
    public static void commands(RegisterClientCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("tnc_quake_preview")
                .executes(context -> startPreview(false, false))
                .then(Commands.literal("hold").executes(context -> startPreview(false, true)))
                .then(Commands.literal("flat").executes(context -> startPreview(true, true)))
                .then(Commands.literal("clear").executes(context -> {
                    preview = null;
                    return 1;
                })));
    }

    private static int startPreview(boolean flat, boolean held) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return 0;
        HitResult hit = mc.player.pick(40.0D, 0.0F, false);
        Vec3 target;
        if (hit instanceof BlockHitResult block && hit.getType() == HitResult.Type.BLOCK) {
            target = block.getLocation();
        } else {
            Vec3 look = mc.player.getLookAngle();
            target = mc.player.position().add(look.x * 8.0D, 0, look.z * 8.0D);
        }
        int x = Mth.floor(target.x), z = Mth.floor(target.z);
        if (!mc.level.hasChunkAt(BlockPos.containing(target))) return 0;
        double reference = mc.level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
        double y = ground(mc.level, x, z, reference);
        if (!Double.isFinite(y)) {
            mc.player.displayClientMessage(Component.literal("请将准星对准附近的地面"), false);
            return 0;
        }
        resetLevel(mc.level);
        preview = new Wave(0, new Vec3(target.x, y, target.z), flat, held);
        preview.clock.receive(0, 0, localTick);
        mc.player.displayClientMessage(Component.literal(held
                ? "波带已定格；/tnc_quake_preview clear 清除"
                : "独立波带：2秒扩至22格，随后0.5秒淡出"), false);
        return 1;
    }

    private static void resetLevel(ClientLevel level) {
        if (currentLevel == level) return;
        currentLevel = level;
        BOSS_WAVES.clear();
        preview = null;
        if (vertexBuffer != null) {
            vertexBuffer.close();
            vertexBuffer = null;
        }
    }

    @SubscribeEvent
    public static void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        resetLevel(mc.level);
        if (mc.level == null || mc.player == null || mc.isPaused()) return;
        localTick++;
        if (preview != null && (localTick - preview.born >= HOLD_TICKS
                || (!preview.held && preview.age(0) >= TortoiseQuake.LIFETIME))) preview = null;
        var present = new HashSet<UUID>();
        for (var entity : mc.level.entitiesForRendering()) {
            if (!(entity instanceof VolcanicTortoiseEntity boss) || boss.quakeWaveStart() < 0
                    || boss.isDeadOrDying() || boss.quakeWaveCenter().distanceToSqr(mc.player.position()) > RANGE_SQR) continue;
            UUID owner = boss.getUUID();
            present.add(owner);
            Wave wave = BOSS_WAVES.get(owner);
            if (wave == null || wave.cast != boss.quakeWaveStart()) {
                wave = new Wave(boss.quakeWaveStart(), boss.quakeWaveCenter(), false, false);
                BOSS_WAVES.put(owner, wave);
            }
            wave.clock.receive(wave.cast, boss.quakeWaveAge(), localTick);
            if (wave.age(0) >= TortoiseQuake.LIFETIME) wave.heights.clear();
        }
        BOSS_WAVES.keySet().removeIf(owner -> !present.contains(owner));
    }

    /** Scan only the collision surface near the locked cast height; never load a chunk or inspect a model. */
    private static double ground(ClientLevel level, int x, int z, double referenceY) {
        if (level == null || !level.hasChunkAt(BlockPos.containing(x, referenceY, z))) return Double.NaN;
        int top = Math.min(level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) - 1,
                Mth.floor(referenceY + 3.0D));
        int bottom = Math.max(level.getMinBuildHeight(), Mth.floor(referenceY - 4.0D));
        for (int y = top; y >= bottom; y--) {
            BlockPos pos = new BlockPos(x, y, z);
            var state = level.getBlockState(pos);
            if (state.isAir() || !state.getFluidState().isEmpty()) continue;
            var shape = state.getCollisionShape(level, pos);
            if (shape.isEmpty()) continue;
            var above = level.getBlockState(pos.above());
            if (!above.getCollisionShape(level, pos.above()).isEmpty() || !above.getFluidState().isEmpty()) continue;
            double surface = y + shape.max(Direction.Axis.Y);
            return Math.abs(surface - referenceY) <= 3.0D ? surface : Double.NaN;
        }
        return Double.NaN;
    }

    @SubscribeEvent
    public static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_WEATHER || shader == null) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != currentLevel || mc.level == null || mc.player == null) return;
        float partial = mc.isPaused() ? 0 : event.getPartialTick();
        Vec3 camera = event.getCamera().getPosition();
        List<Wave> waves = new ArrayList<>();
        if (preview != null) waves.add(preview);
        BOSS_WAVES.values().stream().filter(w -> w.age(partial) < TortoiseQuake.LIFETIME)
                .sorted(java.util.Comparator.comparingDouble(w -> w.center.distanceToSqr(camera)))
                .limit(MAX_BOSS_WAVES).forEach(waves::add);
        if (waves.isEmpty()) return;
        BUILDER.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR_TEX);
        int vertices = 0;
        for (Wave wave : waves) {
            if (wave.center.distanceToSqr(camera) > RANGE_SQR) continue;
            var mesh = TortoiseQuakeMesh.build(wave.center.x, wave.center.z, wave.age(partial), wave::height);
            for (var quad : mesh.quads()) {
                vertex(quad.a(), camera, mesh.opacity());
                vertex(quad.b(), camera, mesh.opacity());
                vertex(quad.c(), camera, mesh.opacity());
                vertex(quad.d(), camera, mesh.opacity());
                vertices += 4;
            }
        }
        if (vertices == 0) {
            BUILDER.end().release();
            return;
        }
        BUILDER.setQuadSorting(VertexSorting.DISTANCE_TO_ORIGIN);
        boolean blend = GL11.glIsEnabled(GL11.GL_BLEND), cull = GL11.glIsEnabled(GL11.GL_CULL_FACE);
        boolean depth = GL11.glIsEnabled(GL11.GL_DEPTH_TEST), depthWrite = GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK);
        int srcRgb = GL11.glGetInteger(GL14.GL_BLEND_SRC_RGB), dstRgb = GL11.glGetInteger(GL14.GL_BLEND_DST_RGB);
        int srcAlpha = GL11.glGetInteger(GL14.GL_BLEND_SRC_ALPHA), dstAlpha = GL11.glGetInteger(GL14.GL_BLEND_DST_ALPHA);
        var previousShader = RenderSystem.getShader();
        float[] previousColor = RenderSystem.getShaderColor().clone();
        try {
            RenderSystem.enableDepthTest();
            RenderSystem.depthMask(false);
            RenderSystem.disableCull();
            RenderSystem.enableBlend();
            RenderSystem.blendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA,
                    GL11.GL_ONE, GL11.GL_ONE_MINUS_SRC_ALPHA);
            RenderSystem.setShaderColor(1, 1, 1, 1);
            RenderSystem.setShaderTexture(0, TEXTURE);
            if (vertexBuffer == null) vertexBuffer = new VertexBuffer(VertexBuffer.Usage.DYNAMIC);
            vertexBuffer.bind();
            vertexBuffer.upload(BUILDER.end());
            Matrix4f view = new Matrix4f().rotateX((float) Math.toRadians(event.getCamera().getXRot()))
                    .rotateY((float) Math.toRadians(event.getCamera().getYRot() + 180.0F));
            vertexBuffer.drawWithShader(view, event.getProjectionMatrix(), shader);
        } finally {
            VertexBuffer.unbind();
            RenderSystem.depthMask(depthWrite);
            if (depth) RenderSystem.enableDepthTest(); else RenderSystem.disableDepthTest();
            if (cull) RenderSystem.enableCull(); else RenderSystem.disableCull();
            RenderSystem.blendFuncSeparate(srcRgb, dstRgb, srcAlpha, dstAlpha);
            if (blend) RenderSystem.enableBlend(); else RenderSystem.disableBlend();
            RenderSystem.setShaderColor(previousColor[0], previousColor[1], previousColor[2], previousColor[3]);
            RenderSystem.setShader(() -> previousShader);
        }
    }

    private static void vertex(TortoiseQuakeMesh.Vertex vertex, Vec3 camera, float opacity) {
        BUILDER.vertex(vertex.x() - camera.x, vertex.y() - camera.y, vertex.z() - camera.z)
                .color(1.0F, 1.0F, 1.0F, opacity).uv(vertex.u(), vertex.v()).endVertex();
    }
}
