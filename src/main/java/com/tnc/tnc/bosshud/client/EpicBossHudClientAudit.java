package com.tnc.tnc.bosshud.client;

import com.tnc.tnc.bosshud.*;
import net.minecraft.client.*;
import net.minecraft.client.gui.*;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.world.BossEvent;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.CustomizeGuiOverlayEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.*;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;
import java.nio.file.*;
import java.util.*;

/** Native GUI gallery plus real S2C identity, renamed-boss, legacy-bar and cleanup checks. Not installed. */
@Mod.EventBusSubscriber(modid="tnc",value=Dist.CLIENT)
public final class EpicBossHudClientAudit {
    private static int ticks;private static boolean opening,setup,foreignPassed,foreignCanceled;
    private static volatile boolean ready;private static final List<Mob> bosses=new ArrayList<>();private static ServerBossEvent foreignBar;
    private static final Set<String> screenshots=new LinkedHashSet<>();private static final List<String> failures=new ArrayList<>();private static long settledDraws;
    @Mod.EventBusSubscriber(modid="tnc",value=Dist.CLIENT,bus=Mod.EventBusSubscriber.Bus.MOD)
    public static final class Setup {
        @SubscribeEvent public static void load(net.minecraftforge.fml.event.lifecycle.FMLLoadCompleteEvent e){if(Boolean.getBoolean("tnc.epicBossHudAudit"))e.enqueueWork(()->{
            net.minecraftforge.common.MinecraftForge.EVENT_BUS.unregister(com.tnc.tnc.magic.TNDarkAimMechanics.class);
            net.minecraftforge.common.MinecraftForge.EVENT_BUS.unregister(com.tnc.tnc.magic.TNDarkHandCord.class);
            net.minecraftforge.common.MinecraftForge.EVENT_BUS.unregister(com.tnc.tnc.client.TNSpellClientVisuals.class);
            net.minecraftforge.common.MinecraftForge.EVENT_BUS.unregister(com.tnc.tnc.client.DarkGiantSiphonInput.class);
        });}
    }
    @SubscribeEvent(priority=EventPriority.LOWEST,receiveCanceled=true) public static void foreign(CustomizeGuiOverlayEvent.BossEventProgress event){
        if(!Boolean.getBoolean("tnc.epicBossHudAudit")||!event.getBossEvent().getName().getString().equals("Compatibility sample"))return;
        foreignPassed|=!event.isCanceled();foreignCanceled|=event.isCanceled();
    }
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent e)throws Exception{
        if(!Boolean.getBoolean("tnc.epicBossHudAudit")||e.phase!=TickEvent.Phase.END)return;var mc=Minecraft.getInstance();
        org.lwjgl.glfw.GLFW.glfwHideWindow(mc.getWindow().getWindow());
        if(mc.level==null||mc.player==null){
            if(mc.getOverlay()==null&&mc.screen instanceof net.minecraft.client.gui.screens.TitleScreen&&!opening){opening=true;mc.createWorldOpenFlows().loadLevel(mc.screen,"boss-hud-gallery");}
            if(mc.screen!=null)for(var child:mc.screen.children())if(child instanceof net.minecraft.client.gui.components.Button b&&Set.of("Proceed","Continue","I know what I'm doing!","继续").contains(b.getMessage().getString())){b.onPress();break;}
            return;
        }
        mc.options.pauseOnLostFocus=false;mc.options.enableVsync().set(false);mc.getWindow().updateVsync(false);
        if(!setup){setup=true;mc.options.guiScale().set(2);mc.resizeDisplay();var player=mc.player.getUUID();mc.getSingleplayerServer().execute(()->{
            var p=mc.getSingleplayerServer().getPlayerList().getPlayer(player);var level=p.serverLevel();p.setGameMode(GameType.CREATIVE);p.teleportTo(.5,96,.5);level.setDayTime(6000);
            // A previous interrupted gallery may have saved its test bosses. This opt-in dev world is reusable.
            for(var entity:level.getAllEntities())if(entity instanceof Mob existing&&BossHudTheme.forType(ForgeRegistries.ENTITY_TYPES.getKey(entity.getType()).toString())!=null)existing.discard();
            for(int x=-20;x<=20;x++)for(int z=-20;z<=24;z++)level.setBlock(new BlockPos(x,95,z),Blocks.SMOOTH_STONE.defaultBlockState(),2);
            for(String id:List.of("apollo","wind_general")){
                var type=ForgeRegistries.ENTITY_TYPES.getValue(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("tnc",id));var b=(Mob)type.create(level);
                b.setNoAi(true);b.setNoGravity(true);b.setPos(id.equals("apollo")?8:-8,96,9);if(id.equals("apollo"))b.setCustomName(Component.literal("RENAMED · APOLLO"));level.addFreshEntity(b);bosses.add(b);
            }
            foreignBar=new ServerBossEvent(Component.literal("Compatibility sample"),BossEvent.BossBarColor.PURPLE,BossEvent.BossBarOverlay.PROGRESS);foreignBar.setProgress(.6F);foreignBar.addPlayer(p);ready=true;
        });return;}
        if(!ready)return;ticks++;
        if(ticks==20)mc.setScreen(new Gallery(0));
        if(ticks==55)shot(mc,"epic-bars-elements.png");
        if(ticks==65)mc.setScreen(new Gallery(1));
        if(ticks==100)shot(mc,"epic-bars-wind-and-ink.png");
        if(ticks==110){mc.options.guiScale().set(4);mc.resizeDisplay();mc.setScreen(new Gallery(2));}
        if(ticks==140)shot(mc,"epic-bars-small-gui.png");
        if(ticks==150){mc.options.guiScale().set(2);mc.resizeDisplay();mc.setScreen(null);mc.getSingleplayerServer().execute(()->{bosses.get(0).setHealth(bosses.get(0).getMaxHealth()*.25F);bosses.get(1).setHealth(bosses.get(1).getMaxHealth()*.65F);});}
        if(ticks==190)shot(mc,"epic-bars-real-hud.png");
        if(ticks==205)mc.getSingleplayerServer().execute(()->{for(var b:bosses)b.discard();foreignBar.removeAllPlayers();});
        if(ticks==230)settledDraws=EpicBossHud.draws();
        if(ticks>=245){
            if(EpicBossHud.draws()<20||!EpicBossHud.drawnThemes().containsAll(Set.of("tnc:apollo","tnc:wind_general")))failures.add("Real S2C boss identities did not reach custom rendering");
            if(!foreignPassed||foreignCanceled)failures.add("Foreign/vanilla boss bar was intercepted");
            if(EpicBossHud.size()!=0||EpicBossHud.draws()!=settledDraws)failures.add("Removed bosses left stale custom bars");
            if(screenshots.size()!=4)failures.add("Missing gallery/HUD screenshot");
            var result=Map.of("failures",failures,"screenshots",screenshots,"realCustomDraws",EpicBossHud.draws(),"drawnThemes",EpicBossHud.drawnThemes(),"foreignBarPreserved",foreignPassed&&!foreignCanceled,"cacheCleared",EpicBossHud.size()==0,"variantsRendered",13);
            Files.writeString(Path.of(System.getProperty("tnc.epicBossHudReport")),new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(result));mc.stop();
        }
    }
    private static void shot(Minecraft mc,String name){if(screenshots.add(name))Screenshot.grab(mc.gameDirectory,name,mc.getMainRenderTarget(),m->{});}
    private static final class Gallery extends Screen {
        final int page;Gallery(int page){super(Component.literal("Epic boss HUD gallery"));this.page=page;}
        @Override public boolean isPauseScreen(){return false;}
        @Override public void render(GuiGraphics g,int mouseX,int mouseY,float partial){
            g.fillGradient(0,0,width,height,0xFF0B101B,0xFF18222D);g.drawString(font,"TN-C  /  EPIC BOSS SIGNATURES",16,12,0xE5E9E5,false);
            int start=page==0?0:page==1?6:8,end=page==0?6:page==1?13:11;
            String[] names={"阿波罗","巨兽人领主","磐岳 · 岩枪古像","蓝金水渊兽","火山巨龟","公孙衍（迷失）","御风将军","翠岚武尊","苍岚战将","墨渊螭","玄翎墨鹤","玄墨翼虎","玄墨天龙"};
            for(int i=start;i<end;i++){
                var theme=BossHudTheme.values()[i];int w=BossHudRules.width(width);int y=38+(i-start)*(page==2?63:62);
                int phase=theme==BossHudTheme.CANGLAN?2:theme.initialPhase;var snap=new BossHudNetwork.Snapshot(new UUID(0,i+1),new UUID(1,i+1),theme.type(),(i%3==0?250:730),1000,phase,theme.phases);
                float fill=BossHudRules.progress(snap.health(),snap.maximum());EpicBossBarArt.draw(g,theme,Component.literal(names[i]),snap,width/2-w/2,y,w,false,fill,Math.min(1,fill+.13F),12.5);
            }
            g.drawString(font,page==2?"SMALL GUI / RESPONSIVE CHECK":"NATIVE GUI GEOMETRY / 13 UNIQUE FRAMES",16,height-14,0x8299A6,false);
        }
    }
}
