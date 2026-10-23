package com.tnc.tnc.dungeon.ecology.client;

import com.tnc.tnc.dungeon.ecology.*;
import com.tnc.tnc.mana.sight.client.ManaSightClient;
import java.nio.file.*;
import java.util.*;
import net.minecraft.client.*;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Opt-in native renderer fixtures. Creates only client entities, never edits a world or inventory. */
@Mod.EventBusSubscriber(modid="tnc",value=Dist.CLIENT)
public final class EcologyNativeAudit {
    private static final String[] SCENES={"normal-disguises","mana-sight-organs","unfolded-crab-and-gargoyle","capture-window"};
    private static final Map<String,EcologyMob> MOBS=new LinkedHashMap<>();
    private static final List<String> FAILURES=new ArrayList<>();
    private static boolean started,saving,captured;
    private static int startup,scene,ticks;
    private static Path output(){return Path.of(System.getProperty("tnc.ecologyDungeonAuditOutput","work/dungeon-ecology-native-audit")).toAbsolutePath();}
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event)throws Exception{
        if(!Boolean.getBoolean("tnc.ecologyDungeonAudit")||event.phase!=TickEvent.Phase.END)return;
        var mc=Minecraft.getInstance();if(mc.level==null||mc.player==null||mc.getOverlay()!=null)return;
        if(!started){if(++startup<80)return;started=true;Files.createDirectories(output());mc.options.pauseOnLostFocus=false;mc.options.guiScale().set(2);mc.options.enableVsync().set(false);mc.getWindow().updateVsync(false);open(mc);return;}
        if(saving)return;ticks++;
        if(ticks>=45&&!captured){captured=true;saving=true;String name=String.format(Locale.ROOT,"%02d-%s.png",scene,SCENES[scene]);Screenshot.grab(mc.gameDirectory,"ecology-"+name,mc.getMainRenderTarget(),message->{try{Files.copy(mc.gameDirectory.toPath().resolve("screenshots/ecology-"+name),output().resolve(name),StandardCopyOption.REPLACE_EXISTING);}catch(Exception e){FAILURES.add(e.toString());}finally{saving=false;}});return;}
        if(ticks<55)return;ticks=0;captured=false;
        if(++scene==SCENES.length){ManaSightClient.setEnabled(false);Files.writeString(output().resolve("report.json"),new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(Map.of("scenes",List.of(SCENES),"failures",FAILURES,"scope","Native Minecraft entity renderers and actual four separate rigs; client-only pose fixtures, no gameplay verification or server world mutation")));if(Boolean.getBoolean("tnc.ecologyDungeonAuditExit"))mc.stop();else mc.setScreen(null);return;}
        open(mc);
    }
    private static void open(Minecraft mc){
        MOBS.clear();ManaSightClient.setEnabled(scene==1||scene==3);
        for(String kind:EcologyContent.KINDS){var mob=EcologyContent.TYPES.get(kind).create(mc.level);if(mob==null){FAILURES.add("No client entity "+kind);continue;}mob.setNoAi(true);mob.tickCount=100;var tag=new CompoundTag();mob.saveWithoutId(tag);if(scene==2&&(kind.equals("crab")||kind.equals("flash")))tag.putInt("EcologyAction",kind.equals("crab")?1:16);if(scene==3){tag.putBoolean("EcologySmall",true);tag.getCompound("ForgeData").putLong("CaptureUntil",mc.level.getGameTime()+200);}mob.load(tag);MOBS.put(kind,mob);}
        mc.setScreen(new Screen(Component.literal("Dungeon ecology render audit")){
            @Override public boolean isPauseScreen(){return false;}
            @Override public void render(GuiGraphics gui,int mouseX,int mouseY,float partial){
                gui.fill(0,0,width,height,0xff101925);gui.drawCenteredString(font,"TN-C · 地下城生态 · "+SCENES[scene],width/2,15,0xffe9e5dc);
                int panelWidth=Math.min(185,(width-35)/4),left=(width-panelWidth*4-18)/2;int floor=height-74;
                int index=0;for(var entry:MOBS.entrySet()){int x=left+index*(panelWidth+6);var mob=entry.getValue();gui.fill(x,47,x+panelWidth,floor+10,0xff202b3a);gui.fill(x+1,floor+7,x+panelWidth-1,floor+10,0xff324650);int scale=mob.ecologyKind().equals("siphon")?70:62;
                    InventoryScreen.renderEntityInInventoryFollowsMouse(gui,x+panelWidth/2,floor,scale,-28,-8,mob);
                    gui.drawCenteredString(font,mob.species().name(),x+panelWidth/2,floor+20,0xffe6e3d9);gui.drawCenteredString(font,kindLabel(entry.getKey()),x+panelWidth/2,floor+34,0xffa5bbc6);index++;}
                gui.drawCenteredString(font,scene==0?"表世界：宝匣伪装 / 幽浮不可见 / 暗紫闪灵 / 笨重吞噬虫":scene==1?"F8：深红伪装核心 / 发光吸盘水母 / 强光胸核 / 液态元素腹囊":scene==2?"独立骨架：六足晶钳展开 / 石像鬼张翼共鸣":"服务端同步缩小状态的原生渲染",width/2,height-18,0xff9db8c6);
            }
        });
    }
    private static String kindLabel(String kind){return switch(kind){case "crab"->"coffer / six legs / crystal claws";case "siphon"->"bell / suckers / six tentacles";case "flash"->"stone / ribbed wings / glare core";default->"mandibles / eight feet / liquid crop";};}
}
