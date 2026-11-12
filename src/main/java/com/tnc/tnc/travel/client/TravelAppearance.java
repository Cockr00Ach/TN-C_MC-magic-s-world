package com.tnc.tnc.travel.client;

import com.tnc.tnc.magic.Element;
import com.tnc.tnc.travel.TravelAppearancePacket;
import com.tnc.tnc.travel.TravelClientState;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;

import java.util.HashMap;
import java.util.Map;

/** 客户端缓存：谁正在以什么元素形态显示。 */
public final class TravelAppearance {

    private static final Map<Integer, Integer> ELEMENTS = new HashMap<>();

    private TravelAppearance() {
    }

    public static void accept(TravelAppearancePacket packet) {
        if (!packet.active()
                || packet.element() < 0
                || packet.element() >= Element.values().length) {
            ELEMENTS.remove(packet.entityId());
            return;
        }
        ELEMENTS.put(packet.entityId(), packet.element());
    }

    /**
     * 本地玩家以 {@link TravelClientState} 为准，避免“状态包还没到、外观包先到”
     * 造成自己短暂显示成旧形态；其他玩家才走外观缓存。
     */
    public static int elementOf(Player player) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == player) {
            Element local = TravelClientState.element();
            return local == null ? -1 : local.ordinal();
        }
        Integer element = ELEMENTS.get(player.getId());
        return element == null ? -1 : element;
    }

    public static void clear() {
        ELEMENTS.clear();
    }
}
