package com.tnc.tnc.journey.client;

import com.tnc.tnc.journey.*;
import java.nio.file.*;
import java.util.*;
import net.minecraft.client.*;
import net.minecraft.client.gui.components.EditBox;
import net.minecraftforge.event.TickEvent;

/** Opt-in full-client input/packet/save/close regression, in the disposable save. */
public final class NamingNativeAudit {
    private static int ticks;
    private static boolean started,saving;
    private static volatile boolean serverChecked;
    private static final List<String> errors=new ArrayList<>(),shots=new ArrayList<>();
    private static final Map<String,Boolean> checks=new LinkedHashMap<>();
    private static Path folder(){return Path.of(System.getProperty("tnc.dungeonAuditOutput"));}
    public static void tick(TickEvent.ClientTickEvent e)throws Exception{
        if(e.phase!=TickEvent.Phase.END)return;var mc=Minecraft.getInstance();
        if(mc.player==null||mc.level==null||mc.getOverlay()!=null||saving)return;
        if(!started){started=true;Files.createDirectories(folder());mc.options.hideGui=false;
            mc.getSingleplayerServer().execute(()->{var p=mc.getSingleplayerServer().getPlayerList().getPlayers().get(0);p.closeContainer();p.setGameMode(net.minecraft.world.level.GameType.CREATIVE);JourneyData.get(p.server).player(p.getUUID()).putBoolean("Named",false);FamilyLife.openProfile(p);});return;}
        ticks++;
        if(ticks==30){checks.put("profile_displayed",mc.screen instanceof JourneyScreen);capture(mc,"00-profile.png");}
        if(ticks==35&&mc.screen instanceof JourneyScreen screen){
            screen.keyPressed(256,0,0);checks.put("escape_keeps_initial_form",mc.screen==screen);
            screen.onClose();checks.put("close_keeps_initial_form",mc.screen==screen);
            screen.keyPressed(257,0,0);screen.keyPressed(335,0,0);
        }
        if(ticks==39){checks.put("enter_does_not_submit",mc.screen instanceof JourneyScreen);
            mc.getSingleplayerServer().execute(()->{var p=mc.getSingleplayerServer().getPlayerList().getPlayers().get(0);checks.put("escape_enter_do_not_name",!JourneyData.get(p.server).player(p.getUUID()).getBoolean("Named"));});}
        if(ticks==40&&mc.screen instanceof JourneyScreen screen){
            var box=screen.children().stream().filter(EditBox.class::isInstance).map(EditBox.class::cast).findFirst().orElseThrow();
            box.setValue("");clickConfirm(screen);checks.put("empty_keeps_form",mc.screen==screen);
        }
        if(ticks==45)capture(mc,"01-empty-name-feedback.png");
        if(ticks==55&&mc.screen instanceof JourneyScreen screen){
            click(screen,300,304);
            for(char c:"星海旅人".toCharArray())screen.charTyped(c,0);
            clickConfirm(screen);
        }
        if(ticks==90){checks.put("confirm_closes_form",!(mc.screen instanceof JourneyScreen));capture(mc,"02-name-saved.png");
            mc.getSingleplayerServer().execute(()->{var p=mc.getSingleplayerServer().getPlayerList().getPlayers().get(0);var data=JourneyData.get(p.server).player(p.getUUID());checks.put("server_name_saved",data.getBoolean("Named")&&data.getString("Name").equals("星海旅人"));checks.put("display_name_updated",p.getDisplayName().getString().contains("星海旅人"));serverChecked=true;});}
        if(ticks>100&&serverChecked){
            Files.writeString(folder().resolve("report.json"),new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(Map.of("screenshots",shots,"checks",checks,"errors",errors)));
            com.tnc.tnc.dungeon.client.WorldFeedbackNativeAudit.finished(mc);
        }
    }
    private static void clickConfirm(JourneyScreen screen){click(screen,519,304);}
    private static void click(JourneyScreen screen,int x,int y){double scale=Math.min(1.3,Math.min((screen.width-16)/600.,(screen.height-16)/360.));screen.mouseClicked((screen.width-600*scale)/2+x*scale,(screen.height-360*scale)/2+y*scale,0);}
    private static void capture(Minecraft mc,String name){saving=true;Screenshot.grab(mc.gameDirectory,"naming-"+name,mc.getMainRenderTarget(),message->{try{Files.copy(mc.gameDirectory.toPath().resolve("screenshots/naming-"+name),folder().resolve(name),StandardCopyOption.REPLACE_EXISTING);shots.add(name);}catch(Exception error){errors.add(error.toString());}finally{saving=false;}});}
}
