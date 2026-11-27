package com.tnc.tnc.sanctum.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.tnc.tnc.sanctum.SanctumGaze;
import static com.tnc.tnc.sanctum.SanctumTimings.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.client.event.RenderHandEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/** A private impression of the statue. All positions derive from the victim's own render camera. */
@Mod.EventBusSubscriber(modid="tnc", value=Dist.CLIENT)
public final class SanctumGazeView {
    private static final MultiBufferSource.BufferSource BUFFERS = MultiBufferSource.immediate(new BufferBuilder(65536));
    private static final RenderType BONE = RenderType.entityCutoutNoCull(
            ResourceLocation.fromNamespaceAndPath("minecraft", "textures/block/bone_block_side.png"));
    private static final ResourceLocation BLOOD = ResourceLocation.fromNamespaceAndPath("tnc", "textures/effects/sanctum/gaze_blood.png");
    private static final float FACE_CENTER_Y = 51.55F, FACE_FRONT_Z = 3.6F;
    private static SanctumGaze.Shot shot;
    private static ClientLevel level;
    private static long started;
    private SanctumGazeView() {}

    public static void start(SanctumGaze.Shot event) {
        var mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || !mc.level.dimension().location().equals(event.level())
                || !mc.player.isAlive() || mc.player.isSpectator()) return;
        shot = event;
        level = mc.level;
        started = level.getGameTime();
        SanctumMesh.request("bleeding");
    }

    private static float age(float partial) {
        var mc = Minecraft.getInstance();
        if (shot == null || mc.level != level || mc.player == null || !mc.player.isAlive() || mc.player.isSpectator()) return GAZE_END;
        return Math.max(0, level.getGameTime() - started + partial);
    }

    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || shot == null) return;
        if (age(0) >= GAZE_END) shot = null;
    }

    @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut event) { shot = null; level = null; }

    @SubscribeEvent public static void hand(RenderHandEvent event) {
        float t=age(event.getPartialTick());
        if(t >= 10 && t < GAZE_STAIN_START)event.setCanceled(true);
    }

    static boolean extending(LivingEntity body, float partial) {
        return shot != null && shot.boss().equals(body.getUUID()) && age(partial) < GAZE_HEAD_END
                && (SanctumMesh.get("bleeding") != null || SanctumMesh.get("bleeding_view") != null);
    }

    private static Vec3 rotated(Quaternionf rotation, double x, double y, double z) {
        var v = rotation.transform(new Vector3f((float)x, (float)y, (float)z));
        return new Vec3(v.x, v.y, v.z);
    }

    @SubscribeEvent public static void world(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_ENTITIES || shot == null) return;
        float t = age(event.getPartialTick());
        var mc = Minecraft.getInstance();
        if (t >= GAZE_HEAD_END) return;
        var model = SanctumMesh.get("bleeding");
        if (model == null) model = SanctumMesh.get("bleeding_view");
        if (model == null) return;
        var eye = event.getCamera().getPosition();
        var view = event.getCamera().getLookVector();
        var forward = new Vec3(view.x(), view.y(), view.z());
        var sideways = new Vec3(forward.z, 0, -forward.x).normalize();
        var body = shot.anchor().subtract(eye);
        var natural = new Quaternionf().rotationY((float)Math.toRadians(-shot.yaw()));
        var home = body.add(rotated(natural, 0, FACE_CENTER_Y, FACE_FRONT_Z));
        var joint = body.add(rotated(natural, 0, 48.6, .55));
        float release = 1 - ease(t, GAZE_STAIN_START, GAZE_HEAD_END);
        float cock = ease(t, 0, 5) * (1 - ease(t, 7, 16)) * release;
        float lunge = ease(t, 5, GAZE_CONTACT) * release;
        // Frame the eye and the whole grin even when the slowness effect narrows field of view.
        double distance = Math.max(4.65, 1.10 + 2.85 * event.getProjectionMatrix().m11());
        double lift = Math.min(1.35, Math.max(0, shot.anchor().y + 2.70 - eye.y - forward.y * distance));
        var contact = forward.scale(distance + .32 * (1 - ease(t, 14, GAZE_CONTACT))).add(0, lift, 0);
        var face = home.lerp(contact, lunge).add(sideways.scale(4.4 * cock + 8.1 * Math.sin(Math.PI * lunge)))
                .add(0, 2.6 * cock - 1.8 * Math.sin(Math.PI * lunge), 0);
        var facing = face.scale(-1).normalize();
        var square = new Quaternionf().rotationYXZ((float)Math.atan2(facing.x, facing.z),
                (float)-Math.asin(Math.max(-1,Math.min(1,facing.y))), 0);
        float align = ease(t, 3, 15) * release;
        var skull = new Quaternionf(natural).slerp(square, align)
                .rotateZ((float)Math.toRadians(49 * cock - 12 * Math.sin(lunge * Math.PI) + 5 * lunge))
                .rotateX((float)Math.toRadians(-17 * cock));
        var nape = face.add(rotated(skull, 0, -3.42, -3.0));
        Matrix4f viewMatrix = event.getPoseStack().last().pose();
        if (t > 1) neck(viewMatrix, joint, nape, sideways, cock, lunge);
        // Hold the face against the lens at wide aspect ratios without clipping the teeth.
        float pressure = ease(t, 14, GAZE_CONTACT) * release;
        float aspect = mc.getMainRenderTarget().width / (float)Math.max(1, mc.getMainRenderTarget().height);
        float spread = Math.min(.64F, .28F + .25F * Math.max(0, aspect - 1.4F));
        var faceMatrix = new Matrix4f(viewMatrix).translate((float)face.x,(float)face.y,(float)face.z)
                .rotate(skull).scale(1 + spread * pressure, 1 + .05F * pressure, 1)
                .translate(0,-FACE_CENTER_Y,-FACE_FRONT_Z);
        model.draw(faceMatrix, BUFFERS, new SanctumPose("bleeding", "gaze_lunge", lunge, t, 0, false),
                "head", LightTexture.pack(15,11));
        BUFFERS.endBatch();
    }

    private static float ease(float t, float lo, float hi) { return SanctumPose.ease(t, lo, hi); }

    private static void neck(Matrix4f view, Vec3 start, Vec3 end, Vec3 side, float cock, float reach) {
        var out = BUFFERS.getBuffer(BONE);
        var normalMatrix = view.normal(new Matrix3f());
        Vec3 delta = end.subtract(start);
        Vec3 lift = new Vec3(0, Math.min(7, delta.length() * .24), 0);
        Vec3[] previous = null, oldNormals = null;
        final int rings = 16, sides = 10;
        for (int i = 0; i <= rings; i++) {
            double u = i / (double)rings, twist = Math.PI * u;
            double bend = 3.0 * cock + 6.4 * reach;
            Vec3 center = start.add(delta.scale(u)).add(side.scale(bend * Math.sin(twist * 2))).add(lift.scale(Math.sin(twist)));
            Vec3 tangent = delta.add(side.scale(bend * Math.PI * 2 * Math.cos(twist * 2)))
                    .add(lift.scale(Math.PI * Math.cos(twist))).normalize();
            Vec3 across = side.subtract(tangent.scale(tangent.dot(side))).normalize();
            if (across.lengthSqr() < .1) across = tangent.cross(new Vec3(0, 0, 1)).normalize();
            Vec3 up = tangent.cross(across).normalize();
            double radius = (1.22 - .44 * Math.sin(twist)) * ((i & 1) == 0 ? .90 : 1);
            Vec3[] ring = new Vec3[sides], normals = new Vec3[sides];
            for (int j = 0; j < sides; j++) {
                double a = j * Math.PI * 2 / sides + u * 1.3 * reach;
                normals[j] = across.scale(Math.cos(a)).add(up.scale(Math.sin(a)));
                ring[j] = center.add(normals[j].scale(radius));
            }
            if (previous != null) for (int j = 0; j < sides; j++) {
                int k = (j + 1) % sides;
                int tint = j == 2 || j == 7 ? 0x803744 : ((i & 1) == 0 ? 0xAA938B : 0xDBC7AF);
                segmentVertex(out,view,normalMatrix,previous[j],oldNormals[j],tint,0,0);
                segmentVertex(out,view,normalMatrix,previous[k],oldNormals[k],tint,1,0);
                segmentVertex(out,view,normalMatrix,ring[k],normals[k],tint,1,1);
                segmentVertex(out,view,normalMatrix,ring[j],normals[j],tint,0,1);
            }
            oldNormals = normals; previous = ring;
        }
    }

    private static void segmentVertex(VertexConsumer out, Matrix4f pose, Matrix3f nm, Vec3 xyz,
                                      Vec3 n, int color, float u, float v) {
        out.vertex(pose,(float)xyz.x,(float)xyz.y,(float)xyz.z).color(color >> 16 & 255,color >> 8 & 255,color & 255,255)
                .uv(u,v).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(LightTexture.pack(13,9))
                .normal(nm,(float)n.x,(float)n.y,(float)n.z).endVertex();
    }

    @SubscribeEvent public static void stain(RenderGuiEvent.Post event) {
        float t = age(event.getPartialTick());
        if (t < GAZE_STAIN_START || t >= GAZE_END) return;
        t -= GAZE_STAIN_START;
        var mc = Minecraft.getInstance();
        int w=mc.getWindow().getGuiScaledWidth(), h=mc.getWindow().getGuiScaledHeight();
        float onset = .28F + .72F * ease(t, 0, 10);
        float fade = 1 - ease(t, 184, GAZE_END - GAZE_STAIN_START);
        var g = event.getGuiGraphics();
        int a = Math.round((106 + 30 * (1 - ease(t, 20, 58))) * (.64F + .36F * onset) * fade);
        if (a > 0) g.fill(0,0,w,h,a << 24 | 0xAC0629);
        RenderSystem.enableBlend(); RenderSystem.defaultBlendFunc();
        RenderSystem.setShaderColor(1,1,1,onset*fade);
        try {
            int length = Math.round(h*(.72F + .28F*ease(t, 0, 78)));
            g.blit(BLOOD,0,0,w,length,0,0,640,360,640,360);
        } finally {
            RenderSystem.setShaderColor(1,1,1,1);
            RenderSystem.disableBlend();
        }
    }
}
