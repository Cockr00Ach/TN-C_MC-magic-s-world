package com.tnc.tnc.travel.client;

import com.tnc.tnc.Config;
import com.tnc.tnc.TNMod;
import com.tnc.tnc.travel.TravelClientState;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 附身时的速度感：按当前速度比例给一点视野扩张。
 *
 * <p>为什么按速度而不是"一进附身就扩"：站着不动也扩视野会让人以为自己在飞 ✗；
 * 而全速时扩 5° 左右，配合形态壳的拖尾，速度感是最直接的加分项 ✓。
 *
 * <p>开关在 {@code Config.travelFovBoost}（0 = 关闭），默认 5°，晕 3D 的玩家可以设 0。
 */
@Mod.EventBusSubscriber(modid = TNMod.MODID, value = Dist.CLIENT)
public final class TravelCamera {

    /** 到这个速度（格/秒）时视野扩张拉满。 */
    private static final double FULL_BOOST_SPEED = 16.0D;

    private TravelCamera() {
    }

    @SubscribeEvent
    public static void onComputeFov(ViewportEvent.ComputeFov event) {
        double boost = Config.travelFovBoost;
        if (boost <= 0.0D || !TravelClientState.active()) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) {
            return;
        }
        double speed = minecraft.player.getDeltaMovement().length() * 20.0D;
        float ratio = (float) Math.min(1.0D, speed / FULL_BOOST_SPEED);
        if (ratio <= 0.05F) {
            return;
        }
        event.setFOV(event.getFOV() + boost * ratio);
    }
}
