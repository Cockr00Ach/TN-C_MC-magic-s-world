package com.tnc.tnc.warrior.client;

import com.tnc.tnc.TNMod;
import com.tnc.tnc.warrior.TemperingPacket;
import com.tnc.tnc.warrior.WarriorWavePacket;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Opt-in screenshots in a copied development world; never active in normal play. */
@Mod.EventBusSubscriber(modid = TNMod.MODID, value = Dist.CLIENT)
public final class WarriorMeshAudit {
    private static int boot, scene, frame;
    private static volatile boolean saving;
    private static final String[] NAMES = {"sword-base", "sword-phantom", "dragon-phantom", "air-blade", "ground-wave", "dragon-left", "dragon-render"};
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event) {
        if (!Boolean.getBoolean("tnc.warriorMeshAudit") || event.phase != TickEvent.Phase.END) return;
        var mc = Minecraft.getInstance();
        if (++boot > 2400) { mc.stop(); return; }
        if (mc.level == null || mc.player == null || mc.getOverlay() != null || saving) return;
        if (boot < 100) return;
        if (scene >= NAMES.length) { System.out.println("WARRIOR MESH AUDIT: " + NAMES.length + " screenshots complete"); mc.stop(); return; }
        mc.setScreen(null);
        mc.options.pauseOnLostFocus = false;
        mc.options.hideGui = true;
        // 第 7 幕看的是"龙在身后飞出去"，所以从背后拍；其余几幕从正面拍手上的刃
        mc.options.setCameraType(scene == 6 ? CameraType.THIRD_PERSON_BACK : CameraType.THIRD_PERSON_FRONT);
        mc.player.setYRot(0); mc.player.setXRot(12); mc.player.yBodyRot = 0; mc.player.yHeadRot = 0;
        mc.player.setMainArm(scene == 5 ? net.minecraft.world.entity.HumanoidArm.LEFT : net.minecraft.world.entity.HumanoidArm.RIGHT);
        mc.player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,
                new ItemStack(scene == 2 || scene == 5 || scene == 6 ? TNMod.DRAGON_SWORD.get() : Items.IRON_SWORD));
        TemperingClient.accept(new TemperingPacket(true, scene == 0 ? 0 : 10000, 6));
        frame++;
        if (frame == 35 && (scene == 3 || scene == 4)) {
            boolean ground = scene == 4;
            Vec3 origin = mc.player.position().add(-2.8, ground ? .12 : .9, 1.1);
            WarriorWaveRenderer.accept(new WarriorWavePacket(mc.level.dimension().location(), origin,
                    new Vec3(1, 0, 0), ground, .55, 9, 1.8, ground ? 0xFFD481 : 0xC9F5FF,
                    ground ? 0x79502C : 0x356CB8, mc.level.getGameTime()));
        }
        // 龙：30 tick 时请求一条，46 tick 时截图 —— 正好是"冲出去一半到全张开"的那几帧
        if (scene == 6 && frame == 30) {
            WarriorDragonRenderer.request(mc.player.getId());
        }
        if (frame == (scene == 6 ? 46 : 37)) {
            saving = true;
            Screenshot.grab(mc.gameDirectory, "warrior-mesh-" + NAMES[scene] + ".png", mc.getMainRenderTarget(),
                    message -> saving = false);
            scene++; frame = 0;
        }
    }
}
