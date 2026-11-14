package com.tnc.tnc.travel.client;

import com.tnc.tnc.TNMod;
import com.tnc.tnc.travel.TravelClientState;
import com.tnc.tnc.travel.TravelInputPacket;
import com.tnc.tnc.travel.TravelKeyHint;
import com.tnc.tnc.travel.TravelStaff;
import com.tnc.tnc.warrior.ComboTracker;
import com.tnc.tnc.warrior.client.WarriorComboKeys;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 元素附身的客户端输入。
 *
 * <p><b>不新建 Q 键位</b>：直接复用战士的 {@link WarriorComboKeys#LEFT}（默认 Q）。
 * 这样「长按 Q」在两个体系里是同一个物理键，玩家改键也只改一处；手持法杖时
 * {@code WarriorComboKeys.combatReady} 为 false，不会误发战士连招。
 *
 * <p>短按 Q 什么都不做（不位移、不丢杖）；按住 ≥ {@link ComboTracker#HOLD_TICKS} = 进入附身；
 * 附身中右键 = 形态技；松手 = 退出。Ctrl+Q 不接管，留给原版丢弃整组。
 */
@Mod.EventBusSubscriber(modid = TNMod.MODID, value = Dist.CLIENT)
public final class TravelKeys {

    private static long tick;
    private static boolean qWasDown;
    private static long qDownAt = -1L;
    private static boolean qHeldSent;
    private static long qKeepAlive;
    private static boolean useWasDown;

    private TravelKeys() {
    }

    @SubscribeEvent
    public static void onLoggingOut(net.minecraftforge.client.event.ClientPlayerNetworkEvent.LoggingOut event) {
        TravelClientState.clear();
        TravelAppearance.clear();
        reset();
    }

    @SubscribeEvent
    public static void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.START) {
            return;
        }
        tick++;
        TravelClientState.tick();
        TravelKeyHint.set(WarriorComboKeys.LEFT.getTranslatedKeyMessage().getString());
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.level == null) {
            TravelClientState.clear();
            TravelAppearance.clear();
            reset();
            return;
        }

        boolean ready = ready(minecraft);
        boolean qNow = WarriorComboKeys.LEFT.isDown();
        if (ready) {
            swallowConflicts(minecraft);
        }
        if (!ready) {
            if (qHeldSent) {
                TravelInputPacket.send(TravelInputPacket.EXIT);
            }
            qDownAt = -1L;
            qHeldSent = false;
            qWasDown = qNow;
            useWasDown = minecraft.options.keyUse.isDown();
            return;
        }

        if (qNow && !qWasDown) {
            qDownAt = tick;
            qHeldSent = false;
        }
        if (qDownAt >= 0) {
            if (!qNow) {
                // 短按 Q 什么都不做：不再有“元素步”这种丑位移。
                if (qHeldSent) {
                    TravelInputPacket.send(TravelInputPacket.EXIT);
                }
                qDownAt = -1L;
                qHeldSent = false;
            } else if (!qHeldSent && tick - qDownAt >= ComboTracker.HOLD_TICKS) {
                TravelInputPacket.send(TravelInputPacket.ENTER);
                qHeldSent = true;
                qKeepAlive = tick;
            } else if (qHeldSent && tick - qKeepAlive >= 10L) {
                TravelInputPacket.send(TravelInputPacket.KEEPALIVE);
                qKeepAlive = tick;
            }
        }
        qWasDown = qNow;

        boolean useNow = minecraft.options.keyUse.isDown();
        if (TravelClientState.active() && useNow && !useWasDown) {
            TravelInputPacket.send(TravelInputPacket.SKILL);
        }
        useWasDown = useNow;
    }

    /** 在游戏里、没开界面、主手是元素法杖、且没按 Ctrl。 */
    private static boolean ready(Minecraft minecraft) {
        if (minecraft.screen != null || minecraft.getOverlay() != null || !minecraft.isWindowActive()) {
            return false;
        }
        if (Screen.hasControlDown()) {
            return false;   // Ctrl+Q = 原版丢弃整组，不抢
        }
        return TravelStaff.of(minecraft.player.getMainHandItem()) != null;
    }

    /** 吃掉与 Q 同键的原版动作（默认丢弃），攻击/使用两个鼠标键位不动。 */
    private static void swallowConflicts(Minecraft minecraft) {
        if (WarriorComboKeys.LEFT.isUnbound()) {
            return;
        }
        for (KeyMapping mapping : minecraft.options.keyMappings) {
            if (mapping == WarriorComboKeys.LEFT
                    || mapping == minecraft.options.keyAttack
                    || mapping == minecraft.options.keyUse) {
                continue;
            }
            if (!WarriorComboKeys.LEFT.getKey().equals(mapping.getKey())) {
                continue;
            }
            while (mapping.consumeClick()) {
                // 只清点击计数；原版 handleKeybinds 在 START 之后
            }
        }
    }

    /** 附身中/长按 Q 未松手时，右键与左键都不许穿透成原版交互/攻击。 */
    @SubscribeEvent
    public static void onInteraction(InputEvent.InteractionKeyMappingTriggered event) {
        if (!(event.isUseItem() || event.isAttack())) {
            return;
        }
        if (TravelClientState.active() || qDownAt >= 0) {
            event.setCanceled(true);
            event.setSwingHand(false);
        }
    }

    private static void reset() {
        qDownAt = -1L;
        qHeldSent = false;
        qWasDown = false;
        useWasDown = false;
    }
}
