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
    public static void accept(UUID player, float scale) { SCALES.put(player, scale); }
    public static double get(UUID player) { return SCALES.getOrDefault(player, 1f); }
    @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut event) { SCALES.clear(); }
}
