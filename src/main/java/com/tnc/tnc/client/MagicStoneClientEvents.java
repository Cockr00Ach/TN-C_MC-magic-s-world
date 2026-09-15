package com.tnc.tnc.client;

import com.tnc.tnc.TNMod;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 把「魔法石」入口挂到物品栏界面上。
 *
 * <p>用 Forge 的界面事件而不是 Mixin —— 少一套 mixin/refmap 配置，也不会和包里别的
 * 界面 mod（FancyMenu / ModernUI / 各种 HUD）抢屏幕。
 */
@Mod.EventBusSubscriber(modid = TNMod.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class MagicStoneClientEvents {

    /** 放在玩家模型和右上角合成栏之间的空档里（设计文档：显示在物品栏中间）。 */
    private static final int OFFSET_X = 81;
    private static final int OFFSET_Y = 44;

    @SubscribeEvent
    public static void onScreenInit(ScreenEvent.Init.Post event) {
        if (event.getScreen() instanceof InventoryScreen inventory) {
            event.addListener(new MagicStoneButton(
                    inventory.getGuiLeft() + OFFSET_X,
                    inventory.getGuiTop() + OFFSET_Y));
        }
    }
}
