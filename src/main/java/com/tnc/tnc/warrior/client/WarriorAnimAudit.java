package com.tnc.tnc.warrior.client;

import com.tnc.tnc.TNMod;
import com.tnc.tnc.warrior.WarriorAnimation;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 玩家动作的客户端渲染审计（只在 {@code -Dtnc.warriorAnimAudit=true} 时跑）。
 *
 * <p>它做三件事：进存档后把镜头切到第三人称正面、依次播放每一个
 * {@link WarriorAnimation}、在每个动作中段截一张图；同时在动作播放后读一次
 * {@code HumanoidModel} 的手臂角度 —— 如果混入的姿势没生效，手臂会停在待机角度，
 * 报告里就能直接看出来。这样"动画到底有没有真的动"不靠肉眼猜。
 *
 * <p>跑法：{@code gradlew runClient -PwarriorAnimAudit}，报告写到
 * {@code run/warrior-anim-audit.json}，截图在 {@code run/screenshots/}。
 */
@Mod.EventBusSubscriber(modid = TNMod.MODID, value = Dist.CLIENT)
public final class WarriorAnimAudit {

    private static final WarriorAnimation[] ORDER = WarriorAnimation.values();
    private static final List<String> failures = new ArrayList<>();
    private static int boot;
    private static int index;
    private static int wait;
    private static boolean started;
    private static boolean saving;
    private static float idleArm;
    private static boolean idleCaptured;

    private WarriorAnimAudit() {
    }

    @SubscribeEvent
    public static void tick(TickEvent.ClientTickEvent event) {
        if (!Boolean.getBoolean("tnc.warriorAnimAudit") || Boolean.getBoolean("tnc.warriorMeshAudit") || event.phase != TickEvent.Phase.END) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null || mc.getOverlay() != null) {
            return;
        }
        if (!started) {
            if (++boot < 40) {
                return;
            }
            started = true;
            mc.options.pauseOnLostFocus = false;
            mc.options.hideGui = false;
            mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT);
            player.setYRot(0);
            player.setXRot(0);
            return;
        }
        if (saving) {
            return;
        }
        if (index >= ORDER.length) {
            finish(mc);
            return;
        }
        PlayerRenderer renderer = renderer(mc);
        if (renderer == null) {
            failures.add("no PlayerRenderer for the local player");
            index = ORDER.length;
            return;
        }
        if (!idleCaptured) {
            idleArm = renderer.getModel().rightArm.xRot;
            idleCaptured = true;
        }
        if (wait == 0) {
            WarriorAnimationClient.accept(player.getId(), ORDER[index], ORDER[index].durationTicks());
            wait = 3;
            return;
        }
        if (--wait > 0) {
            return;
        }
        float arm = renderer.getModel().rightArm.xRot;
        if (Math.abs(arm - idleArm) < 0.05f) {
            failures.add(ORDER[index].name() + " 没有改动手臂角度（idle=" + idleArm + ", now=" + arm + "）");
        }
        String name = "warrior-anim-" + ORDER[index].name().toLowerCase(Locale.ROOT) + ".png";
        saving = true;
        Screenshot.grab(mc.gameDirectory, name, mc.getMainRenderTarget(), message -> saving = false);
        index++;
    }

    private static PlayerRenderer renderer(Minecraft mc) {
        var renderer = mc.getEntityRenderDispatcher().getRenderer(mc.player);
        return renderer instanceof PlayerRenderer playerRenderer ? playerRenderer : null;
    }

    private static void finish(Minecraft mc) {
        try {
            List<String> lines = new ArrayList<>();
            for (WarriorAnimation animation : ORDER) {
                lines.add("    \"" + animation.name() + "\"");
            }
            Path report = mc.gameDirectory.toPath().resolve("warrior-anim-audit.json");
            Files.writeString(report, "{\n  \"animations\": " + ORDER.length
                    + ",\n  \"clips\": " + WarriorComboAnimations.all().size()
                    + ",\n  \"idleRightArm\": " + idleArm
                    + ",\n  \"failures\": " + failures.size()
                    + ",\n  \"failureDetails\": " + failures
                    + ",\n  \"list\": [\n" + String.join(",\n", lines) + "\n  ]\n}\n");
            System.out.println("TN-C WARRIOR ANIM AUDIT: animations=" + ORDER.length
                    + " clips=" + WarriorComboAnimations.all().size()
                    + " failures=" + failures + " report=" + report);
        } catch (Exception error) {
            System.out.println("TN-C WARRIOR ANIM AUDIT: failed to write report: " + error);
        }
        mc.stop();
    }
}
