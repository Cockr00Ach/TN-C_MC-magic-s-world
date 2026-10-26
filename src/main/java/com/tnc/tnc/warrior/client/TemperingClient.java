package com.tnc.tnc.warrior.client;

import com.tnc.tnc.TNMod;
import com.tnc.tnc.warrior.TemperingPacket;
import com.tnc.tnc.warrior.TemperingRules;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = TNMod.MODID, value = Dist.CLIENT)
public final class TemperingClient {
    private static boolean enrolled;
    private static int points;
    private static int talent;
    public static int cap() { return com.tnc.tnc.warrior.WarriorTalentRules.cap(talent); }
    private TemperingClient() {}
    public static boolean enrolled() { return enrolled; }
    public static int points() { return points; }
    public static void accept(TemperingPacket packet) {
        talent = packet.talent(); enrolled = packet.enrolled() && talent > 0;
        points = Math.min(cap(), TemperingRules.clamp(packet.points()));
    }
    @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut event) {
        enrolled = false; points = 0; talent = 0;
    }
}
