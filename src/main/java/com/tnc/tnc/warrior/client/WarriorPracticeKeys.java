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
        if(requested&&mc.player!=null&&mc.level!=null&&mc.screen==null)TemperingActionPacket.send(TemperingActionPacket.Action.VIEW);
    }
    @Mod.EventBusSubscriber(modid=TNMod.MODID,value=Dist.CLIENT,bus=Mod.EventBusSubscriber.Bus.MOD)
    public static final class Registration{
        @SubscribeEvent public static void register(RegisterKeyMappingsEvent event){event.register(OPEN);}
    }
}
