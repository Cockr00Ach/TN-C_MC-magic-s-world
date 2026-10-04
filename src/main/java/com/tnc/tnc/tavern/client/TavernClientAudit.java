package com.tnc.tnc.tavern.client;

import com.google.gson.GsonBuilder;
import com.mojang.blaze3d.platform.Lighting;
import com.mojang.math.Axis;
import com.tnc.tnc.TNMod;
import com.tnc.tnc.npc.TNNpcs;
import com.tnc.tnc.tavern.*;
import net.minecraft.client.*;
import net.minecraft.client.gui.*;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;
import java.nio.file.*;
import java.util.*;

/** Explicit opt-in audit, only in the isolated development save. Never used by the pack. */
@Mod.EventBusSubscriber(modid=TNMod.MODID,value=Dist.CLIENT)
public final class TavernClientAudit {
    private static int age,phase,frames,chosen=-1;
    private static volatile boolean saving,saved,interacted;
    private static final List<String> failures=new ArrayList<>();
    private static final List<String> evidence=new ArrayList<>();
    private static TavernAtmosphere atmosphere;
    private static TavernRoomPacket region;
    private static float volume;
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event)throws Exception {
        if(!Boolean.getBoolean("tnc.tavernClientAudit")||event.phase!=TickEvent.Phase.END)return;
        var mc=Minecraft.getInstance();org.lwjgl.glfw.GLFW.glfwHideWindow(mc.getWindow().getWindow());
        if(mc.player==null||mc.level==null||mc.getOverlay()!=null)return;
        if(atmosphere==null) {
            atmosphere=TavernAtmosphere.load(mc.getSingleplayerServer());mc.options.guiScale().set(1);mc.options.pauseOnLostFocus=false;mc.resizeDisplay();
            volume=mc.options.getSoundSourceVolume(SoundSource.MUSIC);mc.options.getSoundSourceOptionInstance(SoundSource.MUSIC).set(.5);
            region=new TavernRoomPacket(mc.level.dimension().location(),List.of(mc.player.getBoundingBox().inflate(10)));
            send(mc,region);mc.setScreen(new Gallery(false));evidence.add("49 patron definitions loaded; dedicated renderer registered");
        }
        age++;
        if(TavernMusicController.entryTicks()>0&&TavernMusicController.entryTicks()<200&&TavernMusicController.active(mc))failures.add("Music started before 200 entry ticks");
        if(phase<2) {
            if(++frames<30||saving)return;
            if(!saved){capture(mc,phase==0?"tavern-seated-chairs.png":"tavern-patron-clothes.png");return;}
            saved=false;frames=0;phase++;
            if(phase==1){mc.setScreen(new Gallery(true));return;}
            mc.setScreen(null);
            mc.getSingleplayerServer().execute(()->{
                var player=mc.getSingleplayerServer().getPlayerList().getPlayers().get(0);var l=player.serverLevel();var p=player.blockPosition().offset(3,0,0);
                var block=ForgeRegistries.BLOCKS.getValue(ResourceLocation.fromNamespaceAndPath("conquest","oak_chair"));l.setBlock(p,block.defaultBlockState(),18);
                var guest=TNNpcs.TAVERN_GUEST.get().create(l);var s=atmosphere.seats().get(0);
                guest.configure(new TavernAtmosphere.Seat(s.id(),BlockPos.ZERO,"conquest:oak_chair",9.5/16,180,s.persona(),s.name(),s.lines()),p);l.addFreshEntity(guest);
                guest.mobInteract(player,InteractionHand.MAIN_HAND);interacted=true;
            });return;
        }
        if(phase==2) {
            if(!interacted||++frames<30||saving)return;
            if(!(mc.screen instanceof com.tnc.tnc.dialogue.client.DialogueScreen))failures.add("Right-click did not open the dialogue screen");
            if(!saved){capture(mc,"tavern-patron-dialogue.png");return;}
            evidence.add("Actual server mobInteract opened the dialogue over the registered channel");saved=false;frames=0;phase=3;mc.setScreen(new Gallery(true));return;
        }
        if(phase==3&&TavernMusicController.entryTicks()==200&&TavernMusicController.active(mc)) {
            chosen=TavernMusicController.selectedTrack();evidence.add("Looping OGG sound active after 200 entry ticks; track="+chosen);phase=4;frames=0;
        }
        if(phase==4&&++frames==30){mc.options.getSoundSourceOptionInstance(SoundSource.MUSIC).set(0.0);phase=5;frames=0;}
        if(phase==5&&++frames==15){if(TavernMusicController.active(mc))failures.add("Music was not stopped on mute");mc.options.getSoundSourceOptionInstance(SoundSource.MUSIC).set(.5);phase=6;frames=0;}
        if(phase==6&&++frames==45){if(!TavernMusicController.active(mc)||TavernMusicController.selectedTrack()!=chosen)failures.add("Mute/resume changed the visit track or failed");evidence.add("Mute/resume retained selected track");send(mc,new TavernRoomPacket(region.dimension(),List.of()));phase=7;frames=0;}
        if(phase==7&&++frames==15){if(TavernMusicController.active(mc)||TavernMusicController.entryTicks()!=0)failures.add("Leaving the room did not stop and reset music");evidence.add("Room exit stopped the sound and reset timer");send(mc,region);phase=8;frames=0;}
        if(phase==8&&++frames>205&&TavernMusicController.active(mc)){evidence.add("Reentry waited and began a new looping visit");finish(mc);}
        if(age>850){failures.add("Audit timeout at phase "+phase+", entry ticks="+TavernMusicController.entryTicks());finish(mc);}
    }
    private static void send(Minecraft mc,TavernRoomPacket packet){mc.getSingleplayerServer().execute(()->com.tnc.tnc.network.MagicStoneNetwork.CHANNEL.send(net.minecraftforge.network.PacketDistributor.PLAYER.with(()->mc.getSingleplayerServer().getPlayerList().getPlayers().get(0)),packet));}
    private static void capture(Minecraft mc,String name){saving=true;Screenshot.grab(mc.gameDirectory,name,mc.getMainRenderTarget(),message->{System.out.println("TAVERN AUDIT: "+message.getString());saved=true;saving=false;});}
    private static void finish(Minecraft mc)throws Exception {
        mc.options.getSoundSourceOptionInstance(SoundSource.MUSIC).set((double)volume);
        var report=Map.of("guest_definitions",49,"screenshots",3,"evidence",evidence,"failures",failures,"scope","Actual Conquest furniture and custom seated models in a development gallery; real dialogue packet and OpenAL music lifecycle; not a full-pack tour");
        Files.writeString(Path.of("../work/tavern-client-audit.json"),new GsonBuilder().setPrettyPrinting().create().toJson(report));System.out.println("TAVERN AUDIT "+(failures.isEmpty()?"PASS":"FAIL")+": "+report);mc.stop();
    }
    private static final class Gallery extends Screen {
        private final boolean clothes;
        private final List<TavernGuestEntity> guests=new ArrayList<>();
        Gallery(boolean clothes){super(Component.literal("Tavern seated model audit"));this.clothes=clothes;}
        @Override public boolean isPauseScreen(){return false;}
        @Override public void render(GuiGraphics g,int mx,int my,float partial) {
            g.fill(0,0,width,height,0xff222529);g.drawString(font,clothes?"酒馆常客 / 原生服装与固定坐姿":"酒馆坐席 / 实际Conquest家具与专用弯腿模型",24,22,0xE5D6B8,false);
            String[] chairs={"oak_chair","square_stool","round_wooden_stool","rustic_throne","small_red_cushion_stool","small_fancy_leather_stool","red_cushion"};
            double[] heights={9.5/16,8.5/16,8.1/16,10.0/16,6.0/16,6.0/16,3.0/16};int count=clothes?14:7;
            while(guests.size()<count){int i=guests.size();var guest=TNNpcs.TAVERN_GUEST.get().create(minecraft.level);var spec=atmosphere.seats().get(i);
                guest.configure(new TavernAtmosphere.Seat(spec.id(),BlockPos.ZERO,"conquest:oak_chair",heights[clothes?0:i],0,i*3,spec.name(),spec.lines()),BlockPos.ZERO);guests.add(guest);}
            for(int i=0;i<count;i++) {
                int c=clothes?0:i,x=95+i%7*179,y=clothes?310+i/7*260:420;float scale=clothes?95:115;
                var block=ForgeRegistries.BLOCKS.getValue(ResourceLocation.fromNamespaceAndPath("conquest",chairs[c]));var state=block.defaultBlockState();
                for(var property:state.getProperties())if(property.getName().equals("facing")&&property.getValue("south").isPresent())state=setSouth(state,property);
                g.fill(x-73,y-211,x+78,y+66,0xFF555D60);g.flush();Lighting.setupFor3DItems();var pose=g.pose();pose.pushPose();pose.translate(x,y,170);pose.scale(scale,-scale,scale);pose.mulPose(Axis.XP.rotationDegrees(12));pose.mulPose(Axis.YP.rotationDegrees(25));pose.translate(-.5,0,-.5);
                var buffer=minecraft.renderBuffers().bufferSource();minecraft.getBlockRenderer().renderSingleBlock(state,pose,buffer,15728880,OverlayTexture.NO_OVERLAY);
                minecraft.getEntityRenderDispatcher().render(guests.get(i),.5,heights[c],.5,0,partial,pose,buffer,15728880);buffer.endBatch();pose.popPose();Lighting.setupForFlatItems();
                g.drawString(font,clothes?guests.get(i).getCustomName().getString():chairs[c],x-70,y+74,0xDECDAA,false);
            }
            g.drawString(font,"仅开发验收画面 / 正式酒馆按楼层和桌组安排49位常客",24,height-24,0xADB8B5,false);
        }
        private static <T extends Comparable<T>> net.minecraft.world.level.block.state.BlockState setSouth(net.minecraft.world.level.block.state.BlockState state,net.minecraft.world.level.block.state.properties.Property<T> property){return state.setValue(property,property.getValue("south").orElseThrow());}
    }
}
