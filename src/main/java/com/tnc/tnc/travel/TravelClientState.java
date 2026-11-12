package com.tnc.tnc.travel;

import com.tnc.tnc.magic.Element;

/**
 * 客户端只读的附身状态（服务端 {@link TravelStatePacket} 推下来的）。
 *
 * <p>HUD 与输入共用这一份；不在客户端自己算“我进了没有”，
 * 所以服务端拒绝时客户端不会出现“我明明在飞但服务器不认”的鬼状态。
 */
public final class TravelClientState {

    private static int element = -1;
    private static int tier;
    private static int ticksLeft;
    private static int totalTicks;
    private static int cooldownTicks;
    private static int skillCooldownTicks;

    private TravelClientState() {
    }

    public static void accept(TravelStatePacket packet) {
        element = packet.element();
        tier = packet.tier();
        ticksLeft = packet.ticksLeft();
        totalTicks = packet.totalTicks();
        cooldownTicks = packet.cooldownTicks();
        skillCooldownTicks = packet.skillCooldownTicks();
    }

    /** 客户端每 tick 本地递减；服务端每 10 tick 会校正一次。 */
    public static void tick() {
        if (ticksLeft > 0) {
            ticksLeft--;
        }
        if (cooldownTicks > 0) {
            cooldownTicks--;
        }
        if (skillCooldownTicks > 0) {
            skillCooldownTicks--;
        }
        if (ticksLeft <= 0) {
            element = -1;
        }
    }

    public static boolean active() {
        return element >= 0 && element < Element.values().length;
    }

    public static Element element() {
        return active() ? Element.values()[element] : null;
    }

    public static int tier() {
        return tier;
    }

    public static int ticksLeft() {
        return Math.max(0, ticksLeft);
    }

    public static int totalTicks() {
        return Math.max(1, totalTicks);
    }

    public static int cooldownTicks() {
        return Math.max(0, cooldownTicks);
    }

    public static int skillCooldownTicks() {
        return Math.max(0, skillCooldownTicks);
    }

    public static void clear() {
        element = -1;
        tier = 0;
        ticksLeft = 0;
        totalTicks = 0;
        cooldownTicks = 0;
        skillCooldownTicks = 0;
    }
}
