package com.tnc.tnc.skyacademy.client;

import com.tnc.tnc.mana.sight.client.ManaSightClient;
import com.tnc.tnc.skyacademy.FirstSightScript;
import com.tnc.tnc.skyacademy.SkyAcademyWorld;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.*;
import net.minecraftforge.client.event.sound.PlaySoundEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** The invitation, player response and reveal remain one continuous scene. */
@Mod.EventBusSubscriber(modid = "tnc", value = Dist.CLIENT)
public final class FirstSightScene {
    private enum Phase { IDLE, CONVERSATION, INVITATION, BLACKOUT, NIGHT, BLINK, REVEALED }
    private static Phase phase = Phase.IDLE;
    private static net.minecraft.client.multiplayer.ClientLevel world;
    private static ArmorStand camera;
    private static CameraType previousCamera;
    private static Vec3 mentor;
    private static Vec3 playerEye;
    private static String name = "";
    private static int age;
    private static int revealedTicks;
    private static int songTicks;
    private static double transitionSeconds;
    private static long lastNanos;
    private static double blackoutSeconds;
    private static SoundInstance music;
    private static boolean first;
    private static boolean paused;

    public static boolean locked() { return phase != Phase.IDLE && phase != Phase.REVEALED; }
    public static boolean active() { return phase != Phase.IDLE; }
    public static boolean musicActive() { return music != null && Minecraft.getInstance().getSoundManager().isActive(music); }
    private static boolean revealing() { return phase == Phase.REVEALED && revealedTicks < 200; }

    public static void start(CompoundTag packet) {
        var mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;
        close();
        world = mc.level;
        name = packet.getString("PlayerName");
        first = packet.getBoolean("FirstSight");
        mentor = Vec3.atBottomCenterOf(BlockPos.of(packet.getLong("Origin"))
                .offset(SkyAcademyWorld.SITES.get("stargaze"))).add(0, 1.5, 0);
        playerEye = Vec3.atBottomCenterOf(BlockPos.of(packet.getLong("Origin")).offset(SkyAcademyWorld.SITES.get("stargaze")).south(6)).add(0, mc.player.getEyeHeight(), 0);
        age = -140; revealedTicks = songTicks = 0;
        mc.setScreen(null);
        ManaSightClient.setEnabled(false);
        previousCamera = mc.options.getCameraType();
        camera = new ArmorStand(world, playerEye.x, playerEye.y, playerEye.z);
        camera.setInvisible(true);
        camera.setNoGravity(true);
        phase = Phase.CONVERSATION;
        updateCamera();
        mc.options.setCameraType(CameraType.FIRST_PERSON);
        mc.setCameraEntity(camera);

    }

    /** Called by the real remappable mana-sight key before its normal toggle. */
    public static boolean consumeSightKey() {
        if (phase == Phase.IDLE || phase == Phase.REVEALED) return false;
        if (phase == Phase.INVITATION && songTicks >= 680) {
            if (!first) {
                ManaSightClient.setEnabled(true);
                phase = Phase.REVEALED;
                return true;
            }
            phase = Phase.BLACKOUT;
            blackoutSeconds = 0;
            lastNanos = System.nanoTime();
            paused = false;
        }
        return true;
    }

    private static void play(String id, boolean loop) {
        var mc = Minecraft.getInstance();
        if (music != null) mc.getSoundManager().stop(music);
        mc.getMusicManager().stopPlaying();
        mc.getSoundManager().stop(null, SoundSource.MUSIC);
        music = new SimpleSoundInstance(ResourceLocation.parse("tnc:" + id),
                SoundSource.MUSIC, 1, 1, RandomSource.create(), loop, 0,
                SoundInstance.Attenuation.NONE, 0, 0, 0, true);
        mc.getSoundManager().play(music);
    }

    private static void releaseCamera() {
        var mc = Minecraft.getInstance();
        if (camera != null && mc.getCameraEntity() == camera) mc.setCameraEntity(mc.player);
        if (previousCamera != null) mc.options.setCameraType(previousCamera);
        camera = null;
        previousCamera = null;
    }

    public static void close() {
        releaseCamera();
        var mc = Minecraft.getInstance();
        if (music != null) mc.getSoundManager().stop(music);
        music = null;
        phase = Phase.IDLE;
        world = null;
    }

