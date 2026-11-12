package com.tnc.tnc.warrior.client;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import com.tnc.tnc.TNMod;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = TNMod.MODID, value = Dist.CLIENT)
public final class WarriorPhantomScales {
    private static final Map<UUID, Float> SCALES = new HashMap<>();
    private static final Map<UUID, Integer> POINTS = new HashMap<>();
    public static void accept(UUID player, float scale) { accept(player,scale,0); }
    public static void accept(UUID player, float scale,int points) {
        SCALES.put(player,Math.min(scale,(float)com.tnc.tnc.warrior.WarriorReach.MAX_PEAK));POINTS.put(player,com.tnc.tnc.warrior.TemperingRules.clamp(points));
    }
    public static double get(UUID player) { return SCALES.getOrDefault(player, 1f); }
    public static int points(UUID player) { return POINTS.getOrDefault(player,0); }
    @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut event) { SCALES.clear();POINTS.clear(); }
}
