package com.tnc.tnc.boss.fire.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexBuffer;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexSorting;
import com.tnc.tnc.boss.fire.TortoiseQuake;
import com.tnc.tnc.boss.fire.TortoiseQuakeTiles;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
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

/** A travelling ridge of locally textured ground pieces, using the verified world VBO draw path. */
@Mod.EventBusSubscriber(modid = "tnc", value = Dist.CLIENT)
public final class QuakeBandFx {
    private static final ResourceLocation TEXTURE = InventoryMenu.BLOCK_ATLAS;
    private static final Map<UUID, Wave> BOSS_WAVES = new HashMap<>();
    private static final double RANGE_SQR = 96.0D * 96.0D;
    private static final int MAX_BOSS_WAVES = 3;
    private static final int MAX_TILES = 768;
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
                    ResourceLocation.fromNamespaceAndPath("tnc", "quake_band"), DefaultVertexFormat.POSITION_COLOR_TEX_LIGHTMAP),
                    loaded -> shader = loaded);
        }
    }

    private record Face(int[] data, float red, float green, float blue) {
    }

    private record Surface(BlockState state, double groundY, double pivot, int light,
                           List<Face> faces, float shadowU, float shadowV) {
    }

    private static final Surface MISS = new Surface(null,0,0,0,List.of(),0,0);

    private static final class Wave {
        final int cast;
        final Vec3 center;
        final TortoiseQuake.Playback clock = new TortoiseQuake.Playback();
        final Map<Long, Surface> surfaces = new HashMap<>();
        final Surface prototype;
        final boolean flat;
        final boolean held;
        final int born;

        Wave(int cast, Vec3 center, boolean flat, boolean held) {
            this.cast = cast;
            this.center = center;
            this.flat = flat;
            this.held = held;
            this.born = localTick;
            this.prototype = capture(Minecraft.getInstance(), Mth.floor(center.x), Mth.floor(center.z), center.y);
        }

        double age(float partial) {
            return held ? 24.0D : clock.age(localTick + partial);
        }

        Surface tile(int x, int z) {
            if (flat) return prototype == null ? null : new Surface(prototype.state, center.y,
                    prototype.pivot, prototype.light, prototype.faces, prototype.shadowU, prototype.shadowV);
            long key = ((long) x << 32) ^ (z & 0xffffffffL);
            Surface cached = surfaces.get(key);
            if (cached != null) return cached == MISS ? null : cached;
            Surface sampled = capture(Minecraft.getInstance(), x, z, center.y);
            if (surfaces.size() < 4096) surfaces.put(key, sampled == null ? MISS : sampled);
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
        Surface picked = capture(mc, x, z, reference);
        if (picked == null) {
            mc.player.displayClientMessage(Component.literal("请将准星对准附近的地面"), false);
            return 0;
        }
        resetLevel(mc.level);
        preview = new Wave(0, new Vec3(target.x, picked.groundY, target.z), flat, held);
        preview.clock.receive(0, 0, localTick);
        mc.player.displayClientMessage(Component.literal(held
                ? "地表掀起波已定格；/tnc_quake_preview clear 清除"
                : "独立地表波：逐块抬升倾斜，2秒扩至22格后回落"), false);
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
        int dustBudget = 32;
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
            if (wave.age(0) >= TortoiseQuake.LIFETIME) wave.surfaces.clear();
            else dustBudget -= dust(mc, wave, Math.min(12, dustBudget));
        }
        BOSS_WAVES.keySet().removeIf(owner -> !present.contains(owner));
        if (preview != null && !preview.held) dust(mc, preview, Math.min(12, dustBudget));
    }

    /** Collision surface supplies a world-space TOP; model local Y is kept separate from block Y. */
    private static Surface capture(Minecraft mc, int x, int z, double referenceY) {
        ClientLevel level = mc.level;
        if (level == null || !level.hasChunkAt(BlockPos.containing(x, referenceY, z))) return null;
        int top = Math.min(level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) - 1,
                Mth.floor(referenceY + 4.0D));
        int bottom = Math.max(level.getMinBuildHeight(), Mth.floor(referenceY - 4.0D));
        for (int y = top; y >= bottom; y--) {
            BlockPos pos = new BlockPos(x, y, z);
            BlockState state = level.getBlockState(pos);
            if (state.isAir() || !state.getFluidState().isEmpty() || state.hasBlockEntity()
                    || state.getRenderShape() != RenderShape.MODEL) continue;
            var shape = state.getCollisionShape(level, pos);
            if (shape.isEmpty()) continue;
            var above = level.getBlockState(pos.above());
            if (!above.getCollisionShape(level, pos.above()).isEmpty() || !above.getFluidState().isEmpty()) continue;
            double pivot = shape.max(Direction.Axis.Y), surface = y + pivot;
            if (Math.abs(surface - referenceY) > 3.0D) return null;
            var model = mc.getBlockRenderer().getBlockModel(state);
            var random = RandomSource.create(42L);
            List<Face> faces = new ArrayList<>();
            for (int side = 0; side <= Direction.values().length; side++) {
                Direction direction = side == Direction.values().length ? null : Direction.values()[side];
                random.setSeed(state.getSeed(pos));
                for (BakedQuad quad : model.getQuads(state, direction, random)) {
                    if (quad.getVertices().length < 24) continue;
                    int tint = quad.isTinted() ? mc.getBlockColors().getColor(state, level, pos, quad.getTintIndex()) : 0xFFFFFF;
                    float shade = quad.isShade() ? level.getShade(quad.getDirection(), true) : 1.0F;
                    faces.add(new Face(quad.getVertices(), ((tint >> 16) & 255) / 255.0F * shade,
                            ((tint >> 8) & 255) / 255.0F * shade, (tint & 255) / 255.0F * shade));
                    if (faces.size() >= 48) break;
                }
                if (faces.size() >= 48) break;
            }
            TextureAtlasSprite sprite = model.getParticleIcon();
            if (faces.isEmpty()) fallbackCube(faces, sprite, pivot);
            return new Surface(state, surface, pivot, LevelRenderer.getLightColor(level, pos.above()),
                    faces, (sprite.getU0()+sprite.getU1())*0.5F, (sprite.getV0()+sprite.getV1())*0.5F);
        }
        return null;
    }

    /** Keep unsupported mod models visible, without requiring a culled UP quad. */
    private static void fallbackCube(List<Face> out, TextureAtlasSprite sprite, double top) {
        float y = (float) top;
        float[][] quads = {
                {0,y,0, 0,y,1, 1,y,1, 1,y,0}, {0,0,1, 0,0,0, 1,0,0, 1,0,1},
                {1,0,0, 0,0,0, 0,y,0, 1,y,0}, {0,0,1, 1,0,1, 1,y,1, 0,y,1},
                {0,0,0, 0,0,1, 0,y,1, 0,y,0}, {1,0,1, 1,0,0, 1,y,0, 1,y,1}};
        for (int side=0;side<quads.length;side++) {
            int[] data = new int[32];
            for (int i=0;i<4;i++) {
                for (int axis=0;axis<3;axis++) data[i*8+axis]=Float.floatToIntBits(quads[side][i*3+axis]);
                data[i*8+3]=-1;
                data[i*8+4]=Float.floatToIntBits(i==0 || i==1 ? sprite.getU0() : sprite.getU1());
                data[i*8+5]=Float.floatToIntBits(i==0 || i==3 ? sprite.getV1() : sprite.getV0());
            }
            float shade=side==0 ? 1.0F : side==1 ? 0.5F : 0.75F;
            out.add(new Face(data,shade,shade,shade));
        }
    }

    private static int dust(Minecraft mc, Wave wave, int budget) {
        double age=wave.age(0);
        if (budget<=0 || age<=0 || age>TortoiseQuake.TRAVEL_TICKS) return 0;
        double radius=TortoiseQuake.radius(age);
        for(int i=0;i<budget;i++) {
            double angle=Math.PI*2.0D*(i+localTick*0.17D)/budget;
            int x=Mth.floor(wave.center.x+Math.cos(angle)*radius),z=Mth.floor(wave.center.z+Math.sin(angle)*radius);
            Surface tile=wave.tile(x,z);
            if(tile==null)continue;
            double distance=Math.hypot(x+0.5D-wave.center.x,z+0.5D-wave.center.z);
            double y=tile.groundY+TortoiseQuakeTiles.lift(age,distance)+0.2D;
            if(i%4==0)mc.level.addParticle(ParticleTypes.POOF,x+0.5D,y,z+0.5D,Math.cos(angle)*0.05D,0.04D,Math.sin(angle)*0.05D);
            else mc.level.addParticle(new BlockParticleOption(ParticleTypes.BLOCK,tile.state),x+0.5D,y,z+0.5D,
                    Math.cos(angle)*0.11D,0.12D,Math.sin(angle)*0.11D);
        }
        return budget;
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
        BUILDER.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR_TEX_LIGHTMAP);
        int vertices = 0, remaining = MAX_TILES, remainingWaves=waves.size();
        for (Wave wave : waves) {
            int allowance=remaining/remainingWaves--;
            if (wave.center.distanceToSqr(camera) > RANGE_SQR) continue;
            double age=wave.age(partial);
            var cells=TortoiseQuakeTiles.cells(wave.center.x,wave.center.z,age);
            int count=Math.min(allowance,cells.size());
            for(int i=0;i<count;i++) {
                var cell=cells.get(i*cells.size()/count);
                float opacity=TortoiseQuakeTiles.opacity(age,cell.distance());
                if(opacity<=0 || TortoiseQuakeTiles.lift(age,cell.distance())<0.005D)continue;
                Surface tile=wave.tile(cell.x(),cell.z());
                if(tile==null)continue;
                var pose=TortoiseQuakeTiles.pose(cell,wave.center.x,wave.center.z,tile.groundY,tile.pivot,age);
                for(Face face:tile.faces) {
                    int stride=face.data.length/4;
                    for(int v=0;v<4;v++) {
                        int offset=v*stride;
                        double x=Float.intBitsToFloat(face.data[offset]),y=Float.intBitsToFloat(face.data[offset+1]),z=Float.intBitsToFloat(face.data[offset+2]);
                        BUILDER.vertex(pose.x(x,y,z)-camera.x,pose.y(x,y,z)-camera.y,pose.z(x,y,z)-camera.z)
                                .color(face.red,face.green,face.blue,opacity)
                                .uv(Float.intBitsToFloat(face.data[offset+4]),Float.intBitsToFloat(face.data[offset+5]))
                                .uv2(tile.light).endVertex();
                    }
                    vertices+=4;
                }
                float shadow=(float)(Math.min(1,pose.lift()/TortoiseQuakeTiles.PEAK_LIFT)*0.30D)*opacity;
                double y=tile.groundY+0.012D-camera.y,x=cell.x()-camera.x,z=cell.z()-camera.z;
                BUILDER.vertex(x,y,z).color(0.02F,0.02F,0.02F,shadow).uv(tile.shadowU,tile.shadowV).uv2(tile.light).endVertex();
                BUILDER.vertex(x,y,z+1).color(0.02F,0.02F,0.02F,shadow).uv(tile.shadowU,tile.shadowV).uv2(tile.light).endVertex();
                BUILDER.vertex(x+1,y,z+1).color(0.02F,0.02F,0.02F,shadow).uv(tile.shadowU,tile.shadowV).uv2(tile.light).endVertex();
                BUILDER.vertex(x+1,y,z).color(0.02F,0.02F,0.02F,shadow).uv(tile.shadowU,tile.shadowV).uv2(tile.light).endVertex();
                vertices+=4;
                remaining--;
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
        int previousTexture=RenderSystem.getShaderTexture(0),previousLight=RenderSystem.getShaderTexture(2);
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
            mc.gameRenderer.lightTexture().turnOnLightLayer();
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
            RenderSystem.setShaderTexture(0,previousTexture);
            RenderSystem.setShaderTexture(2,previousLight);
            RenderSystem.setShader(() -> previousShader);
        }
    }

}
