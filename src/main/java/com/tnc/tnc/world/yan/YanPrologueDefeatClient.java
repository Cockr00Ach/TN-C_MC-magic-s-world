package com.tnc.tnc.world.yan;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.tnc.tnc.boss.client.YanExecutionCamera;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.RemotePlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.MovementInputUpdateEvent;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.client.event.RenderHandEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.client.event.RenderNameTagEvent;
import net.minecraftforge.client.event.RenderPlayerEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;

@Mod.EventBusSubscriber(modid = "tnc", value = Dist.CLIENT)
public final class YanPrologueDefeatClient {
    private static final int CAMERA_HANDOFF = 16;
    private static final int[] ELEMENT_COLORS = {
            0xb298ff, 0xff7540, 0x70f5bb, 0x61bfff, 0xe9b465, 0xfff1b1, 0xa057de
    };
    private static final MultiBufferSource.BufferSource BUFFERS = MultiBufferSource.immediate(new BufferBuilder(16384));
    private static ClientLevel world;
    private static RemotePlayer body;
    private static ArmorStand camera;
    private static CameraType previous;
    private static Vec3 feet, chest;
    private static float yaw;
    private static int age;

    private YanPrologueDefeatClient() {}

    static void begin(YanPrologueNetwork.Defeat defeat) {
        end();
        var mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || !mc.level.dimension().location().equals(YanPrologueScript.DIMENSION)) return;
        world = mc.level;
        feet = defeat.position();
        yaw = defeat.yaw();
        chest = feet.add(Vec3.directionFromRotation(0, yaw - 90).scale(.63)).add(0, .28, 0);
        body = new RemotePlayer(world, mc.player.getGameProfile());
        body.moveTo(feet.x, feet.y, feet.z, yaw, 0);
        body.yBodyRot = body.yBodyRotO = yaw;
        body.yHeadRot = body.yHeadRotO = yaw;
        for (var slot : EquipmentSlot.values()) {
            if (slot.getType() == EquipmentSlot.Type.ARMOR) body.setItemSlot(slot, mc.player.getItemBySlot(slot).copy());
        }
        if (mc.screen != null) mc.setScreen(null);
    }

    static void end() {
        if (body == null) return;
        var mc = Minecraft.getInstance();
        if (camera != null && mc.getCameraEntity() == camera) {
            mc.setCameraEntity(mc.player);
            mc.options.setCameraType(previous);
        }
        body = null;
        world = null;
        camera = null;
        previous = null;
        age = 0;
    }

    @SubscribeEvent
    public static void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || body == null) return;
        var mc = Minecraft.getInstance();
        if (mc.level != world || mc.player == null) {
            end();
            return;
        }
        if (mc.isPaused()) return;
        age++;
        body.deathTime = Math.min(20, age + 3);
        if (camera == null && age >= CAMERA_HANDOFF) {
            YanExecutionCamera.releaseForPrologue();
            previous = mc.options.getCameraType();
            camera = new ArmorStand(world, feet.x, feet.y, feet.z);
            camera.setInvisible(true);
            mc.options.setCameraType(CameraType.FIRST_PERSON);
            mc.setCameraEntity(camera);
        }
        if (camera != null) {
            float rise = smooth((age - CAMERA_HANDOFF) / 80F);
            var side = Vec3.directionFromRotation(0, yaw + 90 + rise * 26);
            var eye = chest.add(side.scale(4.4 + rise * 2.6)).add(0, 2.1 + rise * 4.5, 0);
            var toward = chest.add(0, .25 + rise, 0).subtract(eye);
            camera.xo = camera.getX();
            camera.yo = camera.getY();
            camera.zo = camera.getZ();
            camera.xRotO = camera.getXRot();
            camera.yRotO = camera.getYRot();
            camera.setPos(eye.x, eye.y - camera.getEyeHeight(), eye.z);
            camera.setYRot((float) Math.toDegrees(Math.atan2(-toward.x, toward.z)));
            camera.setXRot((float) -Math.toDegrees(Math.atan2(toward.y, toward.horizontalDistance())));
            if (age == CAMERA_HANDOFF) {
                camera.setOldPosAndRot();
            }
        }
    }

    @SubscribeEvent
    public static void movement(MovementInputUpdateEvent event) {
        if (body == null) return;
        var input = event.getInput();
        input.forwardImpulse = 0;
        input.leftImpulse = 0;
        input.jumping = false;
        input.shiftKeyDown = false;
    }

    @SubscribeEvent
    public static void interact(InputEvent.InteractionKeyMappingTriggered event) {
        if (body != null) event.setCanceled(true);
    }

    @SubscribeEvent
    public static void hidePlayer(RenderPlayerEvent.Pre event) {
        if (body != null && event.getEntity() == Minecraft.getInstance().player) event.setCanceled(true);
    }

    @SubscribeEvent
    public static void hideBodyName(RenderNameTagEvent event) {
        if (body != null && event.getEntity() == body) event.setResult(Event.Result.DENY);
    }

    @SubscribeEvent
    public static void hand(RenderHandEvent event) {
        if (body != null) event.setCanceled(true);
    }

    @SubscribeEvent
    public static void render(RenderLevelStageEvent event) {
        if (body == null || event.getStage() != RenderLevelStageEvent.Stage.AFTER_WEATHER) return;
        var view = event.getCamera().getPosition();
        var offset = feet.subtract(view);
        var dispatcher = Minecraft.getInstance().getEntityRenderDispatcher();
        dispatcher.render(body, offset.x, offset.y, offset.z, yaw, event.getPartialTick(),
                event.getPoseStack(), BUFFERS, dispatcher.getPackedLightCoords(body, event.getPartialTick()));
        BUFFERS.endBatch();

        float time = age + event.getPartialTick();
        if (time < YanPrologueScript.CORPSE_HOLD || time > YanPrologueScript.CLOCK_START + 7) return;
        var matrix = new Matrix4f(event.getPoseStack().last().pose());
        var consumer = BUFFERS.getBuffer(RenderType.lightning());
        var center = chest.subtract(view);
        for (int i = 0; i < ELEMENT_COLORS.length; i++) {
            float travel = Mth.clamp((time - YanPrologueScript.CORPSE_HOLD - i * 1.8F) / 39F, 0, 1);
            float alpha = smooth(travel * 10) * (1 - smooth((time - YanPrologueScript.CLOCK_START) / 7F));
            if (alpha <= 0) continue;
            double angle = i * Math.PI * 2 / ELEMENT_COLORS.length + .45;
            double head = 1 - travel;
            double tail = Math.min(1, head + .54 - travel * .24);
            Vec3 last = streamPoint(center, angle, i, head, time);
            for (int j = 1; j <= 16; j++) {
                float along = j / 16F;
                Vec3 next = streamPoint(center, angle, i, Mth.lerp(along, head, tail), time);
                Vec3 across = next.subtract(last).cross(next.scale(-1)).normalize();
                double width = .17 * (1 - along * .84) * (1 + .22 * Math.sin(time * .36 + j * .54));
                float opacity = alpha * (1 - along * .9F);
                ribbon(consumer, matrix, last, next, across.scale(width * 2.6), ELEMENT_COLORS[i], opacity * .21F);
                ribbon(consumer, matrix, last, next, across.scale(width), ELEMENT_COLORS[i], opacity * .83F);
                ribbon(consumer, matrix, last, next, across.scale(width * .28), 0xfff5dc, opacity * .92F);
                last = next;
            }
        }
        BUFFERS.endBatch(RenderType.lightning());
    }

    private static Vec3 streamPoint(Vec3 center, double angle, int ray, double progress, float time) {
        double turn = angle + (1 - progress) * .8 + Math.sin(progress * 7 - time * .12 + ray) * .23;
        double radius = progress * (8 + ray % 3 * 1.4);
        double y = progress * (5 + ray % 4 * 1.6) + Math.sin(progress * Math.PI) * 1.6;
        return center.add(Math.cos(turn) * radius, y, Math.sin(turn) * radius);
    }

    private static void ribbon(VertexConsumer out, Matrix4f matrix, Vec3 from, Vec3 to, Vec3 width, int rgb, float alpha) {
        vertex(out, matrix, from.subtract(width), rgb, alpha);
        vertex(out, matrix, from.add(width), rgb, alpha);
        vertex(out, matrix, to.add(width), rgb, alpha);
        vertex(out, matrix, to.subtract(width), rgb, alpha);
    }

    private static void vertex(VertexConsumer out, Matrix4f matrix, Vec3 p, int rgb, float alpha) {
        out.vertex(matrix, (float) p.x, (float) p.y, (float) p.z)
                .color(rgb >> 16 & 255, rgb >> 8 & 255, rgb & 255, (int) (alpha * 255)).endVertex();
    }

    @SubscribeEvent
    public static void clock(RenderGuiEvent.Pre event) {
        if (body == null) return;
        event.setCanceled(true);
        var gui = event.getGuiGraphics();
        int width = gui.guiWidth();
        int height = gui.guiHeight();
        float time = age + event.getPartialTick();
        float timepiece = time - YanPrologueScript.CLOCK_START;
        if (timepiece >= 0) {
            float fade = smooth(timepiece / 10);
            gui.fill(0, 0, width, height, color(0x110c20, fade * .49F));
            var pose = gui.pose();
            pose.pushPose();
            pose.translate(width / 2F, height / 2F, 0);
            float scale = Math.min(width, height) / 190F * (.93F + .07F * fade);
            pose.scale(scale, scale, 1);
            for (int mark = 0; mark < 60; mark++) {
                pose.pushPose();
                pose.mulPose(Axis.ZP.rotationDegrees(mark * 6));
                gui.fill(-3, -57, 3, -55, color(0xbca577, fade * .55F));
                gui.fill(-3, -53, 3, -52, color(0xffedc2, fade * .95F));
                if (mark % 5 == 0) gui.fill(-1, -48, 2, mark % 15 == 0 ? -38 : -42, color(0xffedc2, fade));
                pose.popPose();
            }
            float reverse = timepiece * timepiece;
            drawHand(gui, 60 - reverse * .19F, 25, 3, color(0xffe2a2, fade));
            drawHand(gui, 158 - reverse * .9F, 38, 2, color(0xfff6dc, fade));
            gui.fill(-3, -3, 4, 4, color(0xfff4cc, fade));
            pose.popPose();
        }
        float flash = time - (YanPrologueScript.DEFEAT_TICKS - 8);
        if (flash >= 0) {
            gui.fill(0, 0, width, height, color(0xffeed1, .85F * (1 - Math.abs(Mth.clamp(flash / 5, 0, 2) - 1))));
            gui.fill(0, 0, width, height, color(0, smooth((flash - 4) / 4)));
        }
    }

    private static void drawHand(GuiGraphics gui, float rotation, int length, int thickness, int color) {
        var pose = gui.pose();
        pose.pushPose();
        pose.mulPose(Axis.ZP.rotationDegrees(rotation));
        gui.fill(-thickness / 2, -length, (thickness + 1) / 2, 6, color);
        gui.fill(0, -length - 3, 1, -length, color);
        pose.popPose();
    }

    private static int color(int rgb, float alpha) { return ((int) (255 * alpha) << 24) | rgb; }

    private static float smooth(float time) {
        float t = Mth.clamp(time, 0, 1);
        return t * t * (3 - 2 * t);
    }
}
