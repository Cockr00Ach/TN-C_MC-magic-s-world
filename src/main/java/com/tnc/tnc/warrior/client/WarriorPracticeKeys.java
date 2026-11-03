package com.tnc.tnc.warrior.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.tnc.tnc.TNMod;
import com.tnc.tnc.warrior.TemperingActionPacket;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.settings.KeyConflictContext;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

@Mod.EventBusSubscriber(modid=TNMod.MODID,value=Dist.CLIENT)
public final class WarriorPracticeKeys {
    public static final KeyMapping OPEN=new KeyMapping("key.tnc.warrior_practice",KeyConflictContext.IN_GAME,
        InputConstants.Type.KEYSYM,GLFW.GLFW_KEY_K,"key.categories.tnc");
    private WarriorPracticeKeys(){}
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event){
        if(event.phase!=TickEvent.Phase.END)return;
        boolean requested=false;while(OPEN.consumeClick())requested=true;
        var mc=Minecraft.getInstance();
        if(requested&&mc.player!=null&&mc.level!=null&&mc.screen==null){
            // 快捷键开的是武器图鉴；训练面板只从演武场中央的终端进（见 PracticeTerminalBlock）。
            // 先本地开屏再加一个既有的 VIEW 请求刷数据，回包会重建同一个界面而不是换掉它。
            TemperingClient.hintCodex();
            TemperingActionPacket.send(TemperingActionPacket.Action.VIEW);
            mc.setScreen(new WarriorCodexScreen());
        }
    }
    /**
     * 按键注册入口。由 {@code TNMod.ClientModEvents#onRegisterKeyMappings} 显式调用，
     * <b>故意不加</b> {@code @Mod.EventBusSubscriber}：同一个 MOD 事件总线里，嵌套类的
     * 自动订阅在本整合包里出现过"注册了却没被调用"的情况，显式调用最稳。
     */
    public static final class Registration{
        public static void register(RegisterKeyMappingsEvent event){event.register(OPEN);}
    }
}
