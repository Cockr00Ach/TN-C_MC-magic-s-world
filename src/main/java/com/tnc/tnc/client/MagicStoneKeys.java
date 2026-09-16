package com.tnc.tnc.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.tnc.tnc.TNMod;
import com.tnc.tnc.network.MagicStoneNetwork;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

/**
 * HUD 入口的快捷键 —— 按一下打开魔法石界面。
 *
 * <p>为什么 HUD 上的图标必须是"图标 + 快捷键"而不是"点图标"：
 * 没开界面时鼠标被锁定，HUD 收不到光标位置（详见 {@link MagicStoneHud}）。
 *
 * <p>默认 <b>V</b>。注册成正常的 {@link KeyMapping} 而不是自己监听原始按键，
 * 好处是玩家可以在「选项 → 控制」里改键，按键冲突也会被游戏自己提示出来。
 */
@Mod.EventBusSubscriber(modid = TNMod.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class MagicStoneKeys {

    /** 控制设置里的分类名（翻译在语言文件里）。 */
    public static final String CATEGORY = "key.categories.tnc";

    public static final KeyMapping OPEN_MAGIC_STONE = new KeyMapping(
            "key.tnc.open_magic_stone",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_V,
            CATEGORY);

    private MagicStoneKeys() {
    }

    /**
     * 挂到 mod 事件总线上（由 {@code TNMod.ClientModEvents} 调用）。
     *
     * <p>注意这里<b>故意不加 {@code @SubscribeEvent}</b>：本类是 FORGE 总线的订阅者，
     * 加上注解会被拿去 FORGE 总线注册，而这个事件属于 MOD 总线。
     */
    public static void register(RegisterKeyMappingsEvent event) {
        event.register(OPEN_MAGIC_STONE);
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) {
            return;
        }
        // consumeClick 必须每次都吃掉，否则按键会一直攒着、之后一次性弹好几次
        while (OPEN_MAGIC_STONE.consumeClick()) {
            // 开着别的界面时（比如物品栏）不抢：那种情况下该由界面自己处理
            if (minecraft.screen == null) {
                MagicStoneNetwork.requestSync();
                minecraft.setScreen(new MagicStoneScreen());
            }
        }
    }
}