    private static void updateCamera() {
        var mc = Minecraft.getInstance();
        if (camera == null || mc.player == null) return;
        int line = FirstSightScript.lineAt(name, Math.max(0, age));
        // No reverse shot of the player. The landscape rig sits beyond the platform lip.
        Vec3 eye, focus;
        switch(line % 3) {
            case 1 -> {
                eye=playerEye.add(0,.7,2.5);
                focus=eye.add(0,-22,85);
            }
            case 2 -> {
                eye=playerEye.add(0,1.2,2.5);
                focus=eye.add(0,32,85);
            }
            default -> {
                eye=mentor.add(2.3,.35,3.6);
                focus=mentor;
            }
        }
        mc.setCameraEntity(camera);
        var delta = focus.subtract(eye);
        camera.setPos(eye.x, eye.y - camera.getEyeHeight(), eye.z);
        camera.setYRot((float) Math.toDegrees(Math.atan2(-delta.x, delta.z)));
        camera.setXRot((float) -Math.toDegrees(Math.atan2(delta.y, delta.horizontalDistance())));
        camera.xo = camera.getX(); camera.yo = camera.getY(); camera.zo = camera.getZ();
        camera.yRotO = camera.getYRot(); camera.xRotO = camera.getXRot();
    }

    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !active()) return;
        var mc = Minecraft.getInstance();
        if (mc.level != world || mc.player == null || !mc.player.isAlive()
                || (phase != Phase.REVEALED && mc.player.position().distanceToSqr(mentor) > 48 * 48)) { close(); return; }
        long now = System.nanoTime();
        if (phase == Phase.BLACKOUT || phase == Phase.NIGHT || phase == Phase.BLINK) {
            if (!mc.isPaused() && !paused) blackoutSeconds += (now - lastNanos) / 1_000_000_000.0;
            lastNanos = now;
            paused = mc.isPaused();
        }
        if (mc.isPaused()) return;
        if (age >= 0) songTicks++;
        if (phase == Phase.CONVERSATION) {
            if (++age == 0) play("music.first_sight.full", false);
            if (age >= FirstSightScript.duration(name)) {
                // Do not expose the real first-person camera until the authoritative
                // edge teleport has arrived. Keep the last film shot during transit.
                Vec3 feet=mentor.add(0,-1.5,6);
                Vec3 difference=mc.player.position().subtract(feet);
                int wait=age-FirstSightScript.duration(name);
                if(wait==0 || (wait%20==0 && difference.lengthSqr()>.25))
                    com.tnc.tnc.skyacademy.AcademyNetwork.action(0,"sight_edge");
                boolean positioned=difference.horizontalDistanceSqr()<.25 && Math.abs(difference.y)<1.5;
                if(wait<2 || !positioned) {
                    if(wait>=100) {
                        close();
                        mc.player.displayClientMessage(Component.literal("观景定位未完成，已恢复操作。请重新与瑟兰交谈。"),false);
                    }
                    return;
                }
                releaseCamera();
                mc.options.setCameraType(CameraType.FIRST_PERSON);
                mc.player.setYRot(0); mc.player.setXRot(12);
                phase = Phase.INVITATION;
            } else updateCamera();
        } else if (phase == Phase.INVITATION && songTicks >= 680) {
            consumeSightKey();
        } else if (phase == Phase.BLACKOUT && blackoutSeconds >= 3) {
            phase = Phase.NIGHT;
            blackoutSeconds = 0;
        } else if (phase == Phase.NIGHT && blackoutSeconds >= 1.6) {
            phase = Phase.BLINK;
            blackoutSeconds = 0;
        } else if (phase == Phase.BLINK) {
            if (blackoutSeconds >= .45) ManaSightClient.setEnabled(true);
            if (blackoutSeconds >= 1.05) phase = Phase.REVEALED;
        } else if (phase == Phase.REVEALED && ++revealedTicks > 220 && songTicks > 2760) close();
    }

    @SubscribeEvent public static void input(MovementInputUpdateEvent event) {
        if (locked()) {
            event.getInput().forwardImpulse = 0;
            event.getInput().leftImpulse = 0;
            event.getInput().jumping = false;
            event.getInput().shiftKeyDown = false;
        }
    }

    @SubscribeEvent public static void interactions(InputEvent.InteractionKeyMappingTriggered event) {
        if (locked()) { event.setSwingHand(false); event.setCanceled(true); }
    }
    @SubscribeEvent(priority = net.minecraftforge.eventbus.api.EventPriority.LOWEST)
    public static void angles(ViewportEvent.ComputeCameraAngles event) {
        if (camera != null) { event.setYaw(camera.getYRot()); event.setPitch(camera.getXRot()); event.setRoll(0); }
    }
    @SubscribeEvent public static void hideActor(RenderPlayerEvent.Pre event) {
        if(locked() && event.getEntity()==Minecraft.getInstance().player) event.setCanceled(true);
    }
    @SubscribeEvent public static void hand(RenderHandEvent event) {
        if (locked() || revealing()) event.setCanceled(true);
    }

    @SubscribeEvent public static void music(PlaySoundEvent event) {
        if (active() && event.getSound() != null && event.getSound() != music
                && (event.getSound().getSource() == SoundSource.MUSIC
                || event.getSound().getSource() == SoundSource.RECORDS)) event.setSound(null);
    }

    @SubscribeEvent(priority = net.minecraftforge.eventbus.api.EventPriority.HIGHEST)
    public static void cinematicGui(RenderGuiEvent.Pre event) {
        if (locked() || revealing()) { event.setCanceled(true); draw(event.getGuiGraphics()); }
    }
    @SubscribeEvent(priority = net.minecraftforge.eventbus.api.EventPriority.LOWEST)
    public static void gui(RenderGuiEvent.Post event) {
        if (!locked() && !revealing()) draw(event.getGuiGraphics());
    }
    private static void draw(net.minecraft.client.gui.GuiGraphics graphics) {
        if (!active()) return;
        var mc = Minecraft.getInstance();
        int width = graphics.guiWidth(), height = graphics.guiHeight();
        if (phase == Phase.BLACKOUT) {
            graphics.fill(0, 0, width, height, 0xff000000);
            String text = FirstSightScript.blackoutCaption(blackoutSeconds);
            double start=blackoutSeconds<1.1?.25:1.25;
            double duration=blackoutSeconds<1.1?.7:1.6;
            CinematicCard.draw(graphics, Component.literal(text),
                    CinematicCard.envelope(blackoutSeconds-start,duration,.16));
            return;
        }
        if (phase == Phase.NIGHT) return;
        if (phase == Phase.BLINK) {
            double seconds=blackoutSeconds;
            double closure=seconds<.35?seconds/.35:seconds<.55?1:1-(seconds-.55)/.5;
            closure=Math.max(0,Math.min(1,closure));
            // Curved upper and lower lids close towards a horizontal slit.
            for(int x=0;x<width;x+=4){
                double normalized=(x-width*.5)/(width*.5);
                int lid=(int)(height*.5*closure + height*.10*normalized*normalized*closure*(1-closure));
                graphics.fill(x,0,Math.min(width,x+4),lid,0xff000000);
                graphics.fill(x,height-lid,Math.min(width,x+4),height,0xff000000);
            }
            return;
        }
        if (phase == Phase.CONVERSATION && age < 0) {
            graphics.fill(0, 0, width, height, 0xff000000);
            CinematicCard.draw(graphics, Component.literal("戴上耳机，体验更佳哦"), CinematicCard.envelope((age+140)/20.,7,.7));
            return;
        }
        if (phase == Phase.CONVERSATION) {
            graphics.fill(0, 0, width, 24, 0xff000000);
            graphics.fill(0, height - 64, width, height, 0xff000000);
            var lines = mc.font.split(Component.literal(FirstSightScript.lines(name)
                    .get(FirstSightScript.lineAt(name, age)).text()), Math.min(430, width - 48));
            graphics.drawCenteredString(mc.font, "瑟兰", width / 2, height - 54, 0xffd6c09a);
            for (int i = 0; i < lines.size(); i++)
                graphics.drawString(mc.font, lines.get(i), (width - mc.font.width(lines.get(i))) / 2,
                        height - 36 + i * 12, 0xffeee8df, false);
        } else if (phase == Phase.INVITATION) {
            graphics.drawCenteredString(mc.font, Component.literal("静下来，感受魔力")
                    , width / 2, height - 45, 0xffe2e8e3);
        } else if (revealedTicks >= 220 && revealedTicks <= 420) {
            graphics.drawCenteredString(mc.font, Component.literal("已学会魔力视野 · 按 ").append(ManaSightClient.KEY.getTranslatedKeyMessage()).append(" 自由开启或关闭"), width / 2, height - 45, 0xffeee8df);
        }
    }

    private FirstSightScene() {}
}
