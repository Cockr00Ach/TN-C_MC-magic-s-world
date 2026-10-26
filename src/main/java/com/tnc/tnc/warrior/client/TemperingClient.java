package com.tnc.tnc.warrior.client;

import com.tnc.tnc.TNMod;
import com.tnc.tnc.warrior.TemperingPacket;
import com.tnc.tnc.warrior.TemperingRules;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraft.client.Minecraft;
import net.minecraftforge.event.TickEvent;

@Mod.EventBusSubscriber(modid = TNMod.MODID, value = Dist.CLIENT)
public final class TemperingClient {
    private static boolean enrolled;
    private static int points;
    private static int talent;
    private static boolean graduate,training,ground;
    public static boolean ground(){return ground;}
    private static int blocks,counterTicks,windTicks;
    private static long receivedAt;
    private static long displayTicks;
    public static int cap() { return com.tnc.tnc.warrior.WarriorTalentRules.cap(talent); }
    public static int talent() {return talent;}
    public static boolean graduate() {return graduate;}
    public static boolean training() {return training;}
    public static int blocks() {return blocks;}
    private static long now() {return displayTicks;}
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event) {
        var mc=Minecraft.getInstance();
        if(event.phase==TickEvent.Phase.END&&mc.level!=null&&!mc.isPaused())displayTicks++;
    }
    private static int remaining(int ticks) {return (int)Math.max(0,ticks-Math.max(0,now()-receivedAt));}
    public static int counterTicks() {return remaining(counterTicks);}
    public static int windTicks() {return remaining(windTicks);}
    private TemperingClient() {}
    public static boolean enrolled() { return enrolled; }
    public static int points() { return points; }
    public static void accept(TemperingPacket packet) {
        talent = packet.talent(); enrolled = packet.enrolled() && talent > 0;
        points = Math.min(cap(), TemperingRules.clamp(packet.points()));
        graduate=packet.graduate();training=packet.training()&&enrolled;ground=packet.ground();
        blocks=packet.blocks();counterTicks=packet.counterTicks();windTicks=packet.windTicks();receivedAt=now();
        if(packet.open()&&Minecraft.getInstance().player!=null)Minecraft.getInstance().setScreen(new WarriorPracticeScreen());
    }
    @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut event) {
        enrolled = false; points = 0; talent = 0;
        graduate=training=ground=false;blocks=counterTicks=windTicks=0;receivedAt=displayTicks=0;
    }
}
