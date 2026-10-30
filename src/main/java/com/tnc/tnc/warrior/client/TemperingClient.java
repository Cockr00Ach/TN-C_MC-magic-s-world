package com.tnc.tnc.warrior.client;

import com.tnc.tnc.TNMod;
import com.tnc.tnc.warrior.InstructorEntity;
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
    private static boolean graduate, training, ground;
    private static int blocks, counterTicks, windTicks;
    private static int mode, completions, bestScore, bestReaction;
    /** 当前召出的教官（服务端下发）。 */
    private static String activeRole = "";
    /** 面板上选中的教官（纯客户端选择，点开始才发出去）。 */
    private static String selectedRole = "counter";
    private static long receivedAt;
    private static long displayTicks;

    private TemperingClient() {
    }

    public static boolean ground() {
        return ground;
    }

    public static int mode() {
        return ground ? mode : 0;
    }

    public static int selectedMode() {
        return mode;
    }

    public static int completions() {
        return completions;
    }

    public static int bestScore() {
        return bestScore;
    }

    public static int bestReaction() {
        return bestReaction;
    }

    public static int cap() {
        return com.tnc.tnc.warrior.WarriorTalentRules.cap(talent);
    }

    public static int talent() {
        return talent;
    }

    public static boolean graduate() {
        return graduate;
    }

    public static boolean training() {
        return training;
    }

    public static int blocks() {
        return blocks;
    }

    /** 服务端记录的在场教官；没有则空串。 */
    public static String activeRole() {
        return activeRole;
    }

    /** 面板选中的教官；点「开始练习」才发出去。 */
    public static String selectedRole() {
        return selectedRole;
    }

    public static void selectRole(String role) {
        if (role == null) return;
        for (String name : InstructorEntity.VARIANTS) {
            if (name.equals(role)) {
                selectedRole = name;
                return;
            }
        }
    }

    private static long now() {
        return displayTicks;
    }

    @SubscribeEvent
    public static void tick(TickEvent.ClientTickEvent event) {
        var mc = Minecraft.getInstance();
        if (event.phase == TickEvent.Phase.END && mc.level != null && !mc.isPaused()) displayTicks++;
    }

    private static int remaining(int ticks) {
        return (int) Math.max(0, ticks - Math.max(0, now() - receivedAt));
    }

    public static int counterTicks() {
        return remaining(counterTicks);
    }

    public static int windTicks() {
        return remaining(windTicks);
    }

    public static boolean enrolled() {
        return enrolled;
    }

    public static int points() {
        return points;
    }

    public static void accept(TemperingPacket packet) {
        talent = packet.talent();
        enrolled = packet.enrolled() && talent > 0;
        points = Math.min(cap(), TemperingRules.clamp(packet.points()));
        graduate = packet.graduate();
        training = packet.training() && enrolled;
        ground = packet.ground();
        mode = packet.mode();
        completions = packet.completions();
        bestScore = packet.bestScore();
        bestReaction = packet.bestReaction();
        blocks = packet.blocks();
        counterTicks = packet.counterTicks();
        windTicks = packet.windTicks();
        activeRole = packet.role() == null ? "" : packet.role();
        receivedAt = now();
        if (packet.open() && Minecraft.getInstance().player != null) {
            Minecraft.getInstance().setScreen(new WarriorPracticeScreen());
        }
    }

    @SubscribeEvent
    public static void logout(ClientPlayerNetworkEvent.LoggingOut event) {
        enrolled = false;
        points = 0;
        talent = 0;
        graduate = training = ground = false;
        blocks = counterTicks = windTicks = 0;
        receivedAt = displayTicks = 0;
        mode = completions = bestScore = bestReaction = 0;
        activeRole = "";
    }
}
