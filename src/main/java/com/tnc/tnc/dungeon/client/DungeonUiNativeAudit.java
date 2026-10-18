package com.tnc.tnc.dungeon.client;

import com.tnc.tnc.dungeon.*;
import net.minecraft.client.*;
import net.minecraft.nbt.*;
import net.minecraftforge.event.TickEvent;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;

/** Isolated real-client screenshot fixtures. No server actions, inventories or world edits. */
final class DungeonUiNativeAudit {
    private static final String[] NAMES={"gate-all-segments","gate-locked-member","merchant","wish-lamps","treasure","rest","handbook","optional-encounter","cartographer","old-door","homeward","large-gui-scale","small-window","progress-hud"};
    private static final List<String> failures=new ArrayList<>();
    private static final List<Map<String,Object>> scenes=new ArrayList<>();
    private static boolean started,saving,captured;private static int startup,scene,ticks;
    private static Path output(){return Path.of(System.getProperty("tnc.dungeonAuditOutput")).toAbsolutePath();}
    static void tick(TickEvent.ClientTickEvent event)throws Exception{
        if(event.phase!=TickEvent.Phase.END)return;var mc=Minecraft.getInstance();org.lwjgl.glfw.GLFW.glfwHideWindow(mc.getWindow().getWindow());
        if(mc.options.enableVsync().get()){mc.options.enableVsync().set(false);mc.getWindow().updateVsync(false);}
        if(mc.level==null||mc.player==null||mc.getOverlay()!=null)return;
        if(!started){if(++startup<100)return;started=true;Files.createDirectories(output());mc.options.pauseOnLostFocus=false;mc.options.guiScale().set(2);open(mc);return;}
        if(saving)return;ticks++;
        if(ticks==75&&!captured){
            if(scene!=13&&!(mc.screen instanceof DungeonScreen)){failures.add("Unexpected screen at "+NAMES[scene]);}
            scenes.add(Map.of("name",NAMES[scene],"framebuffer_width",mc.getWindow().getWidth(),"framebuffer_height",mc.getWindow().getHeight(),"gui_width",mc.getWindow().getGuiScaledWidth(),"gui_height",mc.getWindow().getGuiScaledHeight()));
            String name=String.format(Locale.ROOT,"%02d-%s.png",scene,NAMES[scene]);saving=true;captured=true;
            Screenshot.grab(mc.gameDirectory,"dungeon-ui-"+name,mc.getMainRenderTarget(),m->{try{Files.copy(mc.gameDirectory.toPath().resolve("screenshots/dungeon-ui-"+name),output().resolve(name),StandardCopyOption.REPLACE_EXISTING);}catch(Exception ex){failures.add(ex.toString());}finally{saving=false;}});return;
        }
        if(ticks<90)return;if(++scene==NAMES.length){finish(mc);return;}ticks=0;captured=false;
        if(scene==11)mc.options.guiScale().set(3);
        if(scene==12){mc.options.guiScale().set(2);org.lwjgl.glfw.GLFW.glfwSetWindowSize(mc.getWindow().getWindow(),1024,640);}
        open(mc);
    }
    private static void open(Minecraft mc){
        var state=new CompoundTag();state.putString("Theme","astral");state.putInt("Floor",7);state.putLong("Saved",1260);state.putLong("Pending",30);state.putInt("Party",1);state.putInt("Unlocked",26);state.putBoolean("Leader",true);state.putInt("Room",2);state.putInt("Visited",15);state.putInt("Cleared",3);state.putLong("Token",0);
        var rooms=new ListTag();var graph=DungeonGraph.generate(1777,7);
        for(var room:graph.rooms)if(room.id()<5){var r=new CompoundTag();r.putInt("Id",room.id());r.putInt("X",room.cell().x());r.putInt("Z",room.cell().z());r.putString("Type",room.id()==4?"UNKNOWN":room.kind().name());r.putIntArray("Links",room.links().stream().filter(i->i<5).mapToInt(Integer::intValue).toArray());rooms.add(r);}state.put("Map",rooms);
        String kind=switch(scene){case 0,1,11->"gate";case 4->"cache";case 5->"rest";case 6,12->"guide";default->"event";};state.putString("Kind",kind);
        int event=switch(scene){case 2->0;case 3->1;case 7->2;case 8->3;case 9->4;default->5;};state.putInt("Event",event);
        if(kind.equals("gate")){state.putInt("Floor",0);state.remove("Map");state.putLong("Pending",0);}
        if(scene==1){state.putInt("Unlocked",1);state.putBoolean("Leader",false);state.putInt("Party",3);state.putBoolean("Multiplayer",true);}
        if(kind.equals("guide")){state.remove("Map");state.remove("Theme");}
        if(scene==13){
            mc.setScreen(new net.minecraft.client.gui.screens.Screen(net.minecraft.network.chat.Component.literal("Progress audit")){
                @Override public void render(net.minecraft.client.gui.GuiGraphics g,int mx,int my,float partial){DungeonScreen.progress(g,state);}
                @Override public boolean isPauseScreen(){return false;}
            });
        }else DungeonScreen.accept(new DungeonNetwork.State(state,true));
    }
    private static void finish(Minecraft mc)throws Exception{
        String hash;try(var in=DungeonScreen.class.getResourceAsStream("/com/tnc/tnc/dungeon/client/DungeonScreen.class")){hash=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(in.readAllBytes()));}
        var report=new LinkedHashMap<String,Object>();report.put("screenshots",scenes.size());report.put("scenes",scenes);report.put("failures",failures);report.put("screen_class_sha256",hash);report.put("scope","Original full-pack rendering; UI fixture data only; no server actions or world edits");
        Files.writeString(output().resolve("report.json"),new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(report));mc.stop();
    }
}
