package com.tnc.tnc.inkshader;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterClientCommandsEvent;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.*;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.loading.FMLPaths;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Every client keeps its own original preference; changing dimensions never changes another player's config. */
@Mod.EventBusSubscriber(modid="tnc",value=Dist.CLIENT)
public final class InkShaderClient {
    private static final Logger LOG=LoggerFactory.getLogger("TN-C/ink-shader");
    private static InkShaderSession session;private static InkShaderFiles files;private static InkShaderOculus backend;
    private static int ticks;private static boolean unavailable,reported,initialized,disconnected;
    private InkShaderClient(){}

    @SubscribeEvent(priority=EventPriority.LOW) public static void tick(TickEvent.ClientTickEvent event){
        if(event.phase!=TickEvent.Phase.END)return;var mc=Minecraft.getInstance();
        if(mc.getOverlay()!=null)return;
        if(!initialized&&++ticks%20==0)initialize();
        if(session==null||session.failed())return;
        if(disconnected&&mc.level!=null)return;
        if(mc.level==null){
            // getConnection() can itself be null during dimension reconstruction. Use the actual logout signal.
            if(disconnected||mc.screen instanceof net.minecraft.client.gui.screens.TitleScreen||mc.screen instanceof net.minecraft.client.gui.screens.DisconnectedScreen)advance(false);
            return;
        }
        boolean ink=mc.level.dimension().location().toString().equals("tnc:ink_realm");
        if(ink!=session.active()||mc.level.getGameTime()%10==0)advance(ink);
    }
    @SubscribeEvent public static void login(ClientPlayerNetworkEvent.LoggingIn event){disconnected=false;}
    @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut event){disconnected=true;}
    private static void initialize(){
        if(unavailable)return;
        try {
            backend=new InkShaderOculus();files=new InkShaderFiles(FMLPaths.CONFIGDIR.get());session=new InkShaderSession(backend,files);
            session.mode(files.mode());var saved=files.saved();
            if(saved.isPresent()){session.recover(saved.get());LOG.info("Restored the shader preference from an interrupted Ink session");}
            initialized=true;
        }catch(ClassNotFoundException|NoClassDefFoundError e){unavailable=true;initialized=true;}
        catch(IllegalStateException e){session=null;/* Oculus can still be completing startup; retry after twenty ticks. */}
        catch(Exception e){session=null;initialized=true;failure(e);}
    }
    private static void advance(boolean ink){try{session.tick(ink);}catch(Exception e){failure(e);}}
    private static void failure(Exception e){
        LOG.error("Ink shader switching stopped; the original preference was retained for recovery",e);
        if(!reported&&Minecraft.getInstance().player!=null){reported=true;message("水墨维度光影切换失败，已暂停自动切换；请查看日志。");}
    }
    private static void message(String text){var player=Minecraft.getInstance().player;if(player!=null)player.displayClientMessage(Component.literal(text),false);}
    static boolean mode(InkShaderSession.Mode mode){
        if(!initialized)initialize();
        if(session==null||session.failed()){message("Oculus 未就绪，或光影恢复失败；本次没有更改设置。");return false;}
        try {
            if(mode==InkShaderSession.Mode.INK&&!java.nio.file.Files.isRegularFile(backend.shaderDirectory().resolve(InkShaderSession.INK_PACK))){message("未找到水墨光影包："+InkShaderSession.INK_PACK);return false;}
            files.mode(mode);session.mode(mode);
            var level=Minecraft.getInstance().level;advance(level!=null&&level.dimension().location().toString().equals("tnc:ink_realm"));
            message(mode==InkShaderSession.Mode.OFF?"水墨维度：自动关闭光影，离开后恢复原状态。":"水墨维度：使用水墨光影，离开后恢复原状态。");return !session.failed();
        }catch(Exception e){failure(e);return false;}
    }
    @SubscribeEvent public static void commands(RegisterClientCommandsEvent event){
        var root=net.minecraft.commands.Commands.literal("tncinkshader");
        root.then(net.minecraft.commands.Commands.literal("off").executes(c->mode(InkShaderSession.Mode.OFF)?1:0));
        root.then(net.minecraft.commands.Commands.literal("ink").executes(c->mode(InkShaderSession.Mode.INK)?1:0));
        root.then(net.minecraft.commands.Commands.literal("status").executes(c->{
            message(session==null?"水墨维度光影切换：等待 Oculus。":"水墨维度光影："+(session.mode()==InkShaderSession.Mode.OFF?"自动关闭":"水墨光影")+(session.active()?"；离开后恢复原状态。":"。"));return 1;}));
        event.getDispatcher().register(root);
    }
    static InkShaderSession auditSession(){return session;}
    static InkShaderOculus auditBackend(){return backend;}
    static InkShaderFiles auditFiles(){return files;}
}
